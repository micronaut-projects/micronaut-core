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
package io.micronaut.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.DevelopmentMode
import io.micronaut.runtime.exceptions.ApplicationStartupException
import io.micronaut.runtime.server.watch.event.FileWatchRestartListener
import io.micronaut.scheduling.io.watch.FileWatchConfiguration
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MicronautDevelopmentModeSpec extends Specification {

    void "the launcher thread is released when the application is stopped by another component"() {
        given:
        ServerApplication.STARTED.set(null)
        AtomicReference<ApplicationContext> returned = new AtomicReference<>()
        Thread launcher = new Thread({
            returned.set(Micronaut.build()
                .properties('spec.name': 'MicronautDevelopmentModeSpec', 'spec.server': true)
                .start())
        }, "launcher")
        PollingConditions conditions = new PollingConditions(timeout: 20, delay: 0.1)

        when:
        launcher.start()

        then: "the launcher blocks because the application is a server"
        conditions.eventually {
            assert ServerApplication.STARTED.get() != null
            assert ServerApplication.STARTED.get().running
        }
        Thread.sleep(500)
        launcher.alive
        returned.get() == null

        when: "the application is stopped from elsewhere, as a development launcher would"
        ServerApplication.STARTED.get().applicationContext.stop()

        then: "the launcher returns and its monitor thread is gone"
        conditions.eventually {
            assert !launcher.alive
        }
        returned.get() != null
        conditions.eventually {
            assert !Thread.allStackTraces.keySet().any { it.name == "micronaut-shutdown-monitor-thread" && it.alive }
        }
    }

    void "a startup failure in development mode is reported instead of exiting the JVM"() {
        when:
        Micronaut.build()
            .deduceEnvironment(false)
            .properties(
                'spec.name': 'MicronautDevelopmentModeSpec',
                'spec.failing': true,
                (DevelopmentMode.PROPERTY): true)
            .start()

        then:
        def e = thrown(ApplicationStartupException)
        e.cause instanceof IllegalStateException
        e.cause.message == "start failed"
    }

    void "the file watch restart listener is not active in development mode"() {
        given:
        ApplicationContext context = ApplicationContext.run(
            'spec.name': 'MicronautDevelopmentModeSpec',
            'spec.server': true,
            (FileWatchConfiguration.RESTART): true,
            (DevelopmentMode.PROPERTY): devMode)

        expect:
        context.containsBean(FileWatchRestartListener) == listenerPresent
        DevelopmentMode.isEnabled(context.environment) == !listenerPresent

        cleanup:
        context.close()

        where:
        devMode | listenerPresent
        false   | true
        true    | false
        'TRUE'  | false
        'no'    | true
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MicronautDevelopmentModeSpec')
    @Requires(property = 'spec.server', value = 'true')
    static class ServerApplication implements EmbeddedApplication<ServerApplication> {
        static final AtomicReference<ServerApplication> STARTED = new AtomicReference<>()
        private final ApplicationContext applicationContext
        private final ApplicationConfiguration applicationConfiguration
        private final AtomicBoolean running = new AtomicBoolean()

        ServerApplication(ApplicationContext applicationContext, ApplicationConfiguration applicationConfiguration) {
            this.applicationContext = applicationContext
            this.applicationConfiguration = applicationConfiguration
        }

        @Override
        ApplicationContext getApplicationContext() { applicationContext }

        @Override
        ApplicationConfiguration getApplicationConfiguration() { applicationConfiguration }

        @Override
        boolean isRunning() { running.get() && applicationContext.running }

        @Override
        boolean isServer() { true }

        @Override
        ServerApplication start() {
            running.set(true)
            STARTED.set(this)
            return this
        }

        @Override
        ServerApplication stop() {
            running.set(false)
            return this
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MicronautDevelopmentModeSpec')
    @Requires(property = 'spec.failing', value = 'true')
    static class FailingApplication implements EmbeddedApplication<FailingApplication> {
        private final ApplicationContext applicationContext
        private final ApplicationConfiguration applicationConfiguration

        FailingApplication(ApplicationContext applicationContext, ApplicationConfiguration applicationConfiguration) {
            this.applicationContext = applicationContext
            this.applicationConfiguration = applicationConfiguration
        }

        @Override
        ApplicationContext getApplicationContext() { applicationContext }

        @Override
        ApplicationConfiguration getApplicationConfiguration() { applicationConfiguration }

        @Override
        boolean isRunning() { false }

        @Override
        FailingApplication start() {
            throw new IllegalStateException("start failed")
        }

        @Override
        FailingApplication stop() { this }
    }
}
