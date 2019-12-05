package io.micronaut.inject.factory.nullreturn;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

public class Test1 {

        public static final AnnotationMetadata $ANNOTATION_METADATA = new DefaultAnnotationMetadata(null, null, null, null, null);

    static {
        if (!DefaultAnnotationMetadata.areAnnotationDefaultsRegistered("io.micronaut.context.annotation.Requires")) {
            DefaultAnnotationMetadata.registerAnnotationDefaults(new AnnotationClassValue<Object>("Requires"), AnnotationUtil.internMapOf(new Object[]{"condition", new AnnotationClassValue<Object>("Requires"), "beans", new Object[0], "notEnv", new Object[0], "resources", new Object[0], "env", new Object[0], "entities", new Object[0], "missingConfigurations", new Object[0], "missing", new Object[0], "missingBeans", new Object[0], "classes", new Object[0], "missingClasses", new Object[0], "sdk", "MICRONAUT"}));
        }

    }
}
