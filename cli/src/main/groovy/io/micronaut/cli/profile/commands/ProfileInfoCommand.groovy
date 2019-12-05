/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ProjectContext
import picocli.CommandLine
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec

/**
 * A command to find out information about the given profile
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@CommandLine.Command(name = 'profile-info', description = 'Display information about a given profile')
class ProfileInfoCommand extends ArgumentCompletingCommand implements ProfileRepositoryAware {

    public static final String NAME = 'profile-info'

    final String name = NAME

    @Parameters(arity = "1", paramLabel = "PROFILE-NAME", description = "The name or coordinates of the profile",
                completionCandidates = ProfileCompletionCandidates)
    String profileName

    @Option(names = ['-e', '--enhancedInfo'], paramLabel = 'ENHANCED-INFO', description = 'Display additional information about what is included in a profile.')
    boolean enhancedInfo

    @Mixin
    CommonOptionsMixin commonOptionsMixin

    ProfileRepository profileRepository

    ProfileInfoCommand() { }

    // Implementation note: this Command is first created and registered in the CommandRegistry.
    // At that point, the `setProfileRepository` method is called, but we cannot initialize
    // the completion candidates for this command yet, because the commandSpec is still null.
    //
    // When `setCommandSpec` is called by picocli, we can read from the profileRepository
    // to initialize the profile and feature completion candidates for this command.
    @Spec
    void setCommandSpec(CommandSpec commandSpec) {
        super.setCommandSpec(commandSpec)
        ProfileCompletionCandidates.updateCommandArguments(commandSpec, profileRepository)
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

            def profile = profileRepository.getProfile(profileName)
            if (profile == null) {
                console.error("Profile not found for name [$profileName]")
            } else {
                console.addStatus("Profile: ${profile.name}")
                console.log('--------------------')
                console.log(profile.description)
                console.log('')
                def defaultFeaturesList = profile.defaultFeatures.toList()
                def requiredFeaturesList = profile.requiredFeatures.toList()
                if (enhancedInfo) {
                    if (defaultFeaturesList) {
                        console.log('--------------------')
                        console.addStatus("Default Features:")
                        defaultFeaturesList.each {
                            console.log("  ${it.name}")
                        }
                    }

                    if (requiredFeaturesList) {
                        console.addStatus("Required Features:")
                        requiredFeaturesList.each {
                            console.log("  ${it.name}")
                        }
                    }
                }
                console.addStatus('Provided Commands:')
                console.log('--------------------')
                Iterable<Command> commands = findCommands(profile, console).toUnique { Command c -> c.name }.sort { it.name }
                if (!commands.empty) {
                    int padding = commands.collect { it.name }.max { it.length() }.length()
                    int width = Math.min(padding, commands.collect { it.name }.sort { it.length() }.last().length())
                    String separator = String.format('%n').padRight(width) // in case of multi-line command description
                    for (cmd in commands) {
                        def spec = cmd.commandSpec
                        console.log("  ${spec.name().padRight(width)}  ${spec.usageMessage().description()?.join(separator)}")
                    }
                }
                console.log('')
                console.addStatus('Provided Features:')
                console.addStatus('(+) denotes features included by default.')
                console.log('--------------------')
                def features = profile.features.sort { it.name }
                if (!features.empty) {
                    int padding = features.collect { it.name }.max { it.length() }.length()
                    int width = Math.min(padding, features.collect { it.name }.sort { it.length() }.last().length())
                    for (feature in features) {
                        if (defaultFeaturesList.contains(feature) || requiredFeaturesList.contains(feature)) {
                            String fname = feature.name + " (+)"
                            console.log("\033[1m  ${fname.padRight(width)}  ${feature.description}\033[0m")
                        } else {
                            console.log("  ${feature.name.padRight(width)}  ${feature.description}")
                        }

                        if (enhancedInfo) {
                            def dependentFeatures = feature.getDependentFeatures(profile)
                            if (dependentFeatures.size() > 0) {
                                console.log("       Dependent features:")
                                dependentFeatures.each {
                                    console.log("                            ${it.name}")
                                }
                                console.log('  ------------------')
                            }

                            def evictedFeatures = feature.getEvictedFeatureNames()
                            if (evictedFeatures.size() > 0) {
                                console.log("       Evicted features:")
                                evictedFeatures.each {
                                    console.log("                            ${it}")
                                }
                                console.log('  ------------------')
                            }

                            def defaultFeatures = feature.getDefaultFeatures(profile)
                            if (defaultFeatures.size() > 0) {
                                console.log("       Default features:")
                                defaultFeatures.each {
                                    console.log("                            ${it.name}")
                                }
                                console.log('  ------------------')
                            }
                        }
                    }
                }
            }
        }
        return true
    }

    public Iterable<Command> findCommands(Profile profile, MicronautConsole console) {
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
