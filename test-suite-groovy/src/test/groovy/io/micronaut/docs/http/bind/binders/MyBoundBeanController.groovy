package io.micronaut.docs.http.bind.binders

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

@Controller("/customBinding")
class MyBoundBeanController {

    // tag::typed[]
    @Post("/typed")
    HttpResponse<?> typedPost(MyBoundBean bean, HttpRequest<MyBoundBean> request) { //<1>
        Map<String, Object> responseMap = new HashMap<>()
        responseMap.put("userName", bean.getUserName())
        responseMap.put("displayName", bean.getDisplayName())
        responseMap.put("shoppingCartSize", bean.getShoppingCartSize())
        responseMap.put("bindingType", bean.getBindingType())
        return HttpResponse.ok(responseMap)
    }
    // end::typed[]

    // tag::annotated[]
    @Post("/annotated")
    HttpResponse<String> loadCart(@MyBindingAnnotation Long id) { //<1>
        HttpResponse.ok("Session:${id}".toString())
    }
    // end::annotated
}
