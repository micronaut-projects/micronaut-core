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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.profile.ExecutionContext
import java.nio.file.Paths

/**
 *  Creates a federation
 *
 * @author Ben Rhine
 */
@CompileStatic
class CreateFederationCommand extends CreateServiceCommand {
    public static final String NAME = "create-federation"
    public static final String SERVICES_FLAG = "services"

    CreateFederationCommand() {
        description.description = "Creates a federation of services"
        description.usage = "create-federation [NAME] --services [SERVICE_NAME,SERVICE_NAME,...]"

        List<String> flags = getFlags()
        if (flags.contains(SERVICES_FLAG)) {
            description.flag(name: SERVICES_FLAG, description: "The names of the services to create")
        }
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        CommandLine commandLine = executionContext.commandLine

        String profileName = evaluateProfileName(commandLine)

        List<String> validFlags = getFlags()
        commandLine.undeclaredOptions.each { String key, Object value ->
            if (!validFlags.contains(key)) {
                List possibleSolutions = validFlags.findAll { it.substring(0, 2) == key.substring(0, 2) }
                StringBuilder warning = new StringBuilder("Unrecognized flag: ${key}.")
                if (possibleSolutions) {
                    warning.append(" Possible solutions: ")
                    warning.append(possibleSolutions.join(", "))
                }
                executionContext.console.warn(warning.toString())
            }
        }

        String federationName = commandLine.remainingArgs ? commandLine.remainingArgs[0] : ""
        File federationDir = new File(executionContext.baseDir.absoluteFile, federationName)

        AntBuilder ant = new ConsoleAntBuilder()
        makeDir(ant, federationDir)

        List<String> features = commandLine.optionValue(FEATURES_FLAG)?.toString()?.split(',')?.toList()
        List<String> services = commandLine.optionValue(SERVICES_FLAG)?.toString()?.split(',')?.toList()
        String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"
        boolean inPlace = commandLine.hasOption(INPLACE_FLAG) || MicronautCli.isInteractiveModeActive()

        String micronautVersion = MicronautCli.getPackage().getImplementationVersion()
        for(String service: services) {
            CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                    appName: service,
                    baseDir: federationDir,
                    profileName: profileName,
                    micronautVersion: micronautVersion,
                    features: features,
                    inplace: inPlace,
                    build: build,
                    console: executionContext.console
            )
            super.handle(cmd)
        }
        executionContext.console.addStatus("Federation created at ${Paths.get(federationDir.path).toAbsolutePath().normalize()}")
    }

    @Override
    String getName() { NAME }

    @Override
    protected void populateDescription() {
        description.argument(name: "Federation Name", description: "The name of the federation to create.", required: false)
    }

    protected List<String> getFlags() {
        [BUILD_FLAG, FEATURES_FLAG, INPLACE_FLAG, SERVICES_FLAG, PROFILE_FLAG]
    }

    @CompileDynamic
    private void makeDir(AntBuilder ant, File file) {
        ant.mkdir(dir: file)
    }
}
