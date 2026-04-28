package es.kitti.adoption.intake.exception;

public class IntakeRequestNotFoundException extends RuntimeException {
    public IntakeRequestNotFoundException(Long id) {
        super("Intake request not found: " + id);
    }
}
