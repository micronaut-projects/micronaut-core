/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.annotation.processing;

import io.micronaut.annotation.processing.visitor.JavaClassElement;
import io.micronaut.annotation.processing.visitor.JavaElementFactory;
import io.micronaut.annotation.processing.visitor.JavaNativeElement;
import io.micronaut.context.annotation.Mixin;
import io.micronaut.context.visitor.VisitorUtils;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Generated;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.ElementPostponedToNextRoundException;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * <p>The annotation processed used to process the mixins first.</p>
 *
 * @author Denis Stepanov
 * @since 4.9
 */
@SupportedOptions({
    AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_INCREMENTAL,
    AbstractInjectAnnotationProcessor.MICRONAUT_PROCESSING_ANNOTATIONS,
    VisitorContext.MICRONAUT_PROCESSING_PROJECT_DIR,
    VisitorContext.MICRONAUT_PROCESSING_GROUP,
    VisitorContext.MICRONAUT_PROCESSING_MODULE
})
public class MixinVisitorProcessor extends AbstractInjectAnnotationProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(Mixin.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!(annotations.size() == 1 && Generated.class.getName().equals(annotations.iterator().next().getQualifiedName().toString()))) {

            for (Object nativeType : postponedTypes.values()) {
                AbstractAnnotationMetadataBuilder.clearMutated(nativeType);
            }

            var elements = new LinkedHashSet<TypeElement>();

            postponedTypes.keySet().stream().map(elementUtils::getTypeElement).filter(Objects::nonNull).forEach(elements::add);
            postponedTypes.clear();

            JavaElementFactory elementFactory = javaVisitorContext.getElementFactory();
            JavaElementAnnotationMetadataFactory elementAnnotationMetadataFactory = javaVisitorContext.getElementAnnotationMetadataFactory();

            for (TypeElement annotation : annotations) {
                modelUtils.resolveTypeElements(
                    roundEnv.getElementsAnnotatedWith(annotation)
                ).forEach(elements::add);
            }

            List<JavaClassElement> javaClassElements = elements.stream()
                .map(typeElement -> elementFactory.newSourceClassElement(typeElement, elementAnnotationMetadataFactory))
                .toList();

            for (JavaClassElement mixin : javaClassElements) {
                try {
                    AnnotationValue<Mixin> mixinAnnotation = mixin.getAnnotation(Mixin.class);
                    if (mixinAnnotation == null) {
                        continue;
                    }
                    String target = mixinAnnotation.stringValue("target").orElse(mixinAnnotation.stringValue().orElse(null));
                    if (target == null || Object.class.getName().equals(target)) {
                        continue;
                    }
                    TypeElement targetTypeElement = elementUtils.getTypeElement(target);
                    if (targetTypeElement == null) {
                        continue;
                    }
                    JavaClassElement mixinTarget = elementFactory.newSourceClassElement(targetTypeElement, elementAnnotationMetadataFactory);
                    VisitorUtils.applyMixin(mixinAnnotation, mixin, mixinTarget);
                } catch (ProcessingException e) {
                    var originatingElement = (JavaNativeElement) e.getOriginatingElement();
                    if (originatingElement == null) {
                        originatingElement = mixin.getNativeType();
                    }
                    error(originatingElement.element(), e.getMessage());
                } catch (PostponeToNextRoundException e) {
                    postponedTypes.put(mixin.getCanonicalName(), e.getNativeErrorElement());
                } catch (ElementPostponedToNextRoundException e) {
                    Object nativeType = e.getOriginatingElement().getNativeType();
                    Element element = PostponeToNextRoundException.resolvedFailedElement(nativeType);
                    if (element != null) {
                        postponedTypes.put(mixin.getCanonicalName(), element);
                    } else {
                        // should never happen.
                        throw e;
                    }
                }
            }
        }

        if (roundEnv.processingOver()) {
            javaVisitorContext.finish();
        }
        return false;
    }

}
