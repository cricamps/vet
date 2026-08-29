package cl.duoc.dsy2207.bff.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maneja los errores que pueden ocurrir al orquestar las llamadas hacia las
 * funciones serverless (Azure) y las validaciones de negocio del propio BFF,
 * para que el cliente (Postman/frontend) siempre reciba un JSON de error
 * consistente con el codigo HTTP correcto -- en vez de un 500 generico que
 * esconde la causa real.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Una funcion de Azure (Usuarios o Roles) respondio con un codigo de
     * error (404, 500, etc). WebClient.retrieve() no propaga esto solo:
     * sin este handler, cualquier error de una funcion downstream terminaba
     * en un 500 generico del BFF, perdiendo el codigo y el mensaje reales
     * de la funcion. Aqui se reenvia el mismo status que devolvio la
     * funcion, junto con su cuerpo si trae uno.
     */
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Object> manejarErrorFuncion(WebClientResponseException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String cuerpoOriginal = ex.getResponseBodyAsString();

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", status.value());
        error.put("error", ex.getStatusText());
        error.put("mensaje", (cuerpoOriginal != null && !cuerpoOriginal.isBlank())
                ? cuerpoOriginal
                : "La funcion serverless respondio con un error y no entrego detalle adicional.");

        return ResponseEntity.status(status)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(error);
    }

    /**
     * Validaciones de negocio propias del BFF (ej.: "el rol X no existe"
     * antes de crear un usuario). Spring WebFlux ya responde con el status
     * correcto por defecto para ResponseStatusException; se normaliza aqui
     * para que el formato del JSON de error sea siempre el mismo, sea cual
     * sea el origen del error.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> manejarErrorValidacion(ResponseStatusException ex) {
        String motivo = ex.getReason() != null ? ex.getReason() : "Solicitud invalida.";

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", Instant.now().toString());
        error.put("status", ex.getStatusCode().value());
        error.put("error", motivo);
        error.put("mensaje", motivo);

        return ResponseEntity.status(ex.getStatusCode())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(error);
    }
}
