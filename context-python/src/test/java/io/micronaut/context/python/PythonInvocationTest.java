package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bridge hot path: {@code isNone} is a plain interop null check and {@code invokePythonMethod}
 * invokes callable members directly, falling back to the descriptor-binding helper otherwise.
 */
final class PythonInvocationTest {

    @Test
    void isNoneMatchesPythonNoneAndJavaNullOnly() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value probe = context.eval(PYTHON, """
                class Probe:
                    attribute = None
                    def none(self):
                        return None
                    def falsy(self):
                        return [0, "", False, [], {}, 0.0]
                    def __str__(self):
                        return "None"
                Probe()
                """);
            assertTrue(PythonConversion.isNone(null));
            assertTrue(PythonConversion.isNone(Value.asValue(null)));
            assertTrue(PythonConversion.isNone(probe.invokeMember("none")));
            assertTrue(PythonConversion.isNone(probe.getMember("attribute")));
            Value falsy = probe.invokeMember("falsy");
            for (long i = 0; i < falsy.getArraySize(); i++) {
                assertFalse(PythonConversion.isNone(falsy.getArrayElement(i)), "element " + i);
            }
            // An object whose string form is "None" is not None.
            assertFalse(PythonConversion.isNone(probe));
            assertFalse(PythonConversion.isNone(Value.asValue("None")));
            assertFalse(PythonConversion.isNone(Value.asValue(new Object())));
        }
    }

    @Test
    void invokePythonMethodDispatchesDirectlyAndFallsBackToClassDescriptors() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRegistry.registerContext(context);
            Value receiver = context.eval(PYTHON, """
                class Service:
                    def __init__(self):
                        # An instance attribute shadowing the method: getattr yields a str.
                        self.shadowed = "not callable"
                    def greet(self, name, *, punctuation="!"):
                        return "Hello " + name + punctuation
                    def shadowed(self, value):
                        return "class method " + value
                    def echo_all(self, *args):
                        return list(args)
                Service()
                """);
            assertEquals("Hello world!", PythonInvocation.invokePythonMethod(receiver, "greet", new Object[] {"world"}).asString());
            assertEquals("class method x", PythonInvocation.invokePythonMethod(receiver, "shadowed", new Object[] {"x"}).asString());
            assertEquals(0, PythonInvocation.invokePythonMethod(receiver, "echo_all", null).getArraySize());
            Value echoed = PythonInvocation.invokePythonMethod(receiver, "echo_all", new Object[] {1, "two", null});
            assertEquals(3, echoed.getArraySize());
            assertEquals(1, echoed.getArrayElement(0).asInt());
            assertEquals("two", echoed.getArrayElement(1).asString());
            assertTrue(echoed.getArrayElement(2).isNull());
            assertThrows(IllegalArgumentException.class, () -> PythonInvocation.invokePythonMethod(receiver, "missing", new Object[0]));
            assertEquals(0, PythonContextRegistry.activeExecutions());
            PythonContextRegistry.unregisterContext(context);
        }
    }

    /**
     * An introduced method exists as a member of the proxy only. An instance that is not the proxy answers
     * {@code None} for a reference or void return type; a primitive return type has no such answer and the
     * missing member is reported.
     */
    @Test
    void invokeIntroducedMethodAnswersNoneForMissingReferenceMembersOnly() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRegistry.registerContext(context);
            Value proxy = context.eval(PYTHON, """
                class EntityProxy:
                    def find_name(self, id):
                        return "name " + str(id)
                    def count(self):
                        return 3
                EntityProxy()
                """);
            Value entity = context.eval(PYTHON, """
                class Entity:
                    pass
                Entity()
                """);
            assertEquals("name 1", PythonInvocation.invokeIntroducedMethod(proxy, "find_name", String.class, new Object[] {1}).asString());
            assertEquals(3, PythonInvocation.invokeIntroducedMethod(proxy, "count", int.class, null).asInt());
            assertTrue(PythonInvocation.invokeIntroducedMethod(entity, "find_name", String.class, new Object[] {1}).isNull());
            assertTrue(PythonInvocation.invokeIntroducedMethod(entity, "refresh", void.class, null).isNull());
            IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> PythonInvocation.invokeIntroducedMethod(entity, "count", int.class, null));
            assertTrue(missing.getMessage().contains("[count]"), missing.getMessage());
            assertTrue(missing.getMessage().contains("[int]"), missing.getMessage());
            assertEquals(0, PythonContextRegistry.activeExecutions());
            PythonContextRegistry.unregisterContext(context);
        }
    }
}
