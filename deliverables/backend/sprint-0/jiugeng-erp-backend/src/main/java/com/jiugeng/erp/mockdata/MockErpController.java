package com.jiugeng.erp.mockdata;

import java.math.BigDecimal;
import java.util.List;

import com.jiugeng.erp.mockdata.MockErpData.CandidateStatus;
import com.jiugeng.erp.mockdata.MockErpData.CandidateDetail;
import com.jiugeng.erp.mockdata.MockErpData.DemoRole;
import com.jiugeng.erp.mockdata.MockErpData.DemoSession;
import com.jiugeng.erp.mockdata.MockErpData.InventoryBalance;
import com.jiugeng.erp.mockdata.MockErpData.LedgerEntry;
import com.jiugeng.erp.mockdata.MockErpData.Material;
import com.jiugeng.erp.mockdata.MockErpData.Product;
import com.jiugeng.erp.mockdata.MockErpData.SelectionCandidate;
import com.jiugeng.erp.mockdata.MockErpData.TransactionResult;
import com.jiugeng.erp.mockdata.MockErpData.TransactionType;
import com.jiugeng.erp.mockdata.MockErpData.WatchConfirmation;
import com.jiugeng.erp.mockdata.MockErpData.WatchEvent;
import com.jiugeng.erp.mockdata.MockErpData.WatchRunMode;
import com.jiugeng.erp.mockdata.MockErpData.WatchRunResult;
import com.jiugeng.erp.mockdata.MockErpData.WatchStore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("mock")
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = { "http://localhost:5173", "http://127.0.0.1:5173" })
class MockErpController {

    private final MockErpData data;

    MockErpController(MockErpData data) {
        this.data = data;
    }

    @PostMapping("/auth/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        DemoSession session = data.login(request.role());
        return new LoginResponse(session.sessionId(), session.currentUser(), true, MockErpData.SOURCE);
    }

    @GetMapping("/auth/me")
    MeResponse me(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        return new MeResponse(session.currentUser(), true, MockErpData.SOURCE);
    }

