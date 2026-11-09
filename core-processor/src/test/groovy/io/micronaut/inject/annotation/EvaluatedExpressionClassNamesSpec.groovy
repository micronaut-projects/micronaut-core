package io.micronaut.inject.annotation

import spock.lang.Specification

import java.nio.charset.StandardCharsets

class EvaluatedExpressionClassNamesSpec extends Specification {

    private static final String VALUE = 'io.micronaut.context.annotation.Value'

    void "the name holds the element and the annotation member, the signature only goes into the hash"() {
        when:
        String name = EvaluatedExpressionClassNames.name('test.Foo', 'method$find(java.lang.String,int)$param1', VALUE, 'value', '#{ 1 }')

        then:
        name.startsWith('test.$Foo$Expr$method$find$param1$Value$value$')
        name.substring(name.length() - 16) ==~ /\p{XDigit}{16}/
        name == EvaluatedExpressionClassNames.name('test.Foo', 'method$find(java.lang.String,int)$param1', VALUE, 'value', '#{ 1 }')
        name != EvaluatedExpressionClassNames.name('test.Foo', 'method$find(java.lang.String,long)$param1', VALUE, 'value', '#{ 1 }')
        name != EvaluatedExpressionClassNames.name('test.Foo', 'method$find(java.lang.String,int)$param1', 'other.Value', 'value', '#{ 1 }')
        name != EvaluatedExpressionClassNames.name('test.Foo', 'method$find(java.lang.String,int)$param1', VALUE, 'value', '#{ 2 }')
    }

    void "arrays of expressions that would join to the same text get distinct names"() {
        expect:
        EvaluatedExpressionClassNames.name('test.Foo', 'type', VALUE, 'value', ['#{ 1 }\u0000x', 'y'] as String[]) !=
                EvaluatedExpressionClassNames.name('test.Foo', 'type', VALUE, 'value', ['#{ 1 }', 'x\u0000y'] as String[])
    }

    void "expressions whose hashes of 32 bits collide get distinct names"() {
        expect:
        EvaluatedExpressionClassNames.name('test.Foo', 'type', 'test.My', 'value', '#{ 21278 }') !=
                EvaluatedExpressionClassNames.name('test.Foo', 'type', 'test.My', 'value', '#{ 82388 }')
    }

    void "characters that cannot be in a name are replaced"() {
        expect:
        EvaluatedExpressionClassNames.name('Foo', 'method$my test-1()', VALUE, 'value', '#{ 1 }')
                .startsWith('$Foo$Expr$method$my_test_1$Value$value$')
    }

    void "a name is cut short to fit a file name, however long the class or the element"() {
        when:
        String name = EvaluatedExpressionClassNames.name(className, element, VALUE, 'value', '#{ 1 }')
        String simpleName = name.substring(name.lastIndexOf('.') + 1)

        then:
        simpleName.getBytes(StandardCharsets.UTF_8).length <= EvaluatedExpressionClassNames.MAX_SIMPLE_NAME_BYTES
        simpleName.contains('$Expr$')
        name != EvaluatedExpressionClassNames.name(className, element + 'z', VALUE, 'value', '#{ 1 }')

        where:
        className               | element
        'test.Foo'              | 'field$' + 'x' * 300
        'test.Foo'              | 'field$' + '名' * 100
        'test.' + 'F' * 240     | 'field$x'
        'test.' + '名' * 80 | 'field$x'
    }
}
