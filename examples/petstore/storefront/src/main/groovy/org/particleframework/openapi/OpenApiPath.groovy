package io.micronaut.openapi

import groovy.transform.CompileStatic
import io.micronaut.core.naming.conventions.TypeConvention
import io.micronaut.http.HttpMethod
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.Trace

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Parameter

@CompileStatic
class OpenApiPath {
    String path
    HttpMethod httpMethod
    List<OpenApiParameter> parameters = []

    static List<OpenApiPath> of(Class clazz) {
        String controllerPath = controllerPath(clazz)
        if ( controllerPath == null ) {
            return [] as List<OpenApiPath>
        }


        List<OpenApiPath> paths = []
        for (Method method : clazz.declaredMethods) {
            String methodPath = methodPath(method)
            if ( methodPath ) {
                String path = "${controllerPath}${methodPath}"
                if ( controllerPath.endsWith('/') && methodPath.startsWith('/') ) {
                    path = "${controllerPath}${methodPath.substring(1,methodPath.size())}"
                }
                paths << of(path, method.annotations, method.parameters, method.parameterTypes, method.parameterAnnotations)
            }
        }
        paths
    }

    static String controllerPath(Class clazz) {
        for (Annotation annotation : clazz.declaredAnnotations) {
            if (annotation.annotationType().name == Controller.class.name) {
                Controller controllerAnnotation = (Controller) annotation
                String value = controllerAnnotation.value()
                if ( !value ) {
                    return TypeConvention.CONTROLLER.asHyphenatedName(clazz)
                } else {
                    return value
                }
            }
        }
        null
    }

    static String methodPath(Method method) {

        for (Annotation annotation : method.annotations) {
            if ( annotation.annotationType().name == Delete.class.name ) {
                String value = ((Delete) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Delete) annotation).uri()
                if ( value ) {
                    return value
                }
            }
            if ( annotation.annotationType().name == Get.class.name ) {
                String value = ((Get) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Get) annotation).uri()
                if ( value ) {
                    return value
                }
            }
            if ( annotation.annotationType().name == Head.class.name ) {
                String value = ((Head) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Head) annotation).uri()
                if ( value ) {
                    return value
                }
            }
            if ( annotation.annotationType().name == Post.class.name ) {
                String value = ((Post) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Post) annotation).uri()
                if ( value ) {
                    return value
                }
            }
            if ( annotation.annotationType().name == Put.class.name ) {
                String value = ((Put) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Put) annotation).uri()
                if ( value ) {
                    return value
                }
            }
            if ( annotation.annotationType().name == Patch.class.name ) {
                String value = ((Patch) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Patch) annotation).uri()
                if ( value ) {
                    return value
                }
            }
            if ( annotation.annotationType().name == Trace.class.name ) {
                String value = ((Trace) annotation).value()
                if ( value ) {
                    return value
                }
                value = ((Trace) annotation).uri()
                if ( value ) {
                    return value
                }
            }
        }
        return null
    }

    static OpenApiPath of(String path, Annotation[] annotations, Parameter[] parameters, Class[] parameterTypes, Annotation[][] parameterAnnotationsArr) {
        OpenApiPath swaggerPath = new OpenApiPath()
        for ( Annotation annotation : annotations ) {
            HttpMethod httpMethod = methodByAnnotation(annotation)
            if ( httpMethod != null ) {
                swaggerPath.httpMethod = httpMethod
            }
        }
        if ( parameters != null ) {
            for ( int i = 0; i < parameters.length; i++ ) {
                Parameter parameter = parameters[i]
                Class parameterType = parameterTypes[i]
                Annotation[] parameterAnnotations = parameterAnnotationsArr[i]
                swaggerPath.parameters << OpenApiParameter.of(path, parameter, parameterType, parameterAnnotations)
            }
        }
        swaggerPath.path = path
        swaggerPath
    }

    static HttpMethod methodByAnnotation(Annotation annotation) {
        String annotationClassName = annotation.annotationType().name
        if (annotationClassName == Get.class.name) {
            return HttpMethod.GET
        }
        if (annotationClassName == Post.class.name) {
            return HttpMethod.POST
        }
        if (annotationClassName == Put.class.name) {
            return HttpMethod.PUT
        }
        if (annotationClassName == Head.class.name) {
            return HttpMethod.HEAD
        }
        if (annotationClassName == Patch.class.name) {
            return HttpMethod.PATCH
        }
        if (annotationClassName == Trace.class.name) {
            return HttpMethod.TRACE
        }
        if (annotationClassName == Delete.class.name) {
            return HttpMethod.DELETE
        }
        null
    }

    static Map toMap(List<OpenApiPath> openApiPaths) {
        List<String> paths = openApiPaths*.path.unique() as List<String>
        Map m = [:]
        for ( String path : paths ) {
            m[path] = [:]

            List<HttpMethod> httpsMethods = openApiPaths.findAll { OpenApiPath openApiPath ->
                openApiPath.path == path
            }*.httpMethod as List<HttpMethod>
            for ( HttpMethod httpMethod : httpsMethods ) {
                String httpMethodLowerCase = httpMethod.name().toLowerCase()
                m[path][httpMethodLowerCase] = [:]
                OpenApiPath swaggerPath = openApiPaths.find { OpenApiPath swaggerPath ->
                    swaggerPath.httpMethod == httpMethod && swaggerPath.path == path
                }
                List<Map> parameters = []
                if ( openApiPaths.parameters ) {
                    for ( OpenApiParameter swaggerParameter : swaggerPath.parameters ) {
                        parameters << swaggerParameter.toMap()
                    }
                }
                m[path][httpMethodLowerCase]['parameters'] = parameters
                m[path][httpMethodLowerCase]['responses'] = defaultResponses()
            }
        }
        m
    }

    static Map defaultResponses() {
        Map m = [:]
        m['default'] = [:]
        m['default']['description'] = 'successful operation'
        m
    }
}
