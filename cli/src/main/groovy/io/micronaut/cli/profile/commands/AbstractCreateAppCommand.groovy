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
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.util.NameUtils
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

/**
 * Abstract superclass for commands for creating Micronaut applications and profiles.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Remko Popma
 * @since 1.0
 */
@CompileStatic
abstract class AbstractCreateAppCommand extends AbstractCreateCommand {

    // note: description contains a variable that will be replaced by picocli, not by Groovy
    @Option(names = ['-l', '--lang'], paramLabel = 'LANG', description = 'Which language to use. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedLanguage lang

    // note: description contains a variable that will be replaced by picocli, not by Groovy
    @Option(names = ['-b', '--build'], paramLabel = 'BUILD-TOOL', description = 'Which build tool to configure. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedBuildTool build = SupportedBuildTool.gradle

    protected abstract String getNameOfAppToCreate();

    @Override
    boolean handle(ExecutionContext executionContext) {
        String profileName = evaluateProfileName()

        Set<String> selectedFeatures = new LinkedHashSet<>()
        selectedFeatures.addAll(features)


        CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: this.nameOfAppToCreate,
                baseDir: executionContext.baseDir,
                profileName: profileName,
                micronautVersion: VersionInfo.getVersion(MicronautCli),
                features: selectedFeatures,
                lang: (profileName != 'profile') ? resolveLang() : null,
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
