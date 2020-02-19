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
package io.micronaut.runtime.executor

import grails.gorm.transactions.Transactional
import io.reactivex.Single
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.NonBlocking
import io.micronaut.inject.ExecutableMethod
import io.micronaut.scheduling.executor.ExecutorSelector
import spock.lang.Specification
import spock.lang.Unroll

import javax.inject.Singleton
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ExecutorSelectorSpec extends Specification {

    @Unroll
    void "test executor selector for method #methodName"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("test")
        ExecutorSelector selector = applicationContext.getBean(ExecutorSelector)
        Optional<ExecutableMethod> method = applicationContext.findExecutableMethod(MyService, methodName)

        Optional<ExecutorService> executorService = selector.select(method.get(), threadSelection)

        expect:
        executorService.isPresent() == present

        cleanup:
        applicationContext.stop()

        where:
        methodName              | present
        "someMethod"            | true
        "someNonBlockingMethod" | false
        "someReactiveMethod"    | false
        "someFutureMethod"      | false
        "someStageMethod"       | false
    }


}

@Singleton
@Executable
class MyService {

    @Transactional
    void someMethod() {

    }

    @NonBlocking
    void someNonBlockingMethod() {

    }

    Single someReactiveMethod() {}

    CompletableFuture someFutureMethod() {}

    CompletionStage someStageMethod() {}
}

