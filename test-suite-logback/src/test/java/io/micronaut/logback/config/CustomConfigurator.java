package io.micronaut.logback.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.layout.TTLLLayout;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.ContextAwareBase;

/**
 * @see <a href="https://github.com/qos-ch/logback/blob/master/logback-classic/src/main/java/ch/qos/logback/classic/BasicConfigurator.java">Logback Class Basic Configurator</a>
 */
public class CustomConfigurator extends ContextAwareBase implements Configurator {

    public static final Level ROOT_LOGGER_LEVEL = Level.INFO;

    @Override
    public ExecutionStatus configure(LoggerContext lc) {
        addInfo("Setting up default configuration.");
        ConsoleAppender<ILoggingEvent> ca = startConsoleAppender(lc);
        configureRootRootLogger(lc, ca);

        final String pkg = "io.micronaut.logback";
        Logger appPkgLogger = lc.getLogger(pkg);
        appPkgLogger.setLevel(Level.TRACE);
        appPkgLogger.setAdditive(false);
        appPkgLogger.addAppender(ca);

        Logger controllersLogger = lc.getLogger(pkg + ".controllers");
        controllersLogger.setLevel(Level.INFO);
        controllersLogger.setAdditive(false);
        controllersLogger.addAppender(ca);

        Logger configuredLogger = lc.getLogger("i.should.exist");
        configuredLogger.setLevel(Level.TRACE);
        configuredLogger.setAdditive(false);
        configuredLogger.addAppender(ca);

        Logger mnLogger = lc.getLogger("io.micronaut.runtime.Micronaut");
        mnLogger.setLevel(Level.INFO);
        mnLogger.setAdditive(false);
        mnLogger.addAppender(ca);

        return ExecutionStatus.NEUTRAL;
    }

    private void configureRootRootLogger(LoggerContext lc, ConsoleAppender<ILoggingEvent> ca) {
        Logger rootLogger = lc.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(ROOT_LOGGER_LEVEL);
        rootLogger.addAppender(ca);
    }

    private ConsoleAppender<ILoggingEvent> startConsoleAppender(LoggerContext lc) {
        ConsoleAppender<ILoggingEvent> ca = new ConsoleAppender<>();
        ca.setContext(lc);
        ca.setName("stdout");
        LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
        encoder.setContext(lc);

        // same as
        // PatternLayout layout = new PatternLayout();
        // layout.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} -
        // %msg%n");
        TTLLLayout layout = new TTLLLayout();

        layout.setContext(lc);
        layout.start();
        encoder.setLayout(layout);

        ca.setEncoder(encoder);
        ca.start();
        return ca;
    }
}
