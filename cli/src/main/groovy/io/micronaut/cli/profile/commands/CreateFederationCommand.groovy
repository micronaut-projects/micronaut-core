package io.micronaut.cli.profile.commands

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.commands.CreateServiceCommand.CreateServiceCommandObject

import java.nio.file.Paths

@CompileStatic
class CreateFederationCommand extends ArgumentCompletingCommand implements ProfileRepositoryAware {

    public static final String NAME = "create-federation"
    public static final String SERVICES_FLAG = "services"
    public static final String BUILD_FLAG = "build"

    CommandDescription description = new CommandDescription(name, "Creates a federation of services", "create-federation [NAME] --services [SERVICE_NAME,SERVICE_NAME,...]")
    ProfileRepository profileRepository

    CreateFederationCommand() {
        description.argument(name: "Federation Name", description: "The name of the federation to create.")
        description.flag(name: "Service Names", description: "The names of the services to create")
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        CommandLine commandLine = executionContext.commandLine

        List<String> validFlags = [BUILD_FLAG, SERVICES_FLAG]
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

        List<String> services = commandLine.optionValue(SERVICES_FLAG)?.toString()?.split(',')?.toList()
        String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"

        CreateServiceCommand createService = new CreateServiceCommand()
        createService.setProfileRepository(profileRepository)

        String micronautVersion = MicronautCli.getPackage().getImplementationVersion()
        for(String service: services) {
            CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                    appName: service,
                    baseDir: federationDir,
                    profileName: ProfileRepository.DEFAULT_PROFILE_NAME,
                    micronautVersion: micronautVersion,
                    features: [],
                    inplace: false,
                    build: build,
                    console: executionContext.console
            )
            createService.handle(cmd)
        }

        executionContext.console.addStatus("Federation created at ${Paths.get(federationDir.path).toAbsolutePath().normalize()}")
    }

    @CompileDynamic
    private void makeDir(AntBuilder ant, File file) {
        ant.mkdir(dir: file)
    }

    @Override
    String getName() {
        NAME
    }
}
