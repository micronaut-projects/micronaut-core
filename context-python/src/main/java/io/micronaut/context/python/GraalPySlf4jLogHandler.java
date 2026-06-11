/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.python;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Routes GraalVM engine log records through SLF4J instead of the default polyglot console handler.
 */
final class GraalPySlf4jLogHandler extends Handler {
    private static final String DEFAULT_LOGGER_NAME = "org.graalvm.polyglot";

    @Override
    public void publish(LogRecord record) {
        if (record == null || !isLoggable(record)) {
            return;
        }
        Logger logger = LoggerFactory.getLogger(loggerName(record));
        String message = message(record);
        Throwable thrown = record.getThrown();
        int level = record.getLevel().intValue();
        if (level >= Level.SEVERE.intValue()) {
            logError(logger, message, thrown);
        } else if (level >= Level.WARNING.intValue()) {
            logWarn(logger, message, thrown);
        } else if (level >= Level.INFO.intValue()) {
            logInfo(logger, message, thrown);
        } else if (level >= Level.FINE.intValue()) {
            logDebug(logger, message, thrown);
        } else {
            logTrace(logger, message, thrown);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    static String loggerName(LogRecord record) {
        String loggerName = record.getLoggerName();
        if (loggerName == null || loggerName.isBlank()) {
            return DEFAULT_LOGGER_NAME;
        }
        return loggerName;
    }

    static String message(LogRecord record) {
        String message = localizedMessage(record);
        Object[] parameters = record.getParameters();
        if (message == null || parameters == null || parameters.length == 0) {
            return message;
        }
        try {
            return MessageFormat.format(message, parameters);
        } catch (IllegalArgumentException ignored) {
            return message;
        }
    }

    private static String localizedMessage(LogRecord record) {
        String message = record.getMessage();
        ResourceBundle resourceBundle = record.getResourceBundle();
        if (message == null || resourceBundle == null) {
            return message;
        }
        try {
            return resourceBundle.getString(message);
        } catch (MissingResourceException ignored) {
            return message;
        }
    }

    private static void logError(Logger logger, String message, Throwable thrown) {
        if (thrown == null) {
            logger.error(message);
        } else {
            logger.error(message, thrown);
        }
    }

    private static void logWarn(Logger logger, String message, Throwable thrown) {
        if (thrown == null) {
            logger.warn(message);
        } else {
            logger.warn(message, thrown);
        }
    }

    private static void logInfo(Logger logger, String message, Throwable thrown) {
        if (thrown == null) {
            logger.info(message);
        } else {
            logger.info(message, thrown);
        }
    }

    private static void logDebug(Logger logger, String message, Throwable thrown) {
        if (thrown == null) {
            logger.debug(message);
        } else {
            logger.debug(message, thrown);
        }
    }

    private static void logTrace(Logger logger, String message, Throwable thrown) {
        if (thrown == null) {
            logger.trace(message);
        } else {
            logger.trace(message, thrown);
        }
    }
}
