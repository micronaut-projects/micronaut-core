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

import io.micronaut.core.util.ArgumentUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Locale;
import java.util.Objects;

/**
 * Abstract {@link MessageSource} implementation that provides basic message interpolation.
 *
 * @author graemerocher
 * @since 1.2
 */
public abstract class AbstractMessageSource implements MessageSource {
    @NonNull
    @Override
    public String interpolate(@NonNull String template, @NonNull MessageContext context) {
        ArgumentUtils.requireNonNull("template", template);
        ArgumentUtils.requireNonNull("context", context);
        int start = template.indexOf('{');
        int end = template.indexOf('}');
        boolean hasVar = start < end;
        if (hasVar) {
            StringBuilder builder = new StringBuilder();
            while (hasVar) {

                if (start > 0) {
                    builder.append(template, 0, start);
                }
                String message = template.substring(start + 1, end);
                final Object val = context.getVariables().get(message);
                if (val != null) {
                    builder.append(val);
                } else {

                    final String resolved = getMessage(message, context)
                            .map(msg -> interpolate(msg, context))
                            .orElse(message);
                    builder.append(resolved);
                }

                final String remaining = template.substring(end + 1);
                start = remaining.indexOf('{');
                end = remaining.indexOf('}');
                hasVar = start < end;
                if (!hasVar) {
                    builder.append(remaining);
                } else {
                    template = remaining;
                }
            }

            return builder.toString();
        }
        return template;
    }

    /**
     * Internal key storage.
     */
    protected final class MessageKey {
        final Locale locale;
        final String code;

        /**
         * Default constructor.
         * @param locale The locale
         * @param code The code
         */
        public MessageKey(@NonNull Locale locale, @NonNull String code) {
            this.locale = locale;
            this.code = code;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MessageKey key = (MessageKey) o;
            return locale.equals(key.locale) &&
                    code.equals(key.code);
        }

        @Override
        public int hashCode() {
            return Objects.hash(locale, code);
        }
    }
}
