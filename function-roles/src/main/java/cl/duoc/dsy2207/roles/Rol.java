package cl.duoc.dsy2207.roles;

/**
 * Modelo de datos para la entidad Rol.
 * Mapea 1:1 con la tabla ROLES (campos alineados al diagrama de
 * arquitectura acordado con el equipo: ID Rol, Nombre del rol).
 */
public class Rol {

    private Long idRol;
    private String nombreRol;

    public Rol() {
    }

    public Rol(Long idRol, String nombreRol) {
        this.idRol = idRol;
        this.nombreRol = nombreRol;
    }

    public Long getIdRol() {
        return idRol;
    }

    public void setIdRol(Long idRol) {
        this.idRol = idRol;
    }

    public String getNombreRol() {
        return nombreRol;
    }

    public void setNombreRol(String nombreRol) {
        this.nombreRol = nombreRol;
    }
}
