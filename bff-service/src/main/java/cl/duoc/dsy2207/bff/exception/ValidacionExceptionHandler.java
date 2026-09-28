package cl.duoc.dsy2207.bff.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Errores de entrada del cliente (semana 7): cuerpo que no cumple las
 * validaciones de los DTO, o falta el header X-Usuario-Id. Mismo formato de JSON
 * de error que GlobalExceptionHandler.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ValidacionExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> cuerpoInvalido(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return error(HttpStatus.BAD_REQUEST, "Solicitud invalida", detalle);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Object> faltaHeader(MissingRequestHeaderException ex) {
        return error(HttpStatus.BAD_REQUEST, "Falta el header " + ex.getHeaderName(),
                "Indique el id del usuario que genera el evento en el header " + ex.getHeaderName());
    }

    private ResponseEntity<Object> error(HttpStatus status, String error, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("mensaje", mensaje);
        return ResponseEntity.status(status).body(body);
    }
}
