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
package io.micronaut.python.annotation.processing.test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Java implementations of the interface generated for a Python abstract base class, created
 * reflectively because the interface only exists once the Python source is compiled.
 */
public final class JavaMathServices {

    private JavaMathServices() {
    }

    /**
     * An implementation whose {@code compute(int)} answers a fixed result and records its calls.
     *
     * @param type   The generated interface
     * @param result The result of {@code compute}
     * @return The implementation
     */
    public static Object create(Class<?> type, int result) {
        ScopedProxyInitCounter.increment();
        List<Integer> calls = new ArrayList<>();
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) ->
            switch (method.getName()) {
                case "compute" -> {
                    calls.add((Integer) args[0]);
                    yield result;
                }
                case "calls" -> List.copyOf(calls);
                case "toString" -> "JavaMathService(" + result + ")";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
