/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Default message context implementation.
 *
 * @author graemerocher
 * @since 1.2
 */
@Internal
class DefaultMessageContext implements MessageSource.MessageContext {

    private final @Nullable Locale locale;
    private final @Nullable Map<String, Object> variables;

    /**
     * Default constructor.
     * @param locale The locale
     * @param variables The message variables
     */
    DefaultMessageContext(@Nullable Locale locale, @Nullable Map<String, Object> variables) {
        this.locale = locale;
        this.variables = variables;
    }

    @Nonnull
    @Override
    public Map<String, Object> getVariables() {
        if (variables != null) {
            return Collections.unmodifiableMap(variables);
        }
        return Collections.emptyMap();
    }

    @Nonnull
    @Override
    public Locale getLocale() {
        return getLocale(Locale.getDefault());
    }

    /**
     * The locale to use to resolve messages.
     * @param defaultLocale The locale to use if no locale is present
     * @return The locale
     */
    @Nonnull
    public Locale getLocale(@Nullable Locale defaultLocale) {
        if (locale != null) {
            return locale;
        } else {
            return defaultLocale != null ? defaultLocale : Locale.getDefault();
        }
    }
}
