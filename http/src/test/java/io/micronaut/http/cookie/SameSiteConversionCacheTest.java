package io.micronaut.http.cookie;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.HttpTypeConverterRegistrar;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SameSiteConversionCacheTest {

    @Test
    @SuppressWarnings("unchecked")
    void converterDoesNotRetainInputs() throws Exception {
        MutableConversionService real = MutableConversionService.create();
        AtomicReference<Object> captured = new AtomicReference<>();
        MutableConversionService capturing = (MutableConversionService) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{MutableConversionService.class}, (proxy, method, args) -> {
                if (method.getName().equals("addConverter") && args != null && args.length == 3 && args[1] == SameSite.class) {
                    captured.set(args[2]);
                }
                return method.invoke(real, args);
            });
        new HttpTypeConverterRegistrar().register(capturing);
        assertNotNull(captured.get());
        TypeConverter<CharSequence, SameSite> converter = (TypeConverter<CharSequence, SameSite>) captured.get();

        for (int i = 0; i < 1000; i++) {
            assertTrue(converter.convert("x".repeat(100) + i, SameSite.class, ConversionContext.DEFAULT).isEmpty());
        }
        assertEquals(SameSite.Lax, converter.convert("lax", SameSite.class, ConversionContext.DEFAULT).orElseThrow());
        assertEquals(SameSite.Strict, converter.convert("STRICT", SameSite.class, ConversionContext.DEFAULT).orElseThrow());
        assertEquals(SameSite.None, converter.convert(new StringBuilder("None"), SameSite.class, ConversionContext.DEFAULT).orElseThrow());

        int retained = 0;
        for (Field field : converter.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.get(converter) instanceof Map<?, ?> map) {
                retained += map.size();
            }
        }
        assertTrue(retained <= SameSite.values().length, "the converter retained " + retained + " distinct inputs");
    }
}
