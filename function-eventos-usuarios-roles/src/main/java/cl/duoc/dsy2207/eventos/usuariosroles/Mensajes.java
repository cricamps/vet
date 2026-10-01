package cl.duoc.dsy2207.eventos.usuariosroles;

/**
 * Textos de las notificaciones que generan las funciones consumidoras.
 * Logica pura, separada del acceso a datos para poder probarla.
 */
public final class Mensajes {

    public static final String BIENVENIDA = "BIENVENIDA";
    public static final String ROL_ASIGNADO = "ROL_ASIGNADO";
    public static final String CAMBIO_ROL = "CAMBIO_ROL";
    public static final String BAJA = "BAJA";
    public static final String REASIGNACION_ROL = "REASIGNACION_ROL";
    public static final String ROL_RENOMBRADO = "ROL_RENOMBRADO";

    private Mensajes() {
    }

    public static String bienvenida(String nombreUsuario, String nombreRol) {
        return "Bienvenido/a " + nombreUsuario + ": tu cuenta fue creada con el rol " + nombreRol + ".";
    }

    public static String rolAsignadoPorDefecto(String nombreRol) {
        return "No se indico un rol al registrar/modificar la cuenta: se asigno automaticamente el rol "
                + nombreRol + ".";
    }

    public static String cambioRol(String rolAnterior, String rolNuevo) {
        return "Tu rol cambio de " + rolAnterior + " a " + rolNuevo + ".";
    }

    public static String baja(String nombreUsuario) {
        return "La cuenta de " + nombreUsuario + " fue eliminada del sistema.";
    }

    public static String reasignacion(String rolEliminado, String rolNuevo) {
        return "El rol " + rolEliminado + " fue eliminado del sistema: se te reasigno el rol " + rolNuevo + ".";
    }

    public static String rolRenombrado(String nombreAnterior, String nombreNuevo) {
        return "Tu rol " + nombreAnterior + " ahora se llama " + nombreNuevo + ".";
    }

    /** Nombre legible de un rol para los mensajes. */
    public static String nombreRol(String nombre, Long id) {
        if (nombre != null && !nombre.isBlank()) {
            return nombre;
        }
        return id == null ? "(sin rol)" : "#" + id;
    }

    public static String recortar(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
