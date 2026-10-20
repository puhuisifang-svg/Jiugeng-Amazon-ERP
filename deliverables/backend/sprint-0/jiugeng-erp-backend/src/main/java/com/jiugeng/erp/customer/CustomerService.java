package com.jiugeng.erp.customer;

import static com.jiugeng.erp.customer.CustomerTypes.*;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.product.ProductLookup;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerService implements CustomerProductLookup {
    private final JdbcClient jdbc;
    private final ProductLookup catalog;
    private final AuditTrail audit;
    CustomerService(JdbcClient jdbc, ProductLookup catalog, AuditTrail audit) {
        this.jdbc=jdbc; this.catalog=catalog; this.audit=audit;
    }
    private static final String CUSTOMER_FILTER = """
        FROM customer WHERE (:status='' OR status=:status) AND (:q='' OR
        LOWER(name) LIKE :term OR LOWER(account_1688) LIKE :term OR LOWER(contact_person) LIKE :term
        OR LOWER(phone) LIKE :term OR LOWER(wechat_id) LIKE :term)
        """;
    private static final String PRODUCT_FROM = " FROM customer_product p JOIN customer c ON c.id=p.customer_id ";
    private static final String PRODUCT_FILTER = """
        WHERE p.deleted_at IS NULL AND (:status='' OR p.status=:status) AND (:customer='' OR p.customer_id=:customer) AND
        (:q='' OR LOWER(c.name) LIKE :term OR LOWER(p.sku_code) LIKE :term OR LOWER(p.product_name) LIKE :term
        OR LOWER(p.customer_product_code) LIKE :term OR LOWER(p.requirements) LIKE :term)
        """;
    @Transactional(readOnly=true)
    Page<CustomerView> customers(String q,Status status,int page,int size) {
        var params=filters(q,status); params.put("size",size); params.put("offset",page*size);
        long count=jdbc.sql("SELECT COUNT(*) "+CUSTOMER_FILTER).params(params).query(Long.class).single();
        var items=jdbc.sql("SELECT *, (id=(SELECT default_customer_id FROM customer_preferences WHERE id=1)) default_customer "+CUSTOMER_FILTER+" ORDER BY updated_at DESC,id LIMIT :size OFFSET :offset")
            .params(params).query(this::customer).list();
        return new Page<>(items,count,page,size);
    }
    @Transactional(readOnly=true)
    Page<ProductView> products(String q,String customerId,Status status,int page,int size) {
        var params=filters(q,status); params.put("customer",customerId); params.put("size",size); params.put("offset",page*size);
        long count=jdbc.sql("SELECT COUNT(*) "+PRODUCT_FROM+PRODUCT_FILTER).params(params).query(Long.class).single();
        var items=jdbc.sql("SELECT p.*,c.name customer_name,c.status customer_status "+PRODUCT_FROM+PRODUCT_FILTER+
            " ORDER BY p.updated_at DESC,p.id LIMIT :size OFFSET :offset").params(params).query(this::product).list();
        return new Page<>(items,count,page,size);
    }
    private Map<String,Object> filters(String q,Status status) {
        var params=new HashMap<String,Object>(); String term=q.trim().toLowerCase(Locale.ROOT);
        params.put("q",term); params.put("term","%"+term+"%"); params.put("status",status==null?"":status.name()); return params;
    }
    private CustomerView getCustomer(String id) {
        return jdbc.sql("SELECT *, (id=(SELECT default_customer_id FROM customer_preferences WHERE id=1)) default_customer FROM customer WHERE id=:id").param("id",id).query(this::customer).optional()
            .orElseThrow(()->new CustomerException(404,"CUSTOMER_NOT_FOUND","客户不存在"));
    }
    private ProductView getProduct(String id) {
        return jdbc.sql("SELECT p.*,c.name customer_name,c.status customer_status "+PRODUCT_FROM+" WHERE p.id=:id AND p.deleted_at IS NULL")
            .param("id",id).query(this::product).optional()
            .orElseThrow(()->new CustomerException(404,"CUSTOMER_PRODUCT_NOT_FOUND","客户商品不存在"));
    }
    @Transactional
    CustomerView saveCustomer(String id,CustomerInput input,AuthenticatedActor actor) {
        String encodedTitles=input.invoiceTitles()==null?null:CustomerInvoiceTitles.encode(input.invoiceTitles());
        String oldDefault=jdbc.sql("SELECT COALESCE(default_customer_id,'') FROM customer_preferences WHERE id=1 FOR UPDATE").query(String.class).single();
        boolean create=id==null; CustomerView before=create?null:getCustomer(id);
        if(input.status()==Status.DISABLED && (Boolean.TRUE.equals(input.defaultCustomer()) || Objects.equals(id,oldDefault)))
            throw new CustomerException(409,"DEFAULT_CUSTOMER_DISABLED","默认客户不能停用，请先将其他启用客户设为默认");
        if(Boolean.FALSE.equals(input.defaultCustomer()) && Objects.equals(id,oldDefault))
            throw new CustomerException(409,"DEFAULT_CUSTOMER_REQUIRED","请通过设置其他默认客户进行切换");

        if(create) id=UUID.randomUUID().toString();
        var params=new HashMap<String,Object>(); params.put("id",id); params.put("actor",actor.userId());
        params.put("name",required(input.name(),"客户名称")); params.put("account",text(input.account1688()));
        params.put("contact",text(input.contactPerson())); params.put("phone",text(input.phone()));
        params.put("wechat",text(input.wechatId())); params.put("address",text(input.address())); params.put("remark",text(input.remark()));
        params.put("status",input.status().name()); params.put("version",input.version());
        params.put("invoice",text(input.invoiceTitle())); params.put("tax",text(input.taxNumber()));
        params.put("registeredAddress",text(input.registeredAddress())); params.put("registeredPhone",text(input.registeredPhone()));
        params.put("bank",text(input.bankName())); params.put("accountNumber",text(input.bankAccount()));
        if(input.invoiceTitles()!=null) {
            var first=input.invoiceTitles().isEmpty()?new InvoiceTitle("","","","","",""):input.invoiceTitles().get(0);
            params.put("invoice",text(first.invoiceTitle()));params.put("tax",text(first.taxNumber()));
            params.put("registeredAddress",text(first.registeredAddress()));params.put("registeredPhone",text(first.registeredPhone()));
            params.put("bank",text(first.bankName()));params.put("accountNumber",text(first.bankAccount()));
        }
        if(create) {
            jdbc.sql("""
                INSERT INTO customer(id,name,account_1688,contact_person,phone,wechat_id,address,remark,status,created_by,updated_by,invoice_title,tax_number,registered_address,registered_phone,bank_name,bank_account)
                VALUES(:id,:name,:account,:contact,:phone,:wechat,:address,:remark,:status,:actor,:actor,:invoice,:tax,:registeredAddress,:registeredPhone,:bank,:accountNumber)
                """).params(params).update();
        } else if(jdbc.sql("""
            UPDATE customer SET name=:name,account_1688=:account,contact_person=:contact,phone=:phone,wechat_id=:wechat,
            address=:address,remark=:remark,status=:status,invoice_title=:invoice,tax_number=:tax,registered_address=:registeredAddress,registered_phone=:registeredPhone,bank_name=:bank,bank_account=:accountNumber,version=version+1,updated_by=:actor,updated_at=CURRENT_TIMESTAMP
            WHERE id=:id AND version=:version
            """).params(params).update()!=1) throw conflict();
        if(Boolean.TRUE.equals(input.defaultCustomer()) && !id.equals(oldDefault)) {
            jdbc.sql("UPDATE customer_preferences SET default_customer_id=:id WHERE id=1").param("id",id).update();
            if(!oldDefault.isBlank()) jdbc.sql("UPDATE customer SET version=version+1 WHERE id=:id").param("id",oldDefault).update();
            audit(actor,"CUSTOMER_DEFAULT_CHANGE","CUSTOMER",id,Map.of("defaultCustomerId",oldDefault),Map.of("defaultCustomerId",id));
        }
        if(input.invoiceTitles()!=null)
            jdbc.sql("UPDATE customer SET invoice_titles=:titles WHERE id=:id")
                .param("titles",encodedTitles).param("id",id).update();
        var after=getCustomer(id);
        // Contact details and free text never enter audit logs.
        audit(actor,create?"CUSTOMER_CREATE":"CUSTOMER_UPDATE","CUSTOMER",id,
            before==null?null:customerAudit(before),customerAudit(after));
        return after;
    }
    @Transactional
    ProductView saveProduct(String id,ProductInput input,AuthenticatedActor actor) {
        boolean create=id==null; ProductView before=create?null:getProduct(id);
        if(before!=null && (!before.customerId().equals(input.customerId()) || !before.productId().equals(input.productId()) || !before.variantId().equals(input.variantId())))
            throw new CustomerException(400,"CUSTOMER_PRODUCT_IDENTITY_IMMUTABLE","已建档商品不能更换客户或 SKU，请另行新增");
        // Serialize customer status updates with quotation creation/update.
        jdbc.sql("SELECT id FROM customer WHERE id=:id FOR UPDATE").param("id",input.customerId()).query(String.class).optional()
            .orElseThrow(()->new CustomerException(404,"CUSTOMER_NOT_FOUND","客户不存在"));
        jdbc.sql("SELECT id FROM product WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",input.productId()).query(String.class).optional()
            .orElseThrow(()->new CustomerException(400,"CUSTOMER_SKU_INVALID","产品已删除或不存在"));
        jdbc.sql("SELECT id FROM product_variant WHERE id=:id AND deleted_at IS NULL FOR UPDATE").param("id",input.variantId()).query(String.class).optional()
            .orElseThrow(()->new CustomerException(400,"CUSTOMER_SKU_INVALID","SKU 已删除或不存在"));
        var customer=getCustomer(input.customerId());
        String skuCode=before==null?"":before.skuCode(), productName=before==null?"":before.productName();
        if(create || input.status()==Status.ACTIVE) {
            if(customer.status()!=Status.ACTIVE) throw new CustomerException(409,"CUSTOMER_DISABLED","客户已停用，请先启用客户");
            var sku=catalog.findSku(input.productId(),input.variantId())
                .orElseThrow(()->new CustomerException(400,"CUSTOMER_SKU_INVALID","请选择有效的产品 SKU"));
            if(!sku.active()) throw new CustomerException(409,"CUSTOMER_SKU_DISABLED","SKU 或所属产品已停用");
            skuCode=sku.skuCode(); productName=sku.name();
        }
        if(create) id=UUID.randomUUID().toString();
        var params=new HashMap<String,Object>(); params.put("id",id); params.put("actor",actor.userId());
        params.put("customer",input.customerId()); params.put("product",input.productId()); params.put("variant",input.variantId());
        params.put("sku",skuCode); params.put("name",productName); params.put("price",input.supplyPrice());
        params.put("unit",required(input.priceUnit(),"计价单位")); params.put("code",text(input.customerProductCode()));
        params.put("requirements",text(input.requirements())); params.put("status",input.status().name()); params.put("version",input.version());
        try {
            if(create) jdbc.sql("""
                INSERT INTO customer_product(id,customer_id,product_id,variant_id,sku_code,product_name,supply_price,price_unit,
                customer_product_code,requirements,status,created_by,updated_by)
                VALUES(:id,:customer,:product,:variant,:sku,:name,:price,:unit,:code,:requirements,:status,:actor,:actor)
                """).params(params).update();
            else if(jdbc.sql("""
                UPDATE customer_product SET sku_code=:sku,product_name=:name,supply_price=:price,price_unit=:unit,
                customer_product_code=:code,requirements=:requirements,status=:status,version=version+1,
                updated_by=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND version=:version AND deleted_at IS NULL
                """).params(params).update()!=1) throw conflict();
        } catch(DuplicateKeyException error) {
            throw new CustomerException(409,"CUSTOMER_SKU_DUPLICATE","该客户已维护此 SKU，请编辑现有记录（含停用记录）");
        }
        var after=getProduct(id);
        audit(actor,create?"CUSTOMER_PRODUCT_CREATE":"CUSTOMER_PRODUCT_UPDATE","CUSTOMER_PRODUCT",id,
            before==null?null:productAudit(before),productAudit(after));
        return after;
    }
    @Transactional(readOnly=true)
    CustomerView defaultCustomer() {
        return jdbc.sql("SELECT default_customer_id FROM customer_preferences WHERE id=1").query(String.class).optional()
            .map(this::getCustomer).orElse(null);
    }
    @Override @Transactional
    public Optional<ShippingProduct> active(String id) { return lookup(id,false); }
    @Override @Transactional
    public Optional<ShippingProduct> forExisting(String id) { return lookup(id,true); }
    private Optional<ShippingProduct> lookup(String id,boolean existing) {
        if(id==null || id.isBlank()) return Optional.empty();
        return jdbc.sql("SELECT p.*,c.name customer_name FROM customer_product p JOIN customer c ON c.id=p.customer_id WHERE p.id=:id AND (p.deleted_at IS NULL AND p.status='ACTIVE' OR :existing=TRUE AND p.deleted_at IS NOT NULL) AND c.status='ACTIVE' FOR UPDATE").param("existing",existing)
            .param("id",id).query((r,n)->new ShippingProduct(r.getString("id"),r.getString("customer_id"),r.getString("customer_name"),
                r.getString("product_id"),r.getString("variant_id"),r.getString("customer_product_code"),r.getString("requirements"))).optional()
            .filter(value->(existing?catalog.findExistingSku(value.productId(),value.variantId()):catalog.findSku(value.productId(),value.variantId())).map(ProductLookup.SkuReference::active).orElse(false));
    }
    private String text(String value) {
        String result=value==null?"":value.trim();
        if(result.chars().anyMatch(c->c<32 && c!='\n' && c!='\t'))
            throw new CustomerException(400,"CUSTOMER_FIELD_INVALID","字段包含无效字符");
        return result;
    }
    private String required(String value,String label) {
        String result=text(value); if(result.isBlank()) throw new CustomerException(400,"CUSTOMER_FIELD_INVALID",label+"不能为空"); return result;
    }
    private CustomerException conflict() { return new CustomerException(409,"CUSTOMER_VERSION_CONFLICT","记录已被其他用户修改，请刷新后重新编辑"); }
    private Map<String,?> customerAudit(CustomerView v) { return Map.of("status",v.status(),"version",v.version(),"defaultCustomer",v.defaultCustomer()); }
    private Map<String,?> productAudit(ProductView v) {
        return Map.of("customerId",v.customerId(),"variantId",v.variantId(),"supplyPrice",v.supplyPrice()==null?"未定价":v.supplyPrice(),"priceUnit",v.priceUnit(),
            "status",v.status(),"version",v.version());
    }
    private void audit(AuthenticatedActor actor,String action,String type,String id,Map<String,?> before,Map<String,?> after) {
        audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,type,id,true,before,after,"客户档案或客户报价变更"));
    }
    private CustomerView customer(ResultSet r,int row) throws SQLException {
        return new CustomerView(r.getString("id"),r.getString("name"),r.getString("account_1688"),r.getString("contact_person"),
            r.getString("phone"),r.getString("wechat_id"),r.getString("address"),r.getString("remark"),Status.valueOf(r.getString("status")),
            r.getLong("version"),r.getTimestamp("updated_at").toInstant(),r.getString("invoice_title"),r.getString("tax_number"),r.getString("registered_address"),r.getString("registered_phone"),r.getString("bank_name"),r.getString("bank_account"),r.getBoolean("default_customer"),CustomerInvoiceTitles.read(r));
    }
    private ProductView product(ResultSet r,int row) throws SQLException {
        return new ProductView(r.getString("id"),r.getString("customer_id"),r.getString("customer_name"),Status.valueOf(r.getString("customer_status")),
            r.getString("product_id"),r.getString("variant_id"),r.getString("sku_code"),r.getString("product_name"),r.getBigDecimal("supply_price"),
            r.getString("price_unit"),r.getString("customer_product_code"),r.getString("requirements"),Status.valueOf(r.getString("status")),
            r.getLong("version"),r.getTimestamp("updated_at").toInstant());
    }
}
