package com.jiugeng.erp.customs;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.scheduling.annotation.Scheduled;
import java.util.Arrays;
class WarehouseSyncTest {
 @Test void refusesBothAutomaticAndManualUpdates(){
  var sync=new WarehouseSync();
  for(boolean automatic:new boolean[]{true,false})assertThatThrownBy(()->sync.start(automatic)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.GONE));
  assertThat(sync.status().automatic()).isFalse();assertThat(sync.status().nextRunAt()).isEmpty();assertThat(sync.status().state()).isEqualTo("DISABLED");
 }
 @Test void hasNoScheduledTaskOrDataSourceDependency(){
  assertThat(Arrays.stream(WarehouseSync.class.getDeclaredMethods()).anyMatch(m->m.isAnnotationPresent(Scheduled.class))).isFalse();
  assertThat(WarehouseSync.class.getDeclaredFields()).isEmpty();
 }
}
