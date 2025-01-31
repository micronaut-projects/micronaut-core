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

import static org.junit.jupiter.api.Assertions.assertEquals;

@Property(name = "spec.name", value = "CompositeMessageSourceTest")
@MicronautTest(startApplication = false)
class CompositeMessageSourceTest {

    @Test
    void messageSourcesAreSorted(MessageSource messageSource) {
        String code = "jakarta.validation.constraints.Positive.message";
        assertEquals("Must be positive", messageSource.getMessage(code, Locale.ENGLISH).get());
    }

    @Requires(property = "spec.name", value = "CompositeMessageSourceTest")
    @Factory
    static class MessageSourceFactory {

        @Singleton
        MessageSource createMessageSource() {
            return new MessageSource() {
                private final MessageSource delegate = new ResourceBundleMessageSource("i18n.messages");

                @Override
                public @NonNull Optional<String> getRawMessage(@NonNull String code, @NonNull MessageContext context) {
                    return delegate.getRawMessage(code, context);
                }

                @Override
                public @NonNull String interpolate(@NonNull String template, @NonNull MessageContext context) {
                    return delegate.interpolate(template, context);
                }

                @Override
                public int getOrder() {
                    return HIGHEST_PRECEDENCE;
                }
            };
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
