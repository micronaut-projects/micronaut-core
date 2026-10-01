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
package io.micronaut.jdt.beans;

import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

/**
 * A bean carrying nested annotation members, which used to be discarded when compiled with JDT.
 */
@Singleton
@Requirements({
    @Requires(property = "jdt.enabled", notEquals = "false"),
    @Requires(missingBeans = CharSequence.class)
})
public class NestedAnnotated {

    public String name() {
        return "nested";
    }
}
