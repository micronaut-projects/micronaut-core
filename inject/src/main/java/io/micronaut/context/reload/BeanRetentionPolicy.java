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
package io.micronaut.context.reload;

import io.micronaut.context.BeanDependencyGraph;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;

import java.util.Set;

/**
 * Decides which singletons survive a {@link ReloadStrategy#RESTART restart} of the application
 * context: they are not destroyed with the old context and are adopted by the new one.
 *
 * <p>Retaining a bean is for what is expensive to create and independent of the application's
 * classes: a connection pool, a client, a script engine. Policies are beans, consulted in
 * {@link Ordered order}, and each {@link #decide(BeanRegistration) decides} a singleton: it
 * {@link Decision#RETAIN retains} it, {@link Decision#REFUSE refuses} it, or
 * {@link Decision#ABSTAIN abstains}. A bean is retained when a policy retains it and none refuses
 * it, whatever their order, so that a module can veto what another policy, an
 * {@link io.micronaut.context.annotation.Retain} annotation or {@code micronaut.dev.retain} asks for:
 * a client that would instantiate a class of the application named by its configuration, which the
 * context cannot see. A refused bean is destroyed and created again by the new context, as any bean
 * that is not retained, and so is a retained bean that holds a refused one.</p>
 *
 * <p>The launcher then checks that the bean's own class is not stale and that, according to the
 * {@link BeanDependencyGraph}, nothing it received is stale either, and the context refuses a bean
 * that holds state bound to it; a policy cannot override these checks. A retained bean is dropped,
 * and created again by the new context, when configuration under one of the prefixes a policy that
 * retains it {@link #observedConfigurationPrefixes(BeanRegistration) declares} changed, or under the prefix of a
 * configuration bean in its closure: a {@code @ConfigurationProperties}, an {@code @EachProperty} entry or another
 * configuration reader that it, the factory that produced it or any bean it holds received.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
public interface BeanRetentionPolicy extends Ordered {

    /**
     * Decides whether the given singleton survives a restart.
     *
     * @param registration The bean's registration
     * @return {@link Decision#RETAIN} to retain it, {@link Decision#REFUSE} to keep it from being retained whatever
     * another policy decides, or {@link Decision#ABSTAIN} to leave it to the other policies
     */
    Decision decide(BeanRegistration<?> registration);

    /**
     * The configuration prefixes a change under which invalidates a retained bean this policy
     * {@link Decision#RETAIN retains}, so that a changed connection URL produces a new pool. The prefixes of the
     * configuration beans in the bean's closure are observed without being declared, so these only need to cover what
     * the bean reads outside configuration beans, such as a raw property value.
     *
     * @param registration The retained bean's registration
     * @return The prefixes, empty if only the configuration beans of its closure invalidate the bean
     */
    default Set<String> observedConfigurationPrefixes(BeanRegistration<?> registration) {
        return Set.of();
    }

    /**
     * What a policy decides for a singleton.
     */
    enum Decision {
        /**
         * The singleton survives the restart, unless a policy refuses it.
         */
        RETAIN,
        /**
         * The singleton does not survive the restart, whatever the other policies decide: it is destroyed with the
         * context and created again by the next one, as is a retained bean that holds it.
         */
        REFUSE,
        /**
         * The policy has no say on the singleton.
         */
        ABSTAIN
    }
}
