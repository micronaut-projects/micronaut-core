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
  Person(String name, int age, Address address) {
    this(ContextHolder.newInstance("python", "Person", name, age, address.asPolyglotValue()));
  }

  public Value asPolyglotValue() {
    return ContextHolder.newInstance("python", "Person", this.name, this.age, this.address == null ? null : this.address.asPolyglotValue());
  }

  public static Person fromPolyglotValue(Value arg1) {
    return new python.Person(arg1);
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

  @Override
  public boolean equals(Object o) {
    if ((this == o)) {
      return true;
    }
    if ((o == null || (this.getClass()) != (o.getClass()))) {
      return false;
    }
    Person other = (Person) o;
    return Objects.equals(this.name, other.name) && Objects.equals(this.age, other.age) && Objects.equals(this.address, other.address);
  }

  @Override
  public int hashCode() {
    int hashValue = this.name == null ? 0 : this.name.hashCode();
    hashValue = (hashValue * 31) + (Integer.hashCode(this.age));
    hashValue = (hashValue * 31) + (this.address == null ? 0 : this.address.hashCode());
    return hashValue;
  }
""")
    }
}
