package cl.duoc.dsy2207.bff.controller;

import cl.duoc.dsy2207.bff.client.EventosFunctionClient;
import cl.duoc.dsy2207.bff.client.RolesFunctionClient;
import cl.duoc.dsy2207.bff.client.UsuariosFunctionClient;
import cl.duoc.dsy2207.bff.dto.CancelacionRequest;
import cl.duoc.dsy2207.bff.dto.MedicacionRequest;
import cl.duoc.dsy2207.bff.dto.ResultadoLabRequest;
import cl.duoc.dsy2207.bff.dto.TurnoRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PUNTO DE ENTRADA del flujo orientado a eventos (semana 7).
 *
 *   Cliente -> BFF (valida usuario y rol contra function-usuarios / function-roles)
 *           -> funcion generadora de eventos -> Azure Event Grid (topic)
 *           -> suscripciones -> funciones consumidoras -> Oracle
 *
 * RF6: solo los roles configurados en eventos.roles-autorizados (por defecto
 * ADMINISTRADOR y OPERADOR) pueden generar eventos. Un usuario con rol CONSULTA
 * recibe 403 y el evento nunca llega a publicarse.
 *
 * Las operaciones de escritura responden 202 Accepted: el BFF confirma que el
 * evento fue publicado; su efecto (turno, stock, notificacion) ocurre de forma
 * asincrona y se puede verificar con los GET de /api/bff/eventos/consultas/... (base VeterinariaCloud)
 */
@RestController
@RequestMapping("/api/bff/eventos")
public class EventoBffController {

    private static final Logger log = LoggerFactory.getLogger(EventoBffController.class);
    private static final Set<String> CONSULTAS = Set.of("eventos", "procesados", "citas", "comunicaciones", "inventario", "resultados", "alertas");

    private final UsuariosFunctionClient usuariosClient;
    private final RolesFunctionClient rolesClient;
    private final EventosFunctionClient eventosClient;
    private final Set<String> rolesAutorizados;

    public EventoBffController(UsuariosFunctionClient usuariosClient,
                               RolesFunctionClient rolesClient,
                               EventosFunctionClient eventosClient,
                               @Value("${eventos.roles-autorizados:ADMINISTRADOR,OPERADOR}") List<String> roles) {
        this.usuariosClient = usuariosClient;
        this.rolesClient = rolesClient;
        this.eventosClient = eventosClient;
        this.rolesAutorizados = roles.stream()
                .map(r -> r.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------ productores

    @PostMapping("/turnos")
    public Mono<ResponseEntity<Map<String, Object>>> agendarTurno(
            @RequestHeader("X-Usuario-Id") long idUsuario,
            @Valid @RequestBody TurnoRequest turno) {
        return publicarAutorizado(idUsuario, "/eventos/turnos", turno);
    }

    @PostMapping("/turnos/{idTurno}/cancelar")
    public Mono<ResponseEntity<Map<String, Object>>> cancelarTurno(
            @RequestHeader("X-Usuario-Id") long idUsuario,
            @PathVariable String idTurno,
            @RequestBody(required = false) CancelacionRequest cancelacion) {
        return publicarAutorizado(idUsuario, "/eventos/turnos/" + idTurno + "/cancelar",
                cancelacion != null ? cancelacion : new CancelacionRequest(null));
    }

    @PostMapping("/medicacion")
    public Mono<ResponseEntity<Map<String, Object>>> dispensarMedicamento(
            @RequestHeader("X-Usuario-Id") long idUsuario,
            @Valid @RequestBody MedicacionRequest medicacion) {
        return publicarAutorizado(idUsuario, "/eventos/medicacion", medicacion);
    }

    @PostMapping("/laboratorio")
    public Mono<ResponseEntity<Map<String, Object>>> registrarResultadoLab(
            @RequestHeader("X-Usuario-Id") long idUsuario,
            @Valid @RequestBody ResultadoLabRequest resultado) {
        return publicarAutorizado(idUsuario, "/eventos/laboratorio", resultado);
    }

    // ------------------------------------------------------------ ultimo eslabon

    @GetMapping("/consultas/{recurso}")
    public Mono<List<Map<String, Object>>> consultar(@PathVariable String recurso) {
        if (!CONSULTAS.contains(recurso)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Consulta no soportada. Opciones: " + CONSULTAS));
        }
        return eventosClient.consultar(recurso);
    }

    // ------------------------------------------------------------ orquestacion

    /**
     * 1) function-usuarios: el usuario existe y tiene rol
     * 2) function-roles: el nombre del rol esta autorizado
     * 3) funcion generadora: publica el evento en Event Grid
     */
    private Mono<ResponseEntity<Map<String, Object>>> publicarAutorizado(long idUsuario, String ruta, Object cuerpo) {
        return usuariosClient.obtener(idUsuario)
                .flatMap(usuario -> {
                    if (usuario.getIdRol() == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "El usuario " + idUsuario + " no tiene rol asignado"));
                    }
                    return rolesClient.obtener(usuario.getIdRol());
                })
                .flatMap(rol -> {
                    String nombre = rol.getNombreRol() == null ? "" : rol.getNombreRol().toUpperCase(Locale.ROOT);
                    if (!rolesAutorizados.contains(nombre)) {
                        log.warn("Usuario {} con rol {} intento generar un evento en {}", idUsuario, nombre, ruta);
                        return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                                "El rol " + nombre + " no esta autorizado para generar eventos (permitidos: "
                                        + rolesAutorizados + ")"));
                    }
                    log.info("Usuario {} (rol {}) autorizado, publicando evento via {}", idUsuario, nombre, ruta);
                    return eventosClient.publicar(ruta, cuerpo, idUsuario);
                })
                .map(respuesta -> ResponseEntity.status(HttpStatus.ACCEPTED).body(respuesta));
    }
}
