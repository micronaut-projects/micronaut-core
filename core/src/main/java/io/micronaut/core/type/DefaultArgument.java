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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

/**
 * Represents an argument to a constructor or method.
 *
 * @param <T> The argument type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DefaultArgument<T> implements Argument<T> {

    static final Set<Class<?>> CONTAINER_TYPES = CollectionUtils.setOf(
        List.class,
        Set.class,
        Map.class,
        Collection.class,
        Queue.class,
        SortedSet.class,
        Deque.class,
        Vector.class,
        ArrayList.class);

    private final Class<T> type;
    private final String name;
    private final Map<String, Argument<?>> typeParameters;
    private final Argument[] typeParameterArray;
    private final AnnotationMetadata annotationMetadata;

    /**
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     */
    public DefaultArgument(Class<T> type, String name, AnnotationMetadata annotationMetadata, Argument... genericTypes) {
        this(type,
             name,
             annotationMetadata,
             ArrayUtils.isNotEmpty(genericTypes) ? initializeTypeParameters(genericTypes) : Collections.EMPTY_MAP,
             genericTypes
        );
    }

    /**
     * @param type               The type
     * @param annotationMetadata The annotation metadata
     * @param genericTypes       The generic types
     */
    public DefaultArgument(Class<T> type, AnnotationMetadata annotationMetadata, Argument... genericTypes) {
        this(type,
                null,
                annotationMetadata,
                ArrayUtils.isNotEmpty(genericTypes) ? initializeTypeParameters(genericTypes) : Collections.EMPTY_MAP,
                genericTypes
        );
    }

    /**
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     * @param typeParameters     The map of parameters
     * @param typeParameterArray The array of arguments
     */
    public DefaultArgument(Class<T> type, String name, AnnotationMetadata annotationMetadata, Map<String, Argument<?>> typeParameters, Argument[] typeParameterArray) {
        this.type = type;
        this.name = name;
        this.annotationMetadata = annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA;
        this.typeParameters = typeParameters;
        this.typeParameterArray = typeParameterArray;
    }

    /**
     * @param type               The type
     * @param name               The name
     * @param annotationMetadata The annotation metadata
     */
    public DefaultArgument(Type type, String name, AnnotationMetadata annotationMetadata) {
        this.annotationMetadata = annotationMetadata != null ? annotationMetadata : AnnotationMetadata.EMPTY_METADATA;
        if (type == null) {
            type = getClass().getGenericSuperclass();
            if (type instanceof ParameterizedType) {
                type = ((ParameterizedType) type).getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException(type + " is not parameterized");
            }
        }
        if (type instanceof Class) {
            //noinspection unchecked
            this.type = (Class<T>) type;
            this.typeParameterArray = Argument.ZERO_ARGUMENTS;
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            //noinspection unchecked
            this.type = (Class<T>) parameterizedType.getRawType();
            TypeVariable<Class<T>>[] params = this.type.getTypeParameters();
            Type[] paramValues = parameterizedType.getActualTypeArguments();
            typeParameterArray = new Argument[params.length];
            for (int i = 0; i < params.length; i++) {
                TypeVariable param = params[i];
                Type value = paramValues[i];
                typeParameterArray[i] = new DefaultArgument(value, param.getName(), AnnotationMetadata.EMPTY_METADATA);
            }
        } else {
            throw new IllegalArgumentException(type.getClass().getSimpleName() + " types are not supported");
        }
        if (name == null) {
            name = this.type.getSimpleName();
        }
        this.name = name;
        this.typeParameters = initializeTypeParameters(this.typeParameterArray);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Optional<Argument<?>> getFirstTypeVariable() {
        if (!typeParameters.isEmpty()) {
            return typeParameters.values().stream().findFirst();
        }
        return Optional.empty();
    }

    @Override
    public Argument[] getTypeParameters() {
        return typeParameterArray;
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return this.typeParameters;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public String getName() {
        if (name == null) {
            return getType().getSimpleName();
        }
        return name;
    }

    @Override
    public String toString() {
        return type.getSimpleName() + " " + getName();
    }

    @Override
    public boolean equalsType(Argument<?> o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        return Objects.equals(type, o.getType()) &&
            Objects.equals(typeParameters, o.getTypeVariables());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultArgument)) {
            return false;
        }
        DefaultArgument<?> that = (DefaultArgument<?>) o;
        return Objects.equals(type, that.type) &&
            Objects.equals(getName(), that.getName()) &&
            Objects.equals(typeParameters, that.typeParameters);
    }

    @Override
    public int typeHashCode() {
        return Objects.hash(type, typeParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, getName(), typeParameters);
    }

    private static Map<String, Argument<?>> initializeTypeParameters(Argument[] genericTypes) {
        Map<String, Argument<?>> typeParameters;
        if (genericTypes != null && genericTypes.length > 0) {
            typeParameters = new LinkedHashMap<>(genericTypes.length);
            for (Argument genericType : genericTypes) {
                typeParameters.put(genericType.getName(), genericType);
            }
        } else {
            typeParameters = Collections.emptyMap();
        }
        return typeParameters;
    }

}
