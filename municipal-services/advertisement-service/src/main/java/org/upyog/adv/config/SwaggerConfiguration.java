package org.upyog.adv.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.builders.ApiInfoBuilder;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

/**
 * This class configures Swagger documentation for the Advertisement Booking Service.
 * It sets up Swagger 2 to scan and document the API endpoints in the controllers package.
 * 
 * The @EnableSwagger2 annotation enables Swagger support in the application.
 * The @Configuration annotation marks this as a Spring configuration class.
 * 
 * The Docket bean configures the core of Swagger functionality, including:
 * - Which API endpoints to document (based on package)
 * - What paths to include
 * - API information such as title, description, and version
 */
@EnableSwagger2
@Configuration
public class SwaggerConfiguration {

    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2).select()
                .apis(RequestHandlerSelectors.basePackage("org.upyog.adv.web.controllers")) 
                .paths(PathSelectors.any()).build()
                .apiInfo(apiInfo());
    }

    private ApiInfo apiInfo() {
        return new ApiInfoBuilder().title("Advertisement Booking API") 
                .description("API details of the Advertisement service").version("1.0").build();
    }
}
