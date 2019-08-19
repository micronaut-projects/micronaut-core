/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.core.bind;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionError;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * A {@link BeanPropertyBinder} that relies on the {@link BeanIntrospection} api.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public class IntrospectedBeanPropertyBinder implements BeanPropertyBinder {

    private static final Logger LOG = LoggerFactory.getLogger(IntrospectedBeanPropertyBinder.class);
    private final ConversionService<?> conversionService;
    private final BeanPropertyBinder fallbackBinder;
    private final BeanIntrospector beanIntrospector;

    /**
     * Default Constructor. Uses the shared conversion service.
     *
     * @param fallbackBinder The binder to use for non introspected beans
     */
    public IntrospectedBeanPropertyBinder(@Nullable BeanPropertyBinder fallbackBinder) {
        this(ConversionService.SHARED, fallbackBinder);
    }

    /**
     * @param conversionService The conversion service
     * @param fallbackBinder The binder to use for non introspected beans
     */
    public IntrospectedBeanPropertyBinder(ConversionService<?> conversionService, @Nullable BeanPropertyBinder fallbackBinder) {
        this.conversionService = conversionService;
        this.fallbackBinder = fallbackBinder;
        this.beanIntrospector = BeanIntrospector.SHARED;
    }

    @Override
    public <T2> T2 bind(Class<T2> type, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        ArgumentConversionContext<T2> typeConversionContext = ConversionContext.of(type);

        T2 instance = bind(type, typeConversionContext, source);

        if (typeConversionContext.getLastError().isPresent()) {
            throw new ConversionErrorException(typeConversionContext.getArgument(), typeConversionContext.getLastError().get());
        }

        return instance;
    }

    @Override
    public <T2> T2 bind(Class<T2> type, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        T2 instance;
        try {
            BeanIntrospection<T2> introspection = beanIntrospector.getIntrospection(type);
            Argument[] constructorArguments = introspection.getConstructorArguments();
            List<Object> arguments = new ArrayList<>(constructorArguments.length);

            Map<String, Object> bindMap = buildBindMap(source);

            for (Argument<?> argument : constructorArguments) {
                Object value = bindMap.remove(argument.getName());
                ArgumentConversionContext<?> conversionContext = context.with(argument);
                if (value != null) {
                    setValue(value, conversionContext, arguments::add);
                } else if (argument.isDeclaredNullable()) {
                    arguments.add(null);
                } else {
                    throw new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName()));
                }
            }

            if (constructorArguments.length > 0) {
                instance = introspection.instantiate(arguments.toArray(new Object[0]));
            } else {
                instance = introspection.instantiate();
            }

            bind(instance, context, bindMap.entrySet());

            return instance;
        } catch (IntrospectionException e) {
            if (fallbackBinder != null) {
                return fallbackBinder.bind(type, source);
            } else {
                throw e;
            }
        }
    }

    @Override
    public <T2> T2 bind(T2 object, ArgumentConversionContext<T2> context, Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
        try {
            BeanIntrospection<T2> introspection = beanIntrospector.getIntrospection((Class<T2>) object.getClass());
            Collection<BeanProperty<T2, Object>> beanProperties = introspection.getBeanProperties();

            Map<String, Object> bindMap = buildBindMap(source);

            for (BeanProperty<T2, Object> property : beanProperties) {
                if (property.isReadOnly()) {
                    continue;
                }
                Object value = bindMap.remove(property.getName());
                Argument<?> argument = property.asArgument();

                setValue(value, context.with(argument), (val) -> property.set(object, val));
            }

            return object;
        } catch (IntrospectionException e) {
            if (fallbackBinder != null) {
                return fallbackBinder.bind(object, context, source);
            } else {
                throw e;
            }
        }
    }

    @Override
    public <T2> T2 bind(T2 object, Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws ConversionErrorException {
        ArgumentConversionContext<T2> context = ConversionContext.of((Class<T2>) object.getClass());
        bind(object, context, source);
        Optional<ConversionError> error = context.getLastError();
        if (error.isPresent()) {
            throw new ConversionErrorException(context.getArgument(), error.get());
        } else {
            return object;
        }
    }

    @Override
    public BindingResult<Object> bind(ArgumentConversionContext<Object> context, Map<CharSequence, ? super Object> source) {
        try {
            Object result = bind((Class<?>) context.getArgument().getType(), context, source.entrySet());
            return () -> Optional.of(result);
        } catch (Exception e) {
            context.reject(e);
            return BindingResult.EMPTY;
        }
    }

    private void fill(List list, Integer toIndex, Object value) {
        if (toIndex >= list.size()) {
            for (int i = list.size(); i <= toIndex; i++) {
                list.add(i, value);
            }
        }
    }

    private Map<String, Object> buildBindMap(Set<? extends Map.Entry<? extends CharSequence, Object>> source) {
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
                        if (value instanceof List) {
                            List<Object> list = (List<Object>) value;
                            fill(list, index, null);

                            if (firstPartIndex == -1) {
                                list.set(index, entry.getValue());
                            } else {
                                Object indexValue = ((List) value).get(index);
                                if (indexValue == null) {
                                    indexValue = new LinkedHashMap<>();
                                    list.set(index, indexValue);
                                }
                                if (indexValue instanceof Map) {
                                    ((Map) indexValue).put(key.substring(firstPartIndex + 1), entry.getValue());
                                } else {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Skipped binding of [{}]. [{}] was expected to be a map but was actually a [{}]", key, part, indexValue.getClass().getName());
                                    }
                                }
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Skipped binding of [{}]. [{}] was expected to be a collection but was actually a [{}]", key, part, value.getClass().getName());
                            }
                        }
                        return value;
                    });
                } else {
                    bindMap.compute(name, (k, value) -> {
                        if (value == null) {
                            value = new LinkedHashMap<String, Object>();
                        }
                        if (value instanceof Map) {
                            Map map = (Map) value;
                            if (firstPartIndex == -1) {
                                map.put(indexString, entry.getValue());
                            } else {
                                Object subObj = map.get(indexString);
                                if (subObj == null) {
                                    subObj = new LinkedHashMap();
                                    map.put(indexString, subObj);
                                }
                                if (subObj instanceof Map) {
                                    ((Map) subObj).put(key.substring(firstPartIndex + 1), entry.getValue());
                                } else {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Skipped binding of [{}]. [{}] was expected to be a map but was actually a [{}]", key, part, value.getClass().getName());
                                    }
                                }
                            }
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Skipped binding of [{}]. [{}] was expected to be a map but was actually a [{}]", key, part, value.getClass().getName());
                            }
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
                        if (value instanceof Map) {
                            ((Map) value).put(key.substring(firstPartIndex + 1), entry.getValue());
                        } else {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Skipped binding of [{}]. [{}] was expected to be a map but was actually a [{}]", key, part, value.getClass().getName());
                            }
                        }
                        return value;
                    }
                });
            }
        }
        return bindMap;
    }

    private ConversionContext setValue(Object value, ArgumentConversionContext<?> conversionContext, Consumer<Object> consumer) {
        if (value != null) {
            Argument<?> argument = conversionContext.getArgument();
            if (ClassUtils.isJavaBasicType(argument.getType())) {
                Optional<?> converted = conversionService.convert(value, argument.getType(), conversionContext);
                if (converted.isPresent()) {
                    consumer.accept(converted.get());
                } else {
                    conversionContext.reject(value, new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType())));
                }
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
                                Optional<?> converted = conversionService.convert(list.get(i), argType, conversionContext);
                                if (converted.isPresent()) {
                                    list.set(i, converted.get());
                                } else {
                                    conversionContext.reject(value, new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType())));
                                }
                            } else {
                                list.set(i, bind(typeVariable.get().getType(), ((Map) list.get(i)).entrySet()));
                            }
                        }
                    }
                    if (!Iterable.class.isAssignableFrom(argument.getType()) && !isArray) {
                        if (list.size() == 1) {
                            value = list.get(0);
                        } else if (list.size() > 1) {
                            conversionContext.reject(list, new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType())));

                        } else {
                            if (argument.isDeclaredNullable()) {
                                value = null;
                            } else {
                                conversionContext.reject(value, new ConversionErrorException(argument, () -> new IllegalArgumentException("No Value found for argument " + argument.getName())));
                            }
                        }
                    }
                } else if (value instanceof Map && Map.class.isAssignableFrom(argument.getType())) {
                    Optional<Argument<?>> typeVariable = argument.getTypeVariable("V");
                    Map<String, Object> map = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> entry: map.entrySet()) {
                        if (typeVariable.isPresent()) {
                            Class<?> argType = typeVariable.get().getType();
                            if (ClassUtils.isJavaBasicType(argType)) {
                                Optional<?> converted = conversionService.convert(entry.getValue(), argType, conversionContext);
                                if (converted.isPresent()) {
                                    map.put(entry.getKey(), converted.get());
                                } else {
                                    conversionContext.reject(entry.getValue(), new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType())));
                                }
                            } else {
                                Object subValue = entry.getValue();
                                if (subValue instanceof Map) {
                                    map.put(entry.getKey(), bind(typeVariable.get().getType(), ((Map) subValue).entrySet()));
                                } else {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Skipped binding of [{}]. [{}] was expected to be a map but was actually a [{}]", argument.getName(), subValue, subValue.getClass().getName());
                                    }
                                }
                            }
                        }
                    }
                } else if (value instanceof Map) {
                    value = bind(argument.getType(), ((Map) value).entrySet());
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Skipped binding of [{}]. [{}] was expected to be a map but was actually a [{}]", argument.getName(), value, value.getClass().getName());
                    }
                }
                Optional<?> converted = conversionService.convert(value, argument);
                if (converted.isPresent()) {
                    consumer.accept(converted.get());
                } else {
                    conversionContext.reject(value, new ConversionErrorException(argument, new IllegalArgumentException("Value [" + value + "] cannot be converted to type : " + argument.getType())));

                }
            }
        }
        return conversionContext;
    }
}
