package io.micronaut.python.compiler

class BoundedIntroductionInterfaceStubSourceSpec extends GeneratedJavaSourceSpec {

    void "an introduction interface retains the inherited method variable for #hintStyle hints"() {
        expect:
        assertGeneratedSourceContains("""
from abc import abstractmethod
from micronaut.aop import Introduction
from io.micronaut.python.annotation.processing.test.generics import GenericSaveRepository

class Book:
    pass

@Introduction
def Stub(cls):
    return cls

@Stub
class Repository(GenericSaveRepository[Book]):
    @abstractmethod
    def saveAll${typeParameters}(self, ${parameterName}: list[${entityType}]) -> list[${entityType}]:
        ...
""", """
public interface Repository extends GenericSaveRepository<Book> {
  <S extends Book> List<S> saveAll(Iterable<S> ${parameterName});
}
""")

        where:
        hintStyle         | typeParameters | entityType | parameterName
        'PEP 695'         | '[S: Book]'    | 'S'        | 'entities'
        'entity'          | ''             | 'Book'     | 'entities'
        'renamed PEP 695' | '[U: Book]'    | 'U'        | 'items'
    }

    void "a concrete return hint does not add a bound to an inherited unbounded method variable"() {
        expect:
        assertGeneratedSourceContains('''
from abc import abstractmethod
from micronaut.aop import Introduction
from io.micronaut.python.annotation.processing.test.generics import UnboundedMapper

class Book:
    pass

@Introduction
def Stub(cls):
    return cls

@Stub
class Mapper(UnboundedMapper[Book]):
    @abstractmethod
    def map(self, value: Book) -> str:
        ...
''', '''
public interface Mapper extends UnboundedMapper<Book> {
  <R> R map(Book value);
}
''')
    }
}
