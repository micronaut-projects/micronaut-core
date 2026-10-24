package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The slots of attributes fixed at construction: a slot keeps the converted value of an attribute
 * until a change of an attribute of an object of the class is reported, and reads an attribute every
 * time when the class serves its attributes itself.
 */
final class PythonFixedAttributesTest {

    private final AtomicInteger conversions = new AtomicInteger();
    private final PolyglotValueConverter<Object> converter = value -> {
        conversions.incrementAndGet();
        return value == null ? null : value.asLong();
    };

    @Test
    void aSlotKeepsTheAttributeUntilAChangeIsReported() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRegistry.registerContext(context);
            try {
                Value service = context.eval(PYTHON, """
                    class Service:
                        def __init__(self, rate):
                            self.rate = rate
                    Service(2)
                    """);
                context.getBindings(PYTHON).putMember("service", service);
                PythonFixedAttributes attributes = new PythonFixedAttributes();
                PythonFixedAttributes.Slot slot = new PythonFixedAttributes.Slot();

                assertEquals(2L, slot.read(service, "rate", attributes, converter));
                assertEquals(2L, slot.read(service, "rate", attributes, converter));
                assertEquals(1, conversions.get(), "the value is kept");
                assertEquals("_micronaut_fixed_setattr", context.eval(PYTHON, "type(service).__setattr__.__name__").asString());

                context.eval(PYTHON, "service.rate = 3");
                assertEquals(3L, slot.read(service, "rate", attributes, converter), "an assignment in Python");
                service.putMember("rate", 4);
                assertEquals(4L, slot.read(service, "rate", attributes, converter), "an assignment through interop");
                context.eval(PYTHON, "setattr(service, 'rate', 5)");
                assertEquals(5L, slot.read(service, "rate", attributes, converter), "setattr");

                long epoch = attributes.epoch();
                context.eval(PYTHON, "service.other = 1");
                assertEquals(epoch, attributes.epoch(), "a first assignment changes no value read before");

                context.eval(PYTHON, "del service.rate");
                assertNull(slot.read(service, "rate", attributes, converter), "a deleted attribute is read as missing");
                int before = conversions.get();
                assertNull(slot.read(service, "rate", attributes, converter));
                assertEquals(before + 1, conversions.get(), "a missing attribute is not kept");
                context.eval(PYTHON, "service.rate = 6");
                assertEquals(6L, slot.read(service, "rate", attributes, converter), "assigned again");

                Value other = context.eval(PYTHON, "Service(7)");
                assertEquals(7L, slot.read(other, "rate", attributes, converter), "another object is read for itself");
                assertEquals(6L, slot.read(service, "rate", attributes, converter));
            } finally {
                PythonContextRegistry.unregisterContext(context);
            }
        }
    }

    @Test
    void aClassServingItsAttributesIsReadEveryTime() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRegistry.registerContext(context);
            try {
                Value objects = context.eval(PYTHON, """
                    class Computed:
                        def __init__(self):
                            self.rate = 1
                        def __getattribute__(self, name):
                            return object.__getattribute__(self, name) + 1 if name == "rate" else object.__getattribute__(self, name)
                    class Guarded:
                        def __init__(self):
                            self.rate = 1
                        def __setattr__(self, name, value):
                            object.__setattr__(self, name, value)
                    class Shadowed:
                        rate = 0
                        def __init__(self):
                            self.rate = 1
                    class Described:
                        @property
                        def rate(self):
                            return 1
                    [Computed(), Guarded(), Shadowed(), Described()]
                    """);
                PythonFixedAttributes attributes = new PythonFixedAttributes();
                long[] expected = {2L, 1L, 1L, 1L};
                for (int i = 0; i < expected.length; i++) {
                    Value object = objects.getArrayElement(i);
                    PythonFixedAttributes.Slot slot = new PythonFixedAttributes.Slot();
                    conversions.set(0);
                    assertEquals(expected[i], slot.read(object, "rate", attributes, converter));
                    assertEquals(expected[i], slot.read(object, "rate", attributes, converter));
                    assertEquals(2, conversions.get(), "read every time: " + object);
                }
                assertEquals("__setattr__", context.eval(PYTHON, "Guarded.__setattr__.__name__").asString(), "the class keeps its own __setattr__");
            } finally {
                PythonContextRegistry.unregisterContext(context);
            }
        }
    }
}
