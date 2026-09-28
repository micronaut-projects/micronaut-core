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
package docs.javabases;

import java.util.ArrayList;
import java.util.List;

/**
 * A Java base class in the shape of a module builder: the only public entry point is
 * {@link #install()}, the members a subclass calls are protected, and two of them declare a type
 * variable of their own. Extended by a Python class in the language guide.
 */
public abstract class AbstractBindingModule {

    private final List<String> bindings = new ArrayList<>();

    /**
     * The hook a subclass implements; protected and abstract.
     */
    protected abstract void configure();

    /**
     * A protected generic method taking a class, the shape of a binding DSL entry point.
     *
     * @param type The bound type
     * @param <T> The bound type
     * @return The name it was recorded under
     */
    protected <T> String bind(Class<T> type) {
        String name = type.getSimpleName();
        bindings.add(name);
        return name;
    }

    /**
     * A protected generic method whose type variable is the parameter type itself.
     *
     * @param value The bound value
     * @param <T> The value type
     * @return The name it was recorded under
     */
    protected <T> String bindValue(T value) {
        String name = String.valueOf(value);
        bindings.add(name);
        return name;
    }

    /**
     * A protected method without type variables.
     *
     * @return The bindings recorded so far
     */
    protected List<String> binder() {
        return bindings;
    }

    /**
     * A package-private method, which a subclass in another package cannot call.
     *
     * @return The number of bindings
     */
    int bindingCount() {
        return bindings.size();
    }

    /**
     * The public entry point, which the tests call more than once.
     *
     * @return The bindings the subclass configured
     */
    public List<String> install() {
        bindings.clear();
        configure();
        return List.copyOf(bindings);
    }
}
