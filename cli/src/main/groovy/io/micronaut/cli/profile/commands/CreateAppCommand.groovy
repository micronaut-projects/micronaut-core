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
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Command for creating Micronaut applications
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Remko Popma
 * @since 1.0
 */
@CompileStatic
@Command(name = 'create-app', description = 'Creates an application')
class CreateAppCommand extends AbstractCreateAppCommand {
    public static final String NAME = 'create-app'

    @Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the application to create.')
    String appname = ""

    @Override
    String getName() { NAME }

    @Override
    protected String getNameOfAppToCreate() { appname }
}
