/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.aop.beandefinition;

import io.micronaut.aop.Interceptor;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.proxy.InterceptedBean;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Establishes the complete lifecycle candidate set before a generated definition initializes its bean.
 * The resolution context transfers this set, including an empty result, to the completed registration.
 *
 * @since 5.3.0
 */
@Internal
public final class LifecycleInterceptorRegistrations {
    private LifecycleInterceptorRegistrations() {
    }

    /**
     * Retains construction candidates, or captures the proxy candidates or the bean's complete binding set once.
     * This entry point is emitted only by definitions whose lifecycle is intercepted. Older definitions continue
     * to use the compatibility resolution in the lifecycle chain.
     *
     * @param resolutionContext The creation context
     * @param definition The definition being instantiated
     * @param bean The constructed instance
     * @param initialization Whether post-construct interception needs its own candidates
     */
    @UsedByGeneratedCode
    public static void capture(BeanResolutionContext resolutionContext, BeanDefinition<?> definition, @Nullable Object bean, boolean initialization) {
        if (bean == null || resolutionContext.getBeanInterceptors(definition) != null) {
            return;
        }
        if (!initialization) {
            List<?> destructionCandidates = resolutionContext.getBeanDestructionInterceptors(definition);
            if (destructionCandidates != null) {
                resolutionContext.setBeanInterceptors(definition, destructionCandidates);
                return;
            }
        }
        List<?> candidates = bean instanceof InterceptedBean proxy
            ? proxy.$interceptorRegistrations()
            : List.copyOf(resolutionContext.getInterceptorRegistrations(
                Interceptor.ARGUMENT, Qualifiers.byInterceptorBinding(definition.getAnnotationMetadata())));
        resolutionContext.setBeanInterceptors(definition, candidates);
    }
}
