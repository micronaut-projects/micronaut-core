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

    public static final String LANG_JAVA = "java"
    public static final String LANG_GROOVY = "groovy"
    public static final String LANG_KOTLIN = "kotlin"

    public static final String PROVDER_AWS = "aws"

    public static final String PROFILE_DEFAULT = "function-aws"
    public static final String FEATURE_DEFAULT = "java-function"

    CreateFunctionCommand() {
        description.description = "Creates a serverless function application"
        description.usage = "create-function [NAME] -[LANG] (default: java, one of: groovy|kotlin|java) -[PROVIDER] (default: aws)"

        final List<String> flags = getFlags()
        if (flags.contains(LANG_JAVA)) {
            description.flag(name: LANG_JAVA, description: "Create a Java function")
        }
        if (flags.contains(LANG_GROOVY)) {
            description.flag(name: LANG_GROOVY, description: "Create a Groovy function")
        }
        if (flags.contains(LANG_KOTLIN)) {
            description.flag(name: LANG_KOTLIN, description: "Create a Kotlin function")
        }
        if (flags.contains(PROVDER_AWS)) {
            description.flag(name: PROVDER_AWS, description: "Create an AWS Lambda function")
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
        [INPLACE_FLAG, BUILD_FLAG, FEATURES_FLAG, LANG_GROOVY, LANG_KOTLIN, LANG_JAVA, PROVDER_AWS]
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
        final String langFeature = evaluateLangFeature(commandLine)

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
        mainCommandLine.hasOption(PROVDER_AWS) ? "function-aws" : PROFILE_DEFAULT
    }

    protected String evaluateLangFeature(CommandLine commandLine) {
        commandLine.hasOption(LANG_GROOVY) ? "groovy-function" :
                commandLine.hasOption(LANG_KOTLIN) ? "kotlin-function" :
                        commandLine.hasOption(LANG_JAVA) ? "java-function" : FEATURE_DEFAULT
    }
}
