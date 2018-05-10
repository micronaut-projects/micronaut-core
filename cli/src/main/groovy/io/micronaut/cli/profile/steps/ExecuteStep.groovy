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

import io.micronaut.cli.profile.AbstractStep
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandException
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.ProfileCommand

/**
 * A {@link io.micronaut.cli.profile.Step} that can execute another command
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class ExecuteStep extends AbstractStep {

    public static final String NAME = "execute"
    public static final String CLASS_NAME = "class"


    Command target


    ExecuteStep(ProfileCommand command, Map<String, Object> parameters) {
        super(command, parameters)

        try {
            String className = parameters.get(CLASS_NAME)
            def cmd = className ? Class.forName(className, true, Thread.currentThread().contextClassLoader)
                .newInstance() : null
            if (cmd instanceof Command) {
                if (cmd instanceof ProfileCommand) {
                    ((ProfileCommand) cmd).profile = command.profile
                }
                this.target = cmd
            } else {
                throw new CommandException("Invalid command class [$className] specified")
            }
        } catch (Throwable e) {
            throw new CommandException("Unable to create step for command [${command.name}] for parameters $parameters", e)
        }
    }

    @Override
    String getName() { NAME }

    @Override
    boolean handle(ExecutionContext context) {
        return target.handle(context)
    }
}
