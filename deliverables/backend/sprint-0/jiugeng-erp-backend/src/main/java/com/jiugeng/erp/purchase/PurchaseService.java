package com.jiugeng.erp.purchase;

import static com.jiugeng.erp.purchase.PurchaseTypes.*;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.materialinventory.InventoryInboundGateway;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PurchaseService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final PurchaseRepository repository;
    private final InventoryInboundGateway inventory;
    private final AuditTrail audit;
    private final AuditHistoryLookup history;

    PurchaseService(PurchaseRepository repository, InventoryInboundGateway inventory, AuditTrail audit,
            AuditHistoryLookup history) {
        this.repository = repository; this.inventory = inventory; this.audit = audit; this.history = history;
    }

    @Transactional(readOnly = true)
    List<OrderView> orders(Status status) { return repository.list(status); }
    @Transactional(readOnly = true)
    OrderView order(String id) { return repository.find(id, false).orElseThrow(() -> missing(id)); }
    @Transactional(readOnly = true)
    OrderDetail detail(String id) {
        OrderView value = order(id);
        return new OrderDetail(value, repository.receipts(id), history.find("PURCHASE", id));
    }
    @Transactional(readOnly = true)
    List<ReferenceItem> references(OrderType type, String supplierId) {
        if (type == null) throw invalid("采购类型不能为空");
        return repository.referenceItems(type, required(supplierId, "供应商", 36));
    }
    @Transactional(readOnly = true)
    List<ReceiptView> receipts(String orderId) { return repository.receipts(orderId); }
    @Transactional(readOnly = true)
    Dashboard dashboard() {
        List<OrderView> recent = repository.list(null).stream().limit(10).toList();
        return new Dashboard(repository.count(Status.DRAFT), repository.count(Status.SUBMITTED),
                repository.count(Status.PARTIALLY_RECEIVED), repository.count(Status.COMPLETED), recent);
    }

    @Transactional
    OrderView create(OrderInput raw, AuthenticatedActor actor) {
        ValidatedOrder valid = validate(raw, null);
        String id = UUID.randomUUID().toString();
        String orderNo = nextOrderNo();
        repository.insertOrder(id, orderNo, valid.input(), actor.userId());
        repository.replaceLines(id, valid.lines());
        OrderView created = order(id);
        audit(actor, "PURCHASE_ORDER_CREATE", id, null, snapshot(created));
        return created;
    }

    @Transactional
    OrderView update(String id, OrderInput raw, AuthenticatedActor actor) {
        OrderView before = order(id);
        if (before.status() != Status.DRAFT) throw state("只有草稿采购单可编辑");
        if (raw == null || raw.orderType() != before.orderType()) throw invalid("采购类型保存后不可修改");
        ValidatedOrder valid = validate(raw, before.orderType());
        long version = requireVersion(raw.version());
        if (repository.updateDraft(id, valid.input(), actor.userId(), version) != 1) throw versionConflict();
        repository.replaceLines(id, valid.lines());
        OrderView after = order(id);
        audit(actor, "PURCHASE_ORDER_UPDATE", id, snapshot(before), snapshot(after));
        return after;
    }

    @Transactional
    OrderView submit(String id, long version, AuthenticatedActor actor) {
        OrderView before = order(id);
        if (before.status() != Status.DRAFT) throw state("只有草稿采购单可提交");
        transition(id, Status.DRAFT, Status.SUBMITTED, actor, version);
        OrderView after = order(id); audit(actor,"PURCHASE_ORDER_SUBMIT",id,snapshot(before),snapshot(after)); return after;
    }

    @Transactional
    OrderView cancel(String id, long version, AuthenticatedActor actor) {
        OrderView before = order(id);
        if (before.status() == Status.COMPLETED || before.status() == Status.CANCELLED) throw state("已完成或已取消采购单不可取消");
        transition(id, before.status(), Status.CANCELLED, actor, version);
        OrderView after = order(id); audit(actor,"PURCHASE_ORDER_CANCEL",id,snapshot(before),snapshot(after)); return after;
    }

    @Transactional
    ReceiptView receive(String orderId, ReceiptInput raw, AuthenticatedActor actor) {
        ReceiptInput input = validateReceipt(raw);
        String hash = receiptHash(orderId, input);
        OrderView locked = repository.find(orderId, true).orElseThrow(() -> missing(orderId));
        PurchaseRepository.ReceiptStored existing = repository.receiptByRequest(input.requestId()).orElse(null);
        if (existing != null) {
            if (!existing.orderId().equals(orderId) || !existing.payloadHash().equals(hash))
                throw new PurchaseException(409,"PURCHASE_RECEIPT_REQUEST_CONFLICT","同一 requestId 的收货载荷不一致");
            return repository.receipt(existing.id(), true);
        }
        if (locked.status() != Status.SUBMITTED && locked.status() != Status.PARTIALLY_RECEIVED)
            throw state("只有已提交或部分收货采购单可收货");
        Set<String> requestedIds = new HashSet<>();
        input.lines().forEach(line -> {
            if (!requestedIds.add(line.orderLineId())) throw invalid("收货明细不可重复");
            repository.lockLine(line.orderLineId());
        });
        Map<String, LineView> lines = new LinkedHashMap<>();
        repository.lines(orderId).forEach(line -> lines.put(line.id(), line));
        String receiptId = UUID.randomUUID().toString();
        repository.insertReceipt(receiptId, orderId, input.requestId(), hash, input.receivedAt(), input.remark(),
                actor.userId(), actor.loginName());
        for (ReceiptLineInput requested : input.lines()) {
            LineView line = lines.get(requested.orderLineId());
            if (line == null) throw new PurchaseException(409,"PURCHASE_LINE_ORDER_MISMATCH","收货明细不属于该采购单");
            BigDecimal quantity = positive(requested.quantity(), "收货数量");
            if (quantity.compareTo(line.remainingQuantity()) > 0) throw new PurchaseException(409,"PURCHASE_OVER_RECEIPT","收货数量超过剩余数量");
            String inventoryId = null;
            if (locked.orderType() == OrderType.MATERIAL) {
                InventoryInboundGateway.Result result = inventory.postInbound(new InventoryInboundGateway.Command(
                        "PURCHASE:" + input.requestId() + ":" + line.id(), line.itemId(), quantity, line.unit(),
                        "采购收货 " + locked.orderNo()), actor);
                inventoryId = result.transactionId();
            }
            repository.addReceived(line.id(), quantity);
            repository.insertReceiptLine(UUID.randomUUID().toString(), receiptId, line.id(), quantity, inventoryId);
        }
        boolean complete = repository.lines(orderId).stream().allMatch(line -> line.remainingQuantity().signum() == 0);
        repository.setReceiptStatus(orderId, complete ? Status.COMPLETED : Status.PARTIALLY_RECEIVED, actor.userId());
        ReceiptView receipt = repository.receipt(receiptId, false);
        audit(actor,"PURCHASE_RECEIPT_CREATE",orderId,null,Map.of("receiptId",receiptId,"orderId",orderId,"requestId",input.requestId(),
                "lineCount",input.lines().size(),"completed",complete));
        return receipt;
    }

    private void transition(String id, Status from, Status to, AuthenticatedActor actor, long version) {
        if (repository.transition(id, from, to, actor.userId(), version) != 1) throw versionConflict();
    }
    private ValidatedOrder validate(OrderInput raw, OrderType expectedType) {
        if (raw == null || raw.orderType() == null) throw invalid("采购类型不能为空");
        if (expectedType != null && raw.orderType() != expectedType) throw invalid("采购类型不可修改");
        String supplierId = required(raw.supplierId(), "供应商", 36); repository.activeSupplier(supplierId);
        String currency = required(raw.currency(), "币种", 3).toUpperCase(Locale.ROOT);
        if (!currency.matches("[A-Z]{3}")) throw invalid("币种必须为三位字母代码");
        String remark = optional(raw.remark(), 500);
        if (raw.lines() == null || raw.lines().isEmpty() || raw.lines().size() > 200) throw invalid("采购明细需为 1 至 200 行");
        Set<String> items = new HashSet<>(); List<PurchaseRepository.ResolvedLine> lines = new ArrayList<>(); int number=1;
        for (LineInput line : raw.lines()) {
            if (line == null) throw invalid("采购明细不能为空");
            String itemId = required(line.itemId(), "采购对象", 36);
            if (!items.add(itemId)) throw invalid("同一采购对象不可重复");
            PurchaseRepository.ItemRef item = repository.activeItem(raw.orderType(), supplierId, itemId);
            lines.add(new PurchaseRepository.ResolvedLine(UUID.randomUUID().toString(), number++, item,
                    positive(line.quantity(), "采购数量"), nonNegative(line.unitPrice(), "采购单价")));
        }
        return new ValidatedOrder(new OrderInput(raw.orderType(), supplierId, currency, remark, raw.version(), raw.lines()), lines);
    }
    private ReceiptInput validateReceipt(ReceiptInput raw) {
        if (raw == null) throw invalid("收货请求不能为空");
        String requestId = required(raw.requestId(), "请求号", 100);
        if (raw.receivedAt() == null) throw invalid("收货时间不能为空");
        Instant receivedAt = raw.receivedAt();
        if (receivedAt.isAfter(Instant.now().plusSeconds(300))) throw invalid("收货时间不能在未来");
        if (raw.lines() == null || raw.lines().isEmpty() || raw.lines().size() > 200) throw invalid("收货明细需为 1 至 200 行");
        raw.lines().forEach(line -> { if (line == null) throw invalid("收货明细不能为空"); required(line.orderLineId(),"采购明细",36); positive(line.quantity(),"收货数量"); });
        return new ReceiptInput(requestId, receivedAt, optional(raw.remark(),500), List.copyOf(raw.lines()));
    }
    private String nextOrderNo() {
        String date = LocalDate.now(BUSINESS_ZONE).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i=0;i<20;i++) { String value="PO-"+date+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase(Locale.ROOT); if(!repository.orderNoExists(value))return value; }
        throw new IllegalStateException("无法生成采购单号");
    }
    private String receiptHash(String orderId, ReceiptInput input) {
        StringBuilder value=new StringBuilder(orderId).append('\u001f').append(input.receivedAt()).append('\u001f')
                .append(input.remark()==null?"":input.remark());
        input.lines().stream().sorted(Comparator.comparing(ReceiptLineInput::orderLineId)).forEach(line -> value
                .append('\u001f').append(line.orderLineId()).append('=').append(line.quantity().stripTrailingZeros().toPlainString()));
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException error){throw new IllegalStateException(error);}
    }
    private long requireVersion(Long version){if(version==null||version<0)throw invalid("版本号不能为空");return version;}
    private BigDecimal positive(BigDecimal value,String label){if(value==null||value.signum()<=0||value.scale()>4)throw invalid(label+"必须为最多四位小数的正数");return value;}
    private BigDecimal nonNegative(BigDecimal value,String label){if(value==null||value.signum()<0||value.scale()>4)throw invalid(label+"必须为最多四位小数的非负数");return value;}
    private String required(String value,String label,int max){String result=optional(value,max);if(result==null)throw invalid(label+"不能为空");return result;}
    private String optional(String value,int max){if(value==null)return null;String result=value.trim();if(result.isEmpty())return null;if(result.length()>max||result.chars().anyMatch(c->c<32))throw invalid("字段格式或长度不正确");return result;}
    private PurchaseException invalid(String message){return new PurchaseException(400,"PURCHASE_FIELD_INVALID",message);}
    private PurchaseException state(String message){return new PurchaseException(409,"PURCHASE_STATE_INVALID",message);}
    private PurchaseException versionConflict(){return new PurchaseException(409,"PURCHASE_VERSION_CONFLICT","采购单已被其他用户更新");}
    private PurchaseException missing(String id){return new PurchaseException(404,"PURCHASE_ORDER_NOT_FOUND","采购单不存在："+id);}
    private Map<String,Object> snapshot(OrderView value){Map<String,Object> result=new LinkedHashMap<>();result.put("orderNo",value.orderNo());result.put("orderType",value.orderType());result.put("supplierId",value.supplierId());result.put("status",value.status());result.put("currency",value.currency());result.put("version",value.version());result.put("lines",value.lines().stream().map(line->Map.of("itemId",line.itemId(),"quantity",line.orderedQuantity(),"received",line.receivedQuantity(),"unitPrice",line.unitPrice())).toList());return result;}
    private void audit(AuthenticatedActor actor,String action,String id,Map<String,?>before,Map<String,?>after){audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"PURCHASE",id,true,before,after,"采购管理业务变更"));}
    private record ValidatedOrder(OrderInput input,List<PurchaseRepository.ResolvedLine> lines){}
}
