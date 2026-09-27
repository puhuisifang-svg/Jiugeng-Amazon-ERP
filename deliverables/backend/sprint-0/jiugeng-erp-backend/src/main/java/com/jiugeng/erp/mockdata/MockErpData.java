package com.jiugeng.erp.mockdata;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Profile("mock")
@Component
final class MockErpData {

    static final String SOURCE = "SYNTHETIC";
    static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String WAREHOUSE_CODE = "WH-MOCK-001";
    private static final Pattern ASIN_PATTERN = Pattern.compile("^[A-Z0-9]{10,16}$");
    private static final Pattern AMAZON_PATH_PATTERN = Pattern.compile(
            "/(?:dp|gp/product)/([A-Z0-9]{10,16})(?:[/?]|$)", Pattern.CASE_INSENSITIVE);

    private static final Map<DemoRole, Set<String>> CAPABILITIES = Map.of(
            DemoRole.ADMIN, Set.of(
                    "system.users.manage", "products.read", "products.write",
                    "materials.read", "materials.write", "inventory.read", "inventory.write", "selection.read",
                    "selection.write", "watch.read", "watch.write", "imports.read", "imports.template.download",
                    "imports.validate", "imports.commit", "imports.cancel", "imports.result.download",
                    "audit.read.all", "audit.read.self"),
            DemoRole.PRODUCT_MATERIAL, Set.of(
                    "products.read", "products.write", "materials.read",
                    "materials.write", "inventory.read", "inventory.write", "selection.read", "watch.read",
                    "imports.read", "imports.template.download", "imports.validate", "imports.commit",
                    "imports.cancel", "imports.result.download", "audit.read.self"),
            DemoRole.SELECTION, Set.of(
                    "products.read", "materials.read", "selection.read", "selection.write",
                    "watch.read", "watch.write", "audit.read.self"));

    private static final Map<DemoRole, DemoUser> USERS = Map.of(
            DemoRole.ADMIN, new DemoUser("mock-admin", "模拟系统管理员", DemoRole.ADMIN),
            DemoRole.PRODUCT_MATERIAL,
            new DemoUser("mock-product", "模拟产品辅料专员", DemoRole.PRODUCT_MATERIAL),
            DemoRole.SELECTION, new DemoUser("mock-selection", "模拟选品专员", DemoRole.SELECTION));

    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Map<String, Material> materials = new LinkedHashMap<>();
    private final Map<String, InventoryBalance> balances = new LinkedHashMap<>();
    private final Map<String, StoredTransaction> transactionsByRequest = new LinkedHashMap<>();
    private final Map<String, LedgerEntry> ledger = new LinkedHashMap<>();
    private final Set<String> openingMaterials = new java.util.HashSet<>();
    private final Map<String, SelectionCandidate> candidates = new LinkedHashMap<>();
    private final Map<String, WatchEvent> watchEvents = new LinkedHashMap<>();

