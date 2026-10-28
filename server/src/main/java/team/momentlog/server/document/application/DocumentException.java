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

    /** QR 토큰에 맞는 공유가 없음. */
    public static class ShareNotFound extends DocumentException {
        public ShareNotFound() {
            super("문서를 찾을 수 없습니다.");
        }
    }

    /** 공유 기간이 지남. */
    public static class ShareExpired extends DocumentException {
        public ShareExpired() {
            super("문서 공유 기간이 지났습니다.");
        }
    }

    /** 원본이 core 원본 형식(RecordHasher.canonical)으로 읽히지 않음. */
    public static class InvalidBody extends DocumentException {
        public InvalidBody(String reason) {
            super("원본 형식을 읽을 수 없습니다: " + reason);
        }
    }

    /** 올린 원본 바이트가 봉인 해시와 다름. */
    public static class BodyMismatch extends DocumentException {
        public BodyMismatch(String recordId) {
            super("봉인된 원본과 다른 기록입니다: " + recordId);
        }
    }
}
