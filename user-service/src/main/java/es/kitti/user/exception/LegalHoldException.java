package es.kitti.user.exception;

public class LegalHoldException extends RuntimeException {
    public LegalHoldException() {
        super("Erasure request cannot be processed while a legal hold is active on this account");
    }
}
