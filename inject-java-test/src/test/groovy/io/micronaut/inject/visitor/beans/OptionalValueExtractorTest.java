package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

public class OptionalValueExtractorTest {
    public OptionalValueExtractorTest() {
    }

    @Test
    public void optionalValueExtractor() {
        BeanIntrospection<OptionalHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalHolder.class);
        BeanProperty<OptionalHolder, Object> property = introspection.getProperty("optional").get();
        Assertions.assertNotNull(property.asArgument().getFirstTypeVariable().get().getAnnotationMetadata().getAnnotation(NotBlank.class));
        Assertions.assertEquals(property.getType(), Optional.class);
        Assertions.assertEquals(Optional.of("hello"), property.get(new OptionalHolder(Optional.of("hello"))));
    }

    @Test
    public void optionalIntValueExtractor() {
        BeanIntrospection<OptionalIntHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalIntHolder.class);
        BeanProperty<OptionalIntHolder, Object> property = introspection.getProperty("optionalInt").get();
        Assertions.assertNotNull(property.asArgument().getAnnotation(Min.class));
        Assertions.assertEquals(property.getType(), OptionalInt.class);
        Assertions.assertEquals(OptionalInt.of(123), property.get(new OptionalIntHolder(OptionalInt.of(123))));
    }

    @Test
    public void optionalLongValueExtractor() {
        BeanIntrospection<OptionalLongHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalLongHolder.class);
        BeanProperty<OptionalLongHolder, Object> property = introspection.getProperty("optionalLong").get();
        Assertions.assertNotNull(property.asArgument().getAnnotation(Min.class));
        Assertions.assertEquals(property.getType(), OptionalLong.class);
        Assertions.assertEquals(OptionalLong.of(123L), property.get(new OptionalLongHolder(OptionalLong.of(123L))));
    }

    @Test
    public void optionalDoubleValueExtractor() {
        BeanIntrospection<OptionalDoubleHolder> introspection = BeanIntrospector.SHARED.getIntrospection(OptionalDoubleHolder.class);
        BeanProperty<OptionalDoubleHolder, Object> property = introspection.getProperty("optionalDouble").get();
        Assertions.assertNotNull(property.asArgument().getAnnotation(DecimalMin.class));
        Assertions.assertEquals(property.getType(), OptionalDouble.class);
        Assertions.assertEquals(OptionalDouble.of(12.3), property.get(new OptionalDoubleHolder(OptionalDouble.of(12.3))));
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
    private static class OptionalDoubleHolder {
        private final @NotNull @DecimalMin("5") OptionalDouble optionalDouble;

        private OptionalDoubleHolder(OptionalDouble optionalDouble) {
            this.optionalDouble = optionalDouble;
        }
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
    private static class OptionalLongHolder {
        private final @NotNull @Min(5L) OptionalLong optionalLong;

        private OptionalLongHolder(OptionalLong optionalLong) {
            this.optionalLong = optionalLong;
        }
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
    private static class OptionalIntHolder {
        private final @NotNull @Min(5L) OptionalInt optionalInt;

        private OptionalIntHolder(OptionalInt optionalInt) {
            this.optionalInt = optionalInt;
        }
    }

    @Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
    private static class OptionalHolder {
        private final Optional<@NotNull @NotBlank String> optional;

        private OptionalHolder(Optional<String> optional) {
            this.optional = optional;
        }
    }
}
