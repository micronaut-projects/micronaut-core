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
package io.micronaut.aop;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.proxy.InterceptedBean;

import java.util.List;

/**
 * An interface implemented by generated proxy classes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Intercepted extends InterceptedBean {

    /**
     * The interceptor registrations that were resolved for this proxy.
     *
     * <p>Every generated proxy retains the registrations its constructor was given, so a proxy can always report the
     * interceptors bound to it. For an around- or introduction-only proxy that is the set matching its around
     * bindings; a proxy whose target also declares lifecycle advice is given a wider set, described below.</p>
     *
     * <p>The scenario the retention was introduced for is an advised bean that also declares lifecycle advice:</p>
     *
     * <pre>{@code
     * @Retention(RUNTIME)
     * @Around
     * @InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
     * @InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
     * @interface Tracked {}
     *
     * @Singleton @Tracked
     * class MyBean { @PostConstruct void init() {} @PreDestroy void close() {} }
     * }</pre>
     *
     * <p>The proxy constructor is given the registrations that match the target's bindings and keeps them, so
     * post-construct and pre-destroy interception select from the same set the proxy already uses for its methods,
     * and a {@code @Prototype} interceptor is one instance for the whole life of one target. Destruction in
     * particular has no other route to them: it runs with a fresh resolution context, and the proxy instance is the
     * only thing that survives from creation to destruction.</p>
     *
     * <p>A proxy whose target is not a singleton, such as a scoped proxy, keeps its own registrations as well but
     * does not intercept its targets with them: each target resolves its own set when it is created, and the proxy
     * selects the interceptors of every call from the set of the target of that call, so that a non-singleton
     * interceptor is one instance per target. See {@code io.micronaut.aop.beandefinition.TargetInterceptorRegistrations}.</p>
     *
     * <p>Proxies generated before this existed keep the empty default; the runtime then resolves interceptors by
     * binding as it always did.</p>
     *
     * <p>The context reads the same list through {@link InterceptedBean#$interceptorRegistrations()} when
     * {@code destroyBean(Object)} is handed a proxy it tracks no registration for, so the non-singleton interceptors
     * are destroyed with their target there too.</p>
     *
     * @return The retained interceptor registrations, never {@code null}
     * @since 5.2.0
     */
    @Override
    @Internal
    @UsedByGeneratedCode
    // The $ prefix marks this as generated-code infrastructure and keeps it clear of any method on the proxied type.
    @SuppressWarnings({"checkstyle:MethodName", "java:S100"})
    default List<BeanRegistration<Interceptor<?, ?>>> $interceptorRegistrations() {
        return List.of();
    }
}
