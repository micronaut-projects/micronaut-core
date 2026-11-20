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
package io.micronaut.aop.cachedvalues;

import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.inject.annotation.builders.Sample;
import jakarta.inject.Singleton;

@Singleton
@Requires(property = "spec.name", value = "CachedValuesTest")
public class PlainBean {

    @Executable
    @Sample(name = "sample", count = 5)
    public String annotated() {
        return "annotated";
    }

    @Executable
    @Sample(name = "${cached.sample.name}")
    public String placeholderAnnotated() {
        return "placeholder";
    }

    @Executable
    public String argumentExpression(@Sample(name = "#{ 'argument' }") String value) {
        return value;
    }
}
