package io.micronaut.http.server.netty;

import io.micronaut.http.server.netty.fuzzing.BufferLeakDetection;
import org.spockframework.runtime.extension.IGlobalExtension;
import org.spockframework.runtime.model.SpecInfo;

public class BufferLeakDetectionExtension implements IGlobalExtension {

    @Override
    public void visitSpec(SpecInfo spec) {
        spec.addSetupInterceptor(invocation -> {
            BufferLeakDetection.startTracking(invocation.getFeature().getName());
            invocation.proceed();
        });
        spec.addCleanupInterceptor(invocation -> {
            invocation.proceed();
            BufferLeakDetection.stopTrackingAndReportLeaks();
        });
    }
}
