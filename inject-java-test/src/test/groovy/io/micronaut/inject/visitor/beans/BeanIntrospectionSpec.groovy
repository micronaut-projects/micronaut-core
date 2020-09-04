package io.micronaut.inject.visitor.beans

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.naming.Named
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.Argument
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import spock.lang.IgnoreIf

//import org.objectweb.asm.ClassReader
//import org.objectweb.asm.util.ASMifier
//import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Issue
import spock.util.environment.Jvm

import javax.annotation.processing.SupportedAnnotationTypes
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Version
import javax.validation.constraints.Size
import java.lang.reflect.Field

class BeanIntrospectionSpec extends AbstractTypeElementSpec {


    void "test bean introspection with property of generic interface"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

@io.micronaut.core.annotation.Introspected
class Foo implements GenBase<String> {
    public String getName() {
        return "test";
    }
}

interface GenBase<T> {
    T getName();
}
''')
        when:
        def test = introspection.instantiate()

        then:
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
    }

    void "test bean introspection with argument of generic interface"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

@io.micronaut.core.annotation.Introspected
class Foo implements GenBase<Long> {

    private Long value;

    public Long getValue() {
        return value;
    }
    
    public void setValue(Long value) {
        this.value = value;
    }
}

interface GenBase<T> {
    T getValue();
    
    void setValue(T t);
}
''')
        when:
        def test = introspection.instantiate()
        BeanProperty bp = introspection.getRequiredProperty("value", Long)
        bp.set(test, 5L)

        then:
        bp.get(test) == 5L
    }

    void "test bean introspection with property with static creator method on interface"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

import io.micronaut.core.annotation.Creator;

@io.micronaut.core.annotation.Introspected
interface Foo {
    String getName();
    
    @Creator
    static Foo create(String name) {
        return () -> name;
    }
}

''')
        when:
        def test = introspection.instantiate("test")

        then:
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
    }

    void "test bean introspection with property from default interface method"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

@io.micronaut.core.annotation.Introspected
class Test implements Foo {

}

interface Foo {
    default String getBar() {
        return "good";
    }
}

''')
        when:
        def test = introspection.instantiate()

        then:
        introspection.getRequiredProperty("bar", String)
                     .get(test) == 'good'
    }

    void "test generate bean introspection for @ConfigurationProperties interface"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
public interface ValidatedConfig {

    @NotNull
    public URL getUrl();

}


''')
        expect:
        introspection != null
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
        setNameValue == 'test'

    }

    void "test compiled introspection for interface"() {
        when:
        def introspection = BeanIntrospection.getIntrospection(TestNamed.class)
        def property = introspection.getRequiredProperty("name", String)
        String setNameValue
        def named = [getName:{-> "test"}, setName:{String n -> setNameValue= n }].asType(introspection.beanType)

        then:
        property.get(named) == 'test'

    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
public class ValidatedConfig {

    private URL url;
    private String name;

    @NotNull
    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    @NotBlank
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters with inner class"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
public class ValidatedConfig {

    private URL url;

    @NotNull
    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }
    
    public static class Inner {
    
    }
    
}
''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for inner @ConfigurationProperties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig$Another','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
class ValidatedConfig {

    private URL url;

    @NotNull
    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }
    
    public static class Inner {
    
    }
    
    @ConfigurationProperties("another")
    static class Another {
    
        private URL url;

        @NotNull
        public URL getUrl() {
            return url;
        }
    
        public void setUrl(URL url) {
            this.url = url;
        }
    }
}
''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on fields"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
public class ValidatedConfig {

    @NotNull
    URL url;

    @NotBlank
    protected String name;

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


''')
        expect:
        introspection != null
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.MyConfig','''\
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    private String host;
    private int serverPort;
    
    @ConfigurationInject
    MyConfig(@javax.validation.constraints.NotBlank String host, int serverPort) {
        this.host = host;
        this.serverPort = serverPort;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getServerPort() {
        return serverPort;
    }
}

