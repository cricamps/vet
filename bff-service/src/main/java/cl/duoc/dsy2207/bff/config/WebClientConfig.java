package cl.duoc.dsy2207.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configura los clientes HTTP hacia cada funcion serverless.
 * Las URLs vienen de application.yml, que a su vez las lee de variables de
 * entorno (FUNCION_USUARIOS_URL / FUNCION_ROLES_URL). Esto permite reapuntar
 * el BFF a las URLs que genera el Docker Lab sin recompilar el proyecto.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient usuariosWebClient(@Value("${funciones.usuarios.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public WebClient rolesWebClient(@Value("${funciones.roles.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
