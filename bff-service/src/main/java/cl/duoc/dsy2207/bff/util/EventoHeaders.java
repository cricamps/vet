package cl.duoc.dsy2207.bff.util;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Semana 8: las funciones de Usuarios y Roles informan en headers el evento de
 * dominio que publicaron en Azure Event Grid (X-Evento-Tipo, X-Evento-Id,
 * X-Evento-Publicado). El BFF los reenvia tal cual al cliente para que en
 * Postman se vea, en la misma respuesta del CRUD, que el evento se genero.
 */
public final class EventoHeaders {

    static final List<String> NOMBRES = List.of("X-Evento-Tipo", "X-Evento-Id", "X-Evento-Publicado");

    private EventoHeaders() {
    }

    public static HttpHeaders copiar(HttpHeaders origen) {
        HttpHeaders destino = new HttpHeaders();
        if (origen != null) {
            for (String nombre : NOMBRES) {
                String valor = origen.getFirst(nombre);
                if (valor != null) {
                    destino.set(nombre, valor);
                }
            }
        }
        return destino;
    }

    /** Respuesta del BFF con el status indicado, el body de la funcion y sus headers de evento. */
    public static <T> ResponseEntity<T> reenviar(ResponseEntity<T> respuestaFuncion, HttpStatusCode status) {
        return ResponseEntity.status(status)
                .headers(copiar(respuestaFuncion.getHeaders()))
                .body(respuestaFuncion.getBody());
    }
}
