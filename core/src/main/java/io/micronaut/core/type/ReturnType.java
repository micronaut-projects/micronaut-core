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
package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Models a return type of an {@link Executable} method in Micronaut.
 *
 * @param <T> The concrete type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ReturnType<T> extends TypeVariableResolver, AnnotationMetadataProvider {

    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The return type as an argument
     */
    default Argument<T> asArgument() {
        Collection<Argument<?>> values = getTypeVariables().values();
        return Argument.of(getType(), values.toArray(new Argument[0]));
    }

    /**
     * @return Is the return type suspended function (Kotlin).
     * @since 2.0.0
     */
    default boolean isSuspended() {
        return false;
    }

    /**
     * @return Is the return type reactive.
     * @since 2.0.0
     */
    default boolean isReactive() {
        return Publishers.isConvertibleToPublisher(getType());
    }

    /**
     * @return Is the return the return type a reactive completable type.
     * @since 2.0.0
     */
    default boolean isCompletable() {
        return Publishers.isCompletable(getType());
    }

    /**
     * @return Is the return type a single result or multiple results
     * @since 2.0
     */
    default boolean isSingleResult() {
        if (isSpecifiedSingle()) {
            return true;
        } else {
            if (isReactive()) {
                Class<T> returnType = getType();
                return Publishers.isSingle(returnType);
            } else {
                return true;
            }
        }
    }

    /**
     * @return Has the return type been specified to emit a single result with {@link SingleResult}.
     * @since 2.0
     */
    default boolean isSpecifiedSingle() {
        AnnotationMetadata annotationMetadata = getAnnotationMetadata();
        return annotationMetadata.hasStereotype(SingleResult.class) &&
                annotationMetadata.booleanValue(SingleResult.NAME).orElse(true);
    }

    /**
     * @return Is the return type asynchronous.
     * @since 2.0.0
     */
    default boolean isAsync() {
        Class<T> type = getType();
        return CompletionStage.class.isAssignableFrom(type);
    }

    /**
     * @return Is the return type either async or reactive.
     * @since 2.0.0
     */
    default boolean isAsyncOrReactive() {
        return isAsync() || isReactive();
    }

    /**
     * Returns whether the return type is logically void. This includes
     * reactive times that emit nothing (such as {@link io.micronaut.core.async.subscriber.Completable})
     * and asynchronous types that emit {@link Void}.
     *
     * @return Is the return type logically void.
     * @since 2.0.0
     */
    default boolean isVoid() {
        Class<T> javaReturnType = getType();
        if (javaReturnType == void.class) {
            return true;
        } else {
            if (isReactive() || isAsync()) {
                return isCompletable() ||
                        getFirstTypeVariable()
                                .filter(arg -> arg.getType() == Void.class).isPresent();
            }
        }
        return false;
    }

    /**
     * @return Is the return type {@link java.util.Optional}.
     * @since 2.0.1
     */
    default boolean isOptional() {
        Class<T> type = getType();
        return type == Optional.class;
    }

    /**
     * Create a new return type from the given type and arguments.
     *
     * @param type          The type
     * @param typeArguments The type arguments
     * @param <T1>          The return type
     * @return A {@link ReturnType}
     */
    static <T1> ReturnType<T1> of(Class<T1> type, Argument<?>... typeArguments) {
        Map<String, Argument<?>> argumentMap = new LinkedHashMap<>(typeArguments.length);
        for (Argument<?> argument : typeArguments) {
            argumentMap.put(argument.getName(), argument);
        }
        return new ReturnType<T1>() {
            @Override
            public Class<T1> getType() {
                return type;
            }

            @Override
            public Argument[] getTypeParameters() {
                return typeArguments;
            }

            @Override
            public Map<String, Argument<?>> getTypeVariables() {
                return argumentMap;
            }
        };
    }
}
