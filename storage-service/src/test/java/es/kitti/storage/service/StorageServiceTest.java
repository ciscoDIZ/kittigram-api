package es.kitti.storage.service;

import io.smallrye.mutiny.Uni;
import es.kitti.storage.exception.InvalidFileException;
import es.kitti.storage.provider.StorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    StorageProvider storageProvider;

    @InjectMocks
    StorageService storageService;

    @Test
    void upload_validJpeg_returnsKey() {
        byte[] data = new byte[100];

        when(storageProvider.upload(anyString(), eq(data), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        var result = storageService.upload(data, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertNotNull(result);
        assertTrue(result.endsWith(".jpg"));
        verify(storageProvider).upload(anyString(), eq(data), eq("image/jpeg"));
    }

    @Test
    void upload_validPng_returnsKey() {
        byte[] data = new byte[100];

        when(storageProvider.upload(anyString(), eq(data), eq("image/png")))
                .thenReturn(Uni.createFrom().item("some-key.png"));

        var result = storageService.upload(data, "image/png", "photo.png")
                .await().indefinitely();

        assertNotNull(result);
        assertTrue(result.endsWith(".png"));
    }

    @Test
    void upload_invalidContentType_throwsInvalidFileException() {
        byte[] data = new byte[100];

        assertThrows(InvalidFileException.class, () ->
                storageService.upload(data, "application/pdf", "document.pdf")
                        .await().indefinitely()
        );

        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_fileTooLarge_throwsInvalidFileException() {
        byte[] data = new byte[6 * 1024 * 1024]; // 6MB

        assertThrows(InvalidFileException.class, () ->
                storageService.upload(data, "image/jpeg", "photo.jpg")
                        .await().indefinitely()
        );

        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_exactMaxSize_succeeds() {
        byte[] data = new byte[5 * 1024 * 1024]; // exactamente 5MB

        when(storageProvider.upload(anyString(), eq(data), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        assertDoesNotThrow(() ->
                storageService.upload(data, "image/jpeg", "photo.jpg")
                        .await().indefinitely()
        );
    }

    @Test
    void delete_delegatesToProvider() {
        when(storageProvider.delete("some-key.jpg"))
                .thenReturn(Uni.createFrom().voidItem());

        assertDoesNotThrow(() ->
                storageService.delete("some-key.jpg")
                        .await().indefinitely()
        );

        verify(storageProvider).delete("some-key.jpg");
    }
}