package com.jiugeng.erp.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Real concurrent transactions; isolated synthetic database, never the running business service. */
@SpringBootTest(properties="spring.datasource.url=${erp.orders.testJdbcUrl:jdbc:h2:mem:order-concurrency;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}")
@AutoConfigureMockMvc @ActiveProfiles("test")
class CustomerOrderConcurrencyTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;@Autowired Clock clock;
    @Test void concurrentArrangementsCannotOverbookAndInvoiceGenerationIsUnique() throws Exception {
        var f=new CustomerOrderControllerTest();f.mvc=mvc;f.mapper=mapper;f.jdbc=jdbc;f.clock=clock;
        String token=f.login("ADMIN"),cp=f.fixture();var order=f.body(f.send(token,CustomerOrderControllerTest.BASE,f.input(cp)).andExpect(status().isOk()));
        String id=order.path("id").asText();f.confirm(token,id);String line=order.path("lines").get(0).path("id").asText();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CyclicBarrier(2);var tasks=new ArrayList<Future<Integer>>();
            for(int i=0;i<2;i++) {String key="parallel-"+i;tasks.add(pool.submit(()->{start.await(10,TimeUnit.SECONDS);return f.arrange(token,id,f.arrange(line,"1"),key).andReturn().getResponse().getStatus();}));}
            assertThat(List.of(tasks.get(0).get(20,TimeUnit.SECONDS),tasks.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("SELECT SUM(quantity) FROM customer_order_allocation WHERE order_id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("1");
            var invoices=new ArrayList<Future<String>>();var invoiceStart=new CyclicBarrier(2);
            for(int i=0;i<2;i++)invoices.add(pool.submit(()->{invoiceStart.await(10,TimeUnit.SECONDS);return f.body(f.send(token,CustomerOrderControllerTest.BASE+"/"+id+"/invoice",Map.of()).andExpect(status().isOk())).path("id").asText();}));
            assertThat(invoices.get(0).get(20,TimeUnit.SECONDS)).isEqualTo(invoices.get(1).get(20,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_order_invoice WHERE order_id=?",Integer.class,id)).isEqualTo(1);
        }
    }
}
