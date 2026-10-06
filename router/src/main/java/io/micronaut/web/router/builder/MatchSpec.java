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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.PathVariables;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which requests a route matches, besides its method and its URI template: its conditions, the
 * constraints of its path variables, and its order among the routes that match a request equally
 * well. A route, a group, see {@link RouteSpec}, and a direct route, see {@link DirectRouteSpec},
 * declare them with the same methods, declared once here: a way to constrain a route that is added
 * to this interface is available to the routes, the groups and the direct routes at once.
 *
 * <pre>{@code
 * routes.GET("/orders/{id}")
 *     .where(RouteCondition.header("X-Export", "csv"))
 *     .constrain("id", Long.class, id -> id > 0)
 *     .order(-1)
 *     .handle(csvHandler);
 * }</pre>
 *
 * <p>Each kind of route documents what it adds: an ordinary route, or a group, see
 * {@link RouteSpec#where(RouteCondition)}, {@link RouteSpec#constrain(Predicate)} and
 * {@link RouteSpec#order(int)}, and a direct route, which takes only the conditions the server
 * decides before it creates the request, see {@link DirectRouteSpec#where(RouteCondition)}.</p>
 *
 * @param <S> The type of the route or the group
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface MatchSpec<S extends MatchSpec<S>> permits RouteSpec, DirectRouteSpec {

    /**
     * Match the requests that meet a condition only: a request the condition rejects is answered
     * as if the route did not exist. {@link RouteCondition} builds conditions on the headers, the
     * query parameters, the cookies, the method, the host, the address and the time of the
     * request. Several conditions must all be met.
     *
     * @param condition The condition
     * @return The route or the group
     */
    S where(RouteCondition condition);

    /**
     * Constrain the path variables of the route: the route matches a request only when the
     * constraint accepts the path variables its URI template bound, otherwise it is not a match,
     * as if its URI template did not match. A constraint should be cheap, and must not have side
     * effects: it runs for every request the URI template of the route matches. A constraint that
     * throws an exception, e.g. a variable that does not convert, rejects the variables. Several
     * constraints must all pass.
     *
     * @param accepted Whether the path variables are accepted
     * @return The route or the group
     */
    S constrain(Predicate<? super PathVariables> accepted);

    /**
     * Constrain a path variable, see {@link #constrain(Predicate)}: a request whose variable has
     * no value, or a value the predicate does not accept, is not a match of the route.
     *
     * <pre>{@code
     * routes.GET("/files/{name}")
     *     .constrain("name", name -> !name.startsWith("."))
     *     .handle(filesHandler);
     * }</pre>
     *
     * @param variable The name of the variable
     * @param accepted Whether the value, as a string, is accepted
     * @return The route or the group
     */
    default S constrain(String variable, Predicate<? super String> accepted) {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(accepted, "accepted");
        return constrain(variables -> {
            String value = variables.findString(variable).orElse(null);
            return value != null && accepted.test(value);
        });
    }

    /**
     * Constrain a path variable converted to a type, see {@link #constrain(Predicate)}: a request
     * whose variable has no value, a value that does not convert to the type, or a value the
     * predicate does not accept, is not a match of the route.
     *
     * <pre>{@code
     * routes.GET("/orders/{id}")
     *     .constrain("id", Long.class, id -> id > 0)
     *     .handle(ordersHandler); // "/orders/abc" is not a match either
     * }</pre>
     *
     * @param variable The name of the variable
     * @param type     The type to convert the value to
     * @param accepted Whether the converted value is accepted
     * @param <T>      The type
     * @return The route or the group
     */
    default <T> S constrain(String variable, Class<T> type, Predicate<? super T> accepted) {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accepted, "accepted");
        // a conversion error throws, which rejects the variables
        return constrain(variables -> variables.find(variable, type).map(accepted::test).orElse(false));
    }

    /**
     * Constrain a path variable to a set of values, see {@link #constrain(Predicate)}: a request
     * whose variable has another value, or none, is not a match of the route. The values are
     * copied when the constraint is declared.
     *
     * <pre>{@code
     * routes.GET("/shops/{shop}/stock")
     *     .constrain("shop", Set.of("north", "south"))
     *     .handle(stockHandler);
     * }</pre>
     *
     * @param variable The name of the variable
     * @param values   The accepted values
     * @return The route or the group
     */
    default S constrain(String variable, Collection<String> values) {
        Objects.requireNonNull(values, "values");
        return constrain(variable, new ValueMatcher.OneOf(Set.copyOf(values), false));
    }

    /**
     * Constrain a path variable with a matcher of its value, see {@link #constrain(Predicate)}:
     * a request whose variable has a value the matcher does not match is not a match of the
     * route. A variable without a value is given to the matcher as an absent value, which only a
     * negated matcher, e.g. {@code present().negate()}, matches.
     *
     * <pre>{@code
     * routes.GET("/files/{name}")
     *     .constrain("name", ValueMatcher.startsWith(".").negate())
     *     .handle(filesHandler);
     * }</pre>
     *
     * @param variable The name of the variable
     * @param matcher  The matcher of the value, as a string
     * @return The route or the group
     */
    default S constrain(String variable, ValueMatcher matcher) {
        Objects.requireNonNull(variable, "variable");
        ValueMatcher normalized = RouteConditions.normalize(Objects.requireNonNull(matcher, "matcher"));
        return constrain(variables -> normalized.matches(variables.findString(variable).orElse(null)));
    }

    /**
     * Constrain path variables with matchers of their values, see
     * {@link #constrain(String, ValueMatcher)}: every variable must match its matcher.
     *
     * @param matchers The matchers of the values, by the name of the variable, copied
     * @return The route or the group
     */
    default S constrain(Map<String, ValueMatcher> matchers) {
        Objects.requireNonNull(matchers, "matchers");
        Map<String, ValueMatcher> normalized = new LinkedHashMap<>(matchers.size());
        matchers.forEach((variable, matcher) -> normalized.put(Objects.requireNonNull(variable, "variable"),
            RouteConditions.normalize(Objects.requireNonNull(matcher, "matcher"))));
        return constrain(variables -> {
            for (Map.Entry<String, ValueMatcher> entry : normalized.entrySet()) {
                if (!entry.getValue().matches(variables.findString(entry.getKey()).orElse(null))) {
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * Break a tie with other routes that match a request equally well: the route with the lowest
     * order answers it. The order never makes a less specific route win, and two routes left with
     * the same order make the request ambiguous, answered with {@code 400}.
     *
     * @param order The order, lower wins
     * @return The route or the group
     */
    S order(int order);
}
