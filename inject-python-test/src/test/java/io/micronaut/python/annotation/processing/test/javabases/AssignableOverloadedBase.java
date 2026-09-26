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

import java.util.Collection;

/**
 * A Java base whose two same-arity overloads are both supertypes of one type, so a Python type hint
 * naming that type ({@code ArrayList}) is assignable to both and names neither exactly: the hint
 * selects no single overload and the class is rejected.
 */
public class AssignableOverloadedBase {

    public String accept(Collection<String> values) {
        return "collection:" + String.join(",", values);
    }

    public String accept(Iterable<String> values) {
        return "iterable:" + String.join(",", values);
    }
}
