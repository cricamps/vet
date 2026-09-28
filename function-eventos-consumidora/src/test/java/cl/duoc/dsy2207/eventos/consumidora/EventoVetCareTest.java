package cl.duoc.dsy2207.eventos.consumidora;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Verifica que la funcion consumidora entiende el formato por defecto de Event Grid. */
class EventoVetCareTest {

    private static final String EVENTO = """
            {"id":"a1b2","topic":"/subscriptions/x/resourceGroups/rg/providers/Microsoft.EventGrid/topics/vetcare",
             "subject":"/vetcare/turnos/TUR-1A2B3C4D","eventType":"VetCare.TurnoAgendado",
             "eventTime":"2026-09-26T20:15:00.123Z","dataVersion":"1.0","metadataVersion":"1",
             "data":{"idTurno":"TUR-1A2B3C4D","mascota":"Firulais","fechaHora":"2026-10-05T10:30","cantidad":2}}""";

    @Test
    void parseaEsquemaEventGrid() {
        EventoVetCare ev = EventoVetCare.desdeJson(EVENTO);
        assertEquals("a1b2", ev.id());
        assertEquals(EventoVetCare.TURNO_AGENDADO, ev.eventType());
        assertEquals("TUR-1A2B3C4D", ev.dato("idTurno"));
        assertEquals(2, ev.datoEntero("cantidad"));
        assertNotNull(ev.eventTimeTimestamp());
        assertNotNull(EventoVetCare.fechaLocal(ev.dato("fechaHora")));
    }

    @Test
    void aceptaArregloYDataComoString() {
        String arreglo = "[{\"id\":\"z9\",\"eventType\":\"VetCare.ResultadoLabListo\","
                + "\"data\":\"{\\\"examen\\\":\\\"Hemograma\\\"}\"}]";
        EventoVetCare ev = EventoVetCare.desdeJson(arreglo);
        assertEquals("z9", ev.id());
        assertEquals("Hemograma", ev.dato("examen"));
        assertNull(ev.dato("noExiste"));
    }
}
