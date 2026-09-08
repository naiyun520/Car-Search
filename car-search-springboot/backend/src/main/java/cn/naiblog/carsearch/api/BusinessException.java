package cn.naiblog.carsearch.api;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final int code;
    private final HttpStatus status;

    public BusinessException(String message) { this(422, HttpStatus.UNPROCESSABLE_ENTITY, message); }
    public BusinessException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public int code() { return code; }
    public HttpStatus status() { return status; }
}
