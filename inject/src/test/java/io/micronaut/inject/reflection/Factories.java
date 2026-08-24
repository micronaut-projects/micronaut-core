package io.micronaut.inject.reflection;

import java.util.List;
import java.util.Map;

/**
 * The shapes the {@code AnnotationReflectionUtils} argument factories are read from.
 */
public class Factories {

    private Map<String, @Tag("mapValue") List<@Tag("deep") String>> nested;

    private String plain;

    public Factories(@Tag("ctorParam") String plain, List<@Tag("ctorElem") String> more) {
        this.plain = plain;
    }

    public Factories() {
    }

    @Tag("method")
    public List<@Tag("returned") String> produce(@Tag("param") Map<String, @Tag("arg") Integer> input) {
        return List.of();
    }

    public void consume(String value) {
    }
}
