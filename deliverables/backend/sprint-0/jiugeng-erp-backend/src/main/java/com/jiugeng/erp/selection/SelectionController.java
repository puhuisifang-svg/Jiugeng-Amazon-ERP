package com.jiugeng.erp.selection;

import static com.jiugeng.erp.selection.SelectionTypes.*;

import com.jiugeng.erp.iam.AuthenticatedActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("!mock")
@RequestMapping("/api/v1/selection/candidates")
class SelectionController {
    private final SelectionService service;
    SelectionController(SelectionService service){this.service=service;}

    @GetMapping @PreAuthorize("hasAuthority('selection.read')")
    List<CandidateView> candidates(@RequestParam(required=false) String keyword,
            @RequestParam(required=false) CandidateStatus status,@RequestParam(required=false) String owner){return service.candidates(keyword,status,owner);}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('selection.read')") DetailView detail(@PathVariable String id){return service.detail(id);}
    @PostMapping @PreAuthorize("hasAuthority('selection.write')") CandidateView create(@Valid @RequestBody CreateRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.create(request.asinOrUrl(),request.title(),request.score(),actor);}
    @PutMapping("/{id}/evaluation") @PreAuthorize("hasAuthority('selection.write')") CandidateView evaluate(@PathVariable String id,@Valid @RequestBody EvaluationRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.evaluate(id,request.input(),actor);}
    @PostMapping("/{id}/transition") @PreAuthorize("hasAuthority('selection.write')") CandidateView transition(@PathVariable String id,@Valid @RequestBody TransitionRequest request,@AuthenticationPrincipal AuthenticatedActor actor){return service.transition(id,request.status(),request.rejectionReason(),actor);}
    @PostMapping("/{id}/notes") @PreAuthorize("hasAuthority('selection.write')") DetailView note(@PathVariable String id,@Valid @RequestBody NoteRequest request,@AuthenticationPrincipal AuthenticatedActor actor,Authentication auth){return service.addNote(id,request.content(),role(auth),actor);}
    @PostMapping("/{id}/evidence") @PreAuthorize("hasAuthority('selection.write')") DetailView evidence(@PathVariable String id,@Valid @RequestBody EvidenceRequest request,@AuthenticationPrincipal AuthenticatedActor actor,Authentication auth){return service.addEvidence(id,request.name(),request.url(),request.description(),role(auth),actor);}
    private String role(Authentication auth){
        var capabilities=auth.getAuthorities().stream().map(Object::toString).collect(java.util.stream.Collectors.toSet());
        if(capabilities.contains("system.users.manage")) return "ADMIN";
        if(capabilities.contains("selection.write")) return "SELECTION";
        return "PRODUCT_MATERIAL";
    }

    record CreateRequest(@NotBlank @Size(max=1000) String asinOrUrl,@NotBlank @Size(max=300) String title,@Min(0) @Max(100) Integer score){}
    record EvaluationRequest(@Size(max=100) String category,BigDecimal price,BigDecimal rating,@Min(0) Integer reviewCount,@Size(max=100) String owner,@Size(max=20) List<@Size(max=64) String> tags,@Size(max=1000) String selectionReason){EvaluationInput input(){return new EvaluationInput(category,price,rating,reviewCount,owner,tags,selectionReason);}}
    record TransitionRequest(@NotNull CandidateStatus status,@Size(max=500) String rejectionReason){}
    record NoteRequest(@NotBlank @Size(max=1000) String content){}
    record EvidenceRequest(@NotBlank @Size(max=200) String name,@NotBlank @Size(max=1000) String url,@Size(max=1000) String description){}
}
