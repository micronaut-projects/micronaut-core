/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;

/**
 * Utils class to verify if an executable method in annotated with {@link Blocking}.
 * @author Sergio del Amo
 * @since 4.3.0
 */
@Internal
public class BlockingUtils {

    /**
     * Whether a method is annotated with the {@link Blocking} annotation.
     * @param beanContext Bean Context
     * @param bean Bean
     * @param methodName The method name
     * @param argumentTypes The argument types
     * @return Whether a method is annotated with the {@link Blocking} annotation.
     */
    public static boolean isMethodBlocking(@NonNull BeanContext beanContext,
                                            @NonNull Object bean,
                                            @NonNull String methodName,
                                            Class<?>... argumentTypes) {
        return ExecutableMethodUtils.hasAnnotationInMethod(beanContext, bean, Blocking.class, methodName, argumentTypes);
    }
}
