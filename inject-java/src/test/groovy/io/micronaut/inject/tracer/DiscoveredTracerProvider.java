/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.tracer;

import io.micronaut.context.BeanResolutionTracer;
import io.micronaut.context.BeanResolutionTracerProvider;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A provider registered in {@code META-INF/services}, whose tracer activates when discovery is enabled or for the
 * {@value #ENVIRONMENT} environment.
 */
public class DiscoveredTracerProvider implements BeanResolutionTracerProvider {

    public static final String ENVIRONMENT = "bean-tracer-test";
    /** The tracers created, in the order they were created. */
    public static final List<RecordingTracer> CREATED = new CopyOnWriteArrayList<>();
    /** The providers that created a tracer, in the order they created it. */
    public static final List<String> CREATED_BY = new CopyOnWriteArrayList<>();

    @Override
    public boolean isEnabled(Activation activation) {
        return activation.isDiscoveryEnabled() || activation.getEnvironmentNames().contains(ENVIRONMENT);
    }

    @Override
    public BeanResolutionTracer createTracer(Activation activation) {
        RecordingTracer tracer = new RecordingTracer();
        CREATED.add(tracer);
        CREATED_BY.add(getClass().getSimpleName());
        return tracer;
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
