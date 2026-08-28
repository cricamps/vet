package cl.duoc.dsy2207.bff.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de Usuario tal como lo expone/recibe el BFF hacia el consumidor final.
 * Espeja el modelo de la funcion de Usuarios (campos alineados al
 * diagrama de arquitectura del equipo: Nombre usuario, Profesion del
 * usuario, Pais, e ID Rol asociado).
 */
public class UsuarioDto {

    private Long idUsuario;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String nombreUsuario;

    private String profesionUsuario;
    private String pais;

    /** Rol asociado al usuario (FK directa a ROLES, según el diagrama). */
    private Long idRol;

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
