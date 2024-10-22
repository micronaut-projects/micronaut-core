package io.micronaut.inject.visitor

import com.blazebit.persistence.impl.function.entity.ValuesEntity
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.beans.UnsafeBeanProperty
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.introspections.Person
import spock.lang.Issue
import spock.util.environment.RestoreSystemProperties

import jakarta.validation.constraints.Size

@RestoreSystemProperties
class BeanIntrospectionSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, IntrospectedTypeElementVisitor.name)
    }

    void "test annotations"() {
        when:
            def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.value.OptionalMultiValues;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected
class Test {
    @A1
    private String foo;

    Test(@A5 String foo) {
        this.foo = foo;
    }

    @A2
    public String getFoo() {
        return foo;
    }
    @A4
    public void setFoo(@A3 String foo) {
    }
}

@Target([ElementType.TYPE_USE])
@Documented
@interface A1 {
}

@Target([ElementType.TYPE_USE])
@Documented
@interface A2 {
}

@Target([ElementType.TYPE_USE])
@Documented
@interface A3 {
}

@Target([ElementType.TYPE_USE])
@Documented
@interface A4 {
}

@Target([ElementType.TYPE_USE])
@Documented
@interface A5 {
}

''')
            def property = introspection.getBeanProperties().iterator().next()
            def readProperty = introspection.getBeanReadProperties()[0]
            def writeProperty = introspection.getBeanWriteProperties()[0]
        then:
            property.hasAnnotation("test.A1")
            property.hasAnnotation("test.A2")
            !property.hasAnnotation("test.A3")
            property.hasAnnotation("test.A4")
            !property.hasAnnotation("test.A5")
            property.asArgument().getAnnotationMetadata().hasAnnotation("test.A1")
            property.asArgument().getAnnotationMetadata().hasAnnotation("test.A2")
            !property.asArgument().getAnnotationMetadata().hasAnnotation("test.A3")
            property.asArgument().getAnnotationMetadata().hasAnnotation("test.A4")
            !property.asArgument().getAnnotationMetadata().hasAnnotation("test.A5")
            readProperty.hasAnnotation("test.A1")
            readProperty.hasAnnotation("test.A2")
            !readProperty.hasAnnotation("test.A3")
            readProperty.hasAnnotation("test.A4")
            !readProperty.hasAnnotation("test.A5")
            writeProperty.hasAnnotation("test.A1")
            writeProperty.hasAnnotation("test.A2")
            !writeProperty.hasAnnotation("test.A3")
            writeProperty.hasAnnotation("test.A4")
            !writeProperty.hasAnnotation("test.A5")
    }

    void "test TYPE_USE annotations"() {
        when:
            def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.value.OptionalMultiValues;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected
class Test {
    @A1
    private String foo;

    Test(@A4 String foo) {
        this.foo = foo;
    }

    @A2
    public String getFoo() {
        return foo;
    }

    public void setFoo(@A3 String foo) {
    }
}

@Target([ElementType.TYPE_USE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A1 {
}

@Target([ElementType.TYPE_USE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A2 {
}

@Target([ElementType.TYPE_USE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A3 {
}

@Target([ElementType.TYPE_USE])
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A4 {
}

''')
            def property = introspection.getBeanProperties()[0]
            def readProperty = introspection.getBeanReadProperties()[0]
            def writeProperty = introspection.getBeanWriteProperties()[0]
        then:
            property.hasAnnotation("test.A1")
            property.hasAnnotation("test.A2")
            !property.hasAnnotation("test.A3")
            !property.hasAnnotation("test.A4")
            property.asArgument().getAnnotationMetadata().hasAnnotation("test.A1")
            property.asArgument().getAnnotationMetadata().hasAnnotation("test.A2")
            !property.asArgument().getAnnotationMetadata().hasAnnotation("test.A3")
            !property.asArgument().getAnnotationMetadata().hasAnnotation("test.A4")
            readProperty.hasAnnotation("test.A1")
            readProperty.hasAnnotation("test.A2")
            !readProperty.hasAnnotation("test.A3")
            !readProperty.hasAnnotation("test.A4")
            writeProperty.hasAnnotation("test.A1")
            writeProperty.hasAnnotation("test.A2")
            !writeProperty.hasAnnotation("test.A3")
            !writeProperty.hasAnnotation("test.A4")
    }

    void 'test favor method access'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD])
class Test {
    public String one
    public boolean invoked = false
    public String getOne() {
        invoked = true
        one
    }
}
''');
        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()

        then:
        properties.size() == 2

        when:'a primitive is changed with copy constructor'
        def one = introspection.getRequiredProperty("one", String)
        instance.one = 'test'


        then:'the new value is reflected'
        one.get(instance) == 'test'
        instance.invoked

        when:
        instance.invoked = false
        then:'unsafe access is working'
        (one as UnsafeBeanProperty).getUnsafe(instance) == 'test'
        instance.invoked
    }

    void 'test favor method access with custom getter'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD])
@AccessorsStyle(readPrefixes = "read")
class Test {
    public String one
    public boolean invoked = false
    String readOne() {
        invoked = true
        one
    }
}
''')

        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()

        then:
        properties.size() == 2

        when:'a primitive is changed with copy constructor'
        def one = introspection.getRequiredProperty("one", String)
        instance.one = 'test'

        then:'the new value is reflected'
        one.get(instance) == 'test'
        instance.invoked

        when:
        instance.invoked = false
        then:'unsafe access is working'
        (one as UnsafeBeanProperty).getUnsafe(instance) == 'test'
        instance.invoked
    }

    void 'test use getter to read and field to write'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD])
