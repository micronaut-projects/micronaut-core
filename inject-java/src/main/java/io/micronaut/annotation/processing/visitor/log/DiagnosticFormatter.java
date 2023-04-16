package io.micronaut.annotation.processing.visitor.log;

import java.util.Set;

import javax.tools.Diagnostic;

/**
 * Provides simple functionalities for javac diagnostic formatting.
 * @param <D> type of diagnostic handled by this formatter
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
interface DiagnosticFormatter<D extends Diagnostic<?>> {

    /**
     * Whether the source code output for this diagnostic is to be displayed.
     *
     * @param diag diagnostic to be formatted
     * @return true if the source line this diagnostic refers to is to be displayed
     */
    boolean displaySource(D diag);

    /**
     * Format the contents of a diagnostics.
     *
     * @param diag the diagnostic to be formatted
     * @return a string representing the diagnostic
     */
    String format(D diag);

    /**
     * Controls the way in which a diagnostic message is displayed.
     *
     * @param diag diagnostic to be formatted
     * @return string representation of the diagnostic message
     */
    String formatMessage(D diag);

    /**
     * Controls the way in which a diagnostic kind is displayed.
     *
     * @param diag diagnostic to be formatted
     * @return string representation of the diagnostic prefix
     */
    String formatKind(D diag);

    /**
     * Controls the way in which a diagnostic source is displayed.
     *
     * @param diag diagnostic to be formatted
     * @param fullname whether the source fullname should be printed
     * @return string representation of the diagnostic source
     */
    String formatSource(D diag, boolean fullname);

    /**
     * Controls the way in which a diagnostic position is displayed.
     *
     * @param diag diagnostic to be formatted
     * @param pk enum constant representing the position kind
     * @return string representation of the diagnostic position
     */
    String formatPosition(D diag, DiagnosticFormatter.PositionKind pk);
    //where
    /**
     * This enum defines a set of constants for all the kinds of position
     * that a diagnostic can be asked for. All positions are intended to be
     * relative to a given diagnostic source.
     */
    enum PositionKind {
        /**
         * Start position
         */
        START,
        /**
         * End position
         */
        END,
        /**
         * Line number
         */
        LINE,
        /**
         * Column number
         */
        COLUMN,
        /**
         * Offset position
         */
        OFFSET
    }

    /**
     * Get a list of all the enabled verbosity options.
     * @return verbosity options
     */
    DiagnosticFormatter.Configuration getConfiguration();
    //where

    /**
     * This interface provides functionalities for tuning the output of a
     * diagnostic formatter in multiple ways.
     */
    interface Configuration {
        /**
         * Configure the set of diagnostic parts that should be displayed
         * by the formatter.
         * @param visibleParts the parts to be set
         */
        void setVisible(Set<DiagnosticFormatter.Configuration.DiagnosticPart> visibleParts);

        /**
         * Retrieve the set of diagnostic parts that should be displayed
         * by the formatter.
         * @return verbosity options
         */
        Set<DiagnosticFormatter.Configuration.DiagnosticPart> getVisible();

        //where
        /**
         * A given diagnostic message can be divided into sub-parts each of which
         * might/might not be displayed by the formatter, according to the
         * current configuration settings.
         */
        enum DiagnosticPart {
            /**
             * Short description of the diagnostic - usually one line long.
             */
            SUMMARY,
            /**
             * Longer description that provides additional details w.r.t. the ones
             * in the diagnostic's description.
             */
            DETAILS,
            /**
             * Source line the diagnostic refers to (if applicable).
             */
            SOURCE,
            /**
             * Subdiagnostics attached to a given multiline diagnostic.
             */
            SUBDIAGNOSTICS,
            /**
             * JLS paragraph this diagnostic might refer to (if applicable).
             */
            JLS
        }

        /**
         * Set a limit for multiline diagnostics.
         * Note: Setting a limit has no effect if multiline diagnostics are either
         * fully enabled or disabled.
         *
         * @param limit the kind of limit to be set
         * @param value the limit value
         */
        void setMultilineLimit(DiagnosticFormatter.Configuration.MultilineLimit limit, int value);

        /**
         * Get a multiline diagnostic limit.
         *
         * @param limit the kind of limit to be retrieved
         * @return limit value or -1 if no limit is set
         */
        int getMultilineLimit(DiagnosticFormatter.Configuration.MultilineLimit limit);
        //where
        /**
         * A multiline limit control the verbosity of multiline diagnostics
         * either by setting a maximum depth of nested multidiagnostics,
         * or by limiting the amount of subdiagnostics attached to a given
         * diagnostic (or both).
         */
        enum MultilineLimit {
            /**
             * Controls the maximum depth of nested multiline diagnostics.
             */
            DEPTH,
            /**
             * Controls the maximum amount of subdiagnostics that are part of a
             * given multiline diagnostic.
             */
            LENGTH
        }
    }
}
