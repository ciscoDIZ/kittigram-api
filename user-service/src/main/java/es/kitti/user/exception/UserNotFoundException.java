package es.kitti.user.exception;

import java.io.Serial;

public class UserNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    public UserNotFoundException() {
        super("User not found");
    }
    public UserNotFoundException(String email) {
        super("User with email " + email + " not found");
    }
}
