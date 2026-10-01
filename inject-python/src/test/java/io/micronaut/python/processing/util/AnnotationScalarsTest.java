package io.micronaut.python.processing.util;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PrimitiveElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class AnnotationScalarsTest {

    private static final ClassElement STRING = ClassElement.of(String.class);

    @Test
    void pythonLiteralsNarrowToTheDeclaredMemberType() {
        assertEquals("true", AnnotationScalars.coerce(true, STRING));
        assertEquals("42", AnnotationScalars.coerce(42, STRING));
        assertEquals("", AnnotationScalars.coerce("", STRING));
        assertEquals(1L, AnnotationScalars.coerce(1, PrimitiveElement.LONG));
        assertEquals((byte) 7, AnnotationScalars.coerce(7, PrimitiveElement.BYTE));
        assertEquals(1.5f, AnnotationScalars.coerce(1.5, PrimitiveElement.FLOAT));
        assertEquals('a', AnnotationScalars.coerce("a", PrimitiveElement.CHAR));
    }

    @Test
    void literalsOutsideTheMemberTypeAreRejectedLikeJavaSource() {
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce(256, PrimitiveElement.BYTE));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce(1.5, PrimitiveElement.INT));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce(Long.MAX_VALUE, PrimitiveElement.INT));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce("abc", PrimitiveElement.CHAR));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce("", PrimitiveElement.CHAR));
        // a huge double must not saturate to the maximum and pass the range check
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce(1e100, PrimitiveElement.LONG));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.number("1", "int"));
        // a finite double that only overflows once narrowed to float
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.coerce(1e100, PrimitiveElement.FLOAT));
        // an array element that prints as one character is still not a char literal
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.character((Object) 1));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.character((Object) true));
        assertEquals('x', AnnotationScalars.character((Object) "x"));
        assertThrows(IllegalArgumentException.class, () -> AnnotationScalars.bool("yes"));
        assertEquals((byte) -128, AnnotationScalars.coerce(-128, PrimitiveElement.BYTE));
        assertEquals(2, AnnotationScalars.coerce(2.0, PrimitiveElement.INT));
    }

    @Test
    void valuesThatNeedNoNarrowingPassThrough() {
        Object list = java.util.List.of("a");
        assertSame(list, AnnotationScalars.coerce(list, STRING));
        assertEquals(true, AnnotationScalars.coerce(true, PrimitiveElement.BOOLEAN));
        assertNull(AnnotationScalars.coerce(null, STRING));
        assertEquals("x", AnnotationScalars.coerce("x", null));
    }
}
