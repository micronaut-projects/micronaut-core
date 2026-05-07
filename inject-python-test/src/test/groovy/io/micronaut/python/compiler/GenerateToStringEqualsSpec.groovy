package io.micronaut.python.compiler

class GenerateToStringEqualsSpec extends GeneratedJavaSourceSpec {

    void "usage: assert snippet for Person"() {
        given:
        def pythonCode = '''\
from micronaut.core.annotation import Introspected
from dataclasses import dataclass

@Introspected
@dataclass
class Address:
    street: str
    zip: int

@Introspected
@dataclass
class Person:
    name: str
    age: int
    address: Address
'''

        expect:
        assertGeneratedSourceContains(pythonCode, """
  @JsonCreator
  Person(@JsonProperty("name") String name, @JsonProperty("age") int age, @JsonProperty("address") Address address) {
    this(ContextHolder.newInstance("python", "Person", (Object) name, (Object) age, (Object) address));
  }

  public Value asPolyglotValue() {
    return ContextHolder.newInstance("python", "Person", (Object) this.name, (Object) this.age, (Object) this.address);
  }

  public static Person fromPolyglotValue(Value arg1) {
    if (GraalPyRuntimeUtil.isNone(arg1)) {
      return null;
    } else {
      return new python.Person(arg1);
    }
  }

  public void setName(String arg1) {
    this.name = arg1;
  }

  public String getName() {
    return this.name;
  }

  public void setAge(int arg1) {
    this.age = arg1;
  }

  public int getAge() {
    return this.age;
  }

  public void setAddress(Address arg1) {
    this.address = arg1;
  }

  public Address getAddress() {
    return this.address;
  }

  @Override
  public String toString() {
    return new java.lang.StringBuilder("Person[").append("name=").append(this.name).append(", ").append("age=").append(this.age).append(", ").append("address=").append(this.address).append("]").toString();
  }
""")
    }
}
