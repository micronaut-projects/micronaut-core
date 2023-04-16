package io.micronaut.annotation.processing.visitor.log;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.CharBuffer;

import javax.tools.JavaFileObject;

/**
 * A simple abstraction of a source file, as needed for use in a diagnostic message.
 * Provides access to the line and position in a line for any given character offset.
 */
class DiagnosticSource {

    /* constant DiagnosticSource to be used when sourcefile is missing */
    public static final DiagnosticSource NO_SOURCE = new DiagnosticSource() {
        @Override
        protected boolean findLine(int pos) {
            return false;
        }
    };

    /**
     * Carriage return character.
     */
    static final byte CR = 0xD;
    /**
     * Line feed character.
     */
    static final byte LF = 0xA;
    /**
     * Tabulator column increment.
     */
    static final int TAB_INC = 8;
    /**
     * Standard indentation for subdiagnostics
     */
    static final int DIAG_INC = 4;
    /**
     * Standard indentation for additional diagnostic lines
     */
    static final int DETAILS_INC = 2;

    /**
     * The underlying file object.
     */
    protected JavaFileObject fileObject;
    /**
     * A soft reference to the content of the file object.
     */
    protected SoftReference<char[]> refBuf;
    /**
     * A temporary hard reference to the content of the file object.
     */
    protected char[] buf;
    /**
     * The length of the content.
     */
    protected int bufLen;
    /**
     * The start of a line found by findLine.
     */
    protected int lineStart;
    /**
     * The line number of a line found by findLine.
     */
    protected int line;

    /**
     * Bump column to the next tab.
     */
    static int tabulate(int column) {
        return (column / TAB_INC * TAB_INC) + TAB_INC;
    }

    public static char[] toArray(CharBuffer buffer) {
        if (buffer.hasArray()) {
            return buffer.compact().flip().array();
        } else {
            return buffer.toString().toCharArray();
        }
    }

    DiagnosticSource(JavaFileObject fo) {
        fileObject = fo;
    }

    private DiagnosticSource() {
    }

    /**
     * Return the underlying file object handled by this
     * DiagnosticSource object.
     */
    public JavaFileObject getFile() {
        return fileObject;
    }

    /**
     * Return the one-based line number associated with a given pos
     * for the current source file.  Zero is returned if no line exists
     * for the given position.
     */
    public int getLineNumber(int pos) {
        try {
            if (findLine(pos)) {
                return line;
            }
            return 0;
        } finally {
            buf = null;
        }
    }

    /**
     * Return the one-based column number associated with a given pos
     * for the current source file.  Zero is returned if no column exists
     * for the given position.
     */
    public int getColumnNumber(int pos, boolean expandTabs) {
        try {
            if (findLine(pos)) {
                int column = 0;
                for (int bp = lineStart; bp < pos; bp++) {
                    if (bp >= bufLen) {
                        return 0;
                    }
                    if (buf[bp] == '\t' && expandTabs) {
                        column = tabulate(column);
                    } else {
                        column++;
                    }
                }
                return column + 1; // positions are one-based
            }
            return 0;
        } finally {
            buf = null;
        }
    }

    /**
     * Return the content of the line containing a given pos.
     */
    public String getLine(int pos) {
        try {
            if (!findLine(pos)) {
                return null;
            }

            int lineEnd = lineStart;
            while (lineEnd < bufLen && buf[lineEnd] != CR && buf[lineEnd] != LF) {
                lineEnd++;
            }
            if (lineEnd - lineStart == 0) {
                return null;
            }
            return new String(buf, lineStart, lineEnd - lineStart);
        } finally {
            buf = null;
        }
    }

    /**
     * Find the line in the buffer that contains the current position
     *
     * @param pos Character offset into the buffer
     */
    protected boolean findLine(int pos) {
        if (pos == Log.NOPOS) {
            return false;
        }

        try {
            // try and recover buffer from soft reference cache
            if (buf == null && refBuf != null) {
                buf = refBuf.get();
            }

            if (buf == null) {
                buf = initBuf(fileObject);
                lineStart = 0;
                line = 1;
            } else if (lineStart > pos) { // messages don't come in order
                lineStart = 0;
                line = 1;
            }

            int bp = lineStart;
            while (bp < bufLen && bp < pos) {
                switch (buf[bp++]) {
                    case CR -> {
                        if (bp < bufLen && buf[bp] == LF) {
                            bp++;
                        }
                        line++;
                        lineStart = bp;
                    }
                    case LF -> {
                        line++;
                        lineStart = bp;
                    }
                }
            }
            return bp <= bufLen;
        } catch (IOException e) {
            System.err.println("Source unavailable " + e.getMessage());
            buf = new char[0];
            return false;
        }
    }

    protected char[] initBuf(JavaFileObject fileObject) throws IOException {
        char[] buf;
        CharSequence cs = fileObject.getCharContent(true);
        if (cs instanceof CharBuffer charBuffer) {
            buf = toArray(charBuffer);
            bufLen = charBuffer.limit();
        } else {
            buf = cs.toString().toCharArray();
            bufLen = buf.length;
        }
        refBuf = new SoftReference<>(buf);
        return buf;
    }
}
