package es.kitti.organization.exception;

public class MemberLimitExceededException extends RuntimeException {
    public MemberLimitExceededException(int max) {
        super("Member limit reached for current plan (max: " + max + ")");
    }
}
