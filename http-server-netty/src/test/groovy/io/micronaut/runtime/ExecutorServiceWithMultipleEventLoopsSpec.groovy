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
package io.micronaut.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import java.util.concurrent.ExecutorService

class ExecutorServiceWithMultipleEventLoopsSpec extends Specification {

    void "test ExecutorService service with @Context"() {
        when:
            ApplicationContext applicationContext = ApplicationContext.run([
                    'micronaut.netty.event-loops.default.num-threads': 1,
                    'micronaut.netty.event-loops.clients.num-threads': 1,
                    'micronaut.netty.event-loops.abc.num-threads': 1,
                    'micronaut.netty.event-loops.xyz.num-threads': 1,
            ])

        then:
            applicationContext.getBean(MyController1).service != null
            applicationContext.getBean(MyController2).service != null

        cleanup:
            applicationContext.close()
    }

    @Controller
    static class MyController1 {

        private final ExecutorService service;

        @Inject
        MyController1(@Named(TaskExecutors.SCHEDULED) ExecutorService service) {
            this.service = service
        }

        @Get("/ping")
        String ping() {
            return service.toString()
        }

    }

    @Context
    @Controller
    static class MyController2 {

        private final ExecutorService service;

        @Inject
        MyController2(@Named(TaskExecutors.SCHEDULED) ExecutorService service) {
            this.service = service
        }

        @Get("/ping")
        String ping() {
            return service.toString()
        }

    }

}
