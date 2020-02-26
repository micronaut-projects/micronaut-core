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
package io.micronaut.cli.profile.commands.factory

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.io.support.Resource
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.commands.script.GroovyScriptCommand
import io.micronaut.cli.util.NameUtils
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import picocli.groovy.PicocliScriptASTTransformation

/**
 * A {@link CommandFactory} that creates {@link Command} instances from Groovy scripts
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class GroovyScriptCommandFactory extends ResourceResolvingCommandFactory<GroovyScriptCommand> {

    final Collection<String> matchingFileExtensions = ["groovy"]
    final String fileNamePattern = /^.*\.(groovy)$/

    @Override
    protected GroovyScriptCommand readCommandFile(Resource resource) {
        GroovyClassLoader classLoader = createGroovyScriptCommandClassLoader()
        try {
            return (GroovyScriptCommand) classLoader.parseClass(resource.getInputStream(), resource.filename).newInstance()
        } catch (Throwable e) {
            MicronautConsole.getInstance().error("Failed to compile ${resource.filename}: " + e.getMessage(), e)
        }
    }

    @CompileDynamic
    public static GroovyClassLoader createGroovyScriptCommandClassLoader() {
        def configuration = new CompilerConfiguration()
        // TODO: Report bug, this fails with @CompileStatic with a ClassCastException
        String baseClassName = GroovyScriptCommand.class.getName()
        return createClassLoaderForBaseClass(configuration, baseClassName)
    }

    private
    static GroovyClassLoader createClassLoaderForBaseClass(CompilerConfiguration configuration, String baseClassName) {
        configuration.setScriptBaseClass(baseClassName)


        def importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports("io.micronaut.cli.interactive.completers")
        importCustomizer.addStarImports("io.micronaut.cli.util")
        importCustomizer.addStarImports("io.micronaut.cli.codegen.model")
        importCustomizer.addStarImports("io.micronaut.cli.profile.commands.script")
        importCustomizer.addImports("groovy.transform.Field")
        importCustomizer.addImports("picocli.groovy.PicocliScript")
        importCustomizer.addImports("picocli.CommandLine.Command")
        importCustomizer.addImports("picocli.CommandLine.Mixin")
        importCustomizer.addImports("picocli.CommandLine.Option")
        importCustomizer.addImports("picocli.CommandLine.Parameters")
        importCustomizer.addImports("picocli.CommandLine.ParentCommand")
        importCustomizer.addImports("picocli.CommandLine.Spec")
        importCustomizer.addImports("picocli.CommandLine.Unmatched")

        //configuration.addCompilationCustomizers(importCustomizer, new ASTTransformationCustomizer(new PicocliScriptASTTransformation()))
        configuration.addCompilationCustomizers(importCustomizer)
        def classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader, configuration)
        return classLoader
    }

    @Override
    protected String evaluateFileName(String fileName) {
        def fn = super.evaluateFileName(fileName)
        return fn.contains('-') ? fn.toLowerCase() : NameUtils.getScriptName(fn)
    }

    @Override
    protected Command createCommand(Profile profile, String commandName, Resource resource, GroovyScriptCommand data) {
        data.setProfile(profile)
        return data
    }
}
