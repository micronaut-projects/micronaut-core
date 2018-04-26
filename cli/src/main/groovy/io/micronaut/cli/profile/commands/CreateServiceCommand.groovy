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
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.io.IOUtils
import io.micronaut.cli.io.support.GradleBuildTokens
import io.micronaut.cli.io.support.MavenBuildTokens
import io.micronaut.cli.io.support.XmlMerger
import io.micronaut.cli.util.NameUtils
import org.eclipse.aether.graph.Dependency
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.profile.CommandDescription
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.io.support.FileSystemResource
import io.micronaut.cli.io.support.Resource
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Paths

/**
 * Command for creating Grails applications
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 3.0
 */
@CompileStatic
class CreateServiceCommand extends ArgumentCompletingCommand implements ProfileRepositoryAware {
    public static final String NAME = "create-service"
    public static final String PROFILE_FLAG = "profile"
    public static final String FEATURES_FLAG = "features"
    public static final String ENCODING = System.getProperty("file.encoding") ?: "UTF-8"
    public static final String INPLACE_FLAG = "inplace"
    public static final String BUILD_FLAG = "build"

    protected static final List<String> BUILD_OPTIONS = ["gradle", "maven"]
    protected static final String APPLICATION_YML = "application.yml"
    protected static final String BUILD_GRADLE = "build.gradle"
    protected static final String POM_XML = "pom.xml"

    ProfileRepository profileRepository
    Map<String, String> variables = [:]
    String appname
    String groupname
    String defaultpackagename
    File targetDirectory

    CommandDescription description = new CommandDescription(name, "Creates a service", "create-service [NAME]")

