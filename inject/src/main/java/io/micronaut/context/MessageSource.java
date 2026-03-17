/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.exceptions.NoSuchMessageException;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ArgumentUtils;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for resolving messages from some source.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Indexed(MessageSource.class)
public interface MessageSource extends Ordered {

    /**
     * An empty message source.
     */
    MessageSource EMPTY = new MessageSource() {
        @Override
        public Optional<String> getRawMessage(String code, MessageContext context) {
            return Optional.empty();
        }

        @Override
        public String interpolate(String template, MessageContext context) {
            return template;
        }
    };

    /**
     * Resolve a message for the given code and context.
     *
     * @param code   The code
     * @param locale The locale to use to resolve messages.
     * @return A message if present
     */
    default Optional<String> getMessage(String code, Locale locale) {
        return getMessage(code, MessageContext.of(locale));
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code      The code
     * @param locale    The locale to use to resolve messages.
     * @param variables The variables to use resolve message placeholders
     * @return A message if present
     */
    default Optional<String> getMessage(String code, Locale locale, Object... variables) {
        return getMessage(code, locale, MessageSourceUtils.variables(variables));
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code      The code
     * @param locale    The locale to use to resolve messages.
     * @param variables The variables to use resolve message placeholders
     * @return A message if present
     */
    default Optional<String> getMessage(String code, Locale locale, Map<String, Object> variables) {
        return getMessage(code, MessageContext.of(locale, variables));
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code    The code
     * @param context The context
     * @return A message if present
     */
    default Optional<String> getMessage(String code, MessageContext context) {
        Optional<String> rawMessage = getRawMessage(code, context);
        return rawMessage.map(message -> this.interpolate(message, context));
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code           The code
     * @param defaultMessage The default message to use if no other message is found
     * @param locale         The locale to use to resolve messages.
     * @return A message if present
     */
    default String getMessage(String code, String defaultMessage, Locale locale) {
        return getMessage(code, MessageContext.of(locale), defaultMessage);
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code           The code
     * @param defaultMessage The default message to use if no other message is found
     * @param locale         The locale to use to resolve messages.
     * @param variables      The variables to use resolve message placeholders
     * @return A message if present
     */
    default String getMessage(String code, String defaultMessage, Locale locale, Map<String, Object> variables) {
        return getMessage(code, MessageContext.of(locale, variables), defaultMessage);
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code           The code
     * @param defaultMessage The default message to use if no other message is found
     * @param locale         The locale to use to resolve messages.
     * @param variables      The variables to use resolve message placeholders
     * @return A message if present
     */
    default String getMessage(String code, String defaultMessage, Locale locale, Object... variables) {
        return getMessage(code, defaultMessage, locale, MessageSourceUtils.variables(variables));
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code           The code
     * @param context        The context
     * @param defaultMessage The default message to use if no other message is found
     * @return A message if present
     */
    default String getMessage(String code, MessageContext context, String defaultMessage) {
        ArgumentUtils.requireNonNull("defaultMessage", defaultMessage);
        String rawMessage = getRawMessage(code, context, defaultMessage);
        return interpolate(rawMessage, context);
    }

    /**
     * Resolve a message for the given code and context.
     *
     * @param code    The code
     * @param context The context
     * @return A message if present
     */
    Optional<String> getRawMessage(String code, MessageContext context);

    /**
     * Resolve a message for the given code and context.
     *
     * @param code           The code
     * @param context        The context
     * @param defaultMessage The default message to use if no other message is found
     * @return A message if present
     */
    default String getRawMessage(String code, MessageContext context, String defaultMessage) {
        ArgumentUtils.requireNonNull("defaultMessage", defaultMessage);
        return getRawMessage(code, context).orElse(defaultMessage);
    }

    /**
     * Interpolate the given message template.
     *
     * @param template The template
     * @param context  The context to use.
     * @return The interpolated message.
     * @throws IllegalArgumentException If any argument specified is null
     */
    String interpolate(String template, MessageContext context);

    /**
     * Resolve a message for the given code and context or throw an exception.
     *
     * @param code    The code
     * @param context The context
     * @return The message
     * @throws NoSuchMessageException if the message is not found
     */
    default String getRequiredMessage(String code, MessageContext context) {
        return getMessage(code, context).orElseThrow(() ->
            new NoSuchMessageException(code)
        );
    }

    /**
     * Resolve a message for the given code and context or throw an exception.
     *
     * @param code    The code
     * @param context The context
     * @return The message
     * @throws NoSuchMessageException if the message is not found
     */
    default String getRequiredRawMessage(String code, MessageContext context) {
        return getRawMessage(code, context).orElseThrow(() ->
            new NoSuchMessageException(code)
        );
    }

    /**
     * The context to use.
     */
    interface MessageContext {
        /**
         * The default message context.
         */
        MessageContext DEFAULT = new MessageContext() {
        };

        /**
         * The locale to use to resolve messages.
         *
         * @return The locale
         */
        default Locale getLocale() {
            return Locale.getDefault();
        }

        /**
         * The locale to use to resolve messages.
         *
         * @param defaultLocale The locale to use if no locale is present
         * @return The locale
         */
        default Locale getLocale(@Nullable Locale defaultLocale) {
            return defaultLocale != null ? defaultLocale : getLocale();
        }

        /**
         * @return The variables to use resolve message placeholders
         */
        default Map<String, Object> getVariables() {
            return Collections.emptyMap();
        }

        /**
         * Obtain a message context for the given locale.
         *
         * @param locale The locale
         * @return The message context
         */
        static MessageContext of(@Nullable Locale locale) {
            return new DefaultMessageContext(locale, null);
        }

        /**
         * Obtain a message context for the given variables.
         *
         * @param variables The variables.
         * @return The message context
         */
        static MessageContext of(@Nullable Map<String, Object> variables) {
            return new DefaultMessageContext(null, variables);
        }

        /**
         * Obtain a message context for the given locale and variables.
         *
         * @param locale    The locale
         * @param variables The variables.
         * @return The message context
         */
        static MessageContext of(@Nullable Locale locale, @Nullable Map<String, Object> variables) {
            return new DefaultMessageContext(locale, variables);
        }
    }

}
