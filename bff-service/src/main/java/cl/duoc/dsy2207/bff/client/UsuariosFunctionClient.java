package cl.duoc.dsy2207.bff.client;

import cl.duoc.dsy2207.bff.dto.UsuarioDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Cliente HTTP que consume la Funcion Serverless de Usuarios.
 * El BFF nunca accede a la base de datos directamente: toda la logica de
 * usuarios vive en la funcion; este cliente solo traduce llamadas HTTP
 * hacia las 4 funciones (Agregar/Listar/Modificar/Eliminar).
 */
@Component
public class UsuariosFunctionClient {

    private final WebClient webClient;

    public UsuariosFunctionClient(@Qualifier("usuariosWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<List<UsuarioDto>> listar() {
        return webClient.get()
                .uri("/usuarios")
                .retrieve()
                .bodyToFlux(UsuarioDto.class)
                .collectList();
    }

    public Mono<UsuarioDto> obtener(long id) {
        return webClient.get()
                .uri("/usuarios/{id}", id)
                .retrieve()
                .bodyToMono(UsuarioDto.class);
    }

    public Mono<UsuarioDto> agregar(UsuarioDto usuario) {
        return webClient.post()
                .uri("/usuarios")
                .bodyValue(usuario)
                .retrieve()
                .bodyToMono(UsuarioDto.class);
    }

    public Mono<UsuarioDto> modificar(long id, UsuarioDto usuario) {
        return webClient.put()
                .uri("/usuarios/{id}", id)
                .bodyValue(usuario)
                .retrieve()
                .bodyToMono(UsuarioDto.class);
    }

    public Mono<Void> eliminar(long id) {
        return webClient.delete()
                .uri("/usuarios/{id}", id)
                .retrieve()
                .bodyToMono(Void.class);
    }
}
