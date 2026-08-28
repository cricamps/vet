package cl.duoc.dsy2207.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio BFF.
 * Este componente no contiene logica de negocio de usuarios/roles: su unica
 * responsabilidad es orquestar llamadas hacia las funciones serverless
 * correspondientes (ver docs/arquitectura.md).
 */
@SpringBootApplication
public class BffApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
