package cl.duoc.dsy2207.eventos.usuariosroles;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pruebas del parseo del Event Grid Schema y de los textos de notificacion (Semana 8). */
class EventoDominioTest {

    private static final String USUARIO_CREADO = """
            {"id":"a1b2","topic":"/subscriptions/x/topics/usuarios-roles-events","subject":"/usuarios/25",
             "eventType":"UsuariosRoles.UsuarioCreado","eventTime":"2026-10-02T15:04:05.123Z",
             "data":{"idUsuario":25,"nombreUsuario":"Ana Perez","idRol":null,"canal":"REST"},
             "dataVersion":"1.0","metadataVersion":"1"}""";

    @Test
    void parseaUsuarioCreadoSinRol() {
        EventoDominio ev = EventoDominio.desdeJson(USUARIO_CREADO);
        assertEquals("a1b2", ev.id());
        assertEquals(EventoDominio.USUARIO_CREADO, ev.eventType());
        assertEquals("/usuarios/25", ev.subject());
        assertEquals(25L, ev.datoLong("idUsuario"));
        assertNull(ev.datoLong("idRol"));
        assertEquals("Ana Perez", ev.dato("nombreUsuario"));
    }

    @Test
    void aceptaArregloYDataComoString() {
        String json = "[{\"id\":\"x9\",\"eventType\":\"UsuariosRoles.RolEliminado\",\"subject\":\"/roles/10\","
                + "\"data\":\"{\\\"idRol\\\":10,\\\"usuariosAfectados\\\":[21,22]}\"}]";
        EventoDominio ev = EventoDominio.desdeJson(json);
        assertEquals("x9", ev.id());
        assertEquals(List.of(21L, 22L), ev.datoListaLong("usuariosAfectados"));
    }

    @Test
    void booleanosYListasAusentes() {
        EventoDominio ev = EventoDominio.desdeJson("{\"id\":\"1\",\"data\":{\"cambioDeRol\":true}}");
        assertTrue(ev.datoBoolean("cambioDeRol"));
        assertFalse(ev.datoBoolean("otro"));
        assertTrue(ev.datoListaLong("usuariosAfectados").isEmpty());
    }

    @Test
    void eventTimeInvalidoNoRompe() {
        EventoDominio ev = EventoDominio.desdeJson("{\"id\":\"1\",\"eventTime\":\"no-es-fecha\"}");
        assertTrue(ev.eventTimeTimestamp() != null);
    }

    @Test
    void textosDeNotificacion() {
        assertEquals("Tu rol cambio de OPERADOR a ADMINISTRADOR.", Mensajes.cambioRol("OPERADOR", "ADMINISTRADOR"));
        assertEquals("#7", Mensajes.nombreRol(null, 7L));
        assertEquals("abc", Mensajes.recortar("abcdef", 3));
    }

    @Test
    void consultasSoloDeListaCerrada() {
        assertEquals(List.of("auditoria", "procesados", "notificaciones", "usuarios"),
                List.copyOf(ConsultasFunction.CONSULTAS.keySet()));
    }
}
