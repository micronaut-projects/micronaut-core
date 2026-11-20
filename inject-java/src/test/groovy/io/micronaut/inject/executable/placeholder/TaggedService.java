package io.micronaut.inject.executable.placeholder;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "ExecutableMethodContextIsolationSpec")
public class TaggedService {

    @Tagged("${app.tag}")
    public void placeholder() {
        // only the annotation on this method is under test
    }

    @Tagged("#{ env['app.tag'] }")
    public void expression() {
        // only the annotation on this method is under test
    }
}
