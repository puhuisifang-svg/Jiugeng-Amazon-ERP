package com.jiugeng.erp.importexport;

import static com.jiugeng.erp.importexport.ImportTypes.*;

import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.materialinventory.InventoryOpeningImportGateway;
import com.jiugeng.erp.materialinventory.MaterialImportGateway;
import com.jiugeng.erp.product.ProductImportGateway;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
class ImportService {
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final String XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final ImportRepository repository; private final ImportFileStore files; private final XlsxImportValidator validator;
    private final ImportTemplateRegistry templates;
    private final AuditTrail audit; private final ProductImportGateway productImports;
    private final MaterialImportGateway materialImports; private final InventoryOpeningImportGateway openingImports;
    private final TransactionTemplate transactions;
    private final int previewHours;
    ImportService(ImportRepository repository, ImportFileStore files, XlsxImportValidator validator,
            ImportTemplateRegistry templates, AuditTrail audit,
            ProductImportGateway productImports, MaterialImportGateway materialImports,
            InventoryOpeningImportGateway openingImports,
            PlatformTransactionManager transactionManager,
            @Value("${app.imports.preview-hours:24}") int previewHours) {
        this.repository=repository; this.files=files; this.validator=validator; this.templates=templates; this.audit=audit;
        this.productImports=productImports; this.materialImports=materialImports; this.openingImports=openingImports;
        this.transactions=new TransactionTemplate(transactionManager); this.previewHours=previewHours;
    }

