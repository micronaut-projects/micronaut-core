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
package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.BulkheadRegistry
import io.micronaut.retry.exception.BulkheadFullException
import io.micronaut.retry.exception.TimeLimitExceededException
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

class BulkheadTimeLimitSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()
    ReportService service = context.getBean(ReportService)

    void 'a bulkhead refuses a call beyond its limit'() {
        when: 'two calls run and a third comes'
        CompletableFuture<String> first = service.render('first')
        service.render('second')
        service.render('third').get(5, TimeUnit.SECONDS)

        then: 'it fails without the method being called'
        ExecutionException e = thrown()
        e.cause instanceof BulkheadFullException
        !service.isStarted('third')

        when: 'a call ends'
        service.finish('first')
        service.render('fourth')

        then: 'its place goes to the next'
        first.get(5, TimeUnit.SECONDS) == 'first done'
        service.isStarted('fourth')
    }

    void 'methods of one name share the places and wait for one'() {
        given:
        BulkheadRegistry registry = context.getBean(BulkheadRegistry)

        when:
        CompletableFuture<String> stored = service.store('stored')
        CompletableFuture<String> loaded = service.load('loaded')

        then: 'the second waits for the one place of the bulkhead'
        !service.isStarted('loaded')
        registry.findSnapshot('archive').get().active() == 1
        registry.findSnapshot('archive').get().waiting() == 1

        when:
        service.finish('stored')

        then:
        stored.get(5, TimeUnit.SECONDS) == 'stored done'
        service.isStarted('loaded')

        when:
        service.finish('loaded')

        then:
        loaded.get(5, TimeUnit.SECONDS) == 'loaded done'
    }

    void 'a time limit fails a call that takes too long'() {
        when:
        service.export('late').get(5, TimeUnit.SECONDS)

        then:
        ExecutionException e = thrown()
        e.cause instanceof TimeLimitExceededException

        when:
        CompletableFuture<String> inTime = service.export('in time')
        service.finish('in time')

        then:
        inTime.get(5, TimeUnit.SECONDS) == 'in time done'
    }
}
