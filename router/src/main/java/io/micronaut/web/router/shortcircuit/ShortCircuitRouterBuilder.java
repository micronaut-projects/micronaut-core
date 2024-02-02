/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.web.router.shortcircuit;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * This is a netty implementation of {@link ShortCircuitRouterBuilder}.
 * <p>
 * The principle of operation is that all routes are collected into a tree of {@link MatchNode}s.
 * Every level of the tree stands for one {@link DiscriminatorStage}. When all routes have been
 * collected, the {@link io.micronaut.web.router.UriRouteInfo}s are transformed to closures that
 * actually implement the routes by {@link io.micronaut.http.server.netty.RoutingInBoundHandler}.
 * Then a simplification algorithm is run to reduce the size of the decision tree. Finally, the
 * decision tree is transformed into a {@link MatchPlan} using the {@link DiscriminatorStage}s.
 *
 * @param <R> The route type. Initially {@link io.micronaut.web.router.UriRouteInfo} for the router
 *           to add routes. {@link io.micronaut.http.server.netty.RoutingInBoundHandler} then
 *           {@link #transform(BiFunction) transforms} the type to its own prepared route call
 *           object.
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Internal
public final class ShortCircuitRouterBuilder<R> {
    private final MatchNode topNode = new MatchNode();

    public void addRoute(MatchRule rule, R match) {
        addRoute(rule, new ExecutionLeaf.Route<>(match));
    }

    public void addLegacyRoute(MatchRule rule) {
        addRoute(rule, ExecutionLeaf.indeterminate());
    }

    public void addLegacyFallbackRouting() {
    }

    /**
     * Eagerly apply a transformation function to every {@link ExecutionLeaf.Route} in this builder.
     *
     * @param transform The transformation function
     * @return A builder with the transformed routes
     * @param <M> The transformed route type
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <M> ShortCircuitRouterBuilder<M> transform(@NonNull BiFunction<MatchRule, R, ExecutionLeaf<M>> transform) {
        topNode.eachNode(List.of(), (path, n) -> {
            if (n.leaf instanceof ExecutionLeaf.Route<R> route) {
                n.leaf = (ExecutionLeaf<R>) transform.apply(MatchRule.and(path), route.routeMatch());
            }
        });
        return (ShortCircuitRouterBuilder<M>) this;
    }

    public MatchPlan<R> plan() {
        topNode.simplify(0);
        return topNode.plan(0);
    }

    private void addRoute(MatchRule rule, ExecutionLeaf<R> executionLeaf) {
        // transform the input rule to DNF. Then add each part of the OR as a separate route.
        MatchRule.Or dnf = toDnf(rule);
        for (MatchRule conjunction : dnf.rules()) {
            if (conjunction instanceof MatchRule.And and) {
                addRoute(and.rules(), executionLeaf);
            } else {
                addRoute(List.of(conjunction), executionLeaf);
            }
        }
    }

    private void addRoute(List<? extends MatchRule> leafRules, ExecutionLeaf<R> executionLeaf) {
        // split up rules by discriminator
        Map<DiscriminatorStage, MatchRule> rulesByDiscriminator = new EnumMap<>(DiscriminatorStage.class);
        for (MatchRule rule : leafRules) {
            DiscriminatorStage disc;
            if (rule instanceof MatchRule.Method) {
                disc = DiscriminatorStage.METHOD;
            } else if (rule instanceof MatchRule.ContentType) {
                disc = DiscriminatorStage.CONTENT_TYPE;
            } else if (rule instanceof MatchRule.Accept) {
                disc = DiscriminatorStage.ACCEPT;
            } else if (rule instanceof MatchRule.PathMatchExact || rule instanceof MatchRule.PathMatchPattern) {
                disc = DiscriminatorStage.PATH;
            } else if (rule instanceof MatchRule.ServerPort) {
                disc = DiscriminatorStage.SERVER_PORT;
            } else {
                executionLeaf = ExecutionLeaf.indeterminate();
                continue;
            }
            MatchRule existing = rulesByDiscriminator.put(disc, rule);
            if (existing != null && !existing.equals(rule)) {
                // different rules of the same type. this can (probably) never match, just ignore this route.
                return;
            }
        }
        // add to decision tree
        MatchNode node = topNode;
        for (DiscriminatorStage stage : DiscriminatorStage.values()) {
            MatchRule rule = rulesByDiscriminator.get(stage);
            node = node.next.computeIfAbsent(rule, r -> new MatchNode());
        }
        node.leaf = merge(node.leaf, executionLeaf);
    }

    private static MatchRule.Or toDnf(MatchRule rule) {
        // https://en.wikipedia.org/wiki/Disjunctive_normal_form
        if (rule instanceof MatchRule.Or or) {
            return new MatchRule.Or(
                or.rules().stream()
                    .flatMap(r -> toDnf(r).rules().stream())
                    .toList()
            );
        } else if (rule instanceof MatchRule.And and) {
            List<List<MatchRule>> combined = List.of(List.of());
            for (MatchRule right : and.rules()) {
                List<List<MatchRule>> newCombined = new ArrayList<>();
                for (MatchRule rightPart : toDnf(right).rules()) {
                    for (List<MatchRule> left : combined) {
                        List<MatchRule> linked = new ArrayList<>(left.size() + 1);
                        linked.addAll(left);
                        if (rightPart instanceof MatchRule.And ra) {
                            linked.addAll(ra.rules());
                        } else {
                            linked.add(rightPart);
                        }
                        newCombined.add(linked);
                    }
                }
                combined = newCombined;
            }
            return new MatchRule.Or(combined.stream()
                .map(MatchRule::and)
                .toList());
        } else {
            // "Leaf" rule
            return new MatchRule.Or(List.of(rule));
        }
    }

    private static <R> ExecutionLeaf<R> merge(@Nullable ExecutionLeaf<R> a, @Nullable ExecutionLeaf<R> b) {
        if (a == null) {
            return b;
        } else if (b == null) {
            return a;
        } else if (a.equals(b)) {
            return a;
        } else {
            return ExecutionLeaf.indeterminate();
        }
    }

    private class MatchNode {
        /**
         * The next rules in the decision tree. A {@code null} key means that all possible requests
         * are matched.
         */
        final Map<MatchRule, MatchNode> next = new HashMap<>();
        /**
         * If this is not {@code null}, then the decision tree stops here and we have the final
         * result.
         */
        ExecutionLeaf<R> leaf = null;

        void simplify(int level) {
            // simplify children first
            for (MatchNode n : next.values()) {
                n.simplify(level + 1);
            }

            MatchNode anyMatchNode = next.get(null);
            if (anyMatchNode != null) {
                if (next.size() == 1) {
                    this.leaf = merge(this.leaf, anyMatchNode.leaf);
                } else {
                    // give up. we can't merge a wildcard match with the other branches properly
                    this.leaf = ExecutionLeaf.indeterminate();
                }
            } else {
                if (level == DiscriminatorStage.PATH.ordinal()) {
                    for (Map.Entry<MatchRule, MatchNode> entry : List.copyOf(next.entrySet())) {
                        if (entry.getKey() instanceof MatchRule.PathMatchPattern pattern) {
                            // remove any exact path matches that overlap with a pattern
                            next.keySet().removeIf(mr -> mr instanceof MatchRule.PathMatchExact exact && pattern.pattern().matcher(exact.path()).matches());
                            // refuse to match patterns.
                            entry.getValue().leaf = ExecutionLeaf.indeterminate();
                        }
                    }
                }
            }

            // at this point, the next branches do not overlap. remove any nodes that lead to indeterminate decisions.
            next.values().removeIf(n -> n.leaf instanceof ExecutionLeaf.Indeterminate<?>);
            // if there's no more available choices, indeterminate result.
            if (next.isEmpty() && leaf == null) {
                leaf = ExecutionLeaf.indeterminate();
            }

            if (this.leaf != null) {
                next.clear();
            }
        }

        void eachNode(List<MatchRule> rulePath, BiConsumer<List<MatchRule>, MatchNode> consumer) {
            consumer.accept(rulePath, this);
            for (Map.Entry<MatchRule, MatchNode> entry : next.entrySet()) {
                List<MatchRule> newRulePath;
                if (entry.getKey() == null) {
                    newRulePath = rulePath;
                } else {
                    newRulePath = new ArrayList<>(rulePath.size() + 1);
                    newRulePath.addAll(rulePath);
                    newRulePath.add(entry.getKey());
                }
                entry.getValue().eachNode(newRulePath, consumer);
            }
        }

        MatchPlan<R> plan(int level) {
            if (leaf != null) {
                return request -> leaf;
            }
            if (next.containsKey(null)) {
                assert next.size() == 1;
                return next.get(null).plan(level + 1);
            } else {
                DiscriminatorStage ourStage = DiscriminatorStage.values()[level];
                return ourStage.planDiscriminate(next.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().plan(level + 1))));
            }
        }
    }
}
