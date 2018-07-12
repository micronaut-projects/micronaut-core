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
class CreateAppCommand extends AbstractCreateCommand {
    public static final String NAME = 'create-app'

    @Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the application to create.')
    String appname = ""

    // note: description contains a variable that will be replaced by picocli, not by Groovy
    @Option(names = ['-l', '--lang'], paramLabel = 'LANG', description = 'Which language to use. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedLanguage lang = SupportedLanguage.java

    // note: description contains a variable that will be replaced by picocli, not by Groovy
    @Option(names = ['-b', '--build'], paramLabel = 'BUILD-TOOL', description = 'Which build tool to configure. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedBuildTool build = SupportedBuildTool.gradle

    @Override
    String getName() { NAME }


    @Override
    boolean handle(ExecutionContext executionContext) {
        String profileName = evaluateProfileName()

        Set<String> selectedFeatures = new HashSet<>()
        selectedFeatures.addAll(features)
        selectedFeatures.add(resolveLang())

        CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: this.appname,
                baseDir: executionContext.baseDir,
                profileName: profileName,
                micronautVersion: VersionInfo.getVersion(MicronautCli),
                features: selectedFeatures,
                inplace: this.inplace,
                build: this.build.name(),
                console: executionContext.console
        )

        return this.handle(cmd)
    }

    protected String resolveLang() {
        lang
    }

}
