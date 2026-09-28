package cl.duoc.dsy2207.bff.dto;

/** Motivo opcional de cancelacion (genera el evento VetCare.TurnoCancelado). */
public record CancelacionRequest(String motivo) {
}
