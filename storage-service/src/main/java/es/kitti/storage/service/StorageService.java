package es.kitti.storage.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import es.kitti.storage.exception.InvalidFileException;
import es.kitti.storage.provider.StorageProvider;

import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class StorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @Inject
    StorageProvider storageProvider;

    public Uni<String> upload(byte[] data, String contentType, String originalFilename) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            return Uni.createFrom().failure(
                    new InvalidFileException("File type not allowed. Only JPG and PNG are accepted")
            );
        }

        if (data.length > MAX_SIZE) {
            return Uni.createFrom().failure(
                    new InvalidFileException("File size exceeds the 5MB limit")
            );
        }

        String extension = contentType.equals("image/jpeg") ? ".jpg" : ".png";
        String key = UUID.randomUUID() + extension;

        return storageProvider.upload(key, data, contentType);
    }

    public Uni<Void> delete(String key) {
        return storageProvider.delete(key);
    }
}