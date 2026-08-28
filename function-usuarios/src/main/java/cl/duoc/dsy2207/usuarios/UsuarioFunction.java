package cl.duoc.dsy2207.usuarios;

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
 * Funciones Serverless (FaaS) - Gestion de Usuarios.
 * 4 funciones independientes (Agregar, Listar, Modificar, Eliminar), tal
 * como quedaron definidas en el diagrama de arquitectura del equipo
 * (ARQ-USUARIOS-ROLES), mas una funcion auxiliar para obtener un usuario
 * por id que usa el BFF al armar respuestas.
 *
 * Cada funcion es pequena, enfocada y stateless (buena practica de la
 * guia de la semana 3): abre su propia conexion JDBC, hace su trabajo y
 * responde.
 */
public class UsuarioFunction {

    private final UsuarioDao dao = new UsuarioDao();
    private final Gson gson = new Gson();

    @FunctionName("ListarUsuarios")
    public HttpResponseMessage listarUsuarios(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "usuarios",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("GET /api/usuarios");
        try {
            List<Usuario> usuarios = dao.listar();
            return okJson(request, usuarios);
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("ObtenerUsuario")
    public HttpResponseMessage obtenerUsuario(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, route = "usuarios/{id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("id") long id,
            final ExecutionContext context) {
        context.getLogger().info("GET /api/usuarios/" + id);
        try {
            Optional<Usuario> usuario = dao.buscarPorId(id);
            if (usuario.isPresent()) {
                return okJson(request, usuario.get());
            }
            return notFound(request, "Usuario " + id + " no encontrado");
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("AgregarUsuario")
    public HttpResponseMessage agregarUsuario(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST}, route = "usuarios",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("POST /api/usuarios");
        try {
            Usuario body = gson.fromJson(request.getBody().orElse("{}"), Usuario.class);
            Usuario creado = dao.agregar(body);
            return request.createResponseBuilder(HttpStatus.CREATED)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(creado))
                    .build();
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("ModificarUsuario")
    public HttpResponseMessage modificarUsuario(
            @HttpTrigger(name = "req", methods = {HttpMethod.PUT}, route = "usuarios/{id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("id") long id,
            final ExecutionContext context) {
        context.getLogger().info("PUT /api/usuarios/" + id);
        try {
            Usuario body = gson.fromJson(request.getBody().orElse("{}"), Usuario.class);
            boolean actualizado = dao.modificar(id, body);
            if (actualizado) {
                body.setIdUsuario(id);
                return okJson(request, body);
            }
            return notFound(request, "Usuario " + id + " no encontrado");
        } catch (SQLException e) {
            return errorJson(request, e);
        }
    }

    @FunctionName("EliminarUsuario")
    public HttpResponseMessage eliminarUsuario(
            @HttpTrigger(name = "req", methods = {HttpMethod.DELETE}, route = "usuarios/{id}",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @com.microsoft.azure.functions.annotation.BindingName("id") long id,
            final ExecutionContext context) {
        context.getLogger().info("DELETE /api/usuarios/" + id);
        try {
            boolean eliminado = dao.eliminar(id);
            if (eliminado) {
                return request.createResponseBuilder(HttpStatus.NO_CONTENT).build();
            }
            return notFound(request, "Usuario " + id + " no encontrado");
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
