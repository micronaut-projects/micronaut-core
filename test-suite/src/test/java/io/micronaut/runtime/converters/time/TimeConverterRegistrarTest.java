package io.micronaut.runtime.converters.time;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import java.lang.reflect.Field;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "TimeConverterRegistrarTest")
@MicronautTest
class TimeConverterRegistrarTest {

    @DisabledInNativeImage
    @Test
    void errorResponseCacheHasAMaxSizeForCustomException(@Client("/") HttpClient httpClient,
                                                         MutableConversionService conversionService) {
        BlockingHttpClient client = httpClient.toBlocking();
        int size = 110;
        int max = 100;
        assertTrue(max < size);
        for (int i = 1; i <= size; i++) {
            URI uri = UriBuilder.of("/cache").path("/dateFormat").build();
            String locale = String.format("en-x-%06d", i);
            HttpRequest<?> request = HttpRequest.GET(uri)
                .header("Accept-Language", locale)
                .header("date", "01/01/2024 12:00:00 AM UTC");
            assertDoesNotThrow(() -> client.exchange(request));
        }
        MutableConversionService instance = (conversionService instanceof DefaultEnvironment)
            ? ((DefaultEnvironment) conversionService).getMutableConversionService()
            : conversionService;
        assertInstanceOf(DefaultMutableConversionService.class, instance);
        TimeConverterRegistrar timeConverterRegistrar = assertDoesNotThrow(() -> timeConverterRegistrar(instance));
        assertNotNull(timeConverterRegistrar);
        Map<String, DateTimeFormatter> cache = assertDoesNotThrow(() -> cache(timeConverterRegistrar));
        assertEquals(max, cache.size());
    }

    private TimeConverterRegistrar timeConverterRegistrar(MutableConversionService instance) {
        try {
            Field field = instance.getClass().getDeclaredField("internalConverters");
            field.setAccessible(true);
            Map<?, TypeConverter> internalConverters =
                (Map<?, TypeConverter>) field.get(instance);
            TimeConverterRegistrar timeConverterRegistrar = internalConverters.values().stream()
                .filter(v -> v.getClass().getName().contains("TimeConverterRegistrar"))
                .map(v -> {
                    try {
                        Field[] fields = v.getClass().getDeclaredFields();
                        for (Field f : fields) {
                            f.setAccessible(true);
                            Object fieldValue = f.get(v);
                            if (fieldValue instanceof TimeConverterRegistrar) {
                                return (TimeConverterRegistrar) fieldValue;
                            }
                        }
                        return null;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
            return timeConverterRegistrar;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, DateTimeFormatter> cache(TimeConverterRegistrar timeConverterRegistrar) {
        try {
            Field field = timeConverterRegistrar.getClass().getDeclaredField("formattersCache");
            field.setAccessible(true);
            return (Map<String, DateTimeFormatter>) field.get(timeConverterRegistrar);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Requires(property = "spec.name", value = "TimeConverterRegistrarTest")
    @Controller("/cache")
    static class CacheDateFormatController {
        @Status(HttpStatus.OK)
        @Get("/dateFormat")
        void dateFormat(@Format("dd/MM/yyyy hh:mm:ss a z") @Header ZonedDateTime date) {
        }
    }
}
