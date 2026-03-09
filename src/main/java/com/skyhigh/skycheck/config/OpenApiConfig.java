package com.skyhigh.skycheck.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI skycheckOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Local Development Server");

        Contact contact = new Contact();
        contact.setName("SkyHigh Airlines Engineering");
        contact.setEmail("engineering@skyhigh.com");

        License license = new License();
        license.setName("Proprietary");

        Info info = new Info()
                .title("SkyCheck - Digital Check-In API")
                .version("1.0.0")
                .description("Backend API for SkyHigh Airlines digital check-in system. " +
                        "Handles seat reservations with time-bound holds, conflict-free assignments, " +
                        "baggage validation, and payment processing.")
                .contact(contact)
                .license(license);

        return new OpenAPI()
                .info(info)
                .servers(List.of(localServer));
    }
}

