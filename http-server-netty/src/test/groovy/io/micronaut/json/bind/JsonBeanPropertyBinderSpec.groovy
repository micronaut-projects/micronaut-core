package io.micronaut.json.bind

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class JsonBeanPropertyBinderSpec extends Specification {

    def "test map binding"() {
        given:
            ApplicationContext applicationContext = ApplicationContext.run()
            JsonBeanPropertyBinder binder = applicationContext.getBean(JsonBeanPropertyBinder)
        when:
            def jsonNode = binder.buildSourceObjectNode(Map.of("foo", "bar", "abc", ["x", "y", "z"]).entrySet())
        then:
            jsonNode.size() == 2
            jsonNode.isObject()
            jsonNode.get("foo").isArray()
            jsonNode.get("foo").get(0).isString()
            jsonNode.get("foo").get(0).getStringValue() == "bar"
            jsonNode.get("abc").isArray()
            jsonNode.get("abc").get(0).isString()
            jsonNode.get("abc").get(0).getStringValue() == "x"
            jsonNode.get("abc").get(1).getStringValue() == "y"
            jsonNode.get("abc").get(2).getStringValue() == "z"

        cleanup:
            applicationContext.close()
    }

}
