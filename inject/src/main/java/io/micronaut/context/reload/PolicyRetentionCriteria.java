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

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.DefaultBeanContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * What a restart retains according to {@link BeanRetentionPolicy} beans: a singleton a policy
 * {@link BeanRetentionPolicy.Decision#RETAIN retains}, unless a change touched a configuration prefix one of the
 * policies that retain it declares for it, and unless a policy {@link BeanRetentionPolicy.Decision#REFUSE refuses} it,
 * whatever their order. The context that stops also releases it when the change touched the prefix of a configuration
 * bean in its closure, such as the {@code @ConfigurationProperties} it or its factory received, which no policy has to
 * declare. A refusal also keeps a bean that holds the refused one from being retained, and the context
 * that stops logs which policy refused.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Experimental
public final class PolicyRetentionCriteria implements DefaultBeanContext.RetentionCriteria {

    private final List<BeanRetentionPolicy> policies;
    private final Predicate<String> touchedPrefix;
    private final Predicate<Class<?>> replacedClass;

    /**
     * @param policies The policies, consulted in their order
     * @param touchedPrefix Whether the change behind the restart touched a configuration prefix
     * @param replacedClass Whether the restart replaces a class
     */
    public PolicyRetentionCriteria(Collection<? extends BeanRetentionPolicy> policies, Predicate<String> touchedPrefix,
                                   Predicate<Class<?>> replacedClass) {
        List<BeanRetentionPolicy> sorted = new ArrayList<>(policies);
        OrderUtil.sort(sorted);
        this.policies = List.copyOf(sorted);
        this.touchedPrefix = touchedPrefix;
        this.replacedClass = replacedClass;
    }

    /**
     * Whether a policy retains the singleton and the change touched none of the prefixes the policies that retain it
     * declare for it: a pool kept across a changed URL would be the old pool. A refusal is answered by
     * {@link #refusedBy(BeanRegistration)}.
     *
     * @param registration The singleton's registration
     * @return True to retain it
     */
    @Override
    public boolean retain(BeanRegistration<?> registration) {
        boolean retained = false;
        for (BeanRetentionPolicy policy : policies) {
            if (policy.decide(registration) != BeanRetentionPolicy.Decision.RETAIN) {
                continue;
            }
            retained = true;
            for (String prefix : policy.observedConfigurationPrefixes(registration)) {
                if (touchedPrefix.test(prefix)) {
                    return false;
                }
            }
        }
        return retained;
    }

    @Override
    public Set<String> invalidatedBy(BeanRegistration<?> registration) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (BeanRetentionPolicy policy : policies) {
            if (policy.decide(registration) == BeanRetentionPolicy.Decision.RETAIN) {
                prefixes.addAll(policy.observedConfigurationPrefixes(registration));
            }
        }
        return prefixes;
    }

    /**
     * Whether the change behind the restart touched the prefix of a configuration bean in the closure of a retained
     * singleton, which releases it although no policy declares the prefix.
     *
     * @param prefix The prefix
     * @return True when the change touched it
     */
    @Override
    public boolean touches(String prefix) {
        return touchedPrefix.test(prefix);
    }

    @Override
    public boolean isReplaced(Class<?> type) {
        return replacedClass.test(type);
    }

    /**
     * The first policy that refuses the singleton.
     *
     * @param registration The singleton's registration
     * @return The policy, or null when none refuses it
     */
    @Override
    public @Nullable BeanRetentionPolicy refusedBy(BeanRegistration<?> registration) {
        for (BeanRetentionPolicy policy : policies) {
            if (policy.decide(registration) == BeanRetentionPolicy.Decision.REFUSE) {
                return policy;
            }
        }
        return null;
    }
}
