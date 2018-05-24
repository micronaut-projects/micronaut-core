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
import io.micronaut.cli.profile.ProfileCommand
import io.micronaut.cli.profile.Step

/**
 * Dynamic creation of {@link Step} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class DefaultStepFactory implements StepFactory {

    Map<String, Class<? extends Step>> steps = [:]

    DefaultStepFactory() {
        steps.put("render", RenderStep)
        steps.put("execute", ExecuteStep)
        steps.put("mkdir", MkdirStep)
    }

    @Override
    Step createStep(String name, Command command, Map parameters) {
        if (command instanceof ProfileCommand) {
            return steps[name]?.newInstance(command, parameters)
        }
    }
}
