package io.micronaut.context.converters

import spock.lang.Specification

class StringToCharArraySpec extends Specification {

	def "test convert"() {
		given:
		StringToCharArray testInstance = new StringToCharArray()

		when:
		Optional<char[]> result = testInstance.convert("StringToChar", char[], null)
		char[] expected = ['S','t','r','i','n','g','T','o','C','h','a','r']
		char[] actual = result.get()

		then:
		expected == actual

	}
}
