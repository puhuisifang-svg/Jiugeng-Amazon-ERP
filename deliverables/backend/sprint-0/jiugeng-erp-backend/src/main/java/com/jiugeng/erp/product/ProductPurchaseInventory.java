package com.jiugeng.erp.product;
import java.math.BigDecimal;
import com.jiugeng.erp.iam.AuthenticatedActor;
/** Public transaction boundary for purchase receipts; does not grant manual stock permissions. */
public interface ProductPurchaseInventory {
 String receive(String variantId,String requestId,BigDecimal quantity,String reason,AuthenticatedActor actor);
 final class Failure extends RuntimeException {
  public final int status; public final String code;
  public Failure(int status,String code,String message){super(message);this.status=status;this.code=code;}
 }
}
