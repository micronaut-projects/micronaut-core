/*
 * Copyright 2017 original authors
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

package io.micronaut.gradle.profile.task

import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.FileTree
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.incremental.IncrementalTaskInputs
import io.micronaut.cli.profile.commands.script.GroovyScriptCommandTransform
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * Compiles the classes for a profile
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class ProfileCompilerTask extends AbstractCompile {

    public static final String DEFAULT_COMPATIBILITY = "1.8"
    public static final String PROFILE_NAME = "name"
    public static final String PROFILE_COMMANDS = "commands"

    ProfileCompilerTask() {
        setSourceCompatibility(DEFAULT_COMPATIBILITY)
        setTargetCompatibility(DEFAULT_COMPATIBILITY)
    }

    @InputFile
    @Optional
    File config

    @OutputFile
    File profileFile

    @InputDirectory
    @Optional
    File templatesDir

    @TaskAction
    void execute(IncrementalTaskInputs inputs) {
        compile()
    }

    @Override
    @InputFiles
    FileTree getSource() {
        return (super.getSource() + project.files(config)).asFileTree
    }

    @Override
    void setDestinationDir(File destinationDir) {
        profileFile = new File(destinationDir, "META-INF/profile/profile.yml")
        super.setDestinationDir(destinationDir)
    }

    @Override
    protected void compile() {

        boolean profileYmlExists = config?.exists()

        def options = new DumperOptions()
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK)
        def yaml = new Yaml(options)
        Map<String, Object> profileData
        if(profileYmlExists) {
            profileData = (Map<String, Object>) config.withReader { BufferedReader r ->
                yaml.load(r)
            }
        }
        else {
            profileData = new LinkedHashMap<String, Object>()
        }

        profileData.put(PROFILE_NAME, project.name)

        profileFile.parentFile.mkdirs()


        if(!profileData.containsKey("extends")) {
            List<String> dependencies = []
            project.configurations.getByName("runtime").allDependencies.all() { Dependency d ->
                dependencies.add("${d.group}:${d.name}:${d.version}".toString())
            }
            profileData.put("extends", dependencies.join(','))
        }

        def groovySourceFiles = getSource().files.findAll() { File f ->
            f.name.endsWith('.groovy')
        } as File[]
        def ymlSourceFiles = getSource().files.findAll() { File f ->
            f.name.endsWith('.yml') && f.name != 'profile.yml'
        } as File[]

        Map<String, String> commandNames = [:]
        for(File f in groovySourceFiles) {
            def fn = f.name
            commandNames.put(fn - '.groovy', fn)
        }
        for(File f in ymlSourceFiles) {
            def fn = f.name
            commandNames.put(fn - '.yml', fn)
        }

        if(commandNames) {
            profileData.put(PROFILE_COMMANDS, commandNames)
        }

        if( profileYmlExists ) {
            def parentDir = config.parentFile.canonicalFile
            def featureDirs = new File(parentDir, "features").listFiles({ File f -> f.isDirectory() && !f.name.startsWith('.') } as FileFilter)
            if(featureDirs) {
                Map map = (Map)profileData.get("features")
                if(map == null) {
                    map = [:]
                    profileData.put("features", map)
                }
                List featureNames = []
                for(f in featureDirs) {
                    featureNames.add f.name
                }
                if(featureNames) {
                    map.put("provided", featureNames)
                }
                profileData.put("features", map)
            }
        }


        List<String> templates = []
        if(templatesDir?.exists()) {
            project.fileTree(templatesDir).visit { FileVisitDetails f ->
                if(!f.isDirectory() && !f.name.startsWith('.')) {
                    templates.add f.relativePath.pathString
                }
            }
        }

        if(templates) {
            profileData.put("templates", templates)
        }

        profileFile.withWriter { BufferedWriter w ->
            yaml.dump(profileData, w)
        }

        if(groovySourceFiles) {

            CompilerConfiguration configuration = new CompilerConfiguration()
            configuration.setScriptBaseClass("io.micronaut.cli.profile.commands.script.GroovyScriptCommand")
            destinationDir.mkdirs()
            configuration.setTargetDirectory(destinationDir)

            def importCustomizer = new ImportCustomizer()
            importCustomizer.addStarImports("io.micronaut.cli.interactive.completers")
            importCustomizer.addStarImports("io.micronaut.cli.util")
            importCustomizer.addStarImports("io.micronaut.cli.codegen.model")
            configuration.addCompilationCustomizers(importCustomizer,new ASTTransformationCustomizer(new GroovyScriptCommandTransform()))

            for(source in groovySourceFiles) {

                CompilationUnit compilationUnit = new CompilationUnit(configuration)
                configuration.compilationCustomizers.clear()
                configuration.compilationCustomizers.addAll(importCustomizer, new ASTTransformationCustomizer(new GroovyScriptCommandTransform()))
                compilationUnit.addSource(source)
                compilationUnit.compile()
            }
        }
    }
}
