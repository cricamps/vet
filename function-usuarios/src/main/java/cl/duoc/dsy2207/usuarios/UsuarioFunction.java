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
 *
 * Semana 8 (Sumativa 3): Agregar/Modificar/Eliminar son ademas FUNCIONES
 * GENERADORAS DE EVENTOS: tras confirmar el cambio en Oracle publican
 * UsuarioCreado / UsuarioModificado / UsuarioEliminado en Azure Event Grid
 * (ver UsuarioService y PublicadorEventos).
 */
public class UsuarioFunction {

    private final UsuarioDao dao = new UsuarioDao();
    private final UsuarioService service = new UsuarioService(dao);
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
            UsuarioService.Operacion<Usuario> op = service.agregar(body, "REST", context.getLogger());
            return conEvento(request.createResponseBuilder(HttpStatus.CREATED), op.evento())
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(op.resultado()))
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
            UsuarioService.Operacion<Boolean> op = service.modificar(id, body, "REST", context.getLogger());
            if (op.resultado()) {
                return conEvento(request.createResponseBuilder(HttpStatus.OK), op.evento())
                        .header("Content-Type", "application/json")
                        .body(gson.toJson(body))
                        .build();
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
            UsuarioService.Operacion<Boolean> op = service.eliminar(id, "REST", context.getLogger());
            if (op.resultado()) {
                return conEvento(request.createResponseBuilder(HttpStatus.NO_CONTENT), op.evento()).build();
            }
            return notFound(request, "Usuario " + id + " no encontrado");
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
