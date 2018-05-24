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

package io.micronaut.cli.profile

import io.micronaut.cli.profile.commands.events.CommandEvents

/**
 * A command that executes multiple steps
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class MultiStepCommand implements ProfileCommand, CommandEvents {

    String name
    Profile profile
    int minArguments = 1

    MultiStepCommand(String name, Profile profile) {
        this.name = name
        this.profile = profile
    }
    /**
     * @return The steps that make up the command
     */
    abstract List<Step> getSteps()

    @Override
    boolean handle(io.micronaut.cli.profile.ExecutionContext context) {
        if (minArguments > 0 && (!context.commandLine.getRemainingArgs() || context.commandLine.getRemainingArgs().size() < minArguments)) {
            context.console.error("Expecting ${minArguments ? 'an argument' : minArguments + ' arguments'} to $name.")
            context.console.info("${description.usage}")
            return true
        }
        notify("${name}Start", context)
        for (AbstractStep step : getSteps()) {
            if (!step.handle(context)) {
                break
            }
        }
        notify("${name}End", context)
        true
    }
}
