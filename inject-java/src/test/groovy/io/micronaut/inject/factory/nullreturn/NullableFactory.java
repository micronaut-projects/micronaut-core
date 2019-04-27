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

    @Prototype
    A getA(@Parameter String name) {
        if (name.equals("null")) {
            return null;
        } else {
            return new A();
        }
    }

    @Singleton
    @Named("one")
    B getBOne() {
        bCalls++;
        return null;
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

    @EachBean(B.class)
    C getC(@Nullable B b) {
        cCalls++;
        if (b == null) {
            return null;
        }
        if (b.name.equals("three")) {
            return new C(b.name);
        } else {
            return null;
        }
    }

    @EachBean(C.class)
    D getD(@Nullable C c) {
        dCalls++;
        if (c == null) {
            return null;
        }
        return new D();
    }

    @Singleton
    E getE() {
        return null;
    }

    @Singleton
    F getF(E e) {
        return new F();
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
class E {}
class F {}