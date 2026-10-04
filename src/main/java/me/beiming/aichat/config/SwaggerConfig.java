package me.beiming.aichat.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AIChat API文档")
                        .version("1.1")
                        .description("AIChat项目接口文档 - 基于Spring Boot和Spring AI的智能聊天应用")
                        .contact(new Contact()
                                .name("AIChat Team")
                                .email("support@aichat.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .addSecurityItem(new SecurityRequirement().addList("beiming"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("beiming", new SecurityScheme()
                                .name("beiming")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("JWT认证令牌")));
    }
}