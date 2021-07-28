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
package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class InterfaceExtendsSpec extends AbstractTypeElementSpec {

    void "test interface extends one"() {
        when:
            BeanDefinition beanDefinition = buildBeanDefinition('test.$GameStateRepository' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.compile.Stub2;
import io.micronaut.core.annotation.NonNull;
import java.util.UUID;
import java.util.Optional;

class GameState {

    UUID id;

    String name;

}

@Stub2
interface CrudRepository<E, ID> {
    Optional<E> findById(@NonNull ID id);
}

@Stub2
interface GameStateRepository extends CrudRepository<GameState, UUID> {

    @Override
    @NonNull
    Optional<GameState> findById(@NonNull UUID id);
}

''')
        then:
            def methods = beanDefinition.getExecutableMethods();
            methods.size() == 2
            methods[0].annotationMetadata.hasDeclaredAnnotation(Marker.class)
            !methods[1].annotationMetadata.hasDeclaredAnnotation(Marker.class)
    }

    void "test interface extends two"() {
        when:
            BeanDefinition beanDefinition = buildBeanDefinition('test.$H2GameStateRepository' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.compile.Stub2;
import io.micronaut.core.annotation.NonNull;
import java.util.UUID;
import java.util.Optional;

class GameState {

    UUID id;

    String name;

}

@Stub2
interface CrudRepository<E, ID> {
    Optional<E> findById(@NonNull ID id);
}

@Stub2
interface GameStateRepository extends CrudRepository<GameState, UUID> {

    @Override
    @NonNull
    Optional<GameState> findById(@NonNull UUID id);
}

@Stub2
interface H2GameStateRepository extends GameStateRepository {
}

''')
        then:
            def methods = beanDefinition.getExecutableMethods();
            methods.size() == 2
            methods[0].annotationMetadata.hasAnnotation(Marker.class)
            !methods[1].annotationMetadata.hasDeclaredAnnotation(Marker.class)
    }

}
