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
package io.micronaut.python.annotation.processing.test.specifications;

import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.type.Argument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Implements the repository methods over an in-memory list of names, dispatching on the declared
 * parameter type of the invoked method like a data access framework does.
 */
public class SpecificationInterceptor implements MethodInterceptor<Object, Object> {

    private final List<String> people = new ArrayList<>(List.of("Denis", "Josh"));

    public List<String> people() {
        return people;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Argument<?>[] arguments = context.getArguments();
        Object[] values = context.getParameterValues();
        String method = context.getMethodName();
        if (arguments.length == 0) {
            return (long) people.size();
        }
        Class<?> type = arguments[0].getType();
        Object value = values[0];
        if (Iterable.class.isAssignableFrom(type)) {
            return (int) StreamSupport.stream(((Iterable<Object>) value).spliterator(), false).count();
        }
        if (type == PredicateSpec.class) {
            PredicateSpec<String> spec = (PredicateSpec<String>) value;
            List<String> matching = people.stream().filter(name -> spec.test(name, people.size())).toList();
            if (method.equals("deleteAll")) {
                people.removeAll(matching);
                return matching.size();
            }
            return method.equals("count") ? (Object) (long) matching.size() : matching.stream().findFirst();
        }
        if (type == QuerySpec.class) {
            QuerySpec<String> spec = (QuerySpec<String>) value;
            return people.stream().filter(name -> spec.test(name, "query", people.size())).findFirst()
                .map(name -> name + " by " + spec.name());
        }
        if (type == UpdateSpec.class) {
            UpdateSpec<String> spec = (UpdateSpec<String>) value;
            int updated = 0;
            for (int i = 0; i < people.size(); i++) {
                String replacement = spec.update(people.get(i), i, people.size());
                if (replacement != null) {
                    people.set(i, replacement);
                    updated++;
                }
            }
            return updated;
        }
        return Optional.empty();
    }
}
