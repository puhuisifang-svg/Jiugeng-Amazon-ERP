package com.jiugeng.erp.sample;

import com.jiugeng.erp.catalogmedia.JdbcExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class SampleExternalImages extends JdbcExternalImageSource {
    SampleExternalImages(JdbcClient jdbc){super(jdbc,"samples.read","SELECT snapshot_image_url url FROM sample_location WHERE deleted_at IS NULL AND sample_image_id IS NULL",false);}
}
