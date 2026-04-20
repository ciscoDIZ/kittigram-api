package es.kitti.cat.exception;

public class CatNotFoundException extends RuntimeException {
    public CatNotFoundException(Long id) {
        super("Cat not found with id: " + id);
    }
}