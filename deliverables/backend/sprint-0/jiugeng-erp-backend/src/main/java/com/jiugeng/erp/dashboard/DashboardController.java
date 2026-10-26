package com.jiugeng.erp.dashboard;
import jakarta.validation.constraints.Digits;

import static com.jiugeng.erp.dashboard.DashboardTypes.*;

import com.jiugeng.erp.iam.AuthenticatedActor;
import com.jiugeng.erp.iam.AuditHistoryLookup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@Profile("!mock")
@RequestMapping("/api/v1")
class DashboardController {
    private final DashboardService service;
    DashboardController(DashboardService service){this.service=service;}

    @GetMapping("/dashboard/today-shipments") @PreAuthorize("hasAuthority('dashboard.read')")
    HomeSlice<TodayShipment> homeToday(){return service.homeToday();}
    @GetMapping("/dashboard/pending-shipments") @PreAuthorize("hasAuthority('dashboard.read')")
    HomeSlice<PendingShipment> homePendingShipments(){return service.homePendingShipments();}
    @GetMapping("/dashboard/pending-products") @PreAuthorize("hasAuthority('dashboard.read')")
    HomeSlice<PendingProduct> homePendingProducts(){return service.homePendingProducts();}

    @GetMapping("/dashboard-maintenance/today-shipments") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    Page<TodayShipment> todayList(@RequestParam(required=false) @Size(max=128) String keyword,
            @RequestParam(required=false) @Size(max=36) String orderId,
            @RequestParam(required=false) TodayStatus status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return service.todayPage(keyword,status,from,to,page,size,orderId);}

    @PostMapping("/dashboard-maintenance/today-shipments") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    TodayShipment createToday(@Valid @RequestBody ShipmentRequest request,@RequestHeader("Idempotency-Key") String key,
            @AuthenticationPrincipal AuthenticatedActor actor){return service.createToday(request.input(),key,actor);}

    @PatchMapping("/dashboard-maintenance/today-shipments/{id}") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    TodayShipment updateToday(@PathVariable String id,@Valid @RequestBody ShipmentUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor){return service.updateToday(id,request.shipment().input(),
                    request.version(),request.correctionReason(),actor);}

    @PostMapping("/dashboard-maintenance/today-shipments/{id}/archive") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    TodayShipment archiveToday(@PathVariable String id,@Valid @RequestBody StateRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.archiveToday(id,request.version(),request.reason(),key,actor);}
    @PostMapping("/dashboard-maintenance/today-shipments/{id}/transfer-to-pending")
    @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    PendingShipment transferToPending(@PathVariable String id,@Valid @RequestBody StateRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.transferToPending(id,request.version(),request.reason(),key,actor);}

    @GetMapping("/dashboard-maintenance/today-shipments/{id}/history") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    List<AuditHistoryLookup.HistoryEvent> todayHistory(@PathVariable String id){return service.history("TODAY_SHIPMENT",id);}

    @GetMapping("/dashboard-maintenance/pending-shipments") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    Page<PendingShipment> pendingShipmentList(@RequestParam(required=false) @Size(max=128) String keyword,
            @RequestParam(required=false) @Size(max=36) String orderId,
            @RequestParam(required=false) PendingShipmentStatus status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return service.pendingShipmentPage(keyword,status,from,to,page,size,orderId);}

    @PostMapping("/dashboard-maintenance/pending-shipments") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    PendingShipment createPendingShipment(@Valid @RequestBody ShipmentRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.createPendingShipment(request.input(),key,actor);}

    @PatchMapping("/dashboard-maintenance/pending-shipments/{id}") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    PendingShipment updatePendingShipment(@PathVariable String id,@Valid @RequestBody ShipmentUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor){return service.updatePendingShipment(id,
                    request.shipment().input(),request.version(),actor);}

    @PostMapping("/dashboard-maintenance/pending-shipments/{id}/archive") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    PendingShipment archivePendingShipment(@PathVariable String id,@Valid @RequestBody StateRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.archivePendingShipment(id,request.version(),request.reason(),key,actor);}
    @GetMapping("/dashboard-maintenance/pending-shipments/{id}/history") @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    List<AuditHistoryLookup.HistoryEvent> pendingShipmentHistory(@PathVariable String id){return service.history("PENDING_SHIPMENT",id);}

    @PostMapping("/dashboard-maintenance/pending-shipments/{id}/transfer-to-today")
    @PreAuthorize("hasAuthority('dashboard.shipments.manage')")
    TodayShipment transfer(@PathVariable String id,@Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.transfer(id,request.quantity(),request.boxCount(),request.remark(),request.latestInstant(),
                request.imageId(),request.version(),key,actor);}

    @GetMapping("/dashboard-maintenance/pending-products") @PreAuthorize("hasAuthority('dashboard.pending-products.manage')")
    Page<PendingProduct> pendingProductList(@RequestParam(required=false) @Size(max=128) String keyword,
            @RequestParam(required=false) PendingProductStatus status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        return service.pendingProductPage(keyword,status,from,to,page,size);}

    @PostMapping("/dashboard-maintenance/pending-products") @PreAuthorize("hasAuthority('dashboard.pending-products.manage')")
    PendingProduct createPendingProduct(@Valid @RequestBody PendingProductRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.createPendingProduct(request.input(),key,actor);}

