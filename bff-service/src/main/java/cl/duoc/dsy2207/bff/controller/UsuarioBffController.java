package cl.duoc.dsy2207.bff.controller;

import cl.duoc.dsy2207.bff.client.RolesFunctionClient;
import cl.duoc.dsy2207.bff.client.UsuariosFunctionClient;
import cl.duoc.dsy2207.bff.dto.UsuarioDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Orquesta las operaciones de Usuarios contra la funcion serverless
 * correspondiente. Cuando el usuario incluye "idRol", el BFF primero
 * valida contra la Funcion de Roles que ese rol exista (llamada
 * encadenada a un segundo dominio) antes de agregar el usuario -- este
 * es el escenario de orquestacion a demostrar en el video.
 */
@RestController
@RequestMapping("/api/bff/usuarios")
public class UsuarioBffController {

    private final UsuariosFunctionClient usuariosClient;
    private final RolesFunctionClient rolesClient;

    public UsuarioBffController(UsuariosFunctionClient usuariosClient, RolesFunctionClient rolesClient) {
        this.usuariosClient = usuariosClient;
        this.rolesClient = rolesClient;
    }

    @GetMapping
    public Mono<List<UsuarioDto>> listar() {
        return usuariosClient.listar();
    }

    @GetMapping("/{id}")
    public Mono<UsuarioDto> obtener(@PathVariable long id) {
        return usuariosClient.obtener(id);
    }

    @PostMapping
    public Mono<ResponseEntity<UsuarioDto>> agregar(@Valid @RequestBody UsuarioDto usuario) {
        Mono<Void> validacionRol = usuario.getIdRol() != null
                ? rolesClient.obtener(usuario.getIdRol())
                        .switchIfEmpty(Mono.error(new ResponseStatusException(
                                HttpStatus.BAD_REQUEST, "El rol " + usuario.getIdRol() + " no existe")))
                        .then()
                : Mono.empty();

        return validacionRol
                .then(usuariosClient.agregar(usuario))
                .map(creado -> ResponseEntity.status(HttpStatus.CREATED).body(creado));
    }

    @PutMapping("/{id}")
    public Mono<UsuarioDto> modificar(@PathVariable long id, @Valid @RequestBody UsuarioDto usuario) {
        return usuariosClient.modificar(id, usuario);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> eliminar(@PathVariable long id) {
        return usuariosClient.eliminar(id)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
