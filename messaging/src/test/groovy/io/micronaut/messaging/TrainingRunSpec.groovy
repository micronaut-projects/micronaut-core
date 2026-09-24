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
package io.micronaut.messaging

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.Micronaut
import io.micronaut.runtime.event.ApplicationShutdownEvent
import io.micronaut.runtime.event.ApplicationStartupEvent
import io.micronaut.runtime.event.annotation.EventListener
import jakarta.inject.Singleton
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * A messaging application is a server application, so {@link Micronaut#start()} waits for a
 * shutdown unless the training run switch ({@link ApplicationConfiguration#TRAINING_ENABLED}) is on.
 */
class TrainingRunSpec extends Specification {
    static final String SPEC_NAME = 'TrainingRunSpec'

    void setup() {
        LifecycleRecorder.EVENTS.clear()
    }

    @Timeout(30)
    void "a training run stops a messaging application instead of waiting for a shutdown"() {
        when:
        ApplicationContext context = Micronaut.build()
                .environments(Environment.TEST)
                .banner(false)
                .properties(['spec.name': SPEC_NAME, (ApplicationConfiguration.TRAINING_ENABLED): 'true'])
                .start()

        then:
        !context.running
        LifecycleRecorder.EVENTS.first() == 'started: ' + MessagingApplication.name
        LifecycleRecorder.EVENTS.contains('stopped: ' + MessagingApplication.name)
    }

    void "a training run of a messaging application exits with status 0"() {
        given:
        List<String> command = [
                Path.of(System.getProperty('java.home'), 'bin', 'java').toString(),
                '-D' + ApplicationConfiguration.TRAINING_ENABLED + '=true',
                '-cp', System.getProperty('java.class.path'),
                Main.name
        ]
        StringBuilder output = new StringBuilder()

        when:
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start()
        Thread reader = process.consumeProcessOutputStream(output)
        boolean exited = process.waitFor(1, TimeUnit.MINUTES)
        if (!exited) {
            process.destroyForcibly().waitFor()
        }
        reader.join(30_000)

        then:
        exited
        process.exitValue() == 0
        output.toString().contains('stopped: ' + MessagingApplication.name)

        cleanup:
        println output
    }

    /**
     * The application run by the child JVM: without the switch it would wait for a shutdown.
     */
    static class Main {
        static void main(String[] args) {
            Micronaut.build(args)
                    .banner(false)
                    .properties(['spec.name': SPEC_NAME])
                    .start()
        }
    }

    @Singleton
    @Requires(property = 'spec.name', value = SPEC_NAME)
    static class LifecycleRecorder {
        static final List<String> EVENTS = [].asSynchronized()

        @EventListener
        void onStartup(ApplicationStartupEvent event) {
            record('started: ' + event.source.getClass().name)
        }

        @EventListener
        void onShutdown(ApplicationShutdownEvent event) {
            record('stopped: ' + event.source.getClass().name)
        }

        private static void record(String event) {
            EVENTS << event
            println event
        }
    }
}
