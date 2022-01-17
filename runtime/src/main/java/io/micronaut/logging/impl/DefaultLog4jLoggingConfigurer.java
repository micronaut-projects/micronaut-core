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
package io.micronaut.logging.impl;

import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.util.Locale;
import java.util.Map;

/**
 * Log4j default implementation of {@link io.micronaut.context.logging.LoggingConfigurer}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
public class DefaultLog4jLoggingConfigurer extends AbstractLoggingConfigurer<Configuration> {

    @Override
    public void apply(Environment environment) {
        final LoggerContext ctx = (LoggerContext) LogManager.getContext();
        Configuration config = ctx.getConfiguration();

        Util.configureLogLevels(environment, (loggerName, levelValue) -> {
            String levelString = levelValue.toString().toUpperCase(Locale.ROOT);
            Level level = Level.getLevel(levelString);
            if (level == null && StringUtils.isNotEmpty(levelString)) {
                throw new ConfigurationException("Invalid log level: '" + levelValue + "' for logger: '" + loggerName + "'");
            }
            LoggerConfig loggerConfig = config.getLoggerConfig(loggerName);
            if (!loggerName.equals(loggerConfig.getName())) {
                loggerConfig = new LoggerConfig(loggerName, level, true);
                config.addLogger(loggerName, loggerConfig);
                loggerConfig.setLevel(level);
            } else {
                loggerConfig.setLevel(level);
            }
        });

        configureAppenders(environment, config);
        ctx.updateLoggers();
    }

    @Override
    protected void addConsoleAppender(Configuration config, String name, String pattern, Map<String, Object> conf) {
        ConsoleAppender consoleAppender = ConsoleAppender.newBuilder()
                .setConfiguration(config)
                .setName(name)
                .setTarget(ConsoleAppender.Target.SYSTEM_OUT)
                .setLayout(PatternLayout.newBuilder().withConfiguration(config).withPattern(pattern).build())
                .build();
        consoleAppender.start();
        config.addAppender(consoleAppender);
        appendToRoot(config, conf, consoleAppender);
    }

    @Override
    protected void addFileAppender(Configuration config, String name, String pattern, String file, Map<String, Object> conf) {
        FileAppender fileAppender = FileAppender.newBuilder()
                .setConfiguration(config)
                .setLayout(PatternLayout.newBuilder().withConfiguration(config).withPattern(pattern).build())
                .withFileName(file)
                .setName(name)
                .build();
        fileAppender.start();
        config.addAppender(fileAppender);
        appendToRoot(config, conf, fileAppender);
    }

    private void appendToRoot(Configuration config, Map<String, Object> conf, Appender appender) {
        if (shouldAppendToRoot(conf)) {
            config.getRootLogger().addAppender(appender, null, null);
        }
    }
}
