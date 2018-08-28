/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.JaegerTracer
import io.micronaut.context.ApplicationContext
import io.opentracing.Tracer
import io.opentracing.noop.NoopTracer
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class JaegerTracerFactorySpec extends Specification {


    void "test enable jaeger tracing"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run('tracing.jaeger.enabled':'true')
        Tracer tracer = applicationContext.getBean(Tracer)

        then:
        tracer != null
        tracer instanceof JaegerTracer

        cleanup:
        applicationContext.close()
    }

    void "test default tracing"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run()
        Tracer tracer = applicationContext.getBean(Tracer)

        then:
        tracer != null
        tracer instanceof NoopTracer

        cleanup:
        applicationContext.close()
    }
}
