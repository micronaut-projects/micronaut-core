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
package io.micronaut.core.convert;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.converters.MultiValuesConverterFactory;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.convert.format.Format;
import io.micronaut.core.convert.format.FormattingTypeConverter;
import io.micronaut.core.convert.format.ReadableBytesTypeConverter;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import io.micronaut.core.io.IOUtils;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.CopyOnWriteMap;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The default conversion service. Handles basic type conversion operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultMutableConversionService implements MutableConversionService {

    private static final int CACHE_MAX = 256;
    private static final int CACHE_EVICTION_BATCH = 64;
    private static final TypeConverter UNCONVERTIBLE = (object, targetType, context) -> Optional.empty();

    private final Map<ConvertiblePair, TypeConverter> typeConverters = new ConcurrentHashMap<>();
    private final Map<ConvertiblePair, TypeConverter> converterCache = new ConcurrentHashMap<>();

    /**
     * Constructor.
     */
    public DefaultMutableConversionService() {
        registerDefaultConverters();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        if (object == null || targetType == null || context == null) {
            return Optional.empty();
        }
        if (targetType == Object.class) {
            return Optional.of((T) object);
        }
        targetType = targetType.isPrimitive() ? (Class<T>) ReflectionUtils.getWrapperType(targetType) : targetType;
        if (targetType.isInstance(object) && !(object instanceof Iterable) && !(object instanceof Map)) {
            return Optional.of((T) object);
        }

        Class<?> sourceType = object.getClass();
        final AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        if (annotationMetadata.hasStereotypeNonRepeating(Format.class)) {
            Optional<String> formattingAnn = annotationMetadata.getAnnotationNameByStereotype(Format.class);
            String formattingAnnotation = formattingAnn.orElse(null);
            ConvertiblePair pair = new ConvertiblePair(sourceType, targetType, formattingAnnotation);
            TypeConverter<Object, T> typeConverter = converterCache.get(pair);
            if (typeConverter == null) {
                typeConverter = findTypeConverter(sourceType, targetType, formattingAnnotation);
                if (typeConverter == null) {
                    return Optional.empty();
                } else {
                    addToConverterCache(pair, typeConverter);
                    if (typeConverter == UNCONVERTIBLE) {
                        return Optional.empty();
                    } else {
                        return typeConverter.convert(object, targetType, context);
                    }
                }
            } else if (typeConverter != UNCONVERTIBLE) {
                return typeConverter.convert(object, targetType, context);
            }
        } else {
            ConvertiblePair pair = new ConvertiblePair(sourceType, targetType, null);
            TypeConverter<Object, T> typeConverter = converterCache.get(pair);
            if (typeConverter == null) {
                typeConverter = findTypeConverter(sourceType, targetType, null);
                if (typeConverter == null) {
                    addToConverterCache(pair, UNCONVERTIBLE);
                    return Optional.empty();
                } else {
                    addToConverterCache(pair, typeConverter);
                    if (typeConverter == UNCONVERTIBLE) {
                        return Optional.empty();
                    } else {
                        return typeConverter.convert(object, targetType, context);
                    }
                }
            } else if (typeConverter != UNCONVERTIBLE) {
                return typeConverter.convert(object, targetType, context);
            }
        }

        return Optional.empty();
    }

    @Override
    public <S, T> boolean canConvert(Class<S> sourceType, Class<T> targetType) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType, null);
        TypeConverter<Object, T> typeConverter = converterCache.get(pair);
        if (typeConverter == null) {
            typeConverter = findTypeConverter(sourceType, targetType, null);
            if (typeConverter != null) {
                addToConverterCache(pair, typeConverter);
                return typeConverter != UNCONVERTIBLE;
            }
            return false;
        }
        return typeConverter != UNCONVERTIBLE;
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair = newPair(sourceType, targetType, typeConverter);
        typeConverters.put(pair, typeConverter);
        addToConverterCache(pair, typeConverter);
    }

    @Override
    public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> function) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        TypeConverter<S, T> typeConverter = TypeConverter.of(sourceType, targetType, function);
        typeConverters.put(pair, typeConverter);
        addToConverterCache(pair, typeConverter);
    }

    private void addToConverterCache(ConvertiblePair pair, TypeConverter<?, ?> typeConverter) {
        converterCache.put(pair, typeConverter);
        if (converterCache.size() > CACHE_MAX) {
            CopyOnWriteMap.evict(converterCache, CACHE_EVICTION_BATCH);
        }
    }

    /**
     * Default Converters.
     */
    @SuppressWarnings({"OptionalIsPresent", "unchecked"})
    private void registerDefaultConverters() {
        LinkedHashMap<Class<?>, Class<?>> primitiveArrays = new LinkedHashMap<>();
        primitiveArrays.put(Boolean[].class, boolean[].class);
        primitiveArrays.put(Byte[].class, byte[].class);
        primitiveArrays.put(Character[].class, char[].class);
        primitiveArrays.put(Double[].class, double[].class);
        primitiveArrays.put(Float[].class, float[].class);
        primitiveArrays.put(Integer[].class, int[].class);
        primitiveArrays.put(Long[].class, long[].class);
        primitiveArrays.put(Short[].class, short[].class);

        // primitive array to wrapper array
        @SuppressWarnings("rawtypes")
        Function primitiveArrayToWrapperArray = ArrayUtils::toWrapperArray;
        // wrapper to primitive array converters
        Function<Object[], Object> wrapperArrayToPrimitiveArray = ArrayUtils::toPrimitiveArray;
        for (Map.Entry<Class<?>, Class<?>> e : primitiveArrays.entrySet()) {
            Class<?> wrapperArray = e.getKey();
            Class<?> primitiveArray = e.getValue();
            addConverter(primitiveArray, wrapperArray, primitiveArrayToWrapperArray);
            addConverter(wrapperArray, primitiveArray, (Function) wrapperArrayToPrimitiveArray);
        }

        // Object -> List
        addConverter(Object.class, List.class, (object, targetType, context) -> {
            Optional<Argument<?>> firstTypeVariable = context.getFirstTypeVariable();
            Argument<?> argument = firstTypeVariable.orElse(Argument.OBJECT_ARGUMENT);
            Optional converted = DefaultMutableConversionService.this.convert(object, context.with(argument));
            if (converted.isPresent()) {
                return Optional.of(Collections.singletonList(converted.get()));
            }
            return Optional.empty();
        });

        addConverter(byte[].class, String.class, (bytes, targetType, context) -> Optional.of(new String(bytes, context.getCharset())));

        // String -> Class
        addConverter(CharSequence.class, Class.class, (object, targetType, context) -> {
            ClassLoader classLoader = targetType.getClassLoader();
            if (classLoader == null) {
                classLoader = DefaultMutableConversionService.class.getClassLoader();
            }
            //noinspection rawtypes
            return (Optional) ClassUtils.forName(object.toString(), classLoader);
        });

        // AnnotationClassValue -> Class
        addConverter(AnnotationClassValue.class, Class.class, (object, targetType, context) -> object.getType());
        addConverter(AnnotationClassValue.class, Object.class, (object, targetType, context) -> {
            if (targetType.equals(Class.class)) {
                return object.getType();
            } else {
                if (CharSequence.class.isAssignableFrom(targetType)) {
                    return Optional.of(object.getName());
                } else {
                    Optional i = object.getInstance();
                    if (i.isPresent() && targetType.isInstance(i.get())) {
                        return i;
                    }
                    return Optional.empty();
                }
            }
        });
        addConverter(AnnotationClassValue[].class, Class.class, (object, targetType, context) -> {
            if (object.length > 0) {
                final AnnotationClassValue o = object[0];
                if (o != null) {
                    return o.getType();
                }
            }
            return Optional.empty();
        });
        addConverter(AnnotationClassValue[].class, Class[].class, (object, targetType, context) -> {
            List<Class<?>> classes = new ArrayList<>(object.length);
            for (AnnotationClassValue<?> annotationClassValue : object) {
                if (annotationClassValue != null) {
                    final Optional<? extends Class<?>> type = annotationClassValue.getType();
                    if (type.isPresent()) {
                        classes.add(type.get());
                    }
                }
            }
            return Optional.of(classes.toArray(new Class<?>[0]));
        });

        // URI -> URL
        addConverter(URI.class, URL.class, uri -> {
            try {
                return uri.toURL();
            } catch (MalformedURLException e) {
                return null;
            }
        });

        // InputStream -> String
        addConverter(InputStream.class, String.class, (object, targetType, context) -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(object))) {
                return Optional.of(IOUtils.readText(reader));
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        });

        // String -> byte[]
        addConverter(CharSequence.class, byte[].class, (object, targetType, context) -> Optional.of(object.toString().getBytes(context.getCharset())));
        addConverter(Integer.class, byte[].class, (object, targetType, context) -> Optional.of(ByteBuffer.allocate(Integer.BYTES).putInt(object).array()));
        addConverter(Character.class, byte[].class, (object, targetType, context) -> Optional.of(ByteBuffer.allocate(Integer.BYTES).putChar(object).array()));
        addConverter(Long.class, byte[].class, (object, targetType, context) -> Optional.of(ByteBuffer.allocate(Long.BYTES).putLong(object).array()));
        addConverter(Short.class, byte[].class, (object, targetType, context) -> Optional.of(ByteBuffer.allocate(Short.BYTES).putShort(object).array()));
        addConverter(Double.class, byte[].class, (object, targetType, context) -> Optional.of(ByteBuffer.allocate(Double.BYTES).putDouble(object).array()));
        addConverter(Float.class, byte[].class, (object, targetType, context) -> Optional.of(ByteBuffer.allocate(Float.BYTES).putFloat(object).array()));

        // InputStream -> Number
        addConverter(InputStream.class, Number.class, (object, targetType, context) -> {
            Optional<String> convert = DefaultMutableConversionService.this.convert(object, String.class, context);
            if (convert.isPresent()) {
                return convert.flatMap(val -> DefaultMutableConversionService.this.convert(val, targetType, context));
            }
            return Optional.empty();
        });

        // Reader -> String
        addConverter(Reader.class, String.class, (object, targetType, context) -> {
            try (BufferedReader reader = object instanceof BufferedReader bufferedReader ? bufferedReader : new BufferedReader(object)) {
                return Optional.of(IOUtils.readText(reader));
            } catch (IOException e) {
                context.reject(e);
                return Optional.empty();
            }
        });

        // String -> File
        addConverter(CharSequence.class, File.class, (object, targetType, context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            return Optional.of(new File(object.toString()));
        });

        // String[] -> Enum
        addConverter(String[].class, Enum.class, (object, targetType, context) -> {
            if (object == null || object.length == 0) {
                return Optional.empty();
            }

            StringJoiner joiner = new StringJoiner("");
            for (String string : object) {
                joiner.add(string);
            }
            final String val = joiner.toString();
            return convert(val, targetType, context);
        });

        addConverter(String[].class, CharSequence.class, (object, targetType, context) -> {
            if (object == null || object.length == 0) {
                return Optional.empty();
            }

            StringJoiner joiner = new StringJoiner("");
            for (String string : object) {
                joiner.add(string);
            }
            return convert(joiner.toString(), targetType, context);
        });

        // CharSequence -> Long for bytes
        addConverter(CharSequence.class, Number.class, new ReadableBytesTypeConverter());

        // CharSequence -> Date
        addConverter(
            CharSequence.class,
            Date.class,
            (object, targetType, context) -> {
                if (StringUtils.isEmpty(object)) {
                    return Optional.empty();
                }
                try {
                    SimpleDateFormat format = resolveFormat(context);
                    return Optional.of(format.parse(object.toString()));
                } catch (ParseException e) {
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        );

        // Date -> CharSequence
        addConverter(
                Date.class,
                CharSequence.class,
                (object, targetType, context) -> {
                    SimpleDateFormat format = resolveFormat(context);
                    return Optional.of(format.format(object));
                }
        );

        // Number -> CharSequence
        addConverter(
            Number.class,
            CharSequence.class,
            (object, targetType, context) -> {
                NumberFormat format = resolveNumberFormat(context);
                if (format != null) {
                    return Optional.of(format.format(object));
                } else {
                    return Optional.of(object.toString());
                }
            }
        );

        // String -> Path
        addConverter(
                CharSequence.class,
                Path.class, (object, targetType, context) -> {
                    if (StringUtils.isEmpty(object)) {
                        return Optional.empty();
                    }
                    try {
                        return Optional.of(Paths.get(object.toString()));
                    } catch (Exception e) {
                        context.reject("Invalid path [" + object + " ]: " + e.getMessage(), e);
                        return Optional.empty();
                    }
                });

        // String -> Integer
        addConverter(CharSequence.class, Integer.class, (CharSequence object, Class<Integer> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> BigInteger
        addConverter(CharSequence.class, BigInteger.class, (CharSequence object, Class<BigInteger> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                BigInteger converted = new BigInteger(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Float
        addConverter(CharSequence.class, Float.class, (CharSequence object, Class<Float> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                Float converted = Float.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Double
        addConverter(CharSequence.class, Double.class, (CharSequence object, Class<Double> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                Double converted = Double.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Long
        addConverter(CharSequence.class, Long.class, (CharSequence object, Class<Long> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                Long converted = Long.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Short
        addConverter(CharSequence.class, Short.class, (CharSequence object, Class<Short> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                Short converted = Short.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Byte
        addConverter(CharSequence.class, Byte.class, (CharSequence object, Class<Byte> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                Byte converted = Byte.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> BigDecimal
        addConverter(CharSequence.class, BigDecimal.class, (CharSequence object, Class<BigDecimal> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                BigDecimal converted = new BigDecimal(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Boolean
        addConverter(CharSequence.class, Boolean.class, (CharSequence object, Class<Boolean> targetType, ConversionContext context) -> {
            String booleanString = object.toString().toLowerCase(Locale.ENGLISH);
            return switch (booleanString) {
                case "yes", "y", "on", "true" -> Optional.of(Boolean.TRUE);
                default -> Optional.of(Boolean.FALSE);
            };
        });

        // String -> URL
        addConverter(CharSequence.class, URL.class, (CharSequence object, Class<URL> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                String spec = object.toString();
                if (!spec.contains("://")) {
                    spec = "http://" + spec;
                }
                return Optional.of(new URL(spec));
            } catch (MalformedURLException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> URI
        addConverter(CharSequence.class, URI.class, (CharSequence object, Class<URI> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                return Optional.of(new URI(object.toString()));
            } catch (URISyntaxException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Locale
        addConverter(CharSequence.class, Locale.class, object -> StringUtils.parseLocale(object.toString()));

        // String -> UUID
        addConverter(CharSequence.class, UUID.class, (CharSequence object, Class<UUID> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(object.toString()));
            } catch (IllegalArgumentException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Currency
        addConverter(CharSequence.class, Currency.class, (CharSequence object, Class<Currency> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Currency.getInstance(object.toString()));
            } catch (IllegalArgumentException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> TimeZone
        addConverter(CharSequence.class, TimeZone.class, (CharSequence object, Class<TimeZone> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            return Optional.of(TimeZone.getTimeZone(object.toString()));
        });

        // String -> Charset
        addConverter(CharSequence.class, Charset.class, (CharSequence object, Class<Charset> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            try {
                return Optional.of(Charset.forName(object.toString()));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                context.reject(object, e);
                return Optional.empty();
            }
        });

        // String -> Character
        addConverter(CharSequence.class, Character.class, (CharSequence object, Class<Character> targetType, ConversionContext context) -> {
            String str = object.toString();
            if (str.length() == 1) {
                return Optional.of(str.charAt(0));
            } else {
                return Optional.empty();
            }
        });

        // String -> Array
        addConverter(CharSequence.class, Object[].class, (CharSequence object, Class<Object[]> targetType, ConversionContext context) -> {
            if (object instanceof AnnotationClassValue<?> annotationClassValue && targetType.equals(AnnotationClassValue[].class)) {
                AnnotationClassValue<?>[] array = new AnnotationClassValue<?>[1];
                array[0] = annotationClassValue;
                return Optional.of(array);
            }

            String str = object.toString();
            String[] strings = str.split(",");
            Class<?> componentType = ReflectionUtils.getWrapperType(targetType.getComponentType());
            Object newArray = Array.newInstance(componentType, strings.length);
            for (int i = 0; i < strings.length; i++) {
                String string = strings[i];
                Optional<?> converted = convert(string, componentType);
                if (converted.isPresent()) {
                    Array.set(newArray, i, converted.get());
                }
            }
            return Optional.of((Object[]) newArray);
        });

        // String -> Int Array
        addConverter(CharSequence.class, int[].class, (CharSequence object, Class<int[]> targetType, ConversionContext context) -> {
            String str = object.toString();
            String[] strings = str.split(",");
            Object newArray = Array.newInstance(int.class, strings.length);
            for (int i = 0; i < strings.length; i++) {
                String string = strings[i];
                Optional<?> converted = convert(string, int.class);
                if (converted.isPresent()) {
                    Array.set(newArray, i, converted.get());
                }
            }
            return Optional.of((int[]) newArray);
        });

        // String -> Char Array
        addConverter(String.class, char[].class, (String object, Class<char[]> targetType, ConversionContext context) -> Optional.of(object.toCharArray()));

        // Object[] -> String[]
        addConverter(Object[].class, String[].class, (Object[] object, Class<String[]> targetType, ConversionContext context) -> {
            String[] strings = new String[object.length];
            for (int i = 0; i < object.length; i++) {
                Object o = object[i];
                if (o != null) {
                    strings[i] = o.toString();
                }
            }
            return Optional.of(strings);
        });

        // String -> Enum
        addConverter(CharSequence.class, Enum.class, (CharSequence object, Class<Enum> targetType, ConversionContext context) -> {
            if (StringUtils.isEmpty(object)) {
                return Optional.empty();
            }
            String stringValue = object.toString();
            try {
                Enum val = Enum.valueOf(targetType, stringValue);
                return Optional.of(val);
            } catch (IllegalArgumentException e) {
                try {
                    Enum val = Enum.valueOf(targetType, NameUtils.environmentName(stringValue));
                    return Optional.of(val);
                } catch (Exception e1) {
                    Optional<Enum> valOpt = Arrays.stream(targetType.getEnumConstants())
                            .filter(val -> val.toString().equals(stringValue))
                            .findFirst();
                    if (valOpt.isPresent()) {
                        return valOpt;
                    }
                    context.reject(object, e);
                    return Optional.empty();
                }
            }
        });

        // Object -> String
        addConverter(Object.class, String.class, (Object object, Class<String> targetType, ConversionContext context) -> Optional.of(object.toString()));

        // Number -> Number
        addConverter(Number.class, Number.class, (Number object, Class<Number> targetType, ConversionContext context) -> {
            Class<?> targetNumberType = ReflectionUtils.getWrapperType(targetType);
            if (targetNumberType.isInstance(object)) {
                return Optional.of(object);
            }
            if (targetNumberType == Integer.class) {
                return Optional.of(object.intValue());
            }
            if (targetNumberType == Long.class) {
                return Optional.of(object.longValue());
            }
            if (targetNumberType == Short.class) {
                return Optional.of(object.shortValue());
            }
            if (targetNumberType == Byte.class) {
                return Optional.of(object.byteValue());
            }
            if (targetNumberType == Float.class) {
                return Optional.of(object.floatValue());
            }
            if (targetNumberType == Double.class) {
                return Optional.of(object.doubleValue());
            }
            if (targetNumberType == BigInteger.class) {
                if (object instanceof BigDecimal bigDecimal) {
                    return Optional.of(bigDecimal.toBigInteger());
                }
                return Optional.of(BigInteger.valueOf(object.longValue()));
            }
            if (targetNumberType == BigDecimal.class) {
                return Optional.of(new BigDecimal(object.toString()));
            }
            return Optional.empty();
        });

        // String -> List/Iterable
        addConverter(CharSequence.class, Iterable.class, (CharSequence object, Class<Iterable> targetType, ConversionContext context) -> {
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable();
            Argument<?> componentType = typeVariable.orElse(Argument.OBJECT_ARGUMENT);
            ConversionContext newContext = context.with(componentType);

            Class<?> targetComponentType = ReflectionUtils.getWrapperType(componentType.getType());
            String[] strings = object.toString().split(",");
            List<Object> list = new ArrayList<>();
            for (String string : strings) {
                Optional<?> converted = convert(string, targetComponentType, newContext);
                if (converted.isPresent()) {
                    list.add(converted.get());
                }
            }
            return CollectionUtils.convertCollection((Class) targetType, list);
        });

        TypeConverter<Object, Optional> objectToOptionalConverter = (object, targetType, context) -> {
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable();
            Argument<?> componentType = typeVariable.orElse(Argument.OBJECT_ARGUMENT);
            Class<?> targetComponentType = ReflectionUtils.getWrapperType(componentType.getType());

            ConversionContext newContext = context.with(componentType).with(context.getAnnotationMetadata());
            Optional converted = convert(object, targetComponentType, newContext);
            if (converted.isPresent()) {
                return Optional.of(converted);
            }
            return Optional.of(Optional.empty());
        };

        // Optional handling
        addConverter(Object.class, Optional.class, objectToOptionalConverter);

        addConverter(Object.class, OptionalInt.class, (object, targetType, context) -> {
            Optional<Integer> converted = convert(object, Integer.class, context);
            return converted.map(OptionalInt::of).or(() -> Optional.of(OptionalInt.empty()));
        });

        addConverter(Object.class, OptionalLong.class, (object, targetType, context) -> {
            Optional<Long> converted = convert(object, Long.class, context);
            return converted.map(OptionalLong::of).or(() -> Optional.of(OptionalLong.empty()));
        });

        // Iterable -> String
        addConverter(Iterable.class, String.class, (object, targetType, context) -> Optional.of(CollectionUtils.toString(object)));

        // Iterable -> Object
        addConverter(Iterable.class, Object.class, (object, targetType, context) -> {
            if (Optional.class.isAssignableFrom(targetType)) {
                return objectToOptionalConverter.convert(object, (Class) targetType, context);
            }
            Iterator<?> i = object.iterator();
            int count = 0;
            Object value = null;
            while (i.hasNext()) {
                if (count > 0) {
                    context.reject(object, new ConversionErrorException(Argument.of(targetType), new IllegalArgumentException("Cannot convert an iterable with more than 1 value to a non collection object")));
                    return Optional.empty();
                }
                count++;
                value = i.next();
            }
            return convert(value, targetType, context);
        });

        // Iterable -> Iterable (inner type conversion)
        addConverter(Iterable.class, Iterable.class, (object, targetType, context) -> {
            if (ConvertibleValues.class.isAssignableFrom(targetType)) {
                if (object instanceof ConvertibleValues) {
                    return Optional.of(object);
                }
                return Optional.empty();
            }
            Optional<Argument<?>> typeVariable = context.getFirstTypeVariable();
            Argument<?> componentType = typeVariable.orElse(Argument.OBJECT_ARGUMENT);
            Class<?> targetComponentType = ReflectionUtils.getWrapperType(componentType.getType());

            if (targetType.isInstance(object) && targetComponentType == Object.class) {
                return Optional.of(object);
            }
            List<Object> list = new ArrayList<>();
            ConversionContext newContext = context.with(componentType);
            for (Object o : object) {
                Optional<?> converted = convert(o, targetComponentType, newContext);
                if (converted.isPresent()) {
                    list.add(converted.get());
                }
            }
            return CollectionUtils.convertCollection((Class) targetType, list);
        });

        // Object[] -> String
        addConverter(Object[].class, String.class, (object, targetType, context) -> Optional.of(ArrayUtils.toString(object)));

        // Object[] -> Object[] (inner type conversion)
        addConverter(Object[].class, Object[].class, (object, targetType, context) -> {
            Class<?> targetComponentType = targetType.getComponentType();
            List<Object> results = new ArrayList<>(object.length);
            for (Object o : object) {
                Optional<?> converted = convert(o, targetComponentType, context);
                if (converted.isPresent()) {
                    results.add(converted.get());
                }
            }
            return Optional.of(results.toArray((Object[]) Array.newInstance(targetComponentType, results.size())));
        });

        // Iterable -> Object[]
        addConverter(Iterable.class, Object[].class, (object, targetType, context) -> {
            Class<?> targetComponentType = targetType.getComponentType();
            List<Object> results = new ArrayList<>();
            for (Object o : object) {
                Optional<?> converted = convert(o, targetComponentType, context);
                if (converted.isPresent()) {
                    results.add(converted.get());
                }
            }
            return Optional.of(results.toArray((Object[]) Array.newInstance(targetComponentType, results.size())));
        });

        addConverter(Object[].class, Iterable.class, (object, targetType, context) ->
            convert(Arrays.asList(object), targetType, context)
        );

        addConverter(Object.class, Object[].class, (object, targetType, context) -> {
            Class<?> targetComponentType = targetType.getComponentType();
            Optional<?> converted = convert(object, targetComponentType);
            if (converted.isPresent()) {
                Object[] result = (Object[]) Array.newInstance(targetComponentType, 1);
                result[0] = converted.get();
                return Optional.of(result);
            }
            return Optional.empty();
        });

        // Map -> Map (inner type conversion)
        addConverter(Map.class, Map.class, (object, targetType, context) -> {
            Argument<?> keyArgument = context.getTypeVariable("K").orElse(Argument.of(String.class, "K"));
            boolean isProperties = targetType.equals(Properties.class);
            Argument<?> valArgument = context.getTypeVariable("V").orElseGet(() -> {
                if (isProperties) {
                    return Argument.of(String.class, "V");
                }
                return Argument.of(Object.class, "V");
            });
            Class<?> keyType = isProperties ? Object.class : keyArgument.getType();
            Class<?> valueType = isProperties ? Object.class : valArgument.getType();
            ConversionContext keyContext = context.with(keyArgument);
            ConversionContext valContext = context.with(valArgument);

            Map<Object, Object> newMap = isProperties ? new Properties() : new LinkedHashMap<>();

            for (Object o : object.entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry) o;
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (!keyType.isInstance(key)) {
                    Optional<?> convertedKey = convert(key, keyType, keyContext);
                    if (convertedKey.isPresent()) {
                        key = convertedKey.get();
                    } else {
                        continue;
                    }
                }
                if (!valueType.isInstance(value) || value instanceof Map || value instanceof Collection) {
                    Optional<?> converted = convert(value, valueType, valContext);
                    if (converted.isPresent()) {
                        value = converted.get();
                    } else {
                        continue;
                    }
                }
                newMap.put(key, value);
            }
            return Optional.of(newMap);
        });

        addConverter(Map.class, ConvertibleValues.class, (object, targetType, context) -> Optional.of(new ConvertibleValuesMap<Object>(object)));

        // Micronaut ByteBuffer -> byte for streamed results from HTTP clients
        addConverter(io.micronaut.core.io.buffer.ByteBuffer.class, byte[].class, (object, targetType, context) -> {
            byte[] result = object.toByteArray();
            ((ReferenceCounted) object).release();
            return Optional.of(result);
        });

        // ConvertibleMultiValues -> [?]
        addConverter(io.micronaut.core.convert.value.ConvertibleMultiValues.class, Iterable.class,
                new MultiValuesConverterFactory.MultiValuesToIterableConverter(this));
        addConverter(io.micronaut.core.convert.value.ConvertibleMultiValues.class, Map.class,
                new MultiValuesConverterFactory.MultiValuesToMapConverter(this));
        addConverter(io.micronaut.core.convert.value.ConvertibleMultiValues.class, Object.class,
                new MultiValuesConverterFactory.MultiValuesToObjectConverter(this));

        // [?] -> ConvertibleMultiValues
        addConverter(Iterable.class, io.micronaut.core.convert.value.ConvertibleMultiValues.class,
                new MultiValuesConverterFactory.IterableToMultiValuesConverter(this));
        addConverter(Map.class, io.micronaut.core.convert.value.ConvertibleMultiValues.class,
                new MultiValuesConverterFactory.MapToMultiValuesConverter(this));
        addConverter(Object.class, io.micronaut.core.convert.value.ConvertibleMultiValues.class,
                new MultiValuesConverterFactory.ObjectToMultiValuesConverter(this));

        Collection<TypeConverterRegistrar> registrars = new ArrayList<>();
        SoftServiceLoader.load(TypeConverterRegistrar.class)
            .disableFork()
            .collectAll(registrars);
        for (TypeConverterRegistrar registrar : registrars) {
            registrar.register(this);
        }
    }

    /**
     * Find the type converter.
     * @param sourceType sourceType
     * @param targetType  targetType
     * @param formattingAnnotation formattingAnnotation
     * @param <T> Generic type
     * @return type converter
     */
    protected <T> TypeConverter<Object, T> findTypeConverter(Class<?> sourceType, Class<T> targetType, String formattingAnnotation) {
        TypeConverter<Object, T> typeConverter = UNCONVERTIBLE;
        List<Class<?>> sourceHierarchy = ClassUtils.resolveHierarchy(sourceType);
        List<Class<?>> targetHierarchy = ClassUtils.resolveHierarchy(targetType);
        for (Class<?> sourceSuperType : sourceHierarchy) {
            for (Class<?> targetSuperType : targetHierarchy) {
                ConvertiblePair pair = new ConvertiblePair(sourceSuperType, targetSuperType, formattingAnnotation);
                typeConverter = typeConverters.get(pair);
                if (typeConverter != null) {
                    addToConverterCache(pair, typeConverter);
                    return typeConverter;
                }
            }
        }
        boolean hasFormatting = formattingAnnotation != null;
        if (hasFormatting) {
            for (Class<?> sourceSuperType : sourceHierarchy) {
                for (Class<?> targetSuperType : targetHierarchy) {
                    ConvertiblePair pair = new ConvertiblePair(sourceSuperType, targetSuperType);
                    typeConverter = typeConverters.get(pair);
                    if (typeConverter != null) {
                        addToConverterCache(pair, typeConverter);
                        return typeConverter;
                    }
                }
            }
        }
        return typeConverter;
    }

    private SimpleDateFormat resolveFormat(ConversionContext context) {
        AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        Optional<String> format = annotationMetadata.stringValue(Format.class);
        return format
            .map(pattern -> new SimpleDateFormat(pattern, context.getLocale()))
            .orElseGet(() -> new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", context.getLocale()));
    }

    private NumberFormat resolveNumberFormat(ConversionContext context) {
        AnnotationMetadata annotationMetadata = context.getAnnotationMetadata();
        Optional<String> format = annotationMetadata.stringValue(Format.class);

        return format.map(DecimalFormat::new).orElse(null);
    }

    private <S, T> ConvertiblePair newPair(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair;
        if (typeConverter instanceof FormattingTypeConverter<S, T, ?> formattingTypeConverter) {
            pair = new ConvertiblePair(sourceType, targetType, formattingTypeConverter.annotationType().getName());
        } else {
            pair = new ConvertiblePair(sourceType, targetType);
        }
        return pair;
    }

    /**
     * Binds the source and target.
     */
    private static final class ConvertiblePair {
        final Class<?> source;
        final Class<?> target;
        final String formattingAnnotation;
        final int hashCode;

        ConvertiblePair(Class<?> source, Class<?> target) {
            this(source, target, null);
        }

        ConvertiblePair(Class<?> source, Class<?> target, String formattingAnnotation) {
            this.source = source;
            this.target = target;
            this.formattingAnnotation = formattingAnnotation;
            this.hashCode = ObjectUtils.hash(source, target, formattingAnnotation);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConvertiblePair pair = (ConvertiblePair) o;
            if (!source.equals(pair.source)) {
                return false;
            }
            if (!target.equals(pair.target)) {
                return false;
            }
            return Objects.equals(formattingAnnotation, pair.formattingAnnotation);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
