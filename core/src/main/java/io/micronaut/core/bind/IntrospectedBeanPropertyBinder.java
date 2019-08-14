package io.micronaut.core.bind;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.core.type.Argument;

import java.util.*;

public class IntrospectedBeanPropertyBinder implements BeanPropertyBinder {


    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        T2 instance;
        BeanIntrospection<T2> introspection = getIntrospection(type);
        Argument[] constructorArguments = introspection.getConstructorArguments();
        List<Object> arguments = new ArrayList<>(constructorArguments.length);

        if (constructorArguments.length > 0) {
            Map<String, Object> bindMap = new LinkedHashMap<>();
            for (Map.Entry<? extends CharSequence, Object> entry : source) {
                String key = NameUtils.camelCase(entry.getKey().toString());
                int firstPartIndex = key.indexOf('.');
                if (firstPartIndex > -1) {
                    String[] parts = key.split("\\.");
                    String part = parts[0];
                    int arrIndex = part.indexOf('[');
                    if (arrIndex > -1) {
                        int endIndex = part.indexOf(']');
                        String name = part.substring(0, arrIndex);
                        int index = Integer.valueOf(part.substring(arrIndex + 1, endIndex));
                        bindMap.compute(part, (k, value) -> {
                            if (value == null) {
                                value = new ArrayList<Map<String, Object>>();
                            }
                            fill((List) value, index, null);
                            Object indexValue = ((List) value).get(index);
                            if (indexValue == null) {
                                indexValue = new LinkedHashMap<>();
                                ((List) value).add(index, indexValue);
                            }
                            ((Map) indexValue).put(name, entry.getValue());
                            return value;
                        });
                    } else {
                        bindMap.compute(part, (k, value) -> {
                            if (value == null) {
                                value = new LinkedHashMap<String, Object>();
                            }
                            ((Map) value).put(key.substring(firstPartIndex + 1), entry.getValue());
                            return value;
                        });
                    }
                } else {
                    bindMap.put(key, entry.getValue());
                }
            }

            for (Argument<?> argument : constructorArguments) {
                Object value = bindMap.get(argument.getName());
                if (value != null) {
                    if (ClassUtils.isJavaBasicType(argument.getType())) {
                        Object converted = ConversionService.SHARED.convert(bindMap.get(argument.getName()), argument.getType(), ConversionContext.of(argument)).orElseThrow(() ->
                                new ConversionErrorException(argument, new IllegalArgumentException("Value [" + bindMap.get(argument.getName()) + "] cannot be converted to type : " + argument.getType())));
                        arguments.add(converted);
                    } else {
                        if (value instanceof List) {
                            List list = (List) value;
                            for (int i = 0; i < list.size(); i++) {
                                list.set(i, bind(argument.getFirstTypeVariable(), ((Map) list.get(i)).entrySet()));
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
                        } else {
                            value = bind(argument.getType(), ((Map) value).entrySet());
                        }
                        arguments.add(value);
                    }
                } else if (argument.isDeclaredNullable()) {
                    arguments.add(null);
                } else {
                    throw new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName()));
                }
            }

            instance = introspection.instantiate(arguments.toArray());
        } else {
            instance = introspection.instantiate();
        }

        return bind(instance, source);
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        return null;
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        return null;
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
