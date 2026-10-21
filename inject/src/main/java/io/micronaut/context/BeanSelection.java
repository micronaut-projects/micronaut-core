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

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The rules that pick one bean definition among several candidates of a type, shared by the lookups of
 * {@link DefaultBeanContext} and {@link BeanDefinitionRegistry#selectAmong} so that the two cannot drift.
 *
 * @since 5.3.0
 */
@Internal
final class BeanSelection {

    private BeanSelection() {
    }

    /**
     * Applies the rules in their precedence and stops at the first that leaves one candidate: the primary
     * candidates, the customizer, the candidates that are not secondary, the order, the default
     * implementation and the exact type.
     *
     * @param beanType   The requested type, or {@code null} when it is not known, which skips the customizer
     *                   and the exact type
     * @param qualifier  The qualifier the customizer is told of, or {@code null}
     * @param candidates The candidates
     * @param customizer The customizer of the context
     * @param <T>        The bean type
     * @return The one candidate the rules picked, or the candidates they left when none decided
     */
    static <T> List<BeanDefinition<T>> select(@Nullable Argument<T> beanType,
                                              @Nullable Qualifier<T> qualifier,
                                              Collection<BeanDefinition<T>> candidates,
                                              BeanResolutionCustomizer customizer) {
        if (candidates.size() < 2) {
            return List.copyOf(candidates);
        }
        List<BeanDefinition<T>> primary = candidates.stream()
            .filter(BeanDefinition::isPrimary)
            .toList();
        if (primary.size() == 1) {
            return primary;
        }
        // the customizer is handed the caller's own collection where no primary narrowed it, as it always was
        Collection<BeanDefinition<T>> considered = primary.isEmpty() ? candidates : primary;
        if (beanType != null) {
            Optional<BeanDefinition<T>> customResolved = customizer.resolveNonUniqueBean(beanType, qualifier, considered);
            if (customResolved.isPresent()) {
                return List.of(customResolved.get());
            }
        }
        List<BeanDefinition<T>> remaining = List.copyOf(considered);
        List<BeanDefinition<T>> notSecondary = remaining.stream()
            .filter(candidate -> !candidate.hasDeclaredStereotype(Secondary.class))
            .toList();
        if (notSecondary.size() == 1) {
            return notSecondary;
        }
        // When every candidate is @Secondary none of them is preferable to another, but lowering the
        // precedence of all of them shouldn't remove the ability to decide between them: carry on with
        // all of them so that the order and @DefaultImplementation still get a say.
        if (!notSecondary.isEmpty()) {
            remaining = notSecondary;
        }
        // pick the bean with the highest priority
        List<BeanDefinition<T>> ordered = new ArrayList<>(remaining);
        ordered.sort(OrderUtil.ORDERED_COMPARATOR);
        BeanDefinition<T> first = ordered.get(0);
        if (first.getOrder() != ordered.get(1).getOrder()) {
            return List.of(first);
        }
        Class<?> defaultImplementation = remaining.get(0).getDefaultImplementation();
        if (defaultImplementation != null) {
            for (BeanDefinition<T> candidate : remaining) {
                if (candidate.getBeanType().equals(defaultImplementation)) {
                    return List.of(candidate);
                }
            }
        }
        if (beanType != null) {
            List<BeanDefinition<T>> exactMatches = remaining.stream()
                .filter(candidate -> candidate.getBeanType() == beanType.getType())
                .toList();
            if (exactMatches.size() == 1) {
                return exactMatches;
            }
        }
        return remaining;
    }

    /**
     * The primary candidates, or all of them when none is primary.
     *
     * @param candidates The candidates
     * @param <T>        The bean type
     * @return The candidates the later rules apply to
     */
    static <T> List<BeanDefinition<T>> primaryCandidates(Collection<BeanDefinition<T>> candidates) {
        List<BeanDefinition<T>> primary = candidates.stream()
            .filter(BeanDefinition::isPrimary)
            .toList();
        return primary.isEmpty() ? List.copyOf(candidates) : primary;
    }
}
