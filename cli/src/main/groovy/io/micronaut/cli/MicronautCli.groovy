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
import io.micronaut.cli.profile.ResetableCommand
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
import picocli.CommandLine.UnmatchedArgumentException

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
        "  @|bold create-cli-app|@ @|yellow NAME|@",
        "  @|bold create-federation|@ @|yellow NAME|@ @|yellow --services|@ @|yellow,italic SERVICE_NAME[,SERVICE_NAME]...|@",
        "  @|bold create-function|@ @|yellow NAME|@"],
    synopsisHeading = "@|bold,underline Usage:|@ ",
    optionListHeading = "%n@|bold,underline Options:|@%n",
    commandListHeading = "%n@|bold,underline Commands:|@%n")
class MicronautCli {

    public static final String DEFAULT_PROFILE_NAME = ProfileRepository.DEFAULT_PROFILE_NAME
    private static final int KEYPRESS_CTRL_C = 3
    private static final int KEYPRESS_ESC = 27
    private static final String APP_USAGE_MESSAGE = "create-app [NAME]"
    private static final String CLI_APP_USAGE_MESSAGE = "create-cli-app [NAME]"
    private static final String FEDERATION_USAGE_MESSAGE = "create-federation [NAME] --services SERVICE_NAME[,SERVICE_NAME]..."
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
        if (MavenProfileRepository.DEFAULT_REPO == MavenProfileRepository.SNAPSHOT_REPO) {
            cli.profileRepositories.add(MavenProfileRepository.RELEASE_REPO)
        }

        try {
            exit(cli.execute(args))
        } catch (ParameterException e) {
            MicronautConsole console = MicronautConsole.instance
            console.error("Error occurred running Micronaut CLI: $e.message")
            if (!UnmatchedArgumentException.printSuggestions(e, console.out)) {
                console.append(e.commandLine.getUsageMessage(console.ansiEnabled ? Ansi.ON : Ansi.OFF))
            }
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
        System.out.println "Usage: \n\t $APP_USAGE_MESSAGE \n\t $CLI_APP_USAGE_MESSAGE \n\t $FEDERATION_USAGE_MESSAGE \n\t $FUNCTION_USAGE_MESSAGE  \n\n"
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
        if (!parser) {
            parser = createParser()
            // picocli.AutoComplete.bash('mn', new File('mn_completion'), null, parser) // uncomment to regenerate completion script
        }
        def parseResult = parser.parseArgs(args)
        def console = initConsole(parseResult)

        if (printHelpIfRequested(parseResult)) { exit(0) }

        if (!isRunningInProject()) {
            //Execution path for CLI outside of a project

            if (parseResult.hasSubcommand()) {
                def pr = parseResult
                while (pr.hasSubcommand()) { pr = pr.subcommand() } // most specific subcommand
                Command command = pr.commandSpec().userObject() as Command
                int result =  executeCommand(command, pr) ? 0 : 1
                if (command instanceof ResetableCommand) {
                    command.reset()
                }
                return result
            } else if (parseResult.unmatched()) {
                return getBaseUsage()
            } else {
                integrateGradle = false
                console.reader.addCompleter(new PicocliCompleter(parser.commandSpec))

                profile = [handleCommand: { ExecutionContext context ->
                    def pr = context.parseResult
                    assertNoUnmatchedArguments(pr)
                    while (pr.hasSubcommand()) { pr = pr.subcommand() } // most specific subcommand
                    Command command = pr.commandSpec().userObject() as Command
                    boolean result =  executeCommand(command, pr)
                    if (command instanceof ResetableCommand) {
                        command.reset()
                    }
                    return result
                }] as Profile

                startInteractiveMode(console)
                return 0
            }
        } else {
            //Execution path for CLI within a project
            // we already called initializeApplication() when creating the parser
            if (parseResult.hasSubcommand()) {
                return handleCommand(parseResult) ? 0 : 1
            } else if (parseResult.unmatched()) {
                MicronautConsole.instance.error("The command '${parseResult.unmatched().get(0)}' was not found. Some commands like 'create-app' are only available outside of a project.")
                return 1
            } else {
                handleInteractiveMode()
            }
        }
        return 0
    }

    private static boolean isRunningInProject() {
        new File("micronaut-cli.yml").exists() || new File("profile.yml").exists()
    }

    private CommandLine createParser() {
        CommandLine result = new CommandLine(this)

        // allow unmatched args to support !<cmd> (see #handleBuiltInCommands)
        result.commandSpec.parser().unmatchedArgumentsAllowed(true) // TBD only for top-level (mn) command?

        // register all subcommands up front so we can provide completion for the full command hierarchy

        if (isRunningInProject()) {
            initializeApplication()
            profile.getCommands(projectContext).each { Command cmd ->
                result.addSubcommand(cmd.name, new CommandLine(cmd))
            }
        } else {
            profileRepository = createMavenProfileRepository()

            // force resolution of all profiles
            profileRepository.getAllProfiles()

            CommandRegistry.findCommands(profileRepository).each { Command cmd ->
                if (!result.subcommands.containsKey(cmd.name)) { // picocli won't allow duplicates
                    result.addSubcommand(cmd.name, new CommandLine(cmd))
                }
            }
        }

        result.addSubcommand('!', new CommandLine(new BangCommand(this)))
        result.addSubcommand('exit', new CommandLine(new ExitCommand(this)), 'quit')
        result.setUsageHelpWidth(100) // do this last so it applies to all subcommands
        result
    }

