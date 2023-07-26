package io.micronaut.inject.visitor.beans.builder

import io.micronaut.core.beans.BeanIntrospection
import spock.lang.Specification

class IntrospectedBuilderWithClassNotAnnotatedSpec extends Specification {
    void "you can use Builder with class orginally not annotated with @Introspected"() {
        given:
        Book expected = Book.builder().author("Sam Newman").title("Building Microservices").build()

        and: 'book implements equals and hash code'
        expected == Book.builder().author("Sam Newman").title("Building Microservices").build()

        when:
        BeanIntrospection<Book> introspection = BeanIntrospection.getIntrospection(Book.class)

        then:
        introspection.isBuildable()
        introspection.hasBuilder()

        when:
        BeanIntrospection.Builder<Book> builder = introspection.builder()
        Book result = builder
            .with("author", "Sam Newman")
            .with("title", "Building Microservices")
            .build()

        then:
        expected == result
    }
}
