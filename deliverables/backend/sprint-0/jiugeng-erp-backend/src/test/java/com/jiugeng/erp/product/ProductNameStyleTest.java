package com.jiugeng.erp.product;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductNameStyleTest {
    private NameStyleSpan span(int start, int end) { return new NameStyleSpan(start, end, NameStyleSpan.Color.RED, true); }
    @Test void trimsOuterWhitespaceWithoutShiftingFormattingOntoWrongText() {
        assertThat(ProductNameStyleService.validate("  名称\n规格  ", List.of(span(5, 7))))
                .containsExactly(span(3, 5));
    }
    @Test void rejectsOverlapOutOfBoundsAndSplitUnicode() {
        for (var spans : List.of(List.of(span(0, 3), span(2, 4)), List.of(span(0, 10)), List.of(span(-1, 2)), List.of(span(2, 2)))) {
            assertThatThrownBy(() -> ProductNameStyleService.validate("名称规格", spans)).isInstanceOf(ProductException.class);
        }
        assertThatThrownBy(() -> ProductNameStyleService.validate("😀名称", List.of(span(1, 3)))).isInstanceOf(ProductException.class);
    }
    @Test void legacyRenameNeverReusesStaleOffsets() {
        String json = "{\"text\":\"旧名称\",\"spans\":[{\"start\":0,\"end\":3,\"color\":\"RED\",\"bold\":true}]}";
        assertThat(ProductNameStyleService.decode(json, "新名称")).isNull();
        assertThat(ProductNameStyleService.decode(json, "旧名称")).containsExactly(span(0, 3));
        assertThat(ProductNameStyleService.decode(null, "名称")).isNull();
    }
}
