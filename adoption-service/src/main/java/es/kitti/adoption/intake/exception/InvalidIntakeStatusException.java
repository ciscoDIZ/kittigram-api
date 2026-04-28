package es.kitti.adoption.intake.exception;

import es.kitti.adoption.intake.entity.IntakeStatus;

public class InvalidIntakeStatusException extends RuntimeException {
    public InvalidIntakeStatusException(IntakeStatus current, IntakeStatus required) {
        super("Invalid intake status. Current: " + current + ", required: " + required);
    }
}
