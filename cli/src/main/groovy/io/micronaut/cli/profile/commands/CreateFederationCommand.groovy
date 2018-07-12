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
import io.micronaut.cli.io.support.GradleBuildTokens
import io.micronaut.cli.io.support.MavenBuildTokens
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

import java.nio.file.Paths

/**
 * The command to create a federation of services
 *
 * @author James Kleeh
 * @since 1.0
 */
@CompileStatic
@Command(name = 'create-federation', description = 'Creates a federation of services')
class CreateFederationCommand extends AbstractCreateCommand {
    public static final String NAME = 'create-federation'

    @Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the federation to create.')
    String federationName = ''

    @Option(names = ['-s', '--services'], arity = "1..*", paramLabel = 'SERVICE', split = ',',
            required = true, description = 'The names of the services to create.')
    List<String> services = []

    // note: description contains a variable that will be replaced by picocli, not by Groovy
    @Option(names = ['-b', '--build'], paramLabel = 'BUILD-TOOL', description = 'Which build tool to configure. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedBuildTool build = SupportedBuildTool.gradle

    CreateFederationCommand() {
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        final Set<String> featureSet = new HashSet<>(this.features)
        final String micronautVersion = VersionInfo.getVersion(MicronautCli)
        final File serviceDir = inplace ? new File('.').canonicalFile : new File(executionContext.baseDir, federationName)
        final String profileName = evaluateProfileName()

        for (String service : services) {
            final CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: service,
                baseDir: serviceDir,
                profileName: profileName,
                micronautVersion: micronautVersion,
                features: featureSet,
                inplace: false,
                build: this.build.toString(),
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
            features: featureSet,
            inplace: this.inplace,
            build: this.build.toString(),
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
