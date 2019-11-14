package io.micronaut.inject.visitor

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor

import javax.persistence.Id
import javax.validation.constraints.Size

class BeanIntrospectionSpec extends AbstractBeanDefinitionSpec {
    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, IntrospectedTypeElementVisitor.name)
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


    void "test multiple constructors with @JsonCreator"() {
        given:
        ClassLoader classLoader = buildClassLoader('''
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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


    void "test write bean introspection with inner classes"() {
        given:
        def classLoader = buildClassLoader( '''
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
    }
}
