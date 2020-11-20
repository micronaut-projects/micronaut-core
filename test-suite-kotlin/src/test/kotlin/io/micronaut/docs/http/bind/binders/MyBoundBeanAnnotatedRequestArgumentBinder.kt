package io.micronaut.docs.http.bind.binders
// tag::class[]
import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.http.HttpRequest
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder
import java.util.*
import javax.inject.Singleton

@Singleton
open class MyBoundBeanAnnotatedRequestArgumentBinder

protected constructor(conversionService: ConversionService<*>?) :
        AbstractAnnotatedArgumentBinder<MyBindingAnnotation, MyBoundBean, HttpRequest<*>?>(conversionService),
        AnnotatedRequestArgumentBinder<MyBindingAnnotation, MyBoundBean> { //<1>

    override fun bind(context: ArgumentConversionContext<MyBoundBean>, source: HttpRequest<*>?):
            ArgumentBinder.BindingResult<MyBoundBean>? { //<2>
        val result = MyBoundBean()
        result.bindingType = "ANNOTATED"
        if (source != null) {
            result.shoppingCartSize = source.cookies.get("shoppingCart", Int::class.java).orElse(null)
            result.displayName = source.cookies["displayName"].value
            val userNameBase64 = source.headers.authorization.orElse(null)
            val userName: String
            userName = try {
                String(Base64.getDecoder().decode(userNameBase64.substring(6)))
                        .split(":").toTypedArray()[0]
            } catch (iae: IllegalArgumentException) {
                context.reject(iae)
                return object : ArgumentBinder.BindingResult<MyBoundBean> {
                    override fun getValue(): Optional<MyBoundBean>? {
                        return Optional.empty<MyBoundBean>()
                    }

                    override fun isSatisfied(): Boolean {
                        return true
                    }
                }
            }
            result.userName = userName
            result.body = source.getBody(String::class.java).orElse(null)
        }
        return ArgumentBinder.BindingResult { Optional.of(result) }
    }

    override fun getAnnotationType(): Class<MyBindingAnnotation> {
        return MyBindingAnnotation::class.java //<3>
    }
}
// end::class[]