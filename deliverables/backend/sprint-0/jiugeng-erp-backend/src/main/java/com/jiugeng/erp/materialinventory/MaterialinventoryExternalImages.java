package com.jiugeng.erp.materialinventory;

import com.jiugeng.erp.catalogmedia.JdbcExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class MaterialinventoryExternalImages extends JdbcExternalImageSource {
    MaterialinventoryExternalImages(JdbcClient jdbc){super(jdbc,"materials.read","SELECT image_url url FROM auxiliary_material WHERE image_asset_id IS NULL",false);}
}
