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
package io.micronaut.aop.internal.intercepted;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptedMethod;
import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.ReturnType;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The {@link InterceptedMethod} utils class.
 *
 * @author Denis Stepanov
 * @since 2.1.0
 */
@Internal
public final class InterceptedMethodUtil {
    private InterceptedMethodUtil() {
    }

    /**
     * Find possible {@link InterceptedMethod} implementation.
     *
     * @param context The {@link MethodInvocationContext}
     * @return The {@link InterceptedMethod}
     */
    public static InterceptedMethod of(MethodInvocationContext<?, ?> context) {
        if (context.isSuspend()) {
            KotlinInterceptedMethod kotlinInterceptedMethod = KotlinInterceptedMethod.of(context);
            if (kotlinInterceptedMethod != null) {
                return kotlinInterceptedMethod;
            }
            return new SynchronousInterceptedMethod(context);
        } else {
            ReturnType<?> returnType = context.getReturnType();
            Class<?> returnTypeClass = returnType.getType();
            if (returnTypeClass == void.class || returnTypeClass == String.class) {
                // Micro Optimization
                return new SynchronousInterceptedMethod(context);
            } else if (CompletionStage.class.isAssignableFrom(returnTypeClass) || Future.class.isAssignableFrom(returnTypeClass)) {
                return new CompletionStageInterceptedMethod(context);
            } else if (PublisherInterceptedMethod.isConvertibleToPublisher(returnTypeClass)) {
                return new PublisherInterceptedMethod(context);
            } else {
                return new SynchronousInterceptedMethod(context);
            }
        }
    }

    /**
     * Resolve interceptor binding annotations from the metadata.
     * @param annotationMetadata The annotation metadata
     * @param interceptorKind The interceptor kind
     * @return the annotation values
     */
    public static io.micronaut.core.annotation.AnnotationValue<?>[] resolveInterceptorBinding(
            AnnotationMetadata annotationMetadata,
            InterceptorKind interceptorKind) {
        final List<AnnotationValue<InterceptorBinding>> interceptorBindings
                = annotationMetadata.getAnnotationValuesByType(InterceptorBinding.class);
        if (!interceptorBindings.isEmpty()) {
            return interceptorBindings
                    .stream()
                    .filter(av -> {
                        final InterceptorKind kind = av.enumValue("kind", InterceptorKind.class)
                                .orElse(InterceptorKind.AROUND);
                        return kind == interceptorKind;
                    })
                    .toArray(io.micronaut.core.annotation.AnnotationValue[]::new);
        }
        return AnnotationUtil.ZERO_ANNOTATION_VALUES;
    }

    /**
     * Does the given metadata have AOP advice declared.
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    public static boolean hasAroundStereotype(@Nullable AnnotationMetadata annotationMetadata) {
        return hasAround(annotationMetadata,
                annMetadata -> annMetadata.hasStereotype(Around.class),
                annMetdata -> annMetdata.getAnnotationValuesByType(InterceptorBinding.class));
    }

    /**
     * Does the given metadata have declared AOP advice.
     * @param annotationMetadata The annotation metadata
     * @return True if it does
     */
    public static boolean hasDeclaredAroundAdvice(@Nullable AnnotationMetadata annotationMetadata) {
        return hasAround(annotationMetadata,
                annMetadata -> annMetadata.hasDeclaredStereotype(Around.class),
                annMetdata -> annMetdata.getDeclaredAnnotationValuesByType(InterceptorBinding.class));
    }

    private static boolean hasAround(@Nullable AnnotationMetadata annotationMetadata,
                                     @NonNull Predicate<AnnotationMetadata> hasFunction,
                                     @NonNull Function<AnnotationMetadata, List<AnnotationValue<InterceptorBinding>>> interceptorBindingsFunction) {
        if (annotationMetadata == null) {
            return false;
        }
        if (hasFunction.test(annotationMetadata)) {
            return true;
        } else if (annotationMetadata.hasDeclaredStereotype(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS)) {
            return interceptorBindingsFunction.apply(annotationMetadata)
                    .stream().anyMatch(av ->
                            av.enumValue("kind", InterceptorKind.class).orElse(InterceptorKind.AROUND) == InterceptorKind.AROUND
                    );
        }

        return false;
    }
}
