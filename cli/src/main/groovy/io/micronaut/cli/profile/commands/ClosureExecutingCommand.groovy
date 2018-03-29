package io.micronaut.cli.profile.commands

import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileCommand

/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * A command that executes a closure
 *
 * @author Graeme Rocher
 * @since 3.0
 */
class ClosureExecutingCommand implements ProfileCommand {
    String name
    Closure callable
    Profile profile

    ClosureExecutingCommand(String name, Closure callable) {
        this.name = name
        this.callable = callable
    }

    @Override
    CommandDescription getDescription() {
        new CommandDescription(name)
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        callable.call(executionContext)
    }
}
