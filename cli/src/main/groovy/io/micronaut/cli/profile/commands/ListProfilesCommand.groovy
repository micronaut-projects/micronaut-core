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
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware

/**
 * Lists the available {@link io.micronaut.cli.profile.Profile} instances 
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ListProfilesCommand implements Command, ProfileRepositoryAware {

    final String name = "list-profiles"
    final CommandDescription description = new CommandDescription(name, "Lists the available profiles", "mn list-profiles")

    ProfileRepository profileRepository

    @Override
    boolean handle(ExecutionContext executionContext) {
        def allProfiles = profileRepository.allProfiles
        def console = executionContext.console
        console.addStatus("Available Profiles")
        console.log('--------------------')
        for (Profile p in allProfiles) {
            console.log("* $p.name - ${p.description}")
        }

        return true
    }
}
