package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonCallablesTest {

    private Context context;

    @BeforeEach
    void setUp() {
        context = Context.newBuilder("python")
            .allowAllAccess(true)
            .allowHostAccess(new GraalPyHostAccessFactory().hostAccess(List.of()))
            .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void inspectsTheArityOfPythonCallables() {
        assertArity("lambda: 1", 0, 0);
        assertArity("lambda a: a", 1, 1);
        assertArity("lambda a, b: a", 2, 2);
        assertArity("lambda a, b='x': a", 1, 2);
        // *args does not make the callable fit further arities: it would fit every overload
        assertArity("lambda *args: args", 0, 0);
        assertArity("lambda a, *args: args", 1, 1);
        assertArity("lambda a, b='x', *args: args", 1, 2);
        // a bound method does not count self
        assertArity("type('T', (), {'m': lambda self, a: a})().m", 1, 1);
        // a class method bound to its class
        assertArity("type('T', (), {'m': classmethod(lambda cls, a, b: a)}).m", 2, 2);
    }

    @Test
    void callablesWithoutInspectableSignatureAcceptNoArity() {
        // the arity mappings stay out of the decision: every overload would apply and the call be ambiguous
        assertAcceptsNoArity("__import__('functools').partial(lambda a, b: a, 1)");
        assertAcceptsNoArity("str.upper");
        assertAcceptsNoArity("type('T', (), {'__call__': lambda self, a: a})()");
        assertAcceptsNoArity("dict");
    }

    @Test
    void exactArityOnlyMatchesTheDeclaredParameters() {
        assertTrue(PythonCallables.acceptsExactArity(context.eval("python", "lambda a, b='x': a"), 2));
        assertFalse(PythonCallables.acceptsExactArity(context.eval("python", "lambda a, b='x': a"), 1));
        assertTrue(PythonCallables.acceptsExactArity(context.eval("python", "lambda *args: args"), 0));
        assertFalse(PythonCallables.acceptsExactArity(context.eval("python", "lambda *args: args"), 1));
        assertTrue(PythonCallables.acceptsExactArity(context.eval("python", "type('T', (), {'m': lambda self, a: a})().m"), 1));
        assertFalse(PythonCallables.acceptsExactArity(context.eval("python", "__import__('functools').partial(lambda a, b: a, 1)"), 1));
        assertFalse(PythonCallables.acceptsExactArity(context.eval("python", "1"), 0));
    }

    @Test
    void nonCallablesAndHostObjectsAreNotAdapted() {
        assertFalse(PythonCallables.acceptsArity(context.eval("python", "1"), 0));
        assertFalse(PythonCallables.acceptsArity(context.eval("python", "None"), 0));
        assertFalse(PythonCallables.acceptsArity(context.asValue((Runnable) () -> { }), 0));
        assertFalse(PythonCallables.acceptsArity(null, 0));
    }

    @Test
    void resolvesTheFunctionalMethod() {
        assertEquals("apply", PythonCallables.functionalMethod(Function.class).getName());
        assertEquals("compare", PythonCallables.functionalMethod(java.util.Comparator.class).getName());
        assertEquals("run", PythonCallables.functionalMethod(Runnable.class).getName());
        assertEquals("apply", PythonCallables.functionalMethod(java.util.function.UnaryOperator.class).getName());
        assertNull(PythonCallables.functionalMethod(List.class));
        assertNull(PythonCallables.functionalMethod(String.class));
        for (Class<?> type : PythonCallables.STANDARD_INTERFACES) {
            assertNotNull(PythonCallables.functionalMethod(type), type.getName());
        }
    }

    @Test
    void selectsOverloadsByArity() {
        Value overloads = context.asValue(new Overloads());
        Value python = context.eval("python", """
            import functools
            def calls(overloads):
                return [
                    overloads.apply(lambda a: a.upper()),
                    overloads.apply(lambda a, b: a + b),
                    overloads.check(lambda a: a == 'x'),
                    overloads.check(lambda a, n: n == 3),
                    overloads.run(lambda: 'value'),
                    overloads.run(lambda *args: 'varargs'),
                    overloads.apply(lambda a, b='default': a + b),
                    overloads.check(lambda a, *rest: a == 'x'),
                    # only fits the two-argument overload, through its default
                    overloads.check(lambda a, b, c=1: True),
                    # unreadable arity and *args only: the default conversion decides, Function as before
                    overloads.apply(functools.partial(lambda prefix, value: prefix + value, 'p')),
                    overloads.apply(lambda *args: 'v'),
                ]
            calls
            """);
        assertEquals(
            List.of("function:A", "bifunction:ab", "predicate:true", "bipredicate:true", "supplier:value", "supplier:varargs",
                "bifunction:ab", "predicate:true", "bipredicate:true", "function:pa", "function:v"),
            python.execute(overloads).as(List.class)
        );
    }

    @Test
    void lambdasStayCallablesWhenReadBack() {
        Value overloads = context.asValue(new Overloads());
        Value python = context.eval("python", """
            def calls(overloads):
                callback = lambda value: None
                overloads.register(callback)
                stored = overloads.registered()
                seen = []
                overloads.register(lambda value: seen.append(value))
                overloads.registered()('hello')
                return [stored is callback, stored == callback, callable(stored), seen == ['hello']]
            calls
            """);
        assertEquals(List.of(true, true, true, true), python.execute(overloads).as(List.class));
    }

    @Test
    void adaptsCallablesToCustomInterfacesExplicitly() {
        Value callable = context.eval("python", "lambda value, count: value * count");
        Overloads.BiCallback adapted = PythonInterop.fn(Overloads.BiCallback.class, callable);
        assertEquals("xx", adapted.call("x", 2));
        assertTrue(adapted.toString().contains("BiCallback"));
        assertSame(adapted, adapted);
        assertNotNull(PythonInterop.fn(Runnable.class, context.eval("python", "lambda: None")));

        Value overloads = context.asValue(new Overloads());
        Value python = context.eval("python", """
            import java
            PythonInterop = java.type('io.micronaut.context.python.PythonInterop')
            Callback = java.type('io.micronaut.context.python.PythonCallablesTest$Overloads$Callback')
            BiCallback = java.type('io.micronaut.context.python.PythonCallablesTest$Overloads$BiCallback')
            def calls(overloads):
                proxy = PythonInterop.fn(Callback, lambda value: value + '!')
                overloads.callback(proxy)
                stored = overloads.callbacks()[0]
                return [
                    overloads.callback(PythonInterop.fn(BiCallback, lambda value, count: value * count)),
                    stored == proxy,
                    stored.getClass().getInterfaces()[0].getSimpleName(),
                ]
            calls
            """);
        assertEquals(
            List.of("custombi:xx", true, "Callback"),
            python.execute(overloads).as(List.class)
        );
    }

    @Test
    void customOverloadsStillNeedExplicitAdaptation() {
        Value overloads = context.asValue(new Overloads());
        Value python = context.eval("python", "lambda overloads: overloads.callback(lambda value: value + '!')");
        assertThrows(PolyglotException.class, () -> python.execute(overloads));
    }

    @Test
    void rejectsNonFunctionalInterfacesAndNonCallables() {
        Value callable = context.eval("python", "lambda: 1");
        Value nonCallable = context.eval("python", "1");
        assertThrows(IllegalArgumentException.class, () -> PythonInterop.fn(List.class, callable));
        assertThrows(IllegalArgumentException.class, () -> PythonInterop.fn(Runnable.class, nonCallable));
    }

    private void assertAcceptsNoArity(String source) {
        Value callable = context.eval("python", source);
        assertNull(PythonCallables.arityOf(callable), source);
        for (int arity = 0; arity < 5; arity++) {
            assertFalse(PythonCallables.acceptsArity(callable, arity), source + " with " + arity + " arguments");
        }
    }

    private void assertArity(String source, int min, int max) {
        Value callable = context.eval("python", source);
        int[] arities = {0, 1, 2, 3, 4};
        for (int arity : arities) {
            assertEquals(arity >= min && arity <= max, PythonCallables.acceptsArity(callable, arity),
                source + " with " + arity + " arguments");
        }
    }

    public static final class Overloads {
        private final List<Consumer<String>> consumers = new ArrayList<>();
        private final List<Callback> callbacks = new ArrayList<>();

        public String apply(Function<String, String> function) {
            return "function:" + function.apply("a");
        }

        public String apply(BiFunction<String, String, String> function) {
            return "bifunction:" + function.apply("a", "b");
        }

        public String check(Predicate<String> predicate) {
            return "predicate:" + predicate.test("x");
        }

        public String check(BiPredicate<String, Integer> predicate) {
            return "bipredicate:" + predicate.test("x", 3);
        }

        public String run(Runnable runnable) {
            runnable.run();
            return "runnable";
        }

        public String run(Supplier<String> supplier) {
            return "supplier:" + supplier.get();
        }

        public void register(Consumer<String> consumer) {
            consumers.add(consumer);
        }

        public Consumer<String> registered() {
            return consumers.get(consumers.size() - 1);
        }

        public String callback(Callback callback) {
            callbacks.add(callback);
            return "custom:" + callback.call("x");
        }

        public String callback(BiCallback callback) {
            return "custombi:" + callback.call("x", 2);
        }

        public List<Callback> callbacks() {
            return callbacks;
        }

        @FunctionalInterface
        public interface Callback {
            String call(String value);
        }

        @FunctionalInterface
        public interface BiCallback {
            String call(String value, int count);
        }
    }
}
