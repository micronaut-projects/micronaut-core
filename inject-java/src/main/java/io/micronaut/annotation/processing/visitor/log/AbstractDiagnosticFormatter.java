package io.micronaut.annotation.processing.visitor.log;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaFileObject;

import io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.DiagnosticPart;
import io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.MultilineLimit;

/**
 * This abstract class provides a basic implementation of the functionalities that should be provided
 * by any formatter used by javac. Among the main features provided by AbstractDiagnosticFormatter are:
 *
 * <ul>
 *  <li> Provides a standard implementation of the visitor-like methods defined in the interface DiagnosticFormatter.
 *  Those implementations are specifically targeting JCDiagnostic objects.
 *  <li> Provides basic support for i18n and a method for executing all locale-dependent conversions
 *  <li> Provides the formatting logic for rendering the arguments of a JCDiagnostic object.
 * </ul>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
abstract class AbstractDiagnosticFormatter implements DiagnosticFormatter<JcDiagnostic> {

    /**
     * Configuration object used by this formatter
     */
    private SimpleConfiguration config;

    /**
     * Current depth level of the diagnostic being formatted
     * (!= 0 for subdiagnostics)
     */
    protected int depth;

    /**
     * Initialize an AbstractDiagnosticFormatter by setting its JavacMessages object.
     */
    protected AbstractDiagnosticFormatter(SimpleConfiguration config) {
        this.config = config;
    }

    @Override
    public String formatKind(JcDiagnostic d) {
        return switch (d.getType()) {
            case NOTE -> "compiler.note.note";
            case WARNING -> "compiler.warn.warning";
            case ERROR -> "compiler.err.error";
            default -> throw new AssertionError("Unknown diagnostic type: " + d.getType());
        };
    }

    @Override
    public String format(JcDiagnostic d) {
        return formatDiagnostic(d);
    }

    protected abstract String formatDiagnostic(JcDiagnostic d);

    @Override
    public String formatPosition(JcDiagnostic d, DiagnosticFormatter.PositionKind pk) {
        if (d.getPosition() == Log.NOPOS) {
            throw new AssertionError();
        }
        return String.valueOf(getPosition(d, pk));
    }

    //where
    private long getPosition(JcDiagnostic d, DiagnosticFormatter.PositionKind pk) {
        return switch (pk) {
            case START -> d.getPosition();
            case END -> d.getPosition();
            case LINE -> d.getLineNumber();
            case COLUMN -> d.getColumnNumber();
            case OFFSET -> d.getPosition();
            default -> throw new AssertionError("Unknown diagnostic position: " + pk);
        };
    }

    @Override
    public String formatSource(JcDiagnostic d, boolean fullname) {
        JavaFileObject fo = d.getSource();
        if (fo == null) {
            throw new IllegalArgumentException(); // d should have source set
        }
        if (fullname) {
            return fo.getName();
        } else {
            URI uri = fo.toUri();
            String s = uri.getSchemeSpecificPart();
            return s.substring(s.lastIndexOf("/") + 1); // safe when / not found
        }
    }

    /**
     * Format a single argument of a given diagnostic.
     *
     * @param d diagnostic whose argument is to be formatted
     * @param arg argument to be formatted
     *
     * @return string representation of the diagnostic argument
     */
    protected String formatArgument(JcDiagnostic d, Object arg) {
        return String.valueOf(arg);
    }

    /**
     * Format an iterable argument of a given diagnostic.
     *
     * @param d diagnostic whose argument is to be formatted
     * @param it iterable argument to be formatted
     *
     * @return string representation of the diagnostic iterable argument
     */
    protected String formatIterable(JcDiagnostic d, Iterable<?> it) {
        StringBuilder sbuf = new StringBuilder();
        String sep = "";
        for (Object o : it) {
            sbuf.append(sep);
            sbuf.append(formatArgument(d, o));
            sep = ",";
        }
        return sbuf.toString();
    }

    /**
     * Format all the subdiagnostics attached to a given diagnostic.
     *
     * @param d diagnostic whose subdiagnostics are to be formatted
     *
     * @return list of all string representations of the subdiagnostics
     */
    protected AnnList<String> formatSubdiagnostics(JcDiagnostic d) {
        AnnList<String> subdiagnostics = AnnList.nil();
        int maxDepth = config.getMultilineLimit(MultilineLimit.DEPTH);
        if (maxDepth == -1 || depth < maxDepth) {
            depth++;
            try {
                int maxCount = config.getMultilineLimit(MultilineLimit.LENGTH);
                int count = 0;
                for (JcDiagnostic d2 : d.getSubdiagnostics()) {
                    if (maxCount == -1 || count < maxCount) {
                        subdiagnostics = subdiagnostics.append(formatSubdiagnostic(d, d2));
                        count++;
                    } else {
                        break;
                    }
                }
            } finally {
                depth--;
            }
        }
        return subdiagnostics;
    }

    /**
     * Format a subdiagnostics attached to a given diagnostic.
     *
     * @param parent multiline diagnostic whose subdiagnostics is to be formatted
     * @param sub subdiagnostic to be formatted
     *
     * @return string representation of the subdiagnostics
     */
    protected String formatSubdiagnostic(JcDiagnostic parent, JcDiagnostic sub) {
        return formatMessage(sub);
    }

    /**
     * Format the faulty source code line and point to the error.
     *
     * @param d The diagnostic for which the error line should be printed
     */
    protected String formatSourceLine(JcDiagnostic d, int nSpaces) {
        StringBuilder buf = new StringBuilder();
        DiagnosticSource source = d.getDiagnosticSource();
        int pos = (int) d.getPosition();
        if (pos == Log.NOPOS) {
            throw new AssertionError();
        }
        String line = (source == null ? null : source.getLine(pos));
        if (line == null) {
            return "";
        }
        buf.append(indent(line, nSpaces));
        int col = source.getColumnNumber(pos, false);
        if (config.isCaretEnabled()) {
            buf.append("\n");
            for (int i = 0; i < col - 1; i++) {
                buf.append((line.charAt(i) == '\t') ? "\t" : " ");
            }
            buf.append(indent("^", nSpaces));
        }
        return buf.toString();
    }

    @Override
    public boolean displaySource(JcDiagnostic d) {
        return config.getVisible().contains(DiagnosticPart.SOURCE) && d.getPosition() != Log.NOPOS;
    }

    public boolean isRaw() {
        return false;
    }

    /**
     * Creates a string with a given amount of empty spaces. Useful for
     * indenting the text of a diagnostic message.
     *
     * @param nSpaces the amount of spaces to be added to the result string
     *
     * @return the indentation string
     */
    protected String indentString(int nSpaces) {
        String spaces = "                        ";
        if (nSpaces <= spaces.length()) {
            return spaces.substring(0, nSpaces);
        } else {
            return " ".repeat(nSpaces);
        }
    }

    /**
     * Indent a string by prepending a given amount of empty spaces to each line
     * of the string.
     *
     * @param s the string to be indented
     * @param nSpaces the amount of spaces that should be prepended to each line
     *     of the string
     *
     * @return an indented string
     */
    protected String indent(String s, int nSpaces) {
        String indent = indentString(nSpaces);
        StringBuilder buf = new StringBuilder();
        String nl = "";
        for (String line : s.split("\n")) {
            buf.append(nl);
            buf.append(indent).append(line);
            nl = "\n";
        }
        return buf.toString();
    }

    @Override
    public SimpleConfiguration getConfiguration() {
        return config;
    }

    public static class SimpleConfiguration implements DiagnosticFormatter.Configuration {

        protected Map<MultilineLimit, Integer> multilineLimits;
        protected EnumSet<DiagnosticPart> visibleParts;
        protected boolean caretEnabled;

        public SimpleConfiguration(Set<DiagnosticPart> parts) {
            multilineLimits = new HashMap<>();
            setVisible(parts);
            setMultilineLimit(MultilineLimit.DEPTH, -1);
            setMultilineLimit(MultilineLimit.LENGTH, -1);
            caretEnabled = true;
        }

        @Override
        public int getMultilineLimit(MultilineLimit limit) {
            return multilineLimits.get(limit);
        }

        @Override
        public EnumSet<DiagnosticPart> getVisible() {
            return EnumSet.copyOf(visibleParts);
        }

        @Override
        public void setMultilineLimit(MultilineLimit limit, int value) {
            multilineLimits.put(limit, value < -1 ? -1 : value);
        }


        @Override
        public void setVisible(Set<DiagnosticPart> diagParts) {
            visibleParts = EnumSet.copyOf(diagParts);
        }

        public void setVisiblePart(DiagnosticPart diagParts, boolean enabled) {
            if (enabled) {
                visibleParts.add(diagParts);
            } else {
                visibleParts.remove(diagParts);
            }
        }

        /**
         * Shows a '^' sign under the source line displayed by the formatter
         * (if applicable).
         *
         * @param caretEnabled if true enables caret
         */
        public void setCaretEnabled(boolean caretEnabled) {
            this.caretEnabled = caretEnabled;
        }

        /**
         * Tells whether the caret display is active or not.
         *
         * @return true if the caret is enabled
         */
        public boolean isCaretEnabled() {
            return caretEnabled;
        }
    }
}
