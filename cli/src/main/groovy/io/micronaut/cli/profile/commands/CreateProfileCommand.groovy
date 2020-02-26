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

import io.micronaut.cli.MicronautCli
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters

/**
 *  Creates a profile
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Command(name = "create-profile", description = "Creates a profile")
class CreateProfileCommand extends AbstractCreateAppCommand {
    public static final String NAME = "create-profile"

    @Parameters(arity = "0..1", paramLabel = "NAME", description = "The name of the profile to create.")
    String profileToCreate

    @Override
    String getName() { NAME }

    @Override
    protected String getDefaultProfile() { "profile" }

    @Override
    protected String getNameOfAppToCreate() { profileToCreate }
}
