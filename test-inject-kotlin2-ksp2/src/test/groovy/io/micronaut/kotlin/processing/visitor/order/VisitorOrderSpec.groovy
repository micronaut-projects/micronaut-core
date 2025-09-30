package io.micronaut.kotlin.processing.visitor.order

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec

class VisitorOrderSpec extends AbstractKotlinCompilerSpec {

    void 'test annotating'() {
        when:
            buildBeanDefinition('vis.MyBean1', '''
package vis

import io.micronaut.kotlin.processing.visitor.order.VisitMyAnnotation

@VisitMyAnnotation
class MyBean1

@VisitMyAnnotation
class MyBean2

@VisitMyAnnotation
class MyBean3

''')
        then:
            MyVisitor1.ORDER == ["MyVisitor3 vis.MyBean1", "MyVisitor3 vis.MyBean2", "MyVisitor3 vis.MyBean3",
                                 "MyVisitor2 vis.MyBean1", "MyVisitor2 vis.MyBean2", "MyVisitor2 vis.MyBean3",
                                 "MyVisitor1 vis.MyBean1", "MyVisitor1 vis.MyBean2", "MyVisitor1 vis.MyBean3"]
    }

}
