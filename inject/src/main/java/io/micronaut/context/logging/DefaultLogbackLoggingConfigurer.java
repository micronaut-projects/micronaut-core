/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.util.StringUtils;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

/**
 * Logback default implementation of {@link LoggingConfigurer}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public final class DefaultLogbackLoggingConfigurer extends AbstractLoggingConfigurer<LoggerContext> {

    @Override
    public void apply(Environment environment) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) loggerFactory;
            Util.configureLogLevels(environment, (loggerPrefix, levelValue) -> {
                String levelString = levelValue.toString().toUpperCase(Locale.ROOT);
                Level level = Level.toLevel(levelValue.toString());
                if (level == null && StringUtils.isNotEmpty(levelString)) {
                    throw new ConfigurationException("Invalid log level: '" + levelValue + "' for logger: '" + levelString + "'");
                }
                loggerContext.getLogger(loggerPrefix).setLevel(level);
            });
            configureAppenders(environment, loggerContext);
        }
    }

    protected void addConsoleAppender(LoggerContext loggerContext, String name, String pattern, Map<String, Object> conf) {
        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern(pattern);
        ple.setContext(loggerContext);
        ple.start();
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setWithJansi(true);
        consoleAppender.setEncoder(ple);
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName(name);
        consoleAppender.start();
        appendToRoot(loggerContext, conf, consoleAppender);
    }

    protected void addFileAppender(LoggerContext loggerContext, String name, String pattern, String file, Map<String, Object> conf) {
        PatternLayoutEncoder ple = new PatternLayoutEncoder();
        ple.setPattern(pattern);
        ple.setContext(loggerContext);
        ple.start();
        FileAppender<ILoggingEvent> consoleAppender = new FileAppender<>();
        consoleAppender.setFile(file);
        consoleAppender.setEncoder(ple);
        consoleAppender.setContext(loggerContext);
        consoleAppender.setName(name);
        consoleAppender.start();
        appendToRoot(loggerContext, conf, consoleAppender);
    }

    private void appendToRoot(LoggerContext loggerContext, Map<String, Object> conf, Appender<ILoggingEvent> appender) {
        if (shouldAppendToRoot(conf)) {
            loggerContext.getLogger("root").addAppender(appender);
        }
    }

}
