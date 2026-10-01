package cl.duoc.dsy2207.usuarios;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del contrato del campo "data" de los eventos de Usuario (Semana 8). */
class UsuarioServiceTest {

    @Test
    void usuarioCreadoSinRolViajaConIdRolNull() {
        Usuario u = new Usuario(25L, "Ana Perez", "Disenadora", "Chile", null);
        Map<String, Object> data = UsuarioService.datosUsuario(u, "REST");

        assertEquals(25L, data.get("idUsuario"));
        assertEquals("Ana Perez", data.get("nombreUsuario"));
        assertNull(data.get("idRol"), "sin rol -> la consumidora asigna el rol por defecto");
        assertEquals("REST", data.get("canal"));
    }

    @Test
    void modificacionDetectaCambioDeRol() {
        Usuario antes = new Usuario(3L, "Cynthia Torres Leal", "Analista", "Chile", 4L);
        Usuario despues = new Usuario(3L, "Cynthia Torres Leal", "Analista", "Chile", 1L);
        Map<String, Object> data = UsuarioService.datosModificacion(antes, despues, "GraphQL");

        assertEquals(4L, data.get("idRolAnterior"));
        assertEquals(1L, data.get("idRol"));
        assertTrue((Boolean) data.get("cambioDeRol"));
    }

    @Test
    void modificacionSinCambioDeRol() {
        Usuario antes = new Usuario(3L, "Cynthia", "Analista", "Chile", 4L);
        Usuario despues = new Usuario(3L, "Cynthia Torres", "Analista", "Chile", 4L);
        Map<String, Object> data = UsuarioService.datosModificacion(antes, despues, "REST");

        assertFalse((Boolean) data.get("cambioDeRol"));
        assertEquals("Cynthia", data.get("nombreAnterior"));
    }

    @Test
    void subjectDelDominio() {
        assertEquals("/usuarios/7", PublicadorEventos.subject(7L));
    }
}
