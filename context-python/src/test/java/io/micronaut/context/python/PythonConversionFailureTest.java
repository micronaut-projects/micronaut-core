package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Element conversion failures surface as errors instead of silently producing empty collections.
 */
final class PythonConversionFailureTest {

    @Test
    void collectionsConvertAndFailingElementsAreReported() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            assertEquals(List.of(1, 2), PythonConversion.convertList(context.eval(PYTHON, "[1, 2]"), Integer.class));
            assertEquals(Map.of("a", 1), PythonConversion.convertMap(context.eval(PYTHON, "{'a': 1}"), String.class, Integer.class));
            assertEquals(Set.of("x"), PythonConversion.convertSet(context.eval(PYTHON, "{'x'}"), String.class));

            Value mixedList = context.eval(PYTHON, "[1, 'x']");
            assertThrows(RuntimeException.class, () -> PythonConversion.convertList(mixedList, Integer.class));
            Value mixedMap = context.eval(PYTHON, "{'a': 'x'}");
            assertThrows(RuntimeException.class, () -> PythonConversion.convertMap(mixedMap, String.class, Integer.class));
            Value mixedSet = context.eval(PYTHON, "{'x'}");
            assertThrows(RuntimeException.class, () -> PythonConversion.convertSet(mixedSet, Integer.class));
        }
    }
}
