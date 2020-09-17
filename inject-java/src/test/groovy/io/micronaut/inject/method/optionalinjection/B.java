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
package io.micronaut.inject.method.optionalinjection;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
public class B {
    Optional<A> a;

    Optional<C> c;

    @Inject
    public void setA(Optional<A> a) {
        this.a = a;
    }

    @Inject
    public void setC(Optional<C> c) {
        this.c = c;
    }

    A getA() {
        return this.a.get();
    }

    Optional<C> getC() {
        return c;
    }
}
