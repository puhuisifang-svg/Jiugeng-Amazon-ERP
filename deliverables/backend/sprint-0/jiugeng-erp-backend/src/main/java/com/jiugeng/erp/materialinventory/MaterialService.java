package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialInput;
import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialStatus;
import static com.jiugeng.erp.materialinventory.MaterialTypes.MaterialView;

import com.jiugeng.erp.catalogmedia.CatalogImageGateway;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MaterialService {
    private final MaterialRepository repository;
    private final CatalogImageGateway images;
    private final AuditTrail auditTrail;

    MaterialService(MaterialRepository repository, CatalogImageGateway images, AuditTrail auditTrail) {
        this.repository = repository;
        this.images = images;
        this.auditTrail = auditTrail;
    }

    @Transactional(readOnly = true)
    List<MaterialView> materials() { return repository.findAll(); }

    @Transactional(readOnly = true)
    MaterialView material(String id) { return repository.findById(id).orElseThrow(() -> notFound(id)); }

    @Transactional
    MaterialView create(MaterialInput input, AuthenticatedActor actor) {
        ValidatedMaterial valid = validate(input, null);
        String id = UUID.randomUUID().toString();
        try {
            repository.insert(id, valid.code(), valid.normalizedCode(), valid.name(), valid.type(), valid.specification(),
                    valid.unit(), valid.imageUrl(), valid.imageAssetId(), valid.remark(), actor.userId());
            images.attach(valid.imageAssetId(), CatalogImageGateway.Purpose.MATERIAL_IMAGE,
                    "AUXILIARY_MATERIAL", id, actor);
            MaterialView created = material(id);
            auditTrail.record(operation(actor, "MATERIAL_CREATE", id, null, snapshot(created), "创建辅料主数据"));
            return created;
        } catch (DataIntegrityViolationException exception) {
            throw new MaterialException(409, "MATERIAL_CODE_EXISTS", "辅料编码已存在（忽略大小写）");
        } catch (CatalogImageGateway.AttachmentException exception) {
            throw new MaterialException(exception.status(), exception.code(), exception.getMessage());
        }
    }

    @Transactional
    MaterialView update(String id, MaterialInput input, AuthenticatedActor actor) {
        MaterialView before = material(id);
        if (before.status() == MaterialStatus.DISABLED) {
            throw new MaterialException(409, "MATERIAL_DISABLED", "停用辅料不可编辑");
        }
        ValidatedMaterial valid = validate(input, id);
        if (!MaterialRepository.normalize(before.materialCode()).equals(valid.normalizedCode())) {
            throw new MaterialException(409, "MATERIAL_CODE_IMMUTABLE", "已保存的辅料编码不可修改，请停用后新建");
        }
        try {
            repository.update(id, valid.name(), valid.type(), valid.specification(), valid.unit(), valid.imageUrl(),
                    valid.imageAssetId(), valid.remark(), actor.userId());
            images.attach(valid.imageAssetId(), CatalogImageGateway.Purpose.MATERIAL_IMAGE,
                    "AUXILIARY_MATERIAL", id, actor);
            images.releaseReplaced(before.imageAssetId(), valid.imageAssetId(), "AUXILIARY_MATERIAL", id);
            MaterialView updated = material(id);
            auditTrail.record(operation(actor, "MATERIAL_UPDATE", id, snapshot(before), snapshot(updated),
                    "更新辅料主数据"));
            return updated;
        } catch (CatalogImageGateway.AttachmentException exception) {
            throw new MaterialException(exception.status(), exception.code(), exception.getMessage());
        }
    }

    @Transactional
    MaterialView disable(String id, AuthenticatedActor actor) {
        MaterialView before = material(id);
        if (before.status() == MaterialStatus.DISABLED) return before;
        repository.disable(id, actor.userId());
        MaterialView disabled = material(id);
        auditTrail.record(operation(actor, "MATERIAL_DISABLE", id, snapshot(before), snapshot(disabled), "逻辑停用辅料"));
        return disabled;
    }

    private ValidatedMaterial validate(MaterialInput input, String excludingId) {
        String code = required(input.materialCode(), "辅料编码", 64);
        String normalized = MaterialRepository.normalize(code);
        if (repository.codeExists(normalized, excludingId)) {
            throw new MaterialException(409, "MATERIAL_CODE_EXISTS", "辅料编码已存在（忽略大小写）");
        }
        String imageUrl = optional(input.imageUrl(), 500);
        if (imageUrl != null) validateHttpsUrl(imageUrl);
        String imageAssetId = optional(input.imageAssetId(), 36);
        if (imageUrl != null && imageAssetId != null) {
            throw new MaterialException(400, "MATERIAL_IMAGE_SOURCE_CONFLICT", "图片地址与上传图片只能选择一种");
        }
        return new ValidatedMaterial(code, normalized, required(input.name(), "辅料名称", 200),
                required(input.materialType(), "辅料类型", 100), optional(input.specification(), 500),
                required(input.unit(), "单位", 32), imageUrl, imageAssetId, optional(input.remark(), 1000));
    }

    private void validateHttpsUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new MaterialException(400, "MATERIAL_IMAGE_URL_INVALID", "图片地址必须是无用户信息的 HTTPS URL");
        }
    }

    private String required(String value, String label, int max) {
        String result = blankToNull(value);
        if (result == null || result.length() > max) {
            throw new MaterialException(400, "MATERIAL_FIELD_INVALID", label + "不能为空且长度不能超过 " + max);
        }
        return result;
    }

    private String optional(String value, int max) {
        String result = blankToNull(value);
        if (result != null && result.length() > max) {
            throw new MaterialException(400, "MATERIAL_FIELD_INVALID", "字段长度不能超过 " + max);
        }
        return result;
    }

    private String blankToNull(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private MaterialException notFound(String id) {
        return new MaterialException(404, "MATERIAL_NOT_FOUND", "辅料不存在：" + id);
    }

    private AuditTrail.BusinessOperation operation(AuthenticatedActor actor, String action, String id,
            Map<String, ?> before, Map<String, ?> after, String detail) {
        return new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(), action,
                "AUXILIARY_MATERIAL", id, true, before, after, detail);
    }

    private Map<String, Object> snapshot(MaterialView material) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", material.id());
        value.put("materialCode", material.materialCode());
        value.put("name", material.name());
        value.put("status", material.status());
        return value;
    }

    private record ValidatedMaterial(String code, String normalizedCode, String name, String type,
            String specification, String unit, String imageUrl, String imageAssetId, String remark) {}
}
