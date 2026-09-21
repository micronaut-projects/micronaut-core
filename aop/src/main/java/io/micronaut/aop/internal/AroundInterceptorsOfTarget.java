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
package io.micronaut.aop.internal;

import io.micronaut.aop.Interceptor;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.ProxyTargetInterceptorResolver;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates the non-singleton interceptors bound to the methods of a proxy target with the target, as its dependents.
 *
 * <p>The binding is the one the proxy selects with, read from the methods of the target that carry around advice,
 * which are the methods the proxy intercepts. The singleton interceptors are left to the proxy, which resolves them
 * as it is created, and an interceptor of a custom scope to its scope, which hands out the instance on every call. A
 * target fronted by a runtime proxy is left to that proxy.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class AroundInterceptorsOfTarget implements ProxyTargetInterceptorResolver {

    // the non-singleton interceptors bound to the methods of each target definition, found once per definition;
    // none in the common case
    private final Map<BeanDefinition<?>, List<BeanDefinition<Interceptor<?, ?>>>> owned = new ConcurrentHashMap<>();

    @Override
    public void resolveInterceptors(BeanResolutionContext resolutionContext, BeanDefinition<?> target) {
        List<BeanDefinition<Interceptor<?, ?>>> interceptors = owned.get(target);
        if (interceptors == null) {
            // not computed in the map: finding them may create beans, and so other proxy targets
            interceptors = ownedBy(resolutionContext, target);
            owned.putIfAbsent(target, interceptors);
        }
        for (BeanDefinition<Interceptor<?, ?>> interceptor : interceptors) {
            // the instance the target owns, or one created now that joins its dependents
            resolutionContext.getInterceptorRegistration(interceptor);
        }
    }

    private static List<BeanDefinition<Interceptor<?, ?>>> ownedBy(BeanResolutionContext resolutionContext, BeanDefinition<?> target) {
        if (isRuntimeProxyTarget(resolutionContext, target)) {
            // the creator of a runtime proxy is known to its generated code alone, and one that does not select per
            // target keeps interceptors of its own, as in 5.2, so an instance created for the target could intercept
            // nothing: a runtime proxy selects on its first call, as it does for a target this does not see
            return List.of();
        }
        List<AnnotationMetadata> advised = new ArrayList<>();
        for (ExecutableMethod<?, ?> method : target.getExecutableMethods()) {
            if (method.hasStereotype(AnnotationUtil.ANN_AROUND)) {
                advised.add(method);
            }
        }
        if (advised.isEmpty()) {
            return List.of();
        }
        Qualifier<Interceptor<?, ?>> binding = Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(advised.toArray(AnnotationMetadata[]::new)));
        List<BeanDefinition<Interceptor<?, ?>>> result = new ArrayList<>(2);
        for (BeanDefinition<Interceptor<?, ?>> definition : resolutionContext.getContext().getBeanDefinitions(Interceptor.ARGUMENT, binding)) {
            if (!definition.isSingleton() && !resolutionContext.isScopedInterceptor(definition)) {
                result.add(definition);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    /**
     * Whether the proxy fronting the target is created at runtime: a compiled proxy is a generated subclass of the
     * target, which its definition declares as the bean type, while a runtime proxy is generated only as it is
     * created and its definition declares the target type.
     */
    private static <T> boolean isRuntimeProxyTarget(BeanResolutionContext resolutionContext, BeanDefinition<T> target) {
        return resolutionContext.getContext()
            .findProxyBeanDefinition(target.asArgument(), target.getDeclaredQualifier())
            .map(proxy -> proxy.getBeanType() == target.getBeanType())
            .orElse(false);
    }
}
