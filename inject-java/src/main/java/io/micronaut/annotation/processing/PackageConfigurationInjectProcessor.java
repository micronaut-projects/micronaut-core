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
package io.micronaut.annotation.processing;

import io.micronaut.context.annotation.Configuration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.writer.BeanConfigurationWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleElementVisitor8;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;

/**
 * An annotation processor that generates {@link io.micronaut.inject.BeanConfiguration} implementations for
 * each package annotated with {@link Configuration}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SupportedAnnotationTypes({
    "io.micronaut.context.annotation.Configuration"
})
@Internal
public class PackageConfigurationInjectProcessor extends AbstractInjectAnnotationProcessor {

    @Override
    public final synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton("io.micronaut.context.annotation.Configuration");
    }

    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        AnnotationElementScanner scanner = new AnnotationElementScanner();
        Set<? extends Element> elements = roundEnv.getRootElements();
        ElementFilter.packagesIn(elements).forEach(element -> element.accept(scanner, element));
        try {
            classWriterOutputVisitor.finish();
        } catch (Exception e) {
            error("I/O error occurred writing META-INF services information: %s", e);
        }
        return false;
    }

    /**
     * Class to visit annotation elements annotated with {@link Configuration}.
     */
    class AnnotationElementScanner extends SimpleElementVisitor8<Object, Object> {

        @Override
        public Object visitPackage(PackageElement packageElement, Object p) {
            Object aPackage = super.visitPackage(packageElement, p);
            if (annotationUtils.hasStereotype(packageElement, Configuration.class)) {
                String packageName = packageElement.getQualifiedName().toString();
                BeanConfigurationWriter writer = new BeanConfigurationWriter(
                    packageName,
                    annotationUtils.getAnnotationMetadata(packageElement)
                );
                try {
                    writer.accept(classWriterOutputVisitor);
                } catch (IOException e) {
                    error("I/O error occurred writing Configuration for package [%s]: %s", packageElement, e);
                }
            }
            return aPackage;
        }
    }
}
