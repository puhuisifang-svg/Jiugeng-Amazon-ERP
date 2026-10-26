package com.jiugeng.erp.cartonlabel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

final class LabelTypes {
    enum Field { SKU, QUANTITY, BOXES, PURCHASER }
    record Row(@NotNull Field key,@NotBlank @Size(max=60) String label,
               @Min(10) @Max(30) int height,@Min(8) @Max(22) int fontSize) {}
    record Config(@NotBlank @Size(max=100) String title,@Min(8) @Max(20) int titleSize,boolean color,
                  @Size(max=150) String defaultDestination,@NotNull @Size(min=4,max=4) List<@Valid Row> rows,boolean singlePage) {
        Config(String title,int titleSize,boolean color,String defaultDestination,List<Row> rows) {
            this(title,titleSize,color,defaultDestination,rows,false);
        }
    }
    record TemplateInput(@NotBlank @Size(max=100) String name,@NotNull @Valid Config config,
                         boolean enabled,boolean defaultTemplate,@PositiveOrZero Long version) {}
    record Template(String id,String customerId,String name,Config config,boolean enabled,boolean defaultTemplate,long version) {}
    record Entry(@NotBlank @Size(max=36) String shipmentId,@PositiveOrZero long shipmentVersion,
                 @NotBlank @Size(max=36) String templateId,@PositiveOrZero long templateVersion,
                 @NotBlank @Size(max=150) String productName,@NotBlank @Size(max=128) String customerCode,
                 @NotBlank @Size(max=64) String purchaser,@Size(max=64) String contractNo,
                 @Size(max=150) String destination,@Size(max=40) String carrier,@Size(max=80) String trackingNo) {}
    record Batch(@NotNull @Size(min=1,max=50) List<@Valid Entry> items,
                 @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{1,36}") String requestKey) {}
    record Snapshot(Entry entry,String customerId,String customerName,java.math.BigDecimal quantity,int boxes,
                    String unit,String shipDate,String customerProductId,Template template) {
        int pages() { return template.config().singlePage()?1:boxes; }
    }
    record Draft(String shipmentId,long shipmentVersion,String customerId,String customerName,String productName,
                 String customerCode,java.math.BigDecimal quantity,int boxes,String unit,List<Template> templates,Entry previous,String orderReference) {}
    record Export(String id,int pages,String createdAt) {}
}
