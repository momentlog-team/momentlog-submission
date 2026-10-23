package team.momentlog.server.record.application;

public class SealConflictException extends RuntimeException {

    public SealConflictException(String recordId) {
        super("이미 다른 원본으로 봉인된 기록입니다: " + recordId);
    }
}
