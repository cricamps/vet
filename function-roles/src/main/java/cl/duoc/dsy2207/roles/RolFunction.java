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
 */
public class RolFunction {

    private final RolDao dao = new RolDao();
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
            Rol creado = dao.agregar(body);
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(creado))
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
            boolean actualizado = dao.modificar(id, body);
            if (actualizado) {
                body.setIdRol(id);
                return okJson(request, body);
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
            boolean eliminado = dao.eliminar(id);
            if (eliminado) {
                return request.createResponseBuilder(HttpStatus.NO_CONTENT).build();
            }
            return notFound(request, "Rol " + id + " no encontrado");
        } catch (SQLException e) {
            return errorJson(request, e);
        }
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
