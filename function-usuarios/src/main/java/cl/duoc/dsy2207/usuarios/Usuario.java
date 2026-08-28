package cl.duoc.dsy2207.usuarios;

/**
 * Modelo de datos para la entidad Usuario.
 * Mapea 1:1 con la tabla USUARIOS (campos alineados al diagrama de
 * arquitectura acordado con el equipo: Nombre usuario, Profesion del
 * usuario, Pais, y el rol asociado mediante ID_ROL).
 */
public class Usuario {

    private Long idUsuario;
    private String nombreUsuario;
    private String profesionUsuario;
    private String pais;
    private Long idRol;

    public Usuario() {
    }

    public Usuario(Long idUsuario, String nombreUsuario, String profesionUsuario, String pais, Long idRol) {
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.profesionUsuario = profesionUsuario;
        this.pais = pais;
        this.idRol = idRol;
    }

    public Long getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Long idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getProfesionUsuario() {
        return profesionUsuario;
    }

    public void setProfesionUsuario(String profesionUsuario) {
        this.profesionUsuario = profesionUsuario;
    }

    public String getPais() {
        return pais;
    }

    public void setPais(String pais) {
        this.pais = pais;
    }

    public Long getIdRol() {
        return idRol;
    }

    public void setIdRol(Long idRol) {
        this.idRol = idRol;
    }
}
