package org.particleframework.core.convert;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.particleframework.core.reflect.ReflectionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    public <T> Optional<T> convert(Object object, Class<T> targetType) {
        return convert(object, targetType, Collections.emptyMap());
    }

    @Override
    public <T> Optional<T> convert(Object object, Class<T> targetType, Map<String, Class> typeArguments) {
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
        if (typeConverter != null) {
            return typeConverter.convert(object, targetType);
        } else {
            typeConverter = findTypeConverter(sourceType, targetType);
            if (typeConverter != null) {
                return typeConverter.convert(object, targetType, typeArguments);
            }
        }
        return Optional.empty();
    }

    @Override
    public <S, T> DefaultConversionService addConverter(Class<S> sourceType, Class<T> targetType, TypeConverter<S, T> typeConverter) {
        ConvertiblePair pair = new ConvertiblePair(sourceType, targetType);
        typeConverters.put(pair, typeConverter);
        converterCache.put(pair, typeConverter);
        return this;
    }

    protected void registerDefaultConverters() {
        // String -> Integer
        addConverter(CharSequence.class, Integer.class, (CharSequence object, Class<Integer> targetType, Map<String, Class> typeArguments) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Number
        addConverter(CharSequence.class, Number.class, (CharSequence object, Class<Number> targetType, Map<String, Class> typeArguments) -> {
            try {
                Integer converted = Integer.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Long
        addConverter(CharSequence.class, Long.class, (CharSequence object, Class<Long> targetType, Map<String, Class> typeArguments) -> {
            try {
                Long converted = Long.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Short
        addConverter(CharSequence.class, Short.class, (CharSequence object, Class<Short> targetType, Map<String, Class> typeArguments) -> {
            try {
                Short converted = Short.valueOf(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> BigDecimal
        addConverter(CharSequence.class, BigDecimal.class, (CharSequence object, Class<BigDecimal> targetType, Map<String, Class> typeArguments) -> {
            try {
                BigDecimal converted = new BigDecimal(object.toString());
                return Optional.of(converted);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });

        // String -> Boolean
        addConverter(CharSequence.class, Boolean.class, (CharSequence object, Class<Boolean> targetType, Map<String, Class> typeArguments) -> {
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
        addConverter(CharSequence.class, URL.class, (CharSequence object, Class<URL> targetType, Map<String, Class> typeArguments) -> {
            try {
                return Optional.of(new URL(object.toString()));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        });

        // String -> URI
        addConverter(CharSequence.class, URI.class, (CharSequence object, Class<URI> targetType, Map<String, Class> typeArguments) -> {
            try {
                return Optional.of(new URI(object.toString()));
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        });

        // String -> UUID
        addConverter(CharSequence.class, UUID.class, (CharSequence object, Class<UUID> targetType, Map<String, Class> typeArguments) -> {
            try {
                return Optional.of(UUID.fromString(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });

        // String -> Currency
        addConverter(CharSequence.class, Currency.class, (CharSequence object, Class<Currency> targetType, Map<String, Class> typeArguments) -> {
            try {
                return Optional.of(Currency.getInstance(object.toString()));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        });


        // String -> TimeZone
        addConverter(CharSequence.class, TimeZone.class, (CharSequence object, Class<TimeZone> targetType, Map<String, Class> typeArguments) -> Optional.of(TimeZone.getTimeZone(object.toString())));

        // String -> Charset
        addConverter(CharSequence.class, Charset.class, (CharSequence object, Class<Charset> targetType, Map<String, Class> typeArguments) -> {
            try {
                return Optional.of(Charset.forName(object.toString()));
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                return Optional.empty();
            }
        });

        // String -> Character
        addConverter(CharSequence.class, Character.class, (CharSequence object, Class<Character> targetType, Map<String, Class> typeArguments) -> {
            String str = object.toString();
            if (str.length() == 1) {
                return Optional.of(str.charAt(0));
            } else {
                return Optional.empty();
            }
        });

        // String -> Array
        addConverter(CharSequence.class, Object[].class, (CharSequence object, Class<Object[]> targetType, Map<String, Class> typeArguments) -> {
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
        addConverter(CharSequence.class, Enum.class, (CharSequence object, Class<Enum> targetType, Map<String, Class> typeArguments) -> {
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
        addConverter(Object.class, String.class, (Object object, Class<String> targetType, Map<String, Class> typeArguments) -> Optional.of(object.toString()));

        // Number -> Number
        addConverter(Number.class, Number.class, (Number object, Class<Number> targetType, Map<String, Class> typeArguments) -> {
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
        addConverter(CharSequence.class, Iterable.class, (CharSequence object, Class<Iterable> targetType, Map<String, Class> typeArguments) -> {
            TypeVariable<Class<Iterable>> typeVariable = targetType.getTypeParameters()[0];
            String name = typeVariable.getName();
            Class targetComponentType = typeArguments.get(name);
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

        addConverter(Object.class, Optional.class, (object, targetType, typeArguments) -> {
            TypeVariable<Class<Optional>> typeVariable = targetType.getTypeParameters()[0];
            String name = typeVariable.getName();
            Class targetComponentType = typeArguments.get(name);
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
        addConverter(Iterable.class, Iterable.class, (object, targetType, typeArguments) -> {
            TypeVariable<Class<Iterable>> typeVariable = targetType.getTypeParameters()[0];
            String name = typeVariable.getName();
            Class targetComponentType = typeArguments.get(name);
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
                    try {
                        return Optional.of(coerceToType(list, targetType));
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }
            }
            else {
                targetComponentType = Object.class;
                List list = new ArrayList();
                for (Object o : object) {
                    list.add(convert(o, targetComponentType));
                }
                try {
                    return Optional.of(coerceToType(list, targetType));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        });

        // Map -> Map (inner type conversion)
        addConverter(Map.class, Map.class, (object, targetType, typeArguments) -> {
            TypeVariable<Class<Map>>[] typeParameters = targetType.getTypeParameters();
            Class keyType = String.class;
            Class valueType = Object.class;
            boolean isProperties = false;
            if(targetType.equals(Properties.class)) {
                valueType = String.class;
                isProperties = true;
            }
            else if(typeParameters.length == 2) {

                keyType = typeArguments.get(typeParameters[0].getName());
                if(keyType == null) {
                    keyType = String.class;
                }
                valueType = typeArguments.get(typeParameters[1].getName());
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

    private Iterable coerceToType(Collection beans, Class<? extends Iterable> componentType) throws Exception {
        if (componentType.isInstance(beans)) {
            return beans;
        }
        if (componentType == Set.class) {
            return new HashSet<>(beans);
        } else if (componentType == Queue.class) {
            return new LinkedList<>(beans);
        } else if (componentType == List.class) {
            return new ArrayList<>(beans);
        } else if (!componentType.isInterface()) {
            Constructor<? extends Iterable> constructor = componentType.getConstructor(Collection.class);
            return constructor.newInstance(beans);
        } else {
            return null;
        }
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
