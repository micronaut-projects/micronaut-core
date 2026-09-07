package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Python helpers live in one module, {@code micronaut_runtime}, loaded once per context.
 */
final class PythonRuntimeModuleTest {

    private static final List<String> HELPERS = List.of(
        "__micronaut_new_uninitialized_instance", "__micronaut_set_instance_property", "__micronaut_set_instance_properties",
        "__micronaut_find_class_in_package_modules", "__micronaut_inspect_isclass", "__micronaut_import_module",
        "__micronaut_put_member", "__micronaut_to_python_standard_type", "__micronaut_async_member_value",
        "__micronaut_transferable_member_names", "__micronaut_utc_offset", "__micronaut_invoke_method",
        "__micronaut_get_raw_class_member", "__micronaut_prepare_introduction",
        "__micronaut_create_raw_instance", "__micronaut_create_scoped_proxy"
    );

    @Test
    void anAbstractSubclassOfAPreparedIntroductionIsPreparedOnItsOwn() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value classes = context.eval(PYTHON, """
                from abc import ABC, abstractmethod
                class Base(ABC):
                    @abstractmethod
                    def one(self): ...
                class Sub(Base):
                    @abstractmethod
                    def two(self): ...
                (Base, Sub)
                """);
            Value prepare = PythonContextRuntime.helper(context, "__micronaut_prepare_introduction");
            prepare.execute(classes.getArrayElement(0));
            // the marker set on Base must not make Sub, which adds an abstract method, look prepared
            Value sub = prepare.execute(classes.getArrayElement(1));
            assertTrue(sub.canInstantiate(), "the subclass stayed abstract");
            assertEquals(0, sub.getMember("__abstractmethods__").invokeMember("__len__").asInt());
        }
    }

    @Test
    void everyHelperIsResolvedFromTheModuleAndCachedPerContext() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRegistry.registerContext(context);
            for (String helper : HELPERS) {
                Value function = PythonContextRuntime.helper(context, helper);
                assertTrue(function.canExecute(), helper);
                assertSame(function, PythonContextRuntime.helper(context, helper), helper + " is cached");
            }
            Value instance = PythonContextRuntime.helper(context, "__micronaut_new_uninitialized_instance")
                .execute(context.eval(PYTHON, "class Book:\n    def __init__(self):\n        raise AssertionError('init must not run')\nBook"));
            assertEquals("Book", instance.getMetaObject().getMetaSimpleName());
            PythonContextRegistry.unregisterContext(context);
        }
    }
}
