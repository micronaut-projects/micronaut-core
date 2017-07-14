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

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A conversion context is a context object supplied to a {@link TypeConverter} that allows more accurate conversion.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConversionContext {

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
    default Map<String, Class> getTypeVariables() {
        return Collections.emptyMap();
    }

    /**
     * @return The locale to use
     */
    default Locale getLocale() {
        return Locale.getDefault();
    }

    /**
     * @return The format to use
     */
    default Optional<String> getFormat() {
        return Optional.empty();
    }


    static ConversionContext of(Map<String, Class> typeVariables) {
        return new ConversionContext() {
            @Override
            public Map<String, Class> getTypeVariables() {
                return typeVariables;
            }
        };
    }

    static ConversionContext of(String format, Locale locale) {
        return new ConversionContext() {
            @Override
            public Locale getLocale() {
                return locale;
            }

            @Override
            public Optional<String> getFormat() {
                return Optional.of(format);
            }
        };
    }

    static ConversionContext of(Map<String, Class> typeVariables, String format, Locale locale) {
        return new ConversionContext() {
            @Override
            public Map<String, Class> getTypeVariables() {
                return typeVariables;
            }

            @Override
            public Locale getLocale() {
                return locale;
            }

            @Override
            public Optional<String> getFormat() {
                return Optional.of(format);
            }
        };
    }
}
