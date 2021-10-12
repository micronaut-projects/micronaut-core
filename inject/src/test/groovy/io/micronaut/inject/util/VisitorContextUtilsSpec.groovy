package io.micronaut.inject.util

import spock.lang.Specification
import spock.lang.Stepwise
import spock.util.environment.RestoreSystemProperties

import javax.annotation.processing.ProcessingEnvironment

@Stepwise
class VisitorContextUtilsSpec extends Specification {
    public static final String INVALID_CUSTOM_PROP_1 = "invalid.custom.prop"
    public static final String VALID_CUSTOM_PROP_1 = "micronaut.custom.prop"
    public static final String VALID_CUSTOM_PROP_2 = "micronaut.another.custom.prop"

    @RestoreSystemProperties
    def "should build options map from system properties"() {
        given:

        System.setProperty(VALID_CUSTOM_PROP_1, "test1")
        System.setProperty(INVALID_CUSTOM_PROP_1, "test1")
        System.setProperty(VALID_CUSTOM_PROP_2, "test1")

        when:

        def options = VisitorContextUtils.getSystemOptions()

        then:

        options != null
        !options.isEmpty()
        options.containsKey(VALID_CUSTOM_PROP_1)
        options.containsKey(VALID_CUSTOM_PROP_2)
        !options.containsKey(INVALID_CUSTOM_PROP_1)

    }

    def "should contains base options"() {
        given:

        Map<String, String> mapOpts = new HashMap<>()
        def env = Mock(ProcessingEnvironment)
        mapOpts.put(INVALID_CUSTOM_PROP_1, "test1")
        mapOpts.put(VALID_CUSTOM_PROP_1, "test2")
        mapOpts.put(VALID_CUSTOM_PROP_2, "test3")
        env.getOptions() >> mapOpts

        when:

        def options = VisitorContextUtils.getProcessorOptions(env)

        then:

        with(options) {
            size() == 2
            containsKey(VALID_CUSTOM_PROP_1)
            containsKey(VALID_CUSTOM_PROP_2)
            !containsKey(INVALID_CUSTOM_PROP_1)
        }
    }

    def "system options should not contain custom properties"() {
        expect:

        !VisitorContextUtils.getSystemOptions().containsKey(VALID_CUSTOM_PROP_1)
        !VisitorContextUtils.getSystemOptions().containsKey(VALID_CUSTOM_PROP_2)
    }
}