class Test {
    public String one
    public boolean invoked = false
    public String getOne() {
        invoked = true
        one
    }
}
''');
        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()
        then:
        properties.size() == 2


        when:'a primitive is changed with copy constructor'
        def one = introspection.getRequiredProperty("one", String)
        one.set(instance, "test")


        then:'the new value is reflected'
        one.get(instance) == 'test'
        instance.invoked

        and:'unsafe access is working'
        (one as UnsafeBeanProperty).getUnsafe(instance) == 'test'

        when:
        (one as UnsafeBeanProperty).setUnsafe(instance, "test2")
        then:
        (one as UnsafeBeanProperty).getUnsafe(instance) == 'test2'
    }

    void 'test field access only'() {
        given:
        // Using a real class to prevent classloader permissions issue
        def introspection = BeanIntrospection.getIntrospection(TestFieldAccess)
        when:
        def properties = introspection.getBeanProperties()

        then:
        properties.size() == 4

        def one = introspection.getRequiredProperty("one", String)
        one.isReadWrite()

        def two = introspection.getRequiredProperty("two", int.class)
        two.isReadOnly()

        def three = introspection.getRequiredProperty("three", String)
        three.isReadWrite()

        def four = introspection.getRequiredProperty("four", String)
        four.isReadWrite()

        when:'a field is set'
        def instance = introspection.instantiate(10)
        one.set(instance, "test")

        then:'the value is set'
        one.get(instance) == 'test'

        when:'a primitive is changed with copy constructor'
        instance = two.withValue(instance, 20)

        then:'the new value is reflected'
        two.get(instance) == 20

        when:
        four.set(instance, "test")

        then:
        four.get(instance) == "test"

        when:
        instance = four.withValue(instance, "test2")

        then:
        four.get(instance) == "test2"
    }

    // @PackageScope is commented out because type element visitors are run before it
    // is processed because they visitors and the package scope transformation run in
    // the same phase and there is no way to set the order
    void "test copy constructor via mutate method"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.CopyMe','''\
package test

import java.net.URL

@io.micronaut.core.annotation.Introspected
class CopyMe {

    //@groovy.transform.PackageScope
    URL url
    //@groovy.transform.PackageScope
    boolean enabled = false
    private final String name
    private final String another

    CopyMe(String name, String another) {
        this.name = name;
        this.another = another;
    }

    //@groovy.transform.PackageScope
    String getName() {
        return name
    }

    //@groovy.transform.PackageScope
    String getAnother() {
        return another
    }

    CopyMe withAnother(String a) {
        return this.another.is(a) ? this : new CopyMe(this.name, a.toUpperCase())
    }
}
''')
        when:
        def copyMe = introspection.instantiate("Test", "Another")
        def expectUrl = new URL("http://test.com")
        copyMe.url = expectUrl

        then:
        copyMe.name == 'Test'
        copyMe.another == "Another"
        copyMe.url == expectUrl

        when:
        def enabled = introspection.getRequiredProperty("enabled", boolean.class)
        def urlProperty = introspection.getRequiredProperty("url", URL)
        def property = introspection.getRequiredProperty("name", String)
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = property.withValue(copyMe, "Changed")

        then:
        !newInstance.is(copyMe)
        enabled.get(newInstance) == false
        newInstance.name == 'Changed'
        newInstance.url == expectUrl
        newInstance.another == "Another"

        when:"the instance is changed with the same value"
        def result = property.withValue(newInstance, "Changed")

        then:"The existing instance is returned"
        newInstance.is(result)

        when:"An explicit with method is used"
        result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'

        when:"a mutable property is used"
        def anotherUrl = new URL("http://another.com")
        urlProperty.withValue(result, anotherUrl)
        enabled.withValue(result, true)
        then:"it is correct"
        result.url == anotherUrl
        result.enabled == true

        when:'unsafe withValue is used'
        def anotherUrl2 = new URL("http://another123.com")
        then:'unsafe access is working'
        def newBean = (urlProperty as UnsafeBeanProperty).withValueUnsafe(result, anotherUrl2)
        result.url == anotherUrl2
        newBean.url == anotherUrl2
    }

    // @PackageScope is commented out because type element visitors are run before it
    // is processed because the visitors and the package scope transformation run in
    // the same phase and there is no way to set the order
    void "test copy constructor via mutate method with custom getters"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.CopyMe','''\
package test

import java.net.URL
import io.micronaut.core.annotation.*

@Introspected
@AccessorsStyle(readPrefixes = "read")
class CopyMe {

    //@groovy.transform.PackageScope
    URL url
    //@groovy.transform.PackageScope
    boolean enabled = false
    private final String name
    private final String another

    CopyMe(String name, String another) {
        this.name = name;
        this.another = another;
    }

    //@groovy.transform.PackageScope
    String readName() {
        return name
    }

    //@groovy.transform.PackageScope
    String readAnother() {
        return another
    }

