package cl.duoc.dsy2207.bff.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO de Rol tal como lo expone/recibe el BFF hacia el consumidor final.
 * Espeja el modelo de la funcion de Roles (ID Rol, Nombre del rol).
 */
public class RolDto {

    private Long idRol;

    @NotBlank(message = "El nombre del rol es obligatorio")
    private String nombreRol;

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