    @PatchMapping("/dashboard-maintenance/pending-products/{id}") @PreAuthorize("hasAuthority('dashboard.pending-products.manage')")
    PendingProduct updatePendingProduct(@PathVariable String id,@Valid @RequestBody PendingProductUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedActor actor){return service.updatePendingProduct(id,
                    request.product().input(),request.version(),actor);}

    @PostMapping("/dashboard-maintenance/pending-products/{id}/complete") @PreAuthorize("hasAuthority('dashboard.pending-products.manage')")
    PendingProduct completePendingProduct(@PathVariable String id,@Valid @RequestBody VersionRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.completePendingProduct(id,request.version(),key,actor);}

    @PostMapping("/dashboard-maintenance/pending-products/{id}/archive") @PreAuthorize("hasAuthority('dashboard.pending-products.manage')")
    PendingProduct archivePendingProduct(@PathVariable String id,@Valid @RequestBody StateRequest request,
            @RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal AuthenticatedActor actor){
        return service.archivePendingProduct(id,request.version(),request.reason(),key,actor);}
    @GetMapping("/dashboard-maintenance/pending-products/{id}/history") @PreAuthorize("hasAuthority('dashboard.pending-products.manage')")
    List<AuditHistoryLookup.HistoryEvent> pendingProductHistory(@PathVariable String id){return service.history("PENDING_PRODUCT",id);}

    @PostMapping(value="/dashboard-images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('dashboard.shipments.manage','dashboard.pending-products.manage')")
    ImageReceipt upload(@RequestParam ImagePurpose purpose,@RequestParam MultipartFile file,
            Authentication authentication,@AuthenticationPrincipal AuthenticatedActor actor) throws IOException {
        boolean allowed=authentication.getAuthorities().stream().anyMatch(value->value.getAuthority().equals(
                purpose==ImagePurpose.SHIPMENT?"dashboard.shipments.manage":"dashboard.pending-products.manage"));
        if(!allowed)throw new DashboardException(403,"CAPABILITY_DENIED","当前账号无权上传该用途图片");
        return service.uploadImage(purpose,file.getOriginalFilename(),file.getBytes(),actor);
    }

    @GetMapping("/dashboard-images/{id}/thumbnail") @PreAuthorize("hasAuthority('dashboard.read')")
    ResponseEntity<byte[]> thumbnail(@PathVariable String id,Authentication authentication,
            @AuthenticationPrincipal AuthenticatedActor actor)throws IOException{
        return image(service.image(id,true,actor,has(authentication,"dashboard.shipments.manage"),
                has(authentication,"dashboard.pending-products.manage")));}
    @GetMapping("/dashboard-images/{id}/original") @PreAuthorize("hasAuthority('dashboard.read')")
    ResponseEntity<byte[]> original(@PathVariable String id,Authentication authentication,
            @AuthenticationPrincipal AuthenticatedActor actor)throws IOException{
        return image(service.image(id,false,actor,has(authentication,"dashboard.shipments.manage"),
                has(authentication,"dashboard.pending-products.manage")));}

    private boolean has(Authentication authentication,String authority){return authentication.getAuthorities().stream()
            .anyMatch(value->value.getAuthority().equals(authority));}

    private ResponseEntity<byte[]> image(DashboardService.ImageContent image){return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.mediaType())).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header(HttpHeaders.CONTENT_DISPOSITION,"inline").body(image.bytes());}

    record ShipmentRequest(@NotNull LocalDate date,@NotBlank @Size(max=36) String productId,@Size(max=36) String imageId,
                           @NotNull @Positive @Digits(integer=9,fraction=3) java.math.BigDecimal quantity,@Positive int boxCount,@Size(max=500) String remark,
                           @NotNull LocalDateTime latestShipTime,@NotBlank @Size(max=36) String variantId,@NotBlank @Size(max=36) String customerProductId){
        ShipmentInput input(){return new ShipmentInput(date,productId,imageId,quantity,boxCount,remark,
                latestShipTime.atZone(DashboardService.BUSINESS_ZONE).toInstant(),variantId,customerProductId,null);}}
    record ShipmentUpdateRequest(@Valid @NotNull ShipmentRequest shipment,@Min(0) long version,@Size(max=500) String correctionReason){}
    record TransferRequest(@NotNull @Positive @Digits(integer=9,fraction=3) java.math.BigDecimal quantity,@Positive int boxCount,@Size(max=500) String remark,
                           @NotNull LocalDateTime latestShipTime,@Size(max=36) String imageId,@Min(0) long version){
        java.time.Instant latestInstant(){return latestShipTime.atZone(DashboardService.BUSINESS_ZONE).toInstant();}}
    record PendingProductRequest(@NotBlank @Size(max=128) String productName,@Size(max=36) String imageId,
                                 @Positive int expectedQuantity,@Size(max=500) String remark,
                                 @NotNull LocalDate expectedCompletionDate){
        PendingProductInput input(){return new PendingProductInput(productName,imageId,expectedQuantity,remark,expectedCompletionDate);}}
    record PendingProductUpdateRequest(@Valid @NotNull PendingProductRequest product,@Min(0) long version){}
    record VersionRequest(@Min(0) long version){}
    record StateRequest(@Min(0) long version,@NotBlank @Size(max=500) String reason){}
}
