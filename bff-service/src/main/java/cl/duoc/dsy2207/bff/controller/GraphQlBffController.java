package cl.duoc.dsy2207.bff.controller;

import cl.duoc.dsy2207.bff.client.GraphQlFunctionClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * EFT (Semana 9): el BFF orquesta tambien la capa GraphQL de las funciones.
 *
 *   POST /api/bff/graphql/usuarios  -> UsuariosGraphQL (function-usuarios)
 *   POST /api/bff/graphql/roles     -> RolesGraphQL    (function-roles)
 *
 * Body: { "query": "mutation { agregarUsuario(nombreUsuario: \"Ana\") { idUsuario idRol } }",
 *         "variables": { ... } }
 *
 * Las mutations GraphQL pasan por la misma capa de servicio que REST en las
 * funciones, por lo que publican los mismos eventos en Event Grid (canal = GraphQL)
 * y las consumidoras reaccionan igual (rol por defecto, rol quitado, etc.).
 */
@RestController
@RequestMapping(value = "/api/bff/graphql", produces = MediaType.APPLICATION_JSON_VALUE)
public class GraphQlBffController {

    private final GraphQlFunctionClient graphQlClient;

    public GraphQlBffController(GraphQlFunctionClient graphQlClient) {
        this.graphQlClient = graphQlClient;
    }

    @PostMapping("/usuarios")
    public Mono<ResponseEntity<String>> usuarios(@RequestBody Map<String, Object> operacion) {
        return validar(operacion).orElseGet(() -> graphQlClient.usuarios(operacion).map(GraphQlBffController::reenviar));
    }

    @PostMapping("/roles")
    public Mono<ResponseEntity<String>> roles(@RequestBody Map<String, Object> operacion) {
        return validar(operacion).orElseGet(() -> graphQlClient.roles(operacion).map(GraphQlBffController::reenviar));
    }

    static java.util.Optional<Mono<ResponseEntity<String>>> validar(Map<String, Object> operacion) {
        Object query = operacion == null ? null : operacion.get("query");
        if (!(query instanceof String q) || q.isBlank()) {
            return java.util.Optional.of(Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"El campo 'query' es obligatorio: { \\\"query\\\": \\\"...\\\", \\\"variables\\\": {...} }\"}")));
        }
        return java.util.Optional.empty();
    }

    private static ResponseEntity<String> reenviar(ResponseEntity<String> resp) {
        return ResponseEntity.status(resp.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(resp.getBody());
    }
}
