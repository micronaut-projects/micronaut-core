package io.micronaut.http.server.binding;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.PrimitiveTargetTypeConverter;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.http.form.FileUpload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultFormDataPrimitiveTest {

    private static DefaultFormData form(ConversionService conversionService) {
        Map<String, List<String>> fields = new LinkedHashMap<>();
        fields.put("age", List.of("12345", "1"));
        fields.put("big", List.of("9000000000"));
        fields.put("ratio", List.of("1.5"));
        fields.put("flag", List.of("on"));
        fields.put("bad", List.of("x"));
        fields.put("empty", List.of(""));
        fields.put("none", List.of());
        return new DefaultFormData(fields, Map.<String, List<FileUpload>>of(), conversionService);
    }

    // a subclass of the conversion service converts by boxing: it does not share primitive converters
    private final ConversionService boxingService = new DefaultMutableConversionService() { };
    private final DefaultFormData boxed = form(boxingService);
    private final DefaultFormData primitive = form(new DefaultMutableConversionService());

    @Test
    void primitiveAccessorsReturnWhatTheyReturnByBoxing() {
        assertFalse(boxingService.resolveConverter(String.class, Argument.INT) instanceof PrimitiveTargetTypeConverter);
        assertInstanceOf(PrimitiveTargetTypeConverter.class, new DefaultMutableConversionService().resolveConverter(String.class, Argument.INT));

        assertEquals(12345, primitive.getInt("age"));
        assertEquals(boxed.getInt("age"), primitive.getInt("age"));
        assertEquals(12345, primitive.getInt("age", 3));
        assertEquals(3, primitive.getInt("missing", 3));
        assertEquals(3, primitive.getInt("none", 3));
        assertEquals(OptionalInt.of(12345), primitive.findInt("age"));
        assertTrue(primitive.findInt("missing").isEmpty());
        assertEquals(9000000000L, primitive.getLong("big"));
        assertEquals(4L, primitive.getLong("missing", 4L));
        assertEquals(OptionalLong.of(9000000000L), primitive.findLong("big"));
        assertEquals(1.5d, primitive.getDouble("ratio"));
        assertEquals(2d, primitive.getDouble("missing", 2d));
        assertEquals(OptionalDouble.of(1.5d), primitive.findDouble("ratio"));
        assertTrue(primitive.getBoolean("flag"));
        assertFalse(primitive.getBoolean("missing", false));
    }

    @Test
    void valuesThatDoNotConvertFailAsByBoxing() {
        assertSameFailure(() -> boxed.getInt("bad"), () -> primitive.getInt("bad"));
        assertSameFailure(() -> boxed.getInt("empty"), () -> primitive.getInt("empty"));
        assertSameFailure(() -> boxed.getInt("missing"), () -> primitive.getInt("missing"));
        assertSameFailure(() -> boxed.getInt("none"), () -> primitive.getInt("none"));
        assertSameFailure(() -> boxed.getInt("bad", 3), () -> primitive.getInt("bad", 3));
        assertSameFailure(() -> boxed.findInt("bad"), () -> primitive.findInt("bad"));
        assertSameFailure(() -> boxed.getLong("bad"), () -> primitive.getLong("bad"));
        assertSameFailure(() -> boxed.findLong("empty"), () -> primitive.findLong("empty"));
        assertSameFailure(() -> boxed.getDouble("bad"), () -> primitive.getDouble("bad"));
        assertSameFailure(() -> boxed.getDouble("missing"), () -> primitive.getDouble("missing"));
    }

    @Test
    void aConverterThatReplacesTheBuiltInParserConvertsWithTheContextOfTheField() {
        // a converter that reads the argument of its context, and rejects a value it still returns
        DefaultMutableConversionService service = new DefaultMutableConversionService();
        service.addInternalConverter(CharSequence.class, Integer.class, (CharSequence value, Class<Integer> targetType, ConversionContext context) -> {
            if ("x".contentEquals(value)) {
                context.reject(value, new IllegalArgumentException("rejected"));
                return Optional.of(-1);
            }
            return Optional.of(((ArgumentConversionContext<?>) context).getArgument().getName().length());
        });
        DefaultFormData form = form(service);

        assertEquals(3, form.getInt("age"));
        assertEquals(OptionalInt.of(3), form.findInt("age"));
        ConversionErrorException e = assertThrows(ConversionErrorException.class, () -> form.getInt("bad"));
        assertTrue(e.getMessage().contains("rejected"));
    }

    private static void assertSameFailure(Executable expected, Executable actual) {
        Throwable expectedFailure = assertThrows(Throwable.class, expected);
        Throwable actualFailure = assertThrows(Throwable.class, actual);
        assertEquals(expectedFailure.getClass(), actualFailure.getClass());
        assertEquals(expectedFailure.getMessage(), actualFailure.getMessage());
    }
}
