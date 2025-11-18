package io.micronaut.jackson

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@MicronautTest
class JacksonDefaultTest {

    @Test
    fun testValueMissingOnDefaultField(objectMapper: ObjectMapper) {
        val json = """{}"""
        val bean = objectMapper.readValue(json, DefaultConstructorDto::class.java)
        Assertions.assertEquals(22, bean.longField)
    }

    @Test
    fun defaultPrimitiveIsUsedIfMissingOnRequiredField(objectMapper: ObjectMapper) {
        val json = """{}"""
        val bean = objectMapper.readValue(json, RequireConstructorParamDto::class.java)
        Assertions.assertEquals(0, bean.longField)
    }

    @Test
    fun throwExceptionWhenFailOnNullForPrimitiveIsEnabledAndPrimitiveFieldIsMissing(objectMapper: ObjectMapper) {
        val configuredObjectMapper =
            objectMapper.copy().configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
        val json = """{}"""
        val exception = Assertions.assertThrows(
            MismatchedInputException::class.java,
            { configuredObjectMapper.readValue(json, RequireConstructorParamDto::class.java) })
        Assertions.assertTrue(exception.message!!.contains("Cannot map `null` into type `long`"))
    }
}
// @Override
//                    public Object createFromObjectWith(DeserializationContext ctxt,
//                                                       SettableBeanProperty[] props, PropertyValueBuffer buffer) throws IOException {
//
////                        var isKotlinClass = introspection.hasAnnotation("kotlin.Metadata"); //Arrays.stream(introspection.getBeanType().getAnnotations()).anyMatch(annotation -> annotation.getName().equals("kotlin.Metadata"));
////                        if (false && isKotlinClass == false) {
////                            var values = buffer.getParameters(props);
////                            return createFromObjectWith(ctxt, values);
////                        }
//                        Object[] args = new Object[props.length];
//
//                        for (int i = 0; i < props.length; i++) {
//
//                            var prop = props[i];
//                            var isOptionalConstructorArg = Arrays.stream(constructorArguments).anyMatch(introspectionProp -> introspectionProp.getName().equals(prop.getName()) && introspectionProp.findAnnotation(jakarta.annotation.Nullable.class).isPresent());
//                            if (!buffer.hasParameter(prop) && isOptionalConstructorArg) {
//                                args[i] = null;
//                            } else {
//                                args[i] = buffer.getParameter(prop);
//                            }
//                        }
//                        return createFromObjectWith(ctxt, args);
//                    }
