package cl.duoc.dsy2207.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Clientes HTTP hacia las funciones de la capa de eventos (semana 7).
 *
 * - eventosProductoraWebClient: agrega el header x-functions-key, porque las
 *   funciones generadoras de eventos usan authLevel = FUNCTION. La key se lee de
 *   la variable de entorno FUNCION_EVENTOS_PRODUCTORA_KEY (nunca en el codigo).
 * - eventosConsumidoraWebClient: consultas de solo lectura (ANONYMOUS).
 */
@Configuration
public class EventosWebClientConfig {

    @Bean
    public WebClient eventosProductoraWebClient(
            @Value("${funciones.eventos-productora.base-url}") String baseUrl,
            @Value("${funciones.eventos-productora.key:}") String functionKey) {
        WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
        if (functionKey != null && !functionKey.isBlank()) {
            builder.defaultHeader("x-functions-key", functionKey);
        }
        return builder.build();
    }

    @Bean
    public WebClient eventosConsumidoraWebClient(
            @Value("${funciones.eventos-consumidora.base-url}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }
}
