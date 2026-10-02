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
package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationUtil
import jakarta.inject.Named

class EnvironmentQualifierMetadataSpec extends AbstractTypeElementSpec {
    void 'environment metadata retains qualifier values selected by stereotype'() {
        given:
        def context = buildContext('test.environmentqualifier.Consumer', '''
            package test.environmentqualifier;
            import jakarta.inject.*;
            import java.lang.annotation.*;
            import io.micronaut.context.annotation.NonBinding;
            @Qualifier @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Configured {
                String name();
                @NonBinding String defaultValue();
            }
            @Singleton class Consumer {
                @Inject @Configured(name="plain", defaultValue="${external.default}") String value;
                @Inject @Named("${bean.name:chosen}") String named;
            }
        ''')
        def definition = context.getBeanDefinition(context.classLoader.loadClass('test.environmentqualifier.Consumer'))
        def metadata = definition.injectedFields.find { it.name == 'value' }.annotationMetadata
        def named = definition.injectedFields.find { it.name == 'named' }.annotationMetadata

        expect:
        metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER) == ['test.environmentqualifier.Configured']
        def qualifiers = metadata.getAnnotationValuesByStereotype(AnnotationUtil.QUALIFIER)
        qualifiers.size() == 1
        qualifiers[0].stringValue('name').get() == 'plain'
        qualifiers[0].values['defaultValue'] == '${external.default}'
        named.getAnnotationValuesByStereotype(AnnotationUtil.QUALIFIER)[0].stringValue().get() == 'chosen'
        named.findAnnotation(Named).get().stringValue().get() == 'chosen'

        cleanup:
        context.close()
    }
}
