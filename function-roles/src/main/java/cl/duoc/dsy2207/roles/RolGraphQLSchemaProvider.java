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

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(readSchema());

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("roles", listarRolesFetcher(dao))
                        .dataFetcher("rol", obtenerRolFetcher(dao)))
                .type("Mutation", builder -> builder
                        .dataFetcher("agregarRol", agregarRolFetcher(dao))
                        .dataFetcher("modificarRol", modificarRolFetcher(dao))
                        .dataFetcher("eliminarRol", eliminarRolFetcher(dao)))
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

    private static DataFetcher<?> agregarRolFetcher(RolDao dao) {
        return env -> {
            String nombreRol = env.getArgument("nombreRol");
            return dao.agregar(new Rol(null, nombreRol));
        };
    }

    private static DataFetcher<?> modificarRolFetcher(RolDao dao) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            String nombreRol = env.getArgument("nombreRol");
            return dao.modificar(id, new Rol(id, nombreRol));
        };
    }

    private static DataFetcher<?> eliminarRolFetcher(RolDao dao) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            return dao.eliminar(id);
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
