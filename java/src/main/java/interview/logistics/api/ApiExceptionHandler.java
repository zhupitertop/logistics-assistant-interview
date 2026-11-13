package interview.logistics.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ErrorBody(String code, String message) {}
    public record ErrorResponse(ErrorBody error) {}

    @ExceptionHandler({InvalidQuestionException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求必须是仅包含非空字符串 question 的合法 JSON 对象，最多 2000 字。");
    }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMedia(Exception ignored) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "请使用 application/json。");
    }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorResponse> unsupportedMethod(Exception ignored) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).allow(org.springframework.http.HttpMethod.POST)
                .body(new ErrorResponse(new ErrorBody("METHOD_NOT_ALLOWED", "请使用 POST 方法。")));
    }
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    ResponseEntity<ErrorResponse> unsupportedAccept(Exception ignored) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "响应格式为 application/json。");
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> serverError(Exception failure) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error("Ask processing failed", failure);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务处理失败，请稍后重试。");
    }
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(new ErrorResponse(new ErrorBody(code, message)));
    }
}
