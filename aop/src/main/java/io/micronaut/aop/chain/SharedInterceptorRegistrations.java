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
package io.micronaut.aop.chain;

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * Shares one interceptor resolution between the construction and the post-construct interception of a bean.
 *
 * <p>The scenario this exists for is a bean whose advice is declared with {@code @AroundConstruct} and lifecycle
 * interceptor bindings but no {@code @Around}, so no proxy is generated and there is no instance field in which to
 * retain anything:</p>
 *
 * <pre>{@code
 * @Retention(RUNTIME)
 * @AroundConstruct
 * @InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
 * @interface Managed {}
 *
 * @Singleton @Managed
 * class Product { @PostConstruct void init() {} }
 * }</pre>
 *
 * <p>Without sharing, construction and post-construct each resolve their own interceptor, so a {@code @Prototype}
 * interceptor cannot carry state from one phase to the next.</p>
 *
 * <p>A bean definition is a stateless singleton shared by every instance, so the resolved registrations cannot be kept
 * on it. They are kept on the {@link BeanResolutionContext} instead, which is one instance for the whole of a bean's
 * creation. The window is narrower than it looks: for a bean with constructor advice the generated
 * {@code doInstantiate} runs post-construct interception from <em>inside</em> the constructor interceptor chain, so
 * the registrations are pushed before construction and popped after it, and entries are keyed by definition so that a
 * bean created while another is being constructed reads its own.</p>
 *
 * <p>Destruction happens later with a fresh resolution context, so nothing is shared with it here; see
 * {@code MethodInterceptorChain} for how pre-destroy reaches the interceptors a bean owns.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Internal
public final class SharedInterceptorRegistrations {

    private static final String ATTRIBUTE = "io.micronaut.aop.sharedInterceptorRegistrations";

    private SharedInterceptorRegistrations() {
    }

    /**
     * Resolves the interceptor registrations that a bean's construction, post-construct and pre-destroy interception
     * can all select from.
     *
     * <p>The binding is the union of the {@code AROUND_CONSTRUCT}, {@code POST_CONSTRUCT} and {@code PRE_DESTROY}
     * bindings declared by the bean, so the result is a superset of what any one phase needs. Widening cannot
     * over-apply an interceptor: every interception point filters this set again by its own binding and kind before
     * building a chain. It does mean an interceptor bound only to {@code @PreDestroy} is created while the bean is
     * created rather than when it is destroyed, which is what allows the same instance to serve both.</p>
     *
     * <p>Returns {@code null} for a bean that declares none of those bindings, for example a bean with only
     * {@code @Around} advice, so that such beans resolve exactly as they did before.</p>
     *
     * @param resolutionContext The resolution context
     * @param definition        The bean definition
     * @param constructor       The constructor metadata, or {@code null}
     * @param <T>               The bean type
     * @return The resolved registrations, or {@code null} when the bean declares no lifecycle-related binding
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> @Nullable List<BeanRegistration<Interceptor<T, T>>> resolve(BeanResolutionContext resolutionContext,
                                                                                 BeanDefinition<?> definition,
                                                                                 @Nullable AnnotationMetadataProvider constructor) {
        AnnotationMetadata definitionMetadata = definition.getAnnotationMetadata();
        AnnotationMetadata constructorMetadata = constructor == null
            ? definitionMetadata
            : new AnnotationMetadataHierarchy(definitionMetadata, constructor.getAnnotationMetadata());

        Collection<AnnotationValue<?>> binding = new ArrayList<>();
        binding.addAll(AbstractInterceptorChain.resolveInterceptorValues(constructorMetadata, InterceptorKind.AROUND_CONSTRUCT));
        binding.addAll(AbstractInterceptorChain.resolveInterceptorValues(definitionMetadata, InterceptorKind.POST_CONSTRUCT));
        binding.addAll(AbstractInterceptorChain.resolveInterceptorValues(definitionMetadata, InterceptorKind.PRE_DESTROY));
        if (binding.isEmpty()) {
            return null;
        }
        return new ArrayList(resolutionContext.getBeanRegistrations(
            Interceptor.ARGUMENT,
            Qualifiers.byInterceptorBindingValues(binding)
        ));
    }

    /**
     * Makes the registrations resolved for a bean visible to the post-construct interception of that same bean.
     *
     * <p>Called immediately before the constructor interceptor chain runs, and matched by {@link #pop} in a
     * {@code finally}. Because post-construct interception happens inside that chain, this is the only window in
     * which {@link #peek} can see the entry.</p>
     *
     * <p>Nesting is handled by the stack: if the constructor of one bean, or one of its {@code @AroundConstruct}
     * interceptors, causes another bean to be created, that bean pushes and pops its own entry above this one.</p>
     *
     * @param resolutionContext The resolution context
     * @param definition        The definition being instantiated
     * @param registrations     The registrations
     */
    @SuppressWarnings("unchecked")
    public static void push(BeanResolutionContext resolutionContext,
                            BeanDefinition<?> definition,
                            @Nullable List<?> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        Deque<Entry> stack = (Deque<Entry>) resolutionContext.getAttribute(ATTRIBUTE);
        if (stack == null) {
            stack = new ArrayDeque<>(3);
            resolutionContext.setAttribute(ATTRIBUTE, stack);
        }
        stack.push(new Entry(definition, registrations));
    }

    /**
     * Removes the entry added by {@link #push}, once the bean has been constructed and its post-construct
     * interception has run.
     *
     * <p>Removes nothing unless the top of the stack belongs to this definition, so an unbalanced push elsewhere
     * cannot discard an entry that is still in use.</p>
     *
     * @param resolutionContext The resolution context
     * @param definition        The definition being instantiated
     * @param registrations     The registrations that were pushed
     */
    public static void pop(BeanResolutionContext resolutionContext,
                           BeanDefinition<?> definition,
                           @Nullable List<?> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        Deque<Entry> stack = stack(resolutionContext);
        if (stack != null && !stack.isEmpty() && stack.peek().definition == definition) {
            stack.pop();
        }
    }

    /**
     * Returns the registrations resolved while the given definition is being instantiated.
     *
     * @param resolutionContext The resolution context
     * @param definition        The definition being initialized
     * @return The registrations, or {@code null} when this initialization is not nested in that construction
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> peek(BeanResolutionContext resolutionContext,
                                                                                BeanDefinition<?> definition) {
        Deque<Entry> stack = stack(resolutionContext);
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Entry entry = stack.peek();
        return entry.definition == definition
            ? (Collection<BeanRegistration<Interceptor<?, ?>>>) entry.registrations
            : null;
    }

    @SuppressWarnings("unchecked")
    private static @Nullable Deque<Entry> stack(BeanResolutionContext resolutionContext) {
        return (Deque<Entry>) resolutionContext.getAttribute(ATTRIBUTE);
    }

    private record Entry(BeanDefinition<?> definition, List<?> registrations) {
    }
}
