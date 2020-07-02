/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.injectionpoint;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SomeBeanConsumer {

    private final SomeBean fromConstructor;

    @Inject
    @SomeAnn("two")
    SomeBean fromField;

    @Inject
    @SomeAnn("four")
    SomeType someType;

    private SomeBean fromMethod;

    public SomeBeanConsumer(@SomeAnn("one") SomeBean fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    public SomeBean getFromConstructor() {
        return fromConstructor;
    }

    public SomeBean getFromField() {
        return fromField;
    }

    @Inject
    public void setFromMethod(@SomeAnn("three") SomeBean fromMethod) {
        this.fromMethod = fromMethod;
    }

    public SomeBean getFromMethod() {
        return fromMethod;
    }

    public SomeType getSomeType() {
        return someType;
    }
}
