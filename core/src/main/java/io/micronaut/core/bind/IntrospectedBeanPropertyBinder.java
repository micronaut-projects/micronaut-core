package io.micronaut.core.bind;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;

import java.util.*;

public class IntrospectedBeanPropertyBinder implements BeanPropertyBinder {


    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        T2 instance;
        BeanIntrospection<T2> introspection = getIntrospection(type);
        Argument[] constructorArguments = introspection.getConstructorArguments();
        List<Object> arguments = new ArrayList<>(constructorArguments.length);

        Map<String, Object> bindMap = new LinkedHashMap<>();
        for (Map.Entry<? extends CharSequence, Object> entry : source) {
            String key = NameUtils.camelCase(entry.getKey().toString());
            String part;
            int firstPartIndex = key.indexOf('.');
            if (firstPartIndex > -1) {
                String[] parts = key.split("\\.");
                part = parts[0];
            } else {
                part = key;
            }

            int arrIndex = part.indexOf('[');
            if (arrIndex > -1) {
                int endIndex = part.indexOf(']');
                String name = part.substring(0, arrIndex);
                String indexString = part.substring(arrIndex + 1, endIndex);
                if (StringUtils.isDigits(indexString)) {
                    int index = Integer.valueOf(indexString);
                    bindMap.compute(name, (k, value) -> {
                        if (value == null) {
                            value = new ArrayList<>();
                        }
                        fill((List) value, index, null);

                        if (firstPartIndex == -1) {
                            ((List) value).set(index, entry.getValue());
                        } else {
                            Object indexValue = ((List) value).get(index);
                            if (indexValue == null) {
                                indexValue = new LinkedHashMap<>();
                                ((List) value).set(index, indexValue);
                            }
                            ((Map) indexValue).put(key.substring(firstPartIndex + 1), entry.getValue());
                        }
                        return value;
                    });
                } else {
                    bindMap.compute(name, (k, value) -> {
                        if (value == null) {
                            value = new LinkedHashMap<>();
                        }
                        if (firstPartIndex == -1) {
                            ((Map) value).put(indexString, entry.getValue());
                        } else {
                            Map obj = (Map) ((Map) value).get(indexString);
                            if (obj == null) {
                                obj = new LinkedHashMap();
                                ((Map) value).put(indexString, obj);
                            }
                            obj.put(key.substring(firstPartIndex + 1), entry.getValue());
                        }
                        return value;
                    });
                }

            } else {
                bindMap.compute(part, (k, value) -> {
                    if (firstPartIndex == -1) {
                        return entry.getValue();
                    } else {
                        if (value == null) {
                            value = new LinkedHashMap<String, Object>();
                        }
                        ((Map) value).put(key.substring(firstPartIndex + 1), entry.getValue());
                        return value;
                    }
                });
            }
        }

