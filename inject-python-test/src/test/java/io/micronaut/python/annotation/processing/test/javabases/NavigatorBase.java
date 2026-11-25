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
package io.micronaut.python.annotation.processing.test.javabases;

import java.util.List;
import java.util.Map;

/**
 * A Java base with the generic overloads of browserless-test's {@code BaseBrowserlessTest}:
 * {@code navigate(Class<T>, Map)} next to {@code navigate(Class<T>, C)} whose type variable has
 * an intersection bound, and a method naming its type variable in two parameters, the shape of
 * {@code addListener(Class<T>, ComponentEventListener<T>)}, and parameterized parameters naming a
 * concrete type argument next to the variable or the variable twice, and type variables named by
 * the bounds of the method's type variables.
 */
public class NavigatorBase {

    public <T extends View> String navigate(Class<T> target, Map<String, String> parameters) {
        return "map:" + target.getSimpleName() + parameters;
    }

    public <C, T extends View & HasParameter<C>> String navigate(Class<T> target, C parameter) {
        return "parameter:" + target.getSimpleName() + "=" + parameter;
    }

    public <T extends View> String collect(Class<T> type, List<T> views) {
        return "collect:" + type.getSimpleName() + views.size();
    }

    public <T extends View> String index(Map<String, T> views) {
        return "index:" + views.size();
    }

    public <T extends View> String pairs(Map<T, T> views) {
        return "pairs:" + views.size();
    }

    public <T, U extends T> String narrowed(Class<T> type, U value) {
        return "narrowed:" + type.getSimpleName() + "=" + value;
    }

    public <T extends Comparable<T>> String compared(Class<T> type) {
        return "compared:" + type.getSimpleName();
    }

    public <T> String generated(java.util.function.IntFunction<T[]> generator) {
        return "generated:" + generator.apply(2).length;
    }

    /**
     * A view to navigate to.
     */
    public interface View {
    }

    /**
     * A view taking a parameter.
     *
     * @param <C> The parameter type
     */
    public interface HasParameter<C> {
    }

    /**
     * A view taking a string parameter.
     */
    public static final class Detail implements View, HasParameter<String> {
    }
}
