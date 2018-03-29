package io.micronaut.cli.io.support

import groovy.transform.CompileStatic

/**
 * PrintStream that does nothing
 *
 * @author Graeme Rocher
 */
@CompileStatic
class DevNullPrintStream extends PrintStream {

    DevNullPrintStream() {
        super(new ByteArrayOutputStream())
    }

    @Override
    void flush() {
    }

    @Override
    void close() {
    }

    @Override
    boolean checkError() {
    }

    @Override
    protected void setError() {
    }

    @Override
    protected void clearError() {
    }

    @Override
    void write(int b) {
    }

    @Override
    void write(byte[] buf, int off, int len) {
    }

    @Override
    void print(boolean b) {
    }

    @Override
    void print(char c) {
    }

    @Override
    void print(int i) {
    }

    @Override
    void print(long l) {
    }

    @Override
    void print(float f) {
    }

    @Override
    void print(double d) {
    }

    @Override
    void print(char[] s) {
    }

    @Override
    void print(String s) {
    }

    @Override
    void print(Object obj) {
    }

    @Override
    void println() {
    }

    @Override
    void println(boolean x) {
    }

    @Override
    void println(char x) {
    }

    @Override
    void println(int x) {
    }

    @Override
    void println(long x) {
    }

    @Override
    void println(float x) {
    }

    @Override
    void println(double x) {
    }

    @Override
    void println(char[] x) {
    }

    @Override
    void println(String x) {
    }

    @Override
    void println(Object x) {
    }

    @Override
    PrintStream printf(String format, Object... args) {
        return this
    }

    @Override
    PrintStream printf(Locale l, String format, Object... args) {
        return this
    }

    @Override
    PrintStream format(String format, Object... args) {
        return this
    }

    @Override
    PrintStream format(Locale l, String format, Object... args) {
        return this
    }

    @Override
    PrintStream append(CharSequence csq) {
        return this
    }

    @Override
    PrintStream append(CharSequence csq, int start, int end) {
        return this
    }

    @Override
    PrintStream append(char c) {
        return this
    }

    @Override
    void write(byte[] b) throws IOException {
    }
}
