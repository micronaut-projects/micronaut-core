/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.cli.io.IOUtils
import io.micronaut.cli.io.support.*
import io.micronaut.cli.profile.Feature
import io.micronaut.cli.profile.OneOfFeatureGroup
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.ResetableCommand
import io.micronaut.cli.util.NameUtils
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Spec

import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

/**
 * Abstract superclass for commands creating a Micronaut service.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @author Remko Popma
 * @since 1.0
 */
@CompileStatic
@Command()
abstract class AbstractCreateCommand extends ArgumentCompletingCommand implements ProfileRepositoryAware, ResetableCommand {
    public static final String ENCODING = System.getProperty("file.encoding") ?: "UTF-8"

    protected static final String APPLICATION_YML = "application.yml"
    protected static final String BUILD_GRADLE = "build.gradle"
    protected static final String POM_XML = "pom.xml"

    ProfileRepository profileRepository
    Map<String, String> variables = [:]

    @Option(names = ['-i', '--inplace'], description = 'Create a service using the current directory')
    boolean inplace

    @Option(names = ['-m', '--mainClass'], paramLabel = 'MAINCLASS', description = 'The main class to use. Possible values: The full package and class name (ie. io.micronaut.CustomApplication).')
    String mainClass

    @Option(names = ['-p', '--profile'], paramLabel = 'PROFILE', description = 'The profile to use. Possible values: ${COMPLETION-CANDIDATES}.', completionCandidates = ProfileCompletionCandidates)
    String profile

    @Option(names = ['-f', '--features'], paramLabel = 'FEATURE', split = ",", description = 'The features to use. Possible values: ${COMPLETION-CANDIDATES}', completionCandidates = FeatureCompletionCandidates)
    List<String> features = []

    @Mixin
    private CommonOptionsMixin autoHelp // adds help, and version options to the command

    private Map<URL, File> unzippedDirectories = new LinkedHashMap<URL, File>()

    String appname
    String groupname
    String defaultpackagename
    File targetDirectory

    AbstractCreateCommand() {}

    abstract String getName();

    // Implementation note: this Command is first created and registered in the CommandRegistry.
    // At that point, the `setProfileRepository` method is called, but we cannot initialize
    // the completion candidates for this command yet, because the commandSpec is still null.
    //
    // When `setCommandSpec` is called by picocli, we can read from the profileRepository
    // to initialize the profile and feature completion candidates for this command.
    @Spec
    void setCommandSpec(CommandSpec commandSpec) {
        super.setCommandSpec(commandSpec)
        ProfileCompletionCandidates.updateCommandArguments(commandSpec, profileRepository)
        FeatureCompletionCandidates.initCommandArgs(this)
    }

    void setProfileRepository(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository
    }

    /**
     * Completion candidates generator showing the features available for the selected profile,
     * except the features that have already been selected.
     */
    static class FeatureCompletionCandidates implements Iterable<String> {
        // the command to get the profile repository, the selected profile, and selected features from
        private AbstractCreateCommand createCommand

        @Override
        Iterator<String> iterator() {
            if (!createCommand) {
                return Collections.emptyIterator()
            }

            def profile = createCommand.profileRepository.getProfile(createCommand.profile ?: createCommand.getDefaultProfile())
            def featureNames = profile.features.collect { Feature f -> f.name }.sort()

            if (createCommand instanceof AbstractCreateAppCommand) {
                SupportedLanguage.values().each {
                    featureNames.remove(it.name())
                }
            }
            // if no feature specified, show all features for the selected profile
            if (!createCommand.features) {
                return featureNames.iterator()
            }

            // otherwise only show features not chosen yet
            featureNames.findAll { String f ->
                !createCommand.features.contains(f)
            }.iterator()
        }

        static void initCommandArgs(AbstractCreateCommand command) {
            command.commandSpec.options().each { init(it.completionCandidates(), command) }
            command.commandSpec.positionalParameters().each { init(it.completionCandidates(), command) }
        }

        private static void init(def candidates, AbstractCreateCommand command) {
            if (candidates instanceof FeatureCompletionCandidates) {
                ((FeatureCompletionCandidates) candidates).createCommand = command
            }
        }
    }

