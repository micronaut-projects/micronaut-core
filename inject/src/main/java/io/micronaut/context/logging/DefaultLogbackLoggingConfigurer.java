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
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Logback default implementation of {@link LoggingConfigurer}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
public class DefaultLogbackLoggingConfigurer implements LoggingConfigurer {

    @Override
    public void apply(Environment environment) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) loggerFactory;
            Util.configureLogLevels(environment, (loggerPrefix, levelValue) -> {
                Level level = Level.toLevel(levelValue.toString().toUpperCase(Locale.ROOT));
                loggerContext.getLogger(loggerPrefix).setLevel(level);
            });

            Map<String, Object> appenders = environment.get("logger.appenders", Map.class).orElse(null);
            if (appenders != null) {
                for (Map.Entry<String, Object> e : appenders.entrySet()) {
                    String name = e.getKey();
                    Map<String, Object> conf = (Map<String, Object>) e.getValue();
                    if (conf == null) {
                        conf = Collections.emptyMap();
                    }
                    String type = (String) conf.get("type");
                    if (type == null) {
                        type = name;
                    }
                    if ("console".equals(type)) {
                        addConsoleAppender(loggerContext, name, conf);
                    } else if ("file".equals(type)) {
                        addFileAppender(loggerContext, name, conf);
                    } else {
                        throw new IllegalStateException("Unrecognized type for appender: '" + name + "'. Use 'type' property or have the name one of: 'console', 'file'");
                    }
                }
            }
        }
    }

    private void addConsoleAppender(LoggerContext loggerContext, String name, Map<String, Object> conf) {
        String pattern = (String) conf.get("pattern");
        if (pattern == null) {
            throw new IllegalStateException("Console appender is required to have the 'pattern' property specified!");
        }
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

    private void addFileAppender(LoggerContext loggerContext, String name, Map<String, Object> conf) {
        String pattern = (String) conf.get("pattern");
        if (pattern == null) {
            throw new IllegalStateException("Console appender is required to have the 'pattern' property specified!");
        }
        String file = (String) conf.get("file");
        if (file == null) {
            throw new IllegalStateException("File appender is required to have the 'file' property specified!");
        }
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

    private void appendToRoot(LoggerContext loggerContext, Map<String, Object> conf, Appender<ILoggingEvent> consoleAppender) {
        String appendToRoot = (String) conf.get("appendToRoot");
        if (appendToRoot == null || !appendToRoot.toLowerCase(Locale.ROOT).equals("true")) {
            loggerContext.getLogger("root").addAppender(consoleAppender);
        }
    }

}