    MockErpData() {
        seedProduct("PROD-MOCK-001", "旅行收纳袋套装", List.of("SKU-MOCK-001-BLK", "SKU-MOCK-001-BGE"));
        seedProduct("PROD-MOCK-002", "硅胶厨房夹", List.of("SKU-MOCK-002-GRY"));
        seedMaterial("MAT-MOCK-001", "五层瓦楞外箱", "piece", new BigDecimal("860"));
        seedMaterial("MAT-MOCK-002", "产品标签 60x30", "piece", new BigDecimal("2450"));
        seedMaterial("MAT-MOCK-003", "透明自封袋", "piece", new BigDecimal("96"));

        putCandidate(new SelectionCandidate("candidate-mock-001", "B0MOCKASIN1", "可折叠露营灯",
                CandidateStatus.REVIEWING, 86, null, canonicalAmazonUrl("B0MOCKASIN1"), "户外照明",
                new BigDecimal("29.99"), new BigDecimal("4.6"), 1280, "模拟选品员", List.of("户外", "便携"),
                "模拟需求稳定且便于差异化", CandidateSource.MANUAL, null,
                Instant.parse("2026-09-21T08:00:00Z")));
        putCandidate(new SelectionCandidate("candidate-mock-002", "B0MOCKASIN2", "宠物慢食碗",
                CandidateStatus.NEW, 74, null, canonicalAmazonUrl("B0MOCKASIN2"), "宠物用品",
                new BigDecimal("18.50"), new BigDecimal("4.4"), 630, null, List.of("宠物"), null,
                CandidateSource.WATCH, "模拟美国店铺 3", Instant.parse("2026-09-22T10:15:00Z")));
        putCandidate(new SelectionCandidate("candidate-mock-003", "B0MOCKASIN3", "车载纸巾盒",
                CandidateStatus.REJECTED, 41, "模拟毛利不足", canonicalAmazonUrl("B0MOCKASIN3"), "汽车用品",
                new BigDecimal("12.99"), new BigDecimal("4.1"), 215, "模拟选品员", List.of("车载"),
                "模拟竞争过于集中", CandidateSource.MANUAL, null,
                Instant.parse("2026-09-20T06:00:00Z")));

        putWatchEvent(new WatchEvent("event-mock-001", "模拟美国店铺 1", "B0MOCKASIN4", "可折叠露营灯升级款",
                Instant.parse("2026-09-22T08:30:00Z"), WatchStatus.PENDING_REVIEW, null));
        putWatchEvent(new WatchEvent("event-mock-002", "模拟美国店铺 3", "B0MOCKASIN2", "宠物慢食碗",
                Instant.parse("2026-09-22T10:15:00Z"), WatchStatus.CONFIRMED, "candidate-mock-002"));
    }

    DemoSession login(DemoRole role) {
        DemoUser user = USERS.get(role);
        return new DemoSession(sessionId(role), user, capabilities(role));
    }

    DemoSession session(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MockUnauthorizedException("Missing synthetic bearer token");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        for (DemoRole role : DemoRole.values()) {
            if (sessionId(role).equals(token)) {
                return login(role);
            }
        }
        throw new MockUnauthorizedException("Missing or invalid synthetic demo session");
    }

    void require(DemoSession session, String capability) {
        if (!session.capabilities().contains(capability)) {
            throw new MockForbiddenException("Synthetic role " + session.currentUser().role()
                    + " does not have capability " + capability);
        }
    }

    synchronized List<Product> products() {
        return List.copyOf(products.values());
    }

    synchronized Product createProduct(String productCode, String name, List<String> skuCodes) {
        String key = normalized(productCode);
        if (products.containsKey(key)) {
            throw conflict("PRODUCT_CODE_EXISTS", "Product code already exists (case-insensitive): " + productCode);
        }
        Set<String> uniqueSkuCodes = skuCodes.stream().map(MockErpData::normalized)
                .collect(java.util.stream.Collectors.toSet());
        if (uniqueSkuCodes.size() != skuCodes.size()) {
            throw badRequest("DUPLICATE_SKU_CODE", "skuCodes must be unique (case-insensitive)");
        }
        Product product = product(productCode.trim(), name.trim(), skuCodes);
        products.put(key, product);
        return product;
    }

    synchronized List<Material> materials() {
        return List.copyOf(materials.values());
    }

    synchronized Material createMaterial(String materialCode, String name, String unit) {
        String key = normalized(materialCode);
        if (materials.containsKey(key)) {
            throw conflict("MATERIAL_CODE_EXISTS", "Material code already exists (case-insensitive): " + materialCode);
        }
        Material material = new Material(id("material"), materialCode.trim(), name.trim(), unit.trim(), "ACTIVE");
        materials.put(key, material);
        balances.put(key, new InventoryBalance(WAREHOUSE_CODE, material.materialCode(), material.name(),
                BigDecimal.ZERO, material.unit()));
        return material;
    }

    synchronized List<InventoryBalance> balances() {
        return List.copyOf(balances.values());
    }

