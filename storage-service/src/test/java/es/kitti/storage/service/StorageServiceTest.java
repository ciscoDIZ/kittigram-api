package es.kitti.storage.service;

import io.smallrye.mutiny.Uni;
import es.kitti.storage.exception.InvalidFileException;
import es.kitti.storage.provider.StorageProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    private static final byte[] JPEG_DATA = jpegData(100);
    private static final byte[] PNG_DATA  = pngData(100);

    @Mock
    StorageProvider storageProvider;

    @InjectMocks
    StorageService storageService;

    @Test
    void upload_validJpeg_returnsKey() {
        when(storageProvider.upload(anyString(), eq(JPEG_DATA), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        var result = storageService.upload(JPEG_DATA, "image/jpeg", "photo.jpg")
                .await().indefinitely();

        assertNotNull(result);
        assertTrue(result.endsWith(".jpg"));
        verify(storageProvider).upload(anyString(), eq(JPEG_DATA), eq("image/jpeg"));
    }

    @Test
    void upload_validPng_returnsKey() {
        when(storageProvider.upload(anyString(), eq(PNG_DATA), eq("image/png")))
                .thenReturn(Uni.createFrom().item("some-key.png"));

        var result = storageService.upload(PNG_DATA, "image/png", "photo.png")
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
        byte[] data = new byte[6 * 1024 * 1024]; // 6MB — falla antes de llegar a magic bytes

        assertThrows(InvalidFileException.class, () ->
                storageService.upload(data, "image/jpeg", "photo.jpg")
                        .await().indefinitely()
        );

        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_exactMaxSize_succeeds() {
        byte[] data = jpegData(5 * 1024 * 1024);

        when(storageProvider.upload(anyString(), eq(data), eq("image/jpeg")))
                .thenReturn(Uni.createFrom().item("some-key.jpg"));

        assertDoesNotThrow(() ->
                storageService.upload(data, "image/jpeg", "photo.jpg")
                        .await().indefinitely()
        );
    }

    @Test
    void upload_wrongMagicBytesForDeclaredType_throwsInvalidFileException() {
        // PNG content declared as image/jpeg
        assertThrows(InvalidFileException.class, () ->
                storageService.upload(PNG_DATA, "image/jpeg", "photo.jpg")
                        .await().indefinitely()
        );

        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_allZeroesWithValidContentType_throwsInvalidFileException() {
        byte[] malicious = new byte[100]; // sin magic bytes — simula fichero malicioso disfrazado

        assertThrows(InvalidFileException.class, () ->
                storageService.upload(malicious, "image/jpeg", "malware.jpg")
                        .await().indefinitely()
        );

        verify(storageProvider, never()).upload(any(), any(), any());
    }

    @Test
    void upload_tooShortForMagicBytes_throwsInvalidFileException() {
        byte[] tooShort = {(byte) 0xFF, (byte) 0xD8}; // solo 2 bytes, JPEG necesita 3

        assertThrows(InvalidFileException.class, () ->
                storageService.upload(tooShort, "image/jpeg", "photo.jpg")
                        .await().indefinitely()
        );

        verify(storageProvider, never()).upload(any(), any(), any());
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

    // --- helpers ---

    private static byte[] jpegData(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        return data;
    }

    private static byte[] pngData(int size) {
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] data = new byte[size];
        System.arraycopy(magic, 0, data, 0, magic.length);
        return data;
    }
}
