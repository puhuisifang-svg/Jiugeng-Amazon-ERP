package com.jiugeng.erp.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuditTrail;
import com.jiugeng.erp.iam.AuthenticatedActor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Saved within the aggregate's permission-checked, optimistic-locking transaction. */
@Service
class ProductNameStyleService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final AuditTrail audit;
    ProductNameStyleService(JdbcClient jdbc, AuditTrail audit) { this.jdbc = jdbc; this.audit = audit; }
    private record Stored(String text, List<NameStyleSpan> spans) {}
    private record Existing(boolean highlighted, String styles) {}

    static List<NameStyleSpan> decode(String json, String name) {
        if (json == null || json.isBlank()) return null;
        try {
            var stored = JSON.readValue(json, Stored.class);
            // Legacy imports may rename a SKU without supplying formatting. Never apply stale offsets.
            return Objects.equals(stored.text(), name) ? stored.spans() : null;
        } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid stored SKU name formatting", e); }
    }

    static List<NameStyleSpan> validate(String rawName, List<NameStyleSpan> spans) {
        if (spans.size() > 200) throw invalid();
        int previousEnd = 0;
        int trimStart = rawName.indexOf(rawName.trim());
        int trimEnd = trimStart + rawName.trim().length();
        List<NameStyleSpan> normalized = new ArrayList<>();
        for (var span : spans) {
            if (span == null || span.start() < previousEnd || span.end() <= span.start() || span.end() > rawName.length()
                    || span.color() == null || span.bold() == null || splitsSurrogate(rawName, span.start()) || splitsSurrogate(rawName, span.end())) throw invalid();
            previousEnd = span.end();
            int start = Math.max(trimStart, span.start()), end = Math.min(trimEnd, span.end());
            if (end > start) normalized.add(new NameStyleSpan(start - trimStart, end - trimStart, span.color(), span.bold()));
        }
        return List.copyOf(normalized);
    }
    private static boolean splitsSurrogate(String text, int offset) {
        return offset > 0 && offset < text.length() && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset));
    }
    private static ProductException invalid() { return new ProductException(400, "SKU_NAME_STYLE_INVALID", "名称格式范围无效，请重新选择文字设置格式"); }

    @Transactional(propagation = Propagation.MANDATORY)
    void save(String productId, String variantId, String rawName, Boolean highlighted, List<NameStyleSpan> spans, AuthenticatedActor actor) {
        var before = jdbc.sql("SELECT name_highlighted,name_styles FROM product_variant WHERE id=:id AND product_id=:product")
                .param("id", variantId).param("product", productId)
                .query((r, n) -> new Existing(r.getBoolean(1), r.getString(2))).single();
        boolean nextHighlight = highlighted == null ? before.highlighted() : highlighted;
        String nextStyles = before.styles();
        if (spans != null) {
            try { nextStyles = JSON.writeValueAsString(new Stored(rawName.trim(), validate(rawName, spans))); }
            catch (JsonProcessingException e) { throw new IllegalStateException(e); }
        } else if (highlighted != null && highlighted != before.highlighted()) {
            nextStyles = null; // Explicit legacy whole-name toggle replaces partial formatting.
        }
        if (before.highlighted() == nextHighlight && Objects.equals(before.styles(), nextStyles)) return;
        jdbc.sql("UPDATE product_variant SET name_highlighted=:highlighted,name_styles=:styles WHERE id=:id AND product_id=:product")
                .param("highlighted", nextHighlight).param("styles", nextStyles).param("id", variantId).param("product", productId).update();
        audit.record(new AuditTrail.BusinessOperation(actor.userId(), actor.loginName(), actor.displayName(),
                "PRODUCT_NAME_STYLE_UPDATE", "PRODUCT", productId, true,
                Map.of("variantId", variantId, "nameHighlighted", before.highlighted(), "nameStyles", Objects.toString(before.styles(), "")),
                Map.of("variantId", variantId, "nameHighlighted", nextHighlight, "nameStyles", Objects.toString(nextStyles, "")), "修改 SKU 名称文字格式"));
    }
}
