/*
 * Copyright 2026 original authors
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
package io.micronaut.context.processor

import io.micronaut.context.annotation.Executable
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.concurrent.atomic.AtomicInteger

@MicronautTest
class MockBeanExecutableMethodProcessorSpec extends Specification {

    @Inject
    TestMethodProcessor methodProcessor

    void "processes a mock bean method once"() {
        expect:
        methodProcessor.invocations.get() == 1
    }

    @MockBean(Listener)
    Listener listener() {
        new TestListener()
    }

    class TestListener extends Listener {
        @Override
        void receive() {
            super.receive()
        }
    }

    @Singleton
    static class Listener {
        @TestMethod
        void receive() {
        }
    }

    @Singleton
    static class TestMethodProcessor implements ExecutableMethodProcessor<TestMethod> {
        final AtomicInteger invocations = new AtomicInteger()

        @Override
        <B> void process(BeanDefinition<B> beanDefinition, ExecutableMethod<B, ?> method) {
            invocations.incrementAndGet()
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Executable(processOnStartup = true)
    @interface TestMethod {
    }
}
