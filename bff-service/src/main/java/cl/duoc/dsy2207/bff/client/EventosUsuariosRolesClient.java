package cl.duoc.dsy2207.bff.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Semana 8 (Sumativa 3): cliente HTTP hacia el endpoint de consultas de
 * func-eventos-usuarios-roles-dsy2207, para mostrar el ultimo eslabon del flujo
 * (auditoria, eventos procesados, notificaciones y estado final de usuarios).
 *
 * El procesamiento de eventos NO pasa por el BFF: lo dispara Azure Event Grid.
 */
@Component
public class EventosUsuariosRolesClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() { };

    private final WebClient webClient;

    public EventosUsuariosRolesClient(@Value("${funciones.eventos-usuarios-roles.base-url}") String baseUrl) {
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Mono<List<Map<String, Object>>> consultar(String recurso, Long idUsuario) {
        return webClient.get()
                .uri(b -> {
                    b.path("/consultas/{recurso}");
                    if (idUsuario != null) {
                        b.queryParam("idUsuario", idUsuario);
                    }
                    return b.build(recurso);
                })
                .retrieve()
                .bodyToMono(LISTA);
    }
}
