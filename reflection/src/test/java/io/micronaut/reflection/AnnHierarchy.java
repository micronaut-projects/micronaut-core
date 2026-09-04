package io.micronaut.reflection;

/**
 * The shapes an inherited annotation reaches a class through an interface by: an interface implemented directly,
 * a super interface of one, and an interface of a super class. {@link Class#getAnnotations()} reports none of
 * them, while the hierarchy the annotation processors walk includes the interfaces.
 */
public final class AnnHierarchy {

    private AnnHierarchy() {
    }

    /**
     * An interface a class implements directly, carrying an inherited annotation and one that is not.
     */
    @AnnInherited("direct")
    @AnnNotInherited("direct")
    public interface DirectApi {
    }

    /**
     * A super interface of the interface a class implements.
     */
    @AnnInherited("super")
    public interface SuperApi {
    }

    /**
     * The interface between a class and the annotated one, carrying nothing of its own.
     */
    public interface MiddleApi extends SuperApi {
    }

    /**
     * An interface a super class implements.
     */
    @AnnInherited("base")
    public interface BaseApi {
    }

    /**
     * A class implementing the annotated interface directly.
     */
    public static class Direct implements DirectApi {
    }

    /**
     * A class whose interface inherits the annotated one.
     */
    public static class Deep implements MiddleApi {
    }

    /**
     * A super class implementing the annotated interface.
     */
    public static class Base implements BaseApi {
    }

    /**
     * A class reaching the annotated interface through its super class.
     */
    public static class Sub extends Base {
    }

    /**
     * A class declaring the annotation the interface it implements declares too.
     */
    @AnnInherited("declared")
    public static class Declared implements DirectApi {
    }
}
