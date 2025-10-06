package io.micronaut.inject.visitor.beans

import com.blazebit.persistence.impl.function.entity.ValuesEntity
import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.visitor.ConfigurationReaderVisitor
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.NextMajorVersion
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.beans.EnumBeanIntrospection
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.core.value.OptionalMultiValues
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.annotation.EvaluatedAnnotationMetadata
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.beans.outer.MuxedEvent2
import io.micronaut.jackson.modules.BeanIntrospectionModule
import io.micronaut.json.JsonMapper
import io.micronaut.validation.visitor.ValidationVisitor
import jakarta.inject.Singleton
import jakarta.validation.Constraint
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import spock.lang.Issue

import javax.annotation.processing.SupportedAnnotationTypes
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Version
import java.lang.annotation.Annotation
import java.lang.reflect.Field
import java.util.stream.Collectors
import java.util.stream.IntStream

class BeanIntrospectionSpec extends AbstractTypeElementSpec {

    void "test record with multiple constructors"() {
        when:
        def introspection = buildBeanIntrospection('test.MyRecord', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;

import java.time.Instant;
import java.util.Date;

@Introspected
record MyRecord(String fooBar, int abcXyz, @Nullable Date theDate, @Nullable String otherStr) {

    MyRecord {
        if (theDate == null) {
            theDate = Date.from(Instant.now());
        }
    }

    public MyRecord(String fooBar, int abcXyz, Date theDate) {
        this(fooBar, abcXyz, theDate, "random");
    }

    public MyRecord(String fooBar, int abcXyz) {
        this(fooBar, abcXyz, Date.from(Instant.now()), "random");
    }

}


    ''' )

        then:
        introspection != null
        introspection.getConstructorArguments().length == 4
    }

    void "test import field introspection"() {
        when:
        def introspection = buildBeanIntrospection('test.$io_micronaut_inject_visitor_beans_MuxedEvent1', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = io.micronaut.inject.visitor.beans.MuxedEvent1.class, accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class Test {
}


    ''' )

        then:
        introspection != null
        introspection.getBeanProperties().size() == 2
        introspection.getProperty("content").get().get(new MuxedEvent1("abc", "xyz")) == "xyz"
    }

    @NextMajorVersion("This test should fail and require ReflectiveAccess on fields/class or there should be an option on the introspected to use reflective access by default by annotating the introspected type")
    void "test import field introspection reflection"() {
        when:
        def introspection = buildBeanIntrospection('test.$io_micronaut_inject_visitor_beans_outer_MuxedEvent2', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = io.micronaut.inject.visitor.beans.outer.MuxedEvent2.class, accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class Test {
}


    ''' )

        then:
        introspection != null
        introspection.getBeanProperties().size() == 2
        introspection.getProperty("content").get().get(new MuxedEvent2("abc", "xyz")) == "xyz"
    }

    void "test reflective access to package private constructor"() {
        when:
        def introspection = BeanIntrospector.SHARED.getIntrospection(PackagePrivateConstructor)

        then:
        introspection != null
        introspection.instantiate() instanceof PackagePrivateConstructor
    }

    void "test inner introspection"() {
        when:
        def introspection = buildBeanIntrospection('test.Test$Foo', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.value.OptionalMultiValues;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected
class Test {
    @Introspected
    record Foo(String name) {}

    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Bar {
        String name;
    }
}


    ''' )

        then:
        introspection != null
        introspection.getBeanType().simpleName == 'Foo'
    }

    void "test inner introspection - without outer annotation"() {
        when:
        def introspection = buildBeanIntrospection('test.Test$Foo', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.value.OptionalMultiValues;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

class Test {
    @Introspected
    record Foo(String name) {}

    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Bar {
        String name;
    }
}


    ''' )

        then:
        introspection != null
        introspection.getBeanType().simpleName == 'Foo'
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

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A1 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A2 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A3 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A4 {
}

@Retention(RetentionPolicy.RUNTIME)
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

@Target({ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A1 {
}

@Target({ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A2 {
}

@Target({ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A3 {
}

@Target({ElementType.TYPE_USE})
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
        property.hasAnnotation("test.A3")
        !property.hasAnnotation("test.A4")
        property.asArgument().getAnnotationMetadata().hasAnnotation("test.A1")
        property.asArgument().getAnnotationMetadata().hasAnnotation("test.A2")
        property.asArgument().getAnnotationMetadata().hasAnnotation("test.A3")
        !property.asArgument().getAnnotationMetadata().hasAnnotation("test.A4")
        readProperty.hasAnnotation("test.A1")
        readProperty.hasAnnotation("test.A2")
        readProperty.hasAnnotation("test.A3")
        !readProperty.hasAnnotation("test.A4")
        writeProperty.hasAnnotation("test.A1")
        writeProperty.hasAnnotation("test.A2")
        writeProperty.hasAnnotation("test.A3")
        !writeProperty.hasAnnotation("test.A4")
    }

    void "test different setter / getter"() {
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
    private Map<CharSequence, List<String>> foo;
    @A2
    public OptionalMultiValues<String> getFoo() {
        return OptionalMultiValues.of(foo);
    }
    public void setFoo(@A3 Map<String, Object> foo) {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A1 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A2 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A3 {
}

''')
        def property = introspection.getBeanProperties().iterator().next()
        then:
        property.asArgument().getType() == OptionalMultiValues
        property.getType() == OptionalMultiValues
//            property.hasAnnotation("test.A1")
        property.hasAnnotation("test.A2")
//            property.hasAnnotation("test.A3")
        property.isReadOnly()
        introspection.getBeanReadProperties().size() == 1
        !introspection.getBeanReadProperties()[0].hasAnnotation("test.A1")
        introspection.getBeanReadProperties()[0].hasAnnotation("test.A2")
        !introspection.getBeanReadProperties()[0].hasAnnotation("test.A3")
        introspection.getBeanReadProperties().iterator().next().getType() == OptionalMultiValues
        introspection.getBeanWriteProperties().size() == 0
    }

