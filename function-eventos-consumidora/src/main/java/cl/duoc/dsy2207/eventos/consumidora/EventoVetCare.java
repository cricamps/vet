package cl.duoc.dsy2207.eventos.consumidora;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Evento recibido desde Event Grid, ya deserializado.
 *
 * Formato por defecto de todos los eventos (Event Grid Schema):
 * { "topic", "subject", "id", "eventType", "eventTime", "data": {...}, "dataVersion", "metadataVersion" }
 */
public record EventoVetCare(String id, String topic, String subject, String eventType,
                            String eventTime, String dataVersion, JsonObject data, String json) {

    public static final String TURNO_AGENDADO = "VetCare.TurnoAgendado";
    public static final String TURNO_CANCELADO = "VetCare.TurnoCancelado";
    public static final String MEDICACION_DISPENSADA = "VetCare.MedicacionDispensada";
    public static final String RESULTADO_LAB_LISTO = "VetCare.ResultadoLabListo";

    public static EventoVetCare desdeJson(String contenido) {
        JsonElement raiz = JsonParser.parseString(contenido);
        // Por seguridad: si llega un arreglo de eventos, se toma el primero
        // (el trigger de Event Grid entrega un evento por invocacion).
        JsonObject o = raiz.isJsonArray() ? raiz.getAsJsonArray().get(0).getAsJsonObject() : raiz.getAsJsonObject();

        JsonObject data;
        JsonElement d = o.get("data");
        if (d == null || d.isJsonNull()) {
            data = new JsonObject();
        } else if (d.isJsonPrimitive()) {
            // Algunos publicadores envian "data" como string con JSON adentro.
            JsonElement interno = JsonParser.parseString(d.getAsString());
            data = interno.isJsonObject() ? interno.getAsJsonObject() : new JsonObject();
        } else {
            data = d.getAsJsonObject();
        }

        return new EventoVetCare(
                str(o, "id"), str(o, "topic"), str(o, "subject"), str(o, "eventType"),
                str(o, "eventTime"), str(o, "dataVersion"), data, o.toString());
    }

    /** Lee un campo de "data" como texto (null si no viene). */
    public String dato(String campo) {
        return str(data, campo);
    }

    public int datoEntero(String campo) {
        JsonElement e = data.get(campo);
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    /** Lee un id numerico de "data" (null si no viene). */
    public Long datoLong(String campo) {
        JsonElement e = data.get(campo);
        return e == null || e.isJsonNull() ? null : e.getAsLong();
    }

    public Timestamp eventTimeTimestamp() {
        try {
            return Timestamp.from(OffsetDateTime.parse(eventTime).toInstant());
        } catch (RuntimeException e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    public static Timestamp fechaLocal(String iso) {
        try {
            return Timestamp.valueOf(LocalDateTime.parse(iso));
        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String campo) {
        JsonElement e = o == null ? null : o.get(campo);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
