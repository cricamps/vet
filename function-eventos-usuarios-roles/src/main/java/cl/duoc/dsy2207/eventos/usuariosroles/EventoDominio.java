package cl.duoc.dsy2207.eventos.usuariosroles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Evento de dominio del Sistema de Gestion de Usuarios y Roles, tal como lo
 * entrega Azure Event Grid (Event Grid Schema):
 *
 * { "id", "topic", "subject", "eventType", "eventTime", "data": {...}, "dataVersion", "metadataVersion" }
 *
 * Catalogo de eventos (los publican function-usuarios y function-roles):
 *   UsuariosRoles.UsuarioCreado     data: idUsuario, nombreUsuario, profesionUsuario, pais, idRol, canal
 *   UsuariosRoles.UsuarioModificado data: ... + idRolAnterior, cambioDeRol, nombreAnterior
 *   UsuariosRoles.UsuarioEliminado  data: foto del usuario antes de eliminarlo
 *   UsuariosRoles.RolCreado         data: idRol, nombreRol, canal
 *   UsuariosRoles.RolModificado     data: idRol, nombreRol, nombreAnterior, canal
 *   UsuariosRoles.RolEliminado      data: idRol, nombreRol, usuariosAfectados[], rolReemplazo, canal
 */
public record EventoDominio(String id, String topic, String subject, String eventType,
                            String eventTime, String dataVersion, JsonObject data, String json) {

    public static final String USUARIO_CREADO = "UsuariosRoles.UsuarioCreado";
    public static final String USUARIO_MODIFICADO = "UsuariosRoles.UsuarioModificado";
    public static final String USUARIO_ELIMINADO = "UsuariosRoles.UsuarioEliminado";
    public static final String ROL_CREADO = "UsuariosRoles.RolCreado";
    public static final String ROL_MODIFICADO = "UsuariosRoles.RolModificado";
    public static final String ROL_ELIMINADO = "UsuariosRoles.RolEliminado";

    public static EventoDominio desdeJson(String contenido) {
        JsonElement raiz = JsonParser.parseString(contenido);
        // El trigger de Event Grid entrega un evento por invocacion; si llegara
        // un arreglo, se toma el primero.
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

        return new EventoDominio(
                str(o, "id"), str(o, "topic"), str(o, "subject"), str(o, "eventType"),
                str(o, "eventTime"), str(o, "dataVersion"), data, o.toString());
    }

    /** Campo de "data" como texto (null si no viene). */
    public String dato(String campo) {
        return str(data, campo);
    }

    /** Campo numerico de "data" (null si no viene o es null). */
    public Long datoLong(String campo) {
        JsonElement e = data.get(campo);
        return e == null || e.isJsonNull() ? null : e.getAsLong();
    }

    public boolean datoBoolean(String campo) {
        JsonElement e = data.get(campo);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }

    /** Lista de ids de "data" (vacia si no viene). */
    public List<Long> datoListaLong(String campo) {
        List<Long> ids = new ArrayList<>();
        JsonElement e = data.get(campo);
        if (e != null && e.isJsonArray()) {
            JsonArray arr = e.getAsJsonArray();
            for (JsonElement x : arr) {
                if (x != null && !x.isJsonNull()) {
                    ids.add(x.getAsLong());
                }
            }
        }
        return ids;
    }

    public Timestamp eventTimeTimestamp() {
        try {
            return Timestamp.from(OffsetDateTime.parse(eventTime).toInstant());
        } catch (RuntimeException e) {
            return new Timestamp(System.currentTimeMillis());
        }
    }

    private static String str(JsonObject o, String campo) {
        JsonElement e = o == null ? null : o.get(campo);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