    void "test different setter / getter - ignoreSettersWithDifferingType"() {
        when:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.value.OptionalMultiValues;
import java.util.*;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected(ignoreSettersWithDifferingType = false)
class Test {
    @A1
    private Map<CharSequence, List<String>> foo;
    @A2
    public OptionalMultiValues<String> getFoo() {
        return OptionalMultiValues.of(foo);
    }
    public void setFoo(@A3 Map<String, Object> foo) {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A1 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A2 {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface A3 {
}

''')
        def property = introspection.getBeanProperties().iterator().next()
        then:
        property.asArgument().getType() == Map // The property is defined by it's writer type
        property.hasAnnotation("test.A1")
        property.hasAnnotation("test.A2")
        !property.hasAnnotation("test.A3")
        property.getType() == Map
        !property.isReadOnly()
        introspection.getBeanReadProperties().size() == 1
        introspection.getBeanReadProperties()[0].hasAnnotation("test.A1")
        introspection.getBeanReadProperties()[0].hasAnnotation("test.A2")
        !introspection.getBeanReadProperties()[0].hasAnnotation("test.A3")
        introspection.getBeanReadProperties()[0].getType() == OptionalMultiValues
        introspection.getBeanWriteProperties().size() == 1
        introspection.getBeanWriteProperties()[0].getType() == Map
        introspection.getBeanWriteProperties()[0].hasAnnotation("test.A1")
        introspection.getBeanWriteProperties()[0].hasAnnotation("test.A2")
        !introspection.getBeanWriteProperties()[0].hasAnnotation("test.A3")
    }

    void "test bytes[] in a constructor"() {
        given:
        def introspection = buildBeanIntrospection('test.FormulaDto', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

import java.util.Collections;
import java.util.List;

@Introspected
class FormulaDto extends FormulaCreationDto {

	private final List<String> otherColumns;

	public FormulaDto(
			List<String> otherColumns,
			byte[] bytes
	) {
		super(bytes);
		this.otherColumns = Collections.unmodifiableList(otherColumns);
	}

	public List<String> getOtherColumns() {
		return this.otherColumns;
	}
}

@Introspected
class FormulaCreationDto {
    private final byte[] bytes;

    public FormulaCreationDto(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return this.bytes;
    }

}
''')
        expect:
        introspection.instantiate(true, List.of(), new byte[] {123}).getBytes()
    }

    void "test Boolean in a constructor"() {
        given:
        def introspection = buildBeanIntrospection('test.FormulaDto', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

import java.util.Collections;
import java.util.List;

@Introspected
class FormulaDto extends FormulaCreationDto {

	private final List<String> otherColumns;

	public FormulaDto(
			List<String> otherColumns,
			Boolean percent
	) {
		super(Optional.of(percent));
		this.otherColumns = Collections.unmodifiableList(otherColumns);
	}

	public List<String> getOtherColumns() {
		return this.otherColumns;
	}
}

@Introspected
class FormulaCreationDto {
    private final boolean percent;

    public FormulaCreationDto(Optional<Boolean> percent) {
        this.percent = percent.orElse(false);
    }

    public boolean isPercent() {
        return this.percent;
    }

}
''')
        expect:
        introspection.instantiate(true, List.of(), true).isPercent()
    }

    void "test expressions in introspection properties with type use"() {
        given:
        def introspection = buildBeanIntrospection('mixed.Test', '''
package mixed;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
import java.lang.annotation.*;
import static java.lang.annotation.ElementType.*;

@Introspected
class Test {
    @Nullable
    @Ann("#{'test'}")
    private String foo;
    public String getFoo() {
        return foo;
    }
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@interface Ann {
    String value();
}
''')
        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("foo", String)
        test.foo = 'value'

        then: 'expressions can be retrieved'
        prop.get(test) == 'value'
        prop.getAnnotationMetadata() instanceof EvaluatedAnnotationMetadata
        // TODO: Support expressions in TYPE_USE annotations?
        prop.stringValue("mixed.Ann").get() == '#{\'test\'}'
    }

    void "test expressions in introspection record components"() {
        given:
        def introspection = buildBeanIntrospection('mixed.Test', '''
package mixed;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
import java.lang.annotation.*;

@Introspected
record Test(@Ann("#{this.ipAddress}:#{this.port}") String ipAddress, int port) {
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@Target(ElementType.METHOD)
@interface Ann {
    String value();
}
''')
        when:
        def test = introspection.instantiate("value", 10)
        def prop = introspection.getRequiredProperty("ipAddress", String)

        then: 'expressions can be retrieved'
        prop.get(test) == 'value'
        prop.getAnnotationMetadata() instanceof EvaluatedAnnotationMetadata
        prop.getAnnotationMetadata().withArguments(test).stringValue("mixed.Ann").get() == 'value:10'
    }

    void "test expressions in introspection properties"() {
        given:
        def introspection = buildBeanIntrospection('mixed.Test', '''
package mixed;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
import java.lang.annotation.*;

@Introspected
class Test {
    @Nullable
    @Ann("#{'test'}")
    private String foo;
    public String getFoo() {
        return foo;
    }
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Documented
@interface Ann {
    String value();
}
''')
        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("foo", String)
        test.foo = 'value'

        then: 'expressions can be retrieved'
        prop.get(test) == 'value'
        prop.getAnnotationMetadata() instanceof EvaluatedAnnotationMetadata
        prop.stringValue("mixed.Ann").get() == 'test'
    }

    void "test mix getter and setter with interface type"() {
        def introspection = buildBeanIntrospection('mixed.Pet', '''
package mixed;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@Introspected
class Owner implements IOwner {
    @Nullable
    private String name;
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
}

interface IOwner {
    String getName();
}

@Introspected
class Pet {
    private Owner owner;
    public Owner getOwner() {
        return owner;
    }
    public void setOwner(IOwner owner) {
        this.owner = (Owner) owner;
    }
}
''')
        when:
        def owner = introspection.beanType.classLoader.loadClass('mixed.Owner').newInstance(name:"Fred")
        def prop = introspection.getRequiredProperty("owner", Object)
        def pet = introspection.instantiate()
        prop.set(pet, owner)

        then:'the write method is not considered to match the getter/setter pair'
        prop.isReadWrite()
        prop.get(pet).is(owner)
    }

    void "test mix optional getter with setter"() {
        given:
        def introspection = buildBeanIntrospection('mixed.Test', '''
package mixed;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@Introspected
class Test {
    @Nullable
    private String foo;
    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
}
''')
        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("foo", Optional)
        test.foo = 'value'

        then: 'the write method is not considered to match the getter/setter pair'
        prop.get(test).get() == 'value'
        prop.type == Optional
        prop.isReadOnly()
    }

    void "test mix optional getter with setter - different order"() {
        given:
        def introspection = buildBeanIntrospection('mixed.Test', '''
package mixed;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;
@Introspected
class Test {
    @Nullable
    private String foo;
    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }
    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }
}
''')
        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("foo", Optional)
        test.foo = 'value'

        then: 'the write method is not considered to match the getter/setter pair'
        prop.get(test).get() == 'value'
        prop.type == Optional
        prop.isReadOnly()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/8657")
    void "test executable method on abstract class with introspection"() {
        when:
        def introspection = buildBeanIntrospection('issue8657.Test', '''
package issue8657;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.inject.visitor.beans.Auditable;

@Introspected
class Test extends Auditable {
    private String name;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}

''')

        then:
        introspection.beanMethods.size() == 1

        when:
        def bean = introspection.instantiate()
        def method = introspection.beanMethods.first()
        method.invoke(bean)

        then:
        bean.updatedAt != null
    }

    void "test introspection can written to different package"() {
        when:
        def classLoader = buildClassLoader("test.Test", '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(targetPackage = "test.introspections")
public class Test {
    private String name;
    public Test(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}

''')
        def introspectionName = 'test.introspections.$Test$Introspection'
        def introspection = classLoader.loadClass(introspectionName).newInstance() as BeanIntrospection

        then:
        introspection != null
        introspection.getProperty("name").isPresent()

        when:
        def introspectionRefName = 'test.introspections.$Test$Introspection'
        def introspectionRef = classLoader.loadClass(introspectionRefName).newInstance() as BeanIntrospectionReference

        then:
        introspectionRef != null
        introspectionRef.load() != null
    }

    void "test generics in arrays don't stack overflow"() {
        given:
        def introspection = buildBeanIntrospection('arraygenerics.Test', '''
package arraygenerics;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;

@Introspected
class Test<T extends CharSequence> {
    private T[] array;
    public T[] getArray() {
        return array;
    }
    public void setArray(T[] array) {
        this.array = array;
    }

    @Executable
    T[] myMethod() {
        return array;
    }
}
''')
        expect:
        introspection.getRequiredProperty("array", CharSequence[].class)
                .type == CharSequence[].class
        introspection.beanMethods.first().returnType.type == CharSequence[].class
    }

    void "test property type is defined by its writer field"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@Introspected(accessKind = {Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
class Test {
    @Nullable
    String foo;

    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }

}
''')
        expect:
        introspection.getProperty("foo").get().type == String.class
    }

    void "test read property by type is defined by its reader field"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@Introspected(accessKind = {Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
class Test {
    @Nullable
    String foo;

    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }

}
''')
        expect:
        introspection.getReadProperty("foo", String.class).isEmpty()
        introspection.getReadProperty("foo", Optional.class).get().type == Optional.class

    }

    void "test read property type is defined by its field"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@Introspected(accessKind = {Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
class Test {
    @Nullable
    String foo;

    public void setFoo(Optional<String> foo) {
        this.foo = foo.orElse(null);
    }

}
''')
        expect:
        introspection.getReadProperty("foo", String.class).get().type == String.class
        introspection.getReadProperty("foo", Optional.class).isEmpty()
    }

    void "test optional property type is defined by its setter"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.*;

@Introspected
class Test {
    @Nullable
    private String foo;
    @Nullable
    private Long lng;
    @Nullable
    private Double dbl;
    @Nullable
    private Integer ingr;

    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }

    public OptionalDouble getDbl() {
        return OptionalDouble.of(dbl);
    }

    public OptionalLong getLng() {
        return OptionalLong.of(lng);
    }

    public OptionalInt getIngr() {
        return OptionalInt.of(ingr);
    }

    public void setFoo(@Nullable String foo) {
        this.foo = foo;
    }

    public void setLng(@Nullable Long lng) {
        this.lng = lng;
    }

    public void setDbl(@Nullable Double dbl) {
        this.dbl = dbl;
    }

    public void setIngr(@Nullable Integer ingr) {
        this.ingr = ingr;
    }

}
''')
        expect:
        introspection.getPropertyNames().length == 4
        introspection.getProperty("foo").get().type == Optional.class
        introspection.getProperty("lng").get().type == OptionalLong.class
        introspection.getProperty("dbl").get().type == OptionalDouble.class
        introspection.getProperty("ingr").get().type == OptionalInt.class

        introspection.getProperty("foo").get().isReadOnly()
        introspection.getProperty("lng").get().isReadOnly()
        introspection.getProperty("dbl").get().isReadOnly()
        introspection.getProperty("ingr").get().isReadOnly()
    }

    void "test property type is not defined by its not accessible field"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Nullable;
import java.util.Optional;

@Introspected(accessKind = {Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
class Test {
    @Nullable
    private String foo;

    public Optional<String> getFoo() {
        return Optional.ofNullable(foo);
    }

}
''')
        expect:
        introspection.getProperty("foo").get().type == Optional.class
    }

    void 'test favor method access'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;


@Introspected(accessKind={Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
class Test {
    public String one;
    public boolean invoked = false;
    public String getOne() {
        this.invoked = true;
        return one;
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
    }

    void 'test favor method access with custom getter'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;

@Introspected(accessKind={Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD})
@AccessorsStyle(readPrefixes = "with")
class Test {
    public String one;
    public boolean invoked = false;
    public String withOne() {
        this.invoked = true;
        return one;
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
    }

    void 'test use getter to read and field to write'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;

@Introspected(accessKind={Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD})
class Test {
    public String one;
    public boolean invoked = false;
    public String getOne() {
        this.invoked = true;
        return one;
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
        instance.invoked // The property was accessed with getter
    }

    void 'test use filed to read and setter to write'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;


@Introspected(accessKind={Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD})
@AccessorsStyle(readPrefixes = "with")
class Test {
    public String one;
    public boolean invoked = false;
    public String withOne() {
        this.invoked = true;
        return one;
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
        instance.invoked // Setter invoked
    }

    void 'test field access only'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;


@Introspected(accessKind=Introspected.AccessKind.FIELD)
class Test {
    public String one; // read/write
    public final int two; // read-only
    String three; // package protected
    protected String four; // protected can be accessed from the same package
    private String five; // not included since private

    Test(int two) {
        this.two = two;
    }
}
''');
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

    void 'test field access only - public only'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

import io.micronaut.core.annotation.*;


@Introspected(
    accessKind=Introspected.AccessKind.FIELD,
    visibility = Introspected.Visibility.PUBLIC
)
class Test {
    public String one; // read/write
    public final int two; // read-only
    String three; // package protected
    protected String four; // not included since protected
    private String five; // not included since private

    Test(int two) {
        this.two = two;
    }
}
''');
        when:
        def properties = introspection.getBeanProperties()

        then:
        properties.size() == 2

        def one = introspection.getRequiredProperty("one", String)
        one.isReadWrite()

        def two = introspection.getRequiredProperty("two", int.class)
        two.isReadOnly()

    }

    void 'test bean constructor'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('beanctor.Test','''\
package beanctor;

@io.micronaut.core.annotation.Introspected(withPrefix="alter")
public class Test {

    private final String another;

    @com.fasterxml.jackson.annotation.JsonCreator
    Test(String another) {
        this.another = another;
    }

    public String getAnother() {
        return another;
    }
}
''')


        when:
        def constructor = introspection.getConstructor()
        def newInstance = constructor.instantiate("test")

        then:
        newInstance != null
        newInstance.another == "test"
        !introspection.getAnnotationMetadata().hasDeclaredAnnotation(JsonCreator)
        constructor.getAnnotationMetadata().hasDeclaredAnnotation(JsonCreator)
        !constructor.getAnnotationMetadata().hasDeclaredAnnotation(Introspected)
        !constructor.getAnnotationMetadata().hasAnnotation(Introspected)
        !constructor.getAnnotationMetadata().hasStereotype(Introspected)
        constructor.arguments.length == 1
        constructor.arguments[0].type == String
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
        def properties = introspection.getBeanProperties()
        Collection<BeanMethod> beanMethods = introspection.getBeanMethods()

        then:
        properties.size() == 1
        beanMethods*.name as Set == ['invokeMe', 'invokePrim', 'superMethod', 'ok'] as Set
        beanMethods.every({it.annotationMetadata.hasAnnotation(Executable)})
        beanMethods.every { it.declaringBean == introspection}

        when:

        def invokeMe = beanMethods.find { it.name == 'invokeMe' }
        def invokePrim = beanMethods.find { it.name == 'invokePrim' }
        def itfeMethod = beanMethods.find { it.name == 'ok' }
        def bean = introspection.instantiate()

        then:
        invokeMe instanceof ExecutableMethod
        invokeMe.invoke(bean, "test") == 'test'
        invokePrim.invoke(bean, 10) == 10
        itfeMethod.invoke(bean) == true
    }

    void "test generate bean method for introspected class with custom getter"() {
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

@io.micronaut.core.annotation.AccessorsStyle(readPrefixes = "read")
interface SomeInt {
    @Executable
    default boolean ok() {
        return true;
    }

    default String readName() {
        return "ok";
    }
}
''')
        when:
        def properties = introspection.getBeanProperties()
        Collection<BeanMethod> beanMethods = introspection.getBeanMethods()

        then:
        properties.size() == 1
        beanMethods*.name as Set == ['invokeMe', 'invokePrim', 'superMethod', 'ok'] as Set
        beanMethods.every({it.annotationMetadata.hasAnnotation(Executable)})
        beanMethods.every { it.declaringBean == introspection}

        when:

        def invokeMe = beanMethods.find { it.name == 'invokeMe' }
        def invokePrim = beanMethods.find { it.name == 'invokePrim' }
        def itfeMethod = beanMethods.find { it.name == 'ok' }
        def bean = introspection.instantiate()

        then:
        invokeMe instanceof ExecutableMethod
        invokeMe.invoke(bean, "test") == 'test'
        invokePrim.invoke(bean, 10) == 10
        itfeMethod.invoke(bean) == true
    }

    void "test custom with prefix"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('customwith.CopyMe','''\
package customwith;

import java.net.URL;

@io.micronaut.core.annotation.Introspected(withPrefix="alter")
public class CopyMe {

    private final String another;

    CopyMe(String another) {
        this.another = another;
    }

    public String getAnother() {
        return another;
    }

    public CopyMe alterAnother(String a) {
        return this.another == a ? this : new CopyMe(a.toUpperCase());
    }
}
''')


        when:
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = introspection.instantiate("test")

        then:
        newInstance.another == "test"

        when:"An explicit with method is used"
        def result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'
    }

