package io.micronaut.annotation.processing.visitor.log;

import java.io.PrintWriter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaFileObject;

/**
 * A class for error logs. Reports errors and warnings, and
 * keeps track of error numbers and positions.
 */
public class Log {

    public static final int NOPOS = -1;

    private static Log instance;
    private static PrintWriter out;
    private static PrintWriter err;

    /**
     * Factory for diagnostics
     */
    protected JcDiagnostic.Factory diags;
    /**
     * The file that's currently being translated.
     */
    protected DiagnosticSource source;
    /**
     * A cache of lightweight DiagnosticSource objects.
     */
    protected Map<JavaFileObject, DiagnosticSource> sourceMap = new HashMap<>();

    private final Map<Log.WriterKind, PrintWriter> writers;

    /**
     * The maximum number of errors/warnings that are reported.
     */
    protected int maxErrors;
    protected int maxWarnings;
    /**
     * Switch: emit warning messages.
     */
    public boolean emitWarnings;
    /**
     * Switch: suppress note messages.
     */
    public boolean suppressNotes;
    /**
     * Print stack trace on errors?
     */
    public boolean dumpOnError = true;
    /**
     * Keys for expected diagnostics.
     */
    public Set<String> expectDiagKeys;
    /**
     * Set to true if a compressed diagnostic is reported
     */
    public boolean compressedOutput;
    /**
     * The number of errors encountered so far.
     */
    public int nerrors;
    /**
     * The number of warnings encountered so far.
     */
    public int nwarnings;
    /**
     * The number of errors encountered after MaxErrors was reached.
     */
    public int nsuppressederrors;
    /**
     * The number of warnings encountered after MaxWarnings was reached.
     */
    public int nsuppressedwarns;
    /**
     * A set of all errors generated so far. This is used to avoid printing an
     * error message more than once. For each error, a pair consisting of the
     * source file name and source code position of the error is added to the set.
     */
    protected Set<Pair<JavaFileObject, Integer>> recorded = new HashSet<>();
    /**
     * A set of "not-supported-in-source-X" errors produced so far. This is used to only generate
     * one such error per file.
     */
    protected Set<Pair<JavaFileObject, AnnList<String>>> recordedSourceLevelErrors = new HashSet<>();
    /**
     * Formatter for diagnostics.
     */
    private DiagnosticFormatter<JcDiagnostic> diagFormatter;

    /**
     * Get the Log instance for this context.
     */
    public static Log instance() {
        if (instance == null) {
            instance = new Log();
        }
        return instance;
    }

    /**
     * Initialize a map of writers based on values found in the context
     *
     * @return a map of writers
     */
    private static Map<Log.WriterKind, PrintWriter> initWriters() {
        if (out == null && err == null) {
            out = new PrintWriter(System.out, true);
            err = new PrintWriter(System.err, true);
            return initWriters(out, err);
        } else if (out == null || err == null) {
            PrintWriter pw = (out != null) ? out : err;
            return initWriters(pw, pw);
        } else {
            return initWriters(out, err);
        }
    }

    /**
     * Initialize a writer map for a stream for normal output, and a stream for diagnostics.
     *
     * @param out a stream to be used for normal output
     * @param err a stream to be used for diagnostic messages, such as errors, warnings, etc
     *
     * @return a map of writers
     */
    private static Map<Log.WriterKind, PrintWriter> initWriters(PrintWriter out, PrintWriter err) {
        Map<Log.WriterKind, PrintWriter> writers = new EnumMap<>(Log.WriterKind.class);
        writers.put(Log.WriterKind.ERROR, err);
        writers.put(Log.WriterKind.WARNING, err);
        writers.put(Log.WriterKind.NOTICE, out);

        return writers;
    }

    /**
     * Construct a log with default settings.
     * If no streams are set in the context, the log will be initialized to use
     * System.out for normal output, and System.err for all diagnostic output.
     * If one stream is set in the context, with either Log.outKey or Log.errKey,
     * it will be used for all output.
     * Otherwise, the log will be initialized to use both streams found in the context.
     */
    protected Log() {
        this(initWriters());
    }

    /**
     * Construct a log.
     * The log will be initialized to use stdOut for normal output, and stdErr
     * for all diagnostic output.
     */
    protected Log(PrintWriter out, PrintWriter err) {
        this(initWriters(out, err));
    }

    /**
     * Creates a log.
     *
     * @param writers a map of writers that can be accessed by the kind of writer required
     */
    private Log(Map<Log.WriterKind, PrintWriter> writers) {
        diags = JcDiagnostic.Factory.instance();
        diagFormatter = new BasicDiagnosticFormatter();
        this.writers = writers;
    }

    /**
     * Default value for -Xmaxerrs.
     */
    protected int getDefaultMaxErrors() {
        return 100;
    }

