package com.jiugeng.erp.materialinventory;
import com.jiugeng.erp.iam.AuthenticatedActor;import java.math.BigDecimal;import java.util.List;
/** Public boundary for Product aggregate material assignment. */
public interface ProductMaterialAssignmentGateway{
 record Assignment(String materialId,BigDecimal standardQuantity,String unit,BigDecimal lossRate,String remark,boolean enabled){}
 final class AssignmentException extends RuntimeException{
  private final int status;private final String code;
  public AssignmentException(int status,String code,String message){super(message);this.status=status;this.code=code;}
  public int status(){return status;}public String code(){return code;}
 }
 void replaceVariantAssignments(String productId,String variantId,List<Assignment> assignments,AuthenticatedActor actor);
}
