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
package io.micronaut.inject.lifecycle.proxybeanwithpredestroy;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.io.Closeable;

@CustomScope
public class B implements Closeable {

    static int interceptCalled = 0;
    static int noArgsDestroyCalled = 0;
    static int injectedDestroyCalled = 0;

    private final D prototypeD;

    @Inject
    protected A another;
    private A a;

    public B(D prototypeD) {
        this.prototypeD = prototypeD;
    }

    @PostConstruct
    @PreDestroy
    public void intercept() {
        interceptCalled++;
    }

    @Inject
    void setA(A a ) {
        this.a = a;
    }

    A getA() {
        return a;
    }

    @Override
    @PreDestroy
    public void close() {
        noArgsDestroyCalled++;
    }

    @PreDestroy
    void another(C c) {
        if(c != null) {
            injectedDestroyCalled++;
        }
    }
}
