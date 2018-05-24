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

package io.micronaut.cli.profile.steps

import groovy.transform.CompileStatic
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Step

/**
 * Registry of steps
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class StepRegistry {

    private static Collection<StepFactory> registeredStepFactories = []

    static {
        def stepFactories = ServiceLoader.load(StepFactory).iterator()

        while (stepFactories.hasNext()) {
            StepFactory stepFactory = stepFactories.next()
            registeredStepFactories << stepFactory
        }
    }

    /**
     * Looks up a {@link Step}
     *
     * @param name The name of the {@link Step}
     * @return A step or null if it doesn't exist for the given name
     */
    static Step getStep(String name, Command command, Map parameters) {
        if (!name) return null
        for (StepFactory sf in registeredStepFactories) {
            def step = sf.createStep(name, command, parameters)
            if (step) return step
        }
    }
}
