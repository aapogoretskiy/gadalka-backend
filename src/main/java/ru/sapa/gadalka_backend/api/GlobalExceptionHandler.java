package ru.sapa.gadalka_backend.api;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.sapa.gadalka_backend.api.dto.ErrorResponse;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Ошибка валидации запроса [{} {}]: {}", request.getMethod(), request.getRequestURI(), errors);

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(),
                        "Ошибка валидации: " + errors,
                        request.getRequestURI(),
                        LocalDateTime.now()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        log.warn("Некорректный аргумент [{} {}]: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());

        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        LocalDateTime.now()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex,
                                                            HttpServletRequest request) {
        log.error("Ошибка внутреннего состояния [{} {}]: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Внутренняя ошибка сервера",
                        request.getRequestURI(),
                        LocalDateTime.now()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex,
                                                       HttpServletRequest request) {
        log.error("Непредвиденная ошибка выполнения [{} {}]: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Внутренняя ошибка сервера",
                        request.getRequestURI(),
                        LocalDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex, HttpServletRequest request) {
        log.error("Критическая необработанная ошибка [{} {}]: {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.internalServerError()
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Критическая ошибка сервера",
                        request.getRequestURI(),
                        LocalDateTime.now()));
    }
}
