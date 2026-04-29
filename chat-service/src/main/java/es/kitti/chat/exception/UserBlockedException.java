package es.kitti.chat.exception;

public class UserBlockedException extends RuntimeException {
    public UserBlockedException() {
        super("User is blocked from this conversation");
    }
}
