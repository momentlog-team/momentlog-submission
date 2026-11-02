package team.momentlog.server.device.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import team.momentlog.server.device.application.DeviceAuthException;

import java.util.Map;

/** 기기 인증 실패는 어느 API에서 나든 401로 돌려준다. */
@RestControllerAdvice
public class DeviceAuthAdvice {

    @ExceptionHandler(DeviceAuthException.class)
    public ResponseEntity<Map<String, String>> unauthorized(DeviceAuthException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("errorCode", "DEVICE_AUTH", "message", e.getMessage()));
    }
}
