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
package io.micronaut.cli.profile.commands

import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.config.CodeGenConfig
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileCommand
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ProjectCommand
import io.micronaut.cli.profile.commands.factory.CommandFactory

/**
 * Registry of available commands
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class CommandRegistry {

    private static Map<String, Command> registeredCommands = [:]
    private static List<CommandFactory> registeredCommandFactories = []

    static {
        def commands = ServiceLoader.load(Command).iterator()

        while (commands.hasNext()) {
            Command command = commands.next()
            registeredCommands[command.name] = command
        }

        def commandFactories = ServiceLoader.load(CommandFactory).iterator()
        while (commandFactories.hasNext()) {
            CommandFactory commandFactory = commandFactories.next()

            registeredCommandFactories << commandFactory
        }
    }

    /**
     * Returns a command for the given name and repository
     *
     * @param name The command name
     * @param repository The {@link ProfileRepository} instance
     * @return A command or null of non exists
     */
    static Command getCommand(String name, ProfileRepository repository) {
        def command = registeredCommands[name]
        if (command instanceof ProfileRepositoryAware) {
            command.profileRepository = repository
        }
        return command
    }

    static Collection<Command> findCommands(ProfileRepository repository) {
        registeredCommands.values().collect() { Command cmd ->
            if (cmd instanceof ProfileRepositoryAware) {
                ((ProfileRepositoryAware) cmd).profileRepository = repository
            }
            return cmd
        }
    }

    static Collection<Command> findCommands(Profile profile, boolean inherited = false) {
        Collection<Command> commands = []

        for (CommandFactory cf in registeredCommandFactories) {
            def factoryCommands = cf.findCommands(profile, inherited)
            def condition = { Command c -> c.name == 'events' }
            def eventCommands = factoryCommands.findAll(condition)
            for (ec in eventCommands) {
                ec.handle(new MicronautCli.ExecutionContextImpl(new CodeGenConfig(profile.configuration)))
            }
            factoryCommands.removeAll(condition)
            commands.addAll factoryCommands
        }

        commands.addAll(registeredCommands.values()
                            .findAll { Command c -> (c instanceof ProjectCommand) || (c instanceof ProfileCommand) && ((ProfileCommand) c).profile == profile }
        )
        return commands
    }
}
