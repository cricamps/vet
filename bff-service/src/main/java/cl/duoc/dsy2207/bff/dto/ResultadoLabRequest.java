package cl.duoc.dsy2207.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/** Resultado de laboratorio externo disponible (genera VetCare.ResultadoLabListo). */
public record ResultadoLabRequest(
        @NotNull(message = "idMascota es obligatorio") @Positive Long idMascota,
        @NotBlank(message = "laboratorioExterno es obligatorio") String laboratorioExterno,
        @NotBlank(message = "tipoExamen es obligatorio") String tipoExamen,
        Map<String, Object> resultado) {
}
