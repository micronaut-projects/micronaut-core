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
package io.micronaut.inject.provider;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The lookup a provider makes on every call: a singleton already created is returned without copying the
 * resolution context the provider retained, which every other lookup copies.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
final class CreatedSingletonLookup {

    private final BeanContext context;
    private final BeanResolutionContext retained;
    private final Argument<Object> argument;
    private final @Nullable Qualifier<Object> qualifier;
    /**
     * Whether a full lookup selected a bean that is not a singleton, so that {@link #get()} looks for no created
     * singleton. A stale value only skips the shortcut, it never returns a wrong bean.
     */
    private volatile boolean prototypeTarget;

    CreatedSingletonLookup(BeanContext context,
                           BeanResolutionContext retained,
                           Argument<Object> argument,
                           @Nullable Qualifier<Object> qualifier) {
        this.context = context;
        this.retained = retained;
        this.argument = argument;
        this.qualifier = qualifier;
    }

    /**
     * The lookup a jakarta or javax provider makes: a provider definition that is a singleton keeps the bean it
     * looked up once, any other one makes the lookup on every call.
     *
     * @param resolutionContext The resolution context the provider is built in
     * @param context           The bean context
     * @param argument          The provided type
     * @param qualifier         The qualifier
     * @param singleton         Whether the provider definition is a singleton
     * @return The lookup
     */
    static Supplier<Object> forProvider(BeanResolutionContext resolutionContext,
                                        BeanContext context,
                                        Argument<Object> argument,
                                        @Nullable Qualifier<Object> qualifier,
                                        boolean singleton) {
        BeanResolutionContext retained = AbstractProviderDefinition.retain(resolutionContext);
        if (singleton) {
            return new KeptLookup(retained, argument, qualifier);
        }
        return new CreatedSingletonLookup(context, retained, argument, qualifier)::get;
    }

    /**
     * @return The bean, as a lookup through the retained resolution context returns it
     */
    Object get() {
        if (!prototypeTarget && context instanceof DefaultBeanContext defaultBeanContext) {
            Object created = defaultBeanContext.findCreatedSingleton(argument, qualifier);
            if (created != null) {
                return created;
            }
            prototypeTarget = defaultBeanContext.isSelectedCandidateNotSingleton(argument, qualifier);
        }
        return retained.copy().getBean(argument, qualifier);
    }

    /**
     * The lookup of a provider definition that is a singleton: the bean is looked up once and kept.
     */
    private static final class KeptLookup implements Supplier<Object> {
        private final BeanResolutionContext retained;
        private final Argument<Object> argument;
        private final @Nullable Qualifier<Object> qualifier;
        private @Nullable Object bean;

        KeptLookup(BeanResolutionContext retained, Argument<Object> argument, @Nullable Qualifier<Object> qualifier) {
            this.retained = retained;
            this.argument = argument;
            this.qualifier = qualifier;
        }

        @Override
        public Object get() {
            Object kept = bean;
            if (kept == null) {
                kept = retained.copy().getBean(argument, qualifier);
                bean = kept;
            }
            return kept;
        }
    }
}