        for (Argument<?> argument : constructorArguments) {
            Object value = bindMap.remove(argument.getName());
            if (value != null) {
                if (ClassUtils.isJavaBasicType(argument.getType())) {
                    Optional<?> converted = ConversionService.SHARED.convert(value, argument.getType(), ConversionContext.of(argument));
                    if (!converted.isPresent()) {
                        throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + bindMap.get(argument.getName()) + "] cannot be converted to type : " + argument.getType()));
                    }
                    arguments.add(converted.get());
                } else {
                    if (value instanceof List) {
                        List list = (List) value;
                        boolean isArray = argument.getType().isArray();
                        Optional<Argument<?>> typeVariable;
                        if (isArray) {
                            typeVariable = Optional.of(Argument.of(argument.getType().getComponentType()));
                        } else {
                            typeVariable = argument.getFirstTypeVariable();
                        }
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i) == null) {
                                continue;
                            }
                            if (typeVariable.isPresent()) {
                                Class<?> argType = typeVariable.get().getType();
                                if (ClassUtils.isJavaBasicType(argType)) {
                                    Optional<?> converted = ConversionService.SHARED.convert(list.get(i), argType, ConversionContext.of(argument));
                                    if (!converted.isPresent()) {
                                        throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType()));
                                    }
                                    list.set(i, converted.get());
                                } else {
                                    list.set(i, bind(typeVariable.get().getType(), ((Map) list.get(i)).entrySet()));
                                }
                            }
                        }
                        if (!Iterable.class.isAssignableFrom(argument.getType()) && !isArray) {
                            if (list.size() == 1) {
                                value = list.get(0);
                            } else if (list.size() > 1) {
                                throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType()));
                            } else {
                                if (argument.isDeclaredNullable()) {
                                    value = null;
                                } else {
                                    throw new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName()));
                                }
                            }
                        }
                    } else if (Map.class.isAssignableFrom(argument.getType())) {
                        Optional<Argument<?>> typeVariable = argument.getTypeVariable("V");
                        for(Map.Entry<String, Object> entry: ((Map<String, Object>) value).entrySet()) {
                            if (typeVariable.isPresent()) {
                                Class<?> argType = typeVariable.get().getType();
                                if (ClassUtils.isJavaBasicType(argType)) {
                                    Object converted = ConversionService.SHARED.convert(entry.getValue(), argType, ConversionContext.of(argument)).orElseThrow(() ->
                                            new ConversionErrorException(argument, new IllegalArgumentException("Value [" + bindMap.get(argument.getName()) + "] cannot be converted to type : " + argument.getType())));
                                    ((Map) value).put(entry.getKey(), converted);
                                } else {
                                    ((Map) value).put(entry.getKey(), bind(typeVariable.get().getType(), ((Map) entry.getValue()).entrySet()));
                                }
                            }
                        }
                    } else {
                        value = bind(argument.getType(), ((Map) value).entrySet());
                    }
                    Optional<?> converted = ConversionService.SHARED.convert(value, argument);
                    if (!converted.isPresent()) {
                        throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType()));
                    }

                    arguments.add(converted.get());
                }
            } else if (argument.isDeclaredNullable()) {
                arguments.add(null);
            } else {
                throw new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName()));
            }
        }

        if (constructorArguments.length > 0) {
            instance = introspection.instantiate(arguments.toArray());
        } else {
            instance = introspection.instantiate();
        }

        return bind(instance, ConversionContext.of(type), bindMap.entrySet());
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        BeanIntrospection<T2> introspection = getIntrospection((Class<T2>) object.getClass());
        Collection<BeanProperty<T2, Object>> beanProperties = introspection.getBeanProperties();

        Map<String, Object> bindMap = new LinkedHashMap<>();
        for (Map.Entry<? extends CharSequence, Object> entry : source) {
            String key = NameUtils.camelCase(entry.getKey().toString());
            String part;
            int firstPartIndex = key.indexOf('.');
            if (firstPartIndex > -1) {
                String[] parts = key.split("\\.");
                part = parts[0];
            } else {
                part = key;
            }

            int arrIndex = part.indexOf('[');
            if (arrIndex > -1) {
                int endIndex = part.indexOf(']');
                String name = part.substring(0, arrIndex);
                String indexString = part.substring(arrIndex + 1, endIndex);
                if (StringUtils.isDigits(indexString)) {
                    int index = Integer.valueOf(indexString);
                    bindMap.compute(name, (k, value) -> {
                        if (value == null) {
                            value = new ArrayList<>();
                        }
                        fill((List) value, index, null);

                        if (firstPartIndex == -1) {
                            ((List) value).set(index, entry.getValue());
                        } else {
                            Object indexValue = ((List) value).get(index);
                            if (indexValue == null) {
                                indexValue = new LinkedHashMap<>();
                                ((List) value).set(index, indexValue);
                            }
                            ((Map) indexValue).put(key.substring(firstPartIndex + 1), entry.getValue());
                        }
                        return value;
                    });
                } else {
                    bindMap.compute(name, (k, value) -> {
                        if (value == null) {
                            value = new LinkedHashMap<>();
                        }
                        if (firstPartIndex == -1) {
                            ((Map) value).put(indexString, entry.getValue());
                        } else {
                            Map obj = (Map) ((Map) value).get(indexString);
                            if (obj == null) {
                                obj = new LinkedHashMap();
                                ((Map) value).put(indexString, obj);
                            }
                            obj.put(key.substring(firstPartIndex + 1), entry.getValue());
                        }
                        return value;
                    });
                }

            } else {
                bindMap.compute(part, (k, value) -> {
                    if (firstPartIndex == -1) {
                        return entry.getValue();
                    } else {
                        if (value == null) {
                            value = new LinkedHashMap<String, Object>();
                        }
                        ((Map) value).put(key.substring(firstPartIndex + 1), entry.getValue());
                        return value;
                    }
                });
            }
        }

        for (BeanProperty<T2, Object> property : beanProperties) {
            if (property.isReadOnly()) {
                continue;
            }
            Object value = bindMap.remove(property.getName());
            Argument<?> argument = property.asArgument();

            if (value != null) {
                if (ClassUtils.isJavaBasicType(property.getType())) {
                    Optional<?> converted = ConversionService.SHARED.convert(value, property.getType(), ConversionContext.of(property.asArgument()));
                    if (!converted.isPresent()) {
                        throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + bindMap.get(argument.getName()) + "] cannot be converted to type : " + argument.getType()));
                    }
                    property.set(object, converted.get());
                } else {
                    if (value instanceof List) {
                        List list = (List) value;
                        Optional<Argument<?>> typeVariable = argument.getFirstTypeVariable();
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i) == null) {
                                continue;
                            }
                            if (typeVariable.isPresent()) {
                                Class<?> argType = typeVariable.get().getType();
                                if (ClassUtils.isJavaBasicType(argType)) {
                                    Optional<?> converted = ConversionService.SHARED.convert(list.get(i), argType, ConversionContext.of(argument));
                                    if (!converted.isPresent()) {
                                        throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType()));
                                    }
                                    list.set(i, converted.get());
                                } else {
                                    list.set(i, bind(typeVariable.get().getType(), ((Map) list.get(i)).entrySet()));
                                }
                            }
                        }
                        if (!Iterable.class.isAssignableFrom(argument.getType())) {
                            if (list.size() == 1) {
                                value = list.get(0);
                            } else if (list.size() > 1) {
                                throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType()));
                            } else {
                                if (argument.isDeclaredNullable()) {
                                    value = null;
                                } else {
                                    throw new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName()));
                                }
                            }
                        }
                    } else if (Map.class.isAssignableFrom(argument.getType())) {
                        Optional<Argument<?>> typeVariable = argument.getTypeVariable("V");
                        for(Map.Entry<String, Object> entry: ((Map<String, Object>) value).entrySet()) {
                            if (typeVariable.isPresent()) {
                                Class<?> argType = typeVariable.get().getType();
                                if (ClassUtils.isJavaBasicType(argType)) {
                                    Object converted = ConversionService.SHARED.convert(entry.getValue(), argType, ConversionContext.of(argument)).orElseThrow(() ->
                                            new ConversionErrorException(argument, new IllegalArgumentException("Value [" + bindMap.get(argument.getName()) + "] cannot be converted to type : " + argument.getType())));
                                    ((Map) value).put(entry.getKey(), converted);
                                } else {
                                    ((Map) value).put(entry.getKey(), bind(typeVariable.get().getType(), ((Map) entry.getValue()).entrySet()));
                                }
                            }
                        }
                    } else {
                        value = bind(argument.getType(), ((Map) value).entrySet());
                    }
                    Optional<?> converted = ConversionService.SHARED.convert(value, argument);
                    if (!converted.isPresent()) {
                        throw new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType()));
                    }

                    property.set(object, converted.get());
                }
            }
        }

        return object;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        return bind(object, ConversionContext.of((Class<T2>) object.getClass()), source);
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            Object result = bind(context.getArgument().getType(), source.entrySet());
            return () -> Optional.of(result);
        } catch (Exception e) {
            context.reject(e);
            return BindingResult.EMPTY;
        }
    }

    private <T2> BeanIntrospection<T2> getIntrospection(Class<T2> type) {
        return BeanIntrospection.getIntrospection(type);
    }

    private void fill(List list, Integer toIndex, Object value) {
        if (toIndex >= list.size()) {
            for (int i = list.size(); i <= toIndex; i++) {
                list.add(i, value);
            }
        }
    }
}
