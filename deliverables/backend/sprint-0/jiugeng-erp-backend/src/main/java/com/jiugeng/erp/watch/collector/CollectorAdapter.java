package com.jiugeng.erp.watch.collector;

/**
 * Port for an approved collector source. Implementations must not bypass login,
 * CAPTCHA, access controls, rate limits or anti-automation mechanisms.
 */
public interface CollectorAdapter {

    String name();

    String version();

    CollectorResult collect(CollectorRequest request);

    default CollectorResult collectManual(CollectorRequest request, ManualSnapshot snapshot) {
        throw new UnsupportedOperationException("This collector does not accept manual snapshots");
    }
}