''')
        expect:
        introspection != null
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

    public <T extends Enum<T>> Test(int num, String str, Class<T> enumClass) {
        this(num, str + enumClass.getName());
    }
    
    @Creator
    public <T extends Enum<T>> Test(int num, String str) {
        this.num = num;
        this.str = str;
    }
}


''')
        expect:
        introspection != null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2083')
    void "test class references in constructor arguments"() {
        given:
//        TraceClassVisitor traceClassVisitor =
//                new TraceClassVisitor(null, new ASMifier(), new PrintWriter(System.out));
//        new ClassReader('io.micronaut.inject.visitor.beans.TestConstructorIntrospection')
//                .accept(traceClassVisitor, ClassReader.EXPAND_FRAMES)

        BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.BooleanSerializer;

@io.micronaut.core.annotation.Introspected
class Test {
    private String test;
    Test(@JsonSerialize(using=BooleanSerializer.class) String test) {
        this.test = test;
    }
    public String getTest() {
        return test;
    } 
}


''')
        expect:
        introspection != null
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/1645")
    void "test recusive generics 2"() {
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

import io.micronaut.inject.visitor.beans.RecursiveGenerics;

@io.micronaut.core.annotation.Introspected
class Test extends RecursiveGenerics<Test> {

}
''')
        expect:
        introspection != null
    }

    void "test build introspection"() {
        given:
        def context = buildContext('test.Address', '''
package test;

import javax.validation.constraints.*;


@io.micronaut.core.annotation.Introspected
class Address {
    @NotBlank(groups = GroupOne.class)
    @NotBlank(groups = GroupThree.class, message = "different message")
    @Size(min = 5, max = 20, groups = GroupTwo.class)
    private String street;
    
    public String getStreet() {
        return this.street;
    }
}

interface GroupOne {}
interface GroupTwo {}
interface GroupThree {}
''')
        def clazz = context.classLoader.loadClass('test.$Address$IntrospectionRef')
        BeanIntrospectionReference reference = clazz.newInstance()


        expect:
        reference != null
        reference.load()
    }

    void "test multiple constructors with primary constructor marked as @Creator"() {
        given:
        ApplicationContext context = buildContext('test.Book', '''
package test;

@io.micronaut.core.annotation.Introspected
class Book {

    private String title;
    private String author;

    public Book(String title, String author) {
        this.title = title;
        this.author = author;
    }
    
    @io.micronaut.core.annotation.Creator
    public Book(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
    
    void setTitle(String title) {
        this.title = title;
    }

    String getAuthor() {
        return author;
    }

    void setAuthor(String author) {
        this.author = author;
    }
}
''')
        Class clazz = context.classLoader.loadClass('test.$Book$IntrospectionRef')
        BeanIntrospectionReference reference = (BeanIntrospectionReference) clazz.newInstance()

        expect:
        reference != null

        when:
        BeanIntrospection introspection = reference.load()

        then:
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)

        when: "update introspectionMap"
        BeanIntrospector introspector = BeanIntrospector.SHARED
        Field introspectionMapField = introspector.getClass().getDeclaredField("introspectionMap")
        introspectionMapField.setAccessible(true)
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>());
        Map map = (Map) introspectionMapField.get(introspector)
        map.put(reference.getName(), reference)

        and:
        def book = InstantiationUtils.tryInstantiate(introspection.getBeanType(), ["title": "The Stand"], ConversionContext.of(Argument.of(introspection.beanType)))
        def prop = introspection.getRequiredProperty("title", String)

        then:
        prop.get(book.get()) == "The Stand"

        cleanup:
        introspectionMapField.set(introspector, null)
        context?.close()
    }

    void "test default constructor "() {
        given:
        ApplicationContext context = buildContext('test.Book', '''
package test;

@io.micronaut.core.annotation.Introspected
class Book {

    private String title;

    public Book() {
    }   

    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
}
''')
        Class clazz = context.classLoader.loadClass('test.$Book$IntrospectionRef')
        BeanIntrospectionReference reference = (BeanIntrospectionReference) clazz.newInstance()

        expect:
        reference != null

        when:
        BeanIntrospection introspection = reference.load()

        then:
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when: "update introspectionMap"
        BeanIntrospector introspector = BeanIntrospector.SHARED
        Field introspectionMapField = introspector.getClass().getDeclaredField("introspectionMap")
        introspectionMapField.setAccessible(true)
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>());
        Map map = (Map) introspectionMapField.get(introspector)
        map.put(reference.getName(), reference)

        and:
        def book = InstantiationUtils.tryInstantiate(introspection.getBeanType(), ["title": "The Stand"], ConversionContext.of(Argument.of(introspection.beanType)))
        def prop = introspection.getRequiredProperty("title", String)

        then:
        prop.get(book.get()) == null

        cleanup:
        introspectionMapField.set(introspector, null)
        context?.close()
    }

    void "test multiple constructors with @JsonCreator"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
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
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef')
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

        cleanup:
        context?.close()
    }

    void "test write bean introspection with builder style properties"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
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
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef')
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

        cleanup:
        context?.close()
    }


    void "test write bean introspection with inner classes"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
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
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef')
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

        cleanup:
        context?.close()
    }

    void "test bean introspection with constructor"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import javax.persistence.*;