    @Transactional
    UploadReceipt upload(ImportType type, String version, String fileName, String contentType, byte[] bytes,
            String idempotencyKey, AuthenticatedActor actor) {
        String key=key(idempotencyKey); validateUploadMetadata(type,version,fileName,contentType,bytes);
        String fileHash=hash(bytes); String payload=hash((type.name()+"\u001f"+version+"\u001f"+fileHash).getBytes(StandardCharsets.UTF_8));
        var existing=repository.uploadByKey(actor.userId(),key).orElse(null);
        if (existing!=null) {
            if (!existing.payloadHash().equals(payload)) throw conflict();
            return new UploadReceipt(existing.view(),true);
        }
        ValidationResult validation=validator.validate(type,version,bytes);
        String storage=null;
        try {
            storage=files.store(new ByteArrayInputStream(bytes)); String id=UUID.randomUUID().toString();
            repository.insert(id,type,version,storage,fileHash,key,payload,actor.userId(),actor.loginName(),null);
            BatchView queued=repository.batch(id).view(); repository.validating(id);
            repository.validated(id,validation,UUID.randomUUID().toString(),Instant.now().plus(Duration.ofHours(previewHours)));
            audit.record(operation(actor,"IMPORT_UPLOAD_VALIDATE",id,Map.of("importType",type.name(),"fileHash",fileHash)));
            return new UploadReceipt(queued,false);
        } catch (IOException exception) {
            if(storage!=null) files.delete(storage);
            throw new ImportException(500,"IMPORT_TECHNICAL_FAILURE","文件暂存失败");
        } catch (RuntimeException exception) {
            if(storage!=null) files.delete(storage);
            throw exception;
        }
    }
    @Transactional(readOnly=true) List<BatchView> list() { return repository.list(); }
    @Transactional(readOnly=true) BatchView get(String id) { return repository.batch(id).view(); }
    @Transactional(readOnly=true) List<IssueView> issues(String id) { return repository.issues(id); }
    @Transactional(readOnly=true) PreviewView preview(String id) {
        var batch=repository.batch(id); requireValidated(batch);
        if (batch.previewExpiresAt().isBefore(Instant.now())) throw new ImportException(409,"IMPORT_PREVIEW_EXPIRED","预览已过期");
        var rows=repository.previewRows(id); int invalid=batch.errors();
        return new PreviewView(id,batch.previewVersion(),batch.previewExpiresAt(),batch.commitAllowed(),
                Map.of("inputRows",batch.totalRows(),"validRows",batch.totalRows()-invalid,"invalidRows",invalid,
                        "createRows",0,"updateRows",0,"unchangedRows",batch.totalRows()-invalid,"warningRows",batch.warnings()),rows);
    }
    @Transactional
    BatchView cancel(String id,String idempotencyKey,AuthenticatedActor actor) {
        String key=key(idempotencyKey); String payload=hash(("cancel\u001f"+id).getBytes(StandardCharsets.UTF_8));
        var replay=repository.operation(actor.userId(),"CANCEL",id,key).orElse(null);
        if(replay!=null) { if(!replay.payloadHash().equals(payload)) throw conflict(); return repository.batch(replay.responseBatchId()).view(); }
        var batch=repository.batch(id);
        if(batch.status()==Status.CANCELLED){repository.operation(actor.userId(),"CANCEL",id,key,payload,id);return batch.view();}
        if(batch.status()!=Status.QUEUED&&batch.status()!=Status.VALIDATING&&batch.status()!=Status.VALIDATED)
            throw new ImportException(409,"IMPORT_NOT_CANCELLABLE","当前批次状态不可取消");
        String resultKey=storeResult(batch,Status.CANCELLED,null,null);
        try {
            repository.cancel(id,resultKey); repository.operation(actor.userId(),"CANCEL",id,key,payload,id);
            audit.record(operation(actor,"IMPORT_CANCEL",id,Map.of("status","CANCELLED")));
            return repository.batch(id).view();
        } catch(RuntimeException exception) { files.delete(resultKey); throw exception; }
    }
    @Transactional
    UploadReceipt revalidate(String id,String idempotencyKey,AuthenticatedActor actor) {
        String key=key(idempotencyKey); var source=repository.batch(id);
        requireSupportedVersion(source);
        if(source.status()!=Status.VALIDATED || source.previewExpiresAt()==null || source.previewExpiresAt().isAfter(Instant.now()))
            throw new ImportException(409,"IMPORT_REVALIDATION_NOT_ALLOWED","仅过期的已校验批次可重验");
        String payload=hash(("revalidate\u001f"+id+"\u001f"+source.fileHash()).getBytes(StandardCharsets.UTF_8));
        var replay=repository.operation(actor.userId(),"REVALIDATE",id,key).orElse(null);
        if(replay!=null) { if(!replay.payloadHash().equals(payload)) throw conflict(); return new UploadReceipt(repository.batch(replay.responseBatchId()).view(),true); }
        try {
            byte[] bytes=files.read(source.storageKey()).readAllBytes(); ValidationResult result=validator.validate(source.type(),source.version(),bytes);
            String next=UUID.randomUUID().toString(); repository.insert(next,source.type(),source.version(),source.storageKey(),source.fileHash(),
                    "revalidate:"+UUID.randomUUID(),payload,actor.userId(),actor.loginName(),id);
            BatchView queued=repository.batch(next).view(); repository.validating(next);
            repository.validated(next,result,UUID.randomUUID().toString(),Instant.now().plus(Duration.ofHours(previewHours)));
            repository.operation(actor.userId(),"REVALIDATE",id,key,payload,next);
            audit.record(operation(actor,"IMPORT_REVALIDATE",next,Map.of("sourceBatchId",id)));
            return new UploadReceipt(queued,false);
        } catch(IOException exception) { throw new ImportException(500,"IMPORT_TECHNICAL_FAILURE","暂存文件不可读"); }
    }
    BatchView commit(String id,String previewVersion,String fileHash,boolean warningsAccepted,String idempotencyKey,
            AuthenticatedActor actor) {
        String key=key(idempotencyKey); var batch=repository.batch(id);
        requireSupportedVersion(batch);
        String payload=hash(("commit\u001f"+id+"\u001f"+previewVersion+"\u001f"+fileHash+"\u001f"+warningsAccepted).getBytes(StandardCharsets.UTF_8));
        var replay=repository.operation(actor.userId(),"COMMIT",id,key).orElse(null);
        if(replay!=null){if(!replay.payloadHash().equals(payload))throw conflict();return repository.batch(replay.responseBatchId()).view();}
        requireValidated(batch);
        if(batch.previewExpiresAt().isBefore(Instant.now())) throw new ImportException(409,"IMPORT_PREVIEW_EXPIRED","预览已过期");
        if(!java.util.Objects.equals(batch.previewVersion(),previewVersion) || !batch.fileHash().equals(fileHash))
            throw new ImportException(409,"IMPORT_PREVIEW_MISMATCH","确认快照与校验结果不一致");
        if(!batch.commitAllowed()) throw new ImportException(409,"IMPORT_COMMIT_NOT_ALLOWED","批次存在阻断问题");
        if(batch.warnings()>0 && !warningsAccepted) throw new ImportException(409,"IMPORT_WARNINGS_NOT_ACCEPTED","必须确认警告后提交");
        transactions.executeWithoutResult(status->{
            if(!repository.startCommit(id))throw new ImportException(409,"IMPORT_STATE_CONFLICT","批次提交状态已变化");
            repository.operation(actor.userId(),"COMMIT",id,key,payload,id);
        });
        var storedResult=new java.util.concurrent.atomic.AtomicReference<String>();
        try {
            transactions.executeWithoutResult(status->{
                ImportRepository.ProductImportResult result=commitBusinessRows(id,batch.type(),actor);
                String resultKey=storeResult(batch,Status.SUCCEEDED,null,result);storedResult.set(resultKey);
                repository.succeed(id,result,resultKey);
                audit.record(operation(actor,"IMPORT_COMMIT",id,Map.of("importType",batch.type().name(),
                        "created",result.created(),"updated",result.updated(),"unchanged",result.unchanged())));
            });
        } catch(RuntimeException exception) {
            if(storedResult.get()!=null)files.delete(storedResult.get());
            transactions.executeWithoutResult(status->{
                String code=failureCode(exception);String resultKey=storeResult(batch,Status.FAILED,code,null);
                repository.fail(id,code,resultKey);
                audit.record(new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),
                        "IMPORT_COMMIT","IMPORT_BATCH",id,false,null,Map.of("importType",batch.type().name(),"failureCode",code),
                        "导入整批回滚"));
            });
            throw exception instanceof ImportException value?value:new ImportException(409,"IMPORT_COMMIT_FAILED","导入提交失败，未写入任何业务数据");
        }
        return repository.batch(id).view();
    }
    @Transactional(readOnly=true) Map<String,Object> result(String id) {
        var batch=repository.batch(id);
        if(batch.status()!=Status.SUCCEEDED && batch.status()!=Status.FAILED && batch.status()!=Status.CANCELLED)
            throw new ImportException(409,"IMPORT_BATCH_NOT_READY","批次尚未结束");
        Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("batch",batch.view());result.put("createdRows",batch.createdRows());result.put("updatedRows",batch.updatedRows());
        result.put("unchangedRows",batch.unchangedRows());result.put("writtenRows",batch.createdRows()+batch.updatedRows());
        result.put("committedAt",batch.committedAt());result.put("message",batch.status()==Status.SUCCEEDED?"导入提交成功":"导入未成功");
        return result;
    }
    @Transactional(readOnly=true) ResultFile resultFile(String id) {
        var batch=repository.batch(id);
        if(batch.status()!=Status.SUCCEEDED && batch.status()!=Status.FAILED && batch.status()!=Status.CANCELLED)
            throw new ImportException(409,"IMPORT_BATCH_NOT_READY","批次尚未结束");
        if(batch.resultStorageKey()==null)throw new ImportException(409,"IMPORT_RESULT_FILE_NOT_AVAILABLE","当前批次没有结果文件");
        try(var input=files.read(batch.resultStorageKey())) {
            return new ResultFile("import-result-"+batch.id()+".csv",input.readAllBytes());
        } catch(IOException exception) { throw new ImportException(500,"IMPORT_TECHNICAL_FAILURE","结果文件不可读"); }
    }
    private void requireValidated(ImportRepository.BatchRow batch) {
        if(batch.status()!=Status.VALIDATED) throw new ImportException(409,"IMPORT_BATCH_NOT_READY","批次尚未完成校验");
    }
    private ImportRepository.ProductImportResult commitBusinessRows(String id,ImportType type,AuthenticatedActor actor) {
        var previews=repository.previewRows(id);
        if(type==ImportType.PRODUCTS || type==ImportType.VARIANTS) {
            List<ProductImportGateway.ImportRow> rows=previews.stream()
                    .map(row->new ProductImportGateway.ImportRow(row.sheet(),row.row(),row.data())).toList();
            ProductImportGateway.Validation validation=type==ImportType.PRODUCTS
                    ?productImports.validateProducts(rows):productImports.validateVariants(rows);
            if(!validation.valid())throw new ImportException(409,"IMPORT_DATA_CHANGED","提交前业务校验失败，请重新上传");
            ProductImportGateway.Result result=type==ImportType.PRODUCTS
                    ?productImports.commitProducts(rows,actor):productImports.commitVariants(rows,actor);
            return new ImportRepository.ProductImportResult(result.created(),result.updated(),result.unchanged());
        }
        if(type==ImportType.AUXILIARY_MATERIALS || type==ImportType.PRODUCT_AUXILIARY_MATERIALS) {
            List<MaterialImportGateway.ImportRow> rows=previews.stream()
                    .map(row->new MaterialImportGateway.ImportRow(row.sheet(),row.row(),row.data())).toList();
            MaterialImportGateway.Validation validation=type==ImportType.AUXILIARY_MATERIALS
                    ?materialImports.validateMaterials(rows):materialImports.validateRelations(rows);
            if(!validation.valid())throw new ImportException(409,"IMPORT_DATA_CHANGED","提交前业务校验失败，请重新上传");
            MaterialImportGateway.Result result=type==ImportType.AUXILIARY_MATERIALS
                    ?materialImports.commitMaterials(rows,actor):materialImports.commitRelations(rows,actor);
            return new ImportRepository.ProductImportResult(result.created(),result.updated(),result.unchanged());
        }
        List<InventoryOpeningImportGateway.ImportRow> rows=previews.stream()
                .map(row->new InventoryOpeningImportGateway.ImportRow(row.sheet(),row.row(),row.data())).toList();
        InventoryOpeningImportGateway.Validation validation=openingImports.validate(rows);
        if(!validation.valid())throw new ImportException(409,"IMPORT_DATA_CHANGED","提交前业务校验失败，请重新上传");
        InventoryOpeningImportGateway.Result result=openingImports.commit(rows,actor);
        return new ImportRepository.ProductImportResult(result.created(),result.updated(),result.unchanged());
    }
    private String storeResult(ImportRepository.BatchRow batch,Status status,String failureCode,
            ImportRepository.ProductImportResult result) {
        String csv=resultCsv(batch,status,failureCode,result);
        try{return files.storeResult(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));}
        catch(IOException exception){throw new ImportException(500,"IMPORT_TECHNICAL_FAILURE","结果文件写入失败");}
    }
    private String resultCsv(ImportRepository.BatchRow batch,Status status,String failureCode,
            ImportRepository.ProductImportResult result) {
        StringBuilder csv=new StringBuilder("\uFEFFrecord_type,batch_id,import_type,batch_status,sheet,row,business_key,row_status,detail\r\n");
        String detail=result==null?(failureCode==null?status.name():failureCode)
                :"created="+result.created()+";updated="+result.updated()+";unchanged="+result.unchanged();
        csv.append(csvRow("SUMMARY",batch.id(),batch.type().name(),status.name(),"","","",status.name(),detail));
        for(var row:repository.previewRows(batch.id()))csv.append(csvRow("ROW",batch.id(),batch.type().name(),
                status.name(),row.sheet(),String.valueOf(row.row()),row.businessKey(),status.name(),
                status==Status.SUCCEEDED?"committed":detail));
        return csv.toString();
    }
    private String csvRow(String... values) {
        return java.util.Arrays.stream(values).map(this::csvCell).collect(java.util.stream.Collectors.joining(","))+"\r\n";
    }
    private String csvCell(String value) {
        String safe=value==null?"":value;int index=0;while(index<safe.length()&&Character.isWhitespace(safe.charAt(index)))index++;
        if(index<safe.length()&&"=+-@".indexOf(safe.charAt(index))>=0)safe="'"+safe;
        return "\""+safe.replace("\"","\"\"")+"\"";
    }
    private void validateUploadMetadata(ImportType type,String version,String fileName,String mime,byte[] bytes) {
        if(type==null) throw new ImportException(400,"IMPORT_REQUEST_INVALID","导入类型必填");
        if(!templates.supports(type,version)) throw new ImportException(422,"IMPORT_TEMPLATE_VERSION_MISMATCH",
                "模板版本已停用，请下载当前版本后重新上传");
        if(fileName==null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
            throw new ImportException(415,"IMPORT_FILE_TYPE_UNSUPPORTED","仅支持 .xlsx");
        if(!XLSX_MIME.equals(mime)) throw new ImportException(415,"IMPORT_MIME_UNSUPPORTED","上传 MIME 不受支持");
        if(bytes==null || bytes.length==0) throw new ImportException(400,"IMPORT_FILE_EMPTY","上传文件为空");
        if(bytes.length>MAX_BYTES) throw new ImportException(413,"IMPORT_FILE_TOO_LARGE","上传文件超过 10 MiB");
    }
    private String key(String value) {
        if(value==null || value.isBlank() || value.length()>128 || value.chars().anyMatch(ch -> Character.isISOControl(ch)))
            throw new ImportException(400,"IMPORT_REQUEST_INVALID","Idempotency-Key 必须为 1..128 位不透明文本");
        return value.trim();
    }
    private void requireSupportedVersion(ImportRepository.BatchRow batch) {
        if (!templates.supports(batch.type(), batch.version())) {
            throw new ImportException(409,"IMPORT_TEMPLATE_VERSION_RETIRED","该批次模板版本已停用，请取消并使用当前模板重新上传");
        }
    }
    private ImportException conflict(){return new ImportException(409,"IDEMPOTENCY_KEY_REUSED","幂等键已用于不同载荷");}
    private String failureCode(RuntimeException exception){return exception instanceof ImportException value?value.code():"IMPORT_COMMIT_FAILED";}
    private String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor,String action,String id,Map<String,?> after){return new AuditTrail.BusinessOperation(actor.userId(),actor.loginName(),actor.displayName(),action,"IMPORT_BATCH",id,true,null,after,"五类活跃导入批次");}
    record UploadReceipt(BatchView batch,boolean replayed){}
    record ResultFile(String fileName,byte[] content){}
}
