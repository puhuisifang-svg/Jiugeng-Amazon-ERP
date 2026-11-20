package com.jiugeng.erp.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** Shared declaration snapshot codec and validation, independent of its owning catalog. */
public final class CustomsProfiles {
    private CustomsProfiles() {}
    private static final ObjectMapper JSON=new ObjectMapper();
    public static final class ValidationException extends RuntimeException {
        private final String code;
        public ValidationException(String code,String message){super(message);this.code=code;}
        public String code(){return code;}
    }
    public static CustomsProfile decode(String text,long version) {
        if(text==null) return null;
        try {
            var node=JSON.readTree(text); ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("version",version);
            return JSON.treeToValue(node,CustomsProfile.class);
        } catch(JsonProcessingException e) { throw new IllegalStateException("Invalid stored customs profile",e); }
    }
    public static String encode(CustomsProfile value) {
        try { return JSON.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Cannot encode customs profile",e); }
    }
    private static CustomsProfile structured(CustomsProfile value){
        if(value.elements()==null)return value;
        var schema=value.elementFields()==null?com.jiugeng.erp.hs.DeclarationSchema.LEGACY:com.jiugeng.erp.hs.DeclarationSchema.validate(value.elementFields());
        if(value.elementFields()!=null&&!Objects.equals(value.chinaCode(),value.schemaCode()))throw new ValidationException("CUSTOMS_SCHEMA_MISMATCH","HS编码已变化，请等待申报要素查询成功后再保存");
        Set<String> allowed=new HashSet<>();for(var f:schema)allowed.add(f.key());
        if(!allowed.containsAll(value.elements().keySet()))throw new ValidationException("CUSTOMS_ELEMENTS_INVALID","申报要素字段无效");
        var fields=new LinkedHashMap<String,String>();
        for(var f:schema){String v=Objects.toString(value.elements().get(f.key()),"").trim();if(v.length()>(f.key().equals("other")?8000:1000)||v.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new ValidationException("CUSTOMS_ELEMENTS_INVALID","申报要素内容过长或格式无效");fields.put(f.key(),v);}
        String text=com.jiugeng.erp.hs.DeclarationSchema.serialize(schema,fields);if(text.length()>16000)throw new ValidationException("CUSTOMS_ELEMENTS_INVALID","申报要素总长度不能超过16000字");
        try{var node=JSON.valueToTree(value);((com.fasterxml.jackson.databind.node.ObjectNode)node).set("elements",JSON.valueToTree(fields));((com.fasterxml.jackson.databind.node.ObjectNode)node).put("declarationElements",text);return JSON.treeToValue(node,CustomsProfile.class);}catch(JsonProcessingException e){throw new IllegalStateException(e);}
    }
    public static CustomsProfile normalize(CustomsProfile value) {
        value=structured(value);
        if(value.netWeightKg()!=null && value.grossWeightKg()!=null && value.netWeightKg().compareTo(value.grossWeightKg())>0)
            throw new ValidationException("CUSTOMS_WEIGHT_INVALID","毛重不能小于净重");
        if(value.elements()!=null&&value.state()==CustomsProfile.State.REVIEWED&&java.util.stream.Stream.of(value.nameZh(),value.chinaCode()).anyMatch(v->v==null||v.isBlank()))throw new ValidationException("CUSTOMS_REVIEW_INCOMPLETE","标记资料已核对前，请填写HS和品名");
        if(value.elements()==null && value.state()==CustomsProfile.State.REVIEWED && java.util.stream.Stream.of(value.nameZh(),value.nameEn(),value.composition(),value.usage(),value.originCountry(),value.unit(),value.chinaCode(),value.declarationElements()).anyMatch(v->v==null||v.isBlank()))
            throw new ValidationException("CUSTOMS_REVIEW_INCOMPLETE","标记资料已核对前，请填写中英文品名、材质、用途、原产国、申报单位、中国商品编码及申报要素");
        return value;
    }
}
