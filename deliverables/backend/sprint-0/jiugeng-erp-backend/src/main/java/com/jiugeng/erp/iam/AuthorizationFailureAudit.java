package com.jiugeng.erp.iam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
class AuthorizationFailureAudit {
    private final AuditService audit;
    AuthorizationFailureAudit(AuditService audit) { this.audit=audit; }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(IamTypes.SessionPrincipal actor, String path, String reason) {
        audit.append(AuditTypes.Actor.authenticated(actor),"ACCOUNT","AUTHORIZATION_CHANGE_FAILED","ROLE",null,AuditTypes.Outcome.FAILURE,null,null,path+": "+reason);
    }
}
