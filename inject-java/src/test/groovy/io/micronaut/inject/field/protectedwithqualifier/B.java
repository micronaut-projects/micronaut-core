/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.field.protectedwithqualifier;

import io.micronaut.inject.qualifiers.One;

import javax.inject.Inject;
import javax.inject.Named;

public  class B {
    @Inject
    @One
    protected A a;

    @Inject
    @Named("twoA")
    protected A a2;

    public A getA() {
        return a;
    }

    public A getA2() {
        return a2;
    }
}
