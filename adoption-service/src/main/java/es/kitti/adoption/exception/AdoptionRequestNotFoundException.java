package es.kitti.adoption.exception;

public class AdoptionRequestNotFoundException extends RuntimeException {
    public AdoptionRequestNotFoundException(Long id) {
        super("Adoption request not found with id: " + id);
    }
}