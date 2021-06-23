package io.micronaut.inject.annotation

import io.micronaut.aop.Intercepted
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

class AnnotationInheritanceSpec extends AbstractBeanDefinitionSpec {
    void "test inherited AOP advice on types is not inherited when not annotated with @Inherited"() {
        given:
        def context = buildContext('anntest.Test', '''
package anntest;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Prototype
class Test extends Parent {}

@Singleton
@Named("test")
@Requires(property="foo.bar")
@Mutating("test")
class Parent {
}

@io.micronaut.aop.Around
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Mutating {
    String value();
}
''')
        def bean = getBean(context, 'anntest.Test')

        expect:"No AOP advice is applied because @Mutating is not annotated with @Inherited"
        !(bean instanceof Intercepted)

        cleanup:
        context.close()
    }

    void "test inherited AOP advice on methods are not inherited when not annotated with @Inherited"() {
        given:
        def context = buildContext('anntest.Test', '''
package anntest;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Prototype
class Test extends Parent {
    @Override
    void test() {}
}

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Parent {

    @Mutating("test")
    void test() {
    
    }
}

@io.micronaut.aop.Around
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Mutating {
    String value();
}
''')
        def bean = getBean(context, 'anntest.Test')

        expect:"No AOP advice is applied because @Mutating is not annotated with @Inherited"
        !(bean instanceof Intercepted)

        cleanup:
        context.close()
    }

    void "test inherited AOP advice on methods are inherited when annotated with @Inherited"() {
        given:
        def context = buildContext('anntest.Test', '''
package anntest;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Prototype
class Test extends Parent {
    @Override
    void test() {}
}

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Parent {

    @Mutating("test")
    void test() {
    
    }
}
@io.micronaut.aop.Around
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@interface Mutating {
    String value();
}
''')
        def bean = getBean(context, 'anntest.Test')

        expect:"AOP advice is applied since it is inherited through the method"
        (bean instanceof Intercepted)

        cleanup:
        context.close()
    }

    void "test inherited AOP advice on types on bean - inherited"() {
        given:
        def context = buildContext('anntest.Test', '''
package anntest;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;

@Prototype
class Test extends Parent {}

@Singleton
@Named("test")
@Requires(property="foo.bar")
@Mutating("test")
class Parent {
}

@io.micronaut.aop.Around
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@interface Mutating {
    String value();
}
''')
        def bean = getBean(context, 'anntest.Test')

        expect:"AOP advice is applied because the @Mutated annotation is annotated with @Inherited"
        (bean instanceof Intercepted)

        cleanup:
        context.close()
    }

    void "test declared scopes, qualifiers & requirements on types"() {
        given:
        def definition = buildBeanDefinition('anntest1.Test', '''
package anntest1;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Test {
}
''')
        expect:
        definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
        definition.scopeName.isPresent()
        definition.scopeName.get() == AnnotationUtil.SINGLETON
        definition.declaredQualifier
        definition.declaredQualifier == Qualifiers.byName("test")
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 1
    }

    void "test declared scopes, qualifiers & requirements on factory methods"() {
        given:
        def classLoader = buildClassLoader('''
package anntest2;

import jakarta.inject.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;

@Factory
@Requires(property="test.bar")
class TestFactory extends ParentFactory {
    @Override
    @Bean
    @Requires(property="test.method.bar")
    Test test() {
        return new Test();
    }
}

class Test {}

@Factory
@Requires(property="parent.bar")
class ParentFactory {
    @Requires(property="parent.method.bar")
    @jakarta.inject.Singleton
    Test test() {
        return new Test();
    }
}

''')
        BeanDefinition definition = classLoader.loadClass('anntest2.$TestFactory$Test0Definition').newInstance()

        expect:"Is a bean"
        definition != null

        and:"has no declared scope since it was overridden in the child method"
        !definition.scopeName.isPresent()

        and:"The requirements include the ones from the child factory and child method but not from the parent factory and parent method"
        def requiresProperties = definition.getDeclaredAnnotationValuesByType(Requires)
                .collect { it.stringValue("property").orElse(null) }
        requiresProperties.size() == 2
        requiresProperties.contains("test.method.bar")
        requiresProperties.contains("test.bar")
    }

    void "test inherited scopes, qualifiers & requirements on types on non-bean"() {
        given:
        def definition = buildBeanDefinition('anntest3.TestAnnotationChild', '''
package anntest3;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;

class TestAnnotationChild extends Parent {}

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Parent {
}
''')
        expect:"No bean since no declared scopes/qualifiers"
        definition == null
    }

