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
package io.micronaut.docs.inject.tracer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanResolutionTracerProvider
import io.micronaut.context.env.Environment
import spock.lang.Specification

class TimingTracerSpec extends Specification {

    void "test register tracer"() {
        when:
        // tag::register[]
        TimingTracer tracer = new TimingTracer()
        ApplicationContext context = ApplicationContext.builder()
                .properties("spec.name": "TimingTracerSpec")
                .beanResolutionTracer(tracer) // <1>
                .start()
        context.getBean(Greeter)
        // end::register[]

        then:
        tracer.timings.containsKey(Greeter.name)

        cleanup:
        context.close()
    }

    void "test provider enabled in development"() {
        given:
        def provider = new TimingTracerProvider()

        expect:
        provider.isEnabled(activation([Environment.DEVELOPMENT] as Set))
        !provider.isEnabled(activation(["prod"] as Set))
    }

    private static BeanResolutionTracerProvider.Activation activation(Set<String> environmentNames) {
        new BeanResolutionTracerProvider.Activation() {
            @Override
            Set<String> getEnvironmentNames() {
                environmentNames
            }

            @Override
            boolean isDiscoveryEnabled() {
                false
            }
        }
    }
}
