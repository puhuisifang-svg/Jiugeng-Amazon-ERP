package com.jiugeng.erp.mockdata;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mock")
class MockErpControllerTest {

    private static final String AUTHORIZATION = "Authorization";

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void logsInEachDemoRoleWithSyntheticIdentityAndContractCapabilities() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.source").value("SYNTHETIC"))
                .andExpect(jsonPath("$.token").value("mock-session-admin"))
                .andExpect(jsonPath("$.user.id").value("mock-admin"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"PRODUCT_MATERIAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-session-product-material"))
                .andExpect(jsonPath("$.user.role").value("PRODUCT_MATERIAL"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SELECTION\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-session-selection"))
                .andExpect(jsonPath("$.user.role").value("SELECTION"));
    }

    @Test
    void resolvesCurrentUserFromExplicitSyntheticSessionHeader() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header(AUTHORIZATION, bearer("mock-session-product-material")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.user.id").value("mock-product"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.code").value("MOCK_SESSION_REQUIRED"));

        mockMvc.perform(get("/api/auth/me").header(AUTHORIZATION, bearer("not-a-session")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.source").value("SYNTHETIC"));
    }

    @Test
    void exposesProductAndSkuReadModelWithoutListingMapping() throws Exception {
        mockMvc.perform(get("/api/products").header(AUTHORIZATION, bearer("mock-session-selection")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.source").value("SYNTHETIC"))
                .andExpect(jsonPath("$.items[0].productCode").value("PROD-MOCK-001"))
                .andExpect(jsonPath("$.items[0].skuCount").value(2))
                .andExpect(jsonPath("$.items[0].skus.length()").value(2))
                .andExpect(jsonPath("$.items[0].skus[0].listings").doesNotExist());
    }

    @Test
    void exposesMaterialsInventorySelectionAndWatchReadModels() throws Exception {
        String session = bearer("mock-session-product-material");

        mockMvc.perform(get("/api/materials").header(AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].materialCode").value("MAT-MOCK-001"))
                .andExpect(jsonPath("$.items[0].unit").value("piece"));

        mockMvc.perform(get("/api/inventory/balances").header(AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].warehouseCode").value("WH-MOCK-001"))
                .andExpect(jsonPath("$.items[0].materialName").value("五层瓦楞外箱"))
                .andExpect(jsonPath("$.items[0].quantity").value(860));

        mockMvc.perform(get("/api/selection/candidates").header(AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].asin").value("B0MOCKASIN1"));

        mockMvc.perform(get("/api/watch/events").header(AUTHORIZATION, session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].storeName").value("模拟美国店铺 1"))
                .andExpect(jsonPath("$.items[0].asin").value("B0MOCKASIN4"));
    }

    @Test
    void enforcesInventoryCapabilityForSelectionRole() throws Exception {
        mockMvc.perform(get("/api/inventory/balances")
                        .header(AUTHORIZATION, bearer("mock-session-selection")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.source").value("SYNTHETIC"))
                .andExpect(jsonPath("$.code").value("MOCK_CAPABILITY_DENIED"));
    }

    @Test
    void rejectsLoginWithoutSupportedRole() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.source").value("SYNTHETIC"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createsProductsAndMaterialsAndRejectsCaseInsensitiveDuplicates() throws Exception {
        String productRole = bearer("mock-session-product-material");

        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"PROD-TEST-UNIQUE","name":"模拟新品","skuCodes":["SKU-TEST-A","SKU-TEST-B"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.item.skuCount").value(2))
                .andExpect(jsonPath("$.item.skus[0].listings").doesNotExist());

        mockMvc.perform(post("/api/products")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productCode":"prod-test-unique","name":"重复新品","skuCodes":["SKU-X"]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_CODE_EXISTS"));

        mockMvc.perform(post("/api/materials")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialCode":"MAT-TEST-UNIQUE","name":"模拟缓冲棉","unit":"piece"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.materialCode").value("MAT-TEST-UNIQUE"));

        mockMvc.perform(post("/api/materials")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialCode":"mat-test-unique","name":"重复辅料","unit":"piece"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.source").value("SYNTHETIC"))
                .andExpect(jsonPath("$.code").value("MATERIAL_CODE_EXISTS"));
    }

    @Test
    void keepsInventoryLedgerIdempotentAndPreventsDuplicateOpeningAndNegativeBalance() throws Exception {
        String productRole = bearer("mock-session-product-material");
        mockMvc.perform(post("/api/materials")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialCode":"MAT-INV-UNIQUE","name":"模拟库存测试料","unit":"piece"}
                                """))
                .andExpect(status().isOk());

        String opening = """
                {"requestId":"REQ-OPEN-UNIQUE","materialCode":"MAT-INV-UNIQUE","type":"OPENING","quantity":0,"reason":"零期初"}
                """;

        mockMvc.perform(post("/api/inventory/transactions")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(opening))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.item.quantity").value(0))
                .andExpect(jsonPath("$.balance.quantity").value(0));

        mockMvc.perform(post("/api/inventory/transactions")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(opening))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.balance.quantity").value(0));

        mockMvc.perform(post("/api/inventory/transactions")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"REQ-OPEN-SECOND","materialCode":"MAT-INV-UNIQUE","type":"OPENING","quantity":1,"reason":"重复期初"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPENING_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/inventory/transactions")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"REQ-OUT-TOO-MUCH","materialCode":"MAT-INV-UNIQUE","type":"OUTBOUND","quantity":1,"reason":"超额出库"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.code").value("NEGATIVE_INVENTORY"));

        mockMvc.perform(get("/api/inventory/ledger").header(AUTHORIZATION, productRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].requestId", hasItem("REQ-OPEN-UNIQUE")));
    }

    @Test
    void transitionsCandidatesAndRequiresRejectionReason() throws Exception {
        String selectionRole = bearer("mock-session-selection");

        mockMvc.perform(post("/api/selection/candidates/candidate-mock-002/transition")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REJECTION_REASON_REQUIRED"));

        mockMvc.perform(post("/api/selection/candidates/candidate-mock-002/transition")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REVIEWING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("REVIEWING"));

        mockMvc.perform(post("/api/selection/candidates/candidate-mock-002/transition")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("APPROVED"));

        mockMvc.perform(post("/api/selection/candidates/candidate-mock-002/transition")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\",\"rejectionReason\":\"终态不可变\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_CANDIDATE_TRANSITION"));
    }

    @Test
    void createsCandidateFromAmazonUrlAndRejectsDuplicateAsin() throws Exception {
        String selectionRole = bearer("mock-session-selection");

        mockMvc.perform(post("/api/selection/candidates")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asinOrUrl":"https://www.amazon.com/dp/B0TEST12345?tag=synthetic","title":"模拟手工候选","score":68}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockData").value(true))
                .andExpect(jsonPath("$.item.asin").value("B0TEST12345"))
                .andExpect(jsonPath("$.item.sourceUrl").value("https://www.amazon.com/dp/B0TEST12345"))
                .andExpect(jsonPath("$.item.status").value("NEW"))
                .andExpect(jsonPath("$.item.score").value(68));

        mockMvc.perform(post("/api/selection/candidates")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asinOrUrl":"b0test12345","title":"重复候选"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANDIDATE_ASIN_EXISTS"));

        mockMvc.perform(post("/api/selection/candidates")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asinOrUrl":"https://example.com/dp/B0TEST99999","title":"错误来源"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASIN_OR_URL"));
    }

    @Test
    void updatesCandidateEvaluationAndValidatesRatingAndRole() throws Exception {
        String selectionRole = bearer("mock-session-selection");

        mockMvc.perform(put("/api/selection/candidates/candidate-mock-001/evaluation")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"户外用品","price":31.50,"rating":4.7,"reviewCount":1450,"owner":"模拟负责人","tags":["便携","露营"],"selectionReason":"需求稳定且评价增长"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.category").value("户外用品"))
                .andExpect(jsonPath("$.item.price").value(31.5))
                .andExpect(jsonPath("$.item.rating").value(4.7))
                .andExpect(jsonPath("$.item.reviewCount").value(1450))
                .andExpect(jsonPath("$.item.owner").value("模拟负责人"))
                .andExpect(jsonPath("$.item.tags.length()").value(2))
                .andExpect(jsonPath("$.item.selectionReason").value("需求稳定且评价增长"));

        mockMvc.perform(put("/api/selection/candidates/candidate-mock-001/evaluation")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5.1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(put("/api/selection/candidates/candidate-mock-001/evaluation")
                        .header(AUTHORIZATION, bearer("mock-session-product-material"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"无权修改\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmsWatchEventIdempotentlyAndLinksOneCandidate() throws Exception {
        String selectionRole = bearer("mock-session-selection");

        String firstCandidateId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/watch/events/event-mock-001/confirm")
                                .header(AUTHORIZATION, selectionRole))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.replayed").value(false))
                        .andExpect(jsonPath("$.item.status").value("CONFIRMED"))
                        .andReturn().getResponse().getContentAsString(),
                "$.candidate.id");

        mockMvc.perform(post("/api/watch/events/event-mock-001/confirm")
                        .header(AUTHORIZATION, selectionRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.item.candidateId").value(firstCandidateId))
                .andExpect(jsonPath("$.candidate.id").value(firstCandidateId));
    }

    @Test
    void protectsWatchBaselineDeduplicatesEventsAndTracesCandidateSource() throws Exception {
        String selectionRole = bearer("mock-session-selection");
        String storeId = "watch-store-mock-001";

        mockMvc.perform(post("/api/watch/stores/" + storeId + "/runs")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"INCOMPLETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineChanged").value(false))
                .andExpect(jsonPath("$.item.baselineRunId").doesNotExist())
                .andExpect(jsonPath("$.item.lastIntegrityStatus").value("INCOMPLETE"))
                .andExpect(jsonPath("$.item.lastDecision").value("REJECTED_KEEP_PREVIOUS"));

        String firstBaselineRunId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/watch/stores/" + storeId + "/runs")
                                .header(AUTHORIZATION, selectionRole)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"mode\":\"COMPLETE\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.baselineChanged").value(true))
                        .andExpect(jsonPath("$.events.length()").value(0))
                        .andExpect(jsonPath("$.item.lastReasonCode").value("FIRST_COMPLETE_SNAPSHOT"))
                        .andReturn().getResponse().getContentAsString(),
                "$.item.baselineRunId");

        mockMvc.perform(post("/api/watch/stores/" + storeId + "/runs")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"FAILED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineChanged").value(false))
                .andExpect(jsonPath("$.item.baselineRunId").value(firstBaselineRunId))
                .andExpect(jsonPath("$.item.lastIntegrityStatus").value("FAILED"));

        String completeResponse = mockMvc.perform(post("/api/watch/stores/" + storeId + "/runs")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"COMPLETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineChanged").value(true))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].asin").value("B0NEW00001"))
                .andReturn().getResponse().getContentAsString();
        String eventId = com.jayway.jsonpath.JsonPath.read(completeResponse, "$.events[0].id");
        String sourceRunId = com.jayway.jsonpath.JsonPath.read(completeResponse, "$.events[0].sourceRunId");

        mockMvc.perform(post("/api/watch/stores/" + storeId + "/runs")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"COMPLETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(0));

        mockMvc.perform(post("/api/watch/events/" + eventId + "/confirm")
                        .header(AUTHORIZATION, selectionRole))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidate.sourceStore").value("模拟美国店铺 1"))
                .andExpect(jsonPath("$.candidate.sourceRunId").value(sourceRunId))
                .andExpect(jsonPath("$.candidate.sourceEventId").value(eventId));

        mockMvc.perform(post("/api/watch/stores/" + storeId + "/runs")
                        .header(AUTHORIZATION, bearer("mock-session-product-material"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"COMPLETE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enforcesWriteCapabilitiesAcrossOperationalEndpoints() throws Exception {
        String selectionRole = bearer("mock-session-selection");
        String productRole = bearer("mock-session-product-material");

        mockMvc.perform(post("/api/inventory/transactions")
                        .header(AUTHORIZATION, selectionRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestId":"REQ-DENIED","materialCode":"MAT-MOCK-001","type":"INBOUND","quantity":1,"reason":"应拒绝"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MOCK_CAPABILITY_DENIED"));

        mockMvc.perform(post("/api/selection/candidates/candidate-mock-001/transition")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/selection/candidates")
                        .header(AUTHORIZATION, productRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"asinOrUrl":"B0DENIED1234","title":"无权新增"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/watch/events/event-mock-001/confirm")
                        .header(AUTHORIZATION, productRole))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mockData").value(true));
    }
}
