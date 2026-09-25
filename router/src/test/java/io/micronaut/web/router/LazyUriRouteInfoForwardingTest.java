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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LazyUriRouteInfo} answers like the {@link DefaultUrlRouteInfo} it builds: every method of
 * {@link UriRouteInfo}, including those of {@link RouteInfo}, {@link MethodBasedRouteInfo} and
 * {@link RequestMatcher}, that the built route implements in a class, rather than leaving it to
 * the default of the interface, is implemented by the lazy route, which forwards it or answers
 * it without building the route. A method added to those interfaces, abstract or a default the
 * built route overrides, fails this test until the lazy route handles it.
 */
class LazyUriRouteInfoForwardingTest {

    @Test
    void theLazyRouteImplementsEveryMethodTheBuiltRouteImplements() throws NoSuchMethodException {
        List<String> missing = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (Method method : UriRouteInfo.class.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            Method built = DefaultUrlRouteInfo.class.getMethod(method.getName(), method.getParameterTypes());
            if (built.getDeclaringClass().isInterface()) {
                // the default of the interface, computed with the methods the lazy route forwards
                continue;
            }
            checked.add(signature(method));
            Method lazy = LazyUriRouteInfo.class.getMethod(method.getName(), method.getParameterTypes());
            if (lazy.getDeclaringClass() != LazyUriRouteInfo.class) {
                missing.add(signature(method) + " implemented by " + built.getDeclaringClass().getSimpleName());
            }
        }
        assertTrue(missing.isEmpty(), "LazyUriRouteInfo does not implement: " + missing);
        // the methods of the router interfaces are checked, not only those of AnnotationMetadataProvider
        assertTrue(checked.contains("getTargetMethod()"), checked::toString);
        assertTrue(checked.contains("tryMatch(String)"), checked::toString);
        assertTrue(checked.contains("getExecutor(ThreadSelection)"), checked::toString);
    }

    @Test
    void theLazyRouteDoesNotForwardWhatTheInterfaceComputes() throws NoSuchMethodException {
        // a forward of a default the built route does not override adds nothing: keep the lazy route minimal
        List<String> redundant = new ArrayList<>();
        for (Method lazy : LazyUriRouteInfo.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(lazy.getModifiers()) || lazy.isSynthetic() || lazy.isBridge()) {
                continue;
            }
            Method method;
            try {
                method = UriRouteInfo.class.getMethod(lazy.getName(), lazy.getParameterTypes());
            } catch (NoSuchMethodException e) {
                // IndexedRoute, answered without building the route
                continue;
            }
            Method built = DefaultUrlRouteInfo.class.getMethod(method.getName(), method.getParameterTypes());
            if (built.getDeclaringClass().isInterface()) {
                redundant.add(signature(method));
            }
        }
        assertEquals(List.of(), redundant);
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .reduce((a, b) -> a + ", " + b)
            .map(parameters -> "(" + parameters + ")")
            .orElse("()");
    }
}
