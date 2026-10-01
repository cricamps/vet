package cl.duoc.dsy2207.eventos.usuariosroles;

import cl.duoc.dsy2207.eventos.usuariosroles.EventoUsuariosRolesDao.Manejador;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.EventGridTrigger;
import com.microsoft.azure.functions.annotation.FunctionName;

import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * FUNCIONES CONSUMIDORAS DE EVENTOS - Sistema de Gestion de Usuarios y Roles
 * (Semana 8, Actividad Sumativa 3).
 *
 * Las tres escuchan el MISMO Event Grid Topic (usuarios-roles-events), cada una
 * mediante su PROPIA suscripcion (publish/subscribe con fan-out y filtros):
 *
 *   sub-auditoria (sin filtro)                          -> AuditarEventoUsuariosRoles
 *   sub-usuarios  (UsuarioCreado/Modificado/Eliminado)  -> ProcesarEventoUsuario
 *   sub-roles     (RolModificado/RolEliminado)          -> ProcesarEventoRol
 *
 * Asi un UsuarioCreado queda auditado Y genera la asignacion de rol por defecto +
 * la bienvenida, sin que function-usuarios sepa que existen estos consumidores.
 *
 * Manejo de errores: si falla Oracle se relanza la excepcion; la invocacion
 * termina con error y Event Grid REINTENTA la entrega (politica de reintentos
 * de la suscripcion). Los errores de negocio (usuario inexistente, rol por
 * defecto inexistente) quedan como RECHAZADO en UR_EVENTOS_PROCESADOS porque
 * reintentarlos no cambiaria el resultado.
 */
public class ConsumidorasFunction {

    static final String CONSUMIDOR_USUARIOS = "ProcesarEventoUsuario";
    static final String CONSUMIDOR_ROLES = "ProcesarEventoRol";

    private final EventoUsuariosRolesDao dao = new EventoUsuariosRolesDao();

    @FunctionName("AuditarEventoUsuariosRoles")
    public void auditar(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        Logger logger = context.getLogger();
        EventoDominio ev = EventoDominio.desdeJson(content);
        logger.info("[auditoria] Evento recibido: " + ev.eventType() + " id=" + ev.id() + " subject=" + ev.subject());
        logger.info("[auditoria] Data: " + ev.data());
        try {
            boolean nuevo = dao.registrarAuditoria(ev);
            logger.info(nuevo
                    ? "[auditoria] Evento " + ev.id() + " guardado en UR_AUDITORIA_EVENTOS"
                    : "[auditoria] Evento " + ev.id() + " ya estaba registrado (entrega duplicada), se ignora");
        } catch (SQLException e) {
            logger.severe("[auditoria] Error en Oracle, Event Grid reintentara: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }

    @FunctionName("ProcesarEventoUsuario")
    public void procesarEventoUsuario(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        EventoDominio ev = EventoDominio.desdeJson(content);
        Manejador manejador = switch (ev.eventType() == null ? "" : ev.eventType()) {
            case EventoDominio.USUARIO_CREADO -> dao::usuarioCreado;
            case EventoDominio.USUARIO_MODIFICADO -> dao::usuarioModificado;
            case EventoDominio.USUARIO_ELIMINADO -> dao::usuarioEliminado;
            default -> null;
        };
        ejecutar(ev, CONSUMIDOR_USUARIOS, manejador, context.getLogger());
    }

    @FunctionName("ProcesarEventoRol")
    public void procesarEventoRol(
            @EventGridTrigger(name = "eventGridEvent") String content,
            final ExecutionContext context) {
        EventoDominio ev = EventoDominio.desdeJson(content);
        Manejador manejador = switch (ev.eventType() == null ? "" : ev.eventType()) {
            case EventoDominio.ROL_MODIFICADO -> dao::rolModificado;
            case EventoDominio.ROL_ELIMINADO -> dao::rolEliminado;
            default -> null;
        };
        ejecutar(ev, CONSUMIDOR_ROLES, manejador, context.getLogger());
    }

    private void ejecutar(EventoDominio ev, String consumidor, Manejador manejador, Logger logger) {
        logger.info("[" + consumidor + "] " + ev.eventType() + " id=" + ev.id() + " subject=" + ev.subject());
        if (manejador == null) {
            // No deberia ocurrir gracias al filtro de la suscripcion.
            logger.warning("[" + consumidor + "] Tipo de evento no manejado: " + ev.eventType());
            return;
        }
        try {
            logger.info("[" + consumidor + "] " + dao.procesar(ev, consumidor, manejador));
        } catch (SQLException e) {
            logger.severe("[" + consumidor + "] Error en Oracle procesando " + ev.id()
                    + ", Event Grid reintentara: " + e.getMessage());
            throw new IllegalStateException(e);
        }
    }
}
