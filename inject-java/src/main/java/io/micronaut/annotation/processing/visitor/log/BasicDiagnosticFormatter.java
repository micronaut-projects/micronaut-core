package io.micronaut.annotation.processing.visitor.log;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Matcher;

import javax.tools.JavaFileObject;

import io.micronaut.annotation.processing.visitor.log.BasicDiagnosticFormatter.BasicConfiguration.BasicFormatKind;
import io.micronaut.annotation.processing.visitor.log.BasicDiagnosticFormatter.BasicConfiguration.SourcePosition;
import io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.DiagnosticPart;

import static io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.DiagnosticPart.DETAILS;
import static io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.DiagnosticPart.SOURCE;
import static io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.DiagnosticPart.SUBDIAGNOSTICS;
import static io.micronaut.annotation.processing.visitor.log.DiagnosticFormatter.Configuration.DiagnosticPart.SUMMARY;
import static io.micronaut.annotation.processing.visitor.log.DiagnosticSource.DETAILS_INC;
import static io.micronaut.annotation.processing.visitor.log.DiagnosticSource.DIAG_INC;

/**
 * A basic formatter for diagnostic messages.
 * The basic formatter will format a diagnostic according to one of three format patterns, depending on whether
 * or not the source name and position are set. The formatter supports a printf-like string for patterns
 * with the following special characters:
 * <ul>
 * <li>%b: the base of the source name
 * <li>%f: the source name (full absolute path)
 * <li>%l: the line number of the diagnostic, derived from the character offset
 * <li>%c: the column number of the diagnostic, derived from the character offset
 * <li>%o: the character offset of the diagnostic if set
 * <li>%p: the prefix for the diagnostic, derived from the diagnostic type
 * <li>%t: the prefix as it normally appears in standard diagnostics. In this case, no prefix is
 *        shown if the type is ERROR and if a source name is set
 * <li>%m: the text or the diagnostic, including any appropriate arguments
 * <li>%_: space delimiter, useful for formatting purposes
 * </ul>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class BasicDiagnosticFormatter extends AbstractDiagnosticFormatter {

    /**
     * Create a standard basic formatter
     */
    public BasicDiagnosticFormatter() {
        super(new BasicDiagnosticFormatter.BasicConfiguration());
    }

    @Override
    public String formatDiagnostic(JcDiagnostic d) {
        String format = selectFormat(d);
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            boolean meta = false;
            if (c == '%' && i < format.length() - 1) {
                meta = true;
                c = format.charAt(++i);
            }
            buf.append(meta ? formatMeta(c, d) : String.valueOf(c));
        }
        if (depth == 0) {
            return addSourceLineIfNeeded(d, buf.toString());
        } else {
            return buf.toString();
        }
    }

    @Override
    public String formatMessage(JcDiagnostic d) {
        int currentIndentation = 0;
        StringBuilder buf = new StringBuilder();
        String msg = d.getCode();
        String[] lines = msg.split("\n");
        if (lines.length == 0) // will happen when msg only contains one or more separators: "\n", "\n\n", etc.
        {
            lines = new String[] {""};
        }
        if (getConfiguration().getVisible().contains(DiagnosticPart.SUMMARY)) {
            currentIndentation += getConfiguration().getIndentation(DiagnosticPart.SUMMARY);
            buf.append(indent(lines[0], currentIndentation)); //summary
        }
        if (lines.length > 1 && getConfiguration().getVisible().contains(DiagnosticPart.DETAILS)) {
            currentIndentation += getConfiguration().getIndentation(DiagnosticPart.DETAILS);
            for (int i = 1; i < lines.length; i++) {
                buf.append("\n").append(indent(lines[i], currentIndentation));
            }
        }
        if (getConfiguration().getVisible().contains(DiagnosticPart.SUBDIAGNOSTICS)) {
            currentIndentation += getConfiguration().getIndentation(DiagnosticPart.SUBDIAGNOSTICS);
            for (String sub : formatSubdiagnostics(d)) {
                buf.append("\n").append(indent(sub, currentIndentation));
            }
        }
        return buf.toString();
    }

    protected String addSourceLineIfNeeded(JcDiagnostic d, String msg) {
        if (!displaySource(d)) {
            return msg;
        }
        BasicDiagnosticFormatter.BasicConfiguration conf = getConfiguration();
        int indentSource = conf.getIndentation(DiagnosticPart.SOURCE);
        String sourceLine = "\n" + formatSourceLine(d, indentSource);
        boolean singleLine = !msg.contains("\n");
        if (singleLine || getConfiguration().getSourcePosition() == SourcePosition.BOTTOM) {
            return msg + sourceLine;
        } else {
            return msg.replaceFirst("\n", Matcher.quoteReplacement(sourceLine) + "\n");
        }
    }

    protected String formatMeta(char c, JcDiagnostic d) {
        switch (c) {
            case 'b' -> {
                return formatSource(d, false);
            }
            case 'e' -> {
                return formatPosition(d, PositionKind.END);
            }
            case 'f' -> {
                return formatSource(d, true);
            }
            case 'l' -> {
                return formatPosition(d, PositionKind.LINE);
            }
            case 'c' -> {
                return formatPosition(d, PositionKind.COLUMN);
            }
            case 'o' -> {
                return formatPosition(d, PositionKind.OFFSET);
            }
            case 'p' -> {
                return formatKind(d);
            }
            case 's' -> {
                return formatPosition(d, PositionKind.START);
            }
            case 't' -> {
                boolean usePrefix;
                switch (d.getType()) {
                    case ERROR:
                        usePrefix = (d.getPosition() == Log.NOPOS);
                        break;
                    default:
                        usePrefix = true;
                }
                if (usePrefix) {
                    return formatKind(d);
                } else {
                    return "";
                }
            }
            case 'm' -> {
                return formatMessage(d);
            }
            case 'L' -> {
                return "";
            }
            case '_' -> {
                return " ";
            }
            case '%' -> {
                return "%";
            }
            default -> {
                return String.valueOf(c);
            }
        }
    }

    private String selectFormat(JcDiagnostic d) {
        DiagnosticSource source = d.getDiagnosticSource();
        String format = getConfiguration().getFormat(BasicFormatKind.DEFAULT_NO_POS_FORMAT);
        if (source != null && source != DiagnosticSource.NO_SOURCE) {
            if (d.getPosition() != Log.NOPOS) {
                format = getConfiguration().getFormat(BasicFormatKind.DEFAULT_POS_FORMAT);
            } else if (source.getFile() != null &&
                source.getFile().getKind() == JavaFileObject.Kind.CLASS) {
                format = getConfiguration().getFormat(BasicFormatKind.DEFAULT_CLASS_FORMAT);
            }
        }
        return format;
    }

    @Override
    public BasicDiagnosticFormatter.BasicConfiguration getConfiguration() {
        //the following cast is always safe - see init
        return (BasicDiagnosticFormatter.BasicConfiguration) super.getConfiguration();
    }

    public static class BasicConfiguration extends SimpleConfiguration {

        protected Map<DiagnosticPart, Integer> indentationLevels;
        protected Map<BasicFormatKind, String> availableFormats;
        protected SourcePosition sourcePosition;

        public BasicConfiguration() {
            super(EnumSet.of(SUMMARY,
                DETAILS,
                SUBDIAGNOSTICS,
                SOURCE));
            initFormat();
            initIndentation();
        }

        private void initFormat() {
            initFormats("%f:%l:%_%p%L%m", "%p%L%m", "%f:%_%p%L%m");
        }

        private void initOldFormat() {
            initFormats("%f:%l:%_%t%L%m", "%p%L%m", "%f:%_%t%L%m");
        }

        private void initFormats(String pos, String nopos, String clazz) {
            availableFormats = new EnumMap<>(BasicFormatKind.class);
            setFormat(BasicFormatKind.DEFAULT_POS_FORMAT, pos);
            setFormat(BasicFormatKind.DEFAULT_NO_POS_FORMAT, nopos);
            setFormat(BasicFormatKind.DEFAULT_CLASS_FORMAT, clazz);
        }

        @SuppressWarnings("fallthrough")
        private void initFormats(String fmt) {
            String[] formats = fmt.split("\\|");
            switch (formats.length) {
                case 3:
                    setFormat(BasicFormatKind.DEFAULT_CLASS_FORMAT, formats[2]);
                case 2:
                    setFormat(BasicFormatKind.DEFAULT_NO_POS_FORMAT, formats[1]);
                default:
                    setFormat(BasicFormatKind.DEFAULT_POS_FORMAT, formats[0]);
            }
        }

        private void initIndentation() {
            indentationLevels = new EnumMap<>(DiagnosticPart.class);
            setIndentation(SUMMARY, 0);
            setIndentation(DETAILS, DETAILS_INC);
            setIndentation(SUBDIAGNOSTICS, DIAG_INC);
            setIndentation(SOURCE, 0);
        }

        /**
         * Get the amount of spaces for a given indentation kind
         *
         * @param diagPart the diagnostic part for which the indentation is
         *     to be retrieved
         *
         * @return the amount of spaces used for the specified indentation kind
         */
        public int getIndentation(DiagnosticPart diagPart) {
            return indentationLevels.get(diagPart);
        }

        /**
         * Set the indentation level for various element of a given diagnostic -
         * this might lead to more readable diagnostics
         *
         * @param diagPart
         * @param nSpaces amount of spaces for the specified diagnostic part
         */
        public void setIndentation(DiagnosticPart diagPart, int nSpaces) {
            indentationLevels.put(diagPart, nSpaces);
        }

        /**
         * Set the source line positioning used by this formatter
         *
         * @param sourcePos a positioning value for source line
         */
        public void setSourcePosition(SourcePosition sourcePos) {
            sourcePosition = sourcePos;
        }

        /**
         * Get the source line positioning used by this formatter
         *
         * @return the positioning value used by this formatter
         */
        public SourcePosition getSourcePosition() {
            return sourcePosition;
        }
        //where

        /**
         * A source positioning value controls the position (within a given
         * diagnostic message) in which the source line the diagnostic refers to
         * should be displayed (if applicable)
         */
        public enum SourcePosition {
            /**
             * Source line is displayed after the diagnostic message
             */
            BOTTOM,
            /**
             * Source line is displayed after the first line of the diagnostic
             * message
             */
            AFTER_SUMMARY
        }

        /**
         * Set a metachar string for a specific format
         *
         * @param kind the format kind to be set
         * @param s the metachar string specifying the format
         */
        public void setFormat(BasicFormatKind kind, String s) {
            availableFormats.put(kind, s);
        }

        /**
         * Get a metachar string for a specific format
         *
         * @param kind the format kind for which to get the metachar string
         */
        public String getFormat(BasicFormatKind kind) {
            return availableFormats.get(kind);
        }
        //where

        /**
         * This enum contains all the kinds of formatting patterns supported
         * by a basic diagnostic formatter.
         */
        public enum BasicFormatKind {
            /**
             * A format string to be used for diagnostics with a given position.
             */
            DEFAULT_POS_FORMAT,
            /**
             * A format string to be used for diagnostics without a given position.
             */
            DEFAULT_NO_POS_FORMAT,
            /**
             * A format string to be used for diagnostics regarding classfiles
             */
            DEFAULT_CLASS_FORMAT
        }
    }
}
