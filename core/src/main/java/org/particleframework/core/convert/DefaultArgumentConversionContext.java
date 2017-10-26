/*
 * Copyright 2017 original authors
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
package org.particleframework.core.convert;

import org.particleframework.core.annotation.Internal;
import org.particleframework.core.type.Argument;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Default implementation of the {@link ConversionContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultArgumentConversionContext implements ArgumentConversionContext {
    private final Argument<?> argument;
    private final Locale finalLocale;
    private final Charset finalCharset;
    private final List<ConversionError> conversionErrors = new ArrayList<>();

    DefaultArgumentConversionContext(Argument<?> argument, Locale finalLocale, Charset finalCharset) {
        this.argument = argument;
        this.finalLocale = finalLocale;
        this.finalCharset = finalCharset;
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return argument.getTypeVariables();
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return new AnnotatedElement[] { argument };
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
        if(exception != null) {
            conversionErrors.add(()-> exception);
        }
    }

    @Override
    public void reject(Object value, Exception exception) {
        if(exception != null) {
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
        if( !conversionErrors.isEmpty()) {
            return Optional.of(conversionErrors.get(conversionErrors.size()-1));
        }
        return Optional.empty();
    }

    @Override
    public Iterator<ConversionError> iterator() {
        return Collections.unmodifiableCollection(conversionErrors).iterator();
    }

    @Override
    public Argument<?> getArgument() {
        return argument;
    }
}
