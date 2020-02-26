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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.io.support.BuildTokens
import io.micronaut.cli.profile.ExecutionContext
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
class CreateFederationCommand extends AbstractCreateAppCommand {
    public static final String NAME = 'create-federation'

    @Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the federation to create.')
    String federationName = ''

    @Option(names = ['-s', '--services'], arity = "1..*", paramLabel = 'SERVICE', split = ',',
            required = true, description = 'The names of the services to create.')
    List<String> services = []

    CreateFederationCommand() {
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        final String micronautVersion = VersionInfo.getVersion(MicronautCli)
        final File serviceDir = inplace ? new File('.').canonicalFile : new File(executionContext.baseDir, federationName)
        final String profileName = evaluateProfileName()

        final CreateServiceCommandObject parent = new CreateServiceCommandObject(
                appName: federationName,
                baseDir: executionContext.baseDir,
                profileName: 'federation',
                micronautVersion: micronautVersion,
                features: [] as Set,
                inplace: this.inplace,
                build: this.build.toString(),
                console: executionContext.console
        )
        if (super.handle(parent)) {
            for (String service : services) {
                final CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                        appName: service,
                        baseDir: serviceDir,
                        profileName: profileName,
                        micronautVersion: micronautVersion,
                        features: new HashSet<>(this.features),
                        inplace: false,
                        build: this.build.toString(),
                        console: executionContext.console,
                        lang: resolveLang(),
                        skeletonExclude: ["gradlew*", "gradle/", ".mvn/", "mvnw*"]
                )
                super.handle(cmd)
            }
        }
    }

    @Override
    String getName() { NAME }

    @Override
    protected String getNameOfAppToCreate() { federationName }

    @Override
    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        if (command.profileName == "federation") {
            console.addStatus("Federation created at ${Paths.get(targetDir.path).toAbsolutePath().normalize()}")
        }
    }

    @Override
    @CompileDynamic
    protected void withTokens(BuildTokens buildTokens) {
        final AntBuilder ant = new ConsoleAntBuilder()

        Map tokens = buildTokens.getTokens(services.collect {
            groupAppName(it)[1]
        })

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
