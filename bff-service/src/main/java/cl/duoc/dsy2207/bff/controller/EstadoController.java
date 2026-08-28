package cl.duoc.dsy2207.bff.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint simple para verificar rapidamente, durante el video de
 * demostracion, que el BFF esta arriba y sirviendo peticiones.
 */
@RestController
public class EstadoController {

    @GetMapping("/api/bff/estado")
    public Map<String, String> estado() {
        return Map.of(
                "servicio", "bff-service",
                "estado", "UP",
                "descripcion", "BFF orquestador de Usuarios y Roles - DSY2207 S3"
        );
    }
}
