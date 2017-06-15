package org.particleframework.context.env.groovy

import spock.lang.Specification

/**
 * Created by graemerocher on 15/06/2017.
 */
class ConfigurationEvaluatorSpec extends Specification {

    void "test evaluate config simple nested assignment"() {
        when:
        def result = new ConfigurationEvaluator().evaluate('''
foo.bar = 10
foo.bar.baz=20
foo="test"
''')

        then:
        result.get("foo") == "test"
        result.get('foo.bar') == 10
        result.get('foo.bar.baz') == 20
    }

    void "test evaluate config nested closures"() {
        when:
        def result = new ConfigurationEvaluator().evaluate('''
foo {
    bar = 10
    bar.baz=20
    bar {
        stuff = 30
        more.stuff = 40
    }
}
foo = "test"
''')

        then:
        result.get("foo") == "test"
        result.get('foo.bar') == 10
        result.get('foo.bar.baz') == 20
        result.get('foo.bar.stuff') == 30
        result.get('foo.bar.more.stuff') == 40
    }
}