    synchronized TransactionResult transact(String requestId, String materialCode, TransactionType type,
                                              BigDecimal quantity, String reason) {
        String materialKey = normalized(materialCode);
        Material material = materials.get(materialKey);
        if (material == null) {
            throw notFound("MATERIAL_NOT_FOUND", "Unknown material code: " + materialCode);
        }

        String requestKey = requestId.trim();
        BigDecimal normalizedQuantity = quantity.stripTrailingZeros();
        TransactionCommand command = new TransactionCommand(materialKey, type, normalizedQuantity, reason.trim());
        StoredTransaction existing = transactionsByRequest.get(requestKey);
        if (existing != null) {
            if (!existing.command().equals(command)) {
                throw conflict("IDEMPOTENCY_CONFLICT", "requestId was already used with a different payload");
            }
            return new TransactionResult(existing.entry(), existing.balance(), true);
        }

        validateQuantity(type, normalizedQuantity);
        if (type == TransactionType.OPENING && openingMaterials.contains(materialKey)) {
            throw conflict("OPENING_ALREADY_EXISTS", "Only one OPENING transaction is allowed per material");
        }

        InventoryBalance current = balances.get(materialKey);
        BigDecimal delta = switch (type) {
            case OPENING, INBOUND -> normalizedQuantity;
            case OUTBOUND -> normalizedQuantity.negate();
            case ADJUSTMENT -> normalizedQuantity;
        };
        BigDecimal nextQuantity = current.quantity().add(delta);
        if (nextQuantity.signum() < 0) {
            throw conflict("NEGATIVE_INVENTORY", "Transaction would make inventory negative");
        }

        LedgerEntry entry = new LedgerEntry(id("ledger"), requestKey, material.materialCode(), type, delta,
                reason.trim(), Instant.now());
        InventoryBalance nextBalance = new InventoryBalance(WAREHOUSE_CODE, material.materialCode(), material.name(),
                nextQuantity, material.unit());
        ledger.put(entry.id(), entry);
        balances.put(materialKey, nextBalance);
        transactionsByRequest.put(requestKey, new StoredTransaction(command, entry, nextBalance));
        if (type == TransactionType.OPENING) {
            openingMaterials.add(materialKey);
        }
        return new TransactionResult(entry, nextBalance, false);
    }

    synchronized List<LedgerEntry> ledger() {
        return List.copyOf(ledger.values());
    }

    synchronized List<SelectionCandidate> candidates() {
        return List.copyOf(candidates.values());
    }

    synchronized SelectionCandidate createCandidate(String asinOrUrl, String title, Integer score) {
        String asin = extractAsin(asinOrUrl);
        boolean duplicate = candidates.values().stream().anyMatch(item -> item.asin().equalsIgnoreCase(asin));
        if (duplicate) {
            throw conflict("CANDIDATE_ASIN_EXISTS", "A candidate already exists for ASIN: " + asin);
        }
        SelectionCandidate candidate = new SelectionCandidate(id("candidate"), asin, title.trim(),
                CandidateStatus.NEW, score == null ? 50 : score, null, canonicalAmazonUrl(asin), null, null, null,
                null, null, List.of(), null, CandidateSource.MANUAL, null, Instant.now());
        putCandidate(candidate);
        return candidate;
    }

    synchronized SelectionCandidate updateCandidateEvaluation(String candidateId, String category, BigDecimal price,
                                                               BigDecimal rating, Integer reviewCount, String owner,
                                                               List<String> tags, String selectionReason) {
        SelectionCandidate current = candidates.get(candidateId);
        if (current == null) {
            throw notFound("CANDIDATE_NOT_FOUND", "Unknown candidate: " + candidateId);
        }
        List<String> normalizedTags = tags == null ? List.of() : tags.stream()
                .map(String::trim).filter(tag -> !tag.isBlank()).distinct().toList();
        SelectionCandidate updated = new SelectionCandidate(current.id(), current.asin(), current.title(),
                current.status(), current.score(), current.rejectionReason(), current.sourceUrl(), optional(category),
                price, rating, reviewCount, optional(owner), normalizedTags, optional(selectionReason),
                current.source(), current.sourceStore(), current.discoveredAt());
        candidates.put(updated.id(), updated);
        return updated;
    }

