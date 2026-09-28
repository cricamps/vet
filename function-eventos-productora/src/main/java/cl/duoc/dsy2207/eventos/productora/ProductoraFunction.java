package cl.duoc.dsy2207.eventos.productora;

import cl.duoc.dsy2207.eventos.productora.ConstructorEventos.EventoPreparado;
import cl.duoc.dsy2207.eventos.productora.ConstructorEventos.SolicitudInvalidaException;
import com.azure.messaging.eventgrid.EventGridEvent;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * FUNCION GENERADORA DE EVENTOS (productor) - VetCare, semana 7.
 *
 * Cada funcion recibe un POST HTTP, valida el cuerpo, y publica UN evento en el
 * Event Grid Topic. No escribe en la base de datos ni conoce a los consumidores:
 * ese es el desacoplamiento que aporta la arquitectura orientada a eventos.
 *
 * authLevel = FUNCTION (igual que el material de la semana): solo quien tenga la
 * function key (el BFF) puede generar eventos. El BFF es quien valida el rol del
 * usuario contra function-usuarios/function-roles y envia el header X-Usuario-Id.
 *
 * Responde 202 Accepted: el evento quedo aceptado por Event Grid, pero su
 * procesamiento ocurre de forma asincrona en la funcion consumidora.
 */
public class ProductoraFunction {

    private static final String HEADER_USUARIO = "x-usuario-id";
    private final Gson gson = new Gson();

    @FunctionName("AgendarTurno")
    public HttpResponseMessage agendarTurno(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "eventos/turnos",
                    authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return publicar(request, context,
                () -> ConstructorEventos.turnoAgendado(body(request), usuario(request)));
    }

    @FunctionName("CancelarTurno")
    public HttpResponseMessage cancelarTurno(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "eventos/turnos/{idTurno}/cancelar",
                    authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            @BindingName("idTurno") String idTurno,
            final ExecutionContext context) {
        return publicar(request, context,
                () -> ConstructorEventos.turnoCancelado(idTurno, body(request), usuario(request)));
    }

    @FunctionName("DispensarMedicamento")
    public HttpResponseMessage dispensarMedicamento(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "eventos/medicacion",
                    authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return publicar(request, context,
                () -> ConstructorEventos.medicacionDispensada(body(request), usuario(request)));
    }

    @FunctionName("RegistrarResultadoLab")
    public HttpResponseMessage registrarResultadoLab(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "eventos/laboratorio",
                    authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        return publicar(request, context,
                () -> ConstructorEventos.resultadoLabListo(body(request), usuario(request)));
    }

    // ------------------------------------------------------------------------

    private HttpResponseMessage publicar(HttpRequestMessage<Optional<String>> request,
                                         ExecutionContext context,
                                         Supplier<EventoPreparado> preparar) {
        try {
            EventoPreparado preparado = preparar.get();
            EventGridEvent evento = PublicadorEventGrid.publicar(
                    preparado.tipo(), preparado.idRecurso(), preparado.data());

            context.getLogger().info("Evento publicado en Event Grid: " + evento.getEventType()
                    + " id=" + evento.getId() + " subject=" + evento.getSubject());

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("mensaje", "Evento creado correctamente");
            respuesta.put("idEvento", evento.getId());
            respuesta.put("eventType", evento.getEventType());
            respuesta.put("subject", evento.getSubject());
            respuesta.put("idRecurso", preparado.idRecurso());
            respuesta.put("data", preparado.data());
            return json(request, HttpStatus.ACCEPTED, respuesta);

        } catch (SolicitudInvalidaException e) {
            return error(request, HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            context.getLogger().severe("Configuracion incompleta: " + e.getMessage());
            return error(request, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (Exception e) {
            context.getLogger().severe("Error al publicar evento: " + e.getMessage());
            return error(request, HttpStatus.BAD_GATEWAY, "Error al publicar evento en Event Grid: " + e.getMessage());
        }
    }

    private JsonObject body(HttpRequestMessage<Optional<String>> request) {
        String raw = request.getBody().orElse("{}");
        try {
            JsonObject obj = JsonParser.parseString(raw.isBlank() ? "{}" : raw).getAsJsonObject();
            return obj;
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new SolicitudInvalidaException("El cuerpo debe ser un JSON valido");
        }
    }

    private String usuario(HttpRequestMessage<Optional<String>> request) {
        return request.getHeaders().entrySet().stream()
                .filter(h -> h.getKey().equalsIgnoreCase(HEADER_USUARIO))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("sin-usuario");
    }

    private HttpResponseMessage json(HttpRequestMessage<?> request, HttpStatus status, Object body) {
        return request.createResponseBuilder(status)
                .header("Content-Type", "application/json")
                .body(gson.toJson(body))
                .build();
    }

    private HttpResponseMessage error(HttpRequestMessage<?> request, HttpStatus status, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", mensaje);
        return json(request, status, body);
    }
}
