package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.web.router.Router
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The local error and status routes of a controller that another controller extends invoke the
 * controller that declares them, although the bean type of the controller also matches the subclass.
 */
class ExtendedControllerErrorRouteSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ExtendedControllerErrorRouteSpec'])

    @Shared
    Router router = ctx.getBean(Router)

    void "the error route of each controller invokes its own bean"() {
        expect:
        router.route(BaseController, new IllegalStateException()).get().invoke(new IllegalStateException()) == 'base error'
        router.route(SubController, new IllegalStateException()).get().invoke(new IllegalStateException()) == 'sub error'
    }

    void "the status route of each controller invokes its own bean"() {
        expect:
        router.route(BaseController, HttpStatus.INSUFFICIENT_STORAGE).get().invoke() == 'base status'
        router.route(SubController, HttpStatus.INSUFFICIENT_STORAGE).get().invoke() == 'sub status'
    }

    void "the URI route of each controller invokes its own bean"() {
        expect:
        router.GET('/base/index').get().invoke() == 'base index'
        router.GET('/sub/index').get().invoke() == 'sub index'
    }

    @Requires(property = 'spec.name', value = 'ExtendedControllerErrorRouteSpec')
    @Controller('/base')
    static class BaseController {

        String name() {
            'base'
        }

        @Get('/index')
        String index() {
            name() + ' index'
        }

        @Error
        String error(IllegalStateException e) {
            name() + ' error'
        }

        @Error(status = HttpStatus.INSUFFICIENT_STORAGE)
        String status() {
            name() + ' status'
        }
    }

    @Requires(property = 'spec.name', value = 'ExtendedControllerErrorRouteSpec')
    @Controller('/sub')
    static class SubController extends BaseController {

        @Override
        String name() {
            'sub'
        }
    }
}
