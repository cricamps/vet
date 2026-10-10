package cl.duoc.dsy2207.eventos.usuariosroles;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Acceso a datos de las funciones consumidoras (base Oracle "usuariosroles").
 *
 * Tablas propias de la capa de eventos (db/script_eventos_s8.sql):
 *   UR_AUDITORIA_EVENTOS   - todos los eventos recibidos (sub-auditoria)
 *   UR_EVENTOS_PROCESADOS  - control de idempotencia por (evento, consumidor)
 *   UR_NOTIFICACIONES      - notificaciones generadas para cada usuario
 * y las tablas del dominio USUARIOS / ROLES (Sumativa 1).
 *
 * Idempotencia: Event Grid entrega "al menos una vez", por lo que un mismo
 * evento puede llegar repetido. Antes de aplicar cualquier efecto se inserta
 * (ID_EVENTO, CONSUMIDOR) en UR_EVENTOS_PROCESADOS dentro de la MISMA
 * transaccion; si ya existe, el evento se ignora.
 */
public class EventoUsuariosRolesDao {

    /** Error de negocio: no se reintenta, queda como RECHAZADO. */
    public static class RechazoNegocio extends Exception {
        private static final long serialVersionUID = 1L;

        public RechazoNegocio(String mensaje) {
            super(mensaje);
        }
    }

    /** Resultado de aplicar un evento. */
    public record Resultado(String estado, String detalle) {
        static Resultado procesado(String detalle) {
            return new Resultado("PROCESADO", detalle);
        }

        static Resultado sinAccion(String detalle) {
            return new Resultado("SIN_ACCION", detalle);
        }
    }

    @FunctionalInterface
    public interface Manejador {
        Resultado aplicar(Connection con, EventoDominio ev) throws SQLException, RechazoNegocio;
    }

    private record UsuarioActual(long id, String nombre, Long idRol) {
    }

    private record RolActual(long id, String nombre) {
    }

    // ================================================================ auditoria

