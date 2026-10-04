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
 * Which singleton received which other singleton through injection.
 *
 * <p>The graph is recorded while beans are created, only when the context was configured to
 * {@link BeanContextConfiguration#isTrackBeanDependencies() track dependencies}, which a development
 * launcher does and a production run does not. A development launcher uses it to decide, when the
 * application's classes change, which beans of the framework received an instance of a changed
 * class and how: through the constructor, in which case the bean must be recreated, or through a
 * field or method, in which case it can be injected again, or through a provider, in which case
 * nothing is needed because the provider resolves the bean anew on every call.</p>
 *
 * <p>The graph records every bean a bean received, whatever its scope. A prototype instance belongs
 * to the bean that received it and follows its lifecycle; what the prototype itself received is
 * recorded under the prototype's definition, so a singleton reached through a prototype is still a
 * transitive dependent of what it holds.</p>
 *
 * <p>A {@link BeanContext#createBeanRegistration(BeanDefinition) fresh registration} records what it received
 * under its definition, as any instance does, and a singleton's fresh instance shares those edges with the scoped
 * one until both are destroyed. What a bean resolves or creates through an injected {@link BeanDependencyResolver},
 * or a group the resolver opened, is recorded as received by that bean, with {@link InjectionKind#OTHER}.</p>
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
     * @param dependent The bean definition of the receiving bean
     * @return The dependencies, empty if none were recorded
     */
    Collection<BeanDependency> dependenciesOf(BeanDefinition<?> dependent);

    /**
     * The beans that received the given bean.
     *
     * @param dependency The bean definition of the received bean
     * @return The dependents, empty if none were recorded
     */
    Collection<BeanDependency> dependentsOf(BeanDefinition<?> dependency);

    /**
     * Every bean that received the given bean, or received a bean that received it, and so on, apart
     * from those that hold it only {@link BeanDependency#lazy() lazily}.
     *
     * @param dependency The bean definition of the received bean
     * @return The transitive dependents, in the order they were reached
     */
    Set<BeanDefinition<?>> transitiveDependentsOf(BeanDefinition<?> dependency);

    /**
     * Every bean the given bean received, or a bean it received did, and so on, apart from those held
     * only {@link BeanDependency#lazy() lazily}: what must stay alive for the bean to keep working.
     *
     * @param dependent The bean definition of the receiving bean
     * @return The transitive dependencies, in the order they were reached
     */
    Set<BeanDefinition<?>> transitiveDependenciesOf(BeanDefinition<?> dependent);

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
         * A constructor or factory method argument. The receiving bean holds the instance in a way that
         * cannot be replaced; it must be recreated when the dependency is.
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
         * Another injection point, such as an annotation member.
         */
        OTHER
    }

    /**
     * One recorded injection.
     *
     * @param dependent The definition of the bean that received the dependency; for a bean created per qualifier
     *                  (an {@code @EachBean} member) a delegate carrying that qualifier, so that the member is told apart
     * @param dependency The definition of the received bean
     * @param kind How the dependency was injected
     * @param lazy Whether the injection point is a provider, so the dependency is resolved on each use rather than held
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
    }
}
