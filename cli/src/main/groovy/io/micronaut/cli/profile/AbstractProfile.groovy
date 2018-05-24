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

package io.micronaut.cli.profile

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.cli.config.NavigableMap
import io.micronaut.cli.console.parsing.ScriptNameResolver
import io.micronaut.cli.interactive.completers.StringsCompleter
import io.micronaut.cli.io.IOUtils
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.profile.commands.CommandRegistry
import io.micronaut.cli.profile.commands.DefaultMultiStepCommand
import io.micronaut.cli.profile.commands.script.GroovyScriptCommand
import io.micronaut.cli.util.CliSettings
import io.micronaut.cli.util.CosineSimilarity
import jline.console.completer.ArgumentCompleter
import jline.console.completer.Completer
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion
import org.eclipse.aether.util.graph.selector.ExclusionDependencySelector
import org.yaml.snakeyaml.Yaml

/**
 * Abstract implementation of the profile class
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@ToString(includes = ['name'])
abstract class AbstractProfile implements Profile {
    protected final Resource profileDir
    protected String name
    protected List<Profile> parentProfiles
    protected Map<String, Command> commandsByName
    protected NavigableMap navigableConfig
    protected ProfileRepository profileRepository
    protected List<Dependency> dependencies = []
    protected List<String> repositories = []
    protected List<String> parentNames = []
    protected List<String> buildRepositories = []
    protected List<String> buildPlugins = []
    protected List<String> buildExcludes = []
    protected List<String> skeletonExcludes = []
    protected List<String> binaryExtensions = []
    protected List<String> executablePatterns = []
    protected final List<Command> internalCommands = []
    protected List<String> buildMerge = null
    protected List<Feature> features = []
    protected Set<String> defaultFeaturesNames = []
    protected Set<String> requiredFeatureNames = []
    protected String parentTargetFolder
    protected final ClassLoader classLoader
    protected ExclusionDependencySelector exclusionDependencySelector = new ExclusionDependencySelector()
    protected String description = "";
    protected String instructions = "";
    protected String version = CliSettings.package.implementationVersion

    AbstractProfile(Resource profileDir) {
        this(profileDir, AbstractProfile.getClassLoader())
    }

    AbstractProfile(Resource profileDir, ClassLoader classLoader) {
        this.classLoader = classLoader
        this.profileDir = profileDir


        def url = profileDir.getURL()
        def jarFile = IOUtils.findJarFile(url)
        def pattern = ~/.+-(\d.+)\.jar/


        def path
        if (jarFile != null) {
            path = jarFile.name
        } else if (url != null) {
            def p = url.path
            path = p.substring(0, p.indexOf('.jar') + 4)
        }
        if (path) {
            def matcher = pattern.matcher(path)
            if (matcher.matches()) {
                this.version = matcher.group(1)
            }
        }
    }

    String getVersion() {
        return version
    }

    protected void initialize() {
        def profileYml = profileDir.createRelative("profile.yml")
        def profileConfig = (Map<String, Object>) new Yaml().loadAs(profileYml.getInputStream(), Map)

        name = profileConfig.get("name")?.toString()
        description = profileConfig.get("description")?.toString() ?: ''
        instructions = profileConfig.get("instructions")?.toString() ?: ''

        def parents = profileConfig.get("extends")
        if (parents) {
            parentNames = parents.toString().split(',').collect() { String name -> name.trim() }
        }
        if (this.name == null) {
            throw new IllegalStateException("Profile name not set. Profile for path ${profileDir.URL} is invalid")
        }
        def map = new NavigableMap()
        map.merge(profileConfig)
        navigableConfig = map
        def commandsByName = profileConfig.get("commands")
        if (commandsByName instanceof Map) {
            def commandsMap = (Map) commandsByName
            for (clsName in commandsMap.keySet()) {
                def fileName = commandsMap[clsName].toString()
                if (fileName.endsWith(".groovy")) {
                    GroovyScriptCommand cmd = (GroovyScriptCommand) classLoader.loadClass(clsName.toString()).newInstance()
                    cmd.profile = this
                    cmd.profileRepository = profileRepository
                    internalCommands.add cmd
                } else if (fileName.endsWith('.yml')) {
                    def yamlCommand = profileDir.createRelative("commands/$fileName")
                    if (yamlCommand.exists()) {
                        def data = new Yaml().loadAs(yamlCommand.getInputStream(), Map.class)
                        Command cmd = new DefaultMultiStepCommand(clsName.toString(), this, data)
                        Object minArguments = data?.minArguments
                        cmd.minArguments = minArguments instanceof Integer ? (Integer) minArguments : 1
                        internalCommands.add cmd
                    }

                }
            }
        }

        def featuresConfig = profileConfig.get("features")
        if (featuresConfig instanceof Map) {
            Map featureMap = (Map) featuresConfig
            def featureList = (List) featureMap.get("provided") ?: Collections.emptyList()
            def defaultFeatures = (List) featureMap.get("defaults") ?: Collections.emptyList()
            def requiredFeatures = (List) featureMap.get("required") ?: Collections.emptyList()
            for (fn in featureList) {
                def featureData = profileDir.createRelative("features/${fn}/feature.yml")
                if (featureData.exists()) {
                    def f = new DefaultFeature(this, fn.toString(), profileDir.createRelative("features/$fn/"))
                    features.add f
                }
            }

            defaultFeaturesNames.addAll(defaultFeatures)
            requiredFeatureNames.addAll(requiredFeatures)
        }



        def dependencyMap = profileConfig.get("dependencies")

        if (dependencyMap instanceof Map) {
            for (entry in ((Map) dependencyMap)) {
                def scope = entry.key
                def value = entry.value
                if (value instanceof List) {
                    if ("excludes".equals(scope)) {
                        List<Exclusion> exclusions = []
                        for (dep in ((List) value)) {
                            def artifact = new DefaultArtifact(dep.toString())
                            exclusions.add new Exclusion(artifact.groupId ?: null, artifact.artifactId ?: null, artifact.classifier ?: null, artifact.extension ?: null)
                        }
                        exclusionDependencySelector = new ExclusionDependencySelector(exclusions)
                    } else {

                        for (dep in ((List) value)) {
                            String coords = dep.toString()
                            if (coords.count(':') == 1) {
                                coords = "$coords:BOM"
                            }
                            dependencies.add new Dependency(new DefaultArtifact(coords), scope.toString())
                        }
                    }
                }
            }
        }

        this.repositories = (List<String>) navigableConfig.get("repositories", [])

        this.buildRepositories = (List<String>) navigableConfig.get("build.repositories", [])
        this.buildPlugins = (List<String>) navigableConfig.get("build.plugins", [])
        this.buildExcludes = (List<String>) navigableConfig.get("build.excludes", [])
        this.buildMerge = (List<String>) navigableConfig.get("build.merge", null)
        this.parentTargetFolder = (String) navigableConfig.get("skeleton.parent.target", null)
        this.skeletonExcludes = (List<String>) navigableConfig.get("skeleton.excludes", [])
        this.binaryExtensions = (List<String>) navigableConfig.get("skeleton.binaryExtensions", [])
        this.executablePatterns = (List<String>) navigableConfig.get("skeleton.executable", [])
    }

    String getDescription() {
        description
    }

    String getInstructions() {
        instructions
    }

    Set<String> getBinaryExtensions() {
        Set<String> calculatedBinaryExtensions = []
        def parents = getExtends()
        for (profile in parents) {
            calculatedBinaryExtensions.addAll(profile.binaryExtensions)
        }
        calculatedBinaryExtensions.addAll(binaryExtensions)
        return calculatedBinaryExtensions
    }

    Set<String> getExecutablePatterns() {
        Set<String> calculatedExecutablePatterns = []
        def parents = getExtends()
        for (profile in parents) {
            calculatedExecutablePatterns.addAll(profile.executablePatterns)
        }
        calculatedExecutablePatterns.addAll(executablePatterns)
        return calculatedExecutablePatterns
    }

    @Override
    Iterable<Feature> getDefaultFeatures() {
        getFeatures().findAll() { Feature f -> defaultFeaturesNames.contains(f.name) }
    }

    @Override
    Iterable<Feature> getRequiredFeatures() {
        def requiredFeatureInstances = getFeatures().findAll() { Feature f -> requiredFeatureNames.contains(f.name) }
        if (requiredFeatureInstances.size() != requiredFeatureNames.size()) {
            throw new IllegalStateException("One or more required features were not found on the classpath. Required features: $requiredFeatureNames")
        }
        return requiredFeatureInstances
    }

    @Override
    Iterable<Feature> getFeatures() {
        Set<Feature> calculatedFeatures = []
        calculatedFeatures.addAll(features)
        def parents = getExtends()
        for (profile in parents) {
            calculatedFeatures.addAll profile.features
        }
        return calculatedFeatures
    }

    @Override
    List<String> getBuildMergeProfileNames() {
        if (buildMerge != null) {
            return this.buildMerge
        } else {
            List<String> mergeNames = []
            for (parent in getExtends()) {
                mergeNames.add(parent.name)
            }
            mergeNames.add(name)
            return mergeNames
        }
    }

    @Override
    List<String> getBuildRepositories() {
        List<String> calculatedRepositories = []
        if (buildRepositories.empty) {
            def parents = getExtends()
            for (profile in parents) {
                calculatedRepositories.addAll(profile.buildRepositories)
            }
        } else {
            calculatedRepositories.addAll(buildRepositories)
        }
        return calculatedRepositories
    }

    @Override
    List<String> getBuildPlugins() {
        List<String> calculatedPlugins = []
        def parents = getExtends()
        for (profile in parents) {
            def dependencies = profile.buildPlugins
            for (dep in dependencies) {
                if (!buildExcludes.contains(dep))
                    calculatedPlugins.add(dep)
            }
        }
        calculatedPlugins.addAll(buildPlugins)
        return calculatedPlugins
    }

    @Override
    List<String> getRepositories() {
        List<String> calculatedRepositories = []
        if (repositories.empty) {
            def parents = getExtends()
            for (profile in parents) {
                calculatedRepositories.addAll(profile.repositories)
            }
        } else {
            calculatedRepositories.addAll(repositories)
        }
        return calculatedRepositories
    }

    List<Dependency> getDependencies() {
        List<Dependency> calculatedDependencies = []
        def parents = getExtends()
        for (profile in parents) {
            def dependencies = profile.dependencies
            for (dep in dependencies) {
                if (exclusionDependencySelector.selectDependency(dep)) {
                    calculatedDependencies.add(dep)
                }
            }
        }
        calculatedDependencies.addAll(dependencies)
        return calculatedDependencies
    }

    ProfileRepository getProfileRepository() {
        return profileRepository
    }

    void setProfileRepository(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository
    }

    Resource getProfileDir() {
        return profileDir
    }


    @Override
    NavigableMap getConfiguration() {
        navigableConfig
    }

    @Override
    Resource getTemplate(String path) {
        return profileDir.createRelative("templates/$path")
    }

    @Override
    Iterable<Profile> getExtends() {
        return parentNames.collect() { String name ->
            def parent = profileRepository.getProfile(name, true)
            if (parent == null) {
                throw new IllegalStateException("Profile [$name] declares an invalid dependency on parent profile [$name]")
            }
            return parent
        }
    }

    @Override
    Iterable<Completer> getCompleters(io.micronaut.cli.profile.ProjectContext context) {
        def commands = getCommands(context)

        Collection<Completer> completers = []

        for (Command cmd in commands) {
            def description = cmd.description

            def commandNameCompleter = new StringsCompleter(cmd.name)
            if (cmd instanceof Completer) {
                completers << new ArgumentCompleter(commandNameCompleter, (Completer) cmd)
            } else {
                if (description.completer) {
                    if (description.flags) {
                        completers << new ArgumentCompleter(commandNameCompleter,
                                                            description.completer,
                                                            new StringsCompleter(description.flags.collect() { CommandArgument arg -> "-$arg.name".toString() }))
                    } else {
                        completers << new ArgumentCompleter(commandNameCompleter, description.completer)
                    }

                } else {
                    if (description.flags) {
                        completers << new ArgumentCompleter(commandNameCompleter, new StringsCompleter(description.flags.collect() { CommandArgument arg -> "-$arg.name".toString() }))
                    } else {
                        completers << commandNameCompleter
                    }
                }
            }
        }

        return completers
    }

    @Override
    Command getCommand(io.micronaut.cli.profile.ProjectContext context, String name) {
        getCommands(context)
        return commandsByName[name]
    }

    @Override
    Iterable<Command> getCommands(io.micronaut.cli.profile.ProjectContext context) {
        if (commandsByName == null) {
            commandsByName = [:]
            List excludes = []
            def registerCommand = { Command command ->
                def name = command.name
                if (!commandsByName.containsKey(name) && !excludes.contains(name)) {
                    if (command instanceof ProfileRepositoryAware) {
                        ((ProfileRepositoryAware) command).setProfileRepository(profileRepository)
                    }
                    commandsByName[name] = command
                    def desc = command.description
                    def synonyms = desc.synonyms
                    if (synonyms) {
                        for (syn in synonyms) {
                            commandsByName[syn] = command
                        }
                    }
                    if (command instanceof ProjectContextAware) {
                        ((ProjectContextAware) command).projectContext = context
                    }
                    if (command instanceof ProfileCommand) {
                        ((ProfileCommand) command).profile = this
                    }
                }
            }

            CommandRegistry.findCommands(this).each(registerCommand)

            def parents = getExtends()
            if (parents) {
                excludes = (List) configuration.navigate("command", "excludes") ?: []
                registerParentCommands(context, parents, registerCommand)
            }
        }
        return commandsByName.values()
    }

    protected void registerParentCommands(ProjectContext context, Iterable<Profile> parents, Closure registerCommand) {
        for (parent in parents) {
            parent.getCommands(context).each registerCommand

            def extended = parent.extends
            if (extended) {
                registerParentCommands context, extended, registerCommand
            }
        }
    }

    @Override
    boolean hasCommand(io.micronaut.cli.profile.ProjectContext context, String name) {
        getCommands(context) // ensure initialization
        return commandsByName.containsKey(name)
    }

    @Override
    boolean handleCommand(io.micronaut.cli.profile.ExecutionContext context) {
        getCommands(context) // ensure initialization

        def commandLine = context.commandLine
        def commandName = commandLine.commandName
        def cmd = commandsByName[commandName]
        if (cmd) {
            def requiredArguments = cmd?.description?.arguments
            int requiredArgumentCount = requiredArguments?.findAll() { CommandArgument ca -> ca.required }?.size() ?: 0
            if (commandLine.remainingArgs.size() < requiredArgumentCount) {
                context.console.error "Command [$commandName] missing required arguments: ${requiredArguments*.name}. Type 'mn help $commandName' for more info."
                return false
            } else {
                return cmd.handle(context)
            }
        } else {
            // Apply command name expansion (rA for run-app, tA for test-app etc.)
            cmd = commandsByName.values().find() { Command c ->
                ScriptNameResolver.resolvesTo(commandName, c.name)
            }
            if (cmd) {
                return cmd.handle(context)
            } else {
                context.console.error("Command not found ${context.commandLine.commandName}")
                def mostSimilar = CosineSimilarity.mostSimilar(commandName, commandsByName.keySet())
                List<String> topMatches = mostSimilar.subList(0, Math.min(3, mostSimilar.size()));
                if (topMatches) {
                    context.console.log("Did you mean: ${topMatches.join(' or ')}?")
                }
                return false
            }

        }
    }

    @Override
    String getParentSkeletonDir() {
        this.parentTargetFolder
    }

    @Override
    File getParentSkeletonDir(File parent) {
        if (parentSkeletonDir) {
            new File(parent, parentSkeletonDir)
        } else {
            parent
        }
    }

    List<String> getSkeletonExcludes() {
        this.skeletonExcludes
    }
}
