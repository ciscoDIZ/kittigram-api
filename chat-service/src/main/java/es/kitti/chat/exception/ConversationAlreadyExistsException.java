package es.kitti.chat.exception;

public class ConversationAlreadyExistsException extends RuntimeException {
    public ConversationAlreadyExistsException(Long intakeRequestId) {
        super("Conversation already exists for intake request " + intakeRequestId);
    }
}