    CreateServiceCommand() {
        populateDescription()
        List<String> flags = getFlags()
        if (flags.contains(INPLACE_FLAG)) {
            description.flag(name: INPLACE_FLAG, description: "Used to create a service using the current directory")
        }
        if (flags.contains(BUILD_FLAG)) {
            description.flag(name: BUILD_FLAG, description: "Which build tool to configure. Possible values: ${BUILD_OPTIONS.collect({"\"${it}\""}).join(', ')}.", required: false)
        }
        if (flags.contains(PROFILE_FLAG)) {
            description.flag(name: PROFILE_FLAG, description: "The profile to use", required: false)
        }
        if (flags.contains(FEATURES_FLAG)) {
            description.flag(name: FEATURES_FLAG, description: "The features to use", required: false)
        }
    }

    protected List<String> getFlags() {
        [INPLACE_FLAG, BUILD_FLAG, PROFILE_FLAG, FEATURES_FLAG]
    }

    protected void populateDescription() {
        description.argument(name: "Service Name", description: "The name of the service to create.", required: false)
    }

    @Override
    String getName() {
        return NAME
    }

    @Override
    protected int complete(CommandLine commandLine, CommandDescription desc, List<CharSequence> candidates, int cursor) {
        def lastOption = commandLine.lastOption()
        if(lastOption != null) {
            def profileNames = profileRepository.allProfiles.collect() { Profile p -> p.name }
            if(lastOption.key == PROFILE_FLAG) {
                def val = lastOption.value
                // if value == true it means no profile is specified and only the flag is present
                if( val == true) {
                    candidates.addAll(profileNames)
                    return cursor
                }
                else if(!profileNames.contains(val)) {
                    def valStr = val.toString()

                    def candidateProfiles = profileNames.findAll { String pn ->
                        pn.startsWith(valStr)
                    }.collect() { String pn ->
                        "${pn.substring(valStr.size())} ".toString()
                    }
                    candidates.addAll candidateProfiles
                    return cursor
                }
            }
            else if(lastOption.key == FEATURES_FLAG) {
                def val = lastOption.value
                def profile = profileRepository.getProfile(commandLine.hasOption(PROFILE_FLAG) ? commandLine.optionValue(PROFILE_FLAG).toString() : getDefaultProfile())
                def featureNames = profile.features.collect() { Feature f -> f.name }
                if( val == true) {
                    candidates.addAll(featureNames)
                    return cursor
                }
                else if(!profileNames.contains(val)) {
                    def valStr = val.toString()
                    if(valStr.endsWith(',')) {
                        def specified = valStr.split(',')
                        candidates.addAll(featureNames.findAll { String f ->
                            !specified.contains(f)
                        })
                        return cursor
                    }

                    def candidatesFeatures = featureNames.findAll { String pn ->
                        pn.startsWith(valStr)
                    }.collect() { String pn ->
                        "${pn.substring(valStr.size())} ".toString()
                    }
                    candidates.addAll candidatesFeatures
                    return cursor
                }
            }
            else if (lastOption.key == BUILD_FLAG) {
                def val = lastOption.value
                if (val == true) {
                    candidates.addAll(BUILD_OPTIONS.collect {"$it "})
                } else if (!BUILD_OPTIONS.contains(val)) {
                    def valStr = val.toString()
                    candidates.addAll(BUILD_OPTIONS.findAll { it.startsWith(valStr) }.collect {"$it "})
                }
                return cursor
            }
        }
        return super.complete(commandLine, desc, candidates, cursor)
    }

    protected File getDestinationDirectory(File srcFile) {
        String searchDir = "skeleton"
        File srcDir = srcFile.parentFile
        File destDir
        if (srcDir.absolutePath.endsWith(searchDir)) {
            destDir = targetDirectory
        } else {
            int index = srcDir.absolutePath.lastIndexOf(searchDir) + searchDir.size() + 1
            String relativePath = (srcDir.absolutePath - srcDir.absolutePath.substring(0,index))
            if (relativePath.startsWith("gradle-build")) {
                relativePath = relativePath.substring("gradle-build".size())
            }
            if (relativePath.startsWith("maven-build")) {
                relativePath = relativePath.substring("maven-build".size())
            }
            destDir = new File(targetDirectory, relativePath)
        }
        destDir
    }

    protected void appendFeatureFiles(File skeletonDir, String build) {
        def ymlFiles = findAllFilesByName(skeletonDir, APPLICATION_YML)

        ymlFiles.each { File newYml ->
            File oldYml = new File(getDestinationDirectory(newYml), APPLICATION_YML)
            String oldText = (oldYml.isFile()) ? oldYml.getText(ENCODING) : null
            if (oldText) {
                appendToYmlSubDocument(newYml, oldText, oldYml)
            } else {
                oldYml.text = newYml.getText(ENCODING)
            }

        }

        copyBuildFiles(new File(skeletonDir, build + "-build"), build, true)
    }

    @CompileDynamic
    protected void copyBuildFiles(File skeletonDir, String build, boolean allowMerge) {
        AntBuilder ant = new ConsoleAntBuilder()
        if (!skeletonDir.exists()) {
            return
        }

        if (build == "gradle") {
            Set<File> sourceBuildGradles = findAllFilesByName(skeletonDir, BUILD_GRADLE)

            sourceBuildGradles.each { File srcFile ->
                File srcDir = srcFile.parentFile
                File destDir = getDestinationDirectory(srcFile)
                File destFile = new File(destDir, BUILD_GRADLE)

                ant.copy(file:"${srcDir}/.gitignore", todir: destDir, failonerror:false)

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
                File srcDir = srcFile.parentFile
                File destDir = getDestinationDirectory(srcFile)
                File destFile = new File(destDir, POM_XML)

                ant.copy(file:"${srcDir}/.gitignore", todir: destDir, failonerror:false)

                if (!destFile.exists()) {
                    ant.copy file:srcFile, tofile:destFile
                } else if (allowMerge) {
                    ant.echo(file: destFile, message: new XmlMerger().merge(srcFile, destFile))
                }
            }
        }
    }

    protected void buildTargetFolders(Profile profile, Map<Profile, File> targetDir, File projectDir) {
        if (!targetDir.containsKey(profile)) {
            targetDir[profile] = projectDir
        }
        profile.extends.each { Profile p ->
            if (profile.parentSkeletonDir) {
                targetDir[p] = profile.getParentSkeletonDir(projectDir)
            } else {
                targetDir[p] = targetDir[profile]
            }
            buildTargetFolders(p, targetDir, projectDir)
        }
    }


    Set<File> findAllFilesByName(File projectDir, String fileName) {
        Set<File> files = (Set)[]
        if (projectDir.exists()) {
            Files.walkFileTree(projectDir.absoluteFile.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes mainAtts)
                        throws IOException {
                    if (path.fileName.toString() == fileName) {
                        files.add(path.toFile())
                    }
                    return FileVisitResult.CONTINUE;
                }
            })
        }
        files
    }

    boolean handle(CreateServiceCommandObject cmd) {
        if (profileRepository == null) throw new IllegalStateException("Property 'profileRepository' must be set")

        String profileName = cmd.profileName

        Profile profileInstance = profileRepository.getProfile(profileName)
        if (!validateProfile(profileInstance, profileName)) {
            return false
        }

        if (!validateBuild(cmd.build)) {
            return false
        }

        List<Feature> features = evaluateFeatures(profileInstance, cmd.features).toList()

        if (profileInstance) {
            if (!initializeGroupAndName(cmd.appName, cmd.inplace)) {
                return false
            }

            initializeVariables(profileName, cmd.micronautVersion)

            Path appFullDirectory = Paths.get(cmd.baseDir.path, appname)

            File projectTargetDirectory = cmd.inplace ? new File(".").canonicalFile : appFullDirectory.toAbsolutePath().normalize().toFile()

            def profiles = profileRepository.getProfileAndDependencies(profileInstance)

            Map<Profile, File> targetDirs = [:]
            buildTargetFolders(profileInstance, targetDirs, projectTargetDirectory)

            for(Profile p : profiles) {
                Set<File> ymlFiles = findAllFilesByName(projectTargetDirectory, APPLICATION_YML)
                Map<File, String> ymlCache = [:]

                targetDirectory = targetDirs[p]

                ymlFiles.each { File applicationYmlFile ->
                    String previousApplicationYml = (applicationYmlFile.isFile()) ? applicationYmlFile.getText(ENCODING) : null
                    if (previousApplicationYml) {
                        ymlCache[applicationYmlFile] = previousApplicationYml
                    }
                }

                copySkeleton(profileInstance, p, cmd)

                ymlCache.each { File applicationYmlFile, String previousApplicationYml ->
                    if(applicationYmlFile.exists()) {
                        appendToYmlSubDocument(applicationYmlFile, previousApplicationYml)
                    }
                }
            }
            def ant = new ConsoleAntBuilder()

            for(Feature f in features) {
                def location = f.location

                File skeletonDir
                if(location instanceof FileSystemResource) {
                    skeletonDir = location.createRelative("skeleton").file
                }
                else {
                    File tmpDir = unzipProfile(ant, location)
                    skeletonDir = new File(tmpDir, "META-INF/profile/features/$f.name/skeleton")
                }

                targetDirectory = targetDirs[f.profile]

                appendFeatureFiles(skeletonDir, cmd.build)

                if(skeletonDir.exists()) {
                    copySrcToTarget(ant, skeletonDir, ['**/' + APPLICATION_YML], profileInstance.binaryExtensions)
                    copySrcToTarget(ant, new File(skeletonDir, cmd.build + "-build"), ['**/' + APPLICATION_YML], profileInstance.binaryExtensions)
                }
            }


            replaceBuildTokens(cmd.build, profileInstance, features, projectTargetDirectory)

            messageOnComplete(cmd.console, cmd, projectTargetDirectory)

            if (profileInstance.instructions) {
                cmd.console.addStatus(profileInstance.instructions)
            }
            MicronautCli.tiggerAppLoad()
            return true
        }
        else {
            System.err.println "Cannot find profile $profileName"
            return false
        }
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
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

        boolean inPlace = commandLine.hasOption(INPLACE_FLAG) || MicronautCli.isInteractiveModeActive()
        String appName = commandLine.remainingArgs ? commandLine.remainingArgs[0] : ""

        List<String> features = commandLine.optionValue(FEATURES_FLAG)?.toString()?.split(',')?.toList()
        String build = commandLine.hasOption(BUILD_FLAG) ? commandLine.optionValue(BUILD_FLAG) : "gradle"

        CreateServiceCommandObject cmd = new CreateServiceCommandObject(
            appName: appName,
            baseDir: executionContext.baseDir,
            profileName: profileName,
            micronautVersion: MicronautCli.getPackage().getImplementationVersion(),
            features: features,
            inplace: inPlace,
            build: build,
            console: executionContext.console
        )

        return this.handle(cmd)
    }

    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        console.addStatus("Service created at ${targetDir.absolutePath}")
    }

    protected boolean validateProfile(Profile profileInstance, String profileName) {
        if (profileInstance == null) {
            MicronautConsole.instance.error("Profile not found for name [$profileName]")
            return false
        }
        return true
    }

    protected boolean validateBuild(String buildName) {
        if (!BUILD_OPTIONS.contains(buildName)) {
            MicronautConsole.instance.error("Build not one of the supported types [$buildName]. Supported types are ${BUILD_OPTIONS.collect({"\"${it}\""}).join(', ')}.")
            return false
        }
        return true
    }

    private Map<URL, File> unzippedDirectories = new LinkedHashMap<URL, File>()

    @CompileDynamic
    protected File unzipProfile(AntBuilder ant, Resource location) {

        def url = location.URL
        def tmpDir = unzippedDirectories.get(url)

        if(tmpDir == null) {
            def jarFile = IOUtils.findJarFile(url)
            tmpDir = File.createTempDir()
            tmpDir.deleteOnExit()
            ant.unzip(src: jarFile, dest: tmpDir)
            unzippedDirectories.put(url, tmpDir)
        }
        return tmpDir
    }

    @CompileDynamic
    protected void replaceBuildTokens(String build, Profile profile, List features, File targetDirectory) {
        AntBuilder ant = new ConsoleAntBuilder()

        Map tokens
        if (build == "gradle") {
            tokens = new GradleBuildTokens().getTokens(profile, features)
        }
        if (build == "maven") {
            tokens = new MavenBuildTokens().getTokens(profile, features)
        }

        ant.replace(dir: targetDirectory) {
            tokens.each { k, v ->
                replacefilter {
                    replacetoken("@${k}@".toString())
                    replacevalue(v)
                }
            }
            variables.each { k, v ->
                replacefilter {
                    replacetoken("@${k}@".toString())
                    replacevalue(v)
                }
            }
        }
    }

    protected String evaluateProfileName(CommandLine mainCommandLine) {
        mainCommandLine.optionValue(PROFILE_FLAG)?.toString() ?: getDefaultProfile()
    }

    protected Iterable<Feature> evaluateFeatures(Profile profile, List<String> requestedFeatures) {
        Set<Feature> features = []
        if (requestedFeatures) {
            List<String> allFeatureNames = profile.features*.name
            List<String> validFeatureNames = requestedFeatures.intersect(allFeatureNames) as List<String>
            requestedFeatures.removeAll(allFeatureNames)
            requestedFeatures.each { String invalidFeature ->
                List possibleSolutions = allFeatureNames.findAll {
                    it.substring(0, 2) == invalidFeature.substring(0, 2)
                }
                StringBuilder warning = new StringBuilder("Feature ${invalidFeature} does not exist in the profile ${profile.name}!")
                if (possibleSolutions) {
                    warning.append(" Possible solutions: ")
                    warning.append(possibleSolutions.join(", "))
                }
                MicronautConsole.getInstance().warn(warning.toString())
            }

            Iterable<Feature> validFeatures = profile.features.findAll { Feature f -> validFeatureNames.contains(f.name) }// as List<Feature>
            features.addAll(validFeatures)
        } else {
            features.addAll(profile.defaultFeatures)
        }

        features.addAll(profile.requiredFeatures)

        for (int i = 0; i < features.size(); i++) {
            features.addAll(features[i].getDependentFeatures(profile))
        }

        features
    }



    protected String getDefaultProfile() {
        ProfileRepository.DEFAULT_PROFILE_NAME
    }

    protected String createNewApplicationYml(String previousYml, String newYml) {
        def ln = System.getProperty("line.separator")
        if (newYml != previousYml) {
            StringBuilder appended = new StringBuilder(previousYml.length() + newYml.length() + 30)
            if(!previousYml.startsWith("---")) {
                appended.append('---' + ln)
            }
            appended.append(previousYml).append(ln + "---" + ln)
            appended.append(newYml)
            appended.toString()
        } else {
            newYml
        }
    }

    private void appendToYmlSubDocument(File applicationYmlFile, String previousApplicationYml) {
        appendToYmlSubDocument(applicationYmlFile, previousApplicationYml, applicationYmlFile)
    }

    private void appendToYmlSubDocument(File applicationYmlFile, String previousApplicationYml, File setTo) {
        String newApplicationYml = applicationYmlFile.text
        if(previousApplicationYml && newApplicationYml != previousApplicationYml) {
            setTo.text = createNewApplicationYml(previousApplicationYml, newApplicationYml)
        }
    }

    protected boolean initializeGroupAndName(String appName, boolean inplace) {
        if (!appName && !inplace) {
            MicronautConsole.getInstance().error("Specify an application name or use --inplace to create an application in the current directory")
            return false
        }
        String groupAndAppName = appName
        if(inplace) {
            appname = new File(".").canonicalFile.name
            if(!groupAndAppName) {
                groupAndAppName = appname
            }
        }

        if(!groupAndAppName) {
            MicronautConsole.getInstance().error("Specify an application name or use --inplace to create an application in the current directory")
            return false
        }

        try {
            defaultpackagename = establishGroupAndAppName(groupAndAppName)
        } catch (IllegalArgumentException e ) {
            MicronautConsole.instance.error(e.message)
            return false
        }
    }

    private void initializeVariables(String profileName, String micronautVersion) {
        variables.APPNAME = appname

        variables['defaultPackage'] = defaultpackagename
        variables['defaultPackage.path'] = defaultpackagename.replace('.', '/')

        def projectClassName = NameUtils.getNameFromScript(appname)

        variables['project.className'] = projectClassName
        variables['project.naturalName'] = NameUtils.getNaturalName(projectClassName)
        variables['project.name'] = NameUtils.getScriptName(projectClassName)
        variables['profile'] = profileName
        variables['version'] = micronautVersion
        variables['app.name'] = appname
        variables['app.group'] = groupname
    }

    private String establishGroupAndAppName(String groupAndAppName) {
        String defaultPackage
        List<String> parts = groupAndAppName.split(/\./) as List
        if (parts.size() == 1) {
            appname = parts[0]
            defaultPackage = createValidPackageName()
            groupname = defaultPackage
        } else {
            appname = parts[-1]
            groupname = parts[0..-2].join('.')
            defaultPackage = groupname
        }
        return defaultPackage
    }

    private String createValidPackageName() {
        String defaultPackage = appname.split(/[-]+/).collect { String token -> (token.toLowerCase().toCharArray().findAll { char ch -> Character.isJavaIdentifierPart(ch) } as char[]) as String }.join('.')
        if(!NameUtils.isValidJavaPackage(defaultPackage)) {
            throw new IllegalArgumentException("Cannot create a valid package name for [$appname]. Please specify a name that is also a valid Java package.")
        }
        return defaultPackage
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected void copySkeleton(Profile profile, Profile participatingProfile, CreateServiceCommandObject cmd) {
        def buildMergeProfileNames = profile.buildMergeProfileNames
        def excludes = profile.skeletonExcludes
        if (profile == participatingProfile) {
            excludes = []
        }
        excludes.addAll(cmd.skeletonExclude)
        String build = cmd.build

        AntBuilder ant = new ConsoleAntBuilder()

        def skeletonResource = participatingProfile.profileDir.createRelative("skeleton")
        File skeletonDir
        if(skeletonResource instanceof FileSystemResource) {
            skeletonDir = skeletonResource.file
        }
        else {
            // establish the JAR file name and extract
            def tmpDir = unzipProfile(ant, skeletonResource)
            skeletonDir = new File(tmpDir, "META-INF/profile/skeleton")
        }
        copySrcToTarget(ant, skeletonDir, excludes, profile.binaryExtensions)

        copySrcToTarget(ant, new File(skeletonDir, build + "-build"), excludes, profile.binaryExtensions)

        copyBuildFiles(new File(skeletonDir, build + "-build"), build, buildMergeProfileNames.contains(participatingProfile.name))

        ant.chmod(dir: targetDirectory, includes: profile.executablePatterns.join(' '), perm: 'u+x')
    }

    @CompileDynamic
    protected void copySrcToTarget(ConsoleAntBuilder ant, File srcDir, List excludes, Set<String> binaryFileExtensions) {
        if (!srcDir.exists()) {
            return
        }
        ant.copy(todir: targetDirectory, overwrite: true, encoding: 'UTF-8') {
            fileSet(dir: srcDir, casesensitive: false) {
                exclude(name: '**/.gitkeep')
                for (exc in excludes) {
                    exclude name: exc
                }
                exclude name: "**/"+BUILD_GRADLE
                exclude name: "**/"+POM_XML
                exclude name: "maven-build/"
                exclude name: "gradle-build/"
                binaryFileExtensions.each { ext ->
                    exclude(name: "**/*.${ext}")
                }
            }
            filterset {
                variables.each { k, v ->
                    filter(token: k, value: v)
                }
            }
            mapper {
                filtermapper {
                    variables.each { k, v ->
                        replacestring(from: "@${k}@".toString(), to: v)
                    }
                }
            }
        }
        ant.copy(todir: targetDirectory, overwrite: true) {
            fileSet(dir: srcDir, casesensitive: false) {
                binaryFileExtensions.each { ext ->
                    include(name: "**/*.${ext}")
                }
                for (exc in excludes) {
                    exclude name: exc
                }
                exclude name: "**/"+BUILD_GRADLE
                exclude name: "**/"+POM_XML
                exclude name: "maven-build/"
                exclude name: "gradle-build/"
            }
            mapper {
                filtermapper {
                    variables.each { k, v ->
                        replacestring(from: "@${k}@".toString(), to: v)
                    }
                }
            }
        }
    }


    static class CreateServiceCommandObject {
        String appName
        File baseDir
        String profileName
        String micronautVersion
        List<String> features
        boolean inplace = false
        String build = "gradle"
        MicronautConsole console
        List<String> skeletonExclude = []
    }
}
