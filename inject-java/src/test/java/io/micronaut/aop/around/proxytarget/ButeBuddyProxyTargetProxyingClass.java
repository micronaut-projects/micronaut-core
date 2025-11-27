package io.micronaut.aop.around.proxytarget;

import io.micronaut.aop.ByteBuddyRuntimeProxy;
import io.micronaut.aop.runtime.RuntimeProxy;
import jakarta.inject.Singleton;

@Singleton
@RuntimeProxy(ByteBuddyRuntimeProxy.class)
public class ButeBuddyProxyTargetProxyingClass<A extends CharSequence> extends ProxyTargetClass<A> {

}
