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
package io.micronaut.cli.console.logging;

import static org.fusesource.jansi.Ansi.ansi;
import static org.fusesource.jansi.Ansi.Color.DEFAULT;
import static org.fusesource.jansi.Ansi.Color.RED;
import static org.fusesource.jansi.Ansi.Color.BLUE;
import static org.fusesource.jansi.Ansi.Erase.FORWARD;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import jline.Terminal;
import jline.TerminalFactory;
import jline.UnixTerminal;
import jline.console.ConsoleReader;
import jline.console.completer.Completer;
import jline.console.history.FileHistory;
import jline.console.history.History;
import jline.internal.Log;
import jline.internal.ShutdownHooks;
import jline.internal.TerminalLineSettings;

import org.apache.tools.ant.BuildException;
import io.micronaut.cli.console.interactive.CandidateListCompletionHandler;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.codehaus.groovy.runtime.typehandling.NumberMath;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;

/**
 * Utility class for delivering console output in a nicely formatted way.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class MicronautConsole implements ConsoleLogger {

    private static MicronautConsole instance;

    public static final String ENABLE_TERMINAL = "grails.console.enable.terminal";
    public static final String ENABLE_INTERACTIVE = "grails.console.enable.interactive";
    public static final String LINE_SEPARATOR = System.getProperty("line.separator");
    public static final String CATEGORY_SEPARATOR = "|";
    public static final String PROMPT = "mn> ";
    public static final String SPACE = " ";
    public static final String ERROR = "Error";
    public static final String WARNING = "Warning";
    public static final String HISTORYFILE = ".mn_history";
    public static final String STACKTRACE_FILTERED_MESSAGE = " (NOTE: Stack trace has been filtered. Use --verbose to see entire trace.)";
    public static final String STACKTRACE_MESSAGE = " (Use --stacktrace to see the full trace)";
    public static final Character SECURE_MASK_CHAR = new Character('*');
    private PrintStream originalSystemOut;
    private PrintStream originalSystemErr;
    private StringBuilder maxIndicatorString;
    private int cursorMove;
    private Thread shutdownHookThread;
    private Character defaultInputMask = null;
    
    /**
     * Whether to enable verbose mode
     */
    private boolean verbose = Boolean.getBoolean("grails.verbose");

    /**
     * Whether to show stack traces
     */
    private boolean stacktrace = Boolean.getBoolean("grails.show.stacktrace");

    private boolean progressIndicatorActive = false;

    /**
     * The progress indicator to use
     */
    String indicator = ".";
    /**
     * The last message that was printed
     */
    String lastMessage = "";

    Ansi lastStatus = null;
    /**
     * The reader to read info from the console
     */
    ConsoleReader reader;

    Terminal terminal;

    PrintStream out;
    PrintStream err;

    History history;

    /**
     * The category of the current output
     */
    @SuppressWarnings("serial")
    Stack<String> category = new Stack<String>() {
        @Override
        public String toString() {
            if (size() == 1) return peek() + CATEGORY_SEPARATOR;
            return DefaultGroovyMethods.join((Iterable)this, CATEGORY_SEPARATOR) + CATEGORY_SEPARATOR;
        }
    };

    /**
     * Whether ANSI should be enabled for output
     */
    private boolean ansiEnabled = true;

    /**
     * Whether user input is currently active
     */
    private boolean userInputActive;

    public void addShutdownHook() {
        shutdownHookThread = new Thread(new Runnable() {
            @Override
            public void run() {
                beforeShutdown();
            }
        });
        Runtime.getRuntime().addShutdownHook(shutdownHookThread);
    }
    
    public void removeShutdownHook() {
        if(shutdownHookThread != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHookThread);
        }
    }
    
    
    protected MicronautConsole() throws IOException {
        cursorMove = 1;

        initialize(System.in, System.out, System.err);

        // bit of a WTF this, but see no other way to allow a customization indicator
        maxIndicatorString = new StringBuilder(indicator).append(indicator).append(indicator).append(indicator).append(indicator);

    }
    
    /**
     * Use in testing when System.out, System.err or System.in change
     * @throws IOException
     */
    public void reinitialize(InputStream systemIn, PrintStream systemOut, PrintStream systemErr) throws IOException {
        if(reader != null) {
            reader.shutdown();
        }
        initialize(systemIn, systemOut, systemErr);
    }

    protected void initialize(InputStream systemIn, PrintStream systemOut, PrintStream systemErr) throws IOException {
        bindSystemOutAndErr(systemOut, systemErr);

        redirectSystemOutAndErr(true);

        System.setProperty(ShutdownHooks.JLINE_SHUTDOWNHOOK, "false");
        
        if (isInteractiveEnabled()) {
            reader = createConsoleReader(systemIn);
            reader.setBellEnabled(false);
            reader.setCompletionHandler(new CandidateListCompletionHandler());
            if (isActivateTerminal()) {
                terminal = createTerminal();
            }

            history = prepareHistory();
            if (history != null) {
                reader.setHistory(history);
            }
        }
        else if (isActivateTerminal()) {
            terminal = createTerminal();
        }
    }

    protected void bindSystemOutAndErr(PrintStream systemOut, PrintStream systemErr) {
        originalSystemOut = unwrapPrintStream(systemOut);
        out = wrapInPrintStream(originalSystemOut);
        originalSystemErr = unwrapPrintStream(systemErr);
        err = wrapInPrintStream(originalSystemErr);
    }
    
    private PrintStream unwrapPrintStream(PrintStream printStream) {
        if(printStream instanceof ConsolePrintStream) {
            return ((ConsolePrintStream)printStream).getTargetOut();
        }
        if(printStream instanceof ConsoleErrorPrintStream) {
            return ((ConsoleErrorPrintStream)printStream).getTargetOut();
        }
        return printStream;
    }

    private PrintStream wrapInPrintStream(PrintStream printStream) {
        OutputStream ansiWrapped = ansiWrap(printStream);
        if(ansiWrapped instanceof PrintStream) {
            return (PrintStream)ansiWrapped;
        } else {
            return new PrintStream(ansiWrapped, true);
        }
    }

    public PrintStream getErr() {
        return err;
    }

    public void setErr(PrintStream err) {
        this.err = err;
    }

    public void setOut(PrintStream out) {
        this.out = out;
    }

    public boolean isInteractiveEnabled() {
        return readPropOrTrue(ENABLE_INTERACTIVE);
    }

    private boolean isActivateTerminal() {
        return readPropOrTrue(ENABLE_TERMINAL);
    }

    private boolean readPropOrTrue(String prop) {
        String property = System.getProperty(prop);
        return property == null ? true : Boolean.valueOf(property);
    }

    protected ConsoleReader createConsoleReader(InputStream systemIn) throws IOException {
        // need to swap out the output to avoid logging during init
        final PrintStream nullOutput = new PrintStream(new ByteArrayOutputStream());
        final PrintStream originalOut = Log.getOutput();
        try {
            Log.setOutput(nullOutput);
            ConsoleReader consoleReader = new ConsoleReader(systemIn, out);
            consoleReader.setExpandEvents(false);
            return consoleReader;
        } finally {
            Log.setOutput(originalOut);
        }
    }

    /**
     * Creates the instance of Terminal used directly in GrailsConsole. Note that there is also
     * another terminal instance created implicitly inside of ConsoleReader. That instance
     * is controlled by the jline.terminal system property.
     */
    protected Terminal createTerminal() {
        terminal = TerminalFactory.create();
        if(isWindows()) {
            terminal.setEchoEnabled(true);
        }
        return terminal;
    }

    public void resetCompleters() {
        final ConsoleReader reader = getReader();
        if(reader != null) {
            Collection<Completer> completers = reader.getCompleters();
            for (Completer completer : completers) {
                reader.removeCompleter(completer);
            }

            // for some unknown reason / bug in JLine you have to iterate over twice to clear the completers (WTF)
            completers = reader.getCompleters();
            for (Completer completer : completers) {
                reader.removeCompleter(completer);
            }
        }
    }
    /**
     * Prepares a history file to be used by the ConsoleReader. This file
     * will live in the home directory of the user.
     */
    protected History prepareHistory() throws IOException {
        File file = new File(System.getProperty("user.home"), HISTORYFILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
            }
            catch (IOException ignored) {
                // can't create the file, so no history for you
            }
        }
        return file.canWrite() ? new FileHistory(file) : null;
    }

    /**
     * Hook method that allows controlling whether or not output streams should be wrapped by
     * AnsiConsole.wrapOutputStream. Unfortunately, Eclipse consoles will look to the AnsiWrap
     * like they do not understand ansi, even if we were to implement support in Eclipse to'
     * handle it and the wrapped stream will not pass the ansi chars on to Eclipse).
     */
    protected OutputStream ansiWrap(OutputStream out) {
        return AnsiConsole.wrapOutputStream(out);
    }

    public boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().indexOf("windows") != -1;
    }

    public static synchronized MicronautConsole getInstance() {
        if (instance == null) {
            try {
                final MicronautConsole console = createInstance();
                console.addShutdownHook();
                setInstance(console);
            } catch (IOException e) {
                throw new RuntimeException("Cannot create Micronaut console: " + e.getMessage(), e);
            }
        }
        return instance;
    }
    
    public static synchronized void removeInstance() {
        if (instance != null) {
            instance.removeShutdownHook();
            instance.restoreOriginalSystemOutAndErr();
            if(instance.getReader() != null) {
                instance.getReader().shutdown();
            }
            instance = null;
        }
    }

    public void beforeShutdown() {
        persistHistory();
        restoreTerminal();
    }

    protected void restoreTerminal() {
        try {
            terminal.restore();
        } catch (Exception e) {
            // ignore
        }
        if(terminal instanceof UnixTerminal) {
            // workaround for GRAILS-11494
            try {
                new TerminalLineSettings().set("sane");
            } catch (Exception e) {
                // ignore
            }
        }
    }

    protected void persistHistory() {
        if(history instanceof Flushable) {
            try {
                ((Flushable)history).flush();
            }
            catch (IOException e) {
                // ignore exception
            }
        }
    }

    public static void setInstance(MicronautConsole newConsole) {
        instance = newConsole;
        instance.redirectSystemOutAndErr(false);
    }

    protected void redirectSystemOutAndErr(boolean force) {
        if (force || !(System.out instanceof ConsolePrintStream)) {
            System.setOut(new ConsolePrintStream(out));
        }
        if (force || !(System.err instanceof ConsoleErrorPrintStream)) {
            System.setErr(new ConsoleErrorPrintStream(err));
        }
    }

    public static MicronautConsole createInstance() throws IOException {
        String className = System.getProperty("grails.console.class");
        if (className != null) {
            try {
                @SuppressWarnings("unchecked")
                Class<? extends MicronautConsole> klass = (Class<? extends MicronautConsole>) Class.forName(className);
                return klass.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return new MicronautConsole();
    }

    public void setAnsiEnabled(boolean ansiEnabled) {
        this.ansiEnabled = ansiEnabled;
    }

    /**
     * @param verbose Sets whether verbose output should be used
     */
    public void setVerbose(boolean verbose) {
        if (verbose) {
            // enable big traces in verbose mode
            // note - can't use StackTraceFilterer#SYS_PROP_DISPLAY_FULL_STACKTRACE as it is in grails-core
            System.setProperty("grails.full.stacktrace", "true");
        }
        this.verbose = verbose;
    }

    /**
     * @param stacktrace Sets whether to show stack traces on errors
     */
    public void setStacktrace(boolean stacktrace) {
        this.stacktrace = stacktrace;
    }

    /**
     * @return Whether verbose output is being used
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     *
     * @return Whether to show stack traces
     */
    public boolean isStacktrace() {
        return stacktrace;
    }

    /**
     * @return The input stream being read from
     */
    public InputStream getInput() {
        assertAllowInput();
        return reader.getInput();
    }

    private void assertAllowInput() {
        assertAllowInput(null);
    }

    private void assertAllowInput(String prompt) {
        if (reader == null) {
            String msg = "User input is not enabled, cannot obtain input stream";
            if (prompt != null) {
                msg = msg + " - while trying: " + prompt;
            }

            throw new IllegalStateException(msg);
        }
    }

    /**
     * @return The last message logged
     */
    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public ConsoleReader getReader() {
        return reader;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public PrintStream getOut() {
        return out;
    }

    public Stack<String> getCategory() {
        return category;
    }

    /**
     * Indicates progress with the default progress indicator
     */
    @Override
    public void indicateProgress() {
        verifySystemOut();
        progressIndicatorActive = true;
        if (isAnsiEnabled()) {
            if (lastMessage != null && lastMessage.length() > 0) {
                if (!lastMessage.contains(maxIndicatorString)) {
                    updateStatus(lastMessage + indicator);
                }
            }
        }
        else {
            out.print(indicator);
        }
    }

    /**
     * Indicate progress for a number and total
     *
     * @param number The current number
     * @param total  The total number
     */
    @Override
    public void indicateProgress(int number, int total) {
        progressIndicatorActive = true;
        String currMsg = lastMessage;
        try {
            updateStatus(currMsg + ' '+ number + " of " + total);
        } finally {
            lastMessage = currMsg;
        }
    }

    /**
     * Indicates progress as a percentage for the given number and total
     *
     * @param number The number
     * @param total  The total
     */
    @Override
    public void indicateProgressPercentage(long number, long total) {
        verifySystemOut();
        progressIndicatorActive = true;
        String currMsg = lastMessage;
        try {
            int percentage = Math.round(NumberMath.multiply(NumberMath.divide(number, total), 100).floatValue());

            if (!isAnsiEnabled()) {
                out.print("..");
                out.print(percentage + '%');
            }
            else {
                updateStatus(currMsg + ' ' + percentage + '%');
            }
        } finally {
            lastMessage = currMsg;
        }
    }

    /**
     * Indicates progress by number
     *
     * @param number The number
     */
    @Override
    public void indicateProgress(int number) {
        verifySystemOut();
        progressIndicatorActive = true;
        String currMsg = lastMessage;
        try {
            if (isAnsiEnabled()) {
                updateStatus(currMsg + ' ' + number);
            }
            else {
                out.print("..");
                out.print(number);
            }
        } finally {
            lastMessage = currMsg;
        }
    }

    /**
     * Updates the current state message
     *
     * @param msg The message
     */
    @Override
    public void updateStatus(String msg) {
        outputMessage(msg, 1);
    }

    private void outputMessage(String msg, int replaceCount) {
        verifySystemOut();
        if (msg == null || msg.trim().length() == 0) return;
        try {
            if (isAnsiEnabled()) {
                if (replaceCount > 0) {
                    out.print(erasePreviousLine(CATEGORY_SEPARATOR));
                }
                lastStatus = outputCategory(ansi(), CATEGORY_SEPARATOR)
                        .fg(Color.DEFAULT).a(msg).reset();
                out.println(lastStatus);
                if (!userInputActive) {
                    cursorMove = replaceCount;
                }
            } else {
                if (lastMessage != null && lastMessage.equals(msg)) return;

                if (progressIndicatorActive) {
                    out.println();
                }

                out.print(CATEGORY_SEPARATOR);
                out.println(msg);
            }
            lastMessage = msg;
        } finally {
            postPrintMessage();
        }
    }

    private Ansi moveDownToSkipPrompt() {
           return ansi()
                   .cursorDown(1)
                   .cursorLeft(PROMPT.length());
    }

    private void postPrintMessage() {
        progressIndicatorActive = false;
        appendCalled = false;
        if (userInputActive) {
            showPrompt();
        }
    }

    /**
     * Keeps doesn't replace the status message
     *
     * @param msg The message
     */
    @Override
    public void addStatus(String msg) {
        outputMessage(msg, 0);
        lastMessage = "";
    }

    /**
     * Prints an error message
     *
     * @param msg The error message
     */
    @Override
    public void error(String msg) {
        error(ERROR, msg);
    }

    /**
     * Prints an error message
     *
     * @param msg The error message
     */
    @Override
    public void warning(String msg) {
        error(WARNING, msg);
    }

    /**
     * Prints a warn message
     *
     * @param msg The message
     */
    @Override
    public void warn(String msg) {
        warning(msg);
    }

    private void logSimpleError(String msg) {
        verifySystemOut();
        if (progressIndicatorActive) {
            out.println();
        }
        out.println(CATEGORY_SEPARATOR);
        out.println(msg);
    }

    public boolean isAnsiEnabled() {
        return Ansi.isEnabled() && (terminal != null && terminal.isAnsiSupported()) && ansiEnabled;
    }

    /**
     * Use to log an error
     *
     * @param msg The message
     * @param error The error
     */
    @Override
    public void error(String msg, Throwable error) {
        try {
            if ((verbose||stacktrace) && error != null) {
                printStackTrace(msg, error);
                error(ERROR, msg);
            }
            else {
                error(ERROR, msg + STACKTRACE_MESSAGE);
            }
        } finally {
            postPrintMessage();
        }
    }

    /**
     * Use to log an error
     *
     * @param error The error
     */
    @Override
    public void error(Throwable error) {
        printStackTrace(null, error);
    }

    private void printStackTrace(String message, Throwable error) {
        if ((error instanceof BuildException) && error.getCause() != null) {
            error = error.getCause();
        }
        if (!isVerbose() && !Boolean.getBoolean("grails.full.stacktrace")) {
            StackTraceUtils.deepSanitize(error);
        }
        StringWriter sw = new StringWriter();
        PrintWriter ps = new PrintWriter(sw);
        message = message == null ? error.getMessage() : message;
        if (!isVerbose()) {
            message = message + STACKTRACE_FILTERED_MESSAGE;
        }
        ps.println(message);
        error.printStackTrace(ps);
        error(sw.toString());
    }

    /**
     * Logs a message below the current status message
     *
     * @param msg The message to log
     */
    @Override
    public void log(String msg) {
        verifySystemOut();
        PrintStream printStream = out;
        try {
            if (userInputActive) {
                erasePrompt(printStream);
            }
            if (msg.endsWith(LINE_SEPARATOR)) {
                printStream.print(msg);
            }
            else {
                printStream.println(msg);
            }
            cursorMove = 0;
        } finally {
            printStream.flush();
            postPrintMessage();
        }
    }

    private void erasePrompt(PrintStream printStream) {
        printStream.print(ansi()
                .eraseLine(Ansi.Erase.BACKWARD).cursorLeft(PROMPT.length()));
    }

    /**
     * Logs a message below the current status message
     *
     * @param msg The message to log
     */
    private boolean appendCalled = false;

    public void append(String msg) {
        verifySystemOut();
        PrintStream printStream = out;
        try {
            if (userInputActive && !appendCalled) {
                printStream.print(moveDownToSkipPrompt());
                appendCalled = true;
            }
            if (msg.endsWith(LINE_SEPARATOR)) {
                printStream.print(msg);
            }
            else {
                printStream.println(msg);
            }
            cursorMove = 0;
        } finally {
            progressIndicatorActive = false;
        }
    }

    /**
     * Synonym for #log
     *
     * @param msg The message to log
     */
    @Override
    public void info(String msg) {
        log(msg);
    }

    @Override
    public void verbose(String msg) {
        verifySystemOut();
        try {
            if (verbose) {
                out.println(msg);
                cursorMove = 0;
            }
        } finally {
            postPrintMessage();
        }
    }

    /**
     * Replays the last status message
     */
    public void echoStatus() {
        if (lastStatus != null) {
            updateStatus(lastStatus.toString());
        }
    }

    /**
     * Replacement for AntBuilder.input() to eliminate dependency of
     * GrailsScriptRunner on the Ant libraries. Prints a message and
     * returns whatever the user enters (once they press &lt;return&gt;).
     * @param msg The message/question to display.
     * @return The line of text entered by the user. May be a blank
     * string.
     */
    public String userInput(String msg) {
        return doUserInput(msg, false);
    }

    /**
     * Like {@link #userInput(String)} except that the user's entered characters will be replaced with '*' on the CLI,
     * masking the input (i.e. suitable for capturing passwords etc.).
     *
     * @param msg The message/question to display.
     * @return The line of text entered by the user. May be a blank
     * string.
     */
    public String secureUserInput(String msg) {
        return doUserInput(msg, true);
    }

    private String doUserInput(String msg, boolean secure) {
        // Add a space to the end of the message if there isn't one already.
        if (!msg.endsWith(" ") && !msg.endsWith("\t")) {
            msg += ' ';
        }

        lastMessage = "";
        msg = isAnsiEnabled() ? outputCategory(ansi(), ">").fg(DEFAULT).a(msg).reset().toString() : msg;
        try {
            return readLine(msg, secure);
        } finally {
            cursorMove = 0;
        }
    }

    /**
     * Shows the prompt to request user input
     * @param prompt The prompt to use
     * @return The user input prompt
     */
    private String showPrompt(String prompt) {
        verifySystemOut();
        cursorMove = 0;
        if (!userInputActive) {
            return readLine(prompt, false);
        }

        out.print(prompt);
        out.flush();
        return null;
    }

    private String readLine(String prompt, boolean secure) {
        assertAllowInput(prompt);
        userInputActive = true;
        try {
            Character inputMask = secure ? SECURE_MASK_CHAR : defaultInputMask;
            return reader.readLine(prompt, inputMask);
        } catch (IOException e) {
            throw new RuntimeException("Error reading input: " + e.getMessage());
        } finally {
            userInputActive = false;
        }
    }

    /**
     * Shows the prompt to request user input
     * @return The user input prompt
     */
    public String showPrompt() {
        String prompt = isAnsiEnabled() ? ansiPrompt(PROMPT).toString() : PROMPT;
        return showPrompt(prompt);
    }

    private Ansi ansiPrompt(String prompt) {
        return ansi()
                .a(Ansi.Attribute.INTENSITY_BOLD)
                .fg(BLUE)
                .a(prompt)
                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                .fg(DEFAULT);
    }

    public String userInput(String message, List<String> validResponses) {
        return userInput(message, validResponses.toArray(new String[validResponses.size()]));
    }

    /**
     * Replacement for AntBuilder.input() to eliminate dependency of
     * GrailsScriptRunner on the Ant libraries. Prints a message and
     * list of valid responses, then returns whatever the user enters
     * (once they press &lt;return&gt;). If the user enters something
     * that is not in the array of valid responses, the message is
     * displayed again and the method waits for more input. It will
     * display the message a maximum of three times before it gives up
     * and returns <code>null</code>.
     * @param message The message/question to display.
     * @param validResponses An array of responses that the user is
     * allowed to enter. Displayed after the message.
     * @return The line of text entered by the user, or <code>null</code>
     * if the user never entered a valid string.
     */
    public String userInput(String message, String[] validResponses) {
        if (validResponses == null) {
            return userInput(message);
        }

        String question = createQuestion(message, validResponses);
        String response = userInput(question);
        for (String validResponse : validResponses) {
            if (validResponse.equalsIgnoreCase(response)) {
                return response;
            }
        }
        cursorMove = 0;
        return userInput("Invalid input. Must be one of ", validResponses);
    }

    private String createQuestion(String message, String[] validResponses) {
        return message + "[" + DefaultGroovyMethods.join(validResponses, ",") + "] ";
    }

    private Ansi outputCategory(Ansi ansi, String categoryName) {
        return ansi
                .a(Ansi.Attribute.INTENSITY_BOLD)
                .fg(BLUE)
                .a(categoryName)
                .a(SPACE)
                .a(Ansi.Attribute.INTENSITY_BOLD_OFF);
    }

    private Ansi outputErrorLabel(Ansi ansi, String label) {
        return ansi
                .a(Ansi.Attribute.INTENSITY_BOLD)
                .fg(RED)
                .a(CATEGORY_SEPARATOR)
                .a(SPACE)
                .a(label)
                .a(" ")
                .a(Ansi.Attribute.INTENSITY_BOLD_OFF)
                .fg(Color.DEFAULT);
    }

    private Ansi erasePreviousLine(String categoryName) {
        int cursorMove = this.cursorMove;
        if (userInputActive) cursorMove++;
        if (cursorMove > 0) {
            int moveLeftLength = categoryName.length() + lastMessage.length();
            if (userInputActive) {
                moveLeftLength += PROMPT.length();
            }
            return ansi()
                    .cursorUp(cursorMove)
                    .cursorLeft(moveLeftLength)
                    .eraseLine(FORWARD);

        }
        return ansi();
    }

    @Override
    public void error(String label, String message) {
        verifySystemOut();
        if (message == null) {
            return;
        }

        cursorMove = 0;
        try {
            if (isAnsiEnabled()) {
                Ansi ansi = outputErrorLabel(userInputActive ? moveDownToSkipPrompt()  : ansi(), label).a(message).reset();

                if (message.endsWith(LINE_SEPARATOR)) {
                    out.print(ansi);
                }
                else {
                    out.println(ansi);
                }
            }
            else {
                out.print(label);
                out.print(" ");
                logSimpleError(message);
            }
        } finally {
            postPrintMessage();
        }
    }

    private void verifySystemOut() {
        // something bad may have overridden the system out
        redirectSystemOutAndErr(false);
    }
    
    public void restoreOriginalSystemOutAndErr() {
        System.setOut(originalSystemOut);
        System.setErr(originalSystemErr);
    }

    public void cleanlyExit(int status) {
        flush();
        System.exit(status);
    }

    /**
     * Makes sure that the console has been reset to the default state and that
     * the out stream has been flushed.
     */
    public void flush() {
        if (isAnsiEnabled()) {
            out.print(ansi().reset().toString());
        }
        out.flush();
    }

    public Character getDefaultInputMask() {
        return defaultInputMask;
    }

    public void setDefaultInputMask(Character defaultInputMask) {
        this.defaultInputMask = defaultInputMask;
    }
}
