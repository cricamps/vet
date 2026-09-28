package cl.duoc.dsy2207.eventos.productora;

/**
 * Catalogo de eventos de negocio de VetCare (definido en el diseno EDA de la
 * semana 6). El valor de {@link #eventType()} es el campo "eventType" que viaja
 * en el esquema de Event Grid y es el que usan las suscripciones para filtrar.
 */
public enum TipoEvento {

    TURNO_AGENDADO("VetCare.TurnoAgendado", "/vetcare/turnos/"),
    TURNO_CANCELADO("VetCare.TurnoCancelado", "/vetcare/turnos/"),
    MEDICACION_DISPENSADA("VetCare.MedicacionDispensada", "/vetcare/inventario/"),
    RESULTADO_LAB_LISTO("VetCare.ResultadoLabListo", "/vetcare/laboratorio/");

    /** Version del contrato del campo "data" de cada evento. */
    public static final String DATA_VERSION = "1.0";

    private final String eventType;
    private final String prefijoSubject;

    TipoEvento(String eventType, String prefijoSubject) {
        this.eventType = eventType;
        this.prefijoSubject = prefijoSubject;
    }

    public String eventType() {
        return eventType;
    }

    /** Arma el "subject" del evento, p.ej. /vetcare/turnos/TUR-1A2B3C4D. */
    public String subject(String idRecurso) {
        return prefijoSubject + idRecurso;
    }
}