    @GetMapping("/products")
    SyntheticList<Product> products(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "products.read");
        return SyntheticList.of(data.products());
    }

    @PostMapping("/products")
    SyntheticItem<Product> createProduct(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @Valid @RequestBody CreateProductRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "products.write");
        return SyntheticItem.of(data.createProduct(request.productCode(), request.name(), request.skuCodes()));
    }

    @GetMapping("/materials")
    SyntheticList<Material> materials(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "materials.read");
        return SyntheticList.of(data.materials());
    }

    @PostMapping("/materials")
    SyntheticItem<Material> createMaterial(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @Valid @RequestBody CreateMaterialRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "materials.write");
        return SyntheticItem.of(data.createMaterial(request.materialCode(), request.name(), request.unit()));
    }

    @GetMapping("/inventory/balances")
    SyntheticList<InventoryBalance> balances(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "inventory.read");
        return SyntheticList.of(data.balances());
    }

    @PostMapping("/inventory/transactions")
    TransactionResponse transact(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @Valid @RequestBody InventoryTransactionRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "inventory.write");
        TransactionResult result = data.transact(request.requestId(), request.materialCode(), request.type(),
                request.quantity(), request.reason());
        return new TransactionResponse(result.item(), result.balance(), result.replayed(), true, MockErpData.SOURCE);
    }

    @GetMapping("/inventory/ledger")
    SyntheticList<LedgerEntry> ledger(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "inventory.read");
        return SyntheticList.of(data.ledger());
    }

    @GetMapping("/selection/candidates")
    SyntheticList<SelectionCandidate> candidates(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "selection.read");
        return SyntheticList.of(data.candidates());
    }

    @PostMapping("/selection/candidates")
    SyntheticItem<SelectionCandidate> createCandidate(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @Valid @RequestBody CreateCandidateRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "selection.write");
        return SyntheticItem.of(data.createCandidate(request.asinOrUrl(), request.title(), request.score()));
    }

    @GetMapping("/selection/candidates/{candidateId}")
    SyntheticItem<CandidateDetail> candidateDetail(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String candidateId) {
        DemoSession session = data.session(authorization);
        data.require(session, "selection.read");
        return SyntheticItem.of(data.candidateDetail(candidateId));
    }

    @PostMapping("/selection/candidates/{candidateId}/notes")
    SyntheticItem<CandidateDetail> addCandidateNote(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String candidateId,
            @Valid @RequestBody CandidateNoteRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "selection.write");
        return SyntheticItem.of(data.addCandidateNote(candidateId, request.content(), session.currentUser()));
    }

    @PostMapping("/selection/candidates/{candidateId}/transition")
    SyntheticItem<SelectionCandidate> transitionCandidate(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String candidateId,
            @Valid @RequestBody CandidateTransitionRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "selection.write");
        return SyntheticItem.of(data.transitionCandidate(candidateId, request.status(), request.rejectionReason()));
    }

    @PutMapping("/selection/candidates/{candidateId}/evaluation")
    SyntheticItem<SelectionCandidate> updateCandidateEvaluation(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String candidateId,
            @Valid @RequestBody CandidateEvaluationRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "selection.write");
        return SyntheticItem.of(data.updateCandidateEvaluation(candidateId, request.category(), request.price(),
                request.rating(), request.reviewCount(), request.owner(), request.tags(), request.selectionReason()));
    }

    @GetMapping("/watch/events")
    SyntheticList<WatchEvent> watchEvents(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "watch.read");
        return SyntheticList.of(data.watchEvents());
    }

    @GetMapping("/watch/stores")
    SyntheticList<WatchStore> watchStores(@RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization) {
        DemoSession session = data.session(authorization);
        data.require(session, "watch.read");
        return SyntheticList.of(data.watchStores());
    }

    @PostMapping("/watch/stores/{storeId}/runs")
    WatchRunResponse runWatchStore(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String storeId,
            @Valid @RequestBody WatchRunRequest request) {
        DemoSession session = data.session(authorization);
        data.require(session, "watch.write");
        WatchRunResult result = data.runWatchStore(storeId, request.mode());
        return new WatchRunResponse(result.item(), result.events(), result.baselineChanged(), true,
                MockErpData.SOURCE);
    }

    @PostMapping("/watch/events/{eventId}/confirm")
    WatchConfirmationResponse confirmWatchEvent(
            @RequestHeader(MockErpData.AUTHORIZATION_HEADER) String authorization,
            @org.springframework.web.bind.annotation.PathVariable String eventId) {
        DemoSession session = data.session(authorization);
        data.require(session, "watch.write");
        WatchConfirmation result = data.confirmWatchEvent(eventId);
        return new WatchConfirmationResponse(result.item(), result.candidate(), result.replayed(), true,
                MockErpData.SOURCE);
    }

    record LoginRequest(@NotNull DemoRole role) {
    }

    record CreateProductRequest(
            @NotBlank @Size(max = 64) String productCode,
            @NotBlank @Size(max = 200) String name,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 64) String> skuCodes) {
    }

    record CreateMaterialRequest(
            @NotBlank @Size(max = 64) String materialCode,
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 32) String unit) {
    }

    record InventoryTransactionRequest(
            @NotBlank @Size(max = 100) String requestId,
            @NotBlank @Size(max = 64) String materialCode,
            @NotNull TransactionType type,
            @NotNull BigDecimal quantity,
            @NotBlank @Size(max = 500) String reason) {
    }

    record CandidateTransitionRequest(@NotNull CandidateStatus status, @Size(max = 500) String rejectionReason) {
    }

    record CreateCandidateRequest(
            @NotBlank @Size(max = 500) String asinOrUrl,
            @NotBlank @Size(max = 300) String title,
            @Min(0) @Max(100) Integer score) {
    }

    record CandidateNoteRequest(@NotBlank @Size(max = 1000) String content) {
    }

    record CandidateEvaluationRequest(
            @Size(max = 100) String category,
            @DecimalMin("0.0") BigDecimal price,
            @DecimalMin("0.0") @DecimalMax("5.0") BigDecimal rating,
            @PositiveOrZero Integer reviewCount,
            @Size(max = 100) String owner,
            @Size(max = 10) List<@NotBlank @Size(max = 30) String> tags,
            @Size(max = 500) String selectionReason) {
    }

    record WatchRunRequest(@NotNull WatchRunMode mode) {
    }

    record LoginResponse(String token, MockErpData.DemoUser user, boolean mockData, String source) {
    }

    record MeResponse(MockErpData.DemoUser user, boolean mockData, String source) {
    }

    record SyntheticItem<T>(T item, boolean mockData, String source) {
        static <T> SyntheticItem<T> of(T item) {
            return new SyntheticItem<>(item, true, MockErpData.SOURCE);
        }
    }

    record TransactionResponse(
            LedgerEntry item,
            InventoryBalance balance,
            boolean replayed,
            boolean mockData,
            String source) {
    }

    record WatchConfirmationResponse(
            WatchEvent item,
            SelectionCandidate candidate,
            boolean replayed,
            boolean mockData,
            String source) {
    }

    record WatchRunResponse(
            WatchStore item,
            List<WatchEvent> events,
            boolean baselineChanged,
            boolean mockData,
            String source) {
    }

    record SyntheticList<T>(List<T> items, boolean mockData, String source) {
        static <T> SyntheticList<T> of(List<T> items) {
            return new SyntheticList<>(items, true, MockErpData.SOURCE);
        }
    }
}