    void "test copy constructor via mutate method"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.CopyMe','''\
package test;

import java.net.URL;

@io.micronaut.core.annotation.Introspected
public class CopyMe {

    private URL url;
    private final String name;
    private final String another;

    CopyMe(String name, String another) {
        this.name = name;
        this.another = another;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public String getAnother() {
        return another;
    }

    public CopyMe withAnother(String a) {
        return this.another == a ? this : new CopyMe(this.name, a.toUpperCase());
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
        def property = introspection.getRequiredProperty("name", String)
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = property.withValue(copyMe, "Changed")

        then:
        !newInstance.is(copyMe)
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
    }

    void "test secondary constructor for Java 14+ records"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

@io.micronaut.core.annotation.Introspected
public record Foo(int x, int y) {
    public Foo(int x) {
        this(x, 20);
    }
    public Foo() {
        this(20, 20);
    }
}
''')
        when:
        def obj = introspection.instantiate(5, 10)

        then:
        obj.x() == 5
        obj.y() == 10
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/8187')
    void "test secondary constructor for Java 14+ records with initializer"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

@io.micronaut.core.annotation.Introspected
public record Foo(int x, int y) {
    public Foo {
        if (x < 0) {
            throw new IllegalArgumentException("Invalid argument");
        }
    }
    public Foo(int x) {
        this(x, 20);
    }
    public Foo() {
        this(20, 20);
    }
}
''')
        when:
        def obj = introspection.instantiate(5, 10)

