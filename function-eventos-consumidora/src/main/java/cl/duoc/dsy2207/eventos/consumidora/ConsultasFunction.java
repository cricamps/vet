package cl.duoc.dsy2207.eventos.consumidora;

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
 * Endpoint de solo lectura para evidenciar el ULTIMO ESLABON del flujo: lo que
 * la funcion consumidora dejo en la base VeterinariaCloud despues de procesar
 * los eventos. GET /api/consultas/{recurso}. Lo usa el BFF y sirve para el video.
 */
public class ConsultasFunction {

    private static final int LIMITE = 50;

    /** recurso -> SQL (lista cerrada: el path nunca se concatena al SQL). */
    static final Map<String, String> CONSULTAS = new LinkedHashMap<>();

    static {
        CONSULTAS.put("eventos", "SELECT ID_EVENTO, TIPO_EVENTO, SUBJECT, FECHA_EVENTO, FECHA_REGISTRO, PAYLOAD_JSON "
                + "FROM EVENTOS_LOG ORDER BY FECHA_REGISTRO DESC");
        CONSULTAS.put("procesados", "SELECT ID_EVENTO, TIPO_EVENTO, REFERENCIA, ID_CITA, RESULTADO, FECHA_PROCESO "
                + "FROM EVENTOS_PROCESADOS ORDER BY FECHA_PROCESO DESC");
        CONSULTAS.put("citas", "SELECT ci.ID_CITA, p.REFERENCIA AS ID_TURNO, m.NOMBRE AS MASCOTA, "
                + "cl.NOMBRE || ' ' || cl.APELLIDO AS CLIENTE, e.NOMBRE || ' ' || e.APELLIDO AS VETERINARIO, "
                + "ci.FECHA_HORA, ci.MOTIVO, ci.ESTADO, ci.FECHA_CREACION "
                + "FROM CITAS ci JOIN MASCOTAS m ON m.ID_MASCOTA = ci.ID_MASCOTA "
                + "JOIN CLIENTES cl ON cl.ID_CLIENTE = ci.ID_CLIENTE "
                + "LEFT JOIN EMPLEADOS e ON e.ID_EMPLEADO = ci.ID_VETERINARIO "
                + "LEFT JOIN EVENTOS_PROCESADOS p ON p.ID_CITA = ci.ID_CITA AND p.TIPO_EVENTO = 'VetCare.TurnoAgendado' "
                + "ORDER BY ci.ID_CITA DESC");
        CONSULTAS.put("comunicaciones", "SELECT co.ID_COMUNICACION, cl.EMAIL AS DESTINATARIO, co.ID_CITA, co.CANAL, "
                + "co.ASUNTO, co.MENSAJE, co.ESTADO_ENVIO, co.FECHA_ENVIO "
                + "FROM COMUNICACIONES co JOIN CLIENTES cl ON cl.ID_CLIENTE = co.ID_CLIENTE "
                + "ORDER BY co.ID_COMUNICACION DESC");
        CONSULTAS.put("inventario", "SELECT ID_ITEM, NOMBRE, TIPO, UNIDAD_MEDIDA, STOCK_ACTUAL, STOCK_MINIMO "
                + "FROM INVENTARIO ORDER BY ID_ITEM");
        CONSULTAS.put("resultados", "SELECT r.ID_RESULTADO, m.NOMBRE AS MASCOTA, r.LABORATORIO_EXTERNO, r.TIPO_EXAMEN, "
                + "r.RESULTADO_JSON, r.FECHA_RECEPCION FROM RESULTADOS_LABORATORIO r "
                + "JOIN MASCOTAS m ON m.ID_MASCOTA = r.ID_MASCOTA ORDER BY r.ID_RESULTADO DESC");
        CONSULTAS.put("alertas", "SELECT ID_NOTIFICACION, TIPO, DESTINATARIO, MENSAJE, FECHA_PROGRAMADA, ESTADO, REFERENCIA "
                + "FROM NOTIFICACIONES ORDER BY ID_NOTIFICACION DESC");
    }

    private final EventoDao dao = new EventoDao();
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    @FunctionName("Consultar")
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
            List<Map<String, Object>> filas = dao.consultar(sql, LIMITE);
            return responder(request, HttpStatus.OK, filas);
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