    void "test inherited scopes, qualifiers & requirements on types on bean"() {
        given:
        def definition = buildBeanDefinition('anntest4.Test', '''
package anntest4;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;

@Prototype
class Test extends Parent {}

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Parent {
}
''')
        expect:"The type is a bean with a single declared annotation"
        definition.hasDeclaredAnnotation(Prototype)
        definition.declaredAnnotationNames == [Prototype.name] as Set

        and:"has a declared scope"
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)

        and:"But doesn't inherit the qualifier"
        !definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        !definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)

        and:"The scope is correctly resolved"
        definition.scopeName.isPresent()
        definition.scopeName.get() == Prototype.name

        and:"The declared qualifier is not since non is declared"
        definition.declaredQualifier == null

        and:"The requirements are not inherited"
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 0
    }

    void "test inherited scopes, qualifiers & requirements with @Inherited on stereotype"() {
        given:
        def definition = buildBeanDefinition('anntest5.Test', '''
package anntest5;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;


class Test extends Parent {}

@MyAnn
class Parent {
}

@Singleton
@Named("test")
@Requires(property="foo.bar")
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface MyAnn {}
''')
        expect:"inherits the stereotype annotation as a declared annotation"
        definition.hasDeclaredAnnotation('anntest5.MyAnn')

        and:"inherits any scopes as declared stereotypes"
        definition.hasDeclaredStereotype(AnnotationUtil.SINGLETON)
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)

        and:"but not as declared annotations"
        !definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)

        and:"inherits any qualifiers as declared stereotypes"
        definition.hasDeclaredStereotype(AnnotationUtil.NAMED)
        definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)

        and:"but not as declared annotations"
        !definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        !definition.hasDeclaredAnnotation(AnnotationUtil.QUALIFIER)

        and:"The declared qualifier resolves correctly"
        definition.declaredQualifier
        definition.declaredQualifier == Qualifiers.byName("test")

        and:"The declared scope resolves correctly"
        definition.scopeName.isPresent()
        definition.scopeName.get() == AnnotationUtil.SINGLETON

        and:"requirements are inherited from the stereotype as declared requirements"
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 1
    }

    void "test inherited scopes and qualifiers with @Inherited"() {
        given:
        def definition = buildBeanDefinition('anntest6.Test', '''
package anntest6;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;


class Test extends Parent {}

@MyQ
@MyS
@Requires(property="foo.bar")
class Parent {
}

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@jakarta.inject.Qualifier
@interface MyQ {}

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@jakarta.inject.Scope
@interface MyS {}
''')
        expect:"inherits annotations declared @Inherited"
        definition.hasDeclaredAnnotation("anntest6.MyQ")
        definition.hasDeclaredAnnotation("anntest6.MyS")

        and:"inherits stereotypes on @Inherited annotations as declared stereotypes"
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)

        and:"but not as declared annotations"
        !definition.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !definition.hasDeclaredAnnotation(AnnotationUtil.QUALIFIER)

        and:"The declared qualifier resolves to the correct one"
        definition.declaredQualifier
        definition.declaredQualifier.toString() == '@MyQ'

        and:"The declared scope results to the correct one"
        definition.scopeName.isPresent()
        definition.scopeName.get() == "anntest6.MyS"

        and:"Requirements are not inherited "
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 0
    }


    void "test inherited scopes and qualifiers with @Inherited on factory method"() {
        given:
        def classLoader = buildClassLoader('''
package anntest7;

import jakarta.inject.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;

@Factory
class TestFactory extends ParentFactory {
    @Override
    @Bean
    Test test() {
        return new Test();
    }
}

class Test {}

@Factory
class ParentFactory {
    @MyQ
    @MyS
    @Requires(property="foo.bar")
    @Bean
    Test test() {
        return new Test();
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@jakarta.inject.Qualifier
@interface MyQ {}

@Retention(RetentionPolicy.RUNTIME)
@Inherited
@jakarta.inject.Scope
@interface MyS {}
''')
        BeanDefinition definition = classLoader.loadClass('anntest7.$TestFactory$Test0Definition').newInstance()

        expect:"inherits annotations declared @Inherited"
        definition.hasDeclaredAnnotation("anntest7.MyQ")
        definition.hasDeclaredAnnotation("anntest7.MyS")

        and:"inherits stereotypes on @Inherited annotations as declared stereotypes"
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)

        and:"but not as declared annotations"
        !definition.hasDeclaredAnnotation(AnnotationUtil.SCOPE)
        !definition.hasDeclaredAnnotation(AnnotationUtil.QUALIFIER)

        and:"The declared qualifier resolves to the correct one"
        definition.declaredQualifier
        definition.declaredQualifier.toString() == '@MyQ'

        and:"The declared scope results to the correct one"
        definition.scopeName.isPresent()
        definition.scopeName.get() == "anntest7.MyS"

        and:"Requirements are not inherited "
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 0
    }

    void "test inherited scopes, qualifiers & requirements with @Inherited on stereotype for factory method"() {
        given:
        def classLoader = buildClassLoader('''
package anntest8;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;
import java.lang.annotation.*;


import jakarta.inject.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;

@Factory
class TestFactory extends ParentFactory {
    @Override
    @Bean
    Test test() {
        return new Test();
    }
}

class Test {}

@Factory
class ParentFactory {
    @MyAnn
    @Bean
    Test test() {
        return new Test();
    }
}

@Singleton
@Named("test")
@Requires(property="foo.bar")
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@interface MyAnn {}
''')
        BeanDefinition definition = classLoader.loadClass('anntest8.$TestFactory$Test0Definition').newInstance()

        expect:"inherits the stereotype annotation as a declared annotation"
        definition.hasDeclaredAnnotation('anntest8.MyAnn')

        and:"inherits any scopes as declared stereotypes"
        definition.hasDeclaredStereotype(AnnotationUtil.SINGLETON)
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)

        and:"but not as declared annotations"
        !definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)

        and:"inherits any qualifiers as declared stereotypes"
        definition.hasDeclaredStereotype(AnnotationUtil.NAMED)
        definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)

        and:"but not as declared annotations"
        !definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        !definition.hasDeclaredAnnotation(AnnotationUtil.QUALIFIER)

        and:"The declared qualifier resolves correctly"
        definition.declaredQualifier
        definition.declaredQualifier == Qualifiers.byName("test")

        and:"The declared scope resolves correctly"
        definition.scopeName.isPresent()
        definition.scopeName.get() == AnnotationUtil.SINGLETON

        and:"requirements are inherited from the stereotype as declared requirements"
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 1
    }

    void "test inherited scopes, qualifiers & requirements on types on bean from factory method"() {
        given:
        def classLoader = buildClassLoader('''
package anntest9;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;

import jakarta.inject.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;

@Factory
class TestFactory extends ParentFactory {
    @Override
    @Prototype
    Test test() {
        return new Test();
    }
}

class Test {}

@Factory
class ParentFactory {

    @Singleton
    @Named("test")
    @Requires(property="foo.bar")
    Test test() {
        return new Test();
    }
}

''')
        BeanDefinition definition = classLoader.loadClass('anntest9.$TestFactory$Test0Definition').newInstance()

        expect:"The type is a bean with a single declared annotation"
        definition.hasDeclaredAnnotation(Prototype)
        definition.declaredAnnotationNames == [Prototype.name] as Set

        and:"has a declared scope"
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)

        and:"But doesn't inherit the qualifier"
        !definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        !definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)

        and:"The scope is correctly resolved"
        definition.scopeName.isPresent()
        definition.scopeName.get() == Prototype.name

        and:"The declared qualifier is not since non is declared"
        definition.declaredQualifier == null

        and:"The requirements are not inherited"
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 0
    }

    void "test inherited scopes, qualifiers & requirements on types on non-bean from factory method"() {
        given:
        def classLoader = buildClassLoader('''
package anntest10;

import jakarta.inject.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Prototype;

import jakarta.inject.*;
import io.micronaut.context.annotation.*;
import java.lang.annotation.*;

@Factory
class TestFactory extends ParentFactory {
    @Override
    Test test() {
        return new Test();
    }
}

class Test {}

@Factory
class ParentFactory {

    @Singleton
    @Named("test")
    @Requires(property="foo.bar")
    Test test() {
        return new Test();
    }
}

''')
        when:"No bean since no declared scopes/qualifiers"
        classLoader.loadClass('anntest10.$TestFactory$Test0Definition')

        then:"No bean exists"
        thrown(ClassNotFoundException)
    }
}
