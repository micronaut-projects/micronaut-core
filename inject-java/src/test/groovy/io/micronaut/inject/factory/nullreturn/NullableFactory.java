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
package io.micronaut.inject.factory.nullreturn;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;

@Factory
public class NullableFactory {

    public int bCalls = 0;
    public int cCalls = 0;
    public int dCalls = 0;
    public int d2Calls = 0;
    public int d3Calls = 0;

    @Prototype
    A getA(@Parameter String name) {
        return null;
    }

    @Singleton
    @Named("one")
    B getBOne() {
        bCalls++;
        return new B("one");
    }

    @Singleton
    @Named("two")
    B getBTwo() {
        bCalls++;
        return new B("two");
    }

    @Singleton
    @Named("three")
    B getBThree() {
        bCalls++;
        return new B("three");
    }

    @Singleton
    @Named("four")
    B getBFour() {
        bCalls++;
        return null;
    }

    @EachBean(B.class)
    C getC(B b) {
        cCalls++;
        if (b.name.equals("three")) {
            return null;
        } else {
            return new C(b.name);
        }
    }

    @EachBean(C.class)
    D getD(C c) {
        dCalls++;
        if (c.name.equals("two")) {
            return null;
        } else {
            return new D();
        }
    }

    @EachBean(C.class)
    D2 getD2(@Nullable C c) {
        d2Calls++;
        if (c == null) {
            return null;
        }
        if (c.name.equals("two")) {
            return null;
        } else {
            return new D2();
        }
    }

    @EachBean(C.class)
    D3 getD3(@Parameter C c) {
        d3Calls++;
        if (c.name.equals("two")) {
            return null;
        } else {
            return new D3();
        }
    }

    @EachBean(D.class)
    E getE(D d, F f) {
        return new E();
    }

    @Singleton
    F getF() {
        return null;
    }


}

class A {}
class B {
    public final String name;

    B(String name) {
        this.name = name;
    }
}
class C {
    public final String name;

    C(String name) {
        this.name = name;
    }
}
class D {}
class D2 {}
class D3 {}
class E {}
class F {}