    /**
     * Prints usage or version help to the console if requested and returns {@code true};
     * otherwise returns {@code false}.
     */
    private boolean printHelpIfRequested(ParseResult parseResult) {
        def p = parseResult
        while (p) { //
            if (p.isVersionHelpRequested()) {
                p.commandSpec().version().each { MicronautConsole.instance.addStatus(it) }
                return true
            }
            if (p.isUsageHelpRequested()) {
                def console = MicronautConsole.instance
                Ansi ansi = console.ansiEnabled ? Ansi.ON : Ansi.OFF
                console.append(p.commandSpec().commandLine().getUsageMessage(ansi))
                return true
            }
            p = p.subcommand()
        }
        false
    }

    private MicronautConsole initConsole(ParseResult parseResult) {
        boolean showStack = commonOptions.showStacktrace || parseResult.subcommand()?.matchedOptionValue("stacktrace", false)
        boolean verbose = commonOptions.verbose || parseResult.subcommand()?.matchedOptionValue("verbose", false)
        boolean ansiEnabled = commonOptions.ansiEnabled && !parseResult.subcommand()?.matchedOptionValue("plain-output", false)

        System.setProperty("micronaut.show.stacktrace", "${showStack}")
        System.setProperty("micronaut.verbose",         "${verbose}")
        System.setProperty("micronaut.full.stacktrace", "${verbose}")

        def console = MicronautConsole.getInstance()
        console.verbose = verbose
        console.stacktrace = showStack
        console.ansiEnabled = ansiEnabled

        console
    }

    /**
     * Throws an UnmatchedArgumentException if there are unmatched args
     * EXCEPT when the user requested to repeat a previous command with {@code !<cmd>}.
     * Also, no exception is thrown if a subcommand was recognized:
     * in that case the subcommand should be processed and the unknown option is ignored.
     */
    private void assertNoUnmatchedArguments(ParseResult parseResult) {
        if (!parseResult.hasSubcommand() && parseResult.unmatched() && !parseResult.unmatched()[0].startsWith('!')) {
            throw new CommandLine.UnmatchedArgumentException(parseResult.commandSpec().commandLine(), parseResult.unmatched());
        }
    }

    protected boolean executeCommand(Command cmd, ParseResult parseResult) {
        try {
            return cmd.handle(createExecutionContext(parseResult))
        } catch (MissingMethodException e) {
            return false
        }
    }

    protected void initializeApplication() {
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
                def parseResult = context.getParseResult()

                if (handleBuiltInCommands(context))    { return true }
                if (printHelpIfRequested(parseResult)) { return true }
                assertNoUnmatchedArguments(parseResult)

                if (profile.handleCommand(context)) {
                    if (tiggerAppLoad) {
                        console.updateStatus("Initializing application. Please wait...")
                        try {
                            parser = createParser() // initializes application
                            setupCompleters()
                        } finally {
                            tiggerAppLoad = false
                        }
                    }
                    return true;
                }
                return false
            } catch (ParameterException e) {
                console.error(e.message)
                if (!UnmatchedArgumentException.printSuggestions(e, console.out)) {
                    console.append(e.commandLine.getUsageMessage(console.ansiEnabled ? Ansi.ON : Ansi.OFF))
                }
                return false
            } catch (Throwable e) {
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
        MicronautConsole console = projectContext ? projectContext.console : MicronautConsole.instance

        def consoleReader = console.reader
        consoleReader.setHandleUserInterrupt(true)
        def completers = aggregateCompleter.getCompleters()

        console.resetCompleters()
        // add bang operator completer
        completers.add(new ArgumentCompleter(
            new RegexCompletor("!\\w+"), new EscapingFileNameCompletor())
        )

        completers.add(new PicocliCompleter(parser.commandSpec))

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
                        initConsole(parseResult)

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
                        if (!UnmatchedArgumentException.printSuggestions(invalidInput, console.out)) {
                            Ansi ansi = console.ansiEnabled ? Ansi.ON : Ansi.OFF
                            console.append(invalidInput.commandLine.getUsageMessage(ansi))
                        }
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
            return executeProcess(context, parseResult.unmatched() as String[])
        }
        return false
    }

    protected boolean executeProcess(ExecutionContext context, String[] args) {
        def console = context.console
        try {
            args[0] = args[0].substring(1)  // strip off leading '!'
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
                def parseResult = micronautCli.parser.parseArgs(splitCommandLine(historicalCommand))
                micronautCli.initConsole(parseResult)
                return micronautCli.handleCommand(parseResult)
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
