package io.micronaut.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.spi.ContextAwareBase;

public class CustomConfigurator extends ContextAwareBase implements Configurator {

    public static final String LOGGER_NAME = "programmatically.configured";
    public static final Level LOGGER_LEVEL = Level.TRACE;

    @Override
    public void configure(LoggerContext loggerContext) {
        loggerContext.getLogger(LOGGER_NAME).setLevel(LOGGER_LEVEL);
    }
}
