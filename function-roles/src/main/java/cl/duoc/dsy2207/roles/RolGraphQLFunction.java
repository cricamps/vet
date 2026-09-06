package cl.duoc.dsy2207.roles;

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
 * Funcion Serverless (FaaS) - capa GraphQL de Roles.
 *
 * Actividad Formativa 3 (Semana 4, DSY2207): "Añadiendo comunicación Rest y
 * GraphQL". Las 5 funciones REST de Roles (Agregar/Listar/Modificar/
 * Eliminar/Obtener, ver {@link RolFunction}) se mantienen intactas para no
 * romper al BFF ni a la Sumativa 1; esta funcion agrega, sobre la misma
 * base de datos y el mismo {@link RolDao}, un unico endpoint GraphQL que
 * permite las mismas operaciones mediante queries y mutations.
 *
 * La funcion de Usuarios ({@code function-usuarios}) es la que cumple el
 * requisito de comunicacion REST de esta actividad; esta funcion de Roles
 * cumple el requisito GraphQL.
 */
public class RolGraphQLFunction {

    private final Gson gson = new Gson();

    @FunctionName("RolesGraphQL")
    public HttpResponseMessage graphql(
            @HttpTrigger(name = "req", methods = {HttpMethod.POST, HttpMethod.GET}, route = "graphql/roles",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("POST /api/graphql/roles");
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

            GraphQL graphQL = RolGraphQLSchemaProvider.getGraphQL();
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
