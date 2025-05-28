package io.micronaut.visitors

import com.fasterxml.jackson.annotation.JacksonAnnotation
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class MixinSpec extends AbstractTypeElementSpec {

    void 'test field'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;

class MyBean {
    String name;
}

@Mixin(MyBean.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    @JsonProperty("hello")
    String name;
}

''')
        then:
            myBean.getProperty("name").get().stringValue(JsonProperty).get() == "hello"
    }

    void 'test method'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;

class MyBean {
    String name;
    public String getXyz() {
        return name;
    }
}

@Mixin(MyBean.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    String name;

    @Executable
    @JsonProperty("hello")
    @NotNull
    public String getXyz() {
        return name;
    }
}

''')

            def metadata = myBean.getBeanMethods().iterator().next().getAnnotationMetadata().declaredMetadata
        then:
            metadata.stringValue(JsonProperty).get() == "hello"
            metadata.getAnnotationNameByStereotype(JacksonAnnotation.name).get() == JsonProperty.name
            metadata.annotationNames.sort() == [
                    "com.fasterxml.jackson.annotation.JsonProperty",
                    "io.micronaut.context.annotation.Executable",
                    'jakarta.validation.constraints.NotNull$List'
            ]
    }

    void 'test method excludeAnnotations'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;

class MyBean {
    String name;
    public String getXyz() {
        return name;
    }
}

@Mixin(value = Object.class, target = "mixin.MyBean", excludeAnnotations = "jakarta.validation")
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    String name;

    @Executable
    @JsonProperty("hello")
    @NotNull
    public String getXyz() {
        return name;
    }
}

''')

            def metadata = myBean.getBeanMethods().iterator().next().getAnnotationMetadata().declaredMetadata
        then:
            metadata.stringValue(JsonProperty).get() == "hello"
            metadata.getAnnotationNameByStereotype(JacksonAnnotation.name).get() == JsonProperty.name
            metadata.annotationNames.sort() == [
                    "com.fasterxml.jackson.annotation.JsonProperty",
                    "io.micronaut.context.annotation.Executable"
            ]
    }

    void 'test method includeAnnotations'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;

class MyBean {
    String name;
    public String getXyz() {
        return name;
    }
}

@Mixin(value = MyBean.class, includeAnnotations = {
        "io.micronaut", "com.fasterxml"
})
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    String name;

    @Executable
    @JsonProperty("hello")
    @NotNull
    public String getXyz() {
        return name;
    }
}

''')

            def metadata = myBean.getBeanMethods().iterator().next().getAnnotationMetadata().declaredMetadata
        then:
            metadata.stringValue(JsonProperty).get() == "hello"
            metadata.getAnnotationNameByStereotype(JacksonAnnotation.name).get() == JsonProperty.name
            metadata.annotationNames.sort() == [
                    "com.fasterxml.jackson.annotation.JsonProperty",
                    "io.micronaut.context.annotation.Executable"
            ]
    }

    void 'test method includeAnnotations 2'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;

class MyBean {
    String name;
    public String getXyz() {
        return name;
    }
}

@Mixin(MyBean.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    String name;

    @Mixin.Filter(includeAnnotations = {"io.micronaut", "com.fasterxml"})
    @Executable
    @JsonProperty("hello")
    @NotNull
    public String getXyz() {
        return name;
    }
}

''')

            def metadata = myBean.getBeanMethods().iterator().next().getAnnotationMetadata().declaredMetadata
        then:
            metadata.stringValue(JsonProperty).get() == "hello"
            metadata.getAnnotationNameByStereotype(JacksonAnnotation.name).get() == JsonProperty.name
            metadata.annotationNames.sort() == [
                    "com.fasterxml.jackson.annotation.JsonProperty",
                    "io.micronaut.context.annotation.Executable"
            ]
    }

    void 'test method removeAnnotations'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.constraints.NotNull;

class MyBean {
    String name;
    @NotNull
    public String getXyz() {
        return name;
    }
}

@Mixin(MyBean.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    String name;

    @Mixin.Filter(removeAnnotations = "jakarta.validation")
    @Executable
    @JsonProperty("hello")
    public String getXyz() {
        return name;
    }
}

''')

            def metadata = myBean.getBeanMethods().iterator().next().getAnnotationMetadata().declaredMetadata
        then:
            metadata.stringValue(JsonProperty).get() == "hello"
            metadata.getAnnotationNameByStereotype(JacksonAnnotation.name).get() == JsonProperty.name
            metadata.annotationNames.sort() == [
                    "com.fasterxml.jackson.annotation.JsonProperty",
                    "io.micronaut.context.annotation.Executable"
            ]
    }

    void 'test method param'() {
        when:
            def myBean = buildBeanIntrospection('mixin.MyBean', '''
package mixin;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.core.annotation.Introspected;

class MyBean {
    String name;
    public String getXyz(int i) {
        return name;
    }
}

@Mixin(MyBean.class)
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class MyBeanMixin {
    String name;

    @Executable
    public String getXyz(@JsonProperty("hello") int i) {
        return name;
    }
}

''')
        then:
            myBean.getBeanMethods().iterator().next().getArguments()[0].getAnnotationMetadata().stringValue(JsonProperty).get() == "hello"
    }

}
