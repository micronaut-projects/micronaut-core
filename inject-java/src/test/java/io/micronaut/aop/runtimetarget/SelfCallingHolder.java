package io.micronaut.aop.runtimetarget;

import io.micronaut.context.annotation.Prototype;

/**
 * Calls the proxy it receives while it is itself being created, so that the target is resolved in that resolution.
 */
@Prototype
public class SelfCallingHolder {

    public SelfCallingHolder(SelfCallingTarget proxy) {
        SelfCallingTarget.proxy = proxy;
        proxy.ready();
    }
}
