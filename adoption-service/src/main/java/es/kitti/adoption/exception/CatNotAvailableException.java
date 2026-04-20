package es.kitti.adoption.exception;

public class CatNotAvailableException extends RuntimeException {
    public CatNotAvailableException(Long catId) {
        super("Cat is not available for adoption: " + catId);
    }
}