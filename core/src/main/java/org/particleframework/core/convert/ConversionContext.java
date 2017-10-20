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

import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Nullable;
import org.particleframework.core.type.Argument;
import org.particleframework.core.type.TypeVariableResolver;
import org.particleframework.core.util.ArrayUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * A conversion context is a context object supplied to a {@link TypeConverter} that allows more accurate conversion.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConversionContext extends AnnotatedElement, TypeVariableResolver, ErrorsContext {


    /**
     * The default conversion context
     */
    ConversionContext DEFAULT  = new ConversionContext() {};
    /**
     * In the case where the type to be converted contains generic type arguments this map will return
     * the concrete types of those arguments. For example for the {@link Map} type two keys will be present
     * called 'K' and 'V' with the actual types of the key and value
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

    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }

    @Override
    default Annotation[] getAnnotations() {
        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    @Override
    default Annotation[] getDeclaredAnnotations() {
        return AnnotationUtil.ZERO_ANNOTATIONS;
    }

    /**
     * Augment this context with data for the given argument
     *
     * @param argument The argument
     * @return The conversion context
     */
    default ConversionContext with(Argument<?> argument) {

        ConversionContext childContext = ConversionContext.of(argument);
        ConversionContext thisContext =  this;
        return new ConversionContext() {
            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return childContext.getTypeVariables();
            }

            @Override
            public Locale getLocale() {
                return thisContext.getLocale();
            }

            @Override
            public Charset getCharset() {
                return thisContext.getCharset();
            }

            @Override
            public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
                T annotation = childContext.getAnnotation(annotationClass);
                if(annotation == null) {
                    return thisContext.getAnnotation(annotationClass);
                }
                return annotation;
            }

            @Override
            public Annotation[] getAnnotations() {
                return ArrayUtils.concat(childContext.getAnnotations(), thisContext.getAnnotations());
            }

            @Override
            public Annotation[] getDeclaredAnnotations() {
                return ArrayUtils.concat(childContext.getDeclaredAnnotations(), thisContext.getDeclaredAnnotations());
            }

            @Override
            public void reject(Exception exception) {
                thisContext.reject(exception);
            }

            @Override
            public Iterator<ConversionError> iterator() {
                return thisContext.iterator();
            }
        };
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables
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
     * Create a simple {@link ConversionContext} for the given generic type variables
     *
     * @param argument The argument
     * @return The conversion context
     */
    static ConversionContext of(Argument<?> argument) {
        return of(argument, null,null);
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables
     *
     * @param argument The argument
     * @param locale The locale
     * @return The conversion context
     */
    static ConversionContext of(Argument<?> argument, @Nullable Locale locale) {
        return of(argument, locale,null);
    }

    /**
     * Create a simple {@link ConversionContext} for the given generic type variables
     *
     * @param argument The argument
     * @param locale The locale
     * @param charset The charset
     * @return The conversion context
     */
    static ArgumentConversionContext of(Argument<?> argument, @Nullable Locale locale, @Nullable Charset charset) {
        Charset finalCharset = charset != null ? charset : StandardCharsets.UTF_8;
        Locale finalLocale = locale != null ? locale : Locale.ENGLISH;
        return new DefaultArgumentConversionContext(argument, finalLocale, finalCharset);
    }

}
