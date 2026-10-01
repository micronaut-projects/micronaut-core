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
package io.micronaut.inject.injectionpoint.kind;

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Requires(property = "spec.name", value = "InjectionPointKindSpec")
@Singleton
public class KindConsumer {

    private final KindBean fromConstructor;

    @Inject
    KindBean fromField;

    private KindBean fromMethod;

    public KindConsumer(KindBean fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    @Inject
    public void setFromMethod(KindBean fromMethod) {
        this.fromMethod = fromMethod;
    }

    public KindBean getFromConstructor() {
        return fromConstructor;
    }

    public KindBean getFromField() {
        return fromField;
    }

    public KindBean getFromMethod() {
        return fromMethod;
    }
}
