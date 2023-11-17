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
package io.micronaut.http.server.netty.shortcircuit;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.shortcircuit.MatchRule;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The different stages of route matching. These are matched in order. For example the
 * {@link #PATH} stage matches the request path to the candidate routes.<br>
 * This class also handles transforming the {@link MatchRule}s corresponding to each stage to a
 * {@link MatchPlan}.
 *
 * @author Jonas Konrad
 * @since 4.3.0
 */
@Internal
enum DiscriminatorStage {
    PATH {
        @Override
        <R> MatchPlan<R> planDiscriminate(Map<MatchRule, MatchPlan<R>> nextPlans) {
            // simplify already removed any overlaps between patterns and exact matches. only the exact matches are left
            Map<String, MatchPlan<R>> byString = nextPlans.entrySet().stream()
                .filter(e -> e.getKey() instanceof MatchRule.PathMatchExact)
                .collect(Collectors.toMap(e -> ((MatchRule.PathMatchExact) e.getKey()).path(), Map.Entry::getValue));
            return request -> {
                // this replicates AbstractNettyHttpRequest.getPath but not exactly :(
                URI uri;
                try {
                    uri = URI.create(request.uri());
                } catch (IllegalArgumentException iae) {
                    return ExecutionLeaf.indeterminate();
                }
                String rawPath = new QueryStringDecoder(uri).rawPath();
                if (!rawPath.isEmpty() && rawPath.charAt(rawPath.length() - 1) == '/') {
                    rawPath = rawPath.substring(0, rawPath.length() - 1);
                }
                MatchPlan<R> plan = byString.get(rawPath);
                return plan == null ? ExecutionLeaf.indeterminate() : plan.execute(request);
            };
        }
    },
    METHOD {
        @Override
        <R> MatchPlan<R> planDiscriminate(Map<MatchRule, MatchPlan<R>> nextPlans) {
            Map<HttpMethod, MatchPlan<R>> byMethod = coerceRules(MatchRule.Method.class, nextPlans)
                .entrySet().stream().collect(Collectors.toMap(e -> HttpMethod.valueOf(e.getKey().method().name()), Map.Entry::getValue));
            return request -> {
                MatchPlan<R> plan = byMethod.get(request.method());
                return plan == null ? ExecutionLeaf.indeterminate() : plan.execute(request);
            };
        }
    },
    CONTENT_TYPE {
        @Override
        <R> MatchPlan<R> planDiscriminate(Map<MatchRule, MatchPlan<R>> nextPlans) {
            Map<String, MatchPlan<R>> byContentType = coerceRules(MatchRule.ContentType.class, nextPlans)
                .entrySet().stream().collect(Collectors.toMap(e -> {
                    MediaType expectedType = e.getKey().expectedType();
                    return expectedType == null ? null : expectedType.getName();
                }, Map.Entry::getValue));
            return request -> {
                MatchPlan<R> plan = byContentType.get(request.headers().get(HttpHeaderNames.CONTENT_TYPE));
                return plan == null ? ExecutionLeaf.indeterminate() : plan.execute(request);
            };
        }
    },
    ACCEPT {
        @Override
        <R> MatchPlan<R> planDiscriminate(Map<MatchRule, MatchPlan<R>> nextPlans) {
            Map<MatchRule.Accept, MatchPlan<R>> rules = coerceRules(MatchRule.Accept.class, nextPlans);
            return request -> {
                List<MediaType> accept = MediaType.orderedOf(request.headers().getAll(HttpHeaderNames.ACCEPT));
                if (accept.isEmpty() || accept.contains(MediaType.ALL_TYPE)) {
                    if (rules.size() == 1) {
                        return rules.values().iterator().next().execute(request);
                    } else {
                        return ExecutionLeaf.indeterminate();
                    }
                }
                MatchPlan<R> match = null;
                for (Map.Entry<MatchRule.Accept, MatchPlan<R>> e : rules.entrySet()) {
                    for (MediaType producedType : e.getKey().producedTypes()) {
                        if (accept.contains(producedType)) {
                            if (match != null) {
                                return ExecutionLeaf.indeterminate();
                            }
                            match = e.getValue();
                        }
                    }
                }
                return match == null ? ExecutionLeaf.indeterminate() : match.execute(request);
            };
        }
    },
    SERVER_PORT {
        @Override
        <R> MatchPlan<R> planDiscriminate(Map<MatchRule, MatchPlan<R>> nextPlans) {
            // todo: not implemented yet
            return request -> ExecutionLeaf.indeterminate();
        }
    };

    /**
     * Create a {@link MatchPlan} that delegates to the next plan given in {@code nextPlans}
     * depending on which {@link MatchRule} in matched. The {@link MatchRule}s must be appropriate
     * for this stage.
     *
     * @param nextPlans The next match plans
     * @param <R>       The route type
     * @return The combined plan
     */
    @NonNull
    abstract <R> MatchPlan<R> planDiscriminate(@NonNull Map<MatchRule, MatchPlan<R>> nextPlans);

    private static <T, R> Map<T, MatchPlan<R>> coerceRules(Class<T> cl, Map<MatchRule, MatchPlan<R>> nextPlans) {
        assert nextPlans.keySet().stream().allMatch(cl::isInstance);
        //noinspection unchecked,rawtypes
        return (Map) nextPlans;
    }
}
