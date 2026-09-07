package io.micronaut.python.processing.util;

import io.micronaut.inject.ast.ClassElement;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonJavaTypesTest {

    @Retention(RetentionPolicy.RUNTIME)
    @interface Outer {
        Item[] items() default {};

        Mode mode() default Mode.ON;

        @Retention(RetentionPolicy.RUNTIME)
        @Repeatable(Items.class)
        @interface Item {
            String value();
        }

        @Retention(RetentionPolicy.RUNTIME)
        @interface Items {
            Item[] value();
        }

        enum Mode { ON, OFF }
    }

    @Test
    void throwablesAreRecognisedOnReflectionElements() {
        assertTrue(PythonJavaTypes.isThrowable(ClassElement.of(RuntimeException.class)));
        assertTrue(PythonJavaTypes.isThrowable(ClassElement.of(Throwable.class)));
        assertFalse(PythonJavaTypes.isThrowable(ClassElement.of(String.class)));
        assertFalse(PythonJavaTypes.isThrowable(null));
    }

    @Test
    void concreteClassesExcludeInterfacesAndAbstractTypes() {
        assertTrue(PythonJavaTypes.isConcreteClass(ClassElement.of(String.class)));
        assertFalse(PythonJavaTypes.isConcreteClass(ClassElement.of(Runnable.class)));
        assertFalse(PythonJavaTypes.isConcreteClass(ClassElement.of(Number.class)));
        assertFalse(PythonJavaTypes.isConcreteClass(null));
    }

    @Test
    void nestedTypesAreAnsweredOnceWithTheirFacts() {
        Map<String, ClassElement> known = Map.of(
            Outer.class.getName() + "$Item", ClassElement.of(Outer.Item.class),
            Outer.class.getName() + "$Items", ClassElement.of(Outer.Items.class),
            Outer.class.getName() + "$Mode", ClassElement.of(Outer.Mode.class)
        );
        List<PythonAnnotationTypes.NestedType> nested = PythonAnnotationTypes.nestedTypes(ClassElement.of(Outer.class), known::get);

        // reflection lists declared classes in no particular order; javac lists them in declaration order
        assertEquals(Set.of("Item", "Items", "Mode"), nested.stream().map(PythonAnnotationTypes.NestedType::simpleName).collect(Collectors.toSet()));
        assertEquals(3, nested.size(), "each nested type once, although Item is both declared and returned by a member");
        Map<String, PythonAnnotationTypes.NestedType> bySimpleName = nested.stream().collect(Collectors.toMap(PythonAnnotationTypes.NestedType::simpleName, n -> n));
        PythonAnnotationTypes.NestedType item = bySimpleName.get("Item");
        assertTrue(item.annotation());
        assertEquals(Outer.Items.class.getName(), item.repeatableName());
        assertEquals(Outer.Item.class.getName(), item.name());
        PythonAnnotationTypes.NestedType mode = bySimpleName.get("Mode");
        assertFalse(mode.annotation());
        assertNull(mode.repeatableName());
        assertEquals(List.of(), PythonAnnotationTypes.nestedTypes(null, known::get));
    }
}
