package team.momentlog.server.device.application;

/** 기기 토큰이 없거나 등록된 토큰과 다름. */
public class DeviceAuthException extends RuntimeException {

    public DeviceAuthException(String message) {
        super(message);
    }
}
