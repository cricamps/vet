package cl.duoc.dsy2207.roles;

import com.google.gson.Gson;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Funciones Serverless (FaaS) - Gestion de Roles.
 * 4 funciones independientes (Agregar, Listar, Modificar, Eliminar), tal
 * como quedaron definidas en el diagrama de arquitectura del equipo
 * (ARQ-USUARIOS-ROLES), mas una funcion auxiliar para obtener un rol por
 * id que usa el BFF al validar asignaciones.
 *
 * Semana 8 (Sumativa 3): Agregar/Modificar/Eliminar son ademas FUNCIONES
 * GENERADORAS DE EVENTOS: tras confirmar el cambio en Oracle publican
 * RolCreado / RolModificado / RolEliminado en Azure Event Grid (ver RolService).
 * EliminarRol ya no falla por la FK de USUARIOS: desasigna a los usuarios en la
 * misma transaccion (quedan sin rol) y la funcion consumidora les notifica
 * que el rol fue quitado (requerimiento EFT).
 * El rol por defecto (CONSULTA) no se puede eliminar: responde 409.
 */
public class RolFunction {

    private final RolDao dao = new RolDao();
    private final RolService service = new RolService(dao);
    private final Gson gson = new Gson();

    @FunctionName("ListarRoles")
    public HttpResponseMessage listarRoles(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "roles",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("GET /api/roles");
        try {
            List<Rol> roles = dao.listar();
            return okJson(request, roles);
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("ObtenerRol")
    public HttpResponseMessage obtenerRol(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "roles/{id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("id") long id,
            final ExecutionContext context) {
        context.getLogger().info("GET /api/roles/" + id);
        try {
            Optional<Rol> rol = dao.buscarPorId(id);
            if (rol.isPresent()) {
                return okJson(request, rol.get());
            }
            return notFound(request, "Rol " + id + " no encontrado");
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("AgregarRol")
    public HttpResponseMessage agregarRol(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "roles",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("POST /api/roles");
        try {
            Rol body = gson.fromJson(request.getBody().orElse("{}"), Rol.class);
            RolService.Operacion<Rol> op = service.agregar(body, "REST", context.getLogger());
            return conEvento(request.createResponseBuilder(HttpStatus.CREATED), op.evento())
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(op.resultado()))
                    .build();
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("ModificarRol")
    public HttpResponseMessage modificarRol(
            @HttpTrigger(name = "req", methods = {HttpMethod.PUT}, route = "roles/{id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("id") long id,
            final ExecutionContext context) {
        context.getLogger().info("PUT /api/roles/" + id);
        try {
            Rol body = gson.fromJson(request.getBody().orElse("{}"), Rol.class);
            RolService.Operacion<Boolean> op = service.modificar(id, body, "REST", context.getLogger());
            if (op.resultado()) {
                return conEvento(request.createResponseBuilder(HttpStatus.OK), op.evento())
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(body))
                        .build();
            }
            return notFound(request, "Rol " + id + " no encontrado");
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("EliminarRol")
    public HttpResponseMessage eliminarRol(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, route = "roles/{id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("id") long id,
            final ExecutionContext context) {
        context.getLogger().info("DELETE /api/roles/" + id);
        try {
            RolService.Operacion<Boolean> op = service.eliminar(id, "REST", context.getLogger());
            if (op.resultado()) {
                return conEvento(request.createResponseBuilder(HttpStatus.NO_CONTENT), op.evento()).build();
            }
            return notFound(request, "Rol " + id + " no encontrado");
        } catch (RolService.RolProtegidoException e) {
            return request.createResponseBuilder(HttpStatus.CONFLICT)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(new ErrorBody(e.getMessage())))
                    .build();
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    /**
     * Semana 8: informa en headers si el evento de dominio quedo publicado en
     * Event Grid (el BFF y Postman los pueden mostrar sin cambiar el body).
     */
    private HttpResponseMessage.Builder conEvento(HttpResponseMessage.Builder builder,
                                                  PublicadorEventos.Resultado evento) {
        if (evento == null) {
            return builder;
        }
        builder.header("X-Evento-Tipo", evento.eventType())
               .header("X-Evento-Publicado", String.valueOf(evento.publicado()));
        if (evento.idEvento() != null) {
            builder.header("X-Evento-Id", evento.idEvento());
        }
        return builder;
    }

    private HttpResponseMessage okJson(HttpRequestMessage<Optional<String>> request, Object body) {
        return request.createResponseBuilder(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(gson.toJson(body))
                .build();
    }

    private HttpResponseMessage notFound(HttpRequestMessage<Optional<String>> request, String mensaje) {
        return request.createResponseBuilder(HttpStatus.NOT_FOUND)
                .header("Content-Type", "application/json")
                .body(gson.toJson(new ErrorBody(mensaje)))
                .build();
    }

    private HttpResponseMessage errorJson(HttpRequestMessage<Optional<String>> request, Exception e) {
        return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body(gson.toJson(new ErrorBody("Error interno: " + e.getMessage())))
                .build();
    }

    private static class ErrorBody {
        String error;

        ErrorBody(String error) {
            this.error = error;
        }
    }
}
