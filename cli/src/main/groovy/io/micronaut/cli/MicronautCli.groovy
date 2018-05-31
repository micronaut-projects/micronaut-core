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

package io.micronaut.cli

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.cli.config.CodeGenConfig
import io.micronaut.cli.config.ConfigMap
import io.micronaut.cli.config.NavigableMap
import io.micronaut.cli.console.logging.MicronautConsole
import io.micronaut.cli.console.parsing.CommandLine
import io.micronaut.cli.console.parsing.CommandLineParser
import io.micronaut.cli.console.parsing.DefaultCommandLine
import io.micronaut.cli.console.proxy.SystemPropertiesAuthenticator
import io.micronaut.cli.interactive.completers.EscapingFileNameCompletor
import io.micronaut.cli.interactive.completers.RegexCompletor
import io.micronaut.cli.interactive.completers.SortedAggregateCompleter
import io.micronaut.cli.interactive.completers.StringsCompleter
import io.micronaut.cli.io.support.SystemStreamsRedirector
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandArgument
import io.micronaut.cli.profile.CommandCancellationListener
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProjectContext
import io.micronaut.cli.profile.commands.CommandCompleter
import io.micronaut.cli.profile.commands.CommandRegistry
import io.micronaut.cli.profile.repository.MavenProfileRepository
import io.micronaut.cli.profile.repository.RepositoryConfiguration
import io.micronaut.cli.util.CliSettings
import io.micronaut.cli.util.VersionInfo
import jline.UnixTerminal
import jline.console.UserInterruptException
import jline.console.completer.ArgumentCompleter
import jline.internal.NonBlockingInputStream

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Main class for the Micronaut command line. Handles interactive mode and running Micronaut commands within the context of a profile
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@CompileStatic
class MicronautCli {

    static final String ARG_SPLIT_PATTERN = /(?<!\\)\s+/
    public static final String DEFAULT_PROFILE_NAME = ProfileRepository.DEFAULT_PROFILE_NAME
    private static final int KEYPRESS_CTRL_C = 3
    private static final int KEYPRESS_ESC = 27
    private static final String USAGE_MESSAGE = "create-app [NAME]"
    private static
    final String FEDERATION_USAGE_MESSAGE = "create-federation [NAME] --services [SERVICE_NAME],[SERVICE_NAME],..."
    final String FUNCTION_USAGE_MESSAGE = "create-function [NAME]"
    private final SystemStreamsRedirector originalStreams = SystemStreamsRedirector.original()
    // store original System.in, System.out and System.err
    private static ExecutionContext currentExecutionContext = null

    private static boolean interactiveModeActive
    private static boolean tiggerAppLoad = false
    private static final NavigableMap SETTINGS_MAP = new NavigableMap()

    static {
        if (CliSettings.SETTINGS_FILE.exists()) {
            try {
                SETTINGS_MAP.merge new ConfigSlurper().parse(CliSettings.SETTINGS_FILE.toURI().toURL())
            } catch (Throwable e) {
                e.printStackTrace()
                System.err.println("ERROR: Problem loading $CliSettings.SETTINGS_FILE: ${e.message}")
            }

            try {
                Runtime.addShutdownHook {
                    try {
                        Thread.start {
                            currentExecutionContext?.cancel()
                        }.join(1000)
                    } catch (Throwable e) {
                        // ignore
                    }
                }
            } catch (e) {
                // ignore
            }
        }
    }


    SortedAggregateCompleter aggregateCompleter = new SortedAggregateCompleter()
    CommandLineParser cliParser = new CommandLineParser()
    boolean keepRunning = true
    Boolean ansiEnabled = null
    boolean integrateGradle = true
    Character defaultInputMask = null
    ProfileRepository profileRepository
    CodeGenConfig applicationConfig
    ProjectContext projectContext
    Profile profile = null
    List<RepositoryConfiguration> profileRepositories = [MavenProfileRepository.DEFAULT_REPO]

