package io.micronaut.inject.factory.named;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;

import javax.inject.Named;

@Factory
public class TemplateFactory {

    @Bean
    @Named("csw-test-template")
    public Template cswTemplate() {
        return new CSWTestTemplate();
    }

    @Bean
    @Named("ias-test-template")
    public Template iasTemplate() {
        return new IASTestTemplate();
    }
}
