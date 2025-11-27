package io.micronaut.aop.around.noproxytarget;

import io.micronaut.aop.ByteBuddyRuntimeProxy;
import io.micronaut.aop.runtime.RuntimeProxy;
import jakarta.inject.Singleton;

@Singleton
@RuntimeProxy(ByteBuddyRuntimeProxy.class)
public class ByteBuddyNoProxyTargetWithConstructorProxyingClass<A extends CharSequence> extends NoProxyTargetClass<A> {

    private final Bar bar;

    public ByteBuddyNoProxyTargetWithConstructorProxyingClass(Bar bar) {
        this.bar = bar;
    }
}