    synchronized SelectionCandidate transitionCandidate(String candidateId, CandidateStatus status,
                                                         String rejectionReason) {
        SelectionCandidate current = candidates.get(candidateId);
        if (current == null) {
            throw notFound("CANDIDATE_NOT_FOUND", "Unknown candidate: " + candidateId);
        }
        if (!allowedTransitions(current.status()).contains(status)) {
            throw conflict("INVALID_CANDIDATE_TRANSITION",
                    "Cannot transition candidate from " + current.status() + " to " + status);
        }
        String normalizedReason = rejectionReason == null ? null : rejectionReason.trim();
        if (status == CandidateStatus.REJECTED && (normalizedReason == null || normalizedReason.isBlank())) {
            throw badRequest("REJECTION_REASON_REQUIRED", "rejectionReason is required when rejecting a candidate");
        }
        if (status != CandidateStatus.REJECTED && normalizedReason != null && !normalizedReason.isBlank()) {
            throw badRequest("REJECTION_REASON_NOT_ALLOWED", "rejectionReason is only allowed for REJECTED status");
        }
        SelectionCandidate updated = new SelectionCandidate(current.id(), current.asin(), current.title(), status,
                current.score(), status == CandidateStatus.REJECTED ? normalizedReason : null, current.sourceUrl(),
                current.category(), current.price(), current.rating(), current.reviewCount(), current.owner(),
                current.tags(), current.selectionReason(), current.source(), current.sourceStore(),
                current.discoveredAt());
        candidates.put(updated.id(), updated);
        return updated;
    }

    synchronized List<WatchEvent> watchEvents() {
        return List.copyOf(watchEvents.values());
    }

    synchronized WatchConfirmation confirmWatchEvent(String eventId) {
        WatchEvent event = watchEvents.get(eventId);
        if (event == null) {
            throw notFound("WATCH_EVENT_NOT_FOUND", "Unknown watch event: " + eventId);
        }
        if (event.candidateId() != null) {
            return new WatchConfirmation(event, candidates.get(event.candidateId()), true);
        }
        SelectionCandidate candidate = candidates.values().stream()
                .filter(item -> item.asin().equalsIgnoreCase(event.asin()))
                .findFirst()
                .orElseGet(() -> {
                    SelectionCandidate created = new SelectionCandidate(id("candidate"), event.asin(), event.title(),
                            CandidateStatus.NEW, 70, null, canonicalAmazonUrl(event.asin()), null, null, null, null,
                            null, List.of(), null, CandidateSource.WATCH, event.storeName(), event.detectedAt());
                    putCandidate(created);
                    return created;
                });
        WatchEvent confirmed = new WatchEvent(event.id(), event.storeName(), event.asin(), event.title(),
                event.detectedAt(), WatchStatus.CONFIRMED, candidate.id());
        watchEvents.put(confirmed.id(), confirmed);
        return new WatchConfirmation(confirmed, candidate, false);
    }

    private void seedProduct(String code, String name, List<String> skuCodes) {
        products.put(normalized(code), product(code, name, skuCodes));
    }

    private Product product(String code, String name, List<String> skuCodes) {
        List<Sku> skus = skuCodes.stream().map(skuCode -> new Sku(skuCode.trim())).toList();
        return new Product(id("product"), code, name, "ACTIVE", skus.size(), skus);
    }

    private void seedMaterial(String code, String name, String unit, BigDecimal quantity) {
        Material material = new Material(id("material"), code, name, unit, "ACTIVE");
        String key = normalized(code);
        materials.put(key, material);
        InventoryBalance openingBalance = new InventoryBalance(WAREHOUSE_CODE, code, name, quantity, unit);
        balances.put(key, openingBalance);
        String requestId = "SEED-OPENING-" + key;
        LedgerEntry entry = new LedgerEntry(id("ledger"), requestId, code, TransactionType.OPENING, quantity,
                "Synthetic seed opening", Instant.parse("2026-09-22T00:00:00Z"));
        ledger.put(entry.id(), entry);
        transactionsByRequest.put(requestId,
                new StoredTransaction(new TransactionCommand(key, TransactionType.OPENING, quantity,
                        "Synthetic seed opening"), entry, openingBalance));
        openingMaterials.add(key);
    }

    private void putCandidate(SelectionCandidate candidate) {
        candidates.put(candidate.id(), candidate);
    }

    private void putWatchEvent(WatchEvent event) {
        watchEvents.put(event.id(), event);
    }

    private static void validateQuantity(TransactionType type, BigDecimal quantity) {
        if (quantity == null) {
            throw badRequest("INVALID_QUANTITY", "quantity is required");
        }
        switch (type) {
            case OPENING -> {
                if (quantity.signum() < 0) {
                    throw badRequest("INVALID_QUANTITY", "OPENING quantity must be zero or positive");
                }
            }
            case INBOUND, OUTBOUND -> {
                if (quantity.signum() <= 0) {
                    throw badRequest("INVALID_QUANTITY", type + " quantity must be positive");
                }
            }
            case ADJUSTMENT -> {
                if (quantity.signum() == 0) {
                    throw badRequest("INVALID_QUANTITY", "ADJUSTMENT quantity must be non-zero");
                }
            }
        }
    }