        then:
        introspection.getConstructorArguments().length == 2
        obj.x() == 5
        obj.y() == 10
    }

    void "test serializing records respects json annotations"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('json.test.Foo', '''
package json.test;

import io.micronaut.core.annotation.Creator;
import java.util.List;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@io.micronaut.core.annotation.Introspected
public record Foo(@JsonProperty("other") String name, @JsonIgnore int y) {
}
''')
        when:
        def obj = introspection.instantiate("test", 10)
        String result = ApplicationContext.run('bean.introspection.test':'true').withCloseable {
            it.getBean(StaticBeanIntrospectionModule).introspectionMap[introspection.beanType] = introspection
            it.getBean(JsonMapper).writeValueAsString(obj)
        }
        then:
        result == '{"other":"test"}'
    }

    void "test secondary constructor with @Creator for Java 14+ records"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

import io.micronaut.core.annotation.Creator;
import java.util.List;
import jakarta.validation.constraints.Min;

@io.micronaut.core.annotation.Introspected
public record Foo(int x, int y) {
    @Creator
    public Foo(int x) {
        this(x, 20);
    }
    public Foo() {
        this(20, 20);
    }
}
''')
        when:
        def obj = introspection.instantiate(5)

        then:
        obj.x() == 5
        obj.y() == 20
    }

    void "test annotations on generic type arguments for Java 14+ records"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

import jakarta.validation.constraints.Min;

import java.util.List;

@io.micronaut.core.annotation.Introspected
public record Foo(List<@Min(10) Long> value) {
}
''')
        when:
        BeanProperty<?, ?> property = introspection.getRequiredProperty("value", List)
        def genericTypeArg = property.asArgument().getTypeParameters()[0]

        then:
        property != null
        genericTypeArg.annotationMetadata.hasStereotype(Constraint)
        genericTypeArg.annotationMetadata.hasAnnotation(Min)
        genericTypeArg.annotationMetadata.intValue(Min).getAsInt() == 10
    }

    void 'test annotations on generic type arguments'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

import io.micronaut.core.annotation.Creator;
import java.util.List;
import jakarta.validation.constraints.Min;
import java.lang.annotation.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.lang.annotation.ElementType.*;

@io.micronaut.core.annotation.Introspected
public class Foo {
    private List<@Min(10) @SomeAnn Long> value;

    public List<Long> getValue() {
        return value;
    }

    public void setValue(List<Long> value) {
        this.value = value;
    }
}

@Documented
@Retention(RUNTIME)
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@interface SomeAnn {

}
''')
        when:
        BeanProperty<?, ?> property = introspection.getRequiredProperty("value", List)
        def genericTypeArg = property.asArgument().getTypeParameters()[0]

        then:
        property != null
        genericTypeArg.annotationMetadata.hasAnnotation(Min)
        genericTypeArg.annotationMetadata.intValue(Min).getAsInt() == 10
    }

    void "test bean introspection on a Java 14+ record"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

@io.micronaut.core.annotation.Introspected
public record Foo(@jakarta.validation.constraints.NotBlank String name, int age) {
}
''')
        when:
        def test = introspection.instantiate("test", 20)
        def property = introspection.getRequiredProperty("name", String)
        def argument = introspection.getConstructorArguments()[0]

        then:
        argument.name == 'name'
        argument.getAnnotationMetadata().hasStereotype(Constraint)
        argument.getAnnotationMetadata().hasAnnotation(NotBlank)
        test.name == 'test'
        test.name() == 'test'
        introspection.propertyNames.length == 2
        introspection.propertyNames == ['name', 'age'] as String[]
        property.hasAnnotation(NotBlank)
        property.isReadOnly()
        property.hasSetterOrConstructorArgument()
        property.name == 'name'
        property.get(test) == 'test'

        when:"a mutation is applied"
        def newTest = property.withValue(test, "Changed")

        then:"a new instance is returned"
        !newTest.is(test)
        newTest.name() == 'Changed'
        newTest.age() == 20
    }

    void "test create bean introspection for external inner class"() {
        given:
        ApplicationContext applicationContext = buildContext('test.Foo', '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(classes=OuterBean.InnerBean.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = applicationContext.classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_OuterBean$InnerBean$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getBeanType() == OuterBean.InnerBean.class

        when:
        BeanIntrospection i = reference.load()

        then:
        i.propertyNames.length == 1
        i.propertyNames[0] == 'name'

        when:
        def o = i.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'

        cleanup:
        applicationContext.close()
    }

    void "test create bean introspection for external class with custom package"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.visitor.beans.OuterBean;

@Introspected(classes=OuterBean.InnerBean.class, targetPackage="test.micronaut.intro")
class Test {}
''')

        when:"the reference is loaded"
        def reference = classLoader.loadClass('test.micronaut.intro.$io_micronaut_inject_visitor_beans_OuterBean$InnerBean$Introspection').newInstance() as BeanIntrospectionReference

        then:"the reference is valid"
        notThrown(ClassNotFoundException)
        reference.getBeanType() == OuterBean.InnerBean.class
        reference.load() != null

        print(classLoader)

        when:"the introspection is loaded"
        def introspectionName = 'test.micronaut.intro.$io_micronaut_inject_visitor_beans_OuterBean$InnerBean$Introspection'
        def introspection = classLoader.loadClass(introspectionName).newInstance() as BeanIntrospection

        then:"the introspection is valid"
        notThrown(ClassNotFoundException)
        introspection.getProperty("name").isPresent()
    }

    void "test create bean introspection for interface"() {
        given:
        def context = buildContext('itfcetest.MyInterface','''
package itfcetest;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;

@Introspected(classes = MyInterface.class)
class Test {}

@JsonClassDescription
public interface MyInterface {
    String getName();
    @Executable
    default String name() {
        return getName();
    }
}

class MyImpl implements MyInterface {
    @Override public String getName() {
        return "ok";
    }
}
''')
        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('itfcetest.$itfcetest_MyInterface$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        BeanIntrospection introspection = reference.load()

        then:
        introspection.getBeanType().isInterface()
        introspection.beanProperties.size() == 1
        introspection.beanMethods.size() == 1
        introspection.hasAnnotation(JsonClassDescription)
    }

    void "test create bean introspection for interface - only methods"() {
        given:
        def context = buildContext('itfcetest.MyInterface','''
package itfcetest;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.context.annotation.Executable;

@Introspected(classes = MyInterface.class)
class Test {}

public interface MyInterface {
    @Executable
    String name();
}

class MyImpl implements MyInterface {
    @Override public String name() {
        return "ok";
    }
}
''')
        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('itfcetest.$itfcetest_MyInterface$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        BeanIntrospection introspection = reference.load()

        then:
        introspection.getBeanType().isInterface()
        introspection.beanProperties.size() == 0
        introspection.beanMethods.size() == 1
    }

    void "test create bean introspection for external inner interface"() {
        given:
        ApplicationContext applicationContext = buildContext('test.Foo', '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(classes=OuterBean.InnerInterface.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = applicationContext.classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_OuterBean$InnerInterface$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getBeanType() == OuterBean.InnerInterface.class

        when:
        BeanIntrospection i = reference.load()

        then:
        i.propertyNames.length == 1
        i.propertyNames[0] == 'name'

        when:
        def o = i.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'

        cleanup:
        applicationContext.close()
    }

    void "test introspection class member configuration works 2"() {
        when:
        BeanIntrospection introspection = BeanIntrospection.getIntrospection(ValuesEntity)

        then:
        noExceptionThrown()
        introspection != null
    }

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
        def property = introspection.getRequiredProperty("name", String)

        then:
        introspection.beanProperties.first().type == String
        property.get(test) == 'test'
        !property.hasSetterOrConstructorArgument()

        when:
        property.withValue(test, 'try change')

        then:
        def e = thrown(UnsupportedOperationException)
        e.message =='Cannot mutate property [name] that is not mutable via a setter method, field or constructor argument for type: test.Foo'
    }

    void "test bean introspection with property of generic superclass"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

@io.micronaut.core.annotation.Introspected
class Foo extends GenBase<String> {
    public String getName() {
        return "test";
    }
}

abstract class GenBase<T> {
    abstract T getName();

    public T getOther() {
        return (T) "other";
    }
}
''')
        when:
        def test = introspection.instantiate()

        def beanProperties = introspection.beanProperties.toList()
        then:
        beanProperties[0].type == String
        beanProperties[1].type == String
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
        introspection.getRequiredProperty("other", String)
                .get(test) == 'other'
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

        when:
        def returnedBean = bp.withValue(test, 10L)

        then:
        returnedBean.is(test)
        bp.get(test) == 10L
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
        introspection.constructorArguments.length == 1
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
    }

    void "test bean introspection with property with static creator method on interface with generic type arguments"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test;

