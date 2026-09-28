package cl.duoc.dsy2207.eventos.productora;

import cl.duoc.dsy2207.eventos.productora.ConstructorEventos.EventoPreparado;
import cl.duoc.dsy2207.eventos.productora.ConstructorEventos.SolicitudInvalidaException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios de la logica de armado de eventos (lectura de la semana 7:
 * "Quality, Testing and Staging" - probar la logica sin depender de la nube).
 */
class ConstructorEventosTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static String futura() {
        return LocalDateTime.now().plusDays(3).withNano(0).toString();
    }

    @Test
    void turnoAgendadoValidoGeneraEventoConSubjectDelTurno() {
        EventoPreparado ev = ConstructorEventos.turnoAgendado(json("""
                {"idMascota":1,"idVeterinario":1,"fechaHora":"%s","motivo":"Vacuna"}""".formatted(futura())), "1");

        assertEquals(TipoEvento.TURNO_AGENDADO, ev.tipo());
        assertTrue(ev.idRecurso().matches("TUR-[A-F0-9]{8}"));
        assertEquals("/vetcare/turnos/" + ev.idRecurso(), ev.tipo().subject(ev.idRecurso()));
        assertEquals(1L, ev.data().get("idMascota"));
        assertEquals("1", ev.data().get("idUsuario"));
    }

    @Test
    void turnoEnElPasadoEsRechazado() {
        assertThrows(SolicitudInvalidaException.class, () -> ConstructorEventos.turnoAgendado(json("""
                {"idMascota":1,"idVeterinario":1,"fechaHora":"2020-01-01T10:00"}"""), "1"));
    }

    @Test
    void turnoSinVeterinarioEsRechazado() {
        SolicitudInvalidaException ex = assertThrows(SolicitudInvalidaException.class,
                () -> ConstructorEventos.turnoAgendado(json("""
                        {"idMascota":1,"fechaHora":"%s"}""".formatted(futura())), "1"));
        assertTrue(ex.getMessage().contains("idVeterinario"));
    }

    @Test
    void cancelacionConIdMalFormadoEsRechazada() {
        assertThrows(SolicitudInvalidaException.class,
                () -> ConstructorEventos.turnoCancelado("123", new JsonObject(), "1"));
    }

    @Test
    void medicacionConCantidadCeroEsRechazada() {
        assertThrows(SolicitudInvalidaException.class, () -> ConstructorEventos.medicacionDispensada(
                json("{\"idItem\":1,\"cantidad\":0}"), "1"));
    }

    @Test
    void medicacionUsaElItemComoSubject() {
        EventoPreparado ev = ConstructorEventos.medicacionDispensada(
                json("{\"idItem\":2,\"cantidad\":2,\"idMascota\":1}"), "1");
        assertEquals("ITEM-2", ev.idRecurso());
        assertEquals(2, ev.data().get("cantidad"));
        assertEquals(1L, ev.data().get("idMascota"));
    }

    @Test
    void resultadoLabGeneraIdLabYGuardaResultadoComoJson() {
        EventoPreparado ev = ConstructorEventos.resultadoLabListo(json("""
                {"idMascota":1,"laboratorioExterno":"LabVet Santiago","tipoExamen":"Hemograma",
                 "resultado":{"hematocrito":"45%"}}"""), "2");
        assertEquals(TipoEvento.RESULTADO_LAB_LISTO, ev.tipo());
        assertTrue(ev.idRecurso().startsWith("LAB-"));
        assertEquals("{\"hematocrito\":\"45%\"}", ev.data().get("resultado"));
    }
}
