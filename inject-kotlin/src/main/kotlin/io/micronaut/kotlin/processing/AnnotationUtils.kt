package io.micronaut.kotlin.processing

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Internal
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.io.service.ServiceDefinition
import io.micronaut.core.io.service.SoftServiceLoader
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder
import io.micronaut.inject.annotation.AnnotatedElementValidator
import io.micronaut.kotlin.processing.visitor.KotlinVisitorContext

class AnnotationUtils(private val environment: SymbolProcessorEnvironment,
                      private val resolver: Resolver) {

    private val annotationMetadataBuilder = newAnnotationBuilder()
    private var elementValidator: AnnotatedElementValidator? = null

    companion object {
        const val CACHE_SIZE = 100
        val annotationMetadataCache: ConcurrentLinkedHashMap<KSAnnotated, AnnotationMetadata> =
            ConcurrentLinkedHashMap.Builder<KSAnnotated, AnnotationMetadata>().maximumWeightedCapacity(CACHE_SIZE.toLong())
                .build()
    }

    init {
        val validators = SoftServiceLoader.load(AnnotatedElementValidator::class.java)
        val i: Iterator<ServiceDefinition<AnnotatedElementValidator>> = validators.iterator()
        while (i.hasNext()) {
            val validator = i.next()
            if (validator.isPresent) {
                try {
                    elementValidator = validator.load()
                } catch (e: Throwable) {
                    // probably missing required dependencies to load the validator
                }
                break
            }
        }
    }

    /**
     * The [AnnotatedElementValidator] instance. Can be null.
     * @return The validator instance
     */
    @Nullable
    fun getElementValidator(): AnnotatedElementValidator? {
        return elementValidator
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotype.
     *
     * @param element    The element
     * @param stereotype The stereotype
     * @return True if it is
     */
    private fun hasStereotype(element: KSDeclaration?, stereotype: Class<out Annotation>): Boolean {
        return hasStereotype(element, stereotype.name)
    }

    /**
     * Return whether the given element is annotated with the given annotation stereotypes.
     *
     * @param element     The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    private fun hasStereotype(element: KSDeclaration?, vararg stereotypes: String): Boolean {
        return hasStereotype(element, listOf(*stereotypes))
    }

    /**
     * Return whether the given element is annotated with any of the given annotation stereotypes.
     *
     * @param element     The element
     * @param stereotypes The stereotypes
     * @return True if it is
     */
    private fun hasStereotype(element: KSDeclaration?, stereotypes: List<String>): Boolean {
        if (element == null) {
            return false
        }
        if (stereotypes.contains(element.toString())) {
            return true
        }
        val annotationMetadata = getAnnotationMetadata(element)
        for (stereotype in stereotypes) {
            if (annotationMetadata.hasStereotype(stereotype)) {
                return true
            }
        }
        return false
    }

    /**
     * Get the annotation metadata for the given element.
     *
     * @param element The element
     * @return The [AnnotationMetadata]
     */
    fun getAnnotationMetadata(element: KSAnnotated): AnnotationMetadata {
        var metadata: AnnotationMetadata? = annotationMetadataCache[element]
        if (metadata == null) {
            metadata = annotationMetadataBuilder.buildOverridden(element)
            annotationMetadataCache[element] = metadata
        }
        return metadata!!
    }

    /**
     * Get the declared annotation metadata for the given element.
     *
     * @param element The element
     * @return The [AnnotationMetadata]
     */
    fun getDeclaredAnnotationMetadata(element: KSAnnotated): AnnotationMetadata {
        return annotationMetadataBuilder.buildDeclared(element)
    }

    /**
     * Get the annotation metadata for the given element and the given parent.
     * This method is used for cases when you need to combine annotation metadata for
     * two elements, for example a JavaBean property where the field and the method metadata
     * need to be combined.
     *
     * @param parent  The parent
     * @param element The element
     * @return The [AnnotationMetadata]
     */
    fun getAnnotationMetadata(parent: KSAnnotated, element: KSAnnotated): AnnotationMetadata {
        return newAnnotationBuilder().buildForParent(parent, element)
    }

    /**
     * Check whether the method is annotated.
     *
     * @param declaringType The declaring type
     * @param method The method
     * @return True if it is annotated with non internal annotations
     */
    fun isAnnotated(declaringType: String?, method: KSFunctionDeclaration): Boolean {
        if (AbstractAnnotationMetadataBuilder.isMetadataMutated(declaringType, method)) {
            return true
        }
        val annotationMirrors = method.annotations
        for (annotationMirror in annotationMirrors) {
            val typeName = annotationMirror.shortName.getQualifier()
            if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(typeName)) {
                return true
            }
        }
        return false
    }

    /**
     * Creates a new annotation builder.
     *
     * @return The builder
     */
    fun newAnnotationBuilder(): KotlinAnnotationMetadataBuilder {
        return KotlinAnnotationMetadataBuilder()
    }

    /**
     * Creates a new [KotlinVisitorContext].
     *
     * @return The visitor context
     */
    fun newVisitorContext(): KotlinVisitorContext {
        return KotlinVisitorContext(environment, resolver)
    }

    /**
     * Invalidates any cached metadata.
     */
    @Internal
    fun invalidateCache() {
        annotationMetadataCache.clear()
    }

    /**
     * Invalidates any cached metadata.
     *
     * @param element The element
     */
    @Internal
    fun invalidateMetadata(element: KSDeclaration?) {
        if (element != null) {
            annotationMetadataCache.remove(element)
        }
    }
}
