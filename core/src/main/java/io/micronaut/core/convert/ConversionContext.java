/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeVariableResolver;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A conversion context is a context object supplied to a {@link TypeConverter} that allows more accurate conversion.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConversionContext extends AnnotationMetadataProvider, TypeVariableResolver, ErrorsContext {

    /**
     * The default conversion context.
     */
    ConversionContext DEFAULT = new ConversionContext() {
    };

    /**
     * Constant for Boolean argument.
     */
    ArgumentConversionContext<Boolean> BOOLEAN = ConversionContext.of(Argument.BOOLEAN);

    /**
     * Constant for Integer argument.
     */
    ArgumentConversionContext<Integer> INT = ConversionContext.of(Argument.INT);

    /**
     * Constant for Long argument.
     */
    ArgumentConversionContext<Long> LONG = ConversionContext.of(Argument.LONG);

    /**
     * Constant for String argument.
     */
    ArgumentConversionContext<String> STRING = ConversionContext.of(Argument.STRING);

    /**
     * Constant for List<String> argument.
     */
    ArgumentConversionContext<List<String>> LIST_OF_STRING = ConversionContext.of(Argument.LIST_OF_STRING);

    /**
     * In the case where the type to be converted contains generic type arguments this map will return
     * the concrete types of those arguments. For example for the {@link Map} type two keys will be present
     * called 'K' and 'V' with the actual types of the key and value.
     *
     * @return A map of type variables
     */
    @Override
    default Map<String, Argument<?>> getTypeVariables() {
        return Collections.emptyMap();
    }

    /**
     * @return The locale to use
     */
    default Locale getLocale() {
        return Locale.getDefault();
    }

    /**
     * @return The standard charset used in conversion
     */
    default Charset getCharset() {
        return StandardCharsets.UTF_8;
    }

    /**
     * Augment this context with data for the given argument.
     *
     * @param <T>      type Generic
     * @param argument The argument
     * @return The conversion context
     */
    @SuppressWarnings("unchecked")
    default <T> ArgumentConversionContext<T> with(Argument<T> argument) {

        ConversionContext childContext = ConversionContext.of(argument);
        ConversionContext thisContext = this;
        return new DefaultArgumentConversionContext(argument, thisContext.getLocale(), thisContext.getCharset()) {
            @Override
            public <T extends Annotation> T synthesize(Class<T> annotationClass) {
                T annotation = childContext.synthesize(annotationClass);
                if (annotation == null) {
                    return thisContext.synthesize(annotationClass);
                }
                return annotation;
            }

            @Override
            public Annotation[] synthesizeAll() {
                return ArrayUtils.concat(childContext.synthesizeAll(), thisContext.synthesizeAll());
            }

            @Override
            public Annotation[] synthesizeDeclared() {
                return ArrayUtils.concat(childContext.synthesizeDeclared(), thisContext.synthesizeDeclared());
            }

            @Override
            public void reject(Exception exception) {
                thisContext.reject(exception);
            }

            @Override
            public Iterator<ConversionError> iterator() {
                return thisContext.iterator();
            }

            @Override
            public Optional<ConversionError> getLastError() {
                return thisContext.getLastError();
            }
        };
    }



    /**
     * Create a simple {@link ConversionContext} for the given generic type variables.
     *
     * @param typeVariables The type variables
     * @return The conversion context
     */
    static ConversionContext of(Map<String, Argument<?>> typeVariables) {
        return new ConversionContext() {
            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return typeVariables;
            }

        };
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables.
     *
     * @param <T>      type Generic
     * @param argument The argument
     * @return The conversion context
     */
    static <T> ArgumentConversionContext<T> of(Argument<T> argument) {
        return of(argument, null, null);
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables.
     *
     * @param <T>      type Generic
     * @param argument The argument
     * @return The conversion context
     */
    static <T> ArgumentConversionContext<T> of(Class<T> argument) {
        ArgumentUtils.requireNonNull("argument", argument);
        return of(Argument.of(argument), null, null);
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables.
     *
     * @param <T>      type Generic
     * @param argument The argument
     * @param locale   The locale
     * @return The conversion context
     */
    static <T> ArgumentConversionContext of(Argument<T> argument, @Nullable Locale locale) {
        return of(argument, locale, null);
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables.
     *
     * @param <T>      type Generic
     * @param argument The argument
     * @param locale   The locale
     * @param charset  The charset
     * @return The conversion context
     */
    static <T> ArgumentConversionContext<T> of(Argument<T> argument, @Nullable Locale locale, @Nullable Charset charset) {
        ArgumentUtils.requireNonNull("argument", argument);
        Charset finalCharset = charset != null ? charset : StandardCharsets.UTF_8;
        Locale finalLocale = locale != null ? locale : Locale.getDefault();
        return new DefaultArgumentConversionContext<>(argument, finalLocale, finalCharset);
    }
}
