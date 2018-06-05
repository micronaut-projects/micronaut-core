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
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.util.VersionInfo

import java.nio.file.Paths

@CompileStatic
class CreateFunctionCommand extends CreateAppCommand {
    public static final String NAME = "create-function"

    public static final String LANG_FLAG = "lang"
    public static final String PROVIDER_FLAG = "provider"
    public static final String TEST_FLAG = "test"

    protected static final List<String> LANG_OPTIONS = ["java", "groovy", "kotlin"]
    protected static final List<String> PROVIDER_OPTIONS = ["aws"]
    protected static final List<String> TEST_OPTIONS = ["junit", "spock", "spek"]

    public static final String PROVIDER_DEFAULT = "aws"
    public static final String LANG_DEFAULT = "java"

    protected String provider
    protected String lang
    protected String test

    CreateFunctionCommand() {
        description.description = "Creates a serverless function application"
        description.usage = "create-function [NAME] -lang [LANG] -provider [PROVIDER]"

        final List<String> flags = getFlags()
        if (flags.contains(LANG_FLAG)) {
            description.flag(name: LANG_FLAG, description: "Which language to use. Possible values: ${LANG_OPTIONS.collect({ "\"${it}\"" }).join(', ')}.", required: false)
        }

        if (flags.contains(PROVIDER_FLAG)) {
            description.flag(name: PROVIDER_FLAG, description: "Which cloud provider to use. Possible values: ${PROVIDER_OPTIONS.collect({ "\"${it}\"" }).join(', ')}.", required: false)
        }

        if (flags.contains(TEST_FLAG)) {
            description.flag(name: TEST_FLAG, description: "Which test framework to use. Possible values: ${TEST_OPTIONS.collect({ "\"${it}\"" }).join(', ')}.", required: false)
        }
    }

    @Override
    String getName() { NAME }


    @Override
    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        console.addStatus("Function created at ${Paths.get(targetDir.path).toAbsolutePath().normalize()}")
    }

    @Override
    protected List<String> getFlags() {
        [INPLACE_FLAG, BUILD_FLAG, FEATURES_FLAG, LANG_FLAG, TEST_FLAG, PROVIDER_FLAG]
    }

    @Override
    protected void populateDescription() {
        description.argument(name: "Function Name", description: "The name of the function to create.", required: false)
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

        final String functionProfile = evaluateProfileName(commandLine)
        final String langFeature = evaluateLangFeature(commandLine, functionProfile)
        final String testFeature = evaluateTestFeature(commandLine)

        checkInvalidSelections(executionContext, langFeature, testFeature)

        final List<String> commandLineFeatures = commandLine.optionValue(FEATURES_FLAG)?.toString()?.split(',')?.toList()
        Set<String> features = new HashSet<>()
        features.addAll(langFeature, testFeature)
        if (commandLineFeatures) features.addAll(commandLineFeatures)

        final String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"
        final boolean inPlace = commandLine.hasOption(INPLACE_FLAG)
        final String appName = commandLine.remainingArgs ? commandLine.remainingArgs[0] : ""

        final CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: appName,
                baseDir: executionContext.baseDir,
                profileName: functionProfile,
                micronautVersion: VersionInfo.getVersion(MicronautCli),
                features: features,
                inplace: inPlace,
                build: build,
                console: executionContext.console
        )
        super.handle(cmd)
    }

    @Override
    protected String evaluateProfileName(CommandLine commandLine) {
        "function-${resolveProvider(commandLine)}"
    }

    protected String evaluateLangFeature(CommandLine commandLine, String profile) {
        "${profile}-${resolveLang(commandLine)}"
    }

    protected String evaluateTestFeature(CommandLine commandLine) {
        "test-${resolveProvider(commandLine)}-${resolveTest(commandLine)}"
    }

    protected String resolveProvider(CommandLine commandLine) {
        if(!provider) provider = commandLine.optionValue(PROVIDER_FLAG) ?: PROVIDER_DEFAULT
        provider
    }

    protected String resolveLang(CommandLine commandLine) {
        if(!lang) lang = commandLine.optionValue(LANG_FLAG) ?: LANG_DEFAULT
        lang
    }

    protected String resolveTest(CommandLine commandLine) {
        if(!test) test = commandLine.optionValue(TEST_FLAG) ?: defaultTestFeature(resolveLang(commandLine))
        test
    }

    protected static String defaultTestFeature(lang) {
        String testFeature
        switch (lang) {
            case "java":
                testFeature = "junit"
                break
            case "groovy":
                testFeature = "spock"
                break
            case "kotlin":
                testFeature = "spek"
                break
            default:
                testFeature = "junit"
        }

        testFeature
    }

    protected void checkInvalidSelections(ExecutionContext executionContext, String langFeature, String testFeature) {
        if(langFeature.contains("kotlin") && !testFeature.contains("spek")) {
            executionContext.console.warn("Kotlin project may not support your chosen test framework")
        }
    }
}
