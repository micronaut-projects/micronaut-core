/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.inject.annotation.AnnotatedElementValidator;
import io.micronaut.inject.visitor.TypeElementVisitor;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Utility methods for annotations.
 *
 * @author Graeme Rocher
 * @author Dean Wette
 */
@SuppressWarnings("ConstantName")
@Internal
public class AnnotationUtils {

    private final Elements elementUtils;
    private final Messager messager;
    private final Types types;
    private final ModelUtils modelUtils;
    private final Filer filer;
    private final MutableConvertibleValues<Object> visitorAttributes;
    private final ProcessingEnvironment processingEnv;
    private final AnnotatedElementValidator elementValidator;
    private final GenericUtils genericUtils;

    /**
     * Default constructor.
     *
     * @param processingEnv     The processing env
     * @param elementUtils      The elements
     * @param messager          The messager
     * @param types             The types
     * @param modelUtils        The model utils
     * @param genericUtils      The generic utils
     * @param filer             The filer
     * @param visitorAttributes The visitor attributes
     */
    protected AnnotationUtils(
            ProcessingEnvironment processingEnv,
            Elements elementUtils,
            Messager messager,
            Types types,
            ModelUtils modelUtils,
            GenericUtils genericUtils,
            Filer filer,
            MutableConvertibleValues<Object> visitorAttributes) {
        this.elementUtils = elementUtils;
        this.messager = messager;
        this.types = types;
        this.modelUtils = modelUtils;
        this.genericUtils = genericUtils;
        this.filer = filer;
        this.visitorAttributes = visitorAttributes;
        this.processingEnv = processingEnv;
        this.elementValidator = SoftServiceLoader.load(AnnotatedElementValidator.class).firstAvailable().orElse(null);
    }

    /**
     * Default constructor.
     *
     * @param processingEnv     The processing env
     * @param elementUtils      The elements
     * @param messager          The messager
     * @param types             The types
     * @param modelUtils        The model utils
     * @param genericUtils      The generic utils
     * @param filer             The filer
     */
    protected AnnotationUtils(
            ProcessingEnvironment processingEnv,
            Elements elementUtils,
            Messager messager,
            Types types,
            ModelUtils modelUtils,
            GenericUtils genericUtils,
            Filer filer) {
        this(processingEnv, elementUtils, messager, types, modelUtils, genericUtils, filer, new MutableConvertibleValuesMap<>());
    }

    /**
     * The {@link AnnotatedElementValidator} instance. Can be null.
     * @return The validator instance
     */
    public @Nullable AnnotatedElementValidator getElementValidator() {
        return elementValidator;
    }

    /**
     * Creates a new annotation builder.
     *
     * @return The builder
     */
    public JavaAnnotationMetadataBuilder newAnnotationBuilder() {
        return new JavaAnnotationMetadataBuilder(
                elementUtils,
                messager,
                this,
                modelUtils
        );
    }

    /**
     * Creates a new {@link JavaVisitorContext}.
     *
     * @return The visitor context
     */
    public JavaVisitorContext newVisitorContext() {
        return new JavaVisitorContext(
                processingEnv,
                messager,
                elementUtils,
                this,
                types,
                modelUtils,
                genericUtils,
                filer,
                visitorAttributes,
                TypeElementVisitor.VisitorKind.ISOLATING
        );
    }

}
