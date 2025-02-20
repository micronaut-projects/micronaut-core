/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The converter that converts {@link CharSequence} to an enum.
 *
 * @param <T> T The enum type
 * @author Denis Stepanov
 * @since 4.2.0
 */
@Internal
public final class CharSequenceToEnumConverter<T extends Enum<T>> implements TypeConverter<CharSequence, T> {

    private static final String ANNOTATION_JSON_CREATOR = "com.fasterxml.jackson.annotation.JsonCreator";

    private final Map<String, Optional<Method>> creatorMethodsCache = new ConcurrentHashMap<>();

    @Override
    public Optional<T> convert(CharSequence value, Class<T> targetType, ConversionContext context) {
        if (StringUtils.isEmpty(value)) {
            return Optional.empty();
        }

        String stringValue = value.toString();

        var creatorMethodOpt = findCreatorMethod(targetType);
        if (creatorMethodOpt.isPresent()) {
            try {
                var creatorMethod = creatorMethodOpt.get();
                return Optional.ofNullable((T) creatorMethod.invoke(null, convertStringArg(stringValue, creatorMethod.getParameterTypes()[0], context)));
            } catch (IllegalAccessException | InvocationTargetException e) {
                context.reject(value, e);
                return Optional.empty();
            }
        }
        try {
            T val = Enum.valueOf(targetType, stringValue);
            return Optional.of(val);
        } catch (IllegalArgumentException e) {
            try {
                T val = Enum.valueOf(targetType, NameUtils.environmentName(stringValue));
                return Optional.of(val);
            } catch (Exception e1) {
                Optional<T> valOpt = Arrays.stream(targetType.getEnumConstants())
                    .filter(val -> val.toString().equals(stringValue))
                    .findFirst();
                if (valOpt.isPresent()) {
                    return valOpt;
                }
                context.reject(value, e);
                return Optional.empty();
            }
        }
    }

    private Optional<Method> findCreatorMethod(Class<T> targetType) {

        var creatorMethodOpt = creatorMethodsCache.get(targetType.getName());
        if (creatorMethodOpt != null) {
            return creatorMethodOpt;
        }

        Method creatorMethod = null;
        for (var m : targetType.getDeclaredMethods()) {
            for (var annotation : m.getDeclaredAnnotations()) {
                if (ANNOTATION_JSON_CREATOR.equals(annotation.annotationType().getName())) {
                    creatorMethod = m;
                    break;
                }
            }
            if (creatorMethod != null) {
                break;
            }
        }
        creatorMethodOpt = Optional.ofNullable(creatorMethod);
        creatorMethodsCache.put(targetType.getName(), creatorMethodOpt);
        return creatorMethodOpt;
    }

    private Object convertStringArg(String value, Class<?> targetType, ConversionContext context) {

        if (StringUtils.isEmpty(value)) {
            return null;
        }
        var typeName = targetType.getTypeName();

        try {
            if (String.class.getName().equals(typeName)
                || CharSequence.class.getName().equals(typeName)) {
                return value;
            } else if (Byte.class.getName().equals(typeName)
                || byte.class.getName().equals(typeName)) {
                return Byte.valueOf(value);
            } else if (Short.class.getName().equals(typeName)
                || short.class.getName().equals(typeName)) {
                return Short.valueOf(value);
            } else if (Integer.class.getName().equals(typeName)
                || int.class.getName().equals(typeName)) {
                return Integer.valueOf(value);
            } else if (Long.class.getName().equals(typeName)
                || long.class.getName().equals(typeName)) {
                return Long.valueOf(value);
            } else if (Float.class.getName().equals(typeName)
                || float.class.getName().equals(typeName)) {
                return Float.valueOf(value);
            } else if (Double.class.getName().equals(typeName)
                || double.class.getName().equals(typeName)) {
                return Double.valueOf(value);
            } else if (Character.class.getName().equals(typeName)
                || char.class.getName().equals(typeName)) {
                return value.charAt(0);
            } else if (Boolean.class.getName().equals(typeName)
                || boolean.class.getName().equals(typeName)) {
                return Boolean.valueOf(value);
            } else if (BigInteger.class.getName().equals(typeName)) {
                return new BigInteger(value);
            } else if (BigDecimal.class.getName().equals(typeName)) {
                return new BigDecimal(value);
            }
        } catch (Exception e) {
            context.reject(value, e);
            return Optional.empty();
        }

        return Optional.empty();
    }
}
