package com.jiugeng.erp.iam;

/** Public, secret-free identity boundary for audited business operations. */
public interface AuthenticatedActor {
    String userId();
    String loginName();
    String displayName();
}
