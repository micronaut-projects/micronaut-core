package io.micronaut.inject.executable.placeholder;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ExecutableMethodContextIsolationSpec")
public class TaggedService {

    @Tagged("${app.tag}")
    public void placeholder() {
    }

    @Tagged("#{ env['app.tag'] }")
    public void expression() {
    }
}