    /** @return true si se inserto, false si el evento ya estaba registrado (entrega duplicada). */
    public boolean registrarAuditoria(EventoDominio ev) throws SQLException {
        String sql = "INSERT INTO UR_AUDITORIA_EVENTOS (ID_EVENTO, TIPO_EVENTO, SUBJECT, FECHA_EVENTO, PAYLOAD_JSON) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ev.id());
            ps.setString(2, ev.eventType());
            ps.setString(3, ev.subject());
            ps.setTimestamp(4, ev.eventTimeTimestamp());
            ps.setString(5, ev.json());
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException dup) {
            return false;
        }
    }

    // ============================================================ procesamiento

    /**
     * Aplica un manejador de forma idempotente y transaccional.
     * SQLException se relanza (Event Grid reintentara); RechazoNegocio queda registrado.
     */
    public String procesar(EventoDominio ev, String consumidor, Manejador manejador) throws SQLException {
        try (Connection con = DbConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO UR_EVENTOS_PROCESADOS (ID_EVENTO, CONSUMIDOR, TIPO_EVENTO, SUBJECT, RESULTADO) "
                            + "VALUES (?, ?, ?, ?, 'EN_PROCESO')")) {
                ps.setString(1, ev.id());
                ps.setString(2, consumidor);
                ps.setString(3, ev.eventType());
                ps.setString(4, ev.subject());
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                con.rollback();
                return "Evento " + ev.id() + " ya fue procesado por " + consumidor + " (entrega duplicada), se ignora";
            }

            try {
                Resultado r = manejador.aplicar(con, ev);
                actualizarResultado(con, ev.id(), consumidor, r.estado(), r.detalle());
                con.commit();
                return ev.eventType() + " -> " + r.estado() + ": " + r.detalle();
            } catch (RechazoNegocio rechazo) {
                con.rollback();
                registrarRechazo(con, ev, consumidor, rechazo.getMessage());
                return ev.eventType() + " -> RECHAZADO: " + rechazo.getMessage();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // --------------------------------------------------- manejadores de Usuario

    /** UsuarioCreado: asigna el rol por defecto si vino sin rol y envia la bienvenida. */
    public Resultado usuarioCreado(Connection con, EventoDominio ev) throws SQLException, RechazoNegocio {
        UsuarioActual u = usuarioExistente(con, ev.datoLong("idUsuario"));
        String rolFinal;
        String detalle;
        if (u.idRol() == null) {
            RolActual def = asignarRolPorDefecto(con, u.id(), ev.id());
            rolFinal = def.nombre();
            detalle = "rol por defecto " + def.nombre() + " asignado al usuario " + u.id() + "; ";
        } else {
            rolFinal = nombreRol(con, u.idRol());
            detalle = "";
        }
        notificar(con, u.id(), ev.id(), Mensajes.BIENVENIDA, Mensajes.bienvenida(u.nombre(), rolFinal));
        return Resultado.procesado(detalle + "bienvenida enviada a " + u.nombre());
    }

    /** UsuarioModificado: si quedo sin rol se le asigna el por defecto; si cambio de rol se le notifica. */
    public Resultado usuarioModificado(Connection con, EventoDominio ev) throws SQLException, RechazoNegocio {
        UsuarioActual u = usuarioExistente(con, ev.datoLong("idUsuario"));
        if (u.idRol() == null) {
            RolActual def = asignarRolPorDefecto(con, u.id(), ev.id());
            return Resultado.procesado("usuario " + u.id() + " quedo sin rol: se asigno " + def.nombre());
        }
        if (ev.datoBoolean("cambioDeRol")) {
            Long anterior = ev.datoLong("idRolAnterior");
            String nombreAnterior = anterior == null ? "(sin rol)" : nombreRol(con, anterior);
            String nombreNuevo = nombreRol(con, u.idRol());
            notificar(con, u.id(), ev.id(), Mensajes.CAMBIO_ROL, Mensajes.cambioRol(nombreAnterior, nombreNuevo));
            return Resultado.procesado("cambio de rol " + nombreAnterior + " -> " + nombreNuevo
                    + " notificado a " + u.nombre());
        }
        return Resultado.sinAccion("modificacion sin cambio de rol");
    }

    /** UsuarioEliminado: deja registro de la baja (el usuario ya no existe en USUARIOS). */
    public Resultado usuarioEliminado(Connection con, EventoDominio ev) throws SQLException, RechazoNegocio {
        Long id = ev.datoLong("idUsuario");
        if (id == null) {
            throw new RechazoNegocio("El evento no trae idUsuario");
        }
        String nombre = ev.dato("nombreUsuario") == null ? "#" + id : ev.dato("nombreUsuario");
        notificar(con, id, ev.id(), Mensajes.BAJA, Mensajes.baja(nombre));
        return Resultado.procesado("baja del usuario " + id + " registrada");
    }

    // ------------------------------------------------------- manejadores de Rol

    /** RolModificado: avisa a los usuarios que tienen ese rol que cambio de nombre. */
    public Resultado rolModificado(Connection con, EventoDominio ev) throws SQLException, RechazoNegocio {
        Long idRol = ev.datoLong("idRol");
        String anterior = ev.dato("nombreAnterior");
        String nuevo = ev.dato("nombreRol");
        if (idRol == null) {
            throw new RechazoNegocio("El evento no trae idRol");
        }
        if (anterior != null && anterior.equals(nuevo)) {
            return Resultado.sinAccion("el nombre del rol no cambio");
        }
        int n = 0;
        for (long idUsuario : usuariosConRol(con, idRol)) {
            notificar(con, idUsuario, ev.id(), Mensajes.ROL_RENOMBRADO, Mensajes.rolRenombrado(anterior, nuevo));
            n++;
        }
        return n == 0 ? Resultado.sinAccion("ningun usuario tiene el rol " + nuevo)
                : Resultado.procesado(n + " usuario(s) notificados del cambio de nombre del rol");
    }

    /**
     * RolEliminado (requerimiento EFT: "se les debera quitar" el rol).
     * function-roles ya dejo a los usuarios afectados con ID_ROL = NULL en la
     * misma transaccion del DELETE (la FK FK_USUARIOS_ROL lo exige). Aqui la
     * consumidora reacciona al evento: garantiza que ningun usuario siga
     * apuntando al rol eliminado (idempotente) y notifica a cada afectado que
     * su rol fue quitado. NO se reasigna ningun rol.
     */
    public Resultado rolEliminado(Connection con, EventoDominio ev) throws SQLException, RechazoNegocio {
        List<Long> afectados = ev.datoListaLong("usuariosAfectados");
        Long idRol = ev.datoLong("idRol");
        String rolEliminado = Mensajes.nombreRol(ev.dato("nombreRol"), idRol);
        int corregidos = 0;
        if (idRol != null) {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE USUARIOS SET ID_ROL = NULL WHERE ID_ROL = ?")) {
                ps.setLong(1, idRol);
                corregidos = ps.executeUpdate();
            }
        }
        if (afectados.isEmpty()) {
            return Resultado.sinAccion("el rol " + rolEliminado + " no tenia usuarios asignados");
        }
        List<Long> sinRol = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM USUARIOS WHERE ID_USUARIO = ? AND ID_ROL IS NULL")) {
            for (Long idUsuario : afectados) {
                ps.setLong(1, idUsuario);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        sinRol.add(idUsuario);
                        notificar(con, idUsuario, ev.id(), Mensajes.ROL_QUITADO,
                                Mensajes.rolQuitado(rolEliminado));
                    }
                }
            }
        }
        return Resultado.procesado("rol " + rolEliminado + " quitado a " + sinRol.size() + " de "
                + afectados.size() + " usuario(s) " + sinRol
                + (corregidos > 0 ? "; " + corregidos + " referencia(s) residual(es) limpiadas" : ""));
    }

    // ================================================================ consultas

    /** Ejecuta una consulta de solo lectura (SQL de una lista cerrada) con parametros opcionales. */
    public List<Map<String, Object>> consultar(String sql, int limite, Object... params) throws SQLException {
        List<Map<String, Object>> filas = new ArrayList<>();
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setMaxRows(limite);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        Object valor;
                        int tipo = md.getColumnType(i);
                        if (tipo == Types.CLOB || tipo == Types.NCLOB) {
                            valor = rs.getString(i);
                        } else if (tipo == Types.TIMESTAMP || tipo == Types.DATE
                                || tipo == Types.TIMESTAMP_WITH_TIMEZONE) {
                            Timestamp ts = rs.getTimestamp(i);
                            valor = ts == null ? null : ts.toLocalDateTime().toString();
                        } else {
                            valor = rs.getObject(i);
                        }
                        fila.put(md.getColumnLabel(i), valor);
                    }
                    filas.add(fila);
                }
            }
        }
        return filas;
    }

    // ================================================================= helpers

    private UsuarioActual usuarioExistente(Connection con, Long idUsuario) throws SQLException, RechazoNegocio {
        if (idUsuario == null) {
            throw new RechazoNegocio("El evento no trae idUsuario");
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ID_USUARIO, NOMBRE_USUARIO, ID_ROL FROM USUARIOS WHERE ID_USUARIO = ? FOR UPDATE")) {
            ps.setLong(1, idUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RechazoNegocio("El usuario " + idUsuario + " ya no existe");
                }
                long idRol = rs.getLong("ID_ROL");
                Long rol = rs.wasNull() ? null : idRol;
                return new UsuarioActual(rs.getLong("ID_USUARIO"), rs.getString("NOMBRE_USUARIO"), rol);
            }
        }
    }

    private RolActual rolPorDefecto(Connection con) throws SQLException, RechazoNegocio {
        String nombre = nombreRolPorDefecto();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ID_ROL, NOMBRE_ROL FROM ROLES WHERE UPPER(NOMBRE_ROL) = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RechazoNegocio("No existe el rol por defecto " + nombre + " en la tabla ROLES");
                }
                return new RolActual(rs.getLong(1), rs.getString(2));
            }
        }
    }

    private RolActual asignarRolPorDefecto(Connection con, long idUsuario, String idEvento)
            throws SQLException, RechazoNegocio {
        RolActual def = rolPorDefecto(con);
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE USUARIOS SET ID_ROL = ? WHERE ID_USUARIO = ? AND ID_ROL IS NULL")) {
            ps.setLong(1, def.id());
            ps.setLong(2, idUsuario);
            ps.executeUpdate();
        }
        notificar(con, idUsuario, idEvento, Mensajes.ROL_ASIGNADO, Mensajes.rolAsignadoPorDefecto(def.nombre()));
        return def;
    }

    private String nombreRol(Connection con, Long idRol) throws SQLException {
        if (idRol == null) {
            return "(sin rol)";
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT NOMBRE_ROL FROM ROLES WHERE ID_ROL = ?")) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "#" + idRol;
            }
        }
    }

    private List<Long> usuariosConRol(Connection con, long idRol) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ID_USUARIO FROM USUARIOS WHERE ID_ROL = ? ORDER BY ID_USUARIO")) {
            ps.setLong(1, idRol);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private void notificar(Connection con, long idUsuario, String idEvento, String tipo, String mensaje)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO UR_NOTIFICACIONES (ID_USUARIO, ID_EVENTO, TIPO, MENSAJE) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, idUsuario);
            ps.setString(2, idEvento);
            ps.setString(3, tipo);
            ps.setString(4, Mensajes.recortar(mensaje, 500));
            ps.executeUpdate();
        }
    }

    private void actualizarResultado(Connection con, String idEvento, String consumidor, String estado,
                                     String detalle) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE UR_EVENTOS_PROCESADOS SET RESULTADO = ?, DETALLE = ?, FECHA_PROCESO = SYSTIMESTAMP "
                        + "WHERE ID_EVENTO = ? AND CONSUMIDOR = ?")) {
            ps.setString(1, estado);
            ps.setString(2, Mensajes.recortar(detalle, 500));
            ps.setString(3, idEvento);
            ps.setString(4, consumidor);
            ps.executeUpdate();
        }
    }

    private void registrarRechazo(Connection con, EventoDominio ev, String consumidor, String motivo)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO UR_EVENTOS_PROCESADOS (ID_EVENTO, CONSUMIDOR, TIPO_EVENTO, SUBJECT, RESULTADO, DETALLE) "
                        + "VALUES (?, ?, ?, ?, 'RECHAZADO', ?)")) {
            ps.setString(1, ev.id());
            ps.setString(2, consumidor);
            ps.setString(3, ev.eventType());
            ps.setString(4, ev.subject());
            ps.setString(5, Mensajes.recortar(motivo, 500));
            ps.executeUpdate();
        }
        con.commit();
    }

    static String nombreRolPorDefecto() {
        String valor = System.getenv("ROL_POR_DEFECTO");
        return valor == null || valor.isBlank() ? "CONSULTA" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
