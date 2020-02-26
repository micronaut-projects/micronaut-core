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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import java.nio.charset.Charset;
import java.util.*;

/**
 * Default implementation of the {@link ConversionContext} interface.
 *
 * @param <T> type Generic
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultArgumentConversionContext<T> implements ArgumentConversionContext<T> {
    private final Argument<T> argument;
    private final Locale finalLocale;
    private final Charset finalCharset;
    private final List<ConversionError> conversionErrors = new ArrayList<>();

    /**
     * @param argument     The argument
     * @param finalLocale  The final locale
     * @param finalCharset The final charset
     */
    DefaultArgumentConversionContext(Argument<T> argument, Locale finalLocale, Charset finalCharset) {
        this.argument = argument;
        this.finalLocale = finalLocale;
        this.finalCharset = finalCharset;
    }

    @Override
    public Argument[] getTypeParameters() {
        return argument.getTypeParameters();
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return argument.getTypeVariables();
    }

    @Override
    public Locale getLocale() {
        return finalLocale;
    }

    @Override
    public Charset getCharset() {
        return finalCharset;
    }

    @Override
    public void reject(Exception exception) {
        if (exception != null) {
            conversionErrors.add(() -> exception);
        }
    }

    @Override
    public void reject(Object value, Exception exception) {
        if (exception != null) {
            conversionErrors.add(new ConversionError() {
                @Override
                public Optional<Object> getOriginalValue() {
                    return value != null ? Optional.of(value) : Optional.empty();
                }

                @Override
                public Exception getCause() {
                    return exception;
                }
            });
        }
    }

    @Override
    public Optional<ConversionError> getLastError() {
        if (!conversionErrors.isEmpty()) {
            return Optional.of(conversionErrors.get(conversionErrors.size() - 1));
        }
        return Optional.empty();
    }

    @Override
    public Iterator<ConversionError> iterator() {
        return Collections.unmodifiableCollection(conversionErrors).iterator();
    }

    @Override
    public Argument<T> getArgument() {
        return argument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultArgumentConversionContext<?> that = (DefaultArgumentConversionContext<?>) o;
        return Objects.equals(getArgument(), that.getArgument()) &&
            Objects.equals(finalLocale, that.finalLocale) &&
            Objects.equals(finalCharset, that.finalCharset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(argument, finalLocale, finalCharset);
    }

    @Override
    public String toString() {
        return argument.toString();
    }
}
