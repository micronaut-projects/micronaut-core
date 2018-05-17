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
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.io.support.GradleBuildTokens
import io.micronaut.cli.io.support.MavenBuildTokens
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile

import java.nio.file.Paths

/**
 * The command to create a federation of services
 *
 * @author James Kleeh
 * @since 1.0
 */
@CompileStatic
class CreateFederationCommand extends CreateServiceCommand {
    public static final String NAME = "create-federation"
    public static final String SERVICES_FLAG = "services"

    List<String> services = []

    CreateFederationCommand() {
        description.description = "Creates a federation of services"
        description.usage = "create-federation [NAME] --services [SERVICENAME-A SERVICENAME-B ...]"

        final List<String> flags = getFlags()
        if (flags.contains(SERVICES_FLAG)) {
            description.flag(name: SERVICES_FLAG, description: "The names of the services to create", required:true)
        }
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        final CommandLine commandLine = executionContext.commandLine

        final List<String> validFlags = getFlags()
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

        final String federationName = commandLine.remainingArgs ? commandLine.remainingArgs[0] : ""
        final List<String> features = commandLine.optionValue(FEATURES_FLAG)?.toString()?.split(',')?.toList()
        services = commandLine.optionValue(SERVICES_FLAG)?.toString()?.split(',')?.toList()
        if (!services) {
            StringBuilder warning = new StringBuilder("Missing required flag: --services= <service1 service2 service3 ..>")
            executionContext.console.error(warning.toString())
            return false
        }
        final String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"
        final boolean inPlace = commandLine.hasOption(INPLACE_FLAG) || MicronautCli.isInteractiveModeActive()
        final String micronautVersion = MicronautCli.getPackage().getImplementationVersion()
        final String profileName = evaluateProfileName(commandLine)

        final File serviceDir = inPlace ? new File('.').canonicalFile : new File(executionContext.baseDir, federationName)

        for (String service : services) {
            final CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: service,
                baseDir: serviceDir,
                profileName: profileName,
                micronautVersion: micronautVersion,
                features: features,
                inplace: false,
                build: build,
                console: executionContext.console,
                skeletonExclude: ["gradle*", "gradle/", ".mvn/", "mvnw*"]
            )
            super.handle(cmd)
        }

        final CreateServiceCommandObject parent = new CreateServiceCommandObject(
            appName: federationName,
            baseDir: executionContext.baseDir,
            profileName: 'federation',
            micronautVersion: micronautVersion,
            features: features,
            inplace: inPlace,
            build: build,
            console: executionContext.console
        )
        super.handle(parent)
    }

    @Override
    String getName() { NAME }

    @Override
    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        if (command.profileName == "federation") {
            console.addStatus("Federation created at ${Paths.get(targetDir.path).toAbsolutePath().normalize()}")

        }
    }

    @Override
    protected void populateDescription() {
        description.argument(name: "Federation Name", description: "The name of the federation to create.", required: false)
    }

    @Override
    protected List<String> getFlags() {
        [BUILD_FLAG, FEATURES_FLAG, INPLACE_FLAG, SERVICES_FLAG, PROFILE_FLAG, SERVICES_FLAG]
    }

    @Override
    @CompileDynamic
    protected void replaceBuildTokens(String build, Profile profile, List features, File targetDirectory) {
        super.replaceBuildTokens(build, profile, features, targetDirectory)

        final AntBuilder ant = new ConsoleAntBuilder()

        Map tokens = [:]
        if (build == "gradle") {
            tokens = new GradleBuildTokens().getTokens(services)
        }
        if (build == "maven") {
            tokens = new MavenBuildTokens().getTokens(services)
        }

        ant.replace(dir: targetDirectory) {
            tokens.each { k, v ->
                replacefilter {
                    replacetoken("@${k}@".toString())
                    replacevalue(v)
                }
            }
        }

    }

}
