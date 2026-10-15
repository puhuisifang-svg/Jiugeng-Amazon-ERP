package com.jiugeng.erp.supplier;
import com.jiugeng.erp.iam.AuthenticatedActor;import java.util.List;
/** Public transaction-aware boundary for aggregate saves. */
public interface SupplierAssignmentGateway{
 record Assignment(String supplierId,String purchaseUrl,String remark,boolean primary){}
 final class AssignmentException extends RuntimeException{
  private final int status;private final String code;
  public AssignmentException(int status,String code,String message){super(message);this.status=status;this.code=code;}
  public int status(){return status;}public String code(){return code;}
 }
 void replaceVariantAssignments(String variantId,List<Assignment> assignments,AuthenticatedActor actor);
}
