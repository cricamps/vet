package cl.duoc.dsy2207.eventos.productora;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import com.azure.messaging.eventgrid.EventGridPublisherClientBuilder;

import java.util.Map;

/**
 * Envia eventos al Event Grid Topic de VetCare.
 *
 * A diferencia del ejemplo del material descargable (que deja el endpoint y la
 * key escritos en el codigo), aqui se leen desde variables de entorno / App
 * Settings de la Function App, para no subir la key del topic a GitHub:
 *   - EVENTGRID_TOPIC_ENDPOINT  (ej: https://vetcare-events-xxxx.brazilsouth-1.eventgrid.azure.net/api/events)
 *   - EVENTGRID_TOPIC_KEY       (Key 1 de "Access keys" del topic)
 *
 * El cliente se crea una sola vez por instancia (lazy) y se reutiliza entre
 * invocaciones: crear un cliente HTTP en cada llamada es un anti-patron en FaaS.
 */
public final class PublicadorEventGrid {

    private static volatile EventGridPublisherClient<EventGridEvent> cliente;

    private PublicadorEventGrid() {
    }

    private static EventGridPublisherClient<EventGridEvent> cliente() {
        if (cliente == null) {
            synchronized (PublicadorEventGrid.class) {
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

    /**
     * Publica un evento con esquema Event Grid y devuelve el objeto enviado
     * (incluye el id y eventTime que genera el SDK), para poder informarlo en
     * la respuesta HTTP.
     */
    public static EventGridEvent publicar(TipoEvento tipo, String idRecurso, Map<String, Object> data) {
        EventGridEvent evento = new EventGridEvent(
                tipo.subject(idRecurso),
                tipo.eventType(),
                BinaryData.fromObject(data),
                TipoEvento.DATA_VERSION);
        cliente().sendEvent(evento);
        return evento;
    }
}
