# tag::class[]
# TODO: Re-enable when Python custom annotation stereotypes can resolve as Java annotation types for request binders.
# import java
# from jakarta.inject import Singleton
# from micronaut.core.bind import ArgumentBinder
# from micronaut.core.convert import ArgumentConversionContext
# from micronaut.core.convert import ConversionService
# from micronaut.core.type import Argument
# from micronaut.http import HttpRequest
# from micronaut.http.bind.binders import AnnotatedRequestArgumentBinder
# from micronaut.jackson.serialize import JacksonObjectSerializer
#
# from java.util import Optional
#
# ShoppingCartClass = java.type("micronaut.docs.http.server.bind.annotation.ShoppingCart")
# String = java.type("java.lang.String")
# Object = java.type("java.lang.Object")
#
#
# @Singleton
# class ShoppingCartRequestArgumentBinder(AnnotatedRequestArgumentBinder):  # <1>
#     def __init__(
#         self,
#         conversionService: ConversionService,
#         objectSerializer: JacksonObjectSerializer,
#     ):
#         self.conversionService = conversionService
#         self.objectSerializer = objectSerializer
#
#     def getAnnotationType(self):
#         return ShoppingCartClass
#
#     def bind(
#         self,
#         context: ArgumentConversionContext,
#         source: HttpRequest,
#     ):  # <2>
#         parameterName = (
#             context.getAnnotationMetadata()
#             .stringValue(ShoppingCartClass)
#             .orElse("")
#         )
#         if parameterName == "":
#             parameterName = context.getArgument().getName()
#
#         cookie = source.getCookies().get("shoppingCart")
#         if cookie is None:
#             return ArgumentBinder.BindingResult.empty()
#
#         cookieValue = self.objectSerializer.deserialize(
#             bytearray(cookie.getValue(), "utf-8"),
#             Argument.mapOf(String, Object),
#         )
#
#         def value():
#             if cookieValue.isPresent():
#                 return self.conversionService.convert(cookieValue.get().get(parameterName), context)
#             return Optional.empty()
#
#         return value
# end::class[]
