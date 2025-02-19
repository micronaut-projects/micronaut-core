package io.micronaut.visitors


import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class PostponedVisitorsSpec extends AbstractTypeElementSpec {

    void 'test'() {
        when:
            def definition = buildBeanIntrospection('test.Walrus', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.visitors.Wither;

@Introspected
@Wither
public record Walrus (
    @NonNull
    String name,
    int age,
    byte[] chipInfo
) implements WalrusWither  {
}

''')
        then:
            definition
    }
}
