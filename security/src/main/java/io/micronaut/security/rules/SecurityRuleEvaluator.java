/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.security.rules;

import io.micronaut.http.HttpRequest;
import io.reactivex.annotations.NonNull;
import io.reactivex.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates a {@link SecurityRule}s for a given request.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public interface SecurityRuleEvaluator {

    /**
     * @param request        The HTTP request
     * @param claims         The claims from the token. Null if not authenticated
     * @param matchAnyResult List of {@link SecurityRuleResult} to match.
     * @return Evaluation encapsulating the result and the first rule which matched any of the supplied results.
     */
    Optional<SecurityRuleEvaluation> findFirst(@NonNull HttpRequest<?> request,
                                               @Nullable Map<String, Object> claims,
                                               @NonNull List<SecurityRuleResult> matchAnyResult);
}
