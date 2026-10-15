package com.jiugeng.erp.catalogmedia;

import com.jiugeng.erp.iam.AuthenticatedActor;

/** Public attachment boundary used by Product and Supplier modules. */
public interface CatalogImageGateway {
    enum Purpose { SKU_MAIN, SUPPLIER_QR }

    final class AttachmentException extends RuntimeException {
        private final int status; private final String code;
        public AttachmentException(int status,String code,String message){super(message);this.status=status;this.code=code;}
        public int status(){return status;} public String code(){return code;}
    }

    void attach(String imageId, Purpose purpose, String resourceType, String resourceId,
            AuthenticatedActor actor);

    void releaseReplaced(String oldImageId, String newImageId, String resourceType, String resourceId);
}
