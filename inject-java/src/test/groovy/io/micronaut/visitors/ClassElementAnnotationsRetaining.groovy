package io.micronaut.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PropertyElement
/**
 * This spec puts annotations in different places on properties and verifies that
 * the annotations are present in the ClassElement.
 */
class ClassElementAnnotationsRetaining extends AbstractTypeElementSpec {

    void 'test type argument annotation on the property without a setter'() {
        given:
            // Put an annotation on the property type argument
            // Do not define a setter
            def code = '''
package test;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.micronaut.core.annotation.Introspected;
@Introspected
class SaladWithSetter {
    List<@Valid Ingredient> ingredients;
    public List<Ingredient> getIngredients() {
        return ingredients;
    }
    @Introspected
    public record Ingredient(
        @NotBlank String name
    ) {}
}
'''

        when:
            ClassElement classElement = buildClassElement(code)
            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()

        then:
            def propertyTypeArgument = propertyElement.type.typeArguments.get("E")
            propertyTypeArgument.annotationMetadata.hasStereotype("jakarta.validation.Valid")
    }

    void 'test type argument annotation on the getter'() {
        given:
            // Put an annotation on the property type argument
            // Do not define a setter
            def code = '''
package test;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.micronaut.core.annotation.Introspected;
@Introspected
class SaladWithSetter {
    List<Ingredient> ingredients;
    public List<@Valid Ingredient> getIngredients() {
        return ingredients;
    }
    @Introspected
    public record Ingredient(
        @NotBlank String name
    ) {}
}
'''

        when:
            ClassElement classElement = buildClassElement(code)
            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()

        then:
            def propertyTypeArgument = propertyElement.type.typeArguments.get("E")
            propertyTypeArgument.annotationMetadata.hasStereotype("jakarta.validation.Valid")
    }

    void 'test type argument annotation on the setter'() {
        given:
            // Put an annotation on the setter type argument
            def code = '''
package test;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.micronaut.core.annotation.Introspected;
@Introspected
class SaladWithSetter {
    List<Ingredient> ingredients;
    public List<Ingredient> getIngredients() {
        return ingredients;
    }
    public void setIngredients(List<@Valid Ingredient> ingredients) {
        this.ingredients = ingredients;
    }
    @Introspected
    public record Ingredient(
        @NotBlank String name
    ) {}
}
'''

        when:
            ClassElement classElement = buildClassElement(code)
            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()

        then:
            def propertyTypeArgument = propertyElement.type.typeArguments.get("E")
            propertyTypeArgument.annotationMetadata.hasStereotype("jakarta.validation.Valid")
    }

    void 'test type argument annotation on the property with a setter'() {
        given:
            // Put an annotation on the property type argument
            // Define a setter
            def code = '''
package test;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.micronaut.core.annotation.Introspected;
@Introspected
class SaladWithSetter {
    List<@Valid Ingredient> ingredients;
    public List<Ingredient> getIngredients() {
        return ingredients;
    }
    public void setIngredients(List<Ingredient> ingredients) {
        this.ingredients = ingredients;
    }
    @Introspected
    public record Ingredient(
        @NotBlank String name
    ) {}
}
'''

        when:
            ClassElement classElement = buildClassElement(code)
            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()

        then:
            def propertyTypeArgument = propertyElement.type.typeArguments.get("E")
            propertyTypeArgument.annotationMetadata.hasStereotype("jakarta.validation.Valid")
    }

    void 'test annotation on the property with a setter'() {
        given:
            // Put an annotation on the property
            // Define a setter
            def code = '''
package test;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import io.micronaut.core.annotation.Introspected;
@Introspected
class SaladWithSetter {
    @Valid Ingredient ingredient;
    public Ingredient getIngredient() {
        return ingredient;
    }
    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }
    @Introspected
    public record Ingredient(
        @NotBlank String name
    ) {}
}
'''

        when:
            ClassElement classElement = buildClassElement(code)
            PropertyElement propertyElement = classElement.getBeanProperties().iterator().next()

        then:
            propertyElement.annotationMetadata.hasStereotype("jakarta.validation.Valid")
    }

}
