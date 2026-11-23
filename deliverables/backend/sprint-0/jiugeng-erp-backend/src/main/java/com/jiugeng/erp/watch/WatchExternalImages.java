package com.jiugeng.erp.watch;

import com.jiugeng.erp.catalogmedia.JdbcExternalImageSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class WatchExternalImages extends JdbcExternalImageSource {
    WatchExternalImages(JdbcClient jdbc){super(jdbc,"watch.read","SELECT image_url url FROM watch_presence",false);}
}
