package io.micronaut.docs.http.bind.binders
// tag::class[]
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import java.util.*
import javax.inject.Singleton

@Singleton
class MyBoundBeanTypedRequestArgumentBinder : TypedRequestArgumentBinder<MyBoundBean> {

    override fun bind(context: ArgumentConversionContext<MyBoundBean?>?, source: HttpRequest<*>?):
            ArgumentBinder.BindingResult<MyBoundBean> { //<1>
        val result = MyBoundBean()
        result.bindingType = "TYPED"
        if (source != null) {
            result.shoppingCartSize = source.cookies.get("shoppingCart", Int::class.java).orElse(null)
            result.displayName = source.cookies["displayName"].value
            val userNameBase64 = source.headers.authorization.orElse(null)
            val userName = String(Base64.getDecoder().decode(userNameBase64.substring(6)))
                    .split(":").toTypedArray()[0]
            result.userName = userName
            result.body = source.getBody(String::class.java).orElse(null)
        }
        return ArgumentBinder.BindingResult { Optional.of(result) } //<2>
    }

    override fun argumentType(): Argument<MyBoundBean?>? {
        return Argument.of(MyBoundBean::class.java) //<3>
    }
}
// end:class[]