package cl.duoc.dsy2207.usuarios;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Funcion Serverless (FaaS) - capa GraphQL de Usuarios.
 *
 * Actividad Sumativa 2 (Semana 5, DSY2207): "Implementando comunicacion
 * Rest y GraphQL en el desarrollo". Continuando la Sumativa 1, se piden
 * como minimo 2 funciones con API REST y 2 con GraphQL sobre las
 * funciones serverless ya existentes. Las 5 funciones REST de Usuarios
 * (Agregar/Listar/Modificar/Eliminar/Obtener, ver {@link UsuarioFunction})
 * se mantienen intactas -- las sigue usando el BFF -- y esta funcion agrega,
 * sobre la misma base de datos y el mismo {@link UsuarioDao}, un unico
 * endpoint GraphQL con las mismas operaciones mediante queries y mutations,
 * en paralelo a {@code RolesGraphQL} que ya existe en function-roles desde
 * la Semana 4.
 */
public class UsuarioGraphQLFunction {

    private final Gson gson = new Gson();

    @FunctionName("UsuariosGraphQL")
    public HttpResponseMessage graphql(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST, HttpMethod.GET}, route = "graphql/usuarios",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("POST /api/graphql/usuarios");
        try {
            String rawBody = request.getBody().orElse("{}");
            JsonObject payload = gson.fromJson(rawBody.isBlank() ? "{}" : rawBody, JsonObject.class);

            String query = payload.has("query") ? payload.get("query").getAsString() : null;
            if (query == null || query.isBlank()) {
                return badRequest(request, "El campo 'query' es obligatorio en el body (formato { \"query\": \"...\", \"variables\": {...} }).");
            }

            Map<String, Object> variables = new HashMap<>();
            if (payload.has("variables") && payload.get("variables").isJsonObject()) {
                variables = gson.fromJson(payload.get("variables"), Map.class);
            }

            GraphQL graphQL = UsuarioGraphQLSchemaProvider.getGraphQL();
            ExecutionInput executionInput = ExecutionInput.newExecutionInput()
                    .query(query)
                    .variables(variables)
                    .build();

            ExecutionResult result = graphQL.execute(executionInput);

            return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(result.toSpecification()))
                    .build();
        } catch (Exception e) {
            context.getLogger().severe("Error ejecutando GraphQL: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("Content-Type", "application/json")
                    .body(gson.toJson(new ErrorBody("Error interno: " + e.getMessage())))
                    .build();
        }
    }

    private HttpResponseMessage badRequest(HttpRequestMessage<Optional<String>> request, String mensaje) {
        return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body(gson.toJson(new ErrorBody(mensaje)))
                .build();
    }

    private static class ErrorBody {
        String error;

        ErrorBody(String error) {
            this.error = error;
        }
    }
}
