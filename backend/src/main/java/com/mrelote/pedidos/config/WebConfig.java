package com.mrelote.pedidos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve /uploads/** desde disco local. Es una implementación mínima para
 * el esqueleto: la sección 24 (arquitectura técnica) recomienda un
 * servicio de archivos/objetos tipo S3 para producción.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${mrelote.storage.uploads-dir:./uploads}")
    private String uploadsDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadsDir + "/");
    }
}
