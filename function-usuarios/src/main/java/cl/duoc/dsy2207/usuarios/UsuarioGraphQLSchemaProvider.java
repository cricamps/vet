package cl.duoc.dsy2207.usuarios;

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
 * Construye el {@link GraphQL} ejecutable para la entidad Usuario a partir
 * del esquema declarado en {@code schema.graphqls}, cableando cada campo de
 * Query/Mutation contra {@link UsuarioDao} (la misma capa de datos que ya
 * usan las funciones REST de Usuarios).
 *
 * Mismo patron que {@code RolGraphQLSchemaProvider} en function-roles
 * (Semana 4): se arma una sola vez por instancia "caliente" de la funcion,
 * pero cada DataFetcher sigue abriendo su propia conexion JDBC por llamada
 * a UsuarioDao, respetando el diseno stateless de la Semana 3.
 */
public final class UsuarioGraphQLSchemaProvider {

    private static volatile GraphQL graphQL;
    private static final Logger LOG = Logger.getLogger(UsuarioGraphQLSchemaProvider.class.getName());

    private UsuarioGraphQLSchemaProvider() {
    }

    public static GraphQL getGraphQL() {
        GraphQL local = graphQL;
        if (local == null) {
            synchronized (UsuarioGraphQLSchemaProvider.class) {
                if (graphQL == null) {
                    graphQL = build();
                }
                local = graphQL;
            }
        }
        return local;
    }

    private static GraphQL build() {
        UsuarioDao dao = new UsuarioDao();
        // Semana 8: las mutations pasan por UsuarioService para publicar el mismo
        // evento de dominio que las funciones REST (canal = "GraphQL").
        UsuarioService service = new UsuarioService(dao);

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(readSchema());

        RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder
                        .dataFetcher("usuarios", listarUsuariosFetcher(dao))
                        .dataFetcher("usuario", obtenerUsuarioFetcher(dao)))
                .type("Mutation", builder -> builder
                        .dataFetcher("agregarUsuario", agregarUsuarioFetcher(service))
                        .dataFetcher("modificarUsuario", modificarUsuarioFetcher(service))
                        .dataFetcher("eliminarUsuario", eliminarUsuarioFetcher(service)))
                .build();

        GraphQLSchema schema = new SchemaGenerator().makeExecutableSchema(typeRegistry, wiring);
        return GraphQL.newGraphQL(schema).build();
    }

    private static DataFetcher<?> listarUsuariosFetcher(UsuarioDao dao) {
        return env -> dao.listar();
    }

    private static DataFetcher<?> obtenerUsuarioFetcher(UsuarioDao dao) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            return dao.buscarPorId(id).orElse(null);
        };
    }

    private static DataFetcher<?> agregarUsuarioFetcher(UsuarioService service) {
        return env -> {
            String nombreUsuario = env.getArgument("nombreUsuario");
            String profesionUsuario = env.getArgument("profesionUsuario");
            String pais = env.getArgument("pais");
            String idRolArg = env.getArgument("idRol");
            Long idRol = idRolArg != null ? Long.parseLong(idRolArg) : null;
            return service.agregar(new Usuario(null, nombreUsuario, profesionUsuario, pais, idRol), "GraphQL", LOG)
                    .resultado();
        };
    }

    private static DataFetcher<?> modificarUsuarioFetcher(UsuarioService service) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            String nombreUsuario = env.getArgument("nombreUsuario");
            String profesionUsuario = env.getArgument("profesionUsuario");
            String pais = env.getArgument("pais");
            String idRolArg = env.getArgument("idRol");
            Long idRol = idRolArg != null ? Long.parseLong(idRolArg) : null;
            return service.modificar(id, new Usuario(id, nombreUsuario, profesionUsuario, pais, idRol), "GraphQL", LOG)
                    .resultado();
        };
    }

    private static DataFetcher<?> eliminarUsuarioFetcher(UsuarioService service) {
        return env -> {
            long id = Long.parseLong(env.<String>getArgument("id"));
            return service.eliminar(id, "GraphQL", LOG).resultado();
        };
    }

    private static Reader readSchema() {
        InputStream is = UsuarioGraphQLSchemaProvider.class.getClassLoader().getResourceAsStream("schema.graphqls");
        if (is == null) {
            throw new IllegalStateException("No se encontro schema.graphqls en el classpath");
        }
        return new InputStreamReader(is, StandardCharsets.UTF_8);
    }
}
