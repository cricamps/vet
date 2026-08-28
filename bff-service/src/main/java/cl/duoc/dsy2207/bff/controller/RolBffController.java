package cl.duoc.dsy2207.bff.controller;

import cl.duoc.dsy2207.bff.client.RolesFunctionClient;
import cl.duoc.dsy2207.bff.dto.RolDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Orquesta las operaciones de Roles contra la funcion serverless
 * correspondiente.
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
                .map(creado -> ResponseEntity.status(HttpStatus.CREATED).body(creado));
    }

    @PutMapping("/{id}")
    public Mono<RolDto> modificar(@PathVariable long id, @Valid @RequestBody RolDto rol) {
        return rolesClient.modificar(id, rol);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> eliminar(@PathVariable long id) {
        return rolesClient.eliminar(id)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
