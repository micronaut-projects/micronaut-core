package io.micronaut.inject.factory.nullreturn;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;

import edu.umd.cs.findbugs.annotations.Nullable;
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