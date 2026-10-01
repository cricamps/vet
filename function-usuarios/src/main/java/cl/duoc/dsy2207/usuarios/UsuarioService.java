package cl.duoc.dsy2207.usuarios;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Capa de servicio de Usuarios (Semana 8).
 *
 * Une la operacion CRUD sobre Oracle (UsuarioDao) con la publicacion del evento
 * de dominio correspondiente. La usan tanto las funciones REST (UsuarioFunction)
 * como la funcion GraphQL (UsuarioGraphQLSchemaProvider), asi cualquier canal
 * que modifique un usuario genera el mismo evento.
 *
 *   AgregarUsuario   -> UsuariosRoles.UsuarioCreado
 *   ModificarUsuario -> UsuariosRoles.UsuarioModificado (incluye idRolAnterior)
 *   EliminarUsuario  -> UsuariosRoles.UsuarioEliminado  (incluye la foto previa)
 *
 * El evento se publica SOLO si la operacion en la base fue exitosa.
 */
public class UsuarioService {

    /** Resultado de una operacion: lo que devolvio el CRUD + el evento publicado (o null). */
    public record Operacion<T>(T resultado, PublicadorEventos.Resultado evento) {
    }

    private final UsuarioDao dao;

    public UsuarioService() {
        this(new UsuarioDao());
    }

    UsuarioService(UsuarioDao dao) {
        this.dao = dao;
    }

    public Operacion<Usuario> agregar(Usuario usuario, String canal, Logger log) throws SQLException {
        Usuario creado = dao.agregar(usuario);
        Map<String, Object> data = datosUsuario(creado, canal);
        return new Operacion<>(creado, PublicadorEventos.publicar(
                PublicadorEventos.USUARIO_CREADO, PublicadorEventos.subject(creado.getIdUsuario()), data, log));
    }

    public Operacion<Boolean> modificar(long id, Usuario cambios, String canal, Logger log) throws SQLException {
        Optional<Usuario> anterior = dao.buscarPorId(id);
        if (anterior.isEmpty()) {
            return new Operacion<>(false, null);
        }
        boolean actualizado = dao.modificar(id, cambios);
        if (!actualizado) {
            return new Operacion<>(false, null);
        }
        cambios.setIdUsuario(id);
        Map<String, Object> data = datosModificacion(anterior.get(), cambios, canal);
        return new Operacion<>(true, PublicadorEventos.publicar(
                PublicadorEventos.USUARIO_MODIFICADO, PublicadorEventos.subject(id), data, log));
    }

    public Operacion<Boolean> eliminar(long id, String canal, Logger log) throws SQLException {
        Optional<Usuario> anterior = dao.buscarPorId(id);
        if (anterior.isEmpty() || !dao.eliminar(id)) {
            return new Operacion<>(false, null);
        }
        Map<String, Object> data = datosUsuario(anterior.get(), canal);
        return new Operacion<>(true, PublicadorEventos.publicar(
                PublicadorEventos.USUARIO_ELIMINADO, PublicadorEventos.subject(id), data, log));
    }

    // ------------------------------------------------ armado del campo "data"
    // Logica pura (sin red ni base) para poder probarla con tests unitarios.

    static Map<String, Object> datosUsuario(Usuario u, String canal) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idUsuario", u.getIdUsuario());
        data.put("nombreUsuario", u.getNombreUsuario());
        data.put("profesionUsuario", u.getProfesionUsuario());
        data.put("pais", u.getPais());
        data.put("idRol", u.getIdRol());
        data.put("canal", canal);
        return data;
    }

    static Map<String, Object> datosModificacion(Usuario anterior, Usuario nuevo, String canal) {
        Map<String, Object> data = datosUsuario(nuevo, canal);
        data.put("idRolAnterior", anterior.getIdRol());
        data.put("cambioDeRol", !Objects.equals(anterior.getIdRol(), nuevo.getIdRol()));
        data.put("nombreAnterior", anterior.getNombreUsuario());
        return data;
    }
}
