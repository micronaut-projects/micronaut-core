package io.micronaut.http.server.netty;

import io.micronaut.http.tck.netty.TestLeakDetector;
import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.model.SpecInfo;

public class BufferLeakDetectionExtension implements IGlobalExtension {
    static {
        TestLeakDetector.init();
    }

    @Override
    public void visitSpec(SpecInfo spec) {
        spec.addSetupInterceptor(invocation -> {
            TestLeakDetector.startTracking(invocation.getFeature().getName());
            invocation.proceed();
        });
        spec.addCleanupInterceptor(invocation -> {
            invocation.proceed();
            TestLeakDetector.stopTrackingAndReportLeaks();
        });
    }
}
