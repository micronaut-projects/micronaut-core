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
package io.micronaut.context;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
/**
 * Which bean received which other bean through injection, whatever the scope of either.
 *
 * <p>The graph is recorded while beans are created, only when the context was configured to
 * {@link BeanContextConfiguration#beanDependencyTrackingEnabled() track dependencies}, which a development
 * launcher does and a production run does not. A development launcher uses it to decide, when the
 * application's classes change, which beans of the framework received an instance of a changed
 * class and how: through the constructor, in which case the bean must be recreated, or through a
 * field or method, in which case it can be injected again, or through a provider, in which case
 * nothing is needed because the provider resolves the bean anew on every call.</p>
 *
 * <p>Singletons, scoped beans, scoped proxies and prototypes are all recorded. A prototype instance belongs
 * to the bean that received it and follows its lifecycle; what the prototype itself received is
 * recorded under the prototype's definition, so a singleton reached through a prototype is still a
 * transitive dependent of what it holds.</p>
 *
 * <p>A {@link BeanContext#createBeanRegistration(BeanDefinition) fresh registration} records what it received
 * under its definition, as any instance does, and a singleton's fresh instance shares those edges with the scoped
 * one until both are destroyed. What a bean resolves or creates through an injected {@link BeanDependencyResolver},
 * or a group the resolver opened, is recorded as received by that bean, with {@link InjectionKind#OTHER}.</p>
 *
 * <p>A node is a bean definition together with the qualifier the context registers its bean under: the members of
 * an {@code @EachBean} or {@code @EachProperty} set and the beans of an {@code @Any} factory are distinct nodes.
 * The overloads taking a {@link BeanRegistration} look up the node of that registration, which is the node
 * a caller holding the bean wants; those taking a {@link BeanDefinition} expect the definition the registration
 * carries.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface BeanDependencyGraph {

    /**
     * The beans the given bean received.
     *
     * @param dependent The bean definition of the receiving bean, as its {@link BeanRegistration} carries it
     * @return The dependencies, empty if none were recorded
     */
    Collection<BeanDependency> dependenciesOf(BeanDefinition<?> dependent);

    /**
     * The beans the bean of the given registration received.
     *
     * @param dependent The registration of the receiving bean
     * @return The dependencies, empty if none were recorded
     */
    default Collection<BeanDependency> dependenciesOf(BeanRegistration<?> dependent) {
        return dependenciesOf(dependent.getBeanDefinition());
    }

    /**
     * The beans that received the given bean.
     *
     * @param dependency The bean definition of the received bean, as its {@link BeanRegistration} carries it
     * @return The dependents, empty if none were recorded
     */
    Collection<BeanDependency> dependentsOf(BeanDefinition<?> dependency);

    /**
     * The beans that received the bean of the given registration.
     *
     * @param dependency The registration of the received bean
     * @return The dependents, empty if none were recorded
     */
    default Collection<BeanDependency> dependentsOf(BeanRegistration<?> dependency) {
        return dependentsOf(dependency.getBeanDefinition());
    }

    /**
     * Every bean that received the given bean, or received a bean that received it, and so on, apart
     * from those that hold it only {@link BeanDependency#lazy() lazily}.
     *
     * @param dependency The bean definition of the received bean, as its {@link BeanRegistration} carries it
     * @return The transitive dependents, in the order they were reached
     */
    Set<BeanDefinition<?>> transitiveDependentsOf(BeanDefinition<?> dependency);

    /**
     * Every bean that received the bean of the given registration, or received a bean that received it, and so on,
     * apart from those that hold it only {@link BeanDependency#lazy() lazily}.
     *
     * @param dependency The registration of the received bean
     * @return The transitive dependents, in the order they were reached
     */
    default Set<BeanDefinition<?>> transitiveDependentsOf(BeanRegistration<?> dependency) {
        return transitiveDependentsOf(dependency.getBeanDefinition());
    }

    /**
     * Every bean the given bean received, or a bean it received did, and so on, apart from those held
     * only {@link BeanDependency#lazy() lazily}: what must stay alive for the bean to keep working.
     *
     * @param dependent The bean definition of the receiving bean, as its {@link BeanRegistration} carries it
     * @return The transitive dependencies, in the order they were reached
     */
    Set<BeanDefinition<?>> transitiveDependenciesOf(BeanDefinition<?> dependent);

    /**
     * Every bean the bean of the given registration received, or a bean it received did, and so on, apart from
     * those held only {@link BeanDependency#lazy() lazily}.
     *
     * @param dependent The registration of the receiving bean
     * @return The transitive dependencies, in the order they were reached
     */
    default Set<BeanDefinition<?>> transitiveDependenciesOf(BeanRegistration<?> dependent) {
        return transitiveDependenciesOf(dependent.getBeanDefinition());
    }

    /**
     * Every dependency recorded so far.
     *
     * @return The dependencies
     */
    Collection<BeanDependency> dependencies();

    /**
     * How a bean was injected.
     */
    enum InjectionKind {
        /**
         * A constructor or factory method argument, or the factory a bean is produced by. The receiving bean holds
         * the instance in a way that cannot be replaced; it must be recreated when the dependency is.
         */
        CONSTRUCTOR,
        /**
         * A field. The receiving bean can be injected again in place.
         */
        FIELD,
        /**
         * A method, such as a setter. The receiving bean can be injected again in place.
         */
        METHOD,
        /**
         * Anything else the bean received: an injection point that is neither a constructor, a field nor a method,
         * such as an annotation member, and every bean the bean resolved or created through an injected
         * {@link BeanDependencyResolver} or a group the resolver opened. The receiving bean holds such a bean as it
         * holds a constructor argument: it is not {@link BeanDependency#reinjectable() reinjectable}, and the edge
         * stays until the receiving instance is destroyed.
         */
        OTHER
    }

    /**
     * One recorded injection.
     *
     * <p>Two dependencies are equal when they join the same nodes, as the graph identifies them (a definition with
     * the qualifier its bean is registered under), in the same way: whichever definition object stands for a node,
     * a delegate or the definition it wraps, does not matter.</p>
     *
     * @param dependent The definition of the bean that received the dependency; for a bean created per qualifier
     *                  (an {@code @EachBean} member) a delegate carrying that qualifier, so that the member is told apart
     * @param dependency The definition of the received bean
     * @param kind How the dependency was injected
     * @param lazy Whether the dependent received a provider of the dependency rather than the dependency itself. The edge
     *             names each definition the provider's type argument and qualifier select, as the provider resolves one
     *             of them on each call and holds none
     * @param collection Whether the injection point is a collection or array of a type the dependency implements
     */
    record BeanDependency(BeanDefinition<?> dependent, BeanDefinition<?> dependency, InjectionKind kind, boolean lazy, boolean collection) {

        /**
         * Validating constructor.
         *
         * @param dependent The receiving bean
         * @param dependency The received bean
         * @param kind The injection kind
         * @param lazy Whether resolved through a provider
         * @param collection Whether part of a collection
         */
        public BeanDependency {
            Objects.requireNonNull(dependent, "dependent");
            Objects.requireNonNull(dependency, "dependency");
            Objects.requireNonNull(kind, "kind");
        }

        /**
         * Whether the receiving bean can be repaired in place by injecting it again, rather than recreated,
         * when the dependency is replaced. A field or method injection can, a collection included, since
         * injecting again reassigns it; a constructor injection cannot, whatever it copied.
         *
         * @return True for a field or method injection
         */
        public boolean reinjectable() {
            return !lazy && (kind == InjectionKind.FIELD || kind == InjectionKind.METHOD);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof BeanDependency that
                && kind == that.kind
                && lazy == that.lazy
                && collection == that.collection
                && DefaultBeanDependencyGraph.Key.of(dependent).equals(DefaultBeanDependencyGraph.Key.of(that.dependent))
                && DefaultBeanDependencyGraph.Key.of(dependency).equals(DefaultBeanDependencyGraph.Key.of(that.dependency));
        }

        @Override
        public int hashCode() {
            return Objects.hash(DefaultBeanDependencyGraph.Key.of(dependent), DefaultBeanDependencyGraph.Key.of(dependency), kind, lazy, collection);
        }
    }
}