    /**
     * Obtains a value from USER_HOME/.micronaut/settings.yml
     *
     * @param key the property name to resolve
     * @param targetType the expected type of the property value
     * @param defaultValue The default value
     */
    public static <T> T getSetting(String key, Class<T> targetType = Object.class, T defaultValue = null) {
        def value = SETTINGS_MAP.get(key, defaultValue)
        if (value == null) {
            return null
        } else if (targetType.isInstance(value)) {
            return (T) value
        } else {
            try {
                return value.asType(targetType)
            } catch (Throwable e) {
                return null
            }
        }
    }
    /**
     * Main method for running via the command line
     *
     * @param args The arguments
     */
    public static void main(String[] args) {

        Authenticator.setDefault(getSetting(CliSettings.AUTHENTICATOR, Authenticator, new SystemPropertiesAuthenticator()))
        def proxySelector = getSetting(CliSettings.PROXY_SELECTOR, ProxySelector)
        if (proxySelector != null) {
            ProxySelector.setDefault(proxySelector)
        }

        MicronautCli cli = new MicronautCli()
        try {
            exit(cli.execute(args))
        }
        catch (Throwable e) {
            while (e.cause && e != e.cause) {
                e = e.cause
            }
            MicronautConsole.instance.error("Error occurred running Micronaut CLI: $e.message", e)
            exit(1)
        }
    }

    static void exit(int code) {
        MicronautConsole.instance.cleanlyExit(code)
    }

    static boolean isInteractiveModeActive() {
        return interactiveModeActive
    }

    static void tiggerAppLoad() {
        MicronautCli.tiggerAppLoad = true
    }

    private int getBaseUsage() {
        System.out.println "Usage: \n\t $USAGE_MESSAGE \n\t $FEDERATION_USAGE_MESSAGE \n\t $FUNCTION_USAGE_MESSAGE  \n\n"
        this.execute "list-profiles"
        System.out.println "\nType 'mn help' or 'mn -h' for more information."

        return 1
    }

    /**
     * Execute the given command
     *
     * @param args The arguments
     * @return The exit status code
     */
    public int execute(String... args) {
        CommandLine mainCommandLine = cliParser.parse(args)

        if (mainCommandLine.hasOption(CommandLine.VERBOSE_ARGUMENT)) {
            System.setProperty("micronaut.verbose", "true")
            System.setProperty("micronaut.full.stacktrace", "true")
        }
        if (mainCommandLine.hasOption(CommandLine.STACKTRACE_ARGUMENT)) {
            System.setProperty("micronaut.show.stacktrace", "true")
        }

        if (mainCommandLine.hasOption(CommandLine.VERSION_ARGUMENT) || mainCommandLine.hasOption('v')) {
            def console = MicronautConsole.instance
            console.addStatus("Micronaut Version: ${VersionInfo.getVersion(MicronautCli)}")
            console.addStatus("JVM Version: ${System.getProperty('java.version')}")
            exit(0)
        }

        if (mainCommandLine.hasOption(CommandLine.HELP_ARGUMENT) || mainCommandLine.hasOption('h')) {
            profileRepository = createMavenProfileRepository()
            def cmd = CommandRegistry.getCommand("help", profileRepository)
            cmd.handle(createExecutionContext(mainCommandLine))
            exit(0)
        }

        File micronautCli = new File("micronaut-cli.yml")
        File profileYml = new File("profile.yml")
        if (!micronautCli.exists() && !profileYml.exists()) {
            //Execution path for CLI outside of a project
            profileRepository = createMavenProfileRepository()
            if (!mainCommandLine || !mainCommandLine.commandName) {
                integrateGradle = false
                def console = MicronautConsole.getInstance()
                // force resolve of all profiles
                profileRepository.getAllProfiles()
                def commandNames = CommandRegistry.findCommands(profileRepository).collect() { Command cmd -> cmd.name }
                console.reader.addCompleter(new StringsCompleter(commandNames))
                console.reader.addCompleter(new CommandCompleter(CommandRegistry.findCommands(profileRepository)))
                profile = [handleCommand: { ExecutionContext context ->

                    def cl = context.commandLine
                    def name = cl.commandName
                    def cmd = CommandRegistry.getCommand(name, profileRepository)
                    if (cmd != null) {
                        return executeCommandWithArgumentValidation(cmd, cl)
                    } else {
                        console.error("Command not found [$name]")
                        return false
                    }
                }] as Profile

                startInteractiveMode(console)
                return 0
            }
            def cmd = CommandRegistry.getCommand(mainCommandLine.commandName, profileRepository)
            if (cmd) {
                return executeCommandWithArgumentValidation(cmd, mainCommandLine) ? 0 : 1
            } else {
                return getBaseUsage()
            }

        } else {
            //Execution path for CLI within a project
            initializeApplication(mainCommandLine)
            if (mainCommandLine.commandName) {
                return handleCommand(mainCommandLine) ? 0 : 1
            } else {
                handleInteractiveMode()
            }
        }
        return 0
    }

