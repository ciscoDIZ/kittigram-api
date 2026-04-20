package es.kitti.adoption.exception;

public class AdoptionFormAlreadySubmittedException extends RuntimeException {
    public AdoptionFormAlreadySubmittedException(Long adoptionRequestId) {
        super("Adoption form already submitted for request: " + adoptionRequestId);
    }
}