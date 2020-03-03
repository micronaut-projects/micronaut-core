/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.validation.validator.extractors;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.valueextraction.UnwrapByDefault;
import javax.validation.valueextraction.ValueExtractor;
import java.util.*;

/**
 * The default value extractors.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Introspected
public class DefaultValueExtractors implements ValueExtractorRegistry {

    public static final String ITERABLE_ELEMENT_NODE_NAME = "<iterable element>";
    public static final String LIST_ELEMENT_NODE_NAME = "<list element>";
    public static final String MAP_VALUE_NODE_NAME = "<map value>";

    private final UnwrapByDefaultValueExtractor<Optional> optionalValueExtractor =
            (originalValue, receiver) -> receiver.value(null, originalValue.orElse(null));
    private final UnwrapByDefaultValueExtractor<OptionalInt> optionalIntValueExtractor =
            (originalValue, receiver) -> receiver.value(null, originalValue.isPresent() ? originalValue.getAsInt() : null);
    private final UnwrapByDefaultValueExtractor<OptionalLong> optionalLongValueExtractor =
            (originalValue, receiver) -> receiver.value(null, originalValue.isPresent() ? originalValue.getAsLong() : null);
    private final UnwrapByDefaultValueExtractor<OptionalDouble> optionalDoubleValueExtractor =
            (originalValue, receiver) -> receiver.value(null, originalValue.isPresent() ? originalValue.getAsDouble() : null);

    private final ValueExtractor<Iterable> iterableValueExtractor = (originalValue, receiver) -> {
        if (originalValue instanceof List) {
            int i = 0;
            for (Object o : originalValue) {
                receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i++, o);
            }
        } else {
            for (Object o : originalValue) {
                receiver.iterableValue(ITERABLE_ELEMENT_NODE_NAME, o);
            }
        }
    };
    private final ValueExtractor<Map<?, ?>> mapValueExtractor = (originalValue, receiver) -> {
        for (Map.Entry<?, ?> entry : originalValue.entrySet()) {
            receiver.keyedValue(MAP_VALUE_NODE_NAME, entry.getKey(), entry.getValue());
        }
    };
    private final ValueExtractor<Object[]> objectArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<int[]> intArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<byte[]> byteArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<boolean[]> booleanArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<double[]> doubleArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<char[]> charArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<float[]> floatArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final ValueExtractor<short[]> shortArrayValueExtractor = (originalValue, receiver) -> {
        for (int i = 0; i < originalValue.length; i++) {
            receiver.indexedValue(LIST_ELEMENT_NODE_NAME, i, originalValue[i]);
        }
    };

    private final Map<Class, ValueExtractor> valueExtractors;
    private final Set<Class> unwrapByDefaultTypes = new HashSet<>(5);

    /**
     * Default constructor.
     */
    public DefaultValueExtractors() {
        this(null);
    }

    /**
     * Constructor used during DI.
     *
     * @param beanContext The bean context
     */
    @Inject
    protected DefaultValueExtractors(@Nullable BeanContext beanContext) {
        BeanWrapper<DefaultValueExtractors> wrapper = BeanWrapper.findWrapper(this).orElse(null);
        Map<Class, ValueExtractor> extractorMap = new HashMap<>();

        if (beanContext != null && beanContext.containsBean(ValueExtractor.class)) {
            final Collection<BeanRegistration<ValueExtractor>> valueExtractors =
                    beanContext.getBeanRegistrations(ValueExtractor.class);
            if (CollectionUtils.isNotEmpty(valueExtractors)) {
                for (BeanRegistration<ValueExtractor> reg : valueExtractors) {
                    final ValueExtractor valueExtractor = reg.getBean();
                    final Class[] typeParameters = reg.getBeanDefinition().getTypeParameters(ValueExtractor.class);
                    if (ArrayUtils.isNotEmpty(typeParameters)) {
                        final Class targetType = typeParameters[0];
                        extractorMap.put(targetType, valueExtractor);
                        if (valueExtractor instanceof UnwrapByDefaultValueExtractor || valueExtractor.getClass().isAnnotationPresent(UnwrapByDefault.class)) {
                            unwrapByDefaultTypes.add(targetType);
                        }
                    }
                }
            }
        }

        if (wrapper != null) {

            final Collection<BeanProperty<DefaultValueExtractors, Object>> properties = wrapper.getBeanProperties();
            for (BeanProperty<DefaultValueExtractors, Object> property : properties) {
                if (ValueExtractor.class.isAssignableFrom(property.getType())) {
                    final ValueExtractor valueExtractor = wrapper
                            .getProperty(property.getName(), ValueExtractor.class).orElse(null);
                    final Class<?> targetType = property.asArgument().getFirstTypeVariable().map(Argument::getType).orElse(null);
                    extractorMap.put(targetType, valueExtractor);
                    if (valueExtractor instanceof UnwrapByDefaultValueExtractor || valueExtractor.getClass().isAnnotationPresent(UnwrapByDefault.class)) {
                        unwrapByDefaultTypes.add(targetType);
                    }
                }
            }
            this.valueExtractors = new HashMap<>(extractorMap.size());
            this.valueExtractors.putAll(extractorMap);
        } else {
            this.valueExtractors = Collections.emptyMap();
        }
    }

    /**
     * Value extractor for optional.
     *
     * @return The value extractor.
     */
    public UnwrapByDefaultValueExtractor<Optional> getOptionalValueExtractor() {
        return optionalValueExtractor;
    }

    /**
     * Value extractor for {@link OptionalInt}.
     *
     * @return The value extractor
     */
    public UnwrapByDefaultValueExtractor<OptionalInt> getOptionalIntValueExtractor() {
        return optionalIntValueExtractor;
    }

    /**
     * Value extractor for {@link OptionalLong}.
     *
     * @return The value extractor
     */
    public UnwrapByDefaultValueExtractor<OptionalLong> getOptionalLongValueExtractor() {
        return optionalLongValueExtractor;
    }

    /**
     * Value extractor for {@link OptionalDouble}.
     *
     * @return The value extractor
     */
    public UnwrapByDefaultValueExtractor<OptionalDouble> getOptionalDoubleValueExtractor() {
        return optionalDoubleValueExtractor;
    }

    /**
     * Value extractor for iterable.
     * @return The value extractor
     */
    public ValueExtractor<Iterable> getIterableValueExtractor() {
        return iterableValueExtractor;
    }

    /**
     * Value extractor for iterable.
     * @return The value extractor
     */
    public ValueExtractor<Map<?, ?>> getMapValueExtractor() {
        return mapValueExtractor;
    }

    /**
     * Value extractor for Object[].
     * @return The object[] extractor
     */
    public ValueExtractor<Object[]> getObjectArrayValueExtractor() {
        return objectArrayValueExtractor;
    }

    /**
     * Value extractor for int[].
     * @return The int[] extractor
     */
    public ValueExtractor<int[]> getIntArrayValueExtractor() {
        return intArrayValueExtractor;
    }

    /**
     * Value extractor for byte[].
     * @return The byte[] extractor
     */
    public ValueExtractor<byte[]> getByteArrayValueExtractor() {
        return byteArrayValueExtractor;
    }

    /**
     * Value extractor for char[].
     * @return The char[] extractor
     */
    public ValueExtractor<char[]> getCharArrayValueExtractor() {
        return charArrayValueExtractor;
    }

    /**
     * Value extractor for boolean[].
     * @return The boolean[] extractor
     */
    public ValueExtractor<boolean[]> getBooleanArrayValueExtractor() {
        return booleanArrayValueExtractor;
    }

    /**
     * Value extractor for double[].
     * @return The double[] extractor
     */
    public ValueExtractor<double[]> getDoubleArrayValueExtractor() {
        return doubleArrayValueExtractor;
    }

    /**
     * Value extractor for float[].
     * @return The float[] extractor
     */
    public ValueExtractor<float[]> getFloatArrayValueExtractor() {
        return floatArrayValueExtractor;
    }

    /**
     * Value extractor for short[].
     * @return The short[] extractor
     */
    public ValueExtractor<short[]> getShortArrayValueExtractor() {
        return shortArrayValueExtractor;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Optional<ValueExtractor<T>> findValueExtractor(@Nonnull Class<T> targetType) {
        final ValueExtractor valueExtractor = valueExtractors.get(targetType);
        if (valueExtractor != null) {
            return Optional.of(valueExtractor);
        } else {
            return (Optional) valueExtractors.entrySet().stream()
                    .filter(entry -> entry.getKey().isAssignableFrom(targetType))
                    .map(Map.Entry::getValue)
                    .findFirst();
        }
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> Optional<ValueExtractor<T>> findUnwrapValueExtractor(@Nonnull Class<T> targetType) {
        if (unwrapByDefaultTypes.contains(targetType)) {
            return Optional.ofNullable(valueExtractors.get(targetType));
        }
        return Optional.empty();
    }
}
