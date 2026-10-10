package cl.duoc.dsy2207.bff.controller;

import cl.duoc.dsy2207.bff.client.RolesFunctionClient;
import cl.duoc.dsy2207.bff.dto.RolDto;
import cl.duoc.dsy2207.bff.util.EventoHeaders;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Orquesta las operaciones de Roles contra la funcion serverless
 * correspondiente.
 *
 * Semana 8: al eliminar un rol en uso, function-roles desasigna a sus usuarios
 * (quedan sin rol) y publica RolEliminado; la funcion consumidora confirma
 * que el rol fue quitado y notifica a los afectados. Eliminar el rol por defecto (CONSULTA) responde 409.
 */
@RestController
@RequestMapping("/api/bff/roles")
public class RolBffController {

    private final RolesFunctionClient rolesClient;

    public RolBffController(RolesFunctionClient rolesClient) {
        this.rolesClient = rolesClient;
    }

    @GetMapping
    public Mono<List<RolDto>> listar() {
        return rolesClient.listar();
    }

    @GetMapping("/{id}")
    public Mono<RolDto> obtener(@PathVariable long id) {
        return rolesClient.obtener(id);
    }

    @PostMapping
    public Mono<ResponseEntity<RolDto>> agregar(@Valid @RequestBody RolDto rol) {
        return rolesClient.agregar(rol)
                .map(resp -> EventoHeaders.reenviar(resp, HttpStatus.CREATED));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<RolDto>> modificar(@PathVariable long id, @Valid @RequestBody RolDto rol) {
        return rolesClient.modificar(id, rol)
                .map(resp -> EventoHeaders.reenviar(resp, HttpStatus.OK));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> eliminar(@PathVariable long id) {
        return rolesClient.eliminar(id)
                .map(resp -> ResponseEntity.noContent().headers(EventoHeaders.copiar(resp.getHeaders())).build());
    }
}
