import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.micronaut.context.exceptions.ConfigurationException
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.Configuration
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.slf4j.Log4jLogger
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.Unroll

class Log4jConfigurerSpec extends Specification {

    void setup() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext()
        ctx.reconfigure(new DefaultConfiguration())
        ctx.updateLoggers()
    }

    @Unroll
    void 'test that log levels on logger "#loggerName" can be configured via properties'() {
        given:
            LoggerContext ctx = (LoggerContext) LogManager.getContext()

        when:
            def rootLogger = ctx.getRootLogger()
        then:
            rootLogger.level == Level.ERROR

        when:
            ctx.getLogger('foo.bar1').setLevel(Level.DEBUG)
            ctx.getLogger('foo.bar2').setLevel(Level.DEBUG)
            ctx.getLogger('foo.bar3').setLevel(Level.INFO)
            ctx.getLogger('foo.barBaz').setLevel(Level.WARN)
            ctx.getLogger('ignoring.error').setLevel(Level.INFO)

            ApplicationContext context = ApplicationContext.run(
                    [
                            'logger.levels.aaa.bbb.ccc'   : 'OFF',
                            'logger.levels.foo.bar2'      : 'INFO',
                            'logger.levels.foo.bar3'      : '',
                            'logger.levels.foo.barBaz'    : 'INFO',
                            'logger.levels.ignoring.error': 'OFF',
                    ]
            )

        then:
            ctx.getLogger(loggerName).getLevel() == expectedLevel

        cleanup:
            context.close()

        where:
            loggerName       | expectedLevel
            'foo.bar1'       | Level.ERROR // root
            'foo.bar2'       | Level.INFO
            'foo.bar3'       | Level.ERROR // root
            'aaa.bbb.ccc'    | Level.OFF
            'foo.barBaz'     | Level.INFO
            'ignoring.error' | Level.OFF
    }

    void 'test that log level OFF without quotes does not get treated as boolean false'() {
        given:
            LoggerContext ctx = (LoggerContext) LogManager.getContext()
            def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  levels:
    io.annoying.class: OFF
'''.bytes)

        when:
            ApplicationContext context = ApplicationContext.builder()
                    .propertySources(PropertySource.of(map))
                    .start()

        then:
            ctx.getLogger("io.annoying.class").getLevel() == Level.OFF

        cleanup:
            context.close()
    }

    void 'test that log level ON throws BeanInstantiationException with nested cause of ConfigurationException'() {
        given:
            LoggerContext ctx = (LoggerContext) LogManager.getContext()
            def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  levels:
    io.annoying.class: ON
'''.bytes)

        when:
            ApplicationContext.builder()
                    .propertySources(PropertySource.of(map))
                    .start()

        then:
            thrown(ConfigurationException)
    }

    void 'test that logger console pattern appender'() {
        given:
            LoggerContext ctx = (LoggerContext) LogManager.getContext()
            def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  appenders:
    console:
      pattern: '%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n'
  levels:
    xyz: INFO
'''.bytes)
        when:
            def rootLogger = ctx.getLogger("root")
        then:
            ctx.getConfiguration().getAppender("console") == null

        when:
            ApplicationContext context = ApplicationContext.builder()
                    .propertySources(PropertySource.of(map))
                    .start()
            def logger = ctx.getLogger("xyz")
        then:
            logger.getLevel() == Level.INFO
            ctx.getConfiguration().getAppender("console") != null

        cleanup:
            context.close()
    }

    void 'test that logger file pattern appender'() {
        given:
            final LoggerContext ctx = (LoggerContext) LogManager.getContext()

            def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
logger:
  appenders:
    console:
      pattern: '%d [%t] %-5level %logger{36} - %msg%n%throwable'
    custom-console:
      pattern: '%d [%t] %-5level %logger{36} - %msg%n%throwable'
      type: console
    file:
      file: out.log
      pattern: '%d [%t] %-5level %logger{36} - %msg%n%throwable'
    custom-file:
      append-to-root: false
      file: out2.log
      type: file
      pattern: '%d [%t] %-5level %logger{36} - %msg%n%throwable'
  levels:
    xyz: INFO
'''.bytes)
        when:
            Configuration config = ctx.getConfiguration()
            def rootLogger = LoggerFactory.getLogger("root")
        then:
            rootLogger instanceof Log4jLogger
            config.getAppender("console") == null
            config.getAppender("file") == null

        when:
            ApplicationContext context = ApplicationContext.builder()
                    .propertySources(PropertySource.of(map))
                    .start()
        then:
            config.getAppender("console") != null
            config.getAppender("custom-console") != null
            config.getAppender("file") != null
            config.getAppender("custom-file") != null
            ctx.getRootLogger().getAppenders().size() == 4 // 3 defined + default

        cleanup:
            context.close()
    }

}