import java.util.*;

@Entity
class Test {
    @Id
    @GeneratedValue
    private Long id;
    @Version
    private Long version;
    @Column(name="test_name")
    private String name;
    @Size(max=100)
    private int age;
    private int[] primitiveArray;
    
    private long v;
    
    public Test(String name, @Size(max=100) int age, int[] primitiveArray) {
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
    
    @Version
    public long getAnotherVersion() {
        return v;
    }
    
    public void setAnotherVersion(long v) {
        this.v = v;
    }
}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null

        when:"The introspection is loaded"
        BeanIntrospection bi = reference.load()

        then:"it is correct"
        bi.getConstructorArguments().length == 3
        bi.getConstructorArguments()[0].name == 'name'
        bi.getConstructorArguments()[0].type == String
        bi.getConstructorArguments()[1].name == 'age'
        bi.getConstructorArguments()[1].getAnnotationMetadata().hasAnnotation(Size)
        bi.getIndexedProperties(Id).size() == 1
        bi.getIndexedProperty(Id).isPresent()
        bi.getIndexedProperty(Column, "test_name").isPresent()
        bi.getIndexedProperty(Column, "test_name").get().name == 'name'
        bi.getProperty("version").get().hasAnnotation(Version)
        bi.getProperty("anotherVersion").get().hasAnnotation(Version)
        // should not inherit metadata from class
        !bi.getProperty("anotherVersion").get().hasAnnotation(Entity)

        when:
        BeanProperty idProp = bi.getIndexedProperties(Id).first()

        then:
        idProp.name == 'id'
        !idProp.hasAnnotation(Entity)
        !idProp.hasStereotype(Entity)


        when:
        def object = bi.instantiate("test", 10, [20] as int[])

        then:
        object.name == 'test'
        object.age == 10


        cleanup:
        context?.close()
    }

    void "test write bean introspection data for entity"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
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
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null

        when:"The introspection is loaded"
        BeanIntrospection bi = reference.load()

        then:"it is correct"
        bi.instantiate()
        bi.getIndexedProperties(Id).size() == 1
        bi.getIndexedProperties(Id).first().name == 'id'

        cleanup:
        context?.close()
    }

    void "test write bean introspection data for class in another package"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(classes=OtherTestBean.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef0')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getBeanType() == OtherTestBean

        when:
        def introspection = reference.load()

        then: "the introspection is under the reference package"
        noExceptionThrown()
        introspection.class.name == "test.\$io_micronaut_inject_visitor_beans_OtherTestBean\$Introspection"
        introspection.instantiate()

        cleanup:
        context?.close()
    }

    void "test write bean introspection data for class already introspected"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(classes=TestBean.class)
