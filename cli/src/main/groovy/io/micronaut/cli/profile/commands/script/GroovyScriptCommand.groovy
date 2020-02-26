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
package io.micronaut.cli.profile.commands.script

import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.boot.SpringInvoker
import io.micronaut.cli.codegen.model.ModelBuilder
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.logging.ConsoleLogger
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileCommand
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProfileRepositoryAware
import io.micronaut.cli.profile.commands.events.CommandEvents
import io.micronaut.cli.profile.commands.io.FileSystemInteraction
import io.micronaut.cli.profile.commands.io.FileSystemInteractionImpl
import io.micronaut.cli.profile.commands.io.ServerInteraction
import io.micronaut.cli.profile.commands.templates.TemplateRenderer
import io.micronaut.cli.profile.commands.templates.TemplateRendererImpl
import io.micronaut.cli.util.NameUtils
import io.micronaut.cli.util.VersionInfo
import picocli.CommandLine
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import picocli.groovy.PicocliBaseScript

/**
 * A base class for Groovy scripts that implement commands
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class GroovyScriptCommand extends PicocliBaseScript implements ProfileCommand, ProfileRepositoryAware, ConsoleLogger, ModelBuilder, FileSystemInteraction, TemplateRenderer, CommandEvents, ServerInteraction {

    Profile profile
    ProfileRepository profileRepository
    String name = getClass().name.contains('-') ? getClass().name : NameUtils.getScriptName(getClass().name)

    @Delegate
    ExecutionContext executionContext
    @Delegate
    TemplateRenderer templateRenderer
    @Delegate
    ConsoleLogger consoleLogger = MicronautConsole.getInstance()
    @Delegate
    FileSystemInteraction fileSystemInteraction

    /**
     * Allows invoking of Spring Boot's CLI
     */
    SpringInvoker spring = SpringInvoker.getInstance()

    /**
     * Access to Ant via AntBuilder
     */
    AntBuilder ant = new ConsoleAntBuilder()

    /**
     * The location of the user.home directory
     */
    String userHome = System.getProperty('user.home')

    /**
     * The version of Micronaut being used
     */
    String micronautVersion = VersionInfo.getVersion(getClass())

    CommandSpec getCommandSpec() {
        getOrCreateCommandLine().commandSpec
    }

    /**
     * @return The {@link MicronautConsole} instance
     */
    MicronautConsole getMicronautConsole() { executionContext.console }

    /**
     * Implementation of the handle method that runs the script
     *
     * @param executionContext The ExecutionContext
     * @return True if the script succeeds, false otherwise
     */
    @Override
    boolean handle(ExecutionContext executionContext) {
        setExecutionContext(executionContext)
        notify("${name}Start", executionContext)
        def result = runScriptBody() // PicocliBaseScript.run() would try to parse the input again
        notify("${name}End", executionContext)
        if (result instanceof Boolean) {
            return ((Boolean) result)
        }
        return true
    }

    /**
     * Method missing handler used to invoke other commands from a command script
     *
     * @param name The name of the command as a method name (for example 'run-app' would be runApp())
     * @param args The arguments to the command
     */
    def methodMissing(String name, args) {
        Object[] argsArray = (Object[]) args
        def commandName = NameUtils.getScriptName(name)
        def context = executionContext
        if (profile?.hasCommand(context, commandName)) {
            def parseResult = context.parseResult
            def newArgs = [commandName]
            newArgs.addAll argsArray.collect() { it.toString() }
            def newContext = new MicronautCli.ExecutionContextImpl(new CommandLine(parseResult.commandSpec()).parseArgs(newArgs as String[]), context)
            return profile.handleCommand(newContext)
        } else {
            throw new MissingMethodException(name, getClass(), argsArray)
        }
    }

    public void setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext
        this.consoleLogger = executionContext.console
        this.templateRenderer = new TemplateRendererImpl(executionContext, profile, profileRepository)
        this.fileSystemInteraction = new FileSystemInteractionImpl(executionContext)
        setDefaultPackage(executionContext.navigateConfig('defaultPackage'))
    }

    ExecutionContext getExecutionContext() {
        return executionContext
    }
}
