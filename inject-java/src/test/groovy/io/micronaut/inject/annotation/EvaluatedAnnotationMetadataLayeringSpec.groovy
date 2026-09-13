/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ExecutableMethod

import java.lang.annotation.Annotation
import java.util.concurrent.TimeUnit

/**
 * An annotation that a method declares has to be read from the method, even when an unrelated annotation on the
 * same method carries an evaluated expression and the metadata is therefore wrapped in an
 * {@link EvaluatedAnnotationMetadata}.
 */
class EvaluatedAnnotationMetadataLayeringSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.inject.annotation.ExpressionCarrier;
import io.micronaut.inject.annotation.Zone;
import jakarta.inject.Singleton;

@Singleton
@Zone("class")
class Zoned {

    @Zone
    @ExpressionCarrier(VALUE)
    @Executable
    void defaulted() {
    }

    @Zone(value = "method", priority = 3, timeout = 4L, ratio = 0.5, enabled = true, tags = {"a", "b"},
          unit = java.util.concurrent.TimeUnit.MINUTES, units = {java.util.concurrent.TimeUnit.HOURS},
          type = String.class, types = {Integer.class})
    @ExpressionCarrier(VALUE)
    @Executable
    void declared() {
    }
}
'''

    void "test the method layer answers when an unrelated annotation carries an expression"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE.replace('VALUE', '"#{ 1 + 1 }"'))
        Class<? extends Annotation> zone = Zone
        Class<? extends Annotation> carrier = ExpressionCarrier
        AnnotationMetadata metadata = executableMethod(ctx, 'defaulted').getAnnotationMetadata()
        AnnotationMetadata unwrapped = metadata.getTargetAnnotationMetadata()

        expect: "the metadata is wrapped because of the unrelated expression"
        metadata instanceof EvaluatedAnnotationMetadata
        unwrapped instanceof AnnotationMetadataHierarchy

        and: "the annotation is synthesized from the method, which leaves the member to its default"
        metadata.synthesize(zone).value() == 'default'

        and: "every read answers what the unwrapped hierarchy answers"
        metadata.synthesize(zone).value() == unwrapped.synthesize(zone).value()
        metadata.synthesizeDeclared(zone).value() == unwrapped.synthesizeDeclared(zone).value()
        metadata.stringValue(zone) == unwrapped.stringValue(zone)
        metadata.getValue(zone, String) == unwrapped.getValue(zone, String)
        metadata.getValues(zone, String).get('value') == unwrapped.getValues(zone, String).get('value')
        metadata.getAnnotation(zone).stringValue() == unwrapped.getAnnotation(zone).stringValue()
        metadata.findAnnotation(zone).get().stringValue() == unwrapped.findAnnotation(zone).get().stringValue()
        metadata.getDeclaredAnnotation(zone).stringValue() == unwrapped.getDeclaredAnnotation(zone).stringValue()

        and: "the expression of the unrelated annotation is still evaluated"
        metadata.stringValue(carrier).get() == '2'
        metadata.synthesize(carrier).value() == '2'

        cleanup:
        ctx.close()
    }

    void "test a member declared on the method is read from the method when an expression is present"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE.replace('VALUE', '"#{ 1 + 1 }"'))
        Class<? extends Annotation> zone = Zone
        AnnotationMetadata metadata = executableMethod(ctx, 'declared').getAnnotationMetadata()
        AnnotationMetadata unwrapped = metadata.getTargetAnnotationMetadata()

        expect:
        metadata instanceof EvaluatedAnnotationMetadata
        metadata.synthesize(zone).value() == 'method'
        metadata.stringValue(zone).get() == 'method'
        metadata.getValue(zone, String).get() == 'method'
        metadata.getAnnotation(zone).stringValue().get() == 'method'

        and: "the numeric reads resolve the requested member, not the value member"
        metadata.intValue(zone, 'priority').asInt == 3
        metadata.longValue(zone, 'timeout').asLong == 4L
        metadata.intValue(zone, 'priority') == unwrapped.intValue(zone, 'priority')
        metadata.longValue(zone, 'timeout') == unwrapped.longValue(zone, 'timeout')

        and: "the other member reads resolve the method's values, as the unwrapped hierarchy does"
        metadata.doubleValue(zone, 'ratio').asDouble == 0.5d
        metadata.doubleValue(zone.name, 'ratio') == unwrapped.doubleValue(zone.name, 'ratio')
        metadata.booleanValue(zone, 'enabled').get()
        metadata.booleanValue(zone, 'enabled') == unwrapped.booleanValue(zone, 'enabled')
        metadata.isTrue(zone, 'enabled')
        metadata.stringValues(zone, 'tags') as List == ['a', 'b']
        metadata.stringValues(zone, 'tags') as List == unwrapped.stringValues(zone, 'tags') as List
        metadata.enumValue(zone, 'unit', TimeUnit).get() == TimeUnit.MINUTES
        metadata.enumValue(zone, 'unit', TimeUnit) == unwrapped.enumValue(zone, 'unit', TimeUnit)
        metadata.enumValues(zone, 'units', TimeUnit) as List == [TimeUnit.HOURS]
        metadata.classValue(zone, 'type').get() == String
        metadata.classValue(zone, 'type') == unwrapped.classValue(zone, 'type')
        metadata.classValues(zone, 'types') as List == [Integer]
        metadata.synthesize(zone, zone.name).value() == 'method'

        cleanup:
        ctx.close()
    }

    void "test the same reads without any expression, so without the wrapper"() {
        given:
        ApplicationContext ctx = buildContext(SOURCE.replace('VALUE', '"plain"'))
        Class<? extends Annotation> zone = Zone
        AnnotationMetadata defaulted = executableMethod(ctx, 'defaulted').getAnnotationMetadata()
        AnnotationMetadata declared = executableMethod(ctx, 'declared').getAnnotationMetadata()

        expect: "there is no wrapper"
        !(defaulted instanceof EvaluatedAnnotationMetadata)
        defaulted instanceof AnnotationMetadataHierarchy

        and: "the method layer answers the synthesized annotation"
        defaulted.synthesize(zone).value() == 'default'
        declared.synthesize(zone).value() == 'method'

        and: "a member the method does not declare is resolved from the class layer by the value reads, which \
fall through the hierarchy one element at a time, and by findAnnotation, which merges the layers"
        defaulted.stringValue(zone).get() == 'class'
        defaulted.getAnnotation(zone).stringValue().get() == 'class'

        cleanup:
        ctx.close()
    }

    private static ExecutableMethod<?, ?> executableMethod(ApplicationContext ctx, String name) {
        Class<?> beanType = ctx.classLoader.loadClass('test.Zoned')
        ctx.getBean(beanType)
        return ctx.getBeanDefinition(beanType).getRequiredMethod(name)
    }
}
