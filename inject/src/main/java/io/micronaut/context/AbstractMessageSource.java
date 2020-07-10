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

    private static final char QUOT = '\'';
    private static final char L_BRACE = '{';
    private static final char R_BRACE = '}';

    @NonNull
    @Override
    public String interpolate(@NonNull String template, @NonNull MessageContext context) {
        ArgumentUtils.requireNonNull("template", template);
        ArgumentUtils.requireNonNull("context", context);

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == QUOT) {
                int next = i + 1;
                if (next < template.length()) {
                    c = template.charAt(next);
                    if (c == QUOT) {
                        i++;
                        builder.append(QUOT);
                    } else {
                        StringBuilder escaped = new StringBuilder();
                        while (c != QUOT) {
                            escaped.append(c);
                            if (++next < template.length()) {
                                c = template.charAt(next);
                            } else {
                                break;
                            }
                        }
                        if (escaped.length() > 0) {
                            i = next;
                            builder.append(escaped);
                        }
                    }
                }
            } else if (c == L_BRACE) {
                StringBuilder variable = new StringBuilder();
                int next = i + 1;
                if (next < template.length()) {
                    c = template.charAt(next);
                    while (c != R_BRACE) {
                        variable.append(c);
                        if (++next < template.length()) {
                            c = template.charAt(next);
                        } else {
                            break;
                        }
                    }
                    if (variable.length() > 0) {
                        i = next;
                        String var = variable.toString();
                        if (c == R_BRACE) {
                            final Object val = context.getVariables().get(var);
                            if (val != null) {
                                builder.append(val);
                            } else {
                                final String resolved = getMessage(var, context).orElse(var);
                                builder.append(resolved);
                            }
                        } else {
                            builder.append(L_BRACE).append(var);
                        }
                    }
                } else {
                    builder.append(c);
                }
            } else {
                builder.append(c);
            }
        }

        return builder.toString();
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
