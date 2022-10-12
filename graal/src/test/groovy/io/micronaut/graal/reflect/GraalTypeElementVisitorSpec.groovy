package io.micronaut.graal.reflect

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.ReflectionConfig
import io.micronaut.core.annotation.TypeHint
import io.micronaut.core.graal.GraalReflectionConfigurer

class GraalTypeElementVisitorSpec extends AbstractTypeElementSpec {

    void "an @Introspected class doesn't add anything to reflect.json"() {
        when:
        buildReflectionConfigurer( 'test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;

@Introspected
class Test {

}

''')

        then:
        thrown(ClassNotFoundException)
    }

    void "test write reflect.json for @TypeHint with classes"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@TypeHint(Bar.class)
class Test {

}

class Bar {}

''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Bar'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS] as TypeHint.AccessType[]
    }

    void "test write reflect.json for @ReflectionConfig with classes"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@ReflectionConfig(
    type = Bar.class,
    accessType = TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS,
    methods = @ReflectionConfig.ReflectiveMethodConfig(
            name = "foo"
    )
)
class Test {

}

class Bar {}

''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Bar'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS] as TypeHint.AccessType[]
        config.getAnnotations("methods").size() == 1
        config.getAnnotations("methods").first().stringValue("name").get() == 'foo'
    }

    void "test write reflect.json for @TypeHint with classes and type names"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@TypeHint(value = Bar.class, typeNames = "java.lang.String")
class Test {

}

class Bar {}

''')

        when:
        List<AnnotationValue<ReflectionConfig>> configs = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig)

        then:
        configs.size() == 2

        when:
        AnnotationValue<ReflectionConfig> config = configs.find {it.stringValue("type").get() == 'test.Bar' }

        then:
        config
        config.stringValue("type").get() == 'test.Bar'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS] as TypeHint.AccessType[]

        when:
        config = configs.find {it.classValue("type").get() == String }

        then:
        config
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS] as TypeHint.AccessType[]

    }

    void "test write reflect.json for @TypeHint with classes and arrays"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@TypeHint(value = {Bar.class, String[].class})
class Test {

}

class Bar {}

''')

        when:
        def configs = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig)

        then:
        configs.size() == 2

        when:
        def config = configs.find {it.stringValue("type").get() == 'test.Bar' }

        then:
        config
        config.stringValue("type").get() == 'test.Bar'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS] as TypeHint.AccessType[]

        when:
        config = configs.find {it.classValue("type").get() == String[].class }

        then:
        config
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS] as TypeHint.AccessType[]

    }

    void "test write reflect.json for @TypeHint with access type"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

import io.micronaut.core.annotation.*;

@TypeHint(value=Bar.class, accessType = TypeHint.AccessType.ALL_PUBLIC_METHODS)
class Test {

}

class Bar {}

''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Bar'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_PUBLIC_METHODS] as TypeHint.AccessType[]
    }

    void "test write reflect.json for @ReflectiveAccess with access type"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

class Test {

    @ReflectiveAccess
    private String name;

    @ReflectiveAccess
    public String getFoo() {
        return name;
    }
}


''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Test'
        config.enumValues("accessType", TypeHint.AccessType) == [] as TypeHint.AccessType[]
        config.getAnnotations("methods").size() == 1
        config.getAnnotations("methods").first().stringValue("name").get() == 'getFoo'
        config.getAnnotations("fields").size() == 1
        config.getAnnotations("fields").first().stringValue("name").get() == 'name'
    }

    void "test write reflect.json for @Inject on private fields or methods"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.*;

@jakarta.inject.Singleton
class Test {

    @jakarta.inject.Inject
    private String name;

    @jakarta.inject.Inject
    private void setFoo(Other other) {
    }
}

class Other {}
''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Test'
        config.enumValues("accessType", TypeHint.AccessType) == [] as TypeHint.AccessType[]
        config.getAnnotations("methods").size() == 1
        config.getAnnotations("methods").first().stringValue("name").get() == 'setFoo'
        config.getAnnotations("fields").size() == 1
        config.getAnnotations("fields").first().stringValue("name").get() == 'name'
    }

    void "test write reflect.json for @ReflectiveAccess with inheritance"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.HTTPCheck', '''
package test;

import io.micronaut.core.annotation.*;

@Introspected
class HTTPCheck extends NewCheck {

    private String interval;

    @ReflectiveAccess
    protected void setInterval(String interval) {
        this.interval = interval;
    }
}
abstract class NewCheck {

    private String status;

    @ReflectiveAccess
    protected void setStatus(String status) {
        this.status = status;
    }
}


''')

        when:
        // New check is returned first because the methods from the subtype are processed first
        AnnotationValue<ReflectionConfig> httpCheck = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).get(1)
        AnnotationValue<ReflectionConfig> newCheck = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).get(0)

        then:
        httpCheck
        httpCheck.stringValue("type").get() == 'test.HTTPCheck'
        httpCheck.enumValues("accessType", TypeHint.AccessType) == [] as TypeHint.AccessType[]
        httpCheck.getAnnotations("methods").size() == 1
        httpCheck.getAnnotations("methods").first().stringValue("name").get() == 'setInterval'
        httpCheck.getAnnotations("methods").first().classValues("parameterTypes") == [String] as Class[]
        newCheck.stringValue("type").get() == 'test.NewCheck'
        newCheck.enumValues("accessType", TypeHint.AccessType) == [] as TypeHint.AccessType[]
        newCheck.getAnnotations("methods").size() == 1
        newCheck.getAnnotations("methods").first().stringValue("name").get() == 'setStatus'
        newCheck.getAnnotations("methods").first().classValues("parameterTypes") == [String] as Class[]
    }

    void "test write reflect.json for @ReflectiveAccess with classes"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.ReflectiveAccess;

@ReflectiveAccess
class Test {

}
''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Test'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_PUBLIC_METHODS, TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS] as TypeHint.AccessType[]
        config.getAnnotations("methods").first().stringValue("name").get() == '<init>'
    }

    void "test write reflect.json for @Entity with classes"() {

        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;


@javax.persistence.Entity
class Test {


    enum InnerEnum {
        ONE, TWO;
    }
}
''')

        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Test'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_PUBLIC_METHODS, TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS] as TypeHint.AccessType[]
        config.getAnnotations("methods").size() == 1
        config.getAnnotations("methods").first().stringValue("name").get() == '<init>'

        when:
        configurer = buildReflectionConfigurer('test.Test$InnerEnum', '''
package test;


@javax.persistence.Entity
class Test {


    enum InnerEnum {
        ONE, TWO;
    }
}
''')
        config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Test$InnerEnum'

    }

    void "test write reflect.json for @ReflectiveAccess with enums"() {
        given:
        GraalReflectionConfigurer configurer = buildReflectionConfigurer('test.Test', '''
package test;

import io.micronaut.core.annotation.ReflectiveAccess;

@ReflectiveAccess
enum Test {
    A, B
}
''')


        when:
        AnnotationValue<ReflectionConfig> config = configurer.getAnnotationMetadata().getAnnotationValuesByType(ReflectionConfig).first()

        then:
        config
        config.stringValue("type").get() == 'test.Test'
        config.enumValues("accessType", TypeHint.AccessType) == [TypeHint.AccessType.ALL_PUBLIC_METHODS, TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS] as TypeHint.AccessType[]
        config.getAnnotations("methods").size() == 2 // Two methods from Enum

    }
}
