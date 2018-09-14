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

package io.micronaut.configuration.security.ldap.context;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.*;

/**
 * A {@link ConvertibleValues} implementation that uses {@link Attributes} as
 * the backing data source.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class AttributesConvertibleValues implements ConvertibleValues<Object> {

    private final Attributes attributes;
    private final ConversionService conversionService = ConversionService.SHARED;

    /**
     * @param attributes The attributes
     */
    public AttributesConvertibleValues(Attributes attributes) {
        this.attributes = attributes;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new HashSet<>();
        NamingEnumeration<String> ids = attributes.getIDs();
        try {
            while (ids.hasMore()) {
                names.add(ids.next());
            }
        } catch (NamingException e) {
            //swallow
        }
        return names;
    }

    @Override
    public Collection<Object> values() {
        List<Object> values = new ArrayList<>(attributes.size());
        NamingEnumeration<? extends Attribute> all = attributes.getAll();
        try {
            while (all.hasMore()) {
                values.add(all.next().get());
            }
        } catch (NamingException e) {
            //swallow
        }
        return values;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        try {
            NamingEnumeration<?> enumeration = attributes.get(name.toString()).getAll();
            List<Object> results = new ArrayList<>();
            while (enumeration.hasMore()) {
                results.add(enumeration.next());
            }
            if (results.size() > 0) {
                Object value;
                if (results.size() > 1) {
                    value = results;
                } else {
                    value = results.get(0);
                }
                //noinspection unchecked
                return conversionService.convert(value, conversionContext);
            } else {
                return Optional.empty();
            }
        } catch (NamingException e) {
            return Optional.empty();
        }
    }
}