class Test {}
''')

        when:"the reference is loaded"
        context.classLoader.loadClass('test.$Test$IntrospectionRef0')

        then:"The reference is not written"
        thrown(ClassNotFoundException)

        cleanup:
        context?.close()
    }

    void "test write bean introspection data for package with sources"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(packages="io.micronaut.inject.visitor.beans", includedAnnotations=MarkerAnnotation.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef0')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is generated"
        reference != null

        cleanup:
        context?.close()
    }

    void "test write bean introspection data for package with compiled classes"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.*;

@Introspected(packages="io.micronaut.inject.beans.visitor", includedAnnotations=Internal.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef0')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getBeanType() == IntrospectedTypeElementVisitor


        cleanup:
        context?.close()
    }

    void "test write bean introspection data"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
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
    private TypeConverter<String, Object[]> genericsArrayTest;
    
    public TypeConverter<String, Collection> getGenericsTest() {
        return genericsTest;
    }
    
    public TypeConverter<String, Object[]> getGenericsArrayTest() {
        return genericsArrayTest;
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
        def clazz = context.classLoader.loadClass('test.$Test$IntrospectionRef')
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
        introspection.getBeanProperties().size() == 10
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
        BeanProperty genericsTest = introspection.getProperty("genericsTest").get()
        BeanProperty genericsArrayTest = introspection.getProperty("genericsArrayTest").get()
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
        genericsTest != null
        genericsTest.type == TypeConverter
        genericsTest.asArgument().typeParameters.size() == 2
        genericsTest.asArgument().typeParameters[0].type == String
        genericsTest.asArgument().typeParameters[1].type == Collection
        genericsTest.asArgument().typeParameters[1].typeParameters.length == 1
        genericsArrayTest.type == TypeConverter
        genericsArrayTest.asArgument().typeParameters.size() == 2
        genericsArrayTest.asArgument().typeParameters[0].type == String
        genericsArrayTest.asArgument().typeParameters[1].type == Object[].class
        stringArrayProp.get(instance) == null
        stringArrayProp.type == String[]
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
        ageProp.convertAndSet(instance, "20")
        nameProp.set(instance, "100" )

        then:
        ageProp.get(instance) == 20
        nameProp.get(instance, Integer, null) == 100

        when:
        introspection.instantiate("blah") // illegal argument

        then:
        def e = thrown(InstantiationException)
        e.message == 'Argument count [1] doesn\'t match required argument count: 0'

        cleanup:
        context?.close()
    }

    void "test constructor argument generics"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import javax.validation.constraints.*;
import java.util.*;
import com.fasterxml.jackson.annotation.*;

@Introspected
class Test {
    private Map<String, String> properties;
    
    @Creator
    Test(Map<String, String> properties) {
        this.properties = properties;
    }
    
    public Map<String, String> getProperties() {
        return properties;
    }
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }
}
''')
        expect:
        introspection.constructorArguments[0].getTypeVariable("K").get().getType() == String
        introspection.constructorArguments[0].getTypeVariable("V").get().getType() == String
    }

    void "test static creator"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
class Test {
    private String name;
    
    private Test(String name) {
        this.name = name;
    }
    
    @Creator
    public static Test forName(String name) {
        return new Test(name);
    }
    
