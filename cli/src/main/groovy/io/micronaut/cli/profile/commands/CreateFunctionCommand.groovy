package io.micronaut.cli.profile.commands

import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.profile.ExecutionContext

import java.nio.file.Paths

@CompileStatic
class CreateFunctionCommand extends CreateServiceCommand {
    public static final String NAME = "create-function"

    public static final String LANG_FLAG = "lang"
    public static final String PROVIDER_FLAG = "provider"

    protected static final List<String> LANG_OPTIONS = ["java", "groovy", "kotlin"]
    protected static final List<String> PROVIDER_OPTIONS = ["aws"]

    public static final String PROVIDER_DEFAULT = "aws"
    public static final String LANG_DEFAULT = "java"

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
    }

    @Override
    String getName() { NAME }


    @Override
    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        if (command.profileName.startsWith("function")) {
            console.addStatus("Function created at ${Paths.get(targetDir.path).toAbsolutePath().normalize()}")

        }
    }

    @Override
    protected List<String> getFlags() {
        [INPLACE_FLAG, BUILD_FLAG, FEATURES_FLAG, LANG_FLAG, PROVIDER_FLAG]
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

        final List<String> commandLineFeatures = commandLine.optionValue(FEATURES_FLAG)?.toString()?.split(',')?.toList()
        List<String> features = [langFeature]
        if(commandLineFeatures) features.addAll(commandLineFeatures)

        final String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"
        final boolean inPlace = commandLine.hasOption(INPLACE_FLAG) || MicronautCli.isInteractiveModeActive()
        final String appName = commandLine.remainingArgs ? commandLine.remainingArgs[0] : ""

        final CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                appName: appName,
                baseDir: executionContext.baseDir,
                profileName: functionProfile,
                micronautVersion: MicronautCli.getPackage().getImplementationVersion(),
                features: features,
                inplace: inPlace,
                build: build,
                console: executionContext.console
        )
        super.handle(cmd)
    }

    @Override
    protected String evaluateProfileName(CommandLine mainCommandLine) {
        "function-${mainCommandLine.optionValue(PROVIDER_FLAG) ?: PROVIDER_DEFAULT}"
    }

    protected String evaluateLangFeature(CommandLine commandLine, String profile) {
        "${profile}-${commandLine.optionValue(LANG_FLAG) ?: LANG_DEFAULT}"
    }
}
