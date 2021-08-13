package io.micronaut.http.client

import groovy.transform.CompileStatic
import io.netty.util.ResourceLeakDetector
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.SpecInfo
import reactor.core.publisher.Hooks

import java.util.concurrent.atomic.AtomicBoolean

@CompileStatic
class GlobalSpockExtension implements IGlobalExtension {

    //static AtomicBoolean leaksDetected = new AtomicBoolean(false)

    @Override
    void start() {
        Hooks.onOperatorDebug()
        Hooks.enableContextLossTracking()
        //System.setProperty("io.netty.leakDetection.acquireAndReleaseOnly", "true")
        //System.setProperty("io.netty.customResourceLeakDetector", "io.micronaut.http.client.GlobalSpockExtension\$CustomLeakDetector")
        //ResourceLeakDetector.level = ResourceLeakDetector.Level.PARANOID
    }

    /*
    @Override
    void visitSpec(SpecInfo specInfo) {
        specInfo.addCleanupInterceptor(new IMethodInterceptor() {
            @Override
            void intercept(IMethodInvocation invocation) throws Throwable {
                System.gc()
                if (leaksDetected.getAndSet(false)) {
                    throw new RuntimeException("Netty leaks detected")
                } else {
                    invocation.proceed()
                }
            }
        })
    }

    @Override
    void stop() {
        System.clearProperty("io.netty.customResourceLeakDetector")
        System.clearProperty("io.netty.leakDetection.acquireAndReleaseOnly")
    }

    static class CustomLeakDetector extends ResourceLeakDetector {

        CustomLeakDetector(Class resourceType, int samplingInterval) {
            super(resourceType, samplingInterval)
        }

        CustomLeakDetector(Class<?> resourceType, int samplingInterval, long maxActive) {
            super(resourceType, samplingInterval, maxActive)
        }

        @Override
        protected void reportUntracedLeak(String resourceType) {
            super.reportUntracedLeak(resourceType)
            leaksDetected.set(true)
        }

        @Override
        protected void reportTracedLeak(String resourceType, String records) {
            super.reportTracedLeak(resourceType, records)
            leaksDetected.set(true)
        }
    }
    */
}
