package cl.duoc.dsy2207.bff.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP hacia la capa de eventos (semana 7):
 *  - funcion GENERADORA de eventos (publica en Azure Event Grid)
 *  - funcion CONSUMIDORA (solo sus endpoints de consulta; el procesamiento de
 *    eventos lo dispara Event Grid, no el BFF)
 */
@Component
public class EventosFunctionClient {

    private static final ParameterizedTypeReference<Map<String, Object>> MAPA =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() { };

    private final WebClient productora;
    private final WebClient consumidora;

    public EventosFunctionClient(@Qualifier("eventosProductoraWebClient") WebClient productora,
                                 @Qualifier("eventosConsumidoraWebClient") WebClient consumidora) {
        this.productora = productora;
        this.consumidora = consumidora;
    }

    /** POST a una funcion generadora; propaga el usuario validado en X-Usuario-Id. */
    public Mono<Map<String, Object>> publicar(String ruta, Object cuerpo, long idUsuario) {
        return productora.post()
                .uri(ruta)
                .header("X-Usuario-Id", String.valueOf(idUsuario))
                .bodyValue(cuerpo)
                .retrieve()
                .bodyToMono(MAPA);
    }

    public Mono<List<Map<String, Object>>> consultar(String recurso) {
        return consumidora.get()
                .uri("/consultas/{recurso}", recurso)
                .retrieve()
                .bodyToMono(LISTA);
    }
}
