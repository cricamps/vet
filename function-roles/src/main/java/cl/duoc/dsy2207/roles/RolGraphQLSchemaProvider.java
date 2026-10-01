package cl.duoc.dsy2207.roles;

import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Construye el {@link GraphQL} ejecutable para la entidad Rol a partir del
 * esquema declarado en {@code schema.graphqls}, cableando cada campo de
 * Query/Mutation contra {@link RolDao} (la misma capa de datos que ya usan
 * las funciones REST de Roles).
 *
 * Se arma una sola vez por instancia "caliente" de la función (patrón
 * habitual en Azure Functions Java para evitar reparsear el esquema en
 * cada invocación), pero cada DataFetcher sigue abriendo su propia
 * conexión JDBC por llamada a RolDao, respetando el diseño stateless de
 * la Semana 3.
 */
public final class RolGraphQLSchemaProvider {

    private static volatile GraphQL graphQL;
    private static final Logger LOG = Logger.getLogger(RolGraphQLSchemaProvider.class.getName());

    private RolGraphQLSchemaProvider() {
    }

    public static GraphQL getGraphQL() {
        GraphQL local = graphQL;
        if (local == null) {
            synchronized (RolGraphQLSchemaProvider.class) {
                if (graphQL == null) {
                    graphQL = build();
                }
                local = graphQL;
            }
        }
        return local;
    }

    private static GraphQL build() {
        RolDao dao = new RolDao();
        // Semana 8: las mutations pasan por RolService para publicar el mismo
        // evento de dominio que las funciones REST (canal = "GraphQL").
        RolService service = new RolService(dao);

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(readSchema());

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("roles", listarRolesFetcher(dao))
                        .dataFetcher("rol", obtenerRolFetcher(dao)))
                .type("Mutation", builder -> builder
                        .dataFetcher("agregarRol", agregarRolFetcher(service))
                        .dataFetcher("modificarRol", modificarRolFetcher(service))
                        .dataFetcher("eliminarRol", eliminarRolFetcher(service)))
                .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private static DataFetcher<?> listarRolesFetcher(RolDao dao) {
        return env -> dao.listar();
    }

    private static DataFetcher<?> obtenerRolFetcher(RolDao dao) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            return dao.buscarPorId(id).orElse(null);
        };
    }

    private static DataFetcher<?> agregarRolFetcher(RolService service) {
        return env -> {
            String nombreRol = env.getArgument("nombreRol");
            return service.agregar(new Rol(null, nombreRol), "GraphQL", LOG).resultado();
        };
    }

    private static DataFetcher<?> modificarRolFetcher(RolService service) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            String nombreRol = env.getArgument("nombreRol");
            return service.modificar(id, new Rol(id, nombreRol), "GraphQL", LOG).resultado();
        };
    }

    private static DataFetcher<?> eliminarRolFetcher(RolService service) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            return service.eliminar(id, "GraphQL", LOG).resultado();
        };
    }

    private static Reader readSchema() {
        InputStream is = RolGraphQLSchemaProvider.class.getClassLoader().getResourceAsStream("schema.graphqls");
        if (is == null) {
            throw new IllegalStateException("No se encontro schema.graphqls en el classpath");
        }
        return new InputStreamReader(is, StandardCharsets.UTF_8);
    }
}