    protected boolean executeCommandWithArgumentValidation(Command cmd, CommandLine mainCommandLine) {
        def arguments = cmd.description.arguments
        def requiredArgs = arguments.count { CommandArgument arg -> arg.required }
        if (mainCommandLine.remainingArgs.size() < requiredArgs) {
            outputMissingArgumentsMessage cmd
            return false
        } else {
            return cmd.handle(createExecutionContext(mainCommandLine))
        }
    }

    protected void initializeApplication(CommandLine mainCommandLine) {
        applicationConfig = loadApplicationConfig()
        File profileYml = new File("profile.yml")
        if (profileYml.exists()) {
            // use the profile for profiles
            applicationConfig.put(CliSettings.PROFILE, "profile")
        }

        MicronautConsole console = MicronautConsole.instance
        console.ansiEnabled = !mainCommandLine.hasOption(CommandLine.NOANSI_ARGUMENT)
        console.defaultInputMask = defaultInputMask
        if (ansiEnabled != null) {
            console.ansiEnabled = ansiEnabled
        }
        File baseDir = new File(".").canonicalFile
        projectContext = new ProjectContextImpl(console, baseDir, applicationConfig)
        initializeProfile()
    }

    protected MavenProfileRepository createMavenProfileRepository() {
        def profileRepos = getSetting(CliSettings.PROFILE_REPOSITORIES, Map.class, Collections.emptyMap())
        if (!profileRepos.isEmpty()) {
            profileRepositories.clear()
            for (repoName in profileRepos.keySet()) {
                def data = profileRepos.get(repoName)
                if (data instanceof Map) {
                    def uri = data.get("url")
                    def snapshots = data.get('snapshotsEnabled')
                    if (uri != null) {
                        boolean enableSnapshots = snapshots != null ? Boolean.valueOf(snapshots.toString()) : false
                        RepositoryConfiguration repositoryConfiguration
                        final String username = data.get('username')
                        final String password = data.get('password')
                        if (username != null && password != null) {
                            repositoryConfiguration = new RepositoryConfiguration(repoName.toString(), new URI(uri.toString()), enableSnapshots, username, password)
                        } else {
                            repositoryConfiguration = new RepositoryConfiguration(repoName.toString(), new URI(uri.toString()), enableSnapshots)
                        }
                        profileRepositories.add(repositoryConfiguration)
                    }
                }
            }
        }
        return new MavenProfileRepository(profileRepositories)
    }

    protected void outputMissingArgumentsMessage(Command cmd) {
        def console = MicronautConsole.instance
        console.error("Command $cmd.name is missing required arguments:")
        for (CommandArgument arg in cmd.description.arguments.findAll { CommandArgument ca -> ca.required }) {
            console.log("* $arg.name - $arg.description")
        }
    }

    ExecutionContext createExecutionContext(CommandLine commandLine) {
        new ExecutionContextImpl(commandLine, projectContext)
    }

    Boolean handleCommand(CommandLine commandLine) {

        handleCommand(createExecutionContext(commandLine))
    }

