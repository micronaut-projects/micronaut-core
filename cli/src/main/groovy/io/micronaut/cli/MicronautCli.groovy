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
import io.micronaut.cli.console.proxy.SystemPropertiesAuthenticator
import io.micronaut.cli.interactive.completers.EscapingFileNameCompletor
import io.micronaut.cli.interactive.completers.RegexCompletor
import io.micronaut.cli.interactive.completers.SortedAggregateCompleter
import io.micronaut.cli.io.support.SystemStreamsRedirector
import io.micronaut.cli.profile.Command
import io.micronaut.cli.profile.CommandCancellationListener
import io.micronaut.cli.profile.ExecutionContext
import io.micronaut.cli.profile.Profile
import io.micronaut.cli.profile.ProfileRepository
import io.micronaut.cli.profile.ProjectContext
import io.micronaut.cli.profile.commands.ArgumentCompletingCommand
import io.micronaut.cli.profile.commands.CommandRegistry
import io.micronaut.cli.profile.commands.CommonOptionsMixin
import io.micronaut.cli.profile.commands.PicocliCompleter
import io.micronaut.cli.profile.repository.MavenProfileRepository
import io.micronaut.cli.profile.repository.RepositoryConfiguration
import io.micronaut.cli.util.CliSettings
import jline.UnixTerminal
import jline.console.UserInterruptException
import jline.console.completer.ArgumentCompleter
import jline.internal.NonBlockingInputStream
import picocli.CommandLine
import picocli.CommandLine.Help.Ansi
import picocli.CommandLine.ParameterException
import picocli.CommandLine.ParseResult

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

import static picocli.CommandLine.Model.CommandSpec.create