    public String getName() {
        return name;
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
package test;

import io.micronaut.core.annotation.*;

@Introspected
class Test {
    private String name;
    
    private Test(String name) {
        this.name = name;
    }
    
    @Creator
    public static Test forName() {
        return new Test("default");
    }
    
    public String getName() {
        return name;
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
package test;

import io.micronaut.core.annotation.*;

@Introspected
class Test {
    private String name;
    
    private Test(String name) {
        this.name = name;
    }
    
    @Creator
    public static Test forName() {
        return new Test("default");
    }
    
    @Creator
    public static Test forName(String name) {
        return new Test(name);
    }
    
    public String getName() {
        return name;
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

    void "test kotlin static creator"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
class Test {

    private final String name;
    public static final Companion Companion = new Companion();
    
    public final String getName() {
        return name;
    }
    
    private Test(String name) {
        this.name = name;
    }
    
    public static final class Companion {
        
        @Creator
        public final Test forName(String name) {
            return new Test(name);
        }
        
        private Companion() {
        }
    }
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Apple")

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "Apple"
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

    void "test enum bean properties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
public enum Test {

    A(0), B(1), C(2);

    private final int number;

    Test(int number) {
        this.number = number;
    }
    
    public int getNumber() {
        return number;
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

    void "test instantiating an enum"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;

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

    void "test constructor argument nested generics"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;
import java.util.Map;

@Introspected
class Test {

    public Test(Map<String, List<Action>> map) {
    
    }
}

class Action {

}
''')

        expect:
        introspection != null
        introspection.constructorArguments[0].typeParameters.size() == 2
        introspection.constructorArguments[0].typeParameters[0].typeName == 'java.lang.String'
        introspection.constructorArguments[0].typeParameters[1].typeName == 'java.util.List<test.Action>'
        introspection.constructorArguments[0].typeParameters[1].typeParameters.size() == 1
        introspection.constructorArguments[0].typeParameters[1].typeParameters[0].typeName == 'test.Action'
    }

    void "test primitive multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test {

    private int[] oneDimension;
    private int[][] twoDimensions;
    private int[][][] threeDimensions;

    public Test() {
    }
    
    public int[] getOneDimension() {
        return oneDimension;
    }

    public void setOneDimension(int[] oneDimension) {
        this.oneDimension = oneDimension;
    }

    public int[][] getTwoDimensions() {
        return twoDimensions;
    }

    public void setTwoDimensions(int[][] twoDimensions) {
        this.twoDimensions = twoDimensions;
    }
    
    public int[][][] getThreeDimensions() {
        return threeDimensions;
    }

    public void setThreeDimensions(int[][][] threeDimensions) {
        this.threeDimensions = threeDimensions;
    }
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
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test {

    private String[] oneDimension;
    private String[][] twoDimensions;
    private String[][][] threeDimensions;

    public Test() {
    }
    
    public String[] getOneDimension() {
        return oneDimension;
    }

    public void setOneDimension(String[] oneDimension) {
        this.oneDimension = oneDimension;     
    }

    public String[][] getTwoDimensions() {
        return twoDimensions;
    }

    public void setTwoDimensions(String[][] twoDimensions) {
        this.twoDimensions = twoDimensions;
    }
    
    public String[][][] getThreeDimensions() {
        return threeDimensions;
    }

    public void setThreeDimensions(String[][][] threeDimensions) {
        this.threeDimensions = threeDimensions;
    }
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
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.visitor.beans.SomeEnum;

@Introspected
class Test {

    private SomeEnum[] oneDimension;
    private SomeEnum[][] twoDimensions;
    private SomeEnum[][][] threeDimensions;

    public Test() {
    }
    
    public SomeEnum[] getOneDimension() {
        return oneDimension;
    }

    public void setOneDimension(SomeEnum[] oneDimension) {
        this.oneDimension = oneDimension;     
    }

    public SomeEnum[][] getTwoDimensions() {
        return twoDimensions;
    }

    public void setTwoDimensions(SomeEnum[][] twoDimensions) {
        this.twoDimensions = twoDimensions;
    }
    
    public SomeEnum[][][] getThreeDimensions() {
        return threeDimensions;
    }

    public void setThreeDimensions(SomeEnum[][][] threeDimensions) {
        this.threeDimensions = threeDimensions;
    }
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


    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor()]
        }
    }
}
