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

import groovy.transform.CompileStatic;
import io.micronaut.cli.profile.Profile;
import io.micronaut.cli.profile.ProfileRepository;
import io.micronaut.cli.profile.ProfileRepositoryAware
import picocli.CommandLine.Model.CommandSpec

/**
 * Generates a list of profile names as completion candidates for picocli options or positional parameters.
 * <p>
 * Example usage:
 * <pre>
 * class SomeCommand implements ProfileRepositoryAware {
 *     &#064;picocli.CommandLine.Option(names = "--option", completionCandidates = ProfileCompletionCandidates)
 *     String someOption
 *
 *     &#064;picocli.CommandLine.Inject
 *     CommandSpec spec
 *
 *     &#064;Override void setProfileRepository(ProfileRepository profileRepository) {
 *         ProfileCompletionCandidates.updateCommandArguments(spec, profileRepository);
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author Remko Popma
 * @version 1.0
 */
@CompileStatic
class ProfileCompletionCandidates extends ArrayList<String> implements ProfileRepositoryAware {
    @Override void setProfileRepository(ProfileRepository profileRepository) {
        profileRepository.allProfiles.each { Profile p -> add(p.name) }
    }

    /**
     * Calls {@link #setProfileRepository} on {@code ProfileRepositoryAware} completion candidates
     * of all options and positional parameters in the given command spec.
     */
    static updateCommandArguments(CommandSpec commandSpec, ProfileRepository repo) {
        commandSpec.options().each              { customize(it.completionCandidates(), repo) }
        commandSpec.positionalParameters().each { customize(it.completionCandidates(), repo) }
    }

    private static void customize(Iterable<String> candidates, ProfileRepository profileRepository) {
        if (candidates instanceof ProfileRepositoryAware) {
            ((ProfileRepositoryAware) candidates).setProfileRepository(profileRepository);
        }
    }
}
