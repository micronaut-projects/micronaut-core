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

/**
 * A Java base declaring two same-arity overloads of one name where the first delegates to the
 * second, the shape of the reactive gRPC service bases: a subclass overrides the overload it
 * wants and the other keeps the inherited implementation.
 */
public class OverloadedBase {

    public String render(String value) {
        return render(List.of(value));
    }

    public String render(List<String> values) {
        return "base:" + String.join(",", values);
    }

    public String describe(String value) {
        return "described:" + render(value);
    }
}
