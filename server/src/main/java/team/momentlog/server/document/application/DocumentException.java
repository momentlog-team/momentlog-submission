package team.momentlog.server.document.application;

public abstract class DocumentException extends RuntimeException {

    protected DocumentException(String message) {
        super(message);
    }

    /** 봉인된 적 없는 기록으로 문서를 만들려 함. */
    public static class SealNotFound extends DocumentException {
        public SealNotFound(String recordId) {
            super("봉인되지 않은 기록입니다: " + recordId);
        }
    }

    /** 올린 원본 바이트가 봉인 해시와 다름. */
    public static class BodyMismatch extends DocumentException {
        public BodyMismatch(String recordId) {
            super("봉인된 원본과 다른 기록입니다: " + recordId);
        }
    }
}
