package io.micronaut.docs.http.bind.binders;
// tag::class[]
import io.micronaut.core.bind.annotation.AbstractAnnotatedArgumentBinder;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.AnnotatedRequestArgumentBinder;

import javax.inject.Singleton;
import java.util.Base64;
import java.util.Optional;

@Singleton
public class MyBoundBeanAnnotatedRequestArgumentBinder extends AbstractAnnotatedArgumentBinder<MyBindingAnnotation, MyBoundBean, HttpRequest<?>>
        implements AnnotatedRequestArgumentBinder<MyBindingAnnotation, MyBoundBean> { //<1>

    /**
     * Constructor.
     *
     * @param conversionService conversionService
     */
    protected MyBoundBeanAnnotatedRequestArgumentBinder(ConversionService<?> conversionService) {
        super(conversionService);
    }

    @Override
    public BindingResult<MyBoundBean> bind(ArgumentConversionContext<MyBoundBean> context, HttpRequest<?> source) { //<2>
        MyBoundBean result = new MyBoundBean();
        result.setBindingType("ANNOTATED");
        result.setShoppingCartSize(source.getCookies().get("shoppingCart", Integer.class).orElse(null));
        result.setDisplayName(source.getCookies().get("displayName").getValue());
        String userNameBase64 = source.getHeaders().getAuthorization().orElse(null);
        String userName = new String(Base64.getDecoder().decode(userNameBase64.substring(6)))
                .split(":", 2)[0];
        result.setUserName(userName);
        result.setBody(source.getBody(String.class).orElse(null));
        return () -> Optional.of(result);
    }

    @Override
    public Class<MyBindingAnnotation> getAnnotationType() {
        return MyBindingAnnotation.class; //<3>
    }
}
// end::class[]