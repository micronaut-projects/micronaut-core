package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            assertEquals(second, secondClass.getContext());

            Value instance = PythonContextRuntime.newUninitializedInstance(first, BOOK);
            assertEquals("unset", instance.getMember("title").asString());
            assertEquals(firstClass, instance.getMetaObject());

            PythonContextRegistry.unregisterContext(first);
            Value afterReset = PythonContextRuntime.findClass(BOOK, first);
            assertNotSame(firstClass, afterReset, "unregistering the context drops its class cache");
            assertFalse(afterReset.isNull());
            PythonContextRegistry.unregisterContext(first);
            PythonContextRegistry.unregisterContext(second);
        }
    }

    @Test
    void uninitializedAllocatorsAreIsolatedByClassAndContext() {
        var other = new PythonContextRuntime.PythonClassReference(
            "__main__", "Other", new String[0], "Other", "class-instance:__main__.Other");
        try (Context first = newContext(); Context second = newContext()) {
            try {
                for (Context context : new Context[]{first, second}) {
                    context.eval(PYTHON, """
                        from dataclasses import dataclass
                        @dataclass(frozen=True, slots=True)
                        class Book:
                            title: str
                            def __post_init__(self):
                                raise AssertionError("constructor must not run")
                        class Other:
                            def __new__(cls):
                                raise AssertionError("custom allocation must not run")
                            def __init__(self):
                                raise AssertionError("constructor must not run")
                        """);
                    PythonContextRuntime.newUninitializedInstance(context, BOOK);
                    PythonContextRuntime.newUninitializedInstance(context, other);
                    var factories = PythonContextRegistry.state(context).uninitializedInstanceFactories;
                    Value bookAllocator = factories.get(BOOK.cacheKey());
                    Value otherAllocator = factories.get(other.cacheKey());
                    assertNotNull(bookAllocator);
                    assertNotNull(otherAllocator);
                    assertNotSame(bookAllocator, otherAllocator);
                    for (int i = 0; i < 3; i++) {
                        Value book = PythonContextRuntime.newFrozenDataclassInstance(context, BOOK, Map.of("title", "book-" + i));
                        Value otherInstance = PythonContextRuntime.newUninitializedInstance(context, other, Map.of("title", "other-" + i));
                        assertEquals("book-" + i, book.getMember("title").asString());
                        assertEquals("other-" + i, otherInstance.getMember("title").asString());
                        assertEquals(PythonContextRuntime.findClass(BOOK, context), book.getMetaObject());
                        assertEquals(PythonContextRuntime.findClass(other, context), otherInstance.getMetaObject());
                        assertEquals(context, book.getContext());
                        assertEquals(context, otherInstance.getContext());
                        assertSame(bookAllocator, factories.get(BOOK.cacheKey()), "reuse the bound allocator");
                        assertSame(otherAllocator, factories.get(other.cacheKey()), "reuse the bound allocator");
                        assertEquals(2, factories.size());
                    }
                }
                var firstState = PythonContextRegistry.state(first);
                Value firstAllocator = firstState.uninitializedInstanceFactories.get(BOOK.cacheKey());
                assertNotSame(firstAllocator, PythonContextRegistry.state(second).uninitializedInstanceFactories.get(BOOK.cacheKey()));
                PythonContextRegistry.unregisterContext(first);
                assertTrue(firstState.uninitializedInstanceFactories.isEmpty());
                first.eval(PYTHON, "class Book: replacement = True");
                Value replacement = PythonContextRuntime.newUninitializedInstance(first, BOOK);
                assertTrue(replacement.getMember("replacement").asBoolean());
                assertNotSame(firstAllocator, PythonContextRegistry.state(first).uninitializedInstanceFactories.get(BOOK.cacheKey()));
            } finally {
                PythonContextRegistry.unregisterContext(first);
                PythonContextRegistry.unregisterContext(second);
            }
        }
    }

    @Test
    void introductionsArePreparedOncePerClassAndContext() {
        var base = new PythonContextRuntime.PythonClassReference(
            "__main__", "Base", new String[0], "Base", "class-instance:__main__.Base");
        var sub = new PythonContextRuntime.PythonClassReference(
            "__main__", "Sub", new String[0], "Sub", "class-instance:__main__.Sub");
        String helperName = "__micronaut_prepare_introduction";
        try (Context first = newContext(); Context second = newContext()) {
            try {
                for (Context context : new Context[]{first, second}) {
                    context.getBindings(PYTHON).putMember("prepare_introduction", PythonContextRuntime.helper(context, helperName));
                    Value countingHelper = context.eval(PYTHON, """
                        from abc import ABC, abstractmethod
                        class Base(ABC):
                            def __init__(self, value):
                                self.value = value
                            @abstractmethod
                            def one(self): ...
                        class Sub(Base):
                            @abstractmethod
                            def two(self): ...
                        preparation_count = 0
                        fail_preparation = True
                        def counting_prepare(cls):
                            global preparation_count, fail_preparation
                            preparation_count += 1
                            if fail_preparation:
                                fail_preparation = False
                                raise RuntimeError("retry preparation")
                            return prepare_introduction(cls)
                        counting_prepare
                        """);
                    var state = PythonContextRegistry.state(context);
                    state.helpers.put(helperName, countingHelper);
                    assertThrows(PolyglotException.class, () -> PythonContextRuntime.newIntroduction(context, base, "failed"));
                    assertFalse(state.preparedIntroductionClasses.contains(base.cacheKey()));
                    for (int i = 0; i < 3; i++) {
                        Value baseInstance = PythonContextRuntime.newIntroduction(context, base, "base-" + i);
                        Value subInstance = PythonContextRuntime.newIntroduction(context, sub, "sub-" + i);
                        assertEquals("base-" + i, baseInstance.getMember("value").asString());
                        assertEquals("sub-" + i, subInstance.getMember("value").asString());
                        assertTrue(subInstance.invokeMember("two").isNull());
                        assertEquals(3, context.eval(PYTHON, "preparation_count").asInt(), "one failed and two successful preparations");
                    }
                }
                var firstState = PythonContextRegistry.state(first);
                PythonContextRegistry.unregisterContext(first);
                assertTrue(firstState.preparedIntroductionClasses.isEmpty());
                first.eval(PYTHON, """
                    class Base(ABC):
                        @abstractmethod
                        def replacement(self): ...
                    """);
                Value replacement = PythonContextRuntime.newIntroduction(first, base);
                assertTrue(replacement.invokeMember("replacement").isNull());
            } finally {
                PythonContextRegistry.unregisterContext(first);
                PythonContextRegistry.unregisterContext(second);
            }
        }
    }

}