/**
 * Main class for the Micronaut command line. Handles interactive mode and running Micronaut commands within the context of a profile
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@CompileStatic
@CommandLine.Command(name = "mn", description = [
        "Micronaut CLI command line interface for generating projects and services.",
        "Commonly used commands are:",
        "  @|bold create-app|@ @|yellow NAME|@",
        "  @|bold create-federation|@ @|yellow NAME|@ @|yellow --services|@ @|yellow,italic SERVICE_NAME[,SERVICE_NAME]...|@",
        "  @|bold create-function|@ @|yellow NAME|@"],
    synopsisHeading = "@|bold,underline Usage:|@ ",
    optionListHeading = "%n@|bold,underline Options:|@%n",
    commandListHeading = "%n@|bold,underline Commands:|@%n")
class MicronautCli {

    public static final String DEFAULT_PROFILE_NAME = ProfileRepository.DEFAULT_PROFILE_NAME
    private static final int KEYPRESS_CTRL_C = 3
    private static final int KEYPRESS_ESC = 27
    private static final String USAGE_MESSAGE = "create-app [NAME]"
    private static final String FEDERATION_USAGE_MESSAGE = "create-federation [NAME] --services [SERVICE_NAME],[SERVICE_NAME],..."
    private static final String FUNCTION_USAGE_MESSAGE = "create-function [NAME]"

    // store original System.in, System.out and System.err
    private final SystemStreamsRedirector originalStreams = SystemStreamsRedirector.original()
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
    boolean keepRunning = true
    boolean integrateGradle = true
    Character defaultInputMask = null
    ProfileRepository profileRepository
    CodeGenConfig applicationConfig
    ProjectContext projectContext
    Profile profile = null
    List<RepositoryConfiguration> profileRepositories = [MavenProfileRepository.DEFAULT_REPO]

    CommandLine parser

    @CommandLine.Mixin
    CommonOptionsMixin commonOptions = new CommonOptionsMixin()

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
        } catch (ParameterException e) {
            MicronautConsole console = MicronautConsole.instance
            console.error("Error occurred running Micronaut CLI: $e.message")
            console.append(e.commandLine.getUsageMessage(console.ansiEnabled ? Ansi.ON : Ansi.OFF))
            exit(1)
        } catch (Throwable e) {
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
        long start = 0
        if (!parser) {
            start = System.nanoTime()
            parser = createParser()
        }
        def parseResult = parser.parseArgs(args)
        MicronautConsole.instance.ansiEnabled = commonOptions.ansiEnabled

        if (start && commonOptions.verbose) {
            println("Loaded commands in " + java.time.Duration.ofNanos(System.nanoTime() - start))
        }

        def p = parseResult
        while (p) { // check if this command or any subcommand requested help or version info
            if (p.isVersionHelpRequested()) {
                def console = MicronautConsole.instance
                p.commandSpec().version().each { console.addStatus(it) }
                exit(0)
            }
            if (p.isUsageHelpRequested()) {
                def console = MicronautConsole.instance
                Ansi ansi = console.ansiEnabled ? Ansi.ON : Ansi.OFF
                console.append(p.commandSpec().commandLine().getUsageMessage(ansi))
                exit(0)
            }
            p = p.subcommand()
        }


        File micronautCli = new File("micronaut-cli.yml")
        File profileYml = new File("profile.yml")
        if (!micronautCli.exists() && !profileYml.exists()) {
            //Execution path for CLI outside of a project
            if (!parseResult.hasSubcommand()) {
                integrateGradle = false
                def console = MicronautConsole.getInstance()
                // force resolve of all profiles
                profileRepository.getAllProfiles()
                console.reader.addCompleter(new PicocliCompleter(parser.commandSpec))
                profile = [handleCommand: { ExecutionContext context ->

                    def cl = context.parseResult
                    while (cl.hasSubcommand()) { cl = cl.subcommand() } // most specific subcommand
                    def name = cl.commandSpec().name()
                    def cmd = CommandRegistry.getCommand(name, profileRepository)
                    if (cmd != null) {
                        return executeCommandWithArgumentValidation(cmd, cl)
                    } else if (cl != context.parseResult) {
                        return executeCommandWithArgumentValidation(cl.commandSpec().userObject() as Command, cl)
                    }
                }] as Profile

                startInteractiveMode(console)
                return 0
            }
            if (parseResult.hasSubcommand()) {
                def cmd = CommandRegistry.getCommand(parseResult.subcommand().commandSpec().name(), profileRepository)
                return executeCommandWithArgumentValidation(cmd, parseResult.subcommand()) ? 0 : 1
            } else {
                return getBaseUsage()
            }

        } else {
            //Execution path for CLI within a project
            initializeApplication(parseResult)
            if (parseResult.hasSubcommand()) {
                return handleCommand(parseResult) ? 0 : 1
            } else {
                handleInteractiveMode()
            }
        }
        return 0
    }

    private CommandLine createParser() {
        CommandLine result = new CommandLine(this)

        // allow unmatched args to support !<cmd> (see #handleBuiltInCommands)
        result.commandSpec.parser().unmatchedArgumentsAllowed(true) // TBD only for top-level (mn) command?

        // Temporarily switch on verbosity in case there's a problem loading commands.
        // Values will be reset when parsing new input.
        System.setProperty("micronaut.verbose", "true")
        System.setProperty("micronaut.full.stacktrace", "true")
        System.setProperty("micronaut.show.stacktrace", "true")

        // register all subcommands up front so we can provide completion for the full command hierarchy
        MicronautConsole.instance.log("Loading commands...")
        profileRepository = createMavenProfileRepository()
        CommandRegistry.findCommands(profileRepository).each { Command cmd ->
            result.addSubcommand(cmd.name, new CommandLine(cmd))
        }
        result.addSubcommand('!', new CommandLine(new BangCommand(this)))
        result.addSubcommand('exit', new CommandLine(new ExitCommand(this)), 'quit')
        result.setUsageHelpWidth(100) // do this last so it applies to all subcommands
        result
    }

    protected boolean executeCommandWithArgumentValidation(Command cmd, ParseResult parseResult) {
        return cmd.handle(createExecutionContext(parseResult))
    }

    protected void initializeApplication(ParseResult parseResult) {
        applicationConfig = loadApplicationConfig()
        File profileYml = new File("profile.yml")
        if (profileYml.exists()) {
            // use the profile for profiles
            applicationConfig.put(CliSettings.PROFILE, "profile")
        }

        final MicronautConsole console = MicronautConsole.instance
        console.ansiEnabled = commonOptions.ansiEnabled
        console.defaultInputMask = defaultInputMask
        final File baseDir = new File(".").canonicalFile
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

    ExecutionContext createExecutionContext(ParseResult parseResult) {
        new ExecutionContextImpl(parseResult, projectContext)
    }

    Boolean handleCommand(ParseResult parseResult) {
        handleCommand(createExecutionContext(parseResult))
    }

    Boolean handleCommand(ExecutionContext context) {
        def console = MicronautConsole.getInstance()
        synchronized (MicronautCli) {
            try {
                currentExecutionContext = context
                if (handleBuiltInCommands(context)) {
                    return true
                }

                def mainCommandLine = context.getParseResult()
                while (mainCommandLine.hasSubcommand()) { mainCommandLine = mainCommandLine.subcommand() }

                console.setStacktrace(commonOptions.showStacktrace)

                if (profile.handleCommand(context)) {
                    if (tiggerAppLoad) {
                        console.updateStatus("Initializing application. Please wait...")
                        try {
                            initializeApplication(mainCommandLine)
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
                console.error("Command [${context.parseResult.commandSpec().name()}] error: ${e.message}", e)
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
                    try {
                        ParseResult parseResult = parser.parseArgs(splitCommandLine(commandLine))
                        if (!CommandLine.printHelpIfRequested(parseResult.asCommandLineList(), System.out, System.err, console.ansiEnabled ? Ansi.ON : Ansi.OFF)) {
                            if (nonBlockingInput.isNonBlockingEnabled()) {
                                handleCommandWithCancellationSupport(console, parseResult, commandExecutor, nonBlockingInput)
                            } else {
                                handleCommand(parseResult)
                            }
                        }
                    } catch (ParameterException invalidInput) {
                        // if user input was invalid, print the error message and the help message for the relevant subcommand
                        console.error(invalidInput.getMessage())
                        Ansi ansi = console.ansiEnabled ? Ansi.ON : Ansi.OFF
                        console.append(invalidInput.commandLine.getUsageMessage(ansi))
                    }
                }
            } catch (UserInterruptException e) {
                exitInteractiveMode()
            } catch (Throwable e) {
                console.error "Caught exception ${e.message}", e
            }
        }
    }

    private Boolean handleCommandWithCancellationSupport(MicronautConsole console, ParseResult parseResult, ExecutorService commandExecutor, NonBlockingInputStream nonBlockingInput) {
        ExecutionContext executionContext = createExecutionContext(parseResult)
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
        def parseResult = context.parseResult
        if (!parseResult.unmatched().empty && parseResult.unmatched()[0].startsWith('!')) {
            def args = [ 'mn' ]
            args.addAll(parseResult.unmatched())
            args[1] = args[1].substring(1) // strip off leading '!'
            return executeProcess(context, args as String[])
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

    @Canonical
    @CommandLine.Command(name = '!', hidden = true, description = 'Rerun a previously executed command')
    static class BangCommand extends ArgumentCompletingCommand {
        MicronautCli micronautCli

        String getName() { '!' }

        boolean handle(ExecutionContext context) {
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
                return micronautCli.handleCommand(micronautCli.parser.parseArgs(splitCommandLine(historicalCommand)))
            }
            return false
        }
    }

    @Canonical
    @CommandLine.Command(name = 'exit', hidden = true, aliases = 'quit', description = 'Exit interactive mode')
    static class ExitCommand extends ArgumentCompletingCommand {
        MicronautCli micronautCli

        String getName() { 'exit' }

        boolean handle(ExecutionContext context) {
            micronautCli.keepRunning = false
            return true
        }
    }

    private static String[] splitCommandLine(String commandLine) {
        new ArgumentCompleter.WhitespaceArgumentDelimiter().delimit(commandLine, commandLine.length()).arguments
    }

    private void exitInteractiveMode() {
        keepRunning = false
    }


    @Canonical
    public static class ExecutionContextImpl implements ExecutionContext {
        ParseResult parseResult
        @Delegate(excludes = ['getConsole', 'getBaseDir'])
        ProjectContext projectContext
        MicronautConsole console = MicronautConsole.getInstance()

        ExecutionContextImpl(CodeGenConfig config) {
            this(ParseResult.builder(create()).build(), new ProjectContextImpl(MicronautConsole.instance, new File("."), config))
        }

        ExecutionContextImpl(ParseResult parseResult, ProjectContext projectContext) {
            this.parseResult = parseResult
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