    Boolean handleCommand(ExecutionContext context) {
        def console = MicronautConsole.getInstance()
        synchronized (MicronautCli) {
            try {
                currentExecutionContext = context
                if (handleBuiltInCommands(context)) {
                    return true
                }

                def mainCommandLine = context.getCommandLine()
                if (mainCommandLine.hasOption(CommandLine.STACKTRACE_ARGUMENT)) {
                    console.setStacktrace(true)
                } else {
                    console.setStacktrace(false)
                }

                if (mainCommandLine.hasOption(CommandLine.VERBOSE_ARGUMENT)) {
                    System.setProperty("micronaut.verbose", "true")
                    System.setProperty("micronaut.full.stacktrace", "true")
                } else {
                    System.setProperty("micronaut.verbose", "false")
                    System.setProperty("micronaut.full.stacktrace", "false")
                }
                if (profile.handleCommand(context)) {
                    if (tiggerAppLoad) {
                        console.updateStatus("Initializing application. Please wait...")
                        try {
                            initializeApplication(context.commandLine)
                            setupCompleters()
                        } finally {
                            tiggerAppLoad = false
                        }
                    }
                    return true;
                }
                return false
            }
            catch (Throwable e) {
                console.error("Command [${context.commandLine.commandName}] error: ${e.message}", e)
                return false
            } finally {
                currentExecutionContext = null
            }
        }
    }


    private void handleInteractiveMode() {
        MicronautConsole console = setupCompleters()
        startInteractiveMode(console)
    }

    protected MicronautConsole setupCompleters() {
        MicronautConsole console = projectContext.console

        def consoleReader = console.reader
        consoleReader.setHandleUserInterrupt(true)
        def completers = aggregateCompleter.getCompleters()

        console.resetCompleters()
        // add bang operator completer
        completers.add(new ArgumentCompleter(
            new RegexCompletor("!\\w+"), new EscapingFileNameCompletor())
        )

        completers.addAll((profile.getCompleters(projectContext) ?: []) as Collection)
        consoleReader.addCompleter(aggregateCompleter)
        return console
    }

    protected void startInteractiveMode(MicronautConsole console) {
        console.addStatus("Starting interactive mode...")
        ExecutorService commandExecutor = Executors.newFixedThreadPool(1)
        try {
            interactiveModeLoop(console, commandExecutor)
        } finally {
            commandExecutor.shutdownNow()
        }
    }

    private void interactiveModeLoop(MicronautConsole console, ExecutorService commandExecutor) {
        NonBlockingInputStream nonBlockingInput = (NonBlockingInputStream) console.reader.getInput()
        interactiveModeActive = true
        boolean firstRun = true
        while (keepRunning) {
            try {
                if (firstRun) {
                    console.addStatus("Enter a command name to run. Use TAB for completion:")
                    firstRun = false
                }
                String commandLine = console.showPrompt()
                if (commandLine == null) {
                    // CTRL-D was pressed, exit interactive mode
                    exitInteractiveMode()
                } else if (commandLine.trim()) {
                    if (nonBlockingInput.isNonBlockingEnabled()) {
                        handleCommandWithCancellationSupport(console, commandLine, commandExecutor, nonBlockingInput)
                    } else {
                        handleCommand(cliParser.parseString(commandLine))
                    }
                }
            } catch (UserInterruptException e) {
                exitInteractiveMode()
            } catch (Throwable e) {
                console.error "Caught exception ${e.message}", e
            }
        }
    }

    private Boolean handleCommandWithCancellationSupport(MicronautConsole console, String commandLine, ExecutorService commandExecutor, NonBlockingInputStream nonBlockingInput) {
        ExecutionContext executionContext = createExecutionContext(cliParser.parseString(commandLine))
        Future<?> commandFuture = commandExecutor.submit({ handleCommand(executionContext) } as Callable<Boolean>)
        def terminal = console.reader.terminal
        if (terminal instanceof UnixTerminal) {
            ((UnixTerminal) terminal).disableInterruptCharacter()
        }
        try {
            while (!commandFuture.done) {
                if (nonBlockingInput.nonBlockingEnabled) {
                    int peeked = nonBlockingInput.peek(100L)
                    if (peeked > 0) {
                        // read peeked character from buffer
                        nonBlockingInput.read(1L)
                        if (peeked == KEYPRESS_CTRL_C || peeked == KEYPRESS_ESC) {
                            executionContext.console.log('  ')
                            executionContext.console.updateStatus("Stopping build. Please wait...")
                            executionContext.cancel()
                        }
                    }
                }
            }
        } finally {
            if (terminal instanceof UnixTerminal) {
                ((UnixTerminal) terminal).enableInterruptCharacter()
            }
        }
        if (!commandFuture.isCancelled()) {
            try {
                return commandFuture.get()
            } catch (ExecutionException e) {
                throw e.cause
            }
        } else {
            return false
        }
    }

