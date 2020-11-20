package io.micronaut.docs.http.bind.binders
// tag:class[]
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder

import javax.inject.Singleton

@Singleton
class MyBoundBeanAnnotatedRequestArgumentBinder extends AbstractAnnotatedArgumentBinder<MyBindingAnnotation, MyBoundBean, HttpRequest<?>>
        implements AnnotatedRequestArgumentBinder<MyBindingAnnotation, MyBoundBean> {//<1>

    protected MyBoundBeanAnnotatedRequestArgumentBinder(ConversionService<?> conversionService) {
        super(conversionService)
    }

    @Override
    BindingResult<MyBoundBean> bind(ArgumentConversionContext<MyBoundBean> context, HttpRequest<?> source) { //<2>
        MyBoundBean result = new MyBoundBean()
        result.bindingType = "ANNOTATED"
        result.setShoppingCartSize(source.getCookies().get("shoppingCart", Integer.class).orElse(null))
        result.setDisplayName(source.getCookies().get("displayName").getValue())
        String userNameBase64 = source.getHeaders().getAuthorization().orElse(null)
        String userName
        try {
            userName = new String(Base64.getDecoder().decode(userNameBase64.substring(6)))
                    .split(":", 2)[0]
        } catch (IllegalArgumentException iae) {
            context.reject(iae)
            return BindingResult.EMPTY
        }
        result.setUserName(userName)
        result.setBody(source.getBody(String.class).orElse(null))
        return () -> Optional.of(result)
    }

    @Override
    Class<MyBindingAnnotation> getAnnotationType() {
        return MyBindingAnnotation.class //<3>
    }
}
// end::class[]
