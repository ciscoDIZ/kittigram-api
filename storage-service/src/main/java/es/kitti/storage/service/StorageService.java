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

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

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

        if (!hasValidMagicBytes(data, contentType)) {
            return Uni.createFrom().failure(
                    new InvalidFileException("File content does not match the declared type")
            );
        }

        String extension = contentType.equals("image/jpeg") ? ".jpg" : ".png";
        String key = UUID.randomUUID() + extension;

        return storageProvider.upload(key, data, contentType);
    }

    private boolean hasValidMagicBytes(byte[] data, String contentType) {
        byte[] magic = contentType.equals("image/jpeg") ? JPEG_MAGIC : PNG_MAGIC;
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    public Uni<Void> delete(String key) {
        return storageProvider.delete(key);
    }
}