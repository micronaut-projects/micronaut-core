/*
 * Copyright 2018 original authors
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
package io.micronaut.tracing.brave

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.tracing.brave.sender.HttpClientSender
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.Tracer
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class BraveTracerFactorySpec extends Specification {
    void "test brave tracer configuration no endpoint present"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"The tracer is obtained"
        context.getBean(Tracer)


        then:"It is present"
        thrown(NoSuchBeanException)

    }

    void "test brave tracer configuration"() {
        given:
        ApplicationContext context = ApplicationContext.run('tracing.brave.http.endpoint':HttpClientSender.Builder.DEFAULT_ENDPOINT)

        when:"The tracer is obtained"
        Tracer tracer = context.getBean(Tracer)


        then:"It is present"
        tracer != null
    }
}
