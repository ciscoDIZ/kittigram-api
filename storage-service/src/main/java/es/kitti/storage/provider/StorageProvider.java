package es.kitti.storage.provider;

import io.smallrye.mutiny.Uni;

public interface StorageProvider {

    Uni<String> upload(String key, byte[] data, String contentType);

    Uni<Void> delete(String key);

    String getUrl(String key);
}