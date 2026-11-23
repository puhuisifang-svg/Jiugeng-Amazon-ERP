package com.jiugeng.erp.supplier;

import com.jiugeng.erp.catalogmedia.JdbcExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class SupplierExternalImages extends JdbcExternalImageSource {
    SupplierExternalImages(JdbcClient jdbc){super(jdbc,"suppliers.read","SELECT qr_image_url url FROM supplier WHERE qr_image_asset_id IS NULL",true);}
}
