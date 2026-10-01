package cl.duoc.dsy2207.roles;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Capa de servicio de Roles (Semana 8).
 *
 * Une la operacion CRUD sobre Oracle (RolDao) con la publicacion del evento de
 * dominio correspondiente. La usan las funciones REST (RolFunction) y la funcion
 * GraphQL (RolGraphQLSchemaProvider):
 *
 *   AgregarRol   -> UsuariosRoles.RolCreado
 *   ModificarRol -> UsuariosRoles.RolModificado (incluye nombreAnterior)
 *   EliminarRol  -> UsuariosRoles.RolEliminado  (incluye usuariosAfectados)
 *
 * Regla de negocio: el rol por defecto (App Setting ROL_POR_DEFECTO, por defecto
 * CONSULTA) no se puede eliminar, porque es el rol que la funcion consumidora
 * asigna a los usuarios que quedan sin rol.
 */
public class RolService {

    /** Resultado de una operacion: lo que devolvio el CRUD + el evento publicado (o null). */
    public record Operacion<T>(T resultado, PublicadorEventos.Resultado evento) {
    }

    /** El rol solicitado es el rol por defecto y no puede eliminarse (HTTP 409). */
    public static class RolProtegidoException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public RolProtegidoException(String mensaje) {
            super(mensaje);
        }
    }

    private final RolDao dao;

    public RolService() {
        this(new RolDao());
    }

    RolService(RolDao dao) {
        this.dao = dao;
    }

    public static String rolPorDefecto() {
        String valor = System.getenv("ROL_POR_DEFECTO");
        return valor == null || valor.isBlank() ? "CONSULTA" : valor.trim().toUpperCase(Locale.ROOT);
    }

    public Operacion<Rol> agregar(Rol rol, String canal, Logger log) throws SQLException {
        Rol creado = dao.agregar(rol);
        return new Operacion<>(creado, PublicadorEventos.publicar(
                PublicadorEventos.ROL_CREADO, PublicadorEventos.subject(creado.getIdRol()),
                datosRol(creado, canal), log));
    }

    public Operacion<Boolean> modificar(long id, Rol cambios, String canal, Logger log) throws SQLException {
        Optional<Rol> anterior = dao.buscarPorId(id);
        if (anterior.isEmpty() || !dao.modificar(id, cambios)) {
            return new Operacion<>(false, null);
        }
        cambios.setIdRol(id);
        Map<String, Object> data = datosRol(cambios, canal);
        data.put("nombreAnterior", anterior.get().getNombreRol());
        return new Operacion<>(true, PublicadorEventos.publicar(
                PublicadorEventos.ROL_MODIFICADO, PublicadorEventos.subject(id), data, log));
    }

    public Operacion<Boolean> eliminar(long id, String canal, Logger log) throws SQLException {
        Optional<Rol> anterior = dao.buscarPorId(id);
        if (anterior.isEmpty()) {
            return new Operacion<>(false, null);
        }
        validarEliminable(anterior.get());
        List<Long> afectados = dao.eliminarDesasignando(id);
        if (afectados == null) {
            return new Operacion<>(false, null);
        }
        return new Operacion<>(true, PublicadorEventos.publicar(
                PublicadorEventos.ROL_ELIMINADO, PublicadorEventos.subject(id),
                datosEliminacion(anterior.get(), afectados, canal), log));
    }

    // ------------------------------------------------ logica pura (testeable)

    static void validarEliminable(Rol rol) {
        String nombre = rol.getNombreRol() == null ? "" : rol.getNombreRol().trim().toUpperCase(Locale.ROOT);
        if (nombre.equals(rolPorDefecto())) {
            throw new RolProtegidoException("El rol " + nombre
                    + " es el rol por defecto del sistema y no se puede eliminar");
        }
    }

    static Map<String, Object> datosRol(Rol rol, String canal) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("idRol", rol.getIdRol());
        data.put("nombreRol", rol.getNombreRol());
        data.put("canal", canal);
        return data;
    }

    static Map<String, Object> datosEliminacion(Rol rol, List<Long> afectados, String canal) {
        Map<String, Object> data = datosRol(rol, canal);
        data.put("usuariosAfectados", afectados);
        data.put("rolReemplazo", rolPorDefecto());
        return data;
    }
}
