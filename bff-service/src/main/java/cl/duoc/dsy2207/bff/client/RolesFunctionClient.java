package cl.duoc.dsy2207.bff.client;

import cl.duoc.dsy2207.bff.dto.RolDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Cliente HTTP que consume la Funcion Serverless de Roles
 * (Agregar/Listar/Modificar/Eliminar).
 */
@Component
public class RolesFunctionClient {

    private final WebClient webClient;

    public RolesFunctionClient(@Qualifier("rolesWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<RolDto>> listar() {
        return webClient.get()
                .uri("/roles")
                .retrieve()
                .bodyToFlux(RolDto.class)
                .collectList();
    }

    public Mono<RolDto> obtener(long id) {
        return webClient.get()
                .uri("/roles/{id}", id)
                .retrieve()
                .bodyToMono(RolDto.class);
    }

    public Mono<RolDto> agregar(RolDto rol) {
        return webClient.post()
                .uri("/roles")
                .bodyValue(rol)
                .retrieve()
                .bodyToMono(RolDto.class);
    }

    public Mono<RolDto> modificar(long id, RolDto rol) {
        return webClient.put()
                .uri("/roles/{id}", id)
                .bodyValue(rol)
                .retrieve()
                .bodyToMono(RolDto.class);
    }

    public Mono<Void> eliminar(long id) {
        return webClient.delete()
                .uri("/roles/{id}", id)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
