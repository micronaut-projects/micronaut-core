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
import io.micronaut.context.env.DevelopmentActive
import io.micronaut.context.env.DevelopmentInactive
import io.micronaut.context.env.DevelopmentMode
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.runtime.exceptions.ApplicationStartupException
import io.micronaut.runtime.server.watch.event.FileWatchRestartListener
import io.micronaut.scheduling.io.watch.FileWatchConfiguration
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.lang.ref.WeakReference
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

    void "the shutdown hook of an application that is not kept alive goes when its context is stopped"() {
        given: "an application that start() returns from with its shutdown hook registered"
        WeakReference<Application> stopped = startAndStopApplication()

        expect: "nothing keeps the stopped application reachable, the hook least of all: a stopped context holds no singleton"
        new PollingConditions(timeout: 20, delay: 0.2).eventually {
            System.gc()
            assert stopped.get() == null
        }
    }

    void "the shutdown hook goes when the context is stopped while the embedded application starts, before the hook is registered"() {
        given: "an application whose context another thread stops while it starts, as a development launcher may"
        WeakReference<StoppedWhileStarting> stopped = startApplicationStoppedWhileStarting()

        expect: "the hook registered after the context stopped is removed at once, and keeps nothing"
        new PollingConditions(timeout: 20, delay: 0.2).eventually {
            System.gc()
            assert stopped.get() == null
        }
    }

    private static WeakReference<StoppedWhileStarting> startApplicationStoppedWhileStarting() {
        ApplicationContext context = Micronaut.build()
            .deduceEnvironment(false)
            .properties('spec.name': 'MicronautDevelopmentModeSpec', 'spec.application': 'stopped-while-starting')
            .start()
        assert !context.running
        return new WeakReference<>(StoppedWhileStarting.LAST.getAndSet(null))
    }

    private static WeakReference<Application> startAndStopApplication() {
        ApplicationContext context = Micronaut.build()
            .deduceEnvironment(false)
            .properties('spec.name': 'MicronautDevelopmentModeSpec', 'spec.application': true)
            .start()
        Application application = context.getBean(Application)
        assert application.running
        context.stop()
        return new WeakReference<>(application)
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

    void "a startup failure that stopped the context is still reported instead of exiting the JVM in development mode"() {
        when: "the failing application stops the context first, which drops the environment's properties"
        Micronaut.build()
            .deduceEnvironment(false)
            .properties(
                'spec.name': 'MicronautDevelopmentModeSpec',
                'spec.failing': true,
                'spec.stop-context': true,
                (DevelopmentMode.PROPERTY): true)
            .start()

        then: "the decision to exit was taken while the environment ran, so the JVM is still here to see this"
        def e = thrown(ApplicationStartupException)
        e.cause.message == "start failed"
    }

    void "an ApplicationStartupException from outside the embedded application goes through the exit handlers"() {
        given:
        AtomicReference<Throwable> mapped = new AtomicReference<>()

        when: "a startup listener of the context fails, before any embedded application starts"
        Micronaut.build()
            .deduceEnvironment(false)
            .properties(
                'spec.name': 'MicronautDevelopmentModeSpec',
                'spec.failing-listener': true,
                (DevelopmentMode.PROPERTY): true)
            .mapError(ApplicationStartupException, { Throwable t -> mapped.set(t); 3 })
            .start()

        then: "it is handled like any other failure: mapped to an exit code, then reported since the JVM may not exit"
        def e = thrown(ApplicationStartupException)
        mapped.get() instanceof ApplicationStartupException
        mapped.get().message == "listener failed"
        e.cause.is(mapped.get())
    }

    void "the file watch restart listener is not active in development mode, which the development annotations follow"() {
        given:
        Map<String, Object> properties = [
            'spec.name': 'MicronautDevelopmentModeSpec',
            'spec.server': true,
            (FileWatchConfiguration.RESTART): true]
        if (devMode != null) {
            properties.put(DevelopmentMode.PROPERTY, devMode)
        }
        ApplicationContext context = ApplicationContext.run(properties)

        expect:
        context.containsBean(FileWatchRestartListener) == listenerPresent
        context.containsBean(DevelopmentOnly) == !listenerPresent
        context.containsBean(ProductionOnly) == listenerPresent
        DevelopmentMode.isEnabled(context.environment) == !listenerPresent

        cleanup:
        context.close()

        where:
        devMode | listenerPresent
        false   | true
        true    | false
        'TRUE'  | false
        'no'    | true
        'yes'   | true
        null    | true
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MicronautDevelopmentModeSpec')
    @DevelopmentActive
    static class DevelopmentOnly {
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MicronautDevelopmentModeSpec')
    @DevelopmentInactive
    static class ProductionOnly {
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
    @Requires(property = 'spec.failing-listener', value = 'true')
    static class FailingStartupListener implements ApplicationEventListener<StartupEvent> {
        @Override
        void onApplicationEvent(StartupEvent event) {
            throw new ApplicationStartupException("listener failed")
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MicronautDevelopmentModeSpec')
    @Requires(property = 'spec.application', value = 'stopped-while-starting')
    static class StoppedWhileStarting implements EmbeddedApplication<StoppedWhileStarting> {
        static final AtomicReference<StoppedWhileStarting> LAST = new AtomicReference<>()
        private final ApplicationContext applicationContext
        private final ApplicationConfiguration applicationConfiguration
        private final AtomicBoolean running = new AtomicBoolean()

        StoppedWhileStarting(ApplicationContext applicationContext, ApplicationConfiguration applicationConfiguration) {
            this.applicationContext = applicationContext
            this.applicationConfiguration = applicationConfiguration
        }

        @Override
        ApplicationContext getApplicationContext() { applicationContext }

        @Override
        ApplicationConfiguration getApplicationConfiguration() { applicationConfiguration }

        @Override
        boolean isRunning() { running.get() }

        @Override
        StoppedWhileStarting start() {
            running.set(true)
            LAST.set(this)
            // the context stops on another thread before start() returns and Micronaut registers the hook
            Thread stopper = new Thread(() -> applicationContext.stop())
            stopper.start()
            stopper.join()
            return this
        }

        @Override
        StoppedWhileStarting stop() {
            running.set(false)
            return this
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MicronautDevelopmentModeSpec')
    @Requires(property = 'spec.application', value = 'true')
    static class Application implements EmbeddedApplication<Application> {
        private final ApplicationContext applicationContext
        private final ApplicationConfiguration applicationConfiguration
        private final AtomicBoolean running = new AtomicBoolean()

        Application(ApplicationContext applicationContext, ApplicationConfiguration applicationConfiguration) {
            this.applicationContext = applicationContext
            this.applicationConfiguration = applicationConfiguration
        }

        @Override
        ApplicationContext getApplicationContext() { applicationContext }

        @Override
        ApplicationConfiguration getApplicationConfiguration() { applicationConfiguration }

        @Override
        boolean isRunning() { running.get() }

        @Override
        Application start() {
            running.set(true)
            return this
        }

        @Override
        Application stop() {
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
            if (applicationContext.getProperty('spec.stop-context', Boolean).orElse(false)) {
                // as the Netty server does when it cannot bind: it stops the context, and with it the environment
                applicationContext.stop()
            }
            throw new IllegalStateException("start failed")
        }

        @Override
        FailingApplication stop() { this }
    }
}