import io.micronaut.core.annotation.Creator;

@io.micronaut.core.annotation.Introspected
interface Foo<T> {
    String getName();

    @Creator
    static <T1> Foo<T1> create(String name) {
        return () -> name;
    }
}

''')
        when:
        def test = introspection.instantiate("test")

        then:
        introspection.constructorArguments.length == 1
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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
public interface ValidatedConfig {

    @NotNull
    URL getUrl();

}


''')
        expect:
        introspection != null
        introspection.getProperty("url")
    }

    void "test generate bean introspection for @ConfigurationProperties interface with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig', '''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;import io.micronaut.core.annotation.AccessorsStyle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = {"read"})
public interface ValidatedConfig {

    @NotNull
    URL readUrl();

}


''')
        expect:
        introspection != null
        introspection.getProperty("url")
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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
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
        introspection.getProperty("url")
        introspection.getProperty("name")
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters with custom getters and setters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig', '''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AccessorsStyle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
public class ValidatedConfig {

    private URL url;
    private String name;

    @NotNull
    public URL readUrl() {
        return url;
    }

    public void withUrl(URL url) {
        this.url = url;
    }

    @NotBlank
    public String readName() {
        return name;
    }

    public void withName(String name) {
        this.name = name;
    }
}
''')
        expect:
        introspection != null
        introspection.getProperty("url")
        introspection.getProperty("name")
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters and custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
@io.micronaut.core.annotation.AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
public class ValidatedConfig {

    private URL url;
    private String name;

    @NotNull
    public URL readUrl() {
        return url;
    }

    public void withUrl(URL url) {
        this.url = url;
    }

    @NotBlank
    public String readName() {
        return name;
    }

    public void withName(String name) {
        this.name = name;
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
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
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

    void "test generate bean introspection for @ConfigurationProperties with validation rules on getters with inner class and custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
@io.micronaut.core.annotation.AccessorsStyle(readPrefixes = "read")
public class ValidatedConfig {

    private URL url;

    @NotNull
    public URL readUrl() {
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

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
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
        introspection.getProperty("url")
    }

    void "test generate bean introspection for inner @ConfigurationProperties with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig$Another', '''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.AccessorsStyle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = "read")
class ValidatedConfig {

    private URL url;

    @NotNull
    public URL readUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public static class Inner {
    }

    @ConfigurationProperties("another")
    @AccessorsStyle(readPrefixes = "read")
    static class Another {

        private URL url;

        @NotNull
        public URL readUrl() {
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
        introspection.getProperty("url")
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on fields"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
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
        !introspection.getIndexedProperties(Constraint.class).isEmpty()
        introspection.getIndexedProperties(Constraint.class).size() == 2
    }

    void "not found indexed properties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig','''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
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
        !introspection.getIndexedProperties(Constraint.class).isEmpty()
        introspection.getIndexedProperties(Constraint.class).size() == 2
        introspection.getIndexedProperty(Nullable.class).isEmpty()
        introspection.getIndexedProperties(Nullable.class).isEmpty()
    }

    void "test generate bean introspection for @ConfigurationProperties with validation rules on fields and custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.ValidatedConfig', '''\
package test;

import io.micronaut.context.annotation.ConfigurationProperties;import io.micronaut.core.annotation.AccessorsStyle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.net.URL;

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = "read")
public class ValidatedConfig {

    @NotNull
    URL url;

    @NotBlank
    protected String name;

    public URL readUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String readName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}


''')

        expect:
        introspection != null
        !introspection.getIndexedProperties(Constraint.class).isEmpty()
        introspection.getIndexedProperties(Constraint.class).size() == 2
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
    MyConfig(@jakarta.validation.constraints.NotBlank String host, int serverPort) {
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

    void "test generate bean introspection for @ConfigurationProperties with validation rules with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.MyConfig', '''\
package test;

import io.micronaut.context.annotation.*;import io.micronaut.core.annotation.AccessorsStyle;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
@AccessorsStyle(readPrefixes = "read")
class MyConfig {
    private String host;
    private int serverPort;

    @ConfigurationInject
    MyConfig(@jakarta.validation.constraints.NotBlank String host, int serverPort) {
        this.host = host;
        this.serverPort = serverPort;
    }

    public String readHost() {
        return host;
    }

    public int readServerPort() {
        return serverPort;
    }
}

''')

        expect:
        introspection != null
        introspection.getProperty("host")
        introspection.getProperty("serverPort")
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

    void "test annotation metadata present on deep type parameters"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test;
import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Set;

@Introspected
public class Test {
    List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>> deepList;
    List<List<List<List<List<List<String>>>>>> deepList2;

    Test(List<List<List<String>>> deepList) { this.deepList = deepList; }
    List<List<List<String>>> getDeepList() { return deepList; }
    List<List<List<List<List<List<String>>>>>> getDeepList2() { return deepList2; }
}
''')
        expect:
        introspection != null
        def property = introspection.getProperty("deepList").get().asArgument()
        property.getTypeParameters().length == 1
        def param1 = property.getTypeParameters()[0]
        param1.getTypeParameters().length == 1
        def param2 = param1.getTypeParameters()[0]
        param2.getTypeParameters().length == 1
        def param3 = param2.getTypeParameters()[0]

        param1.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
        param2.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
        param3.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2083')
    void "test class references in constructor arguments"() {
        given:
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

import jakarta.validation.constraints.*;


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
        def clazz = context.classLoader.loadClass('test.$Address$Introspection')
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
        Class<?> clazz = context.classLoader.loadClass('test.$Book$Introspection')
        BeanIntrospectionReference reference = (BeanIntrospectionReference) clazz.newInstance()

        expect:
        reference != null

        when:
        BeanIntrospection introspection = reference.load()

        then:
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 2 // Author also passes the rules to be a property, but only write only

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
        Class<?> clazz = context.classLoader.loadClass('test.$Book$Introspection')
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
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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

    void "test write bean introspection with builder style properties with custom getters and setters"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
class Test {
    private String name;
    public String readName() {
        return this.name;
    }
    public Test withName(String n) {
        this.name = n;
        return this;
    }
}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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
        !bi.getIndexedProperty(Column, null).isPresent()
        bi.getIndexedProperty(Column, "unknown").isEmpty()
        bi.getIndexedProperty(Column, null).isEmpty()
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
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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

    void "test write bean introspection indexes"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import jakarta.validation.constraints.*;
import javax.persistence.*;


import java.lang.annotation.*;
import io.micronaut.core.annotation.Introspected;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
@interface MappedProperty1 {

    /**
     * The destination the property is persisted to. This could be the column name etc. or some external form.
     *
     * @return The destination
     */
    String value() default "";
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.FIELD, ElementType.METHOD})
@Documented
@interface MappedProperty2 {

