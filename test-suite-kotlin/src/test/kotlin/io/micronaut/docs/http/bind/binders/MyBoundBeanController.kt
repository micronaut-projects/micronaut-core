package io.micronaut.docs.http.bind.binders

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import java.util.*

@Controller("/customBinding")
class MyBoundBeanController {

    // tag::typed[]
    @Post("/typed")
    fun typedPost(bean: MyBoundBean, request: HttpRequest<MyBoundBean?>?): HttpResponse<*> { //<1>
        val responseMap: MutableMap<String, String?> = HashMap()
        responseMap["userName"] = bean.userName
        responseMap["displayName"] = bean.displayName
        responseMap["shoppingCartSize"] = bean.shoppingCartSize.toString()
        responseMap["bindingType"] = bean.bindingType
        return HttpResponse.ok<Map<String, String?>>(responseMap)
    }
    // end::typed[]

    // tag::annotated[]
    @Post("/annotated")
    fun annotatedPost(@MyBindingAnnotation  bean: MyBoundBean, request: HttpRequest<MyBoundBean?>?): HttpResponse<*> { //<1>
        val responseMap: MutableMap<String, String?> = HashMap()
        responseMap["userName"] = bean.userName
        responseMap["displayName"] = bean.displayName
        responseMap["shoppingCartSize"] = bean.shoppingCartSize.toString()
        responseMap["bindingType"] = bean.bindingType
        return HttpResponse.ok<Map<String, String?>>(responseMap)
    }
    // end::annotated
}