    CopyMe withAnother(String a) {
        return this.another.is(a) ? this : new CopyMe(this.name, a.toUpperCase())
    }
}
''')
        when:
        def copyMe = introspection.instantiate("Test", "Another")
        def expectUrl = new URL("http://test.com")
        copyMe.url = expectUrl

        then:
        copyMe.name == 'Test'
        copyMe.another == "Another"
        copyMe.url == expectUrl

        when:
        def enabled = introspection.getRequiredProperty("enabled", boolean.class)
        def urlProperty = introspection.getRequiredProperty("url", URL)
        def property = introspection.getRequiredProperty("name", String)
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = property.withValue(copyMe, "Changed")

        then:
        !newInstance.is(copyMe)
        enabled.get(newInstance) == false
        newInstance.name == 'Changed'
        newInstance.url == expectUrl
        newInstance.another == "Another"

        when:"the instance is changed with the same value"
        def result = property.withValue(newInstance, "Changed")

        then:"The existing instance is returned"
        newInstance.is(result)

        when:"An explicit with method is used"
        result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'

        when:"a mutable property is used"
        def anotherUrl = new URL("http://another.com")
        urlProperty.withValue(result, anotherUrl)
        enabled.withValue(result, true)
        then:"it is correct"
        result.url == anotherUrl
        result.enabled == true
    }

    void "test generate bean method for introspected class"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.MethodTest', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;

@Introspected
public class MethodTest extends SuperType implements SomeInt {
    public boolean nonAnnotated() {
        return true;
    }

    @Executable
    public String invokeMe(String str) {
        return str;
    }

    @Executable
    int invokePrim(int i) {
        return i;
    }
}

class SuperType {
    @Executable
    String superMethod(String str) {
        return str;
    }

    @Executable
    public String invokeMe(String str) {
        return str;
    }
}

interface SomeInt {
    @Executable
    default boolean ok() {
        return true;
    }

    default String getName() {
        return "ok";
    }
}
''')
        when:
        Collection<BeanMethod> beanMethods = introspection.getBeanMethods()

        then:
        // bizarrely Groovy doesn't support resolving default interface methods
        beanMethods*.name as Set == ['invokeMe', 'invokePrim', 'superMethod', 'ok'] as Set
        beanMethods.every({it.annotationMetadata.hasAnnotation(Executable)})
        beanMethods.every { it.declaringBean == introspection}

        when:
        def invokeMe = beanMethods.find { it.name == 'invokeMe' }
        def invokePrim = beanMethods.find { it.name == 'invokePrim' }
        def itfeMethod = beanMethods.find { it.name == 'ok' }
        def bean = introspection.instantiate()

        then:
        invokeMe.invoke(bean, "test") == 'test'
        invokePrim.invoke(bean, 10) == 10
        itfeMethod.invoke(bean) == true
    }

    void "test generate bean introspection for interface"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;

