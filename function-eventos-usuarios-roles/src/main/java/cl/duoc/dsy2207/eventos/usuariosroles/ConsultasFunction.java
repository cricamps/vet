package cl.duoc.dsy2207.eventos.usuariosroles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoint de solo lectura que evidencia el ULTIMO ESLABON del flujo: lo que
 * las funciones consumidoras dejaron en Oracle despues de procesar los eventos.
 *
 *   GET /api/consultas/auditoria                 UR_AUDITORIA_EVENTOS
 *   GET /api/consultas/procesados                UR_EVENTOS_PROCESADOS
 *   GET /api/consultas/notificaciones[?idUsuario=N]  UR_NOTIFICACIONES
 *   GET /api/consultas/usuarios                  USUARIOS + ROLES (estado final)
 *
 * El path se resuelve contra una lista cerrada: nunca se concatena al SQL.
 */
public class ConsultasFunction {

    private static final int LIMITE = 50;

    static final Map<String, String> CONSULTAS = new LinkedHashMap<>();

    static {
        CONSULTAS.put("auditoria", "SELECT ID_EVENTO, TIPO_EVENTO, SUBJECT, FECHA_EVENTO, FECHA_REGISTRO, PAYLOAD_JSON "
                + "FROM UR_AUDITORIA_EVENTOS ORDER BY FECHA_REGISTRO DESC");
        CONSULTAS.put("procesados", "SELECT ID_EVENTO, CONSUMIDOR, TIPO_EVENTO, SUBJECT, RESULTADO, DETALLE, FECHA_PROCESO "
                + "FROM UR_EVENTOS_PROCESADOS ORDER BY FECHA_PROCESO DESC");
        CONSULTAS.put("notificaciones", "SELECT ID_NOTIFICACION, ID_USUARIO, TIPO, MENSAJE, ID_EVENTO, FECHA "
                + "FROM UR_NOTIFICACIONES ORDER BY ID_NOTIFICACION DESC");
        CONSULTAS.put("usuarios", "SELECT u.ID_USUARIO, u.NOMBRE_USUARIO, u.PROFESION_USUARIO, u.PAIS, u.ID_ROL, r.NOMBRE_ROL "
                + "FROM USUARIOS u LEFT JOIN ROLES r ON r.ID_ROL = u.ID_ROL ORDER BY u.ID_USUARIO");
    }

    static final String NOTIFICACIONES_POR_USUARIO = "SELECT ID_NOTIFICACION, ID_USUARIO, TIPO, MENSAJE, ID_EVENTO, FECHA "
            + "FROM UR_NOTIFICACIONES WHERE ID_USUARIO = ? ORDER BY ID_NOTIFICACION DESC";

    private final EventoUsuariosRolesDao dao = new EventoUsuariosRolesDao();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @FunctionName("ConsultarEventosUsuariosRoles")
    public HttpResponseMessage consultar(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "consultas/{recurso}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("recurso") String recurso,
            final ExecutionContext context) {
        String sql = CONSULTAS.get(recurso);
        if (sql == null) {
            return responder(request, HttpStatus.NOT_FOUND,
                    Map.of("status", 404, "error", "Consulta no soportada", "opciones", CONSULTAS.keySet()));
        }
        try {
            String idUsuario = request.getQueryParameters().get("idUsuario");
            List<Map<String, Object>> filas;
            if ("notificaciones".equals(recurso) && idUsuario != null && !idUsuario.isBlank()) {
                filas = dao.consultar(NOTIFICACIONES_POR_USUARIO, LIMITE, Long.parseLong(idUsuario.trim()));
            } else {
                filas = dao.consultar(sql, LIMITE);
            }
            return responder(request, HttpStatus.OK, filas);
        } catch (NumberFormatException e) {
            return responder(request, HttpStatus.BAD_REQUEST, Map.of("status", 400, "error", "idUsuario debe ser numerico"));
        } catch (SQLException | IllegalStateException e) {
            context.getLogger().severe("Error en consulta " + recurso + ": " + e.getMessage());
            return responder(request, HttpStatus.INTERNAL_SERVER_ERROR,
                    Map.of("status", 500, "error", String.valueOf(e.getMessage())));
        }
    }

    private HttpResponseMessage responder(HttpRequestMessage<?> request, HttpStatus status, Object body) {
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(gson.toJson(body))
                .build();
    }
}
