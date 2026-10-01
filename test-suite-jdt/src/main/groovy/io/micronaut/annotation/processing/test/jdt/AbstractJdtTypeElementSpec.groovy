/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test.jdt

import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.visitor.TypeElementVisitor

/**
 * Variant of {@link AbstractTypeElementSpec} that compiles the sources under test with the
 * Eclipse JDT compiler instead of javac. Every helper inherited from the superclass
 * ({@code buildBeanDefinition}, {@code buildBeanIntrospection}, {@code buildContext}, ...)
 * therefore exercises the annotation processors against JDT's {@code javax.lang.model}
 * implementation.
 *
 * <p>The {@code buildClassElement}/{@code buildTypeElement} helpers are <b>not</b> supported here,
 * because they rely on the javac specific {@code JavacTask} parse/analyze phases.</p>
 *
 * @since 5.2.0
 */
abstract class AbstractJdtTypeElementSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        def visitors = getLocalTypeElementVisitors()
        if (visitors) {
            return new JdtParser() {
                @Override
                protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                    return new TypeElementVisitorProcessor() {
                        @NonNull
                        @Override
                        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                            return visitors
                        }
                    }
                }

                @Override
                protected AggregatingTypeElementVisitorProcessor getAggregatingTypeElementVisitorProcessor() {
                    return new AggregatingTypeElementVisitorProcessor() {
                        @NonNull
                        @Override
                        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
                            return visitors
                        }
                    }
                }
            }
        }
        return new JdtParser()
    }
}
