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
package io.micronaut.jackson.bind;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.bind.BeanPropertyBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.runtime.bind.AbstractBeanPropertyBinder;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * An {@link io.micronaut.core.bind.ArgumentBinder} capable of binding from an object from a map.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Requires(property = "jackson.bean-property-binder.enabled", value = "true")
@Singleton
public class JacksonBeanPropertyBinder extends AbstractBeanPropertyBinder implements BeanPropertyBinder {

    private final ObjectMapper objectMapper;
    private final int arraySizeThreshhold;

    /**
     * @param objectMapper  To read/write JSON
     * @param configuration The configuration for Jackson JSON parser
     */
    public JacksonBeanPropertyBinder(ObjectMapper objectMapper, JacksonConfiguration configuration) {
        this.objectMapper = objectMapper;
        this.arraySizeThreshhold = configuration.getArraySizeThreshold();
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            Map objectNode = buildMapObject(source.entrySet());
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            JavaType javaType = JacksonConfiguration.constructType(context.getArgument(), typeFactory);
            Object result = objectMapper.convertValue(objectNode, javaType);
            return () -> Optional.of(result);
        } catch (Exception e) {
            context.reject(e);
            return new BindingResult<Object>() {
                @Override
                public List<ConversionError> getConversionErrors() {
                    return CollectionUtils.iterableToList(context);
                }

                @Override
                public boolean isSatisfied() {
                    return false;
                }

                @Override
                public Optional<Object> getValue() {
                    return Optional.empty();
                }
            };
        }
    }

    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            Map objectNode = buildMapObject(source);
            return objectMapper.convertValue(objectNode, type);
        } catch (Exception e) {
            throw newConversionError(null, e);
        }
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            Map objectNode = buildMapObject(source);
            JsonNode jacksonTree = objectMapper.valueToTree(objectNode);
            objectMapper.readerForUpdating(object).readValue(jacksonTree);
        } catch (Exception e) {
            context.reject(e);
        }
        return object;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        try {
            Map objectNode = buildMapObject(source);
            JsonNode jacksonTree = objectMapper.valueToTree(objectNode);
            return objectMapper.readerForUpdating(object).readValue(jacksonTree);
        } catch (Exception e) {
            throw newConversionError(object, e);
        }
    }

    /**
     * @param object The bean
     * @param e      The exception object
     * @return The new conversion error
     */
    protected ConversionErrorException newConversionError(Object object, Exception e) {
        if (e instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) e;
            Object originalValue = ife.getValue();
            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.ofNullable(originalValue);
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            List<JsonMappingException.Reference> path = ife.getPath();
            String name;
            if (!path.isEmpty()) {
                name = path.get(path.size() - 1).getFieldName();
            } else {
                name = NameUtils.decapitalize(type.getSimpleName());
            }
            return new ConversionErrorException(Argument.of(ife.getTargetType(), name), conversionError);
        } else {

            ConversionError conversionError = new ConversionError() {
                @Override
                public Exception getCause() {
                    return e;
                }

                @Override
                public Optional<Object> getOriginalValue() {
                    return Optional.empty();
                }
            };
            Class type = object != null ? object.getClass() : Object.class;
            return new ConversionErrorException(Argument.of(type), conversionError);
        }
    }

    @Override
    protected Object convertUnknownValue(Object o) {
        return objectMapper.valueToTree(o);
    }

    @Override
    protected int getArraySizeThreshold() {
        return arraySizeThreshhold;
    }
}