@io.micronaut.core.annotation.Introspected
interface Test extends io.micronaut.core.naming.Named {
    void setName(String name);
}
''')
        then:
        introspection != null
        introspection.propertyNames.length == 1
        introspection.propertyNames[0] == 'name'

        when:
        introspection.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'

        when:
        def property = introspection.getRequiredProperty("name", String)
        String setNameValue
        def named = [getName:{-> "test"}, setName:{String n -> setNameValue= n }].asType(introspection.beanType)

        property.set(named, "test")

        then:
        property.get(named) == 'test'
        (property as UnsafeBeanProperty).getUnsafe(named) == 'test'
        setNameValue == 'test'

        when:
        (property as UnsafeBeanProperty).setUnsafe(named, "test2")

        then:
        setNameValue == 'test2'
    }

    void "test generate bean introspection for interface with custom setter"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;

import io.micronaut.core.annotation.*

@Introspected
@AccessorsStyle(writePrefixes = "with")
interface Test extends io.micronaut.core.naming.Named {
    void withName(String name)
}
''')
        then:
        introspection != null
        introspection.propertyNames.length == 1
        introspection.propertyNames[0] == 'name'

        when:
        introspection.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'

        when:
        def property = introspection.getRequiredProperty("name", String)
        String setNameValue
        def named = [getName:{-> "test"}, withName:{String n -> setNameValue= n }].asType(introspection.beanType)
        property.set(named, "test")

        then:
        property.get(named) == 'test'
        (property as UnsafeBeanProperty).getUnsafe(named) == 'test'
        setNameValue == 'test'
    }

    void "test multiple constructors with @JsonCreator"() {
        given:
        ClassLoader classLoader = buildClassLoader('''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import com.fasterxml.jackson.annotation.*;

@Introspected
class Test {
    private String name;
    private int age;

    @JsonCreator
    Test(@JsonProperty("name") String name) {
        this.name = name;
    }

    Test(int age) {
        this.age = age;
    }

    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return this.name;
    }
    public Test setName(String n) {
        this.name = n;
        return this;
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 2

        when:
        def test = introspection.instantiate("Fred")
        def prop = introspection.getRequiredProperty("name", String)

        then:
        prop.get(test) == 'Fred'

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test multiple constructors with @JsonCreator with custom getters and setters"() {
        given:
        ClassLoader classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.*
import com.fasterxml.jackson.annotation.*

@Introspected
@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
class Test {
    private String name
    private int age

    @JsonCreator
    Test(@JsonProperty("name") String name) {
        this.name = name
    }

    Test(int age) {
        this.age = age
    }

    int readAge() {
        return age
    }
    void withAge(int age) {
        this.age = age
    }

    String readName() {
        return this.name
    }
    Test withName(String n) {
        this.name = n
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 2

        when:
        def test = introspection.instantiate("Fred")
        def prop = introspection.getRequiredProperty("name", String)

        then:
        prop.get(test) == 'Fred'

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test write bean introspection with builder style properties"() {
        given:
        ClassLoader classLoader = buildClassLoader( '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;

@Introspected
class Test {
    private String name;
    public String getName() {
        return this.name;
    }
    public Test setName(String n) {
        this.name = n;
        return this;
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("name", String)
        prop.set(test, "Foo")

        then:
        prop.get(test) == 'Foo'

    }

    void "test write bean introspection with builder style properties with custom getter and setter"() {
        given:
        ClassLoader classLoader = buildClassLoader( '''
package test

import io.micronaut.core.annotation.*

@Introspected
@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
class Test {
    private String name
    String readName() {
        return this.name
    }
    Test withName(String n) {
        this.name = n
        return this
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("name", String)
        prop.set(test, "Foo")

        then:
        prop.get(test) == 'Foo'
        (prop as UnsafeBeanProperty).getUnsafe(test) == 'Foo'
    }

    void "test write bean introspection with inner classes"() {
        given:
        def classLoader = buildClassLoader( '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;

@Introspected
class Test {
    private Status status;

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return this.status;
    }

    public enum Status {
        UP, DOWN
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1
    }

    void "test write bean introspection with inner classes with custom getter"() {
        given:
        def classLoader = buildClassLoader( '''
package test

import io.micronaut.core.annotation.*

@Introspected
@AccessorsStyle(readPrefixes = "read")
class Test {
    private Status status

    Status readStatus() {
        return this.status
    }

    void setStatus(Status status) {
        this.status = status
    }

    enum Status {
        UP, DOWN
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1
    }

    void "test bean introspection with constructor"() {
        given:
        ClassLoader classLoader = buildClassLoader( '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import javax.persistence.*;
import java.util.*;

@Entity
class Test {
    @Id
    @GeneratedValue
    private Long id;
    @Version
    private Long version;
    private String name;
    @Size(max=100)
    private int age;
    private int[] primitiveArray;

    public Test(String name, int age, int[] primitiveArray) {
        this.name = name;
        this.age = age;
    }
    public String getName() {
        return this.name;
    }
    public void setName(String n) {
        this.name = n;
    }
    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return this.id;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getVersion() {
        return this.version;
    }
}
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null

        when:"The introspection is loaded"
        BeanIntrospection bi = reference.load()

        then:"it is correct"
        bi.getConstructorArguments().length == 3
        bi.getConstructorArguments()[0].name == 'name'
        bi.getConstructorArguments()[0].type == String


        when:
        def object = bi.instantiate("test", 10, [20] as int[])

        then:
        object.name == 'test'
        object.age == 10


    }

    void "test write bean introspection data"() {
        given:
        ClassLoader classLoader = buildClassLoader('''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.core.convert.TypeConverter;

@Introspected
class Test extends ParentBean {
    private String readOnly;
    private String name;
    @Size(max=100)
    private int age;

    private List<Number> list;
    private String[] stringArray;
    private int[] primitiveArray;
    private boolean flag;
    private TypeConverter<String, Collection> genericsTest;

    public TypeConverter<String, Collection> getGenericsTest() {
        return genericsTest;
    }

    public String getReadOnly() {
        return readOnly;
    }
    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public String getName() {
        return this.name;
    }
    public void setName(String n) {
        this.name = n;
    }
    public int getAge() {
        return age;
    }
    public void setAge(int age) {
        this.age = age;
    }

    public List<Number> getList() {
        return this.list;
    }

    public void setList(List<Number> l) {
        this.list = l;
    }

    public int[] getPrimitiveArray() {
        return this.primitiveArray;
    }

    public void setPrimitiveArray(int[] a) {
        this.primitiveArray = a;
    }

    public String[] getStringArray() {
        return this.stringArray;
    }

    public void setStringArray(String[] s) {
        this.stringArray = s;
    }
}

class ParentBean {
    private List<byte[]> listOfBytes;

    public List<byte[]> getListOfBytes() {
        return this.listOfBytes;
    }

    public void setListOfBytes(List<byte[]> list) {
        this.listOfBytes = list;
    }
}
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.instantiate().getClass().name == 'test.Test'
        introspection.getBeanProperties().size() == 9
        introspection.getProperty("name").isPresent()
        introspection.getProperty("name", String).isPresent()
        !introspection.getProperty("name", Integer).isPresent()

        when:
        BeanProperty nameProp = introspection.getProperty("name", String).get()
        BeanProperty boolProp = introspection.getProperty("flag", boolean.class).get()
        BeanProperty ageProp = introspection.getProperty("age", int.class).get()
        BeanProperty listProp = introspection.getProperty("list").get()
        BeanProperty primitiveArrayProp = introspection.getProperty("primitiveArray").get()
        BeanProperty stringArrayProp = introspection.getProperty("stringArray").get()
        BeanProperty listOfBytes = introspection.getProperty("listOfBytes").get()
        def readOnlyProp = introspection.getProperty("readOnly", String).get()
        def instance = introspection.instantiate()

        then:
        readOnlyProp.isReadOnly()
        nameProp != null
        !nameProp.isReadOnly()
        !nameProp.isWriteOnly()
        nameProp.isReadWrite()
        boolProp.get(instance) == false
        nameProp.get(instance) == null
        ageProp.get(instance) == 0
        stringArrayProp.get(instance) == null
        primitiveArrayProp.get(instance) == null
        ageProp.hasAnnotation(Size)
        listOfBytes.asArgument().getFirstTypeVariable().get().type == byte[].class
        listProp.asArgument().getFirstTypeVariable().isPresent()
        listProp.asArgument().getFirstTypeVariable().get().type == Number

        when:
        boolProp.set(instance, true)
        nameProp.set(instance, "foo")
        ageProp.set(instance, 10)
        primitiveArrayProp.set(instance, [10] as int[])
        stringArrayProp.set(instance, ['foo'] as String[])


        then:
        boolProp.get(instance) == true
        nameProp.get(instance) == 'foo'
        ageProp.get(instance) == 10
        stringArrayProp.get(instance) == ['foo'] as String[]
        primitiveArrayProp.get(instance) == [10] as int[]

        when:
        (nameProp as UnsafeBeanProperty).setUnsafe(instance, 'bar')
        then:
        nameProp.get(instance) == 'bar'
        (nameProp as UnsafeBeanProperty).getUnsafe(instance) == 'bar'

        when:
        introspection.instantiate("blah") // illegal argument

        then:
        def e = thrown(InstantiationException)
        e.message == 'Argument count [1] doesn\'t match required argument count: 0'

    }

    void "test write bean introspection data with custom getters"() {
        given:
        ClassLoader classLoader = buildClassLoader('''
package test

import io.micronaut.core.annotation.*
import jakarta.validation.constraints.*
import io.micronaut.core.convert.TypeConverter

@Introspected
class Test extends ParentBean {
    private String readOnly
    private String name
    @Size(max=100)
    private int age

    private List<Number> list
    private String[] stringArray
    private int[] primitiveArray
    private boolean flag
    private TypeConverter<String, Collection> genericsTest

    TypeConverter<String, Collection> readGenericsTest() {
        return genericsTest
    }

    String readReadOnly() {
        return readOnly
    }

    boolean readFlag() {
        return flag
    }
    void setFlag(boolean flag) {
        this.flag = flag
    }

    String readName() {
        return this.name
    }
    void setName(String n) {
        this.name = n
    }

    int readAge() {
        return age
    }
    void setAge(int age) {
        this.age = age
    }

    List<Number> readList() {
        return this.list
    }
    void setList(List<Number> l) {
        this.list = l
    }

    int[] readPrimitiveArray() {
        return this.primitiveArray
    }
    void setPrimitiveArray(int[] a) {
        this.primitiveArray = a
    }

    String[] readStringArray() {
        return this.stringArray
    }
    void setStringArray(String[] s) {
        this.stringArray = s
    }
}

@AccessorsStyle(readPrefixes = "read")
class ParentBean {
    private List<byte[]> listOfBytes

    List<byte[]> readListOfBytes() {
        return this.listOfBytes
    }

    void setListOfBytes(List<byte[]> list) {
        this.listOfBytes = list
    }
}
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.instantiate().getClass().name == 'test.Test'
        introspection.getBeanProperties().size() == 9
        introspection.getProperty("name").isPresent()
        introspection.getProperty("name", String).isPresent()
        !introspection.getProperty("name", Integer).isPresent()

        when:
        BeanProperty nameProp = introspection.getProperty("name", String).get()
        BeanProperty boolProp = introspection.getProperty("flag", boolean.class).get()
        BeanProperty ageProp = introspection.getProperty("age", int.class).get()
        BeanProperty listProp = introspection.getProperty("list").get()
        BeanProperty primitiveArrayProp = introspection.getProperty("primitiveArray").get()
        BeanProperty stringArrayProp = introspection.getProperty("stringArray").get()
        BeanProperty listOfBytes = introspection.getProperty("listOfBytes").get()
        def readOnlyProp = introspection.getProperty("readOnly", String).get()
        def instance = introspection.instantiate()

        then:
        readOnlyProp.isReadOnly()
        nameProp != null
        !nameProp.isReadOnly()
        !nameProp.isWriteOnly()
        nameProp.isReadWrite()
        boolProp.get(instance) == false
        nameProp.get(instance) == null
        ageProp.get(instance) == 0
        stringArrayProp.get(instance) == null
        primitiveArrayProp.get(instance) == null
        ageProp.hasAnnotation(Size)
        listOfBytes.asArgument().getFirstTypeVariable().get().type == byte[].class
        listProp.asArgument().getFirstTypeVariable().isPresent()
        listProp.asArgument().getFirstTypeVariable().get().type == Number

        when:
        boolProp.set(instance, true)
        nameProp.set(instance, "foo")
        ageProp.set(instance, 10)
        primitiveArrayProp.set(instance, [10] as int[])
        stringArrayProp.set(instance, ['foo'] as String[])


        then:
        boolProp.get(instance) == true
        nameProp.get(instance) == 'foo'
        ageProp.get(instance) == 10
        stringArrayProp.get(instance) == ['foo'] as String[]
        primitiveArrayProp.get(instance) == [10] as int[]

        when:
        introspection.instantiate("blah") // illegal argument

        then:
        def e = thrown(InstantiationException)
        e.message == 'Argument count [1] doesn\'t match required argument count: 0'

    }

    void "test final property"() {
        given:
        ClassLoader classLoader = buildClassLoader('''
package test;

import io.micronaut.core.annotation.*;

@Introspected
class Test {

    final String name
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        BeanIntrospection introspection = reference.load()
        def test = classLoader.loadClass('test.Test').newInstance()

        then:
        introspection.getRequiredProperty("name", String).isReadOnly()

        when:
        introspection.getRequiredProperty("name", String).set(test, "test")

        then:
        thrown(UnsupportedOperationException)
    }

    void "test static creator"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test {
    private String name

    private Test(String name) {
        this.name = name
    }

    @Creator
    static Test forName(String name) {
        new Test(name)
    }

    String getName() {
        name
    }
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Sally")

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "Sally"

        when:
        introspection.instantiate(new Object[0])

        then:
        thrown(InstantiationException)

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test static creator with no args"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test {
    private String name

    private Test(String name) {
        this.name = name
    }

    @Creator
    static Test forName() {
        new Test("default")
    }

    String getName() {
        name
    }
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Sally")

        then:
        thrown(InstantiationException)

        when:
        instance = introspection.instantiate(new Object[0])

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"

        when:
        instance = introspection.instantiate()

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"
    }

    void "test static creator multiple"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test {

    private String name

    private Test(String name) {
        this.name = name
    }

    @Creator
    static Test forName() {
        new Test("default")
    }

    @Creator
    static Test forName(String name) {
        new Test(name)
    }

    String getName() {
        name
    }
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Sally")

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "Sally"

        when:
        instance = introspection.instantiate(new Object[0])

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"

        when:
        instance = introspection.instantiate()

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"
    }

    void "test introspections are not created for super classes"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
class Test extends Foo {

}

class Foo {

}
''')

        expect:
        introspection != null

        when:
        introspection.getClass().getClassLoader().loadClass("test.\$Foo\$Introspection")

        then:
        thrown(ClassNotFoundException)
    }

    void "test instantiating an enum"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
enum Test {
    A, B, C
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("A")

        then:
        instance.name() == "A"

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test enum bean properties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
enum Test {

    A(0), B(1), C(2)

    private final int number

    Test(int number) {
        this.number = number
    }

    int getNumber() {
        number
    }
}
''')

        expect:
        introspection != null
        introspection.beanProperties.size() == 1
        introspection.getProperty("number").isPresent()

        when:
        def instance = introspection.instantiate("A")

        then:
        instance.name() == "A"
        introspection.getRequiredProperty("number", int).get(instance) == 0

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)

        when:
        introspection.getClass().getClassLoader().loadClass("java.lang.\$Enum\$Introspection")

        then:
        thrown(ClassNotFoundException)
    }

    void "test enum bean properties with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
@AccessorsStyle(readPrefixes = "read")
enum Test {

    A(0), B(1), C(2)

    private final int number

    Test(int number) {
        this.number = number
    }

    int readNumber() {
        number
    }
}
''')

        expect:
        introspection != null
        introspection.beanProperties.size() == 1
        introspection.getProperty("number").isPresent()

        when:
        def instance = introspection.instantiate("A")

        then:
        instance.name() == "A"
        introspection.getRequiredProperty("number", int).get(instance) == 0

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)

        when:
        introspection.getClass().getClassLoader().loadClass("java.lang.\$Enum\$Introspection")

        then:
        thrown(ClassNotFoundException)
    }

    void "test introspection class member configuration works"() {
        when:
        BeanIntrospection introspection = BeanIntrospection.getIntrospection(Person)

        then:
        noExceptionThrown()
        introspection != null
        introspection.getProperty("name", String).get().get(new Person(name: "Sally")) == "Sally"
    }

    void "test introspection class member configuration works 2"() {
        when:
        BeanIntrospection introspection = BeanIntrospection.getIntrospection(ValuesEntity)

        then:
        noExceptionThrown()
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
class ValidatedConfig {

    private URL url
    private String name

    @NotNull
    URL getUrl() {
        url
    }

    void setUrl(URL url) {
        this.url = url
    }

    @NotBlank
    String getName() {
        name
    }

    void setName(String name) {
        this.name = name
    }
}


''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters with custom getters and setters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test

import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import jakarta.validation.constraints.*

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = "read")
class ValidatedConfig {

    private URL url
    private String name

    @NotNull
    URL readUrl() {
        url
    }

    void setUrl(URL url) {
        this.url = url
    }

    @NotBlank
    String readName() {
        name
    }

    void setName(String name) {
        this.name = name
    }
}
''')

        expect:
        introspection != null
        introspection.getProperty("url")
        introspection.getProperty("name")
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters with inner class"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test

import io.micronaut.context.annotation.ConfigurationProperties

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotBlank
import java.net.URL

@ConfigurationProperties("foo.bar")
class ValidatedConfig {

    @NotNull
    URL url

    static class Inner {

    }

}
''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on fields"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test

import io.micronaut.context.annotation.ConfigurationProperties

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotBlank
import java.net.URL

@ConfigurationProperties("foo.bar")
public class ValidatedConfig {

    @NotNull
    URL url

    @NotBlank
    String name

}
''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.MyConfig','''\
package test

import io.micronaut.context.annotation.*
import java.time.Duration

@ConfigurationProperties("foo.bar")
class MyConfig {

    private String host
    private int serverPort

    @ConfigurationInject
    MyConfig(@jakarta.validation.constraints.NotBlank String host, int serverPort) {
        this.host = host
        this.serverPort = serverPort
    }

    String getHost() {
        host
    }

    int getServerPort() {
        serverPort
    }
}

''')
        expect:
        introspection != null
        introspection.getProperty("host")
        introspection.getProperty("serverPort")
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules with custom getters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.MyConfig', '''\
package test

import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.AccessorsStyle

import java.time.Duration

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = ["read", ""])
class MyConfig {

    private String host
    private int serverPort

    @ConfigurationInject
    MyConfig(@jakarta.validation.constraints.NotBlank String host, int serverPort) {
        this.host = host
        this.serverPort = serverPort
    }

    String readHost() {
        host
    }

    int serverPort() {
        serverPort
    }
}
''')
        expect:
        introspection != null
        introspection.getProperty("host")
        introspection.getProperty("serverPort")
    }

    void "test generate bean introspection for inner @ConfigurationProperties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig$Another','''\
package test

import io.micronaut.context.annotation.ConfigurationProperties

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotBlank
import java.net.URL

@ConfigurationProperties("foo.bar")
class ValidatedConfig {

    @NotNull
    URL url

    public static class Inner {
    }

    @ConfigurationProperties("another")
    static class Another {

        @NotNull
        URL url
    }
}
''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for inner @ConfigurationProperties with custom getters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig$Another', '''\
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.AccessorsStyle

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.NotBlank
import java.net.URL

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = "read")
class ValidatedConfig {

    @NotNull
    private URL url

    URL readUrl() {
        url
    }

    public static class Inner {
    }

    @ConfigurationProperties("another")
    @AccessorsStyle(readPrefixes = "read")
    static class Another {

        @NotNull
        private URL url

        URL readUrl() {
            url
        }
    }
}
''')
        expect:
        introspection != null
        introspection.getProperty("url")
    }

    void "test multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    int[] oneDimension
    int[][] twoDimensions
    int[][][] threeDimensions
}
''')

        then:
        noExceptionThrown()
        introspection != null

        when:
        def instance = introspection.instantiate()
        def property = introspection.getRequiredProperty("oneDimension", int[].class)
        int[] level1 = [1, 2, 3] as int[]
        property.set(instance, level1)

        then:
        property.get(instance) == level1

        when:
        property = introspection.getRequiredProperty("twoDimensions", int[][].class)
        int[] level2 = [4, 5, 6] as int[]
        int[][] twoDimensions = [level1, level2] as int[][]
        property.set(instance, twoDimensions)

        then:
        property.get(instance) == twoDimensions

        when:
        property = introspection.getRequiredProperty("threeDimensions", int[][][].class)
        int[][][] threeDimensions = [[level1], [level2]] as int[][][]
        property.set(instance, threeDimensions)

        then:
        property.get(instance) == threeDimensions
    }

    void "test class multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    String[] oneDimension
    String[][] twoDimensions
    String[][][] threeDimensions
}
''')

        then:
        noExceptionThrown()
        introspection != null

        when:
        def instance = introspection.instantiate()
        def property = introspection.getRequiredProperty("oneDimension", String[].class)
        String[] level1 = ["1", "2", "3"] as String[]
        property.set(instance, level1)

        then:
        property.get(instance) == level1

        when:
        property = introspection.getRequiredProperty("twoDimensions", String[][].class)
        String[] level2 = ["4", "5", "6"] as String[]
        String[][] twoDimensions = [level1, level2] as String[][]
        property.set(instance, twoDimensions)

        then:
        property.get(instance) == twoDimensions

        when:
        property = introspection.getRequiredProperty("threeDimensions", String[][][].class)
        String[][][] threeDimensions = [[level1], [level2]] as String[][][]
        property.set(instance, threeDimensions)

        then:
        property.get(instance) == threeDimensions
    }

    void "test enum multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.visitor.SomeEnum

@Introspected
class Test {
    SomeEnum[] oneDimension
    SomeEnum[][] twoDimensions
    SomeEnum[][][] threeDimensions
}
''')

        then:
        noExceptionThrown()
        introspection != null

        when:
        def instance = introspection.instantiate()
        def property = introspection.getRequiredProperty("oneDimension", SomeEnum[].class)
        SomeEnum[] level1 = [SomeEnum.A, SomeEnum.B, SomeEnum.A] as SomeEnum[]
        property.set(instance, level1)

        then:
        property.get(instance) == level1

        when:
        property = introspection.getRequiredProperty("twoDimensions", SomeEnum[][].class)
        SomeEnum[] level2 = [SomeEnum.B, SomeEnum.A, SomeEnum.B] as SomeEnum[]
        SomeEnum[][] twoDimensions = [level1, level2] as SomeEnum[][]
        property.set(instance, twoDimensions)

        then:
        property.get(instance) == twoDimensions

        when:
        property = introspection.getRequiredProperty("threeDimensions", SomeEnum[][][].class)
        SomeEnum[][][] threeDimensions = [[level1], [level2]] as SomeEnum[][][]
        property.set(instance, threeDimensions)

        then:
        property.get(instance) == threeDimensions
    }

    void "test introspection on abstract class"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
