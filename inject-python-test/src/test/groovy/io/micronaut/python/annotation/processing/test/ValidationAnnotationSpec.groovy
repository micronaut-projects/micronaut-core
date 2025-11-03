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
package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement
import jakarta.validation.Constraint
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.intellij.lang.annotations.Language

/**
 * Tests for Jakarta validation annotation processing in Python code.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class ValidationAnnotationSpec extends AbstractPythonTypeElementSpec {

    def "test dataclass attributes with Jakarta validation annotations"() {
        given: "Python dataclass with validation annotations on attributes"
        @Language("python") def pythonCode = '''
from dataclasses import dataclass
from typing import Annotated
from jakarta.validation.constraints import NotBlank, Min, Max, Size

@dataclass
class Product:
    name: Annotated[str, NotBlank] = ""
    price: Annotated[float, Min(0), Max(10000)] = 0.0
    description: Annotated[str, Size(min=10, max=1000)] = ""
    tags: Annotated[list[str], Size(max=10)] = []
'''

        when: "Processing the Python code"
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "Product"

            // Verify Constraint stereotype annotations are available on fields
            def allConstraintAnnotations = []
            element.getFields().each { field ->
                allConstraintAnnotations.addAll(field.getAnnotationValuesByStereotype("jakarta.validation.Constraint"))
            }
            assert allConstraintAnnotations.size() == 5, "Should have 5 constraint annotations total across all fields (NotBlank + Min + Max + 2x Size)"

            // Check individual fields
            def fields = element.getFields()
            assert fields.size() == 4

            // Verify name field has NotBlank constraint
            def nameField = fields.find { it.name == "name" }
            assert nameField != null
            assert nameField.hasAnnotation(NotBlank.class)
            def nameConstraints = nameField.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert nameConstraints.size() == 1
            assert nameConstraints[0].annotationName == NotBlank.class.name

            // Verify price field has Min and Max constraints
            def priceField = fields.find { it.name == "price" }
            assert priceField != null
            assert priceField.hasAnnotation(Min.class)
            assert priceField.hasAnnotation(Max.class)
            def priceConstraints = priceField.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert priceConstraints.size() == 2
            def minConstraint = priceConstraints.find { it.annotationName == Min.class.name }
            def maxConstraint = priceConstraints.find { it.annotationName == Max.class.name }
            assert minConstraint != null
            assert maxConstraint != null

            // Verify Min constraint has correct value
            assert minConstraint.intValue("value").get() == 0

            // Verify Max constraint has correct value
            assert maxConstraint.longValue("value").get() == 10000L

            // Verify description field has Size constraint
            def descField = fields.find { it.name == "description" }
            assert descField != null
            assert descField.hasAnnotation(Size.class)
            def descConstraints = descField.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert descConstraints.size() == 1
            assert descConstraints[0].annotationName == Size.class.name

            // Verify Size constraint has correct min and max values
            assert descConstraints[0].intValue("min").get() == 10
            assert descConstraints[0].intValue("max").get() == 1000

            // Verify tags field has Size constraint
            def tagsField = fields.find { it.name == "tags" }
            assert tagsField != null
            assert tagsField.hasAnnotation(Size.class)
            def tagsConstraints = tagsField.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert tagsConstraints.size() == 1
            assert tagsConstraints[0].annotationName == Size.class.name

            // Verify Size constraint has correct max value (min should be 0 by default)
            assert tagsConstraints[0].intValue("max").get() == 10
            // Min should not be present when not specified (defaults to 0)
            assert !tagsConstraints[0].intValue("min").isPresent()

            return element
        }

        then: "All validation constraints should be properly parsed"
        classElement != null
    }

    def "test singleton bean method parameters and return types with validation annotations"() {
        given: "Python singleton bean with validated method parameters and return types"
        @Language("python") def pythonCode = '''
from jakarta.inject import Singleton
from typing import Annotated
from jakarta.validation.constraints import NotBlank, Min, Max, Size
from jakarta.validation import Valid

@Singleton
class ProductService:
    def create_product(self, name: Annotated[str, NotBlank], price: Annotated[float, Min(0), Max(10000)]) -> Annotated[str, NotBlank]:
        return f"Created product {name}"

    def update_product(self, product_id: Annotated[int, Min(1)], updates: Annotated[dict, Valid]) -> Annotated[bool, NotBlank]:
        return True

    def list_products(self, limit: Annotated[int, Min(1), Max(100)] = 10, offset: Annotated[int, Min(0)] = 0) -> Annotated[list[str], Size(max=100)]:
        return ["product1", "product2"]
'''

        when: "Processing the Python code"
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "ProductService"

            // Get all methods
            def methods = element.getEnclosedElements(io.micronaut.inject.ast.ElementQuery.ALL_METHODS)
            assert methods.size() == 3

            // Test create_product method
            def createMethod = methods.find { it.name == "create_product" }
            assert createMethod != null

            // Check parameters
            def createParams = createMethod.getParameters()
            assert createParams.length == 2

            // Verify name parameter has NotBlank constraint
            def nameParam = createParams[0]
            assert nameParam.name == "name"
            assert nameParam.hasAnnotation(NotBlank.class)
            def nameConstraints = nameParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert nameConstraints.size() == 1
            assert nameConstraints[0].annotationName == NotBlank.class.name

            // Verify price parameter has Min and Max constraints
            def priceParam = createParams[1]
            assert priceParam.name == "price"
            assert priceParam.hasAnnotation(Min.class)
            assert priceParam.hasAnnotation(Max.class)
            def priceConstraints = priceParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert priceConstraints.size() == 2

            // Verify return type has NotBlank constraint
            def createReturnType = createMethod.getReturnType()
            assert createReturnType.hasAnnotation(NotBlank.class)
            def returnConstraints = createReturnType.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert returnConstraints.size() == 1
            assert returnConstraints[0].annotationName == NotBlank.class.name

            // Test update_product method
            def updateMethod = methods.find { it.name == "update_product" }
            assert updateMethod != null

            def updateParams = updateMethod.getParameters()
            assert updateParams.length == 2

            // Verify product_id parameter has Min constraint
            def idParam = updateParams[0]
            assert idParam.name == "product_id"
            assert idParam.hasAnnotation(Min.class)
            def idConstraints = idParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert idConstraints.size() == 1
            assert idConstraints[0].intValue("value").get() == 1

            // Verify updates parameter has Valid constraint
            def updatesParam = updateParams[1]
            assert updatesParam.name == "updates"
            assert updatesParam.hasAnnotation(Valid.class)
            def updatesConstraints = updatesParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert updatesConstraints.size() == 0

            // Test list_products method
            def listMethod = methods.find { it.name == "list_products" }
            assert listMethod != null

            def listParams = listMethod.getParameters()
            assert listParams.length == 2

            // Verify limit parameter has Min and Max constraints
            def limitParam = listParams[0]
            assert limitParam.name == "limit"
            assert limitParam.hasAnnotation(Min.class)
            assert limitParam.hasAnnotation(Max.class)
            def limitConstraints = limitParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert limitConstraints.size() == 2

            // Verify offset parameter has Min constraint
            def offsetParam = listParams[1]
            assert offsetParam.name == "offset"
            assert offsetParam.hasAnnotation(Min.class)
            def offsetConstraints = offsetParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert offsetConstraints.size() == 1
            assert offsetConstraints[0].intValue("value").get() == 0

            // Verify return type has Size constraint
            def listReturnType = listMethod.getReturnType()
            assert listReturnType.hasAnnotation(Size.class)
            def listReturnConstraints = listReturnType.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert listReturnConstraints.size() == 1
            assert listReturnConstraints[0].intValue("max").get() == 100

            return element
        }

        then: "All method parameter and return type validation constraints should be properly parsed"
        classElement != null
    }

    def "test singleton bean constructor parameters with validation annotations"() {
        given: "Python singleton bean with validated constructor parameters"
        @Language("python") def pythonCode = '''
from jakarta.inject import Singleton
from typing import Annotated
from jakarta.validation.constraints import NotBlank, Min, Max, Email, Pattern

@Singleton
class UserService:
    def __init__(self, db_host: Annotated[str, NotBlank], db_port: Annotated[int, Min(1024), Max(65535)],
                 admin_email: Annotated[str, NotBlank, Email], api_key_pattern: Annotated[str, Pattern(regexp=r'^[A-Za-z0-9]{32}$')]):
        self.db_host = db_host
        self.db_port = db_port
        self.admin_email = admin_email
        self.api_key_pattern = api_key_pattern
'''

        when: "Processing the Python code"
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "UserService"

            // Get primary constructor
            def constructor = element.getPrimaryConstructor()
            assert constructor.isPresent()

            def constructorElement = constructor.get()
            def constructorParams = constructorElement.getParameters()
            assert constructorParams.length == 4

            // Verify db_host parameter has NotBlank constraint
            def dbHostParam = constructorParams[0]
            assert dbHostParam.name == "db_host"
            assert dbHostParam.hasAnnotation(NotBlank.class)
            def dbHostConstraints = dbHostParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert dbHostConstraints.size() == 1
            assert dbHostConstraints[0].annotationName == NotBlank.class.name

            // Verify db_port parameter has Min and Max constraints
            def dbPortParam = constructorParams[1]
            assert dbPortParam.name == "db_port"
            assert dbPortParam.hasAnnotation(Min.class)
            assert dbPortParam.hasAnnotation(Max.class)
            def dbPortConstraints = dbPortParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert dbPortConstraints.size() == 2

            // Verify Min constraint value
            def minConstraint = dbPortConstraints.find { it.annotationName == Min.class.name }
            assert minConstraint != null
            assert minConstraint.intValue("value").get() == 1024

            // Verify Max constraint value
            def maxConstraint = dbPortConstraints.find { it.annotationName == Max.class.name }
            assert maxConstraint != null
            assert maxConstraint.longValue("value").get() == 65535L

            // Verify admin_email parameter has NotBlank and Email constraints
            def emailParam = constructorParams[2]
            assert emailParam.name == "admin_email"
            assert emailParam.hasAnnotation(NotBlank.class)
            assert emailParam.hasAnnotation(Email.class)
            def emailConstraints = emailParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert emailConstraints.size() == 2
            assert emailConstraints.any { it.annotationName == NotBlank.class.name }
            assert emailConstraints.any { it.annotationName == Email.class.name }

            // Verify api_key_pattern parameter has Pattern constraint
            def patternParam = constructorParams[3]
            assert patternParam.name == "api_key_pattern"
            assert patternParam.hasAnnotation(Pattern.class)
            def patternConstraints = patternParam.getAnnotationValuesByStereotype("jakarta.validation.Constraint")
            assert patternConstraints.size() == 1
            assert patternConstraints[0].annotationName == Pattern.class.name

            // Verify Pattern constraint regexp value
            assert patternConstraints[0].stringValue("regexp").get() == '^[A-Za-z0-9]{32}$'

            return element
        }

        then: "All constructor parameter validation constraints should be properly parsed"
        classElement != null
    }
}
