package io.micronaut.inject.qualifiers

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Specification

class MatchArgumentSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "test forArgument rejects null with clear message"() {
        when:
            Qualifiers.forArgument(null)

        then:
            def e = thrown(NullPointerException)
            e.message == "Argument cannot be null"
    }

    void "test of rejects null metadata with clear message"() {
        when:
            Qualifiers.of(null)

        then:
            def e = thrown(NullPointerException)
            e.message == "Annotation metadata cannot be null"
    }

    void "test match serialize specific argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(List, [Argument.STRING] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListStringSerializer
    }

    void "test match serialize object argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(List, [Argument.OBJECT_ARGUMENT] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 2
            beanDefinitions.collect { it.getBeanType() } as Set == [ListGenericSerializer, ListObjectSerializer] as Set
    }

    void "test match serialize non-specific argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(List, [Argument.of(Boolean)] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 2
            beanDefinitions.collect { it.getBeanType() } as Set == [ListGenericSerializer, ListObjectSerializer] as Set
    }

    void "test match serialize Number argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(List, [Argument.of(Number)] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListGenericNumberSerializer
    }

    void "test match serialize Long argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(List, [Argument.of(Long)] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListLongSerializer
    }

    void "test match serialize Double argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(List, [Argument.of(Double)] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListGenericNumberSerializer
    }

    void "test match serialize ArrayList Long argument"() {
        given:
            def argument = Argument.of(MySerializer, [Argument.of(ArrayList, [Argument.of(Long)] as Argument[])] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.ofArgument(argument))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListArrayListLongSerializer
    }

    void "test match raw serialize argument prefers generic type variable candidate"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MySerializer,
                    MatchArgumentQualifier.contravariant(MySerializer, Argument.of(List))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListGenericSerializer
    }

    void "test match raw subtype serialize argument prefers generic fallback over concrete closer candidate"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MyRawCollectionSerializer,
                    MatchArgumentQualifier.contravariant(MyRawCollectionSerializer, Argument.of(ArrayList))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == GenericRawIterableSerializer
    }

    void "test match raw serialize argument ignores non-generic object fallback"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MyNonGenericRawSerializer,
                    MatchArgumentQualifier.contravariant(MyNonGenericRawSerializer, Argument.of(List))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListObjectNonGenericRawSerializer
    }

    void "test match raw serialize argument prefers exact object candidate over generic iterable fallback"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MyPageSerializer,
                    MatchArgumentQualifier.contravariant(MyPageSerializer, Argument.of(MyPage))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == MyPageObjectSerializer
    }

    void "test match raw serialize implementation argument prefers exact object candidate over generic iterable fallback"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MyPageSerializer,
                    MatchArgumentQualifier.contravariant(MyPageSerializer, Argument.of(DefaultMyPage))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == DefaultMyPageObjectSerializer
    }

    void "test match raw serialize implementation argument prefers object supertype candidate over generic iterable fallback"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MyPageSupertypeSerializer,
                    MatchArgumentQualifier.contravariant(MyPageSupertypeSerializer, Argument.of(DefaultMyPage))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == MyPageSupertypeObjectSerializer
    }

    void "test match serialize Collection String argument"() {
        when:
            def beanDefinitions = context.getBeanDefinitions(MySerializer,
                    MatchArgumentQualifier.contravariant(MySerializer, Argument.of(Collection, [Argument.of(String)] as Argument[]))
            )

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == IterableSerializer
    }

    void "test match deserialize List String argument"() {
        def item = Argument.of(Collection, [Argument.of(String)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ArrayListStringDeserializer
    }

    void "test match deserialize List Object argument"() {
        def item = Argument.of(Collection, [Argument.of(Object)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ArrayListObjectDeserializer
    }

    void "test match deserialize Collection Number argument"() {
        def item = Argument.of(Collection, [Argument.of(Number)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ArrayListNumberDeserializer
    }

    void "test match deserialize List Number argument"() {
        def item = Argument.of(List, [Argument.of(Number)] as Argument[])
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == ListNumberDeserializer
    }

    void "test match deserialize enum with interface argument"() {
        def item = Argument.of(MyEnum)
        given:
            def argument = Argument.of(MyDeserializer, [item] as Argument[])
        when:
            def beanDefinitions = context.getBeanDefinitions(argument, MatchArgumentQualifier.covariant(MyDeserializer, item))

        then:
            beanDefinitions.size() == 1
            beanDefinitions[0].getBeanType() == EnumDeserializer
    }

    void "test finding higher types type object finding all instances"() {
        def item = Argument.OBJECT_ARGUMENT
        when:
            def beanDefinitions = context.getBeanDefinitions(MyReader, MatchArgumentQualifier.covariant(MyReader, item))
            def beanAllDefinitions = context.getBeanDefinitions(Argument.of(MyReader))

        then:
            // The list reader is eliminated because array list is higher
            beanDefinitions.size() + 1 == beanAllDefinitions.size()
            beanDefinitions.size() == 3
    }

    void "test finding higher types type object finding all instances - object reader is a direct match and eliminating everything else"() {
        given:
            ApplicationContext context = ApplicationContext.run(["MatchArgumentSpec.enableObjectReader": "true"])
            def item = Argument.OBJECT_ARGUMENT
        when:
            def beanDefinitions = context.getBeanDefinitions(MyReader, MatchArgumentQualifier.covariant(MyReader, item))

        then:
            beanDefinitions.size() == 1
        cleanup:
            context.close()
    }

    void "test generic higher types reader"() {
        when:
            def stringReaders = context.getBeanDefinitions(MyReader2,
                    MatchArgumentQualifier.covariant(MyReader2, Argument.of(MyType3)))

        then:
            stringReaders.size() == 1
            stringReaders[0].getBeanType() == MyType2AndHigherReader
    }

    interface MySerializer<E> {}

    @Singleton
    static class ListObjectSerializer implements MySerializer<List<Object>> {}

    @Singleton
    static class ListGenericNumberSerializer<T extends Number> implements MySerializer<List<T>> {}

    @Singleton
    static class CollectionGenericNumberSerializer<T extends Number> implements MySerializer<Collection<T>> {}

    @Singleton
    static class ListLongSerializer implements MySerializer<List<Long>> {}

    @Singleton
    static class ListArrayListLongSerializer implements MySerializer<ArrayList<Long>> {}

    @Singleton
    static class ListStringSerializer implements MySerializer<List<String>> {}

    @Singleton
    static class ListCharSequenceSerializer implements MySerializer<List<CharSequence>> {}

    @Singleton
    static class ListGenericSerializer<T> implements MySerializer<List<T>> {}

    @Singleton
    static class IterableSerializer<T> implements MySerializer<Iterable<T>> {}

    interface MyRawCollectionSerializer<E> {}

    @Singleton
    static class ConcreteRawListSerializer implements MyRawCollectionSerializer<List<MyType1>> {}

    @Singleton
    static class GenericRawIterableSerializer<T> implements MyRawCollectionSerializer<Iterable<T>> {}

    interface MyNonGenericRawSerializer<E> {}

    @Singleton
    static class ObjectNonGenericRawSerializer implements MyNonGenericRawSerializer<Object> {}

    @Singleton
    static class ListObjectNonGenericRawSerializer implements MyNonGenericRawSerializer<List<Object>> {}

    interface MyPage<E> extends Iterable<E> {}

    static abstract class DefaultMyPage<E> implements MyPage<E> {}

    interface MyPageSerializer<E> {}

    @Singleton
    static class MyPageObjectSerializer implements MyPageSerializer<MyPage<Object>> {}

    @Singleton
    static class DefaultMyPageObjectSerializer implements MyPageSerializer<DefaultMyPage<Object>> {}

    @Singleton
    static class MyPageIterableSerializer<T> implements MyPageSerializer<Iterable<T>> {}

    interface MyPageSupertypeSerializer<E> {}

    @Singleton
    static class MyPageSupertypeObjectSerializer implements MyPageSupertypeSerializer<MyPage<Object>> {}

    @Singleton
    static class MyPageSupertypeIterableSerializer<T> implements MyPageSupertypeSerializer<Iterable<T>> {}

    interface MyDeserializer<E> {}

    @Singleton
    static class IterableObjectDeserializer<T> implements MyDeserializer<Iterable<T>> {}

    @Singleton
    static class ListObjectDeserializer<T> implements MyDeserializer<List<T>> {}

    @Singleton
    static class ListStringDeserializer implements MyDeserializer<List<String>> {}

    @Singleton
    static class ArrayListObjectDeserializer<T> implements MyDeserializer<ArrayList<T>> {}

    @Singleton
    static class ArrayListStringDeserializer implements MyDeserializer<ArrayList<String>> {}

    @Singleton
    static class ArrayListNumberDeserializer implements MyDeserializer<ArrayList<Number>> {}

    @Singleton
    static class ListNumberDeserializer implements MyDeserializer<List<Number>> {}

    @Singleton
    static class EnumDeserializer<E extends Enum<E>> implements MyDeserializer<E> {}

    @Singleton
    static class MyInterfaceDeserializer implements MyDeserializer<MyInterface> {}

    static interface MyInterface {}

    static enum MyEnum implements MyInterface {}

    interface MyReader<E> {}

    @Requires(property = "MatchArgumentSpec.enableObjectReader", value = "true")
    @Singleton
    static class ObjectMyReader implements MyReader<Object> {}

    @Singleton
    static class StringMyReader implements MyReader<CharSequence> {}

    @Singleton
    static class ListMyReader<T extends List<T>> implements MyReader<T> {}

    @Singleton
    static class ArrayListMyReader<T extends ArrayList<T>> implements MyReader<T> {}

    @Singleton
    static class EnumMyReader implements MyReader<MyEnum> {}

    static class MyType1 implements MyInterface {

    }

    static class MyType2 implements MyInterface {

    }

    static class MyType3 extends MyType2 {

    }
    static interface MyInterface2 extends MyInterface {

    }

    interface MyReader2<E> {}

    @Singleton
    static class MyType2AndHigherReader<T extends MyType2> implements MyReader2<T> {}

    @Singleton
    static class NumberReader<T extends Number> implements MyReader2<T> {}

}
