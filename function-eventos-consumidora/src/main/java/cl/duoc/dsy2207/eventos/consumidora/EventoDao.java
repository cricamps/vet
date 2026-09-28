package cl.duoc.dsy2207.eventos.consumidora;

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
import java.util.Map;

/**
 * Acceso a datos de la capa de eventos sobre la base VeterinariaCloud (vetcloud).
 *
 * Usa las tablas del sistema veterinario (CITAS, MASCOTAS, CLIENTES, COMUNICACIONES,
 * INVENTARIO, RESULTADOS_LABORATORIO) y dos tablas propias de la capa de eventos
 * (script db/script_eventos_s7.sql):
 *   - EVENTOS_LOG         : event store / auditoria (todos los eventos, JSON completo)
 *   - EVENTOS_PROCESADOS  : control de idempotencia + resultado de cada evento de negocio
 *   - NOTIFICACIONES      : alertas internas (STOCK_BAJO)
 *
 * IDEMPOTENCIA: Event Grid entrega "at least once". Cada evento de negocio primero
 * inserta su ID en EVENTOS_PROCESADOS dentro de la misma transaccion: si el evento
 * llega repetido, la PK falla (ORA-00001) y NO se vuelve a crear la cita ni a
 * descontar stock.
 */
public class EventoDao {

    private static final String DESTINO_INVENTARIO = "inventario@vetcare.cl";

    /** Error de negocio: el evento se marca RECHAZADO y no se reintenta. */
    public static class RechazoNegocio extends Exception {
        public RechazoNegocio(String mensaje) {
            super(mensaje);
        }
    }

    @FunctionalInterface
    interface Manejador {
        String aplicar(Connection con, EventoVetCare ev) throws SQLException, RechazoNegocio;
    }

    // ------------------------------------------------------------ auditoria

