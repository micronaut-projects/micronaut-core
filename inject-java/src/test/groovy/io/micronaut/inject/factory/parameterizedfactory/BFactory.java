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
package io.micronaut.inject.factory.parameterizedfactory;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

@Factory
public class BFactory {
    String name = "fromFactory";
    boolean postConstructCalled = false;
    boolean getCalled = false;
    @Inject
    private A fieldA;
    @Inject protected A anotherField;
    @Inject A a;
    private A methodInjected;

    @Inject private void injectMe(A a) {
        methodInjected = a;
    }
    A getFieldA() {
        return fieldA;
    }

    A getAnotherField() {
        return anotherField;
    }

    A getMethodInjected() {
        return methodInjected;
    }

    @PostConstruct
    void init() {
        assertState();
        postConstructCalled = true;
        name = name.toUpperCase();
    }

    @Singleton
    B get() {
        assert postConstructCalled : "post construct should have been called";
        assertState();

        getCalled = true;
        B b = new B();
        b.setName(name);
        return b;
    }

    @Prototype
    C buildC(B b, @Parameter int count) {
        return new C(b, count);
    }

    private void assertState() {
        assert fieldA != null: "private fields should have been injected first";
        assert anotherField != null: "protected fields should have been injected field";
        assert a != null: "public properties should have been injected first";
        assert methodInjected != null: "methods should have been injected first";
    }
}
