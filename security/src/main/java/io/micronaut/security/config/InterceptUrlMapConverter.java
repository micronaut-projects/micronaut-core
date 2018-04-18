/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.security.config;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.HttpMethod;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
public class InterceptUrlMapConverter implements TypeConverter<Map, InterceptUrlMapPattern> {

    @Override
    public Optional<InterceptUrlMapPattern> convert(Map m, Class<InterceptUrlMapPattern> targetType, ConversionContext context) {
        if ( m == null ) {
            return Optional.empty();
        }
        String pattern = null;
        if ( m.containsKey("pattern") ) {
            Object patternObj = m.get("pattern");
            if (patternObj instanceof String) {
                pattern = (String) patternObj;
            }
        }
        if ( pattern == null ) {
            return Optional.empty();
        }
        List<String> access = new ArrayList<>();
        if ( m.containsKey("access") ) {
            Object accessObj = m.get("access");
            if (accessObj instanceof List) {
                for ( Object accessEntryObj : (List) accessObj ) {
                    if ( accessEntryObj instanceof String ) {
                        access.add((String) accessEntryObj);
                    } else {
                        return Optional.empty();
                    }
                }
            }
        }
        if ( access.isEmpty() ) {
            return Optional.empty();
        }
        HttpMethod httpMethod = HttpMethod.GET;
        if ( m.containsKey("httpMethod") ) {
            Object httpMethodObj = m.get("httpMethod");
            if ( httpMethodObj instanceof String ) {
                try {
                    httpMethod =  HttpMethod.valueOf(((String) httpMethodObj).toUpperCase());
                } catch(IllegalArgumentException e) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(new InterceptUrlMapPattern(pattern, access, httpMethod));
    }
}
