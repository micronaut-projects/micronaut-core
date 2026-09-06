package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Python classes are resolved once per context and reused for every instance created afterwards.
 */
final class PythonClassCacheTest {

    private static final PythonContextRuntime.PythonClassReference BOOK =
        new PythonContextRuntime.PythonClassReference("__main__", "Book", new String[0], "Book", "class-instance:__main__.Book");

    private static Context newContext() {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        context.eval(PYTHON, """
            import sys
            class Book:
                title = "unset"
            """);
        return context;
    }

    @Test
    void classIsResolvedOncePerContext() {
        try (Context first = newContext(); Context second = newContext()) {
            PythonContextRegistry.registerContext(first);
            PythonContextRegistry.registerContext(second);
            Value firstClass = PythonContextRuntime.findClass(BOOK, first);
            assertSame(firstClass, PythonContextRuntime.findClass(BOOK, first));
            assertSame(firstClass, PythonContextRuntime.findClass(BOOK, first.eval(PYTHON, "1").getContext()));

            Value secondClass = PythonContextRuntime.findClass(BOOK, second);
            assertNotSame(firstClass, secondClass, "contexts must not share class values");
            assertTrue(secondClass.getContext().equals(second));

            Value instance = PythonContextRuntime.newUninitializedInstance(first, BOOK);
            assertEquals("unset", instance.getMember("title").asString());
            assertTrue(instance.getMetaObject().equals(firstClass));

            PythonContextRegistry.unregisterContext(first);
            Value afterReset = PythonContextRuntime.findClass(BOOK, first);
            assertNotSame(firstClass, afterReset, "unregistering the context drops its class cache");
            assertFalse(afterReset.isNull());
            PythonContextRegistry.unregisterContext(first);
            PythonContextRegistry.unregisterContext(second);
        }
    }
}
