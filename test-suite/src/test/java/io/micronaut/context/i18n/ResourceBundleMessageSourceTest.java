package io.micronaut.context.i18n;

import io.micronaut.context.BeanContext;
import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledInNativeImage;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "ResourceBundleMessageSourceTest")
@MicronautTest
class ResourceBundleMessageSourceTest {

    @Inject
    BeanContext beanContext;

    @DisabledInNativeImage
    @Test
    void resourceBundleMessageCacheHasAMaxSize(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        int size = 110;
        int max = 100;
        assertTrue(max < size);
        for (int i = 1; i <= size; i++) {
            HttpRequest<?> request = request(i);
            HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(request));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        }
        ResourceBundleMessageSource resourceBundleMessageSource = resourceBundleMessageSource();
        assertNotNull(resourceBundleMessageSource);
        Map<?, Optional<ResourceBundle>> cache = assertDoesNotThrow(() -> cache(resourceBundleMessageSource));
        assertEquals(max, cache.size());
    }

    private ResourceBundleMessageSource resourceBundleMessageSource() {
        Collection<MessageSource> messageSources = beanContext.getBeansOfType(MessageSource.class);
        assertNotNull(messageSources);
        assertFalse(messageSources.isEmpty());
        return messageSources.stream().filter(ResourceBundleMessageSource.class::isInstance)
            .map(rb -> (ResourceBundleMessageSource) rb)
            .findFirst()
            .orElse(null);
    }

    private static HttpRequest<?> request(int i) {
        String locale = String.format("zz-%04d", i);
        String path = String.format("/nonexistent-path-%06d", i);
        return HttpRequest.GET(path)
            .header("Accept", "text/html")
            .header("Accept-Language", locale);
    }

    private static Map<?, Optional<ResourceBundle>> cache(ResourceBundleMessageSource instance) {
        try {
            Field field = instance.getClass().getDeclaredField("bundleCache");
            field.setAccessible(true);
            return (Map<?, Optional<ResourceBundle>>) field.get(instance);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Requires(property = "spec.name", value = "ResourceBundleMessageSourceTest")
    @Factory
    static class MessageSourceFactory {
        @Singleton
        MessageSource createMessageSource() {
            return new ResourceBundleMessageSource("io.micronaut.docs.i18n.messages");
        }
    }
}
