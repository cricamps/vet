package cl.duoc.dsy2207.eventos.productora;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Valida el cuerpo de cada request y arma el campo "data" de cada evento.
 * Es logica pura (sin Azure ni red) para poder probarla con tests unitarios.
 *
 * Los datos referencian el modelo de la base VeterinariaCloud (MASCOTAS,
 * EMPLEADOS, INVENTARIO): el productor solo manda ids; la funcion consumidora
 * es la que resuelve cliente, email, stock, etc. contra Oracle.
 */
public final class ConstructorEventos {

    private ConstructorEventos() {
    }

    /** Error de validacion del request: la funcion lo traduce a HTTP 400. */
    public static class SolicitudInvalidaException extends RuntimeException {
        public SolicitudInvalidaException(String mensaje) {
            super(mensaje);
        }
    }

    /** Resultado del armado: id del recurso (va en el subject) + data del evento. */
    public record EventoPreparado(TipoEvento tipo, String idRecurso, Map<String, Object> data) {
    }

    /** Agenda una cita: CITAS + recordatorio en COMUNICACIONES (lo hace la consumidora). */
    public static EventoPreparado turnoAgendado(JsonObject body, String idUsuario) {
        String idTurno = nuevoId("TUR");
        LocalDateTime fechaHora = fecha(body, "fechaHora");
        if (fechaHora.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw new SolicitudInvalidaException("fechaHora debe ser una fecha futura");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idTurno", idTurno);
        data.put("idMascota", idPositivo(body, "idMascota"));
        data.put("idVeterinario", idPositivo(body, "idVeterinario"));
        data.put("fechaHora", fechaHora.toString());
        data.put("motivo", textoOpcional(body, "motivo", "Control general"));
        data.put("idUsuario", idUsuario);
        return new EventoPreparado(TipoEvento.TURNO_AGENDADO, idTurno, data);
    }

    public static EventoPreparado turnoCancelado(String idTurno, JsonObject body, String idUsuario) {
        if (idTurno == null || !idTurno.matches("TUR-[A-F0-9]{8}")) {
            throw new SolicitudInvalidaException("idTurno invalido (formato esperado TUR-XXXXXXXX)");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idTurno", idTurno);
        data.put("motivo", textoOpcional(body, "motivo", "Cancelado por el cliente"));
        data.put("idUsuario", idUsuario);
        return new EventoPreparado(TipoEvento.TURNO_CANCELADO, idTurno, data);
    }

    /** Medicamento/insumo usado en una atencion: descuenta INVENTARIO.STOCK_ACTUAL. */
    public static EventoPreparado medicacionDispensada(JsonObject body, String idUsuario) {
        long idItem = idPositivo(body, "idItem");
        int cantidad = (int) entero(body, "cantidad");
        if (cantidad <= 0) {
            throw new SolicitudInvalidaException("cantidad debe ser mayor a 0");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idItem", idItem);
        data.put("cantidad", cantidad);
        data.put("idMascota", idOpcional(body, "idMascota"));
        data.put("idUsuario", idUsuario);
        return new EventoPreparado(TipoEvento.MEDICACION_DISPENSADA, "ITEM-" + idItem, data);
    }

    /** Resultado de laboratorio externo: RESULTADOS_LABORATORIO + aviso al dueno. */
    public static EventoPreparado resultadoLabListo(JsonObject body, String idUsuario) {
        String idResultado = nuevoId("LAB");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idResultado", idResultado);
        data.put("idMascota", idPositivo(body, "idMascota"));
        data.put("laboratorioExterno", texto(body, "laboratorioExterno"));
        data.put("tipoExamen", texto(body, "tipoExamen"));
        JsonElement resultado = body == null ? null : body.get("resultado");
        data.put("resultado", resultado == null || resultado.isJsonNull() ? null : resultado.toString());
        data.put("idUsuario", idUsuario);
        return new EventoPreparado(TipoEvento.RESULTADO_LAB_LISTO, idResultado, data);
    }

    // ---------------------------------------------------------------- helpers

    static String nuevoId(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String texto(JsonObject body, String campo) {
        JsonElement e = body == null ? null : body.get(campo);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive() || e.getAsString().isBlank()) {
            throw new SolicitudInvalidaException("El campo '" + campo + "' es obligatorio");
        }
        return e.getAsString().trim();
    }

    private static String textoOpcional(JsonObject body, String campo, String porDefecto) {
        JsonElement e = body == null ? null : body.get(campo);
        if (e == null || e.isJsonNull() || !e.isJsonPrimitive() || e.getAsString().isBlank()) {
            return porDefecto;
        }
        return e.getAsString().trim();
    }

    private static long entero(JsonObject body, String campo) {
        String valor = texto(body, campo);
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException ex) {
            throw new SolicitudInvalidaException("El campo '" + campo + "' debe ser un numero entero");
        }
    }

    private static long idPositivo(JsonObject body, String campo) {
        long v = entero(body, campo);
        if (v <= 0) {
            throw new SolicitudInvalidaException("El campo '" + campo + "' debe ser un id mayor a 0");
        }
        return v;
    }

    private static Long idOpcional(JsonObject body, String campo) {
        JsonElement e = body == null ? null : body.get(campo);
        if (e == null || e.isJsonNull() || e.getAsString().isBlank()) {
            return null;
        }
        return idPositivo(body, campo);
    }

    private static LocalDateTime fecha(JsonObject body, String campo) {
        String valor = texto(body, campo);
        try {
            return LocalDateTime.parse(valor);
        } catch (DateTimeParseException ex) {
            throw new SolicitudInvalidaException(
                    "El campo '" + campo + "' debe tener formato ISO, ej: 2026-10-05T10:30");
        }
    }
}
