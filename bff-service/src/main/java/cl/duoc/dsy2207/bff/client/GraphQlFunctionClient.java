package cl.duoc.dsy2207.bff.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * EFT (Semana 9): integracion BFF -> funciones serverless via GraphQL.
 *
 * Ademas de la capa REST, function-usuarios y function-roles exponen un
 * endpoint GraphQL cada una (UsuariosGraphQL -> /api/graphql/usuarios,
 * RolesGraphQL -> /api/graphql/roles). Este cliente reenvia la operacion
 * GraphQL ({ "query": "...", "variables": {...} }) a la funcion que
 * corresponde, reutilizando los mismos WebClient (y por lo tanto las mismas
 * URLs configurables por variables de entorno) que usa la capa REST.
 */
@Component
public class GraphQlFunctionClient {

    private final WebClient usuariosWebClient;
    private final WebClient rolesWebClient;

    public GraphQlFunctionClient(@Qualifier("usuariosWebClient") WebClient usuariosWebClient,
                                 @Qualifier("rolesWebClient") WebClient rolesWebClient) {
        this.usuariosWebClient = usuariosWebClient;
        this.rolesWebClient = rolesWebClient;
    }

    public Mono<ResponseEntity<String>> usuarios(Map<String, Object> operacion) {
        return ejecutar(usuariosWebClient, "/graphql/usuarios", operacion);
    }

    public Mono<ResponseEntity<String>> roles(Map<String, Object> operacion) {
        return ejecutar(rolesWebClient, "/graphql/roles", operacion);
    }

    private Mono<ResponseEntity<String>> ejecutar(WebClient client, String ruta, Map<String, Object> operacion) {
        return client.post()
                .uri(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(operacion)
                .exchangeToMono(resp -> resp.toEntity(String.class));
    }
}
