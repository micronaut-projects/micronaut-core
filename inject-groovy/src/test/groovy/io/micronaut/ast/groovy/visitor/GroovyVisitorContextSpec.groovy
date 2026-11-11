package io.micronaut.ast.groovy.visitor

import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

class GroovyVisitorContextSpec extends Specification {

    static final String VALID_CUSTOM_PROP = "micronaut.custom.prop"
    static final String ANOTHER_CUSTOM_PROP = "micronaut.another.custom.prop"
    static final String INVALID_CUSTOM_PROP = "invalid.custom.prop"

    def "getOptions does not expose compiler -A flags when none are configured"() {
        given:
        def context = newVisitorContext(new CompilerConfiguration())

        expect:
        !context.options.containsKey(VALID_CUSTOM_PROP)
    }

    def "getOptions reads micronaut processor flags from joint compilation configuration"() {
        given:
        def configuration = new CompilerConfiguration()
        configuration.jointCompilationOptions = [
            flags: ["A${VALID_CUSTOM_PROP}=from-flag", "proc:none"] as String[],
            namedValues: ["A${ANOTHER_CUSTOM_PROP}", "from-named"] as String[]
        ]

        when:
        def options = newVisitorContext(configuration).options

        then:
        options[VALID_CUSTOM_PROP] == "from-flag"
        options[ANOTHER_CUSTOM_PROP] == "from-named"
        !options.containsKey(INVALID_CUSTOM_PROP)
        !options.containsKey("proc:none")
    }

    def "getOptions reads micronaut keys stored directly on joint compilation options"() {
        given:
        def configuration = new CompilerConfiguration()
        configuration.jointCompilationOptions = [
            (VALID_CUSTOM_PROP): "from-map",
            (INVALID_CUSTOM_PROP): "ignored"
        ]

        when:
        def options = newVisitorContext(configuration).options

        then:
        options[VALID_CUSTOM_PROP] == "from-map"
        !options.containsKey(INVALID_CUSTOM_PROP)
    }

    @RestoreSystemProperties
    def "system properties override compiler configuration options"() {
        given:
        System.setProperty(VALID_CUSTOM_PROP, "from-system")
        def configuration = new CompilerConfiguration()
        configuration.jointCompilationOptions = [
            flags: ["A${VALID_CUSTOM_PROP}=from-flag"] as String[]
        ]

        expect:
        newVisitorContext(configuration).options[VALID_CUSTOM_PROP] == "from-system"
    }

    private static GroovyVisitorContext newVisitorContext(CompilerConfiguration configuration) {
        def sourceUnit = new SourceUnit("test", "", configuration, new GroovyClassLoader(), new ErrorCollector(configuration))
        return new GroovyVisitorContext(sourceUnit, new CompilationUnit(configuration))
    }
}
