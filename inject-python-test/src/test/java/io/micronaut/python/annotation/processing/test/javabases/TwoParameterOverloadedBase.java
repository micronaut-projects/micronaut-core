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
 * A Java base whose two-parameter overloads differ in the second parameter only, so a Python method
 * hinting the first parameter alone selects neither.
 */
public class TwoParameterOverloadedBase {

    public String join(String prefix, String value) {
        return prefix + value;
    }

    public String join(String prefix, List<String> values) {
        return prefix + String.join(",", values);
    }
}
