package cl.duoc.dsy2207.bff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Datos para agendar una cita (genera el evento VetCare.TurnoAgendado).
 * idMascota / idVeterinario son ids de MASCOTAS / EMPLEADOS de la base VeterinariaCloud.
 */
public record TurnoRequest(
        @NotNull(message = "idMascota es obligatorio") @Positive Long idMascota,
        @NotNull(message = "idVeterinario es obligatorio") @Positive Long idVeterinario,
        @NotBlank(message = "fechaHora es obligatoria")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?",
                message = "fechaHora debe tener formato ISO, ej: 2026-10-05T10:30") String fechaHora,
        String motivo) {
}
