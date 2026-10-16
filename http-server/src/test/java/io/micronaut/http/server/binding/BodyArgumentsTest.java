package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.http.annotation.Body;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyArgumentsTest {

    @Test
    void plainTypeIsShared() {
        Argument<String> first = BodyArguments.bodyArgument(Argument.of(String.class));
        assertSame(first, BodyArguments.bodyArgument(Argument.of(String.class)));
        assertEquals(String.class, first.getType());
        assertEquals(BodyArguments.BODY_ARGUMENT, first.getName());
        assertTrue(first.getAnnotationMetadata().hasAnnotation(Body.class));
        assertFalse(first.isNullable());
    }

    @Test
    void parameterizedTypeIsNotShared() {
        Argument<List<String>> type = Argument.listOf(String.class);
        Argument<List<String>> first = BodyArguments.bodyArgument(type);
        assertNotSame(first, BodyArguments.bodyArgument(type));
        assertEquals(String.class, first.getTypeParameters()[0].getType());
        assertTrue(first.getAnnotationMetadata().hasAnnotation(Body.class));
    }

    @Test
    void annotatedTypeIsNotShared() {
        Argument<String> type = Argument.of(String.class);
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
        Argument<String> annotated = Argument.of(String.class, "x", metadata);
        Argument<String> first = BodyArguments.bodyArgument(annotated);
        assertNotSame(first, BodyArguments.bodyArgument(annotated));
        assertNotSame(first, BodyArguments.bodyArgument(type));
        assertTrue(first.isNullable());
        assertTrue(first.getAnnotationMetadata().hasAnnotation(Body.class));
    }

    @Test
    void nullableDiffers() {
        Argument<String> nonNull = BodyArguments.bodyArgument(Argument.of(String.class));
        Argument<String> nullable = BodyArguments.bodyArgument(new NullableArgument());
        assertNotSame(nonNull, nullable);
        assertTrue(nullable.isNullable());
        assertFalse(nonNull.isNullable());
        assertSame(nullable, BodyArguments.bodyArgument(new NullableArgument()));
    }

    /**
     * A plain argument that is nullable without annotations.
     */
    private static final class NullableArgument extends DefaultArgument<String> {
        NullableArgument() {
            super(String.class, null, AnnotationMetadata.EMPTY_METADATA);
        }

        @Override
        public boolean isNullable() {
            return true;
        }
    }
}