    /**
     * The destination the property is persisted to. This could be the column name etc. or some external form.
     *
     * @return The destination
     */
    String value() default "";
}

@Introspected(indexed = {
        @Introspected.IndexedAnnotation(annotation = MappedProperty1.class, member = "value"),
        @Introspected.IndexedAnnotation(annotation = MappedProperty2.class, member = "value")
})
@Entity
class Test {
    @Id
    @GeneratedValue
    private Long id;
    @Version
    @MappedProperty1("test_version")
    private Long version;
    @MappedProperty1
    @MappedProperty2("xyz")
    private String ln;
    @MappedProperty1("test_name")
    private String name;
    @Size(max=100)
    private int age;

    public String getLn() {
        return ln;
    }

    public void setLn(String ln) {
        this.ln = ln;
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

        when:"The introspection is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
        Class<Annotation> mappedProperty1Class = context.classLoader.loadClass("test.MappedProperty1")
        Class<Annotation> mappedProperty2Class = context.classLoader.loadClass("test.MappedProperty2")
        BeanIntrospectionReference reference = clazz.newInstance()

        BeanIntrospection bi = reference.load()

        then:"it is correct"
        bi.instantiate()
        bi.getIndexedProperties(mappedProperty1Class).size() == 3
        bi.getIndexedProperty(mappedProperty1Class, "test_version").get().name == "version"
        bi.getIndexedProperty(mappedProperty1Class, "test_name").get().name == "name"
        bi.getIndexedProperty(mappedProperty1Class, null).get().name == "ln"
        bi.getIndexedProperty(mappedProperty1Class, "unknown").isEmpty()
        bi.getIndexedProperty(mappedProperty2Class, "xyz").get().name == "ln"
        bi.getIndexedProperty(mappedProperty2Class, null).isEmpty()

        cleanup:
        context?.close()
    }

    void "test write bean introspection data for class in another package"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(classes=OtherTestBean.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_OtherTestBean$Introspection')
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
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(classes=TestBean.class)
class Test {}
''')

        when:"the reference is loaded"
        context.classLoader.loadClass('test.$Test$Introspection')

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
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.inject.visitor.beans.*;

@Introspected(packages="io.micronaut.inject.visitor.beans", includedAnnotations=MarkerAnnotation.class)
class Test {}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_OtherTestBean$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is generated"
        reference != null

        cleanup:
        context?.close()
    }

    void "test write bean introspection data"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
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
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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

    void "test write bean introspection data with custom getters and setters"() {
        given:
        ApplicationContext context = buildContext('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.*;
import io.micronaut.core.convert.TypeConverter;

@Introspected
@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
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

    public TypeConverter<String, Collection> readGenericsTest() {
        return genericsTest;
    }
    public TypeConverter<String, Object[]> readGenericsArrayTest() {
        return genericsArrayTest;
    }

    public String readReadOnly() {
        return readOnly;
    }

    public boolean readFlag() {
        return flag;
    }
    public void withFlag(boolean flag) {
        this.flag = flag;
    }

    public String readName() {
        return this.name;
    }
    public void withName(String n) {
        this.name = n;
    }

    public int readAge() {
        return age;
    }
    public void withAge(int age) {
        this.age = age;
    }

    public List<Number> readList() {
        return this.list;
    }
    public void withList(List<Number> l) {
        this.list = l;
    }

    public int[] readPrimitiveArray() {
        return this.primitiveArray;
    }
    public void withPrimitiveArray(int[] a) {
        this.primitiveArray = a;
    }

    public String[] readStringArray() {
        return this.stringArray;
    }
    public void withStringArray(String[] s) {
        this.stringArray = s;
    }
}

@AccessorsStyle(readPrefixes = "read", writePrefixes = "with")
class ParentBean {
    private List<byte[]> listOfBytes;

    public List<byte[]> readListOfBytes() {
        return this.listOfBytes;
    }

    public void withListOfBytes(List<byte[]> list) {
        this.listOfBytes = list;
    }
}
''')

        when:"the reference is loaded"
        def clazz = context.classLoader.loadClass('test.$Test$Introspection')
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
import jakarta.validation.constraints.*;
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

    void "test static creator with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read")
class Test {
    private String name;

    private Test(String name) {
        this.name = name;
    }

    @Creator
    public static Test forName(String name) {
        return new Test(name);
    }

    public String readName() {
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

    void "test enum bean with annotations"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;
import io.micronaut.core.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
@Introspected
public enum Test {
    @JsonProperty("X")
    A(0),
    @JsonProperty("Y")
    B(1),
    @JsonProperty("Z")
    C(2),
    D(3);
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
        introspection instanceof EnumBeanIntrospection

        when:
        def enumIntrospection = introspection as EnumBeanIntrospection
        def instance = introspection.instantiate("A")

        then:
        instance.name() == "A"
        introspection.getRequiredProperty("number", int).get(instance) == 0

        when:
        def annotationMetadata = enumIntrospection.constants.find { it.value == instance }.annotationMetadata

        then:
        annotationMetadata.stringValue(JsonProperty).get() == "X"

        and:
        enumIntrospection.constants[0].stringValue(JsonProperty).get() == "X"
        enumIntrospection.constants[1].stringValue(JsonProperty).get() == "Y"
        enumIntrospection.constants[2].stringValue(JsonProperty).get() == "Z"
        enumIntrospection.constants[3].stringValue(JsonProperty).isEmpty()
    }

    void "test enum bean properties with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read")
public enum Test {

    A(0), B(1), C(2);

    private final int number;

    Test(int number) {
        this.number = number;
    }

    public int readNumber() {
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

    void "test superclass methods are read before interface methods"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;

interface IEmail {
String getEmail();
}
@Introspected
class SuperClass implements IEmail {
    @NotNull
    public String getEmail() {
        return null;
    }
}
@Introspected
class SubClass extends SuperClass {
}
@Introspected
class Test extends SuperClass implements IEmail {
}

''')
        expect:
        introspection != null
        introspection.getProperty("email").isPresent()
        introspection.getIndexedProperties(Constraint).size() == 1
    }

    void "test superclass methods are read before interface methods with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.NotNull;

interface IEmail {
String readEmail();
}
@Introspected
@AccessorsStyle(readPrefixes = "read")
class SuperClass implements IEmail {
    @NotNull
    public String readEmail() {
        return null;
    }
}
@Introspected
class SubClass extends SuperClass {
}
@Introspected
class Test extends SuperClass implements IEmail {
}

''')
        expect:
        introspection != null
        introspection.getProperty("email").isPresent()
        introspection.getIndexedProperties(Constraint).size() == 1
    }

