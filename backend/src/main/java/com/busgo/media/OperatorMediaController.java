package com.busgo.media;

import com.busgo.common.security.CurrentUser;
import com.busgo.common.response.ApiResponse;
import com.busgo.media.OperatorMediaService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class OperatorMediaController {
    private static final String TYPES="/operator/bus-types/{type}/media";
    private final OperatorMediaService media;
    public OperatorMediaController(OperatorMediaService media) { this.media=media; }
    public record Edit(@NotNull Category category,@NotNull @Size(max=180) String alt, boolean featured) {}
    public record Order(@NotNull @Size(max=12) List<@NotNull UUID> ids) {}

    @GetMapping("/public/operators/{owner}/media")
    public ApiResponse<Gallery> gallery(@PathVariable long owner) { return ApiResponse.of(media.gallery(owner)); }
    @GetMapping("/public/operators/{owner}/media/{id}")
    public ResponseEntity<byte[]> file(@PathVariable long owner,@PathVariable UUID id) { return media.file(owner,id); }
    @PostMapping("/operator/marketplace-profile/logo")
    public ApiResponse<Boolean> logo(@AuthenticationPrincipal CurrentUser user,@RequestParam MultipartFile file) {
        media.logo(user,file); return ApiResponse.of(true);
    }
    @DeleteMapping("/operator/marketplace-profile/logo")
    public ApiResponse<Boolean> removeLogo(@AuthenticationPrincipal CurrentUser user) {
        media.logo(user,null); return ApiResponse.of(true);
    }
    @GetMapping("/operator/marketplace-profile/logo")
    public ResponseEntity<byte[]> currentLogo(@AuthenticationPrincipal CurrentUser user) {
        return media.previewLogo(user);
    }
    @GetMapping(TYPES)
    public ApiResponse<List<Photo>> list(@AuthenticationPrincipal CurrentUser user,@PathVariable long type) {
        return ApiResponse.of(media.own(user,type));
    }
    @GetMapping(TYPES+"/{id}")
    public ResponseEntity<byte[]> preview(@AuthenticationPrincipal CurrentUser user,@PathVariable long type,@PathVariable UUID id) {
        return media.preview(user,type,id);
    }
    @PostMapping(TYPES)
    public ApiResponse<Boolean> upload(@AuthenticationPrincipal CurrentUser user,@PathVariable long type,
            @RequestParam Category category,@RequestParam(defaultValue="") @Size(max=180) String alt,
            @RequestParam List<MultipartFile> files) {
        media.upload(user,type,category,alt,files); return ApiResponse.of(true);
    }
    @PutMapping(TYPES+"/order")
    public ApiResponse<Boolean> order(@AuthenticationPrincipal CurrentUser user,@PathVariable long type,@Valid @RequestBody Order order) {
        media.reorder(user,type,order.ids()); return ApiResponse.of(true);
    }
    @PatchMapping(TYPES+"/{id}")
    public ApiResponse<Boolean> edit(@AuthenticationPrincipal CurrentUser user,@PathVariable long type,@PathVariable UUID id,@Valid @RequestBody Edit edit) {
        media.edit(user,type,id,edit.category(),edit.alt(),edit.featured()); return ApiResponse.of(true);
    }
    @DeleteMapping(TYPES+"/{id}")
    public ApiResponse<Boolean> delete(@AuthenticationPrincipal CurrentUser user,@PathVariable long type,@PathVariable UUID id) {
        media.delete(user,type,id); return ApiResponse.of(true);
    }
}
