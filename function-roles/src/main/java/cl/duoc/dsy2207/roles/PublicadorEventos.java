package cl.duoc.dsy2207.roles;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;

import java.util.Map;
import java.util.logging.Logger;

/**
 * GENERADOR DE EVENTOS (Semana 8 - Actividad Sumativa 3).
 *
 * Despues de que una operacion CRUD de Roles queda confirmada en Oracle, la
 * funcion publica un evento de dominio en el Event Grid Topic del sistema
 * (usuarios-roles-events). La funcion NO conoce a los consumidores: Event Grid
 * entrega el evento a cada suscripcion (auditoria, procesamiento de roles).
 *
 * Endpoint y key del topic se leen desde App Settings, nunca desde el codigo:
 *   - EVENTGRID_TOPIC_ENDPOINT  https://usuarios-roles-events-xxxx.brazilsouth-1.eventgrid.azure.net/api/events
 *   - EVENTGRID_TOPIC_KEY       Key 1 de "Access keys" del topic
 *
 * La publicacion es "best effort": si Event Grid no esta disponible el CRUD ya
 * se hizo y NO se revierte; el resultado se informa al cliente en los headers
 * X-Evento-Publicado / X-Evento-Id y queda en el log de la funcion.
 */
public final class PublicadorEventos {

    public static final String ROL_CREADO = "UsuariosRoles.RolCreado";
    public static final String ROL_MODIFICADO = "UsuariosRoles.RolModificado";
    public static final String ROL_ELIMINADO = "UsuariosRoles.RolEliminado";
    public static final String DATA_VERSION = "1.0";

    /** Resultado de la publicacion, para informarlo en la respuesta HTTP. */
    public record Resultado(boolean publicado, String idEvento, String eventType, String detalle) {
        static Resultado noPublicado(String eventType, String detalle) {
            return new Resultado(false, null, eventType, detalle);
        }
    }

    private static volatile EventGridPublisherClient<EventGridEvent> cliente;

    private PublicadorEventos() {
    }

    private static EventGridPublisherClient<EventGridEvent> cliente() {
        if (cliente == null) {
            synchronized (PublicadorEventos.class) {
                if (cliente == null) {
                    String endpoint = System.getenv("EVENTGRID_TOPIC_ENDPOINT");
                    String key = System.getenv("EVENTGRID_TOPIC_KEY");
                    if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()) {
                        throw new IllegalStateException(
                                "Faltan las App Settings EVENTGRID_TOPIC_ENDPOINT / EVENTGRID_TOPIC_KEY");
                    }
                    cliente = new EventGridPublisherClientBuilder()
                            .endpoint(endpoint)
                            .credential(new AzureKeyCredential(key))
                            .buildEventGridEventPublisherClient();
                }
            }
        }
        return cliente;
    }

    /** subject estandar del dominio: /roles/{id}. */
    public static String subject(Long idRol) {
        return "/roles/" + idRol;
    }

    /** Publica un evento con esquema Event Grid. Nunca lanza excepcion. */
    public static Resultado publicar(String eventType, String subject, Map<String, Object> data, Logger log) {
        try {
            EventGridEvent evento = new EventGridEvent(subject, eventType, BinaryData.fromObject(data), DATA_VERSION);
            cliente().sendEvent(evento);
            log.info("Evento publicado en Event Grid: " + eventType + " id=" + evento.getId() + " subject=" + subject);
            return new Resultado(true, evento.getId(), eventType, "Evento publicado en Event Grid");
        } catch (RuntimeException e) {
            log.severe("No se pudo publicar " + eventType + " (" + subject + "): " + e.getMessage());
            return Resultado.noPublicado(eventType, e.getMessage());
        }
    }
}