    void "test introspection with single leading lowercase character"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test {

    private final String xForwardedFor;

    Test(String xForwardedFor) {
        this.xForwardedFor = xForwardedFor;
    }

    public String getXForwardedFor() {
        return xForwardedFor;
    }
}
''')

        expect:
        introspection != null

        when:
        def obj = introspection.instantiate("localhost")

        then:
        noExceptionThrown()
        introspection.getProperty("xForwardedFor", String).get().get(obj) == "localhost"
    }

    void "test introspection with single leading lowercase character with custom getter"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read")
class Test {

    private final String xForwardedFor;

    Test(String xForwardedFor) {
        this.xForwardedFor = xForwardedFor;
    }

    public String readXForwardedFor() {
        return xForwardedFor;
    }
}
''')

        expect:
        introspection != null

        when:
        def obj = introspection.instantiate("localhost")

        then:
        noExceptionThrown()
        introspection.getProperty("xForwardedFor", String).get().get(obj) == "localhost"
    }

    void "test introspection on abstract class"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
abstract class Test {
    private String name;
    private String author;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test introspection on abstract class with custom getter"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read")
abstract class Test {
    private String name;
    private String author;

    public String readName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String readAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test targeting abstract class with @Introspected(classes = "() {
        ClassLoader classLoader = buildClassLoader("test.Test", """
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = {io.micronaut.inject.visitor.beans.TestClass.class})
class MyConfig {

}
""")

        BeanIntrospection beanIntrospection = classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_TestClass$Introspection').newInstance()

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test targeting abstract class with @Introspected(classes = ) with custom getter"() {
        ClassLoader classLoader = buildClassLoader(TestCustomGetterClass.name, """
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected(classes = {io.micronaut.inject.visitor.beans.TestCustomGetterClass.class})
class MyConfig {

}
""")
        BeanIntrospection beanIntrospection = classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_TestCustomGetterClass$Introspection').newInstance()

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test introspection on abstract class with extra getter"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
abstract class Test {
    private String name;
    private String author;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getAge() {
        return 0;
    }
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 3
    }

    void "test introspection on abstract class with extra getter and custom getter/setter"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read")
abstract class Test {
    private String name;
    private String author;

    public String readName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String readAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int readAge() {
        return 0;
    }
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 3
    }

    void "test class loading is not shared between the introspection and the ref"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import io.micronaut.core.annotation.Introspected;

import java.util.Set;

@Introspected(excludedAnnotations = Deprecated.class)
public class Test {

    private Set<Author> authors;

    public Set<Author> getAuthors() {
        return authors;
    }

    public void setAuthors(Set<Author> authors) {
        this.authors = authors;
    }
}
@Introspected(excludedAnnotations = Deprecated.class)
class Author {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
    }

    void "test annotation on setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class Test {
    public String getFoo() {
        return "bar";
    }

    @JsonProperty
    public void setFoo(String s) {
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.hasAnnotation(JsonProperty)
    }

    void "test annotation on setter with custom setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(writePrefixes = "with")
public class Test {
    public String getFoo() {
        return "bar";
    }

    @JsonProperty
    public void withFoo(String s) {
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.hasAnnotation(JsonProperty)
    }

    void "test annotation on field"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class Test {
    @JsonProperty
    String foo;

    public String getFoo() {
        return foo;
    }

    public void setFoo(String s) {
        this.foo = s;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.hasAnnotation(JsonProperty)
    }

    void "test annotation on field with custom getter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(readPrefixes = "read")
public class Test {
    @JsonProperty
    String foo;

    public String readFoo() {
        return foo;
    }

    public void setFoo(String s) {
        this.foo = s;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.hasAnnotation(JsonProperty)
    }

    void "test getter annotation overrides setter and field"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class Test {
    @JsonProperty("field")
    String foo;

    @JsonProperty("getter")
    public String getFoo() {
        return foo;
    }

    @JsonProperty("setter")
    public void setFoo(String s) {
        this.foo = s;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.getAnnotation(JsonProperty).stringValue().get() == 'getter'
    }

    void "test getter annotation overrides setter and field with custom setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(writePrefixes = "with")
public class Test {
    @JsonProperty("field")
    String foo;

    @JsonProperty("getter")
    public String getFoo() {
        return foo;
    }

    @JsonProperty("setter")
    public void withFoo(String s) {
        this.foo = s;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.getAnnotation(JsonProperty).stringValue().get() == 'getter'
    }

    void "test field annotation overrides setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class Test {
    @JsonProperty("field")
    String foo;

    public String getFoo() {
        return foo;
    }

    @JsonProperty("setter")
    public void setFoo(String s) {
        this.foo = s;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.getAnnotation(JsonProperty).stringValue().get() == 'field'
    }

    void "verify the type of the bean property should be the getter/setter/constructor type, not the private field type."() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", '''\
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class Test {
    private final java.util.ArrayList<String> foo;

    public Test(java.util.List<String> foo) {
        this.foo = null;
    }

    public java.util.List<String> getFoo() {
        return null;
    }

}
''')

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].type == List.class
    }

    void "test field annotation overrides setter with custom setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.*;

@Introspected
@AccessorsStyle(writePrefixes = "with")
public class Test {
    @JsonProperty("field")
    String foo;

    public String getFoo() {
        return foo;
    }

    @JsonProperty("setter")
    public void withFoo(String s) {
        this.foo = s;
    }
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.getAnnotation(JsonProperty).stringValue().get() == 'field'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6756")
    def "covariant property is not read only"() {
        when:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

@io.micronaut.core.annotation.Introspected
class Test implements B {

    private AImpl a;

    @Override
    public AImpl getA() {
        return a;
    }

    public void setA(AImpl a) {
        this.a = a;
    }
}

interface A {}

interface B {
    A getA();
}

class AImpl implements A {
}
''')
        def property = introspection.getProperty("a").get()

        then:
        property.isReadWrite()
    }

    void "test targeting abstract class with @Introspected(classes = ) with getter matching field name"() {
        ClassLoader classLoader = buildClassLoader("test.MyConfig", '''\
package test;
import io.micronaut.core.annotation.Introspected;
@Introspected(classes = {io.micronaut.inject.visitor.beans.TestMatchingGetterClass.class})
class MyConfig {
}''')
        when:
        BeanIntrospection beanIntrospection = classLoader.loadClass('test.$io_micronaut_inject_visitor_beans_TestMatchingGetterClass$Introspection').newInstance()

        then:
        beanIntrospection
        beanIntrospection.beanProperties.size() == 3
        beanIntrospection.propertyNames as List<String> == ["deleted", "updated", "name"]
    }

    void "test records with is in the property name"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''\
package test;

import io.micronaut.core.annotation.Creator;
import java.util.List;
import jakarta.validation.constraints.Min;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

@io.micronaut.core.annotation.Introspected
public record Foo(String name, String isSurname, boolean contains, Boolean purged, boolean isUpdated, Boolean isDeleted) {
}''')
        expect:
        introspection.propertyNames as List<String> == ["name", "isSurname", "contains", "purged", "isUpdated", "isDeleted"]
    }

    void 'test annotation on a generic field argument'() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection('test.Book', '''
package test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import io.micronaut.core.annotation.Introspected;

class Author {
}

@Introspected
class Book {

    @Size(min=2)
    private String name;

    private List<@Valid Author> authors;

    public Book(String name) {
        this.name = name;
        this.authors = null;
    }

    public Book(String name, List<Author> authors) {
        this.name = name;
        this.authors = authors;
    }

    public List<Author> getAuthors() {
        return authors;
    }

    public String getName() {
        return name;
    }
}
''')
        def property =  beanIntrospection.getBeanProperties().first()

        then:
        property.name == "authors"
        property.asArgument().getTypeParameters()[0].annotationMetadata.hasStereotype("jakarta.validation.Valid")
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
    @io.micronaut.inject.visitor.beans.TypeUseRuntimeAnn
    private String name;
    @io.micronaut.inject.visitor.beans.TypeUseClassAnn
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
        nameField.hasStereotype("io.micronaut.inject.visitor.beans.TypeUseRuntimeAnn")
        !secondNameField.hasStereotype(TypeUseClassAnn)
        !secondNameField.hasStereotype("io.micronaut.inject.visitor.beans.TypeUseClassAnn")
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
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.visitor.beans.MySuperclass;

@Introspected
class Test extends MySuperclass {

    private final String name;

    Test(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
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
        BeanIntrospection introspection = buildBeanIntrospection('io.micronaut.inject.visitor.beans.Test', '''
package io.micronaut.inject.visitor.beans;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test extends MySuperclass {

    private final String name;

    Test(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
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

    void "test private property 1"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.OptionalDoubleHolder', '''
package test;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
import java.util.Collections;
import java.util.OptionalDouble;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class OptionalDoubleHolder {
    @DecimalMin("5")
    private final OptionalDouble optionalDouble;

    private OptionalDoubleHolder(OptionalDouble optionalDouble) {
        this.optionalDouble = optionalDouble;
    }
}
        ''')

        expect:
        introspection.getProperty("optionalDouble").get().getType() == OptionalDouble.class
        introspection.getProperty("optionalDouble").get().hasAnnotation(DecimalMin)
    }

    void "test private property 2"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.OptionalStringHolder', '''
package test;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Collections;
import java.util.Optional;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class OptionalStringHolder {
    private final Optional<@NotBlank String> optionalString;

    private OptionalStringHolder(Optional<String> optionalString) {
        this.optionalString = optionalString;
    }
}
        ''')

        expect:
        introspection.getProperty("optionalString").get().getType() == Optional.class
        introspection.getProperty("optionalString").get().asArgument().getFirstTypeVariable().get().getAnnotationMetadata().hasAnnotation(NotBlank)

    }

    void "test massive dispatch"() {
        given:
        int count = 120
        List<String> fieldNames = IntStream.range(0, count).mapToObj(i -> "f$i").toList()
        BeanIntrospection introspection = buildBeanIntrospection('test.Massive', """
package test;
import io.micronaut.core.annotation.Introspected;

@Introspected
class Massive {
    ${fieldNames.stream().map(f -> "private final String $f;").collect(Collectors.joining("\n"))}

    Massive(${fieldNames.stream().map(f -> "String $f").collect(Collectors.joining(", "))}) {
        ${fieldNames.stream().map(f -> "this.$f = $f;").collect(Collectors.joining("\n"))}
    }

    ${fieldNames.stream().map(f -> "public String get${f.capitalize()}() { return $f; }").collect(Collectors.joining("\n"))}
}""")

        expect:
        introspection.getBeanProperties().size() == count
    }

    void "test massive dispatch with annotations"() {
        given:
        int count = 120
        List<String> fieldNames = IntStream.range(0, count).mapToObj(i -> "f$i").toList()
        BeanIntrospection introspection = buildBeanIntrospection('test.Massive', """
package test;
import io.micronaut.core.annotation.Introspected;

@Introspected
class Massive {
    ${fieldNames.stream().map(f -> "@io.micronaut.core.annotation.NextMajorVersion(\"$f\") private final String $f;").collect(Collectors.joining("\n"))}

    Massive(${fieldNames.stream().map(f -> "String $f").collect(Collectors.joining(", "))}) {
        ${fieldNames.stream().map(f -> "this.$f = $f;").collect(Collectors.joining("\n"))}
    }

    ${fieldNames.stream().map(f -> "public String get${f.capitalize()}() { return $f; }").collect(Collectors.joining("\n"))}
}""")

        expect:
        introspection.getBeanProperties().size() == count
    }

    void "test massive dispatch and executable methods with annotations"() {
        given:
        int propsCount = 100
        int methodsCount = 1000
        List<String> methods = IntStream.range(0, methodsCount).mapToObj(i -> "f$i").toList()
        List<String> fieldNames = IntStream.range(0, propsCount).mapToObj(i -> "f$i").toList()
        BeanIntrospection introspection = buildBeanIntrospection('test.Massive', """
package test;
import io.micronaut.core.annotation.Introspected;

@Introspected
class Massive {
    ${fieldNames.stream().map(f -> "@io.micronaut.core.annotation.NextMajorVersion(\"$f\") private final String $f;").collect(Collectors.joining("\n"))}

    Massive(${fieldNames.stream().map(f -> "String $f").collect(Collectors.joining(", "))}) {
        ${fieldNames.stream().map(f -> "this.$f = $f;").collect(Collectors.joining("\n"))}
    }

    ${fieldNames.stream().map(f -> "public String get${f.capitalize()}() { return $f; }").collect(Collectors.joining("\n"))}

    ${methods.stream().map(m -> "@io.micronaut.core.annotation.NextMajorVersion(\"$m\") @io.micronaut.context.annotation.Executable public void ${m}() {}").collect(Collectors.joining("\n"))}
}""")

        expect:
        introspection.getBeanProperties().size() == propsCount
        introspection.getBeanMethods().size() == methodsCount
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10647")
    void 'handles generic definitions as generated by protobuf'() {
        when:
        buildBeanIntrospection('test.MyMessage', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.visitor.beans.Message;

@Introspected
class MyMessage extends Message {
}
''')
        then:
        noExceptionThrown()
    }

    void "test package"() {
        given:
            ApplicationContext applicationContext = buildContext('''
@Introspected
@AccessorsStyle(readPrefixes = "", writePrefixes = "")
package test;


import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;

''','test.FoobarPerson', '''
package test;

class FoobarPerson {

    private String name;
    private int age;

    public FoobarPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String name() { // <2>
        return name;
    }

    public void name(String name) { // <2>
        this.name = name;
    }

    public int age() { // <2>
        return age;
    }

    public void age(int age) { // <2>
        this.age = age;
    }
}
''')

        when:"the reference is loaded"
            def clazz = applicationContext.classLoader.loadClass('test.$FoobarPerson$Introspection')
            BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
            reference != null

        cleanup:
            applicationContext.close()
    }

    void "test sub package"() {
        given:
            ApplicationContext applicationContext = buildContext(
                    new JavaFiles()
.add("package-info", '''
@Introspected
@AccessorsStyle(readPrefixes = "", writePrefixes = "")
package test;


import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.Introspected;

''')
.add('test.FoobarPerson', '''
package test;

class FoobarPerson {

    private String name;
    private int age;

    public FoobarPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String name() { // <2>
        return name;
    }

    public void name(String name) { // <2>
        this.name = name;
    }

    public int age() { // <2>
        return age;
    }

    public void age(int age) { // <2>
        this.age = age;
    }
}
''')
.add('test.test.AbcPerson', '''
package test.test;

class AbcPerson {

    private String name;
    private int age;

    public AbcPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String name() { // <2>
        return name;
    }

    public void name(String name) { // <2>
        this.name = name;
    }

    public int age() { // <2>
        return age;
    }

    public void age(int age) { // <2>
        this.age = age;
    }
}
''')
            )

        when: "the reference is loaded"
            def clazz = applicationContext.classLoader.loadClass('test.$FoobarPerson$Introspection')

        then: "The reference is valid"
            clazz.newInstance()

        when: "the reference is not loaded"
            applicationContext.classLoader.loadClass('test.test.$AbcPerson$Introspection')

        then: "The reference is valid"
            thrown(ClassNotFoundException)

        cleanup:
            applicationContext.close()
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
        @NonNull
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new ValidationVisitor(), new ConfigurationReaderVisitor(), new io.micronaut.validation.visitor.IntrospectedValidationIndexesVisitor(), new IntrospectedTypeElementVisitor()]
        }
    }

    @Singleton
    @Replaces(BeanIntrospectionModule)
    @io.micronaut.context.annotation.Requires(property = "bean.introspection.test")
    static class StaticBeanIntrospectionModule extends BeanIntrospectionModule {
        Map<Class<?>, BeanIntrospection> introspectionMap = [:]
        @Override
        protected BeanIntrospection<Object> findIntrospection(Class<?> beanClass) {
            return introspectionMap.get(beanClass)
        }
    }
}

