package com.jiugeng.erp.catalogmedia;

import com.jiugeng.erp.iam.AuthenticatedActor;

/** Public attachment boundary used by Product, Material and Supplier modules. */
public interface CatalogImageGateway {
    enum Purpose { PRODUCT_MAIN, SKU_MAIN, SUPPLIER_QR, MATERIAL_IMAGE, SAMPLE_STORAGE, CUSTOMER_WECHAT, SAMPLE_IMAGE, PURCHASE_ITEM, PURCHASE_WAYBILL }

    final class AttachmentException extends RuntimeException {
        private final int status; private final String code;
        public AttachmentException(int status,String code,String message){super(message);this.status=status;this.code=code;}
        public int status(){return status;} public String code(){return code;}
    }

    /** Independent sample snapshot; never shares storage keys with a replaceable source. */
    default String copyForSample(String imageId,String sampleId,AuthenticatedActor actor) { throw new UnsupportedOperationException("Sample image copy unavailable"); }

    void attach(String imageId, Purpose purpose, String resourceType, String resourceId,
            AuthenticatedActor actor);

    void releaseReplaced(String oldImageId, String newImageId, String resourceType, String resourceId);
}
