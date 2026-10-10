package cl.duoc.dsy2207.roles;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pruebas del contrato de los eventos de Rol y de la regla del rol por defecto (Semana 8). */
class RolServiceTest {

    @Test
    void rolPorDefectoNoSePuedeEliminar() {
        assertThrows(RolService.RolProtegidoException.class,
                () -> RolService.validarEliminable(new Rol(5L, "consulta")));
    }

    @Test
    void otrosRolesSiSePuedenEliminar() {
        assertDoesNotThrow(() -> RolService.validarEliminable(new Rol(10L, "SOPORTE")));
    }

    @Test
    void rolEliminadoIncluyeUsuariosAfectadosSinRolDeReemplazo() {
        Map<String, Object> data = RolService.datosEliminacion(new Rol(10L, "SOPORTE"), List.of(21L, 22L), "REST");

        assertEquals(10L, data.get("idRol"));
        assertEquals("SOPORTE", data.get("nombreRol"));
        assertEquals(List.of(21L, 22L), data.get("usuariosAfectados"));
        assertFalse(data.containsKey("rolReemplazo"));
    }

    @Test
    void subjectDelDominio() {
        assertEquals("/roles/10", PublicadorEventos.subject(10L));
    }
}
