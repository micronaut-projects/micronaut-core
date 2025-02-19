package io.micronaut.runtime.context;

import io.micronaut.context.MessageSource;
import io.micronaut.context.StaticMessageSource;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static io.micronaut.core.order.Ordered.HIGHEST_PRECEDENCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "CompositeMessageSourceTest")
@MicronautTest(startApplication = false)
class CompositeMessageSourceTest {
    private static final Locale SPANISH = new Locale("es", "ES");

    @Test
    void messageSourcesAreSorted(MessageSource messageSource) {
        String code = "jakarta.validation.constraints.Positive.message";
        Optional<String> messageOptional = messageSource.getMessage(code, Locale.ENGLISH);
        assertTrue(messageOptional.isPresent());
        assertEquals("Must be positive", messageOptional.get());
        messageOptional = messageSource.getMessage(code, SPANISH);
        assertTrue(messageOptional.isPresent());
        assertEquals("Debe ser positivo", messageOptional.get());
    }

    @Requires(property = "spec.name", value = "CompositeMessageSourceTest")
    @Factory
    static class MessageSourceFactory {
        @Singleton
        MessageSource createMessageSource() {
            return new ResourceBundleMessageSource("i18n.messages", HIGHEST_PRECEDENCE);
        }
    }

    @Requires(property = "spec.name", value = "CompositeMessageSourceTest")
    @Singleton
    static class DefaultMessages extends StaticMessageSource {
        DefaultMessages() {
            addMessage("jakarta.validation.constraints.Positive.message", "must be greater than 0");
        }
    }
}
