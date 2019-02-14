package io.micronaut.views.handlebars;

import com.github.jknack.handlebars.Handlebars;
import io.micronaut.context.annotation.Factory;

import javax.inject.Singleton;

@Factory
public class HandlebarsFactory {

    @Singleton
    public Handlebars handlebars() {
        return new Handlebars();
    }
}
