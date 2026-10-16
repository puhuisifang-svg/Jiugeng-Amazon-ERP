package com.jiugeng.erp.materialinventory;

import static com.jiugeng.erp.materialinventory.InventoryTypes.BalanceView;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionCommand;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionResult;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionType;
import static com.jiugeng.erp.materialinventory.InventoryTypes.TransactionView;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/inventory")
class InventoryController {
    private final InventoryService service;

    InventoryController(InventoryService service) { this.service = service; }

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('inventory.read')")
    List<BalanceView> balances() { return service.balances(); }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('inventory.read')")
    List<TransactionView> transactions(@RequestParam(required = false) String materialId) {
        return service.transactions(materialId);
    }

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('inventory.write')")
    TransactionResult transact(@Valid @RequestBody TransactionRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.transact(request.command(), actor);
    }

    @PutMapping("/balances/{materialId}/safety-stock")
    @PreAuthorize("hasAuthority('inventory.write')")
    BalanceView updateSafetyStock(@PathVariable String materialId, @Valid @RequestBody SafetyStockRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor) {
        return service.updateSafetyStock(materialId, request.safetyStock(), actor);
    }

    record TransactionRequest(@NotBlank @Size(max = 100) String requestId,
            @NotBlank @Size(max = 36) String materialId,
            @NotNull TransactionType type,
            @Digits(integer = 15, fraction = 0) BigDecimal quantity,
            @NotBlank @Size(max = 32) String unit,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 36) String referenceTransactionId) {
        TransactionCommand command() {
            return new TransactionCommand(requestId, materialId, type, quantity, unit, reason,
                    referenceTransactionId);
        }
    }

    record SafetyStockRequest(@NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 0) BigDecimal safetyStock) {}
}
