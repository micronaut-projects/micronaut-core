package io.micronaut.aop.around.noproxytarget;

import io.micronaut.aop.ByteBuddyRuntimeProxy;
import io.micronaut.aop.runtime.RuntimeProxy;
import jakarta.inject.Singleton;

@Singleton
@RuntimeProxy(value = ByteBuddyRuntimeProxy.class, proxyTarget = true)
public class ByteBuddyForceProxyTargetProxyingClass<A extends CharSequence> extends NoProxyTargetClass<A> {

}
