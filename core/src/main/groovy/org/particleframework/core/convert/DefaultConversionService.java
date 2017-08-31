package org.particleframework.core.convert;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.core.naming.NameUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.util.CollectionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.TypeVariable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The default conversion service. Handles basic type conversion operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultConversionService implements ConversionService<DefaultConversionService> {
    private final Map<ConvertiblePair, TypeConverter> typeConverters = new ConcurrentHashMap<>();
    private final Cache<ConvertiblePair, TypeConverter> converterCache = Caffeine.newBuilder()
            .maximumSize(60)
            .softValues()
            .build();

    public DefaultConversionService() {
        registerDefaultConverters();
    }


    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, ConversionContext context) {
        if (object == null) {
            return Optional.empty();
        }
        if(targetType.isInstance(object) && !Iterable.class.isInstance(object) && !Map.class.isInstance(object)) {
            return Optional.of((T) object);
        }

        Class<?> sourceType = ReflectionUtils.getWrapperType(object.getClass());
        targetType = ReflectionUtils.getWrapperType(targetType);
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        TypeConverter typeConverter = converterCache.getIfPresent(pair);
        if (typeConverter == null) {
            typeConverter = findTypeConverter(sourceType, targetType);
            if (typeConverter == null) {
                return Optional.empty();
            }
        }
        return typeConverter.convert(object, targetType, context);
    }

    @Override
    public <S, T> DefaultConversionService addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        typeConverters.put(pair, typeConverter);
        converterCache.put(pair, typeConverter);
        return this;
    }

    @Override
    public <S, T> DefaultConversionService addConverter(Class<S> sourceType, Class<T> targetType, Function<S, T> function) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        TypeConverter<S,T> typeConverter = TypeConverter.of(sourceType, targetType, function);
        typeConverters.put(pair, typeConverter);
        converterCache.put(pair, typeConverter);
        return this;
    }

    protected void registerDefaultConverters() {

        // CharSequence -> ZonedDateTime
        addConverter(
                CharSequence.class,
                ZonedDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = context.getFormat()
                                .map((pattern)-> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
                                .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
                        ZonedDateTime result = ZonedDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> LocalDataTime
        addConverter(
                CharSequence.class,
                LocalDateTime.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = context.getFormat()
                                .map((pattern)-> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
                                .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
                        LocalDateTime result = LocalDateTime.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // CharSequence -> LocalDate
        addConverter(
                CharSequence.class,
                LocalDate.class,
                (object, targetType, context) -> {
                    try {
                        DateTimeFormatter formatter = context.getFormat()
                                .map((pattern)-> DateTimeFormatter.ofPattern(pattern, context.getLocale()))
                                .orElse(DateTimeFormatter.RFC_1123_DATE_TIME);
                        LocalDate result = LocalDate.parse(object, formatter);
                        return Optional.of(result);
                    } catch (DateTimeParseException e) {
                        return Optional.empty();
                    }
                }
        );

        // String -> Integer
        addConverter(CharSequence.class, Integer.class, (CharSequence object, Class<Integer> targetType, ConversionContext context) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Number
        addConverter(CharSequence.class, Number.class, (CharSequence object, Class<Number> targetType, ConversionContext context) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Long
        addConverter(CharSequence.class, Long.class, (CharSequence object, Class<Long> targetType, ConversionContext context) -> {
            try {
                Long converted = Long.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Short
        addConverter(CharSequence.class, Short.class, (CharSequence object, Class<Short> targetType, ConversionContext context) -> {
            try {
                Short converted = Short.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> BigDecimal
        addConverter(CharSequence.class, BigDecimal.class, (CharSequence object, Class<BigDecimal> targetType, ConversionContext context) -> {
            try {
                BigDecimal converted = new BigDecimal(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Boolean
        addConverter(CharSequence.class, Boolean.class, (CharSequence object, Class<Boolean> targetType, ConversionContext context) -> {
            String booleanString = object.toString().toLowerCase(Locale.ENGLISH);
            switch (booleanString) {
                case "yes":
                case "y":
                case "on":
                case "true":
                    return Optional.of(Boolean.TRUE);
                default:
                    return Optional.of(Boolean.FALSE);
            }
        });

        // String -> URL
        addConverter(CharSequence.class, URL.class, (CharSequence object, Class<URL> targetType, ConversionContext context) -> {
            try {
                return Optional.of(new URL(object.toString()));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        });

        // String -> URI
        addConverter(CharSequence.class, URI.class, (CharSequence object, Class<URI> targetType, ConversionContext context) -> {
            try {
                return Optional.of(new URI(object.toString()));
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        });

        // String -> UUID
        addConverter(CharSequence.class, UUID.class, (CharSequence object, Class<UUID> targetType, ConversionContext context) -> {
            try {
                return Optional.of(UUID.fromString(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });

        // String -> Currency
        addConverter(CharSequence.class, Currency.class, (CharSequence object, Class<Currency> targetType, ConversionContext context) -> {
            try {
                return Optional.of(Currency.getInstance(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });


        // String -> TimeZone
        addConverter(CharSequence.class, TimeZone.class, (CharSequence object, Class<TimeZone> targetType, ConversionContext context) -> Optional.of(TimeZone.getTimeZone(object.toString())));

        // String -> Charset
        addConverter(CharSequence.class, Charset.class, (CharSequence object, Class<Charset> targetType, ConversionContext context) -> {
            try {
                return Optional.of(Charset.forName(object.toString()));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
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

        // String -> Enum
        addConverter(CharSequence.class, Enum.class, (CharSequence object, Class<Enum> targetType, ConversionContext context) -> {
            String stringValue = object.toString();
            try {
                Enum val = Enum.valueOf(targetType, stringValue);
                return Optional.of(val);
            } catch (IllegalArgumentException e) {
                try {
                    Enum val = Enum.valueOf(targetType, NameUtils.underscoreSeparate(stringValue).toUpperCase(Locale.ENGLISH));
                    return Optional.of(val);
                } catch (Exception e1) {
                    return Optional.empty();
                }
            }
        });

        // Object -> String
        addConverter(Object.class, String.class, (Object object, Class<String> targetType, ConversionContext context) -> Optional.of(object.toString()));

        // Number -> Number
        addConverter(Number.class, Number.class, (Number object, Class<Number> targetType, ConversionContext context) -> {
            Class targetNumberType = ReflectionUtils.getWrapperType(targetType);
            if (targetNumberType.isInstance(object)) {
                return Optional.of(object);
            } else if (targetNumberType == Integer.class) {
                return Optional.of(object.intValue());
            } else if (targetNumberType == Long.class) {
                return Optional.of(object.longValue());
            } else if (targetNumberType == Short.class) {
                return Optional.of(object.shortValue());
            } else if (targetNumberType == Byte.class) {
                return Optional.of(object.byteValue());
            } else if (targetNumberType == Float.class) {
                return Optional.of(object.floatValue());
            } else if (targetNumberType == Double.class) {
                return Optional.of(object.doubleValue());
            } else if (targetNumberType == BigInteger.class) {
                if (object instanceof BigDecimal) {
                    return Optional.of(((BigDecimal) object).toBigInteger());
                } else {
                    return Optional.of(BigInteger.valueOf(object.longValue()));
                }
            } else if (targetNumberType == BigDecimal.class) {
                return Optional.of(new BigDecimal(object.toString()));
            }
            return Optional.empty();
        });

        // String -> List/Iterable
        addConverter(CharSequence.class, Iterable.class, (CharSequence object, Class<Iterable> targetType, ConversionContext context) -> {
            TypeVariable<Class<Iterable>> typeVariable = targetType.getTypeParameters()[0];
            String name = typeVariable.getName();
            Class targetComponentType = context.getTypeVariables().get(name);
            if(targetComponentType == null) {
                targetComponentType = Object.class;
            }
            targetComponentType = ReflectionUtils.getWrapperType(targetComponentType);
            String[] strings = object.toString().split(",");
            List list = new ArrayList();
            for (String string : strings) {
                Optional converted = convert(string, targetComponentType);
                if(converted.isPresent()) {
                    list.add(converted.get());
                }
            }
            return Optional.of(list);
        });

        addConverter(Object.class, Optional.class, (object, targetType, context) -> {
            TypeVariable<Class<Optional>> typeVariable = targetType.getTypeParameters()[0];
            String name = typeVariable.getName();
            Class targetComponentType = context.getTypeVariables().get(name);
            if(targetComponentType == null) targetComponentType = Object.class;
            targetComponentType = ReflectionUtils.getWrapperType(targetComponentType);
            Optional converted = convert(object, targetComponentType);
            if(converted.isPresent()) {
                return Optional.of(converted);
            }
            else {
                return Optional.of(Optional.empty());
            }
        });

        // Iterable -> Iterable (inner type conversion)
        addConverter(Iterable.class, Iterable.class, (object, targetType, context) -> {
            Class targetComponentType = resolveComponentTypeForIterable(targetType, context);
            if(targetType.isInstance(object)) {
                if(targetComponentType == null) {
                    return Optional.of(object);
                }
                else {
                    List list = new ArrayList();
                    for (Object o : object) {
                        Optional converted = convert(o, targetComponentType);
                        if(converted.isPresent()) {
                            list.add(converted.get());
                        }
                    }
                    return CollectionUtils.convertCollection((Class)targetType, list);
                }
            }
            else {
                targetComponentType = Object.class;
                List list = new ArrayList();
                for (Object o : object) {
                    list.add(convert(o, targetComponentType));
                }
                return CollectionUtils.convertCollection((Class)targetType, list);
            }
        });

        // Map -> Map (inner type conversion)
        addConverter(Map.class, Map.class, (object, targetType, context) -> {
            TypeVariable<Class<Map>>[] typeParameters = targetType.getTypeParameters();
            Class keyType = String.class;
            Class valueType = Object.class;
            boolean isProperties = false;
            if(targetType.equals(Properties.class)) {
                valueType = String.class;
                isProperties = true;
            }
            else if(typeParameters.length == 2) {

                keyType = context.getTypeVariables().get(typeParameters[0].getName());
                if(keyType == null) {
                    keyType = String.class;
                }
                valueType = context.getTypeVariables().get(typeParameters[1].getName());
                if(valueType == null) {
                    valueType = Object.class;
                }
            }
            Map newMap = isProperties ? new Properties() :  new LinkedHashMap();
            for (Object o : object.entrySet()) {
                Map.Entry entry = (Map.Entry) o;
                Object key = entry.getKey();
                Object value = entry.getValue();
                if(!keyType.isInstance(key)) {
                    Optional convertedKey = convert(key, keyType);
                    if(convertedKey.isPresent()) {
                        key = convertedKey.get();
                    }
                    else {
                        continue;
                    }
                }
                if(!valueType.isInstance(value)) {
                    Optional converted = convert(value, valueType);
                    if(converted.isPresent()) {
                        value = converted.get();
                    }
                    else {
                        continue;
                    }
                }
                newMap.put(key, value);
            }
            return Optional.of(newMap);
        });

    }

    private Class resolveComponentTypeForIterable(Class<Iterable> targetType, ConversionContext context) {
        TypeVariable<Class<Iterable>>[] typeParameters = targetType.getTypeParameters();
        if(typeParameters != null && typeParameters.length > 0) {

            TypeVariable<Class<Iterable>> typeVariable = typeParameters[0];
            String name = typeVariable != null ? typeVariable.getName() : null;
            return name != null ? context.getTypeVariables().get(name) : null;
        }
        return null;
    }

    protected <T> TypeConverter findTypeConverter(Class<?> sourceType, Class<T> targetType) {
        TypeConverter typeConverter = null;
        List<Class> sourceHierarchy = resolveHierarchy(sourceType);
        List<Class> targetHierarchy = resolveHierarchy(targetType);
        for (Class sourceSuperType : sourceHierarchy) {
            for (Class targetSuperType : targetHierarchy) {
                ConvertiblePair pair = new ConvertiblePair(sourceSuperType, targetSuperType);
                typeConverter = typeConverters.get(pair);
                if (typeConverter != null) {
                    converterCache.put(pair, typeConverter);
                    return typeConverter;
                }
            }
        }
        return typeConverter;
    }

    private void populateHierarchyInterfaces(Class<?> superclass, List<Class> hierarchy) {
        if (!hierarchy.contains(superclass)) {
            hierarchy.add(superclass);
        }
        for (Class<?> aClass : superclass.getInterfaces()) {
            if (!hierarchy.contains(aClass)) {
                hierarchy.add(aClass);
            }
            populateHierarchyInterfaces(aClass, hierarchy);
        }
    }

    private List<Class> resolveHierarchy(Class<?> type) {
        Class<?> superclass = type.getSuperclass();
        List<Class> hierarchy = new ArrayList<>();
        if (superclass != null) {
            populateHierarchyInterfaces(type, hierarchy);

            while (superclass != Object.class) {
                populateHierarchyInterfaces(superclass, hierarchy);
                superclass = superclass.getSuperclass();
            }
        }
        else if(type.isInterface()) {
            populateHierarchyInterfaces(type, hierarchy);
        }

        if (type.isArray()) {
            hierarchy.add(Object[].class);
        } else {
            hierarchy.add(Object.class);
        }

        return hierarchy;
    }

    private class ConvertiblePair {
        final Class source;
        final Class target;

        ConvertiblePair(Class source, Class target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ConvertiblePair pair = (ConvertiblePair) o;

            if (!source.equals(pair.source)) return false;
            return target.equals(pair.target);
        }

        @Override
        public int hashCode() {
            int result = source.hashCode();
            result = 31 * result + target.hashCode();
            return result;
        }
    }
}
