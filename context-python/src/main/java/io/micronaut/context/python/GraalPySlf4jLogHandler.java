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

import io.micronaut.logging.LogLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Routes GraalVM engine log records through SLF4J instead of the default polyglot console handler.
 */
final class GraalPySlf4jLogHandler extends Handler {
    static final String POLYGLOT_LEVEL_ALL = "ALL";
    static final String POLYGLOT_LEVEL_FINEST = "FINEST";
    static final String POLYGLOT_LEVEL_FINE = "FINE";
    static final String POLYGLOT_LEVEL_INFO = "INFO";
    static final String POLYGLOT_LEVEL_WARNING = "WARNING";
    static final String POLYGLOT_LEVEL_SEVERE = "SEVERE";
    static final String POLYGLOT_LEVEL_OFF = "OFF";
    private static final String BOOLEAN_FALSE = "false";
    private static final String DEFAULT_LOGGER_NAME = "org.graalvm.polyglot";
    private static final Map<String, Logger> CACHED_LOGGERS = new ConcurrentHashMap<>();

    static String polyglotRootLevel() {
        return polyglotLevel(LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    }

    static String polyglotLevel(Logger logger) {
        if (logger.isTraceEnabled()) {
            return POLYGLOT_LEVEL_FINEST;
        }
        if (logger.isDebugEnabled()) {
            return POLYGLOT_LEVEL_FINE;
        }
        if (logger.isInfoEnabled()) {
            return POLYGLOT_LEVEL_INFO;
        }
        if (logger.isWarnEnabled()) {
            return POLYGLOT_LEVEL_WARNING;
        }
        if (logger.isErrorEnabled()) {
            return POLYGLOT_LEVEL_SEVERE;
        }
        return POLYGLOT_LEVEL_OFF;
    }

    static @Nullable String polyglotLevel(@Nullable String slf4jLevel) {
        if (slf4jLevel == null || slf4jLevel.isBlank()) {
            return null;
        }
        if (BOOLEAN_FALSE.equalsIgnoreCase(slf4jLevel)) {
            return POLYGLOT_LEVEL_OFF;
        }
        try {
            return polyglotLevel(LogLevel.valueOf(slf4jLevel.toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable String polyglotLevel(LogLevel slf4jLevel) {
        return switch (slf4jLevel) {
            case ALL -> POLYGLOT_LEVEL_ALL;
            case TRACE -> POLYGLOT_LEVEL_FINEST;
            case DEBUG -> POLYGLOT_LEVEL_FINE;
            case INFO -> POLYGLOT_LEVEL_INFO;
            case WARN -> POLYGLOT_LEVEL_WARNING;
            case ERROR -> POLYGLOT_LEVEL_SEVERE;
            case OFF -> POLYGLOT_LEVEL_OFF;
            case NOT_SPECIFIED -> null;
        };
    }

    @Override
    public void publish(@Nullable LogRecord record) {
        if (record == null) {
            return;
        }
        int level = record.getLevel().intValue();

        if (level >= Level.SEVERE.intValue()) {
            Logger logger = resolveLogger(record);

            if (logger.isErrorEnabled()) {
                String message = message(record);
                if (message != null) {
                    Throwable thrown = record.getThrown();
                    logError(logger, message, thrown);
                }
            }
        } else if (level >= Level.WARNING.intValue()) {
            Logger logger = resolveLogger(record);
            if (logger.isWarnEnabled()) {
                String message = message(record);
                if (message != null) {
                    Throwable thrown = record.getThrown();
                    logWarn(logger, message, thrown);
                }
            }
        } else if (level >= Level.INFO.intValue()) {
            Logger logger = resolveLogger(record);
            if (logger.isInfoEnabled()) {
                String message = message(record);
                if (message != null) {
                    Throwable thrown = record.getThrown();
                    logInfo(logger, message, thrown);
                }
            }
        } else if (level >= Level.FINE.intValue()) {
            Logger logger = resolveLogger(record);
            if (logger.isDebugEnabled()) {
                String message = message(record);
                if (message != null) {
                    Throwable thrown = record.getThrown();
                    logDebug(logger, message, thrown);
                }
            }
        } else {
            Logger logger = resolveLogger(record);
            if (logger.isTraceEnabled()) {
                String message = message(record);
                if (message != null) {
                    Throwable thrown = record.getThrown();
                    logTrace(logger, message, thrown);
                }
            }
        }
    }

    private static Logger resolveLogger(LogRecord record) {
        String loggerName = loggerName(record);
        Logger logger = CACHED_LOGGERS.computeIfAbsent(loggerName, LoggerFactory::getLogger);
        return logger;
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

    static @Nullable String message(LogRecord record) {
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

    private static @Nullable String localizedMessage(LogRecord record) {
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

    private static void logError(Logger logger, String message, @Nullable Throwable thrown) {
        if (thrown == null) {
            logger.error(message);
        } else {
            logger.error(message, thrown);
        }
    }

    private static void logWarn(Logger logger, String message, @Nullable Throwable thrown) {
        if (thrown == null) {
            logger.warn(message);
        } else {
            logger.warn(message, thrown);
        }
    }

    private static void logInfo(Logger logger, String message, @Nullable Throwable thrown) {
        if (thrown == null) {
            logger.info(message);
        } else {
            logger.info(message, thrown);
        }
    }

    private static void logDebug(Logger logger, String message, @Nullable Throwable thrown) {
        if (thrown == null) {
            logger.debug(message);
        } else {
            logger.debug(message, thrown);
        }
    }

    private static void logTrace(Logger logger, String message, @Nullable Throwable thrown) {
        if (thrown == null) {
            logger.trace(message);
        } else {
            logger.trace(message, thrown);
        }
    }
}