    /**
     * Default value for -Xmaxwarns.
     */
    protected int getDefaultMaxWarnings() {
        return 100;
    }

    /**
     * Returns true if an error needs to be reported for a given
     * source name and pos.
     */
    protected boolean shouldReport(JavaFileObject file, int pos) {
        if (file == null) {
            return true;
        }

        Pair<JavaFileObject, Integer> coords = new Pair<>(file, pos);
        boolean shouldReport = !recorded.contains(coords);
        if (shouldReport) {
            recorded.add(coords);
        }
        return shouldReport;
    }

    /**
     * Returns true if a diagnostics needs to be reported.
     */
    private boolean shouldReport(JcDiagnostic d) {
        JavaFileObject file = d.getSource();

        if (file == null) {
            return true;
        }

        if (!shouldReport(file, (int) d.getPosition())) {
            return false;
        }

        return true;
    }

    //where
    private AnnList<String> getCode(JcDiagnostic d) {
        ListBuffer<String> buf = new ListBuffer<>();
        getCodeRecursive(buf, d);
        return buf.toList();
    }

    private void getCodeRecursive(ListBuffer<String> buf, JcDiagnostic d) {
        buf.add(d.getCode());
    }

    /**
     * Print the text of a message, translating newlines appropriately
     * for the platform.
     */
    public static void printRawLines(PrintWriter writer, String msg) {
        int nl;
        while ((nl = msg.indexOf('\n')) != -1) {
            writer.println(msg.substring(0, nl));
            msg = msg.substring(nl + 1);
        }
        if (!msg.isEmpty()) {
            writer.println(msg);
        }
    }

    /**
     * Write out a diagnostic.
     */
    protected void writeDiagnostic(JcDiagnostic diag) {

        PrintWriter writer = getWriterForDiagnosticType(diag.getType());

        printRawLines(writer, diagFormatter.format(diag));

        if (dumpOnError) {
            new RuntimeException().printStackTrace(writer);
        }

        writer.flush();
    }

    protected PrintWriter getWriterForDiagnosticType(JcDiagnostic.DiagnosticType dt) {
        return switch (dt) {
            case NOTE -> writers.get(WriterKind.NOTICE);
            case WARNING -> writers.get(WriterKind.WARNING);
            case ERROR -> writers.get(WriterKind.ERROR);
            default -> throw new Error();
        };
    }

    public JavaFileObject useSource(JavaFileObject file) {
        JavaFileObject prev = (source == null ? null : source.getFile());
        source = getSource(file);
        return prev;
    }

    protected DiagnosticSource getSource(JavaFileObject file) {
        if (file == null) {
            return DiagnosticSource.NO_SOURCE;
        }
        DiagnosticSource s = sourceMap.get(file);
        if (s == null) {
            s = new DiagnosticSource(file);
            sourceMap.put(file, s);
        }
        return s;
    }

    /**
     * Report an error, unless another error was already reported at same
     * source position.
     *
     * @param pos The source position at which to report the error.
     * @param msg The key for the localized error message.
     */
    public void error(int pos, String msg) {
        report(diags.error(null, source, pos, msg));
    }

    /**
     * Report a warning, unless suppressed by the  -nowarn option or the
     * maximum number of warnings has been reached.
     *
     * @param pos The source position at which to report the warning.
     * @param msg The key for the localized warning message.
     */
    public void warning(int pos, String msg) {
        report(diags.warning(source, pos, msg));
    }

    /**
     * Provide a non-fatal notification, unless suppressed by the -nowarn option.
     *
     * @param pos The source position at which to report the warning.
     * @param msg The key for the localized notification message.
     */
    public void note(int pos, String msg) {
        report(diags.note(source, pos, msg));
    }

    public void report(JcDiagnostic diagnostic) {
        if (expectDiagKeys != null) {
            expectDiagKeys.remove(diagnostic.getCode());
        }

        switch (diagnostic.getType()) {
            case NOTE -> {
                // Print out notes only when we are permitted to report warnings
                // Notes are only generated at the end of a compilation, so should be small
                // in number.
                if ((emitWarnings || diagnostic.isMandatory()) && !suppressNotes) {
                    writeDiagnostic(diagnostic);
                }
            }
            case WARNING -> {
                if (emitWarnings || diagnostic.isMandatory()) {
                    if (nwarnings < maxWarnings) {
                        writeDiagnostic(diagnostic);
                        nwarnings++;
                    } else {
                        nsuppressedwarns++;
                    }
                }
            }
            case ERROR -> {
                if (shouldReport(diagnostic)) {
                    if (nerrors < maxErrors) {
                        writeDiagnostic(diagnostic);
                        nerrors++;
                    } else {
                        nsuppressederrors++;
                    }
                }
            }
        }
    }

    public enum WriterKind {
        NOTICE, WARNING, ERROR
    }
}
