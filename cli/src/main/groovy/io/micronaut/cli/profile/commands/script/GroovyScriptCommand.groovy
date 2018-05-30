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

package io.micronaut.cli.profile.commands.script

import groovy.transform.CompileStatic
import io.micronaut.cli.MicronautCli
import io.micronaut.cli.boot.SpringInvoker
import io.micronaut.cli.codegen.model.ModelBuilder
import io.micronaut.cli.console.logging.ConsoleAntBuilder
import io.micronaut.cli.console.logging.ConsoleLogger
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.profile.CommandDescription
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

/**
 * A base class for Groovy scripts that implement commands
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
abstract class GroovyScriptCommand extends Script implements ProfileCommand, ProfileRepositoryAware, ConsoleLogger, ModelBuilder, FileSystemInteraction, TemplateRenderer, CommandEvents, ServerInteraction {

    Profile profile
    ProfileRepository profileRepository
    String name = getClass().name.contains('-') ? getClass().name : NameUtils.getScriptName(getClass().name)
    CommandDescription description = new CommandDescription(name)
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

    /**
     * Provides a description for the command
     *
     * @param desc The description
     * @param usage The usage information
     */
    void description(String desc, String usage) {
        // ignore, just a stub for documentation purposes, populated by CommandScriptTransform
    }

    /**
     * Provides a description for the command
     *
     * @param desc The description
     * @param usage The usage information
     */
    void description(String desc, Closure detail) {
        // ignore, just a stub for documentation purposes, populated by CommandScriptTransform
    }

    /**
     * Obtains details of the given flag if it has been set by the user
     *
     * @param name The name of the flag
     * @return The flag information, or null if it isn't set by the user
     */
    def flag(String name) {
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name)
        } else {
            def value = commandLine?.undeclaredOptions?.get(name)
            return value ?: null
        }
    }

    /**
     * @return The undeclared command line arguments
     */
    Map<String, Object> getArgsMap() {
        executionContext.commandLine.undeclaredOptions
    }

    /**
     * @return The arguments as a list of strings
     */
    List<String> getArgs() {
        executionContext.commandLine.remainingArgs
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
        def result = run()
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
            def commandLine = context.commandLine
            def newArgs = [commandName]
            newArgs.addAll argsArray.collect() { it.toString() }
            def newContext = new MicronautCli.ExecutionContextImpl(commandLine.parseNew(newArgs as String[]), context)
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
