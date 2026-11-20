package com.jiugeng.erp.product;

import static com.jiugeng.erp.product.ProductTypes.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProductPageService {
    record Page(List<ProductView> items,long totalCount,long matchingSkuCount,List<String> matchingVariantIds,int page,int size,List<ProductInventoryService.Balance> balances) {}
    record Filter(String q,String field,boolean exact,String productStatus,String status,String hasImage,String hasSupplier,String hasMaterial,String stock,String missing,String productType,List<String> variantIds) {}
    record Query(String where,Map<String,Object> params) {}
    private final JdbcClient jdbc; private final ProductRepository repo; private final ProductInventoryService inventory;
    ProductPageService(JdbcClient jdbc,ProductRepository repo,ProductInventoryService inventory){this.jdbc=jdbc;this.repo=repo;this.inventory=inventory;}
    private static final String FROM=" FROM product p LEFT JOIN product_variant v ON v.product_id=p.id AND v.deleted_at IS NULL LEFT JOIN product_inventory_balance b ON b.variant_id=v.id ";
    Query query(Filter f) {
        var where=new StringBuilder("p.deleted_at IS NULL");var params=new HashMap<String,Object>();
        add(where,params,"p.status","parentStatus",f.productStatus());add(where,params,"v.lifecycle_status","status",f.status());add(where,params,"p.product_type","productType",f.productType());
        if(f.variantIds()!=null&&!f.variantIds().isEmpty()){if(f.variantIds().size()>10000)throw new ProductException(400,"EXPORT_LIMIT","最多选择 10000 条");where.append(" AND v.id IN (:selectedIds)");params.put("selectedIds",f.variantIds());}
        String image="(v.main_image_asset_id IS NOT NULL OR (v.main_image_url IS NOT NULL AND v.main_image_url<>''))";
        String supplier="EXISTS (SELECT 1 FROM product_variant_supplier s WHERE s.variant_id=v.id)";
        String material="EXISTS (SELECT 1 FROM product_auxiliary_material m WHERE m.variant_id=v.id)";
        flag(where,image,f.hasImage());flag(where,supplier,f.hasSupplier());flag(where,material,f.hasMaterial());
        if("POSITIVE".equals(f.stock()))where.append(" AND b.quantity>0");
        else if("ZERO".equals(f.stock()))where.append(" AND v.id IS NOT NULL AND COALESCE(b.quantity,0)=0");
        else if("UNINITIALIZED".equals(f.stock()))where.append(" AND v.id IS NOT NULL AND b.variant_id IS NULL");
        String missing=switch(f.missing()==null?"":f.missing()) {case "image"->"NOT "+image;case "supplier"->"NOT "+supplier;case "dimensions"->"(v.item_length_cm IS NULL OR v.item_width_cm IS NULL OR v.item_height_cm IS NULL)";case "weight"->"v.package_weight_grams IS NULL";default->null;};
        if(missing!=null)where.append(" AND v.id IS NOT NULL AND ").append(missing);
        if(f.q()!=null&&!f.q().isBlank()) {
            String expression=switch(f.field()==null?"":f.field()) {
                case "SKU"->"COALESCE(v.sku_code,'')";case "NAME","SKU_NAME"->"COALESCE(v.variant_name,'')";
                case "PRODUCT_CODE"->"COALESCE(p.product_code,'')";case "PRODUCT_NAME"->"COALESCE(p.product_name,'')";
                default->"CONCAT(COALESCE(p.product_name,''),' ',p.product_code,' ',COALESCE(v.variant_name,''),' ',COALESCE(v.sku_code,''),' ',COALESCE(v.specification,''),' ',COALESCE(v.item_dimensions,''),' ',COALESCE(v.package_dimensions,''),' ',COALESCE(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TRIM(CAST(v.item_length_cm AS CHAR(32))))), ''),'×',COALESCE(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TRIM(CAST(v.item_width_cm AS CHAR(32))))), ''),'×',COALESCE(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TRIM(CAST(v.item_height_cm AS CHAR(32))))), ''),' ',COALESCE(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TRIM(CAST(v.package_length_cm AS CHAR(32))))), ''),'×',COALESCE(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TRIM(CAST(v.package_width_cm AS CHAR(32))))), ''),'×',COALESCE(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM TRIM(CAST(v.package_height_cm AS CHAR(32))))), ''))";
            };
            var terms=f.q().lines().map(String::trim).filter(t->!t.isEmpty()).distinct().toList();
            if(terms.size()>100)throw new ProductException(400,"SEARCH_LIMIT","最多输入 100 个搜索项");
            var clauses=new ArrayList<String>();for(int i=0;i<terms.size();i++){String key="q"+i;params.put(key,terms.get(i).toUpperCase(Locale.ROOT));clauses.add(f.exact()?"UPPER("+expression+")=:"+key:"LOCATE(:"+key+",UPPER("+expression+"))>0");}
            where.append(" AND (").append(String.join(" OR ",clauses)).append(")");
        }
        return new Query(where.toString(),params);
    }
    private void add(StringBuilder w,Map<String,Object> p,String column,String key,String value){if(value!=null&&!value.isBlank()){w.append(" AND ").append(column).append("=:").append(key);p.put(key,value);}}
    private void flag(StringBuilder w,String condition,String value){if("true".equals(value))w.append(" AND ").append(condition);else if("false".equals(value))w.append(" AND NOT ").append(condition);}
    @Transactional(readOnly=true)
    Page page(Filter filter,String sort,String direction,int page,int size,boolean suppliers,boolean stock) { return page(filter,sort,direction,page,size,suppliers,stock,null); }
    @Transactional(readOnly=true)
    Page page(Filter filter,String sort,String direction,int page,int size,boolean suppliers,boolean stock,String focusProductId) {
        if(page<0||size<1||size>100)throw new ProductException(400,"PAGE_INVALID","分页参数无效");
        var query=query(filter);
        long total=jdbc.sql("SELECT COUNT(DISTINCT p.id)"+FROM+" WHERE "+query.where()).params(query.params()).query(Long.class).single();
        long skus=jdbc.sql("SELECT COUNT(DISTINCT v.id)"+FROM+" WHERE "+query.where()).params(query.params()).query(Long.class).single();
        int current=Math.min(page,(int)Math.max(0,(total-1)/size));
        String order="sku".equals(sort)?"p.product_name":"p.updated_at";String dir="asc".equals(direction)?" ASC":" DESC";
        if(focusProductId!=null&&!focusProductId.isBlank()){
            var value=jdbc.sql("SELECT "+order.substring(2)+" FROM product WHERE id=:id AND deleted_at IS NULL").param("id",focusProductId).query((r,n)->r.getObject(1)).optional();
            boolean matches=jdbc.sql("SELECT COUNT(*)"+FROM+" WHERE "+query.where()+" AND p.id=:focusId").params(query.params()).param("focusId",focusProductId).query(Long.class).single()>0;
            if(value.isPresent()&&matches){String comparison="asc".equals(direction)?"<":">";
                long preceding=jdbc.sql("SELECT COUNT(DISTINCT p.id)"+FROM+" WHERE "+query.where()+" AND ("+order+comparison+":focusValue OR ("+order+"=:focusValue AND p.id<:focusId))").params(query.params()).param("focusValue",value.get()).param("focusId",focusProductId).query(Long.class).single();current=(int)(preceding/size);
            }
        }
        var ids=jdbc.sql("SELECT DISTINCT p.id,p.product_name,p.updated_at"+FROM+" WHERE "+query.where()+" ORDER BY "+order+dir+",p.id LIMIT :limit OFFSET :offset")
            .params(query.params()).param("limit",size).param("offset",(long)current*size).query((r,n)->r.getString("id")).list();
        var items=repo.findByIds(ids,suppliers);
        var matches=ids.isEmpty()?List.<String>of():jdbc.sql("SELECT DISTINCT v.id"+FROM+" WHERE "+query.where()+" AND p.id IN (:ids) AND v.id IS NOT NULL").params(query.params()).param("ids",ids).query(String.class).list();
        var variants=items.stream().flatMap(p->p.variants().stream()).map(VariantView::id).toList();
        return new Page(items,total,skus,matches,current,size,stock?inventory.balances(variants):List.of());
    }
}
