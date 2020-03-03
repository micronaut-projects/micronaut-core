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
package io.micronaut.inject.value.singletonwithvalue;

import io.micronaut.context.annotation.Value;

import javax.inject.Singleton;

@Singleton
public class B {
    int fromConstructor;
    A a;

    public B(
        A a,
        @Value("${foo.bar}") int port
    ) {
        this.fromConstructor = port;
        this.a = a;
    }

    public int getFromConstructor() {
        return fromConstructor;
    }

    public void setFromConstructor(int fromConstructor) {
        this.fromConstructor = fromConstructor;
    }

    public A getA() {
        return a;
    }

    public void setA(A a) {
        this.a = a;
    }
}