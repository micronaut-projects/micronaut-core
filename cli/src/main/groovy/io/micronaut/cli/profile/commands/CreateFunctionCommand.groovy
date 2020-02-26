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
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters

import java.nio.file.Paths

/**
 * Command for creating Micronaut functions
 *
 * @author Zachary Klein
 * @author Remko Popma
 * @since 1.0
 */
@CompileStatic
@Command(name = "create-function", description = "Creates a serverless function application")
class CreateFunctionCommand extends AbstractCreateCommand {
    public static final String NAME = "create-function"

    @Parameters(arity = '0..1', paramLabel = 'NAME', description = 'The name of the function to create.')
    String functionName

    @Option(names = ['-r', '--provider'], paramLabel = 'PROVIDER', description = 'Which cloud provider to use. Possible values: ${COMPLETION-CANDIDATES-VALUE}.')
    protected SupportedProvider provider = SupportedProvider.aws

    @Option(names = ['-l', '--lang'], paramLabel = 'LANG', description = 'Which language to use. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedLanguage lang = SupportedLanguage.java

    @Option(names = ['-t', '--test'], paramLabel = 'TEST', description = 'Which test framework to use. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedTestFramework testFramework

    @Option(names = ['-b', '--build'], paramLabel = 'BUILD-TOOL', description = 'Which build tool to configure. Possible values: ${COMPLETION-CANDIDATES}.')
    SupportedBuildTool build = SupportedBuildTool.gradle

    protected static final String DEFAULT_PROFILE_NAME = "function-aws"

    CreateFunctionCommand() {
    }

    @Override
    String getName() { NAME }


    @Override
    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        console.addStatus("Function created at ${Paths.get(targetDir.path).toAbsolutePath().normalize()}")
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        final String functionProfile = evaluateProfileName()
        final String langFeature = evaluateLangFeature(functionProfile)
        final String testFeature = evaluateTestFeature()

        checkInvalidSelections(executionContext, langFeature, testFeature)

        final Set<String> selectedFeatures = new HashSet<>()
        selectedFeatures.addAll(langFeature, testFeature)
        selectedFeatures.addAll(this.features)

        final CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: this.functionName,
                baseDir: executionContext.baseDir,
                lang: lang.name(),
                profileName: functionProfile,
                micronautVersion: VersionInfo.getVersion(MicronautCli),
                features: selectedFeatures,
                inplace: this.inplace,
                build: this.build.toString(),
                console: executionContext.console
        )
        super.handle(cmd)
    }

    @Override
    protected String evaluateProfileName() {
        "function-${provider.fullName}"
    }

    protected String evaluateLangFeature(String profile) {
        "${profile}-${lang.name()}"
    }

    protected String evaluateTestFeature() {
        "test-${provider.fullName}-${resolveTest()}"
    }

    protected SupportedTestFramework resolveTest() {
        testFramework ?: defaultTestFeature(lang)
    }

    protected static SupportedTestFramework defaultTestFeature(SupportedLanguage lang) {
        switch (lang) {
            case SupportedLanguage.java:   return SupportedTestFramework.junit
            case SupportedLanguage.groovy: return SupportedTestFramework.spock
            case SupportedLanguage.kotlin: return SupportedTestFramework.kotlintest
            default:                       return SupportedTestFramework.junit
        }
    }

    protected void checkInvalidSelections(ExecutionContext executionContext, String langFeature, String testFeature) {
        if (langFeature.contains("kotlin") && !testFeature.contains("kotlintest")) {
            executionContext.console.warn("Kotlin project may not support your chosen test framework")
        }
    }
}
