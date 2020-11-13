package io.micronaut.docs.http.bind.binders
// tag:class[]
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder

import javax.inject.Singleton

@Singleton
class MyBoundBeanTypedRequestArgumentBinder implements TypedRequestArgumentBinder<MyBoundBean> {

    @Override
    BindingResult<MyBoundBean> bind(ArgumentConversionContext<MyBoundBean> context, HttpRequest<?> source) { //<1>
        MyBoundBean result = new MyBoundBean()
        result.setBindingType("TYPED")
        result.setShoppingCartSize(source.getCookies().get("shoppingCart", Integer.class).orElse(null))
        result.setDisplayName(source.getCookies().get("displayName").getValue())
        String userNameBase64 = source.getHeaders().getAuthorization().orElse(null)
        String userName = new String(Base64.getDecoder().decode(userNameBase64.substring(6)))
                .split(":", 2)[0]
        result.setUserName(userName)
        result.setBody(source.getBody(String.class).orElse(null))
        return () -> Optional.of(result) //<2>
    }

    @Override
    Argument<MyBoundBean> argumentType() {
        return Argument.of(MyBoundBean.class) //<3>
    }
}
// end:class[]