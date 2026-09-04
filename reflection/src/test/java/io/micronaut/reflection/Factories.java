package io.micronaut.reflection;

import java.util.List;
import java.util.Map;

/**
 * The shapes the {@code ReflectionArguments} factories are read from.
 *
 * <p>The fields are never read and the methods do nothing: what the specs read is the type-use annotations of
 * the declarations, so the declarations are the whole fixture and there is no behaviour to run.</p>
 */
public class Factories {

    private Map<String, @Tag("mapValue") List<@Tag("deep") String>> nested; // NOSONAR - unread on purpose, the field's annotated type is what is described

    private String plain;

    public Factories(@Tag("ctorParam") String plain, List<@Tag("ctorElem") String> more) { // NOSONAR - "more" is unused on purpose, its annotated element type is what is described
        this.plain = plain;
    }

    public Factories() {
        // empty on purpose - only the declaration is read
    }

    @Tag("method")
    public List<@Tag("returned") String> produce(@Tag("param") Map<String, @Tag("arg") Integer> input) {
        return List.of();
    }

    public void consume(String value) { // NOSONAR - the parameter is unused on purpose, the signature is what is described
        // empty on purpose - only the declaration is read
    }
}
