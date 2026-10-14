package com.jiugeng.erp.materialinventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.materialinventory.InventoryOpeningService.OpeningCommand;
import com.jiugeng.erp.materialinventory.InventoryTypes.TransactionCommand;
import com.jiugeng.erp.materialinventory.InventoryTypes.TransactionType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryControllerTest {
    private static final String PASSWORD = "Inventory-Test-42";
    private static final String MATERIAL_ID = "00000000-0000-0000-0000-000000000131";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryService service;
    @Autowired InventoryOpeningService openingService;
    private ExecutorService executor;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM inventory_transaction");
        jdbc.update("DELETE FROM inventory_balance");
        jdbc.update("DELETE FROM product_auxiliary_material");
        jdbc.update("DELETE FROM auxiliary_material");
        jdbc.update("DELETE FROM iam_password_reset_challenge");
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM iam_session");
        jdbc.update("DELETE FROM iam_user");
        jdbc.update("""
                INSERT INTO auxiliary_material (id, material_code, material_code_normalized, name, material_type,
                    unit, status, created_by, updated_by)
                VALUES (?, 'MAT-INV', 'MAT-INV', '库存辅料', '包装', 'PCS', 'ACTIVE', 'system', 'system')
                """, MATERIAL_ID);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void shutdownExecutor() { executor.shutdownNow(); }

    @Test
    void writerCanPostImmutableTransactionsUpdateSafetyAndReverse() throws Exception {
        String token = createUserAndLogin("inventory-owner", "PRODUCT_MATERIAL");
        transact(token, command("req-in", "INBOUND", 10, null), 200);
        mvc.perform(put("/api/v1/inventory/balances/{id}/safety-stock", MATERIAL_ID)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("safetyStock", 7))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lowStock").value(false));
        String outbound = transact(token, command("req-out", "OUTBOUND", 4, null), 200);
        String outboundId = objectMapper.readTree(outbound).at("/item/id").asText();
        mvc.perform(get("/api/v1/inventory/balances").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(6))
                .andExpect(jsonPath("$[0].lowStock").value(true));
        transact(token, command("req-reverse", "REVERSAL", null, outboundId), 200);
        transact(token, command("req-repeat-reverse", "REVERSAL", null, outboundId), 409);

        BigDecimal sum = jdbc.queryForObject("SELECT SUM(signed_quantity) FROM inventory_transaction", BigDecimal.class);
        BigDecimal balance = jdbc.queryForObject("SELECT quantity FROM inventory_balance", BigDecimal.class);
        assertThat(balance).isEqualByComparingTo(sum);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE resource_type='INVENTORY'", Integer.class))
                .isEqualTo(4);
    }

    @Test
    void requestIsIdempotentButRejectsPayloadChangesAndNegativeStock() throws Exception {
        String token = createUserAndLogin("inventory-owner", "PRODUCT_MATERIAL");
        String first = transact(token, command("req-idempotent", "INBOUND", 5, null), 200);
        String replay = transact(token, command("req-idempotent", "INBOUND", 5, null), 200);
        assertThat(objectMapper.readTree(first).at("/item/id").asText())
                .isEqualTo(objectMapper.readTree(replay).at("/item/id").asText());
        assertThat(objectMapper.readTree(replay).get("replayed").asBoolean()).isTrue();
        transact(token, command("req-idempotent", "INBOUND", 6, null), 409);
        transact(token, command("req-negative", "OUTBOUND", 6, null), 409);
        transact(token, command("req-opening", "OPENING", 1, null), 400);
        Map<String, Object> wrongUnit = command("req-unit", "INBOUND", 1, null);
        wrongUnit.put("unit", "KG");
        transact(token, wrongUnit, 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_transaction", Integer.class)).isEqualTo(1);
    }

    @Test
    void readAndWriteCapabilitiesAreEnforced() throws Exception {
        String writer = createUserAndLogin("inventory-owner", "PRODUCT_MATERIAL");
        transact(writer, command("req-capability", "INBOUND", 2, null), 200);
        String selector = createUserAndLogin("selector", "SELECTION");
        mvc.perform(get("/api/v1/inventory/transactions").header("Authorization", bearer(selector)))
                .andExpect(status().isForbidden());
        transact(selector, command("req-denied", "INBOUND", 1, null), 403);
    }

    @Test
    void warehouseLockPreventsConcurrentNegativeStockAndOpeningIsSafe() throws Exception {
        AuthenticatedActor actor = actor();
        openingService.postOpening(new OpeningCommand("opening-1", MATERIAL_ID, BigDecimal.TEN, "PCS",
                BigDecimal.ONE, "期初导入"), actor);
        assertThat(openingService.postOpening(new OpeningCommand("opening-1", MATERIAL_ID, BigDecimal.TEN, "PCS",
                BigDecimal.ONE, "期初导入"), actor).replayed()).isTrue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var first = asyncOutbound("concurrent-1", ready, start, actor);
        var second = asyncOutbound("concurrent-2", ready, start, actor);
        ready.await();
        start.countDown();
        var results = java.util.List.of(first.join(), second.join());
        assertThat(results).containsExactlyInAnyOrder("OK", "NEGATIVE_INVENTORY");
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory_balance", BigDecimal.class))
                .isEqualByComparingTo("4");
    }

    private CompletableFuture<String> asyncOutbound(String requestId, CountDownLatch ready, CountDownLatch start,
            AuthenticatedActor actor) {
        return CompletableFuture.supplyAsync(() -> {
            ready.countDown();
            try {
                start.await();
                service.transact(new TransactionCommand(requestId, MATERIAL_ID, TransactionType.OUTBOUND,
                        new BigDecimal("6"), "PCS", "并发出库", null), actor);
                return "OK";
            } catch (MaterialException exception) {
                return exception.code();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
        }, executor);
    }

    private AuthenticatedActor actor() {
        return new AuthenticatedActor() {
            public String userId() { return "system-import"; }
            public String loginName() { return "erp-017-import"; }
            public String displayName() { return "期初导入"; }
        };
    }

    private Map<String, Object> command(String requestId, String type, Integer quantity, String referenceId) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("requestId", requestId);
        value.put("materialId", MATERIAL_ID);
        value.put("type", type);
        value.put("quantity", quantity);
        value.put("unit", "PCS");
        value.put("reason", "ERP-013 test");
        value.put("referenceTransactionId", referenceId);
        return value;
    }

    private String transact(String token, Map<String, Object> command, int expectedStatus) throws Exception {
        return mvc.perform(post("/api/v1/inventory/transactions").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(command)))
                .andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    private String createUserAndLogin(String loginName, String role) throws Exception {
        String id = UUID.randomUUID().toString();
        var encoder = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        jdbc.update("""
                INSERT INTO iam_user
                    (id, login_name, display_name, password_hash, role, status, must_change_password)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', FALSE)
                """, id, loginName, loginName, encoder.encode(PASSWORD), role);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("loginName", loginName, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
    private String bearer(String token) { return "Bearer " + token; }
}
