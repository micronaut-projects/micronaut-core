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
import groovy.transform.TypeCheckingMode
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.io.support.FileSystemResource
import io.micronaut.cli.io.support.GradleBuildTokens
import io.micronaut.cli.io.support.MavenBuildTokens
import io.micronaut.cli.io.support.XmlMerger
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile

import java.nio.file.Path
import java.nio.file.Paths

/**
 *  Creates a federation
 *
 * @author Ben Rhine
 */
@CompileStatic
class CreateFederationCommand extends CreateServiceCommand {
    public static final String NAME = "create-federation"
    public static final String SERVICES_FLAG = "services"

    protected static final String SETTINGS_GRADLE = "settings.gradle"

    CreateFederationCommand() {
        description.description = "Creates a federation of services"
        description.usage = "create-federation [NAME] --services [SERVICE_NAME,SERVICE_NAME,...]"

        List<String> flags = getFlags()
        if (flags.contains(SERVICES_FLAG)) {
            description.flag(name: SERVICES_FLAG, description: "The names of the services to create")
        }
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        if (profileRepository == null) throw new IllegalStateException("Property 'profileRepository' must be set")

        CommandLine commandLine = executionContext.commandLine

        String profileName = evaluateProfileName(commandLine)

        List<String> validFlags = getFlags()
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

        List<String> features = commandLine.optionValue(FEATURES_FLAG)?.toString()?.split(',')?.toList()
        List<String> services = commandLine.optionValue(SERVICES_FLAG)?.toString()?.split(',')?.toList()
        String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"
        boolean inPlace = commandLine.hasOption(INPLACE_FLAG) || MicronautCli.isInteractiveModeActive()

        AntBuilder ant = new ConsoleAntBuilder()
        makeDir(ant, federationDir)

        //---------------------
        Profile profileInstance = profileRepository.getProfile(profileName)
        if (!validateProfile(profileInstance, profileName)) {
            return false
        }

        if (profileInstance) {
            def profiles = profileRepository.getProfileAndDependencies(profileInstance)

            Path appFullDirectory = Paths.get(federationDir.path)

            File projectTargetDirectory = inPlace ? new File(".").canonicalFile : appFullDirectory.toAbsolutePath().normalize().toFile()


            Map<Profile, File> targetDirs = [:]
            buildTargetFolders(profileInstance, targetDirs, projectTargetDirectory)

            for(Profile p : profiles) {
                targetDirectory = targetDirs[p]

                copySkeletonSettings(ant, profileInstance, p, build)
            }

            replaceBuildTokens(ant, build, profileInstance, services, targetDirectory)
        }

        //---------------------

        //copySettingsFile(ant, new File(skeletonDir, build + "-settings"), build, false)

        String micronautVersion = MicronautCli.getPackage().getImplementationVersion()
        for(String service: services) {
            CreateServiceCommandObject cmd = new CreateServiceCommandObject(
                    appName: service,
                    baseDir: federationDir,
                    profileName: profileName,
                    micronautVersion: micronautVersion,
                    features: features,
                    inplace: false,  //false for sercies
                    build: build,
                    console: executionContext.console
            )
            super.handle(cmd)
        }
        executionContext.console.addStatus("Federation created at ${Paths.get(federationDir.path).toAbsolutePath().normalize()}")
    }

    @Override
    String getName() { NAME }

    @Override
    protected void populateDescription() {
        description.argument(name: "Federation Name", description: "The name of the federation to create.", required: false)
    }

    protected List<String> getFlags() {
        [BUILD_FLAG, FEATURES_FLAG, INPLACE_FLAG, SERVICES_FLAG, PROFILE_FLAG]
    }

    @CompileDynamic
    protected void copySettingsFile(ConsoleAntBuilder ant, File skeletonDir, String build, boolean allowMerge) {
        if (!skeletonDir.exists()) {
            return
        }

        File destDir = targetDirectory

        if (build == "gradle") {
            Set<File> sourceBuildGradles = findAllFilesByName(skeletonDir, SETTINGS_GRADLE)

            sourceBuildGradles.each { File srcFile ->
                File destFile = new File(destDir, SETTINGS_GRADLE)

                if (!destFile.exists()) {
                    ant.copy file:srcFile, tofile:destFile
                } else if (allowMerge) {
                    def concatFile = "${destDir}/concat.gradle"
                    ant.move(file:destFile, tofile: concatFile)
                    ant.concat([destfile: destFile, fixlastline: true], {
                        path {
                            pathelement location: concatFile
                            pathelement location: srcFile
                        }
                    })
                    ant.delete(file: concatFile, failonerror: false)
                }
            }
        }
        if (build == "maven") {
            Set<File> sourcePomXmls = findAllFilesByName(skeletonDir, POM_XML)

            sourcePomXmls.each { File srcFile ->
                File destFile = new File(destDir, POM_XML)

                if (!destFile.exists()) {
                    ant.copy file:srcFile, tofile:destFile
                } else if (allowMerge) {
                    ant.echo(file: destFile, message: new XmlMerger().merge(srcFile, destFile))
                }
            }
        }
    }


    //protected void replaceBuildTokens(String build, Profile profile, List<Feature> features, File targetDirectory) {
    @CompileDynamic
    protected void replaceBuildTokens(ConsoleAntBuilder ant, String build, Profile profile, List<String> services, File targetDirectory) {
        Map tokens
        //MicronautConsole.instance.info("${build}")
        //MicronautConsole.instance.info("${services}")
        //MicronautConsole.instance.info("${targetDirectory}")

        if (build == "gradle") {
            final servicesList = services.collect { String name ->
                "include \"$name\""
            }.join(System.getProperty("line.separator"))

            tokens = ["services":servicesList]
        }
        if (build == "maven") {
            final servicesList = services.collect { String name ->
                "<module>$name</module>"
            }.join(System.getProperty("line.separator"))

            tokens = ["services":servicesList]
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

    @CompileStatic(TypeCheckingMode.SKIP)
    private void copySkeletonSettings(ConsoleAntBuilder ant, Profile profile, Profile participatingProfile, String build) {
        def skeletonResource = participatingProfile.profileDir.createRelative("skeleton")
        File skeletonDir
        if(skeletonResource instanceof FileSystemResource) {
            skeletonDir = skeletonResource.file
        } else {
            // establish the JAR file name and extract
            def tmpDir = unzipProfile(ant, skeletonResource)
            skeletonDir = new File(tmpDir, "META-INF/profile/skeleton")
        }
        copySettingsFile(ant, new File(skeletonDir.path), build, false)

        ant.chmod(dir: targetDirectory, includes: profile.executablePatterns.join(' '), perm: 'u+x')
    }

    @CompileDynamic
    private void makeDir(AntBuilder ant, File file) {
        ant.mkdir(dir: file)
    }
}