abstract class Test {
    String name
    String author
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
        !beanIntrospection.isBuildable()

        when:
        beanIntrospection.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'
    }

    void "test targeting abstract class with @Introspected(classes = "() {
        ClassLoader classLoader = buildClassLoader("""
package test

import io.micronaut.core.annotation.Introspected

@Introspected(classes = [io.micronaut.inject.visitor.TestClass])
class MyConfig {

}
""")

        when:
        BeanIntrospector beanIntrospector = BeanIntrospector.forClassLoader(classLoader)

        then:
        BeanIntrospection beanIntrospection = beanIntrospector.getIntrospection(TestClass)
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test introspection on abstract class with extra getter"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
abstract class Test {
    String name
    String author

    int getAge() {
        0
    }
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 3
    }

    void "test introspection on abstract class with extra and custom getter"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected

@Introspected
@AccessorsStyle(readPrefixes = "read")
abstract class Test {
    String name
    String author

    int readAge() {
        0
    }
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 3
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6756")
    def "covariant property is not read only"() {
        when:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected

@Introspected
class Test implements B {

    private AImpl a

    @Override
    AImpl getA() {
        a
    }

    void setA(AImpl a) {
        this.a = a
    }
}

interface A {}

interface B {
    A getA()
}

class AImpl implements A {
}
''')
        def property = introspection.getProperty("a").get()

        then:
        property.isReadWrite()
    }

    def "property name with a number is not duplicated"() {
        when:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    UUID id
    String s3Name
}
''')

        then:
            introspection.beanProperties.size() == 2
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2059')
    void "test annotation metadata doesn't cause stackoverflow"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;

import io.micronaut.core.annotation.*;

@Introspected
public class Test {
    int num;
    String str;

    @Creator
    public <T extends Enum<T>> Test(int num, String str, Class<T> enumClass) {
        this(num, str + enumClass.getName());
    }

    public <T extends Enum<T>> Test(int num, String str) {
        this.num = num;
        this.str = str;
    }
}


''')
        expect:
            introspection != null
    }

    void "test annotation metadata doesn't cause stackoverflow 2"() {
        def bd = buildBeanDefinition('test.SessionFactoryFactory','''\
package test;

import io.micronaut.aop.interceptors.Mutating

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryDelegatingImpl

@Factory
class SessionFactoryFactory {

    @Mutating("name")
    @Prototype
    SessionFactory sessionFactory() {
        return new SessionFactoryDelegatingImpl(null)
    }
}


''')
        expect:
            bd != null
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/1645")
    void "test recursive generics 2"() {
        given:
            BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;

@io.micronaut.core.annotation.Introspected
class Test<T extends B> {
    private T child;
    public T getChild() {
        return child;
    }
}
class B<T extends Test> {}

''')
        expect:
            introspection != null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1607')
    void "test recursive generics"() {
        given:
            BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;

import io.micronaut.inject.visitor.RecursiveGenerics;

@io.micronaut.core.annotation.Introspected
class Test extends RecursiveGenerics<Test> {

}
''')

        expect:
            introspection != null
    }

    void "test type_use annotations"() {
        given:
            def introspection = buildBeanIntrospection('test.Test', '''
package test;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.*;
import io.micronaut.inject.visitor.*;
@Introspected
class Test {
    @io.micronaut.inject.visitor.TypeUseRuntimeAnn
    private String name;
    @io.micronaut.inject.visitor.TypeUseClassAnn
    private String secondName;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getSecondName() {
        return name;
    }
    public void setSecondName(String secondName) {
        this.secondName = secondName;
    }
}
''')
            def nameField = introspection.getProperty("name").orElse(null)
            def secondNameField = introspection.getProperty("secondName").orElse(null)

        expect:
            nameField
            secondNameField

            nameField.hasStereotype(TypeUseRuntimeAnn)
            nameField.hasStereotype("io.micronaut.inject.visitor.TypeUseRuntimeAnn")
            !secondNameField.hasStereotype(TypeUseClassAnn)
            !secondNameField.hasStereotype("io.micronaut.inject.visitor.TypeUseClassAnn")
    }

    void "test subtypes"() {
        given:
            BeanIntrospection introspection = buildBeanIntrospection('test.Holder', '''
package test;
import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Collections;

@Introspected
class Animal {
}

@Introspected
class Cat extends Animal {
    final public int lives;
    Cat(int lives) {
        this.lives = lives;
    }
}

@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Holder<A extends Animal> {
    public final Animal animalNonGeneric;
    public final List<Animal> animalsNonGeneric;
    public final A animal;
    public final List<A> animals;
    Holder(A animal) {
        this.animal = animal;
        this.animals = Collections.singletonList(animal);
        this.animalNonGeneric = animal;
        this.animalsNonGeneric = Collections.singletonList(animal);
    }
}


        ''')

        expect:
            def animalListArgument = introspection.getProperty("animals").get().asArgument().getTypeParameters()[0]
            animalListArgument instanceof GenericPlaceholder
            animalListArgument.isTypeVariable()

            def animal = introspection.getProperty("animal").get().asArgument()
            animal instanceof GenericPlaceholder
            animal.isTypeVariable()
    }

    void "test package private property introspection"() {
        when:
        def introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.inject.visitor.MySuperclass

@Introspected
class Test extends MySuperclass {

    String name
}
''')
        then: 'the property in this class is introspected'
        introspection.getProperty("name").orElse(null)

        and: 'the public property in the java superclass is introspected'
        introspection.getProperty("publicProperty").orElse(null)

        and: 'the private property in the java superclass is not introspected'
        !introspection.getProperty("privateProperty").orElse(null)

        and: 'the package private superclass property is not introspected'
        !introspection.getProperty("packagePrivateProperty").orElse(null)
    }

    void "test package private property introspection in same package"() {
        when:
        def introspection = buildBeanIntrospection('io.micronaut.inject.visitor.Test', '''
package io.micronaut.inject.visitor

import io.micronaut.core.annotation.Introspected

@Introspected
class Test extends MySuperclass {

    String name
}
''')
        then: 'the property in this class is introspected'
        introspection.getProperty("name").orElse(null)

        and: 'the public property in the java superclass is introspected'
        introspection.getProperty("publicProperty").orElse(null)

        and: 'the private property in the java superclass is not introspected'
        !introspection.getProperty("privateProperty").orElse(null)

        and: 'the package private superclass property is introspected, as we are in the same package'
        introspection.getProperty("packagePrivateProperty").orElse(null)
    }
}
