/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.aop.introduction.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class IntroducedWithRepeatableAnnotationSpec extends AbstractTypeElementSpec {

    void "test annotation is repeatable after added annotation"() {
        given:
            def context = buildContext("""
package test;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;
import java.lang.annotation.*;
import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.NonNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
@interface MyDataMethod {
}

@Repeatable(MyRepContainer.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
@interface MyRep {
    /**
     * @return Name of the hint.
     **/
    String name();

    /**
     * @return Value of the hint.
     **/
    String value();
}

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Target(ElementType.METHOD)
@interface MyRepContainer {

    MyRep[] value();

}

@Introduction
@Type(MyRepoIntroducer.class)
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
@interface RepoDef {
}


@Singleton
class MyRepoIntroducer implements MethodInterceptor<Object, Object> {

    @Nullable
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return null;
    }
}

interface CrudRepo<E, ID> {

    Optional<E> findById(ID id);

}

interface CrudRepoX<E, ID> extends CrudRepo<E, ID> {

    @MyRep(name = "aa", value = "vv")
    <S extends E> S saveAndFlush(@NonNull @Valid @NotNull S entity);
}

@RepoDef
interface CustomCrudRepo3 extends CrudRepoX<String, Long> {

    @Override
    Optional<String> findById(Long aLong);
}


""")

        when:
            def annClass = context.classLoader.loadClass('test.MyRep')
            def beanDef1 = context.getBeanDefinition(context.classLoader.loadClass("test.CustomCrudRepo3"))
            def method = beanDef1.getRequiredMethod("saveAndFlush", String)
        then:
            beanDef1.getAnnotationMetadata().isRepeatableAnnotation(annClass)
            method.getAnnotationMetadata().isRepeatableAnnotation(annClass)
            method.getAnnotationMetadata().hasAnnotation(annClass)

        cleanup:
            context.close()
    }
}
