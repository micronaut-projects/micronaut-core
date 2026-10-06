/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject.context.processor_legacy

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ExecutableMethodProcessorSpec extends Specification {

    void "test bean processors are invoked"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": "ExecutableMethodProcessorSpec"])

        expect:
        ctx.getBean(ProcessedAnnotationProcessor).beans.size() == 1
        ctx.getBean(ProcessedAnnotationProcessor).beans.first().beanType == SomeBean

        cleanup:
        ctx.close()
    }

    void "test a deprecated processor is given each method once"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": "ExecutableMethodProcessorSpec"])
        FizzProcessor processor = ctx.getBean(FizzProcessor)

        expect: "the processor is deprecated, so the legacy listener gives it every method of the annotated class"
        ctx.getBeanDefinition(FizzProcessor).hasAnnotation(Deprecated)

        and: "the startup pass does not give it those methods again"
        processor.processed.sort() == ["FizzOnClass.third", "FizzOnMethods.second"]

        cleanup:
        ctx.close()
    }

    void "test a deprecated processor is given each method once when the context is started again"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": "ExecutableMethodProcessorSpec"])
        FizzProcessor first = ctx.getBean(FizzProcessor)

        when:
        ctx.stop()
        ctx.start()
        FizzProcessor processor = ctx.getBean(FizzProcessor)

        then:
        !processor.is(first)
        processor.processed.sort() == ["FizzOnClass.third", "FizzOnMethods.second"]

        cleanup:
        ctx.close()
    }

    void "test a deprecated processor is given each method by the startup pass without events"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder(["spec.name": "ExecutableMethodProcessorSpec"])
            .eventsEnabled(false)
            .start()
        FizzProcessor processor = ctx.getBean(FizzProcessor)

        expect: "the legacy listener is not installed, so the startup pass gives it the methods of the annotated class"
        processor.processed.sort() == ["FizzOnClass.third", "FizzOnMethods.second"]

        cleanup:
        ctx.close()
    }
}
