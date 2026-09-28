package cl.duoc.dsy2207.bff.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Medicamento/insumo aplicado (genera VetCare.MedicacionDispensada). idItem = INVENTARIO.ID_ITEM. */
public record MedicacionRequest(
        @NotNull(message = "idItem es obligatorio") @Positive Long idItem,
        @NotNull(message = "cantidad es obligatoria") @Min(value = 1, message = "cantidad debe ser mayor a 0") Integer cantidad,
        @Positive Long idMascota) {
}
