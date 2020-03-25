/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.profile.commands.factory

import groovy.transform.CompileStatic
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileCommand

/**
 * Uses the service registry pattern to locate commands
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ServiceCommandFactory implements CommandFactory {
    @Override
    Collection<Command> findCommands(Profile profile, boolean inherited) {
        if (inherited) return Collections.emptyList()
        ServiceLoader.load(Command).findAll() { Command cmd ->
            cmd instanceof ProfileCommand
        }
    }
}