    protected File getDestinationDirectory(File srcFile) {
        String searchDir = "skeleton"
        File srcDir = srcFile.parentFile
        File destDir
        if (srcDir.absolutePath.endsWith(searchDir)) {
            destDir = targetDirectory
        } else {
            int index = srcDir.absolutePath.lastIndexOf(searchDir) + searchDir.size() + 1
            String relativePath = (srcDir.absolutePath - srcDir.absolutePath.substring(0, index))
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

                ant.copy(file: "${srcDir}/.gitignore", todir: destDir, failonerror: false)

                if (!destFile.exists()) {
                    ant.copy file: srcFile, tofile: destFile
                } else if (allowMerge) {
                    def concatFile = "${destDir}/concat.gradle"
                    ant.move(file: destFile, tofile: concatFile)
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

                ant.copy(file: "${srcDir}/.gitignore", todir: destDir, failonerror: false)

                if (!destFile.exists()) {
                    ant.copy file: srcFile, tofile: destFile
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
        Set<File> files = (Set) []
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

    @CompileStatic(TypeCheckingMode.SKIP)
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

        if (cmd.features.any { SupportedLanguage.isValidValue(it) }) {
            MicronautConsole.instance.error("Language features cannot be used with the --features flag. Use --lang instead")
            return false
        }

        List<Feature> features = evaluateFeatures(profileInstance, cmd.features, cmd.lang).toList()

        if (profileInstance) {
            if (!initializeGroupAndName(cmd.appName, cmd.inplace)) {
                return false
            }

            initializeVariables(profileName, cmd.micronautVersion)

            Path appFullDirectory = Paths.get(cmd.baseDir.path, appname)

            File projectTargetDirectory = cmd.inplace ? new File(".").canonicalFile : appFullDirectory.toAbsolutePath().normalize().toFile()

            if (projectTargetDirectory.exists() && !cmd.inplace) {
                MicronautConsole.instance.error("Cannot create the project because the target directory already exists")
                return false
            }

            def profiles = profileRepository.getProfileAndDependencies(profileInstance)

            Map<Profile, File> targetDirs = [:]
            buildTargetFolders(profileInstance, targetDirs, projectTargetDirectory)

            for (Profile p : profiles) {
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

                for (Map.Entry<File, String> entry: ymlCache.entrySet()) {
                    if (entry.getKey().exists()) {
                        appendToYmlSubDocument(entry.getKey(), entry.getValue())
                    }
                }
            }
            AntBuilder ant = new ConsoleAntBuilder()

            for (Feature f in features) {
                def location = f.location

                File skeletonDir
                File tmpDir
                if (location instanceof FileSystemResource) {
                    skeletonDir = location.createRelative("skeleton").file
                } else {
                    tmpDir = unzipProfile(ant, location)
                    skeletonDir = new File(tmpDir, "META-INF/profile/features/$f.name/skeleton")
                }

                targetDirectory = targetDirs[f.profile]

                appendFeatureFiles(skeletonDir, cmd.build)

                List<String> featureExcludes = ['**/' + APPLICATION_YML]
                featureExcludes.addAll(profileInstance.skeletonExcludes)

                if (skeletonDir.exists()) {
                    copySrcToTarget(ant, skeletonDir, featureExcludes, profileInstance.binaryExtensions)
                    copySrcToTarget(ant, new File(skeletonDir, cmd.build + "-build"), featureExcludes, profileInstance.binaryExtensions)
                    ant.chmod(dir: targetDirectory, includes: profileInstance.executablePatterns.join(' '), perm: 'u+x')
                }
            }
            replaceBuildTokens(cmd, profileInstance, features, projectTargetDirectory)

            messageOnComplete(cmd.console, cmd, projectTargetDirectory)

            if (profileInstance.instructions) {
                cmd.console.addStatus(profileInstance.instructions)
            }
            MicronautCli.tiggerAppLoad()
            return true
        } else {
            MicronautConsole.getInstance().error "Cannot find profile $profileName"
            return false
        }
    }

    @Override
    void reset() {
        variables = [:]
        inplace = false
        profile = null
        features = []
        appname = null
        groupname = null
        defaultpackagename = null
        targetDirectory = null
    }

    protected void messageOnComplete(MicronautConsole console, CreateServiceCommandObject command, File targetDir) {
        console.addStatus("Application created at ${targetDir.absolutePath}")
    }

    protected boolean validateProfile(Profile profileInstance, String profileName) {
        if (profileInstance == null) {
            MicronautConsole.instance.error("Profile not found for name [$profileName]")
            return false
        }
        if (profileInstance.isAbstract()) {
            MicronautConsole.instance.error("Profile [$profileName] is designed only for extension and not direct usage")
            return false
        }
        return true
    }

    protected boolean  validateBuild(String buildName) {
        if (!SupportedBuildTool.names().contains(buildName)) {
            MicronautConsole.instance.error("Build not one of the supported types [$buildName]. Supported types are ${SupportedBuildTool.quoted().join(', ')}.")
            return false
        }
        return true
    }

    @CompileDynamic
    protected File unzipProfile(AntBuilder ant, Resource location) {

        def url = location.URL
        def tmpDir = unzippedDirectories.get(url)

        if (tmpDir == null) {
            def jarFile = IOUtils.findJarFile(url)
            tmpDir = File.createTempDir()
            tmpDir.deleteOnExit()
            ant.unzip(src: jarFile, dest: tmpDir)
            unzippedDirectories.put(url, tmpDir)
        }
        return tmpDir
    }

    @CompileDynamic
    protected void replaceBuildTokens(CreateServiceCommandObject cmd, Profile profile, List<Feature> features, File targetDirectory) {
        AntBuilder ant = new ConsoleAntBuilder()

        List<String> requestedFeatureNames = features.findAll { it.requested }*.name
        List<String> allFeatureNames = features*.name
        String testFramework = null
        String sourceLanguage = null

        if (profile.name != "profile") {

            if (requestedFeatureNames) {
                testFramework = evaluateTestFramework(requestedFeatureNames)
                sourceLanguage = evaluateSourceLanguage(requestedFeatureNames)
            }

            if (!testFramework) {
                testFramework = evaluateTestFramework(allFeatureNames)
            }

            if (!sourceLanguage) {
                sourceLanguage = evaluateSourceLanguage(allFeatureNames)
            }

        }

        BuildTokens buildTokens
        if (cmd.build == "gradle") {
            buildTokens = new GradleBuildTokens(appname, sourceLanguage, testFramework)
        } else if (cmd.build == "maven") {
            buildTokens = new MavenBuildTokens(appname, sourceLanguage, testFramework)
        } else {
            return
        }

        Map tokens = buildTokens.getTokens(profileRepository, profile, features)

        if (tokens == null) {
            return
        }

        tokens.put("micronautVersion", cmd.micronautVersion)

        if (profile.name.contains("function")) {
            if (cmd.lang == "kotlin") {
                tokens.put("mainClass", mainClass ? "${mainClass}FunctionKt" : "${this.defaultpackagename}.${variables['project.className']}FunctionKt")
            } else {
                tokens.put("mainClass", mainClass ? "${mainClass}Function" : "${this.defaultpackagename}.${variables['project.className']}Function")
            }
        } else {
            tokens.put("mainClass", mainClass ? mainClass : "${this.defaultpackagename}.Application")
        }

        ant.replace(dir: targetDirectory) {
            tokens.each { k, v ->
                replacefilter {
                    if (k) {
                        replacetoken("@${k}@".toString())
                    }
                    if (v) {
                        replacevalue(v)
                    }
                }
            }
            variables.each { k, v ->
                replacefilter {
                    replacetoken("@${k}@".toString())
                    replacevalue(v)
                }
            }
        }

        withTokens(buildTokens)
    }

    protected void withTokens(BuildTokens buildTokens) {
        //no-op
    }

    protected static String evaluateTestFramework(List<String> features) {
        String testFramework = null
        if (features.contains("spock"))
            testFramework = "spock"
        else if (features.contains("junit"))
            testFramework = "junit"
        else if (features.contains("spek"))
            testFramework = "spek"
        else if (features.contains("kotlintest"))
            testFramework = "kotlintest"

        testFramework
    }

    protected static String evaluateSourceLanguage(List<String> features) {
        String sourceLanguage = null
        if (features.contains("groovy"))
            sourceLanguage = "groovy"
        else if (features.contains("kotlin"))
            sourceLanguage = "kotlin"
        else if (features.contains("java"))
            sourceLanguage = "java"

        sourceLanguage
    }

    protected String evaluateProfileName() {
        this.profile ?: getDefaultProfile()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected static Iterable<Feature> evaluateFeatures(Profile profile, Set<String> requestedFeatures, String lang) {

        def (Set<Feature> features, List<String> validRequestedFeatureNames) = populateFeatures(profile, requestedFeatures, lang)
        features = addDependentFeatures(profile, features)

        features = pruneOneOfFeatures(profile, features)

        String language = getLanguage(features)?.capitalize()
        if (language) {
            MicronautConsole.getInstance().addStatus "Generating ${language} project..."
        }

        List<String> removedFeatures = validRequestedFeatureNames.findAll { !features*.name.contains(it) }

        if (removedFeatures) {
            StringBuilder warning = new StringBuilder("The following features are incompatible with other feature selections and have been removed from the project")
            warning.append(System.getProperty('line.separator'))
            warning.append("| ${removedFeatures.join(", ")}")
            MicronautConsole.getInstance().warn(warning.toString())
        }

        features
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected static Tuple populateFeatures(Profile profile, Set<String> requestedFeatures, String lang) {
        Set<Feature> features = []
        List<String> validRequestedFeatureNames = []

        if (requestedFeatures) {
            List<String> allFeatureNames = profile.features*.name
            validRequestedFeatureNames = requestedFeatures.intersect(allFeatureNames) as List<String>

            if (lang) validRequestedFeatureNames.add(lang)

            requestedFeatures.removeAll(allFeatureNames)
            requestedFeatures.each { String invalidFeature ->
                int idx = Math.min(invalidFeature.size(), 2)
                List possibleSolutions = allFeatureNames.findAll {
                    it.substring(0, idx) == invalidFeature.substring(0, idx)
                }
                StringBuilder warning = new StringBuilder("Feature ${invalidFeature} does not exist in the profile ${profile.name}!")
                if (possibleSolutions) {
                    warning.append(" Possible solutions: ")
                    warning.append(possibleSolutions.join(", "))
                }
                MicronautConsole.getInstance().warn(warning.toString())
            }

            Iterable<Feature> validFeatures = profile.features.findAll { Feature f -> validRequestedFeatureNames.contains(f.name) }

            features.addAll(validFeatures)
        } else {
            features.addAll(profile.defaultFeatures)
        }

        Feature langFeature = profile.features.find { Feature f ->
            if (lang) return f.name == lang
            return f.name == SupportedLanguage.java.name()
        } as Feature

        if (langFeature) {
            features.add(langFeature)
        }

        features.addAll(profile.requiredFeatures)

        if (validRequestedFeatureNames) {
            features = features.each { feature ->
                if (validRequestedFeatureNames.contains(feature.name) || (lang && feature.name == lang)) {
                    feature.requested = true
                }
            }.toSet()
        }

        features = addDependentFeatures(profile, features, true)

        [features, validRequestedFeatureNames]
    }


    protected static Set<Feature> pruneOneOfFeatures(Profile profile, Set<Feature> features) {
        if (!profile.oneOfFeatures.empty) {
            profile.oneOfFeatures.each { OneOfFeatureGroup group ->
                Set<Feature> toRemove = features.findAll { group.oneOfFeatures*.feature.contains(it) }

                Feature requestedOneOf = toRemove.find { it.requested }
                if (requestedOneOf) {
                    toRemove.remove(requestedOneOf)
                } else {
                    toRemove.remove(toRemove[0])
                }

                if (!toRemove.isEmpty()) {
                    features.removeAll(toRemove)
                    features = features.findAll { !it.getDependentFeatures(profile).any { toRemove.contains(it) } }
                }
            }
        }
        features
    }

    protected static Set<Feature> addDependentFeatures(Profile profile, Set<Feature> features, Boolean oneOfOnly = false) {
        Integer javaVersion = VersionInfo.getJavaVersion()
        features = features.findAll { it.isSupported(javaVersion) }

        def evicted = features.collect() { it.evictedFeatureNames }.flatten()
        if (evicted) {
            features.removeIf { evicted.contains(it.name) }
        }

        List<String> oneOfFeatureNames = []
        profile.oneOfFeatures.each { g ->
            oneOfFeatureNames.addAll(g.oneOfFeatures*.feature*.name)
        }

        List<Feature> dependentFeatures = []
        for (int i = 0; i < features.size(); i++) {
            Feature feature = features[i]

            List<Feature> dependents = feature.getDependentFeatures(profile).toList() + feature.getDefaultFeatures(profile).toList()
            for (Feature d: dependents) {

                if (!oneOfOnly || oneOfFeatureNames.contains(d.name)) {
                    if (d.isSupported(javaVersion)) {
                        if (feature.requested) {
                            d.requested = true
                        }
                        dependentFeatures.add(d)
                    }
                }
            }
        }

        if (dependentFeatures) {
            Set<Feature> newFeatures = new LinkedHashSet<>()
            newFeatures.addAll(dependentFeatures)
            newFeatures.addAll(features)
            return newFeatures
        } else {
            return features
        }
    }

    protected static String getLanguage(Set<Feature> features) {
        List<String> names = SupportedLanguage.values()*.name()
        features.find { names.contains(it.name) }?.name
    }

    protected String getDefaultProfile() {
        ProfileRepository.DEFAULT_PROFILE_NAME
    }

    protected static String createNewApplicationYml(String previousYml, String newYml) {
        def ln = System.getProperty("line.separator")
        if (newYml != previousYml) {
            StringBuilder appended = new StringBuilder(previousYml.length() + newYml.length() + 30)
            if (!previousYml.startsWith("---")) {
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
        if (previousApplicationYml && newApplicationYml != previousApplicationYml) {
            setTo.text = createNewApplicationYml(previousApplicationYml, newApplicationYml)
        }
    }

    protected boolean initializeGroupAndName(String appName, boolean inplace) {
        if (!appName && !inplace) {
            MicronautConsole.getInstance().error("Specify an application name or use --inplace to create an application in the current directory")
            return false
        }
        String groupAndAppName = appName
        if (inplace) {
            appname = new File(".").canonicalFile.name
            if (!groupAndAppName) {
                groupAndAppName = appname
            }
        }

        if (!groupAndAppName) {
            MicronautConsole.getInstance().error("Specify an application name or use --inplace to create an application in the current directory")
            return false
        }

        try {
            defaultpackagename = establishGroupAndAppName(groupAndAppName)

            if (!NameUtils.isValidServiceId(appname)) {
                MicronautConsole.instance.error("Application name should be all lower case and separated by dashes. For example: hello-world")
                return false
            } else {
                return true
            }
        } catch (IllegalArgumentException e) {
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
        variables['project.propertyName'] = NameUtils.getPropertyName(projectClassName)
        variables['profile'] = profileName
        variables['version'] = micronautVersion
        variables['app.name'] = appname
        variables['app.group'] = groupname
        variables['jansi'] = isWindows() ? "false" : "true"
    }

    private boolean isWindows() {
        System.getProperty("os.name")?.toLowerCase(Locale.ENGLISH)?.contains("windows")
    }

    private String establishGroupAndAppName(String groupAndAppName) {
        String[] groupApp = groupAppName(groupAndAppName)
        appname = groupApp[1]
        groupname = groupApp[0]

        return groupname
    }

    protected String[] groupAppName(String groupAndAppName) {
        String packageName
        String appName
        List<String> parts = groupAndAppName.split(/\./) as List
        if (parts.size() == 1) {
            appName = parts[0]
            packageName = createValidPackageName(appName)
        } else {
            appName = parts[-1]
            packageName = parts[0..-2].join('.')
        }

        [packageName, appName] as String[]
    }

    private String createValidPackageName(String appName) {
        String defaultPackage = appName.split(/[-]+/).collect { String token -> (token.toLowerCase().toCharArray().findAll { char ch -> Character.isJavaIdentifierPart(ch) } as char[]) as String }.join('.')
        if (!NameUtils.isValidJavaPackage(defaultPackage)) {
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
        File tmpDir
        if (skeletonResource instanceof FileSystemResource) {
            skeletonDir = skeletonResource.file
        } else {
            // establish the JAR file name and extract
            tmpDir = unzipProfile(ant, skeletonResource)
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
                exclude name: "**/" + BUILD_GRADLE
                exclude name: "**/" + POM_XML
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
                exclude name: "**/" + BUILD_GRADLE
                exclude name: "**/" + POM_XML
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
        Set<String> features
        String lang
        boolean inplace = false
        String build = "gradle"
        MicronautConsole console
        List<String> skeletonExclude = []
    }
}
