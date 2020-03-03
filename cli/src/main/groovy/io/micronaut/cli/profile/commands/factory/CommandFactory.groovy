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

import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Profile

/**
 * Factory for the creation of {@link Command} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface CommandFactory {

    /**
     * Creates a command for the given name
     *
     * @param name The name of the command
     * @param profile The {@link Profile}
     * @param inherited Whether the profile passed is inherited (ie a parent profile)
     * @return A command or null if it wasn't possible to create one
     */
    Collection<Command> findCommands(Profile profile, boolean inherited)

}