    private static Set<CandidateStatus> allowedTransitions(CandidateStatus status) {
        return switch (status) {
            case NEW -> Set.of(CandidateStatus.REVIEWING, CandidateStatus.REJECTED);
            case REVIEWING -> Set.of(CandidateStatus.APPROVED, CandidateStatus.REJECTED);
            case APPROVED, REJECTED -> Set.of();
        };
    }

    private static String normalized(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String extractAsin(String asinOrUrl) {
        String value = asinOrUrl.trim();
        String normalizedValue = normalized(value);
        if (ASIN_PATTERN.matcher(normalizedValue).matches()) {
            return normalizedValue;
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("amazon.com")
                    || host.toLowerCase(Locale.ROOT).endsWith(".amazon.com"))) {
                throw new IllegalArgumentException("Unsupported host");
            }
            Matcher matcher = AMAZON_PATH_PATTERN.matcher(uri.getPath().toUpperCase(Locale.ROOT) + "/");
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (IllegalArgumentException ignored) {
            // Converted to a stable business validation error below.
        }
        throw badRequest("INVALID_ASIN_OR_URL", "Enter an ASIN or a standard amazon.com /dp/ URL");
    }

    private static String canonicalAmazonUrl(String asin) {
        return "https://www.amazon.com/dp/" + asin.toUpperCase(Locale.ROOT);
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String sessionId(DemoRole role) {
        return "mock-session-" + role.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static List<String> capabilities(DemoRole role) {
        return CAPABILITIES.get(role).stream().sorted().toList();
    }

    private static MockBusinessException badRequest(String code, String detail) {
        return new MockBusinessException(HttpStatus.BAD_REQUEST, code, detail);
    }

    private static MockBusinessException conflict(String code, String detail) {
        return new MockBusinessException(HttpStatus.CONFLICT, code, detail);
    }

    private static MockBusinessException notFound(String code, String detail) {
        return new MockBusinessException(HttpStatus.NOT_FOUND, code, detail);
    }

    enum DemoRole { ADMIN, PRODUCT_MATERIAL, SELECTION }
    enum TransactionType { OPENING, INBOUND, OUTBOUND, ADJUSTMENT }
    enum CandidateStatus { NEW, REVIEWING, APPROVED, REJECTED }
    enum CandidateSource { MANUAL, WATCH }
    enum WatchStatus { PENDING_REVIEW, CONFIRMED }

    record DemoUser(String id, String displayName, DemoRole role) {}
    record DemoSession(String sessionId, DemoUser currentUser, List<String> capabilities) {}
    record Sku(String skuCode) {}
    record Product(String id, String productCode, String name, String status, int skuCount, List<Sku> skus) {}
    record Material(String id, String materialCode, String name, String unit, String status) {}
    record InventoryBalance(String warehouseCode, String materialCode, String materialName, BigDecimal quantity,
                            String unit) {}
    record LedgerEntry(String id, String requestId, String materialCode, TransactionType type, BigDecimal quantity,
                       String reason, Instant occurredAt) {}
    record TransactionCommand(String materialKey, TransactionType type, BigDecimal quantity, String reason) {}
    record StoredTransaction(TransactionCommand command, LedgerEntry entry, InventoryBalance balance) {}
    record TransactionResult(LedgerEntry item, InventoryBalance balance, boolean replayed) {}
    record SelectionCandidate(String id, String asin, String title, CandidateStatus status, int score,
                              String rejectionReason, String sourceUrl, String category, BigDecimal price,
                              BigDecimal rating, Integer reviewCount, String owner, List<String> tags,
                              String selectionReason, CandidateSource source, String sourceStore,
                              Instant discoveredAt) {}
    record WatchEvent(String id, String storeName, String asin, String title, Instant detectedAt, WatchStatus status,
                      String candidateId) {}
    record WatchConfirmation(WatchEvent item, SelectionCandidate candidate, boolean replayed) {}
}
