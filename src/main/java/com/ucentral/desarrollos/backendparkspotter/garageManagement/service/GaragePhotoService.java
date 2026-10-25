package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GaragePhotoResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GaragePhoto;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GaragePhotoRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Fotos del garaje: las sube el dueño (o un administrador) y sirven para la verificación y para el detalle.
 * Se valida el tipo real del archivo por su firma (magic bytes), no solo por el Content-Type que manda el cliente.
 */
@Service
@RequiredArgsConstructor
public class GaragePhotoService {

    static final int MAX_PHOTOS_PER_GARAGE = 8;
    static final int MAX_BYTES = 5 * 1024 * 1024;

    private final GarageRepository garageRepository;
    private final GaragePhotoRepository photoRepository;

    @Transactional(readOnly = true)
    public List<GaragePhotoResponse> list(UUID garageId) {
        if (!garageRepository.existsById(garageId)) {
            throw new EntityNotFoundException("Garage not found");
        }
        return photoRepository.findMetadataByGarageId(garageId);
    }

    @Transactional(readOnly = true)
    public GaragePhoto get(UUID garageId, UUID photoId) {
        GaragePhoto photo = photoRepository.findByIdAndGarageId(photoId, garageId)
                .orElseThrow(() -> new EntityNotFoundException("Foto no encontrada"));
        // Se fuerza la carga de los bytes dentro de la transacción.
        photo.getData();
        return photo;
    }

    @Transactional
    public GaragePhotoResponse upload(UUID garageId, MultipartFile file, UserAccount user) {
        Garage garage = garageRepository.findById(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        GarageAccessPolicy.assertCanManage(garage, user);
        if (file == null || file.isEmpty()) {
            throw new ApiException("Adjunta una imagen en el campo 'file'");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException("La imagen no puede pesar más de 5 MB");
        }
        if (photoRepository.countByGarageId(garageId) >= MAX_PHOTOS_PER_GARAGE) {
            throw new ApiException("Un garaje puede tener máximo " + MAX_PHOTOS_PER_GARAGE + " fotos");
        }
        byte[] bytes = readBytes(file);
        String contentType = detectImageType(bytes);
        if (contentType == null) {
            throw new ApiException("Formato no soportado: usa JPG, PNG o WEBP");
        }
        GaragePhoto photo = new GaragePhoto();
        photo.setGarage(garage);
        photo.setContentType(contentType);
        photo.setSizeBytes(bytes.length);
        photo.setData(bytes);
        GaragePhoto saved = photoRepository.save(photo);
        return new GaragePhotoResponse(saved.getId(), garageId, contentType, bytes.length, saved.getCreatedAt());
    }

    @Transactional
    public void delete(UUID garageId, UUID photoId, UserAccount user) {
        Garage garage = garageRepository.findById(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        GarageAccessPolicy.assertCanManage(garage, user);
        GaragePhoto photo = photoRepository.findByIdAndGarageId(photoId, garageId)
                .orElseThrow(() -> new EntityNotFoundException("Foto no encontrada"));
        photoRepository.delete(photo);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new ApiException("No se pudo leer la imagen");
        }
    }

    /** Tipo de imagen según los primeros bytes del archivo; null si no es JPG, PNG ni WEBP. */
    static String detectImageType(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