    private initializeProfile() {
        CliSettings.TARGET_DIR?.mkdirs()

        this.profileRepository = createMavenProfileRepository()

        String profileName = applicationConfig.get(CliSettings.PROFILE) ?: getSetting(CliSettings.PROFILE, String, DEFAULT_PROFILE_NAME)
        this.profile = profileRepository.getProfile(profileName)

        if (profile == null) {
            throw new IllegalStateException("No profile found for name [$profileName].")
        }
    }

    private CodeGenConfig loadApplicationConfig() {
        CodeGenConfig config = new CodeGenConfig()
        File cliYml = new File("micronaut-cli.yml")
        if (cliYml.exists()) {
            config.loadYml(cliYml)
        }
        config
    }

    private boolean handleBuiltInCommands(ExecutionContext context) {
        CommandLine commandLine = context.commandLine
        def commandName = commandLine.commandName

        if (commandName && commandName.size() > 1 && commandName.startsWith('!')) {
            return executeProcess(context, commandLine.rawArguments)
        } else {
            switch (commandName) {
                case '!':
                    return bang(context)
                case 'exit':
                    exitInteractiveMode()
                    return true
                    break
                case 'quit':
                    exitInteractiveMode()
                    return true
                    break
            }
        }

        return false
    }

    protected boolean executeProcess(ExecutionContext context, String[] args) {
        def console = context.console
        try {
            args[0] = args[0].substring(1)
            def process = new ProcessBuilder(args).redirectErrorStream(true).start()
            console.log process.inputStream.getText('UTF-8')
            return true
        } catch (e) {
            console.error "Error occurred executing process: $e.message"
            return false
        }
    }

    /**
     * Removes '\' escape characters from the given string.
     */
    private String unescape(String str) {
        return str.replace('\\', '')
    }

    protected Boolean bang(ExecutionContext context) {
        def console = context.console
        def history = console.reader.history

        //move one step back to !
        history.previous()

        if (!history.previous()) {
            console.error "! not valid. Can not repeat without history"
        }

        //another step to previous command
        String historicalCommand = history.current()
        if (historicalCommand.startsWith("!")) {
            console.error "Can not repeat command: $historicalCommand"
        } else {
            return handleCommand(cliParser.parseString(historicalCommand))
        }
        return false
    }

    private void exitInteractiveMode() {
        keepRunning = false
    }


    @Canonical
    public static class ExecutionContextImpl implements ExecutionContext {
        CommandLine commandLine
        @Delegate(excludes = ['getConsole', 'getBaseDir'])
        ProjectContext projectContext
        MicronautConsole console = MicronautConsole.getInstance()

        ExecutionContextImpl(CodeGenConfig config) {
            this(new DefaultCommandLine(), new ProjectContextImpl(MicronautConsole.instance, new File("."), config))
        }

        ExecutionContextImpl(CommandLine commandLine, ProjectContext projectContext) {
            this.commandLine = commandLine
            this.projectContext = projectContext
            if (projectContext?.console) {
                console = projectContext.console
            }
        }

        private List<CommandCancellationListener> cancelListeners = []

        @Override
        public void addCancelledListener(CommandCancellationListener listener) {
            cancelListeners << listener
        }

        @Override
        public void cancel() {
            for (CommandCancellationListener listener : cancelListeners) {
                try {
                    listener.commandCancelled()
                } catch (Exception e) {
                    console.error("Error notifying listener about cancelling command", e)
                }
            }
        }

        @Override
        File getBaseDir() {
            this.projectContext?.baseDir ?: new File(".")
        }
    }

    @Canonical
    private static class ProjectContextImpl implements ProjectContext {
        MicronautConsole console = MicronautConsole.getInstance()
        File baseDir
        CodeGenConfig cliConfig

        @Override
        public String navigateConfig(String... path) {
            cliConfig.navigate(path)
        }

        @Override
        ConfigMap getConfig() {
            return cliConfig
        }

        @Override
        public <T> T navigateConfigForType(Class<T> requiredType, String... path) {
            cliConfig.navigate(requiredType, path)
        }
    }
}
