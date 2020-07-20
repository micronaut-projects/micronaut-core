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
package io.micronaut.inject.lifecycle.beaninitializingeventlistener;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class BFactory implements Provider<B> {
    String name = "original";
    boolean postConstructCalled = false;
    boolean getCalled = false;
    @Inject
    private A fieldA;
    @Inject protected A anotherField;
    @Inject A a;
    private A methodInjected;

    @Inject private Object injectMe(A a) {
        methodInjected = a;
        return methodInjected;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isPostConstructCalled() {
        return postConstructCalled;
    }

    public void setPostConstructCalled(boolean postConstructCalled) {
        this.postConstructCalled = postConstructCalled;
    }

    public boolean isGetCalled() {
        return getCalled;
    }

    public void setGetCalled(boolean getCalled) {
        this.getCalled = getCalled;
    }

    public void setFieldA(A fieldA) {
        this.fieldA = fieldA;
    }

    public void setAnotherField(A anotherField) {
        this.anotherField = anotherField;
    }

    public A getA() {
        return a;
    }

    public void setA(A a) {
        this.a = a;
    }

    public void setMethodInjected(A methodInjected) {
        this.methodInjected = methodInjected;
    }

    @PostConstruct
    void init() {
        postConstructCalled = true;
        name = name.toUpperCase();
    }

    @Override
    public B get() {
        getCalled = true;
        B b = new B();
        b.setName(name);
        return b;
    }
}
