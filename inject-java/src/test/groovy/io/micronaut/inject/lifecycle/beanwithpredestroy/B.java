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
package io.micronaut.inject.lifecycle.beanwithpredestroy;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.Closeable;

@Singleton
public class B implements Closeable {

    boolean noArgsDestroyCalled = false;
    boolean injectedDestroyCalled = false;

    @Inject
    protected A another;
    private A a;

    @Inject
    void setA(A a ) {
        this.a = a;
    }

    A getA() {
        return a;
    }

    @PreDestroy
    public void close() {
        noArgsDestroyCalled = true;
    }

    @PreDestroy
    void another(C c) {
        if(c != null) {
            injectedDestroyCalled = true;
        }
    }
}
