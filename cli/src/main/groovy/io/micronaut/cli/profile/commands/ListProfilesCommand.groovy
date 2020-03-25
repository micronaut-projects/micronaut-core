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
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ResetableCommand
import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec

/**
 * Lists the available {@link io.micronaut.cli.profile.Profile} instances 
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@CommandLine.Command(name = 'list-profiles', description = 'Lists the available profiles')
class ListProfilesCommand implements Command, ProfileRepositoryAware {

    final String name = "list-profiles"

    @CommandLine.Spec
    CommandSpec commandSpec

    @CommandLine.Mixin
    private CommonOptionsMixin autoHelp // adds help, version and other common options to the command

    ProfileRepository profileRepository

    @Override
    boolean handle(ExecutionContext executionContext) {
        def allProfiles = profileRepository.allProfiles.sort { it.name }
        def console = executionContext.console
        console.addStatus("Available Profiles")
        console.log('--------------------')

        if (!allProfiles.empty) {
            int width = Math.min(20, allProfiles.collect { it.name }.sort { it.length() }.last().length())
            for (Profile p in allProfiles) {
                if (!p.isAbstract()) {
                    console.log("  ${p.name.padRight(width)}  ${p.description}")
                }
            }
        }
        return true
    }

}
