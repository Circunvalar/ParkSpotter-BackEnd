package com.ucentral.desarrollos.backendparkspotter.garageManagement.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GaragePhotoResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GaragePhoto;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GaragePhotoService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** Fotos del garaje (públicas para ver; subir y borrar solo el dueño o un administrador) y solicitud de verificación. */
@RestController
@RequestMapping("/api/v1/garages/{garageId}")
@RequiredArgsConstructor
public class GaragePhotoController {

    private final GaragePhotoService photoService;
    private final GarageVerificationService verificationService;

    @GetMapping("/photos")
    public ResponseEntity<List<GaragePhotoResponse>> list(@PathVariable UUID garageId) {
        return ResponseEntity.ok(photoService.list(garageId));
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> image(@PathVariable UUID garageId, @PathVariable UUID photoId) {
        GaragePhoto photo = photoService.get(garageId, photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.getContentType()))
                // Una foto nunca cambia (se borra y se sube otra con otro id): se puede cachear mucho tiempo.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(photo.getData());
    }

    @PostMapping(path = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<GaragePhotoResponse> upload(@PathVariable UUID garageId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(photoService.upload(garageId, file, user));
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> delete(@PathVariable UUID garageId, @PathVariable UUID photoId,
                                       @AuthenticationPrincipal UserAccount user) {
        photoService.delete(garageId, photoId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verification-request")
    public ResponseEntity<GarageResponse> requestVerification(@PathVariable UUID garageId,
                                                              @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(verificationService.requestVerification(garageId, user));
    }
}
