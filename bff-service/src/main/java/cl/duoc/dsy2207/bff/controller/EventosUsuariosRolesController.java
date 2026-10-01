package cl.duoc.dsy2207.bff.controller;

import cl.duoc.dsy2207.bff.client.EventosUsuariosRolesClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semana 8 (Sumativa 3) - Evidencia del flujo orientado a eventos del Sistema de
 * Gestion de Usuarios y Roles:
 *
 *   Postman -> BFF -> function-usuarios / function-roles (CRUD + GENERADORAS)
 *           -> Azure Event Grid topic usuarios-roles-events
 *           -> sub-auditoria / sub-usuarios / sub-roles
 *           -> func-eventos-usuarios-roles (CONSUMIDORAS) -> Oracle
 *
 *   GET /api/bff/usuarios-roles/eventos/auditoria
 *   GET /api/bff/usuarios-roles/eventos/procesados
 *   GET /api/bff/usuarios-roles/eventos/notificaciones
 *   GET /api/bff/usuarios-roles/eventos/usuarios
 *   GET /api/bff/usuarios/{id}/notificaciones
 */
@RestController
@RequestMapping("/api/bff")
public class EventosUsuariosRolesController {

    static final Set<String> RECURSOS = Set.of("auditoria", "procesados", "notificaciones", "usuarios");

    private final EventosUsuariosRolesClient client;

    public EventosUsuariosRolesController(EventosUsuariosRolesClient client) {
        this.client = client;
    }

    @GetMapping("/usuarios-roles/eventos/{recurso}")
    public Mono<List<Map<String, Object>>> consultar(@PathVariable String recurso) {
        if (!RECURSOS.contains(recurso)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Consulta no soportada. Opciones: " + RECURSOS));
        }
        return client.consultar(recurso, null);
    }

    @GetMapping("/usuarios/{id}/notificaciones")
    public Mono<List<Map<String, Object>>> notificacionesDeUsuario(@PathVariable long id) {
        return client.consultar("notificaciones", id);
    }
}
