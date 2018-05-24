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

import io.micronaut.cli.console.parsing.CommandLine

/**
 * Abstract implementation of the {@link Step} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractStep implements Step {
    ProfileCommand command
    Map<String, Object> parameters

    AbstractStep(ProfileCommand command, Map<String, Object> parameters) {
        this.command = command
        this.parameters = parameters
    }

    /**
     * Obtains details of the given flag if it has been set by the user
     *
     * @param name The name of the flag
     * @return The flag information, or null if it isn't set by the user
     */
    def flag(CommandLine commandLine, String name) {
        def value = commandLine?.undeclaredOptions?.get(name)
        return value ?: null
    }
}
