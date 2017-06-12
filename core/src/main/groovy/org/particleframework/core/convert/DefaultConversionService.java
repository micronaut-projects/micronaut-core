package org.particleframework.core.convert;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.core.reflect.ReflectionUtils;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default conversion service. Handles basic type conversion operations.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultConversionService implements ConversionService {
    private final Map<ConvertiblePair, TypeConverter> typeConverters = new ConcurrentHashMap<>();
    private final Cache<ConvertiblePair, TypeConverter> converterCache = Caffeine.newBuilder()
            .maximumSize(60)
            .softValues()
            .build();

    public DefaultConversionService() {
        registerDefaultConverters();
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType) {
        if (object == null) {
            return Optional.empty();
        }
        if(targetType.isInstance(object)) {
            return Optional.of((T) object);
        }

        Class<?> sourceType = ReflectionUtils.getWrapperType(object.getClass());
        targetType = ReflectionUtils.getWrapperType(targetType);
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        TypeConverter typeConverter = converterCache.getIfPresent(pair);
        if (typeConverter != null) {
            return typeConverter.convert(targetType, object);
        } else {
            typeConverter = findTypeConverter(sourceType, targetType);
            if (typeConverter != null) {
                return typeConverter.convert(targetType, object);
            }
        }
        return Optional.empty();
    }

    @Override
    public <S, T> ConversionService addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        typeConverters.put(pair, typeConverter);
        converterCache.put(pair, typeConverter);
        return this;
    }

    protected void registerDefaultConverters() {
        // String -> Integer
        addConverter(CharSequence.class, Integer.class, (Class<Integer> targetType, CharSequence object) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Number
        addConverter(CharSequence.class, Number.class, (Class<Number> targetType, CharSequence object) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Long
        addConverter(CharSequence.class, Long.class, (Class<Long> targetType, CharSequence object) -> {
            try {
                Long converted = Long.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Short
        addConverter(CharSequence.class, Short.class, (Class<Short> targetType, CharSequence object) -> {
            try {
                Short converted = Short.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> BigDecimal
        addConverter(CharSequence.class, BigDecimal.class, (Class<BigDecimal> targetType, CharSequence object) -> {
            try {
                BigDecimal converted = new BigDecimal(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Boolean
        addConverter(CharSequence.class, Boolean.class, (Class<Boolean> targetType, CharSequence object) -> {
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
        addConverter(CharSequence.class, URL.class, (Class<URL> targetType, CharSequence object) -> {
            try {
                return Optional.of(new URL(object.toString()));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        });

        // String -> URI
        addConverter(CharSequence.class, URI.class, (Class<URI> targetType, CharSequence object) -> {
            try {
                return Optional.of(new URI(object.toString()));
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        });

        // String -> UUID
        addConverter(CharSequence.class, UUID.class, (Class<UUID> targetType, CharSequence object) -> {
            try {
                return Optional.of(UUID.fromString(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });

        // String -> Currency
        addConverter(CharSequence.class, Currency.class, (Class<Currency> targetType, CharSequence object) -> {
            try {
                return Optional.of(Currency.getInstance(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });


        // String -> TimeZone
        addConverter(CharSequence.class, TimeZone.class, (Class<TimeZone> targetType, CharSequence object) -> Optional.of(TimeZone.getTimeZone(object.toString())));

        // String -> Charset
        addConverter(CharSequence.class, Charset.class, (Class<Charset> targetType, CharSequence object) -> {
            try {
                return Optional.of(Charset.forName(object.toString()));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                return Optional.empty();
            }
        });

        // String -> Character
        addConverter(CharSequence.class, Character.class, (Class<Character> targetType, CharSequence object) -> {
            String str = object.toString();
            if (str.length() == 1) {
                return Optional.of(str.charAt(0));
            } else {
                return Optional.empty();
            }
        });

        // String -> Array
        addConverter(CharSequence.class, Object[].class, (Class<Object[]> targetType, CharSequence object) -> {
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
        addConverter(CharSequence.class, Enum.class, (Class<Enum> targetType, CharSequence object) -> {
            try {
                Enum val = Enum.valueOf(targetType, object.toString());
                return Optional.of(val);
            } catch (IllegalArgumentException e) {
                try {
                    Enum val = Enum.valueOf(targetType, object.toString().toUpperCase(Locale.ENGLISH));
                    return Optional.of(val);
                } catch (Exception e1) {
                    return Optional.empty();
                }
            }
        });

        // Object -> String
        addConverter(Object.class, String.class, (Class<String> targetType, Object object) -> Optional.of(object.toString()));

        // Number -> Number
        addConverter(Number.class, Number.class, (Class<Number> targetType, Number object) -> {
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
        hierarchy.add(superclass);
        for (Class<?> aClass : superclass.getInterfaces()) {
            if (!hierarchy.contains(aClass)) {
                hierarchy.add(aClass);
            }
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

            ConvertiblePair that = (ConvertiblePair) o;

            if (!source.equals(that.source)) return false;
            return target.equals(that.target);
        }

        @Override
        public int hashCode() {
            int result = source.hashCode();
            result = 31 * result + target.hashCode();
            return result;
        }
    }
}
