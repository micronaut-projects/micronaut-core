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
package io.micronaut.cli.console.logging;

/**
 * Interface containing methods for logging to the Micronaut console.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConsoleLogger {

    /**
     * Indicates progress with the default progress indicator.
     */
    void indicateProgress();

    /**
     * Indicate progress for a number and total.
     *
     * @param number The current number
     * @param total  The total number
     */
    void indicateProgress(int number, int total);

    /**
     * Indicates progress as a percentage for the given number and total.
     *
     * @param number The number
     * @param total  The total
     */
    void indicateProgressPercentage(long number, long total);

    /**
     * Indicates progress by number.
     *
     * @param number The number
     */
    void indicateProgress(int number);

    /**
     * Updates the current state message.
     *
     * @param msg The message
     */
    void updateStatus(String msg);

    /**
     * Adds a new message that won't be overwritten by {#updateStatus(String)}.
     *
     * @param msg The message
     */
    void addStatus(String msg);

    /**
     * Prints an error message.
     *
     * @param msg The error message
     */
    void error(String msg);

    /**
     * Prints a warning message.
     *
     * @param msg The warning message
     */
    void warning(String msg);

    /**
     * Prints a warning message.
     *
     * @param msg The warning message
     */
    void warn(String msg);

    /**
     * Use to log an error.
     *
     * @param msg   The message
     * @param error The error
     */
    void error(String msg, Throwable error);

    /**
     * Log an error with a specific error label.
     *
     * @param label   The label
     * @param message The message
     */
    void error(String label, String message);

    /**
     * Use to log an error.
     *
     * @param error The error
     */
    void error(Throwable error);

    /**
     * Logs a message below the current status message.
     *
     * @param msg The message to log
     */
    void log(String msg);

    /**
     * Synonym for #log.
     *
     * @param msg The message to log
     */
    void info(String msg);

    /**
     * Outputs a verbose message.
     *
     * @param msg The message
     */
    void verbose(String msg);
}
