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

import groovy.transform.Canonical
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import jline.console.completer.Completer

/**
 * Describes a {@link Command}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Canonical
class CommandDescription {
    /**
     * The name of the command
     */
    String name
    /**
     * The description of the command
     */
    String description
    /**
     * The usage instructions for the command
     */
    String usage

    /**
     * Any names that should also map to this command
     */
    Collection<String> synonyms = []

    /**
     * A completer for the command
     */
    Completer completer = null

    private Map<String, CommandArgument> arguments = new LinkedHashMap<>()
    private Map<String, CommandArgument> flags = new LinkedHashMap<>()

    CommandDescription(String name) {
        this.name = name
    }

    CommandDescription(String name, String description) {
        this.name = name
        this.description = description
    }

    CommandDescription(String name, String description, String usage) {
        this.name = name
        this.description = description
        this.usage = usage
    }

    /**
     * Returns an argument for the given name or null if it doesn't exist
     * @param name The name
     * @return The argument or null
     */
    CommandArgument getArgument(String name) {
        arguments[name]
    }

    /**
     * Returns a flag for the given name or null if it doesn't exist
     * @param name The name
     * @return The argument or null
     */
    CommandArgument getFlag(String name) {
        flags[name]
    }

    /**
     * Arguments to the command
     */
    Collection<CommandArgument> getArguments() {
        arguments.values()
    }

    /**
     * Flags to the command. These differ as they are optional and are prefixed with a hyphen (Example -debug)
     */
    Collection<CommandArgument> getFlags() {
        flags.values()
    }

    /**
     * Adds a synonyms for this command
     *
     * @param synonyms The synonyms
     * @return This command description
     */
    CommandDescription synonyms(String... synonyms) {
        this.synonyms.addAll(synonyms)
        return this
    }
    /**
     * Sets the completer
     *
     * @param completer The class of the completer to set
     * @return The description instance
     */
    CommandDescription completer(Class<Completer> completer) {
        this.completer = completer.newInstance()
        return this
    }

    /**
     * Sets the completer
     *
     * @param completer The completer to set
     * @return The description instance
     */
    CommandDescription completer(Completer completer) {
        this.completer = completer
        return this
    }

    /**
     * Adds an argument for the given named arguments
     *
     * @param args The named arguments
     */
    @CompileDynamic
    CommandDescription argument(Map args) {
        def arg = new CommandArgument(args)
        def name = arg.name
        if (name) {
            arguments[name] = arg
        }
        return this
    }

    /**
     * Adds a flag for the given named arguments
     *
     * @param args The named arguments
     */
    @CompileDynamic
    CommandDescription flag(Map args) {
        def arg = new CommandArgument(args)
        def name = arg.name
        if (name) {
            arg.required = false
            flags[name] = arg
        }
        return this
    }
}
