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

package io.micronaut.cli.profile.commands

import groovy.transform.CompileStatic
import io.micronaut.cli.config.CodeGenConfig
import io.micronaut.cli.config.ConfigMap
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ProjectContext

/**
 * A command to find out information about the given profile
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ProfileInfoCommand extends ArgumentCompletingCommand implements ProfileRepositoryAware {

    public static final String NAME = 'profile-info'

    final String name = NAME
    final CommandDescription description = new CommandDescription(name, "Display information about a given profile")

    ProfileRepository profileRepository

    ProfileInfoCommand() {
        description.argument(name: "Profile Name", description: "The name or coordinates of the profile", required: true)
    }

    void setProfileRepository(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        def console = executionContext.console
        if (profileRepository == null) {
            console.error("No profile repository provided")
            return false
        } else {

            def profileName = executionContext.commandLine.remainingArgs[0]

            def profile = profileRepository.getProfile(profileName)
            if (profile == null) {
                console.error("Profile not found for name [$profileName]")
            } else {
                console.log("Profile: ${profile.name}")
                console.log('--------------------')
                console.log(profile.description)
                console.log('')
                console.log('Provided Commands:')
                console.log('--------------------')
                Iterable<Command> commands = findCommands(profile, console).toUnique { Command c -> c.name }.sort { it.name }

                for (cmd in commands) {
                    def description = cmd.description
                    console.log("* ${description.name} - ${description.description}")
                }
                console.log('')
                console.log('Provided Features:')
                console.log('--------------------')
                def features = profile.features.sort { it.name }

                for (feature in features) {
                    console.log("* ${feature.name} - ${feature.description}")
                }
            }
        }
        return true
    }

    @Override
    protected int complete(CommandLine commandLine, CommandDescription desc, List<CharSequence> candidates, int cursor) {
        List<String> lastOption = commandLine.remainingArgs
        def profileNames = profileRepository.allProfiles.collect() { Profile p -> p.name }
        if (!lastOption.empty) {
            String name = lastOption.get(0)
            profileNames = profileNames.findAll { String pn ->
                pn.startsWith(name)
            }.collect {
                "${it.substring(name.size())} ".toString()
            }
        }
        candidates.addAll profileNames
        return cursor
    }

    protected Iterable<Command> findCommands(Profile profile, MicronautConsole console) {
        def commands = profile.getCommands(new ProjectContext() {
            @Override
            MicronautConsole getConsole() {
                console
            }

            @Override
            File getBaseDir() {
                return new File(".")
            }

            @Override
            ConfigMap getConfig() {
                return new CodeGenConfig()
            }

            @Override
            String navigateConfig(String... path) {
                return config.navigate(path)
            }

            @Override
            def <T> T navigateConfigForType(Class<T> requiredType, String... path) {
                return (T) config.navigate(path)
            }
        })
        commands
    }
}