    /** @return true si se inserto, false si el evento ya estaba registrado (duplicado). */
    public boolean registrarLog(EventoVetCare ev) throws SQLException {
        String sql = "INSERT INTO EVENTOS_LOG (ID_EVENTO, TIPO_EVENTO, SUBJECT, ORIGEN, FECHA_EVENTO, PAYLOAD_JSON) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, ev.id());
            ps.setString(2, ev.eventType());
            ps.setString(3, ev.subject());
            ps.setString(4, ev.topic());
            ps.setTimestamp(5, ev.eventTimeTimestamp());
            ps.setString(6, ev.json());
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException dup) {
            return false;
        }
    }

    // ------------------------------------------------------------ plantilla transaccional

    /**
     * 1) registra el evento en EVENTOS_PROCESADOS (si ya estaba: duplicado, se ignora),
     * 2) ejecuta la logica de negocio, 3) guarda el resultado y hace commit.
     * Rechazos de negocio: rollback y se registra el evento como RECHAZADO (sin reintento).
     * Errores tecnicos (SQLException): rollback y se relanzan para que Event Grid reintente.
     */
    public String procesar(EventoVetCare ev, String referencia, Manejador manejador) throws SQLException {
        try (Connection con = DbConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO EVENTOS_PROCESADOS (ID_EVENTO, TIPO_EVENTO, REFERENCIA, RESULTADO) VALUES (?, ?, ?, 'EN_PROCESO')")) {
                ps.setString(1, ev.id());
                ps.setString(2, ev.eventType());
                ps.setString(3, referencia);
                ps.executeUpdate();
            } catch (SQLIntegrityConstraintViolationException dup) {
                con.rollback();
                return "Evento " + ev.id() + " ya fue procesado (entrega duplicada), se ignora";
            }

            try {
                String resultado = manejador.aplicar(con, ev);
                actualizarResultado(con, ev.id(), "OK: " + resultado);
                con.commit();
                return resultado;
            } catch (RechazoNegocio r) {
                return rechazar(con, ev, referencia, r.getMessage());
            } catch (SQLIntegrityConstraintViolationException fk) {
                // p.ej. ORA-02291: la mascota o el veterinario no existen
                return rechazar(con, ev, referencia, "Restriccion de integridad: " + primeraLinea(fk.getMessage()));
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private String rechazar(Connection con, EventoVetCare ev, String referencia, String motivo) throws SQLException {
        con.rollback();
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO EVENTOS_PROCESADOS (ID_EVENTO, TIPO_EVENTO, REFERENCIA, RESULTADO) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, ev.id());
            ps.setString(2, ev.eventType());
            ps.setString(3, referencia);
            ps.setString(4, recortar("RECHAZADO: " + motivo, 300));
            ps.executeUpdate();
        }
        con.commit();
        return "Evento " + ev.eventType() + " RECHAZADO: " + motivo;
    }

    private void actualizarResultado(Connection con, String idEvento, String resultado) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE EVENTOS_PROCESADOS SET RESULTADO = ? WHERE ID_EVENTO = ?")) {
            ps.setString(1, recortar(resultado, 300));
            ps.setString(2, idEvento);
            ps.executeUpdate();
        }
    }

    // ------------------------------------------------------------ citas (RF1 + RF2)

    /** TurnoAgendado: crea la CITA y programa el recordatorio (COMUNICACIONES) 24 h antes. */
    public String turnoAgendado(Connection con, EventoVetCare ev) throws SQLException, RechazoNegocio {
        Timestamp fechaHora = EventoVetCare.fechaLocal(ev.dato("fechaHora"));
        if (fechaHora == null) {
            throw new RechazoNegocio("fechaHora invalida");
        }
        Long idMascota = ev.datoLong("idMascota");
        Duenio d = duenioDeMascota(con, idMascota);

        long idCita;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO CITAS (ID_MASCOTA, ID_CLIENTE, ID_VETERINARIO, FECHA_HORA, MOTIVO, ESTADO, FECHA_CREACION) "
                        + "VALUES (?, ?, ?, ?, ?, 'AGENDADA', SYSTIMESTAMP)", new String[]{"ID_CITA"})) {
            ps.setLong(1, idMascota);
            ps.setLong(2, d.idCliente);
            ps.setLong(3, ev.datoLong("idVeterinario"));
            ps.setTimestamp(4, fechaHora);
            ps.setString(5, ev.dato("motivo"));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                idCita = keys.getLong(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE EVENTOS_PROCESADOS SET ID_CITA = ? WHERE ID_EVENTO = ?")) {
            ps.setLong(1, idCita);
            ps.setString(2, ev.id());
            ps.executeUpdate();
        }

        Timestamp recordatorio = new Timestamp(Math.max(
                fechaHora.getTime() - 24L * 60 * 60 * 1000, System.currentTimeMillis()));
        insertarComunicacion(con, d.idCliente, idCita, "Recordatorio de cita VetCare",
                "Hola " + d.nombre + ", te recordamos la cita de " + d.mascota + " el "
                        + ev.dato("fechaHora").replace('T', ' ') + " (" + ev.dato("motivo") + ").",
                "PROGRAMADO", recordatorio);

        return "Cita " + idCita + " (" + ev.dato("idTurno") + ") agendada para " + d.mascota
                + "; recordatorio programado " + recordatorio;
    }

    /** TurnoCancelado: CITAS.ESTADO = CANCELADA, anula el recordatorio y avisa al cliente. */
    public String turnoCancelado(Connection con, EventoVetCare ev) throws SQLException, RechazoNegocio {
        String idTurno = ev.dato("idTurno");
        Long idCita = null;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ID_CITA FROM EVENTOS_PROCESADOS WHERE REFERENCIA = ? AND TIPO_EVENTO = ? AND ID_CITA IS NOT NULL")) {
            ps.setString(1, idTurno);
            ps.setString(2, EventoVetCare.TURNO_AGENDADO);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    idCita = rs.getLong(1);
                }
            }
        }
        if (idCita == null) {
            throw new RechazoNegocio("No existe una cita creada para " + idTurno);
        }
        int filas;
        long idCliente;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE CITAS SET ESTADO = 'CANCELADA' WHERE ID_CITA = ? AND ESTADO IN ('AGENDADA', 'CONFIRMADA')")) {
            ps.setLong(1, idCita);
            filas = ps.executeUpdate();
        }
        if (filas == 0) {
            throw new RechazoNegocio("La cita " + idCita + " ya estaba cancelada o completada");
        }
        try (PreparedStatement ps = con.prepareStatement("SELECT ID_CLIENTE FROM CITAS WHERE ID_CITA = ?")) {
            ps.setLong(1, idCita);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idCliente = rs.getLong(1);
            }
        }
        int anulados;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE COMUNICACIONES SET ESTADO_ENVIO = 'CANCELADO' WHERE ID_CITA = ? AND ESTADO_ENVIO = 'PROGRAMADO'")) {
            ps.setLong(1, idCita);
            anulados = ps.executeUpdate();
        }
        insertarComunicacion(con, idCliente, idCita, "Cita cancelada",
                "Tu cita " + idCita + " fue cancelada. Motivo: " + ev.dato("motivo"),
                "PENDIENTE", new Timestamp(System.currentTimeMillis()));
        return "Cita " + idCita + " (" + idTurno + ") cancelada; recordatorios anulados: " + anulados;
    }

    // ------------------------------------------------------------ inventario (RF3)

    /** MedicacionDispensada: descuenta INVENTARIO.STOCK_ACTUAL y alerta si queda bajo el minimo. */
    public String medicacionDispensada(Connection con, EventoVetCare ev) throws SQLException, RechazoNegocio {
        long idItem = ev.datoLong("idItem");
        int cantidad = ev.datoEntero("cantidad");
        int filas;
        try (PreparedStatement u = con.prepareStatement(
                "UPDATE INVENTARIO SET STOCK_ACTUAL = STOCK_ACTUAL - ? WHERE ID_ITEM = ? AND STOCK_ACTUAL >= ?")) {
            u.setInt(1, cantidad);
            u.setLong(2, idItem);
            u.setInt(3, cantidad);
            filas = u.executeUpdate();
        }
        if (filas == 0) {
            throw new RechazoNegocio("Item " + idItem + " inexistente o con stock insuficiente para descontar " + cantidad);
        }
        String nombre;
        int stock;
        int minimo;
        try (PreparedStatement s = con.prepareStatement(
                "SELECT NOMBRE, STOCK_ACTUAL, STOCK_MINIMO FROM INVENTARIO WHERE ID_ITEM = ?")) {
            s.setLong(1, idItem);
            try (ResultSet rs = s.executeQuery()) {
                rs.next();
                nombre = rs.getString(1);
                stock = rs.getInt(2);
                minimo = rs.getInt(3);
            }
        }
        String extra = "";
        if (stock <= minimo) {
            try (PreparedStatement n = con.prepareStatement(
                    "INSERT INTO NOTIFICACIONES (ID_EVENTO, TIPO, DESTINATARIO, MENSAJE, FECHA_PROGRAMADA, ESTADO, REFERENCIA) "
                            + "VALUES (?, 'STOCK_BAJO', ?, ?, SYSTIMESTAMP, 'PENDIENTE', ?)")) {
                n.setString(1, ev.id());
                n.setString(2, DESTINO_INVENTARIO);
                n.setString(3, "Stock bajo de " + nombre + " (item " + idItem + "): quedan " + stock
                        + " (minimo " + minimo + ")");
                n.setString(4, "ITEM-" + idItem);
                n.executeUpdate();
            }
            extra = " -> ALERTA de stock bajo";
        }
        return nombre + ": stock descontado en " + cantidad + ", stock actual " + stock + extra;
    }

    // ------------------------------------------------------------ laboratorio (RF4)

    /** ResultadoLabListo: guarda el resultado y deja el aviso al dueno en COMUNICACIONES. */
    public String resultadoLabListo(Connection con, EventoVetCare ev) throws SQLException, RechazoNegocio {
        Long idMascota = ev.datoLong("idMascota");
        Duenio d = duenioDeMascota(con, idMascota);
        long idResultado;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO RESULTADOS_LABORATORIO (ID_MASCOTA, LABORATORIO_EXTERNO, TIPO_EXAMEN, RESULTADO_JSON, FECHA_RECEPCION) "
                        + "VALUES (?, ?, ?, ?, SYSTIMESTAMP)", new String[]{"ID_RESULTADO"})) {
            ps.setLong(1, idMascota);
            ps.setString(2, ev.dato("laboratorioExterno"));
            ps.setString(3, ev.dato("tipoExamen"));
            String resultado = ev.dato("resultado");
            if (resultado == null) {
                ps.setNull(4, Types.CLOB);
            } else {
                ps.setString(4, resultado);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                idResultado = keys.getLong(1);
            }
        }
        insertarComunicacion(con, d.idCliente, null, "Resultado de laboratorio disponible",
                "Hola " + d.nombre + ", el resultado de " + ev.dato("tipoExamen") + " de " + d.mascota
                        + " ya esta disponible en VetCare.",
                "PENDIENTE", new Timestamp(System.currentTimeMillis()));
        return "Resultado " + idResultado + " (" + ev.dato("idResultado") + ") registrado; aviso a " + d.email;
    }

    // ------------------------------------------------------------ consultas (GET)

    public List<Map<String, Object>> consultar(String sql, int limite) throws SQLException {
        List<Map<String, Object>> filas = new ArrayList<>();
        try (Connection con = DbConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setMaxRows(limite);
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

    // ------------------------------------------------------------ helpers

    private record Duenio(long idCliente, String nombre, String email, String mascota) {
    }

    private Duenio duenioDeMascota(Connection con, Long idMascota) throws SQLException, RechazoNegocio {
        if (idMascota == null) {
            throw new RechazoNegocio("Falta idMascota");
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT c.ID_CLIENTE, c.NOMBRE || ' ' || c.APELLIDO, c.EMAIL, m.NOMBRE "
                        + "FROM MASCOTAS m JOIN CLIENTES c ON c.ID_CLIENTE = m.ID_CLIENTE WHERE m.ID_MASCOTA = ?")) {
            ps.setLong(1, idMascota);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new RechazoNegocio("La mascota " + idMascota + " no existe");
                }
                return new Duenio(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4));
            }
        }
    }

    private void insertarComunicacion(Connection con, long idCliente, Long idCita, String asunto, String mensaje,
                                      String estado, Timestamp fecha) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO COMUNICACIONES (ID_CLIENTE, ID_CITA, CANAL, ASUNTO, MENSAJE, ESTADO_ENVIO, FECHA_ENVIO) "
                        + "VALUES (?, ?, 'EMAIL', ?, ?, ?, ?)")) {
            ps.setLong(1, idCliente);
            if (idCita == null) {
                ps.setNull(2, Types.NUMERIC);
            } else {
                ps.setLong(2, idCita);
            }
            ps.setString(3, recortar(asunto, 150));
            ps.setString(4, recortar(mensaje, 500));
            ps.setString(5, estado);
            ps.setTimestamp(6, fecha);
            ps.executeUpdate();
        }
    }

    private static String recortar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static String primeraLinea(String s) {
        return s == null ? "" : s.split("\\R", 2)[0];
    }
}
