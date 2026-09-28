package cl.duoc.dsy2207.eventos.consumidora;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * FUNCIONES CONSUMIDORAS DE EVENTOS - VetCare, semana 7.
 *
 * Ambas escuchan el mismo Event Grid Topic, cada una mediante su PROPIA
 * suscripcion (patron publish/subscribe con fan-out):
 *
 *   sub-auditoria  (sin filtro)            -> RegistrarEventoLog
 *   sub-clinica    (filtro por eventType)  -> ProcesarEventoClinico
 *
 * Asi un mismo evento TurnoAgendado queda auditado Y genera el turno + recordatorio,
 * sin que la funcion productora sepa que existen estos dos consumidores.
 *
 * Base de datos: VeterinariaCloud (CITAS, COMUNICACIONES, INVENTARIO,
 * RESULTADOS_LABORATORIO) + EVENTOS_LOG / EVENTOS_PROCESADOS.
 *
 * Manejo de errores: si falla la base de datos se relanza la excepcion; la
 * invocacion termina con error y Event Grid REINTENTA la entrega (politica de
 * reintentos de la suscripcion). Los errores de negocio (turno inexistente,
 * stock insuficiente, mascota inexistente, duplicados) quedan registrados como
 * RECHAZADO en EVENTOS_PROCESADOS, porque reintentarlos no cambiaria el resultado.
 */
public class ConsumidoraFunction {

    private final EventoDao dao = new EventoDao();

    @FunctionName("RegistrarEventoLog")
    public void registrarEventoLog(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        logger.info("Funcion con Event Grid trigger ejecutada (auditoria).");

        EventoVetCare ev = EventoVetCare.desdeJson(content);
        logger.info("Evento recibido: " + ev.eventType() + " id=" + ev.id() + " subject=" + ev.subject());
        logger.info("Data del evento: " + ev.data());

        try {
            boolean nuevo = dao.registrarLog(ev);
            logger.info(nuevo
                    ? "Evento " + ev.id() + " guardado en EVENTOS_LOG"
                    : "Evento " + ev.id() + " ya existia en EVENTOS_LOG (entrega duplicada), se ignora");
        } catch (SQLException e) {
            logger.severe("Error guardando en EVENTOS_LOG, Event Grid reintentara: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    @FunctionName("ProcesarEventoClinico")
    public void procesarEventoClinico(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        logger.info("Funcion con Event Grid trigger ejecutada (procesamiento clinico).");

        EventoVetCare ev = EventoVetCare.desdeJson(content);
        logger.info("Tipo de evento: " + ev.eventType() + " | subject: " + ev.subject());

        try {
            String resultado = switch (ev.eventType()) {
                case EventoVetCare.TURNO_AGENDADO -> dao.procesar(ev, ev.dato("idTurno"), dao::turnoAgendado);
                case EventoVetCare.TURNO_CANCELADO -> dao.procesar(ev, ev.dato("idTurno"), dao::turnoCancelado);
                case EventoVetCare.MEDICACION_DISPENSADA ->
                        dao.procesar(ev, "ITEM-" + ev.datoLong("idItem"), dao::medicacionDispensada);
                case EventoVetCare.RESULTADO_LAB_LISTO -> dao.procesar(ev, ev.dato("idResultado"), dao::resultadoLabListo);
                default -> "Tipo de evento no manejado por esta funcion: " + ev.eventType();
            };
            logger.info(resultado);
        } catch (SQLException e) {
            logger.severe("Error procesando " + ev.eventType() + " (" + ev.id()
                    + "), Event Grid reintentara: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }
}
