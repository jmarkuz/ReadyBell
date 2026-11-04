package org.readybell.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the built dashboard UI (copied into {@code static/} by the Docker build) and falls back to
 * {@code index.html} for client-side routes such as {@code /track/<session>} or {@code /orders}.
 * API paths and real files with an extension are never rewritten.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        boolean routeLike = !resourcePath.startsWith("api/") && !resourcePath.startsWith("actuator/")
                                && !resourcePath.substring(resourcePath.lastIndexOf('/') + 1).contains(".");
                        Resource index = new ClassPathResource("/static/index.html");
                        return routeLike && index.exists() ? index : null;
                    }
                });
    }
}
