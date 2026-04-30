package es.kitti.cat.exception;

public class CatHasActiveAdoptionsException extends RuntimeException {
    public CatHasActiveAdoptionsException(Long catId) {
        super("Cat " + catId + " has active adoption requests");
    }
}
