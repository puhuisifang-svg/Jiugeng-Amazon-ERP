package com.jiugeng.erp.watch;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.*;
import org.junit.jupiter.api.Test;
class WatchNoveltyTest {
    private final Instant found=Instant.parse("2026-10-27T08:00:00Z");
    @Test void sixtyDayWindowIsInclusiveAndUsesDiscoveryDay(){
        assertThat(WatchNoveltyTarget.assess("August 28, 2026",found).status()).isEqualTo("CANDIDATE");
        assertThat(WatchNoveltyTarget.assess("August 27, 2026",found).status()).isEqualTo("OLD");
        assertThat(WatchNoveltyTarget.assess("October 27, 2026",found).status()).isEqualTo("CANDIDATE");
        assertThat(WatchNoveltyTarget.assess("October 28, 2026",found).status()).isEqualTo("PENDING");
        assertThat(WatchNoveltyTarget.assess("August 28, 2026",Instant.parse("2026-10-28T06:59:59Z")).status()).isEqualTo("CANDIDATE");
    }
    @Test void missingOrInvalidDatesNeverBecomeSuspected(){
        for(String raw:new String[]{null,"","not a date","February 30, 2026"})assertThat(WatchNoveltyTarget.assess(raw,found).status()).isEqualTo("PENDING");
        assertThat(WatchNoveltyTarget.assess("\u200eSeptember 1, 2026",found).date()).isEqualTo(LocalDate.of(2026,9,1));
    }
}
