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
package io.micronaut.web.router;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A match of another router that does not have the raw values of its variables does not
 * substitute the decoded ones.
 */
class UriRouteMatchRawValuesTest {

    @Test
    void theDefaultDoesNotSubstituteTheDecodedValues() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getVariableValues" -> Map.of("path", "a/b");
            case "toString" -> "other match";
            default -> method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
        };
        UriRouteMatch<?, ?> match = (UriRouteMatch<?, ?>) Proxy.newProxyInstance(
            UriRouteMatch.class.getClassLoader(), new Class<?>[] {UriRouteMatch.class}, handler);

        assertThrows(UnsupportedOperationException.class, match::getRawVariableValues);
    }
}
