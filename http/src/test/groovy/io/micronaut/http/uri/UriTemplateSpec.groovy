/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.uri

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class UriTemplateSpec extends Specification {

    @Unroll
    void "Test nest template #template with path #nested and #arguments"() {
        given:
        UriTemplate uriTemplate = new UriTemplate(template)

        expect:
        uriTemplate.nest(nested).expand(arguments) == result

        where:
        template       | nested               | arguments                               | result
        '/city'        | 'country/{name}'     | [name: 'Fred']                          | '/city/country/Fred'
        '/city/'       | 'country/{name}'     | [name: 'Fred']                          | '/city/country/Fred'
        '/city/'       | '/country/{name}'    | [name: 'Fred']                          | '/city/country/Fred'
        '/city'        | '/country/{name}'    | [name: 'Fred']                          | '/city/country/Fred'
        '/poetry'      | '/{?max}'            | [max: '10']                             | '/poetry?max=10'
        '/poetry'      | '{?max}'             | [max: '10']                             | '/poetry?max=10'
        '/'            | '/hello/{name}'      | [name: 'Fred']                          | '/hello/Fred'
        ''             | '/hello/{name}'      | [name: 'Fred']                          | '/hello/Fred'
        '/test/'       | '/hello/{name}'      | [name: 'Fred']                          | '/test/hello/Fred'
        '{var}'        | '{var2}'             | [var: 'foo', var2: 'bar']               | 'foo/bar'
        '/book{/id}'   | '/author{/authorId}' | [id: 'foo', authorId: 'bar']            | '/book/foo/author/bar'
        '{var}/'       | '{var2}'             | [var: 'foo', var2: 'bar']               | 'foo/bar'
        '{var}'        | '/{var2}'            | [var: 'foo', var2: 'bar']               | 'foo/bar'
        '{var}{?q}'    | '/{var2}'            | [var: 'foo', var2: 'bar', q: 'test']    | 'foo/bar?q=test'
        '{var}{?q}'    | '{var2}'             | [var: 'foo', var2: 'bar', q: 'test']    | 'foo/bar?q=test'
        '{var}{#hash}' | '{var2}'             | [var: 'foo', var2: 'bar', hash: 'test'] | 'foo/bar#test'
        '/'            | '{?req*}'            | [req: [var: 'foo', var2: null]]         | '/?var=foo'
    }

    @Unroll
    void "Test nest template #template toPathString() with path #nested"() {
        given:
        UriMatchTemplate uriTemplate = new UriMatchTemplate(template)

        expect:
        uriTemplate.nest(nested).toPathString() == result

        where:
        template       | nested                         | result
        '/city'        | '/'                            | '/city'
        '/city'        | ''                             | '/city'
        '/city'        | 'country/{name}'               | '/city/country/{name}'
//        '/foo'         | '/find?{x,empty}'              | '/foo/find' TODO: Not yet implemented. Probably should work
        '/foo'         | '/find{?x,empty}'              | '/foo/find'
        '/foo'         | '{/list*,path:4}'              | '/foo{/list*,path:4}'
        '/person'      | '/{fred}'                      | '/person/{fred}'
        '/books'       | '{/id}'                        | '/books{/id}'
        '/books/'      | '{/id}'                        | '/books{/id}'
        ""             | '/authors{/authorId}'          | '/authors{/authorId}'
        '/'            | '/regex/{color:^blue|orange$}' | '/regex/{color:^blue|orange$}'
        '/poetry'      | '/{?max}'                      | '/poetry'
        '/poetry'      | '{?max}'                       | '/poetry'
        '/'            | '/hello/{name}'                | '/hello/{name}'
        ''             | '/hello/{name}'                | '/hello/{name}'
        '/test/'       | '/hello/{name}'                | '/test/hello/{name}'
        '{var}'        | '{var2}'                       | '{var}/{var2}'
        '/book{/id}'   | '/author{/authorId}'           | '/book{/id}/author{/authorId}'
        '{var}/'       | '{var2}'                       | '{var}/{var2}'
        '{var}'        | '/{var2}'                      | '{var}/{var2}'
        '{var}{?q}'    | '/{var2}'                      | '{var}/{var2}'
        '{var}{#hash}' | '{var2}'                       | '{var}/{var2}'
        '/foo'         | '/find{?year*}'                | '/foo/find'


    }

    @Unroll
    void "Test nest template #template toString() with path #nested"() {
        given:
        UriTemplate uriTemplate = new UriTemplate(template)

        expect:
        uriTemplate.nest(nested).toString() == result

        where:
        template       | nested                         | result
        '/city'        | '/'                            | '/city'
        '/city'        | ''                             | '/city'
        '/city'        | 'country/{name}'               | '/city/country/{name}'
        '/city/'       | 'country/{name}'               | '/city/country/{name}'
        '/city/'       | '/country/{name}'              | '/city/country/{name}'
        '/city'        | '/country/{name}'              | '/city/country/{name}'
        '/foo'         | '/find?{x,empty}'              | '/foo/find?{x,empty}'
        '/foo'         | '/find{?x,empty}'              | '/foo/find{?x,empty}'
        '/foo'         | '{/list*,path:4}'              | '/foo{/list*,path:4}'
        '/person'      | '/{fred}'                      | '/person/{fred}'
        '/books'       | '{/id}'                        | '/books{/id}'
        '/books/'      | '{/id}'                        | '/books{/id}'
        ""             | '/authors{/authorId}'          | '/authors{/authorId}'
        '/'            | '/regex/{color:^blue|orange$}' | '/regex/{color:^blue|orange$}'
        '/poetry'      | '/{?max}'                      | '/poetry{?max}'
        '/poetry'      | '{?max}'                       | '/poetry{?max}'
        '/'            | '/hello/{name}'                | '/hello/{name}'
        ''             | '/hello/{name}'                | '/hello/{name}'
        '/test/'       | '/hello/{name}'                | '/test/hello/{name}'
        '{var}'        | '{var2}'                       | '{var}/{var2}'
        '/book{/id}'   | '/author{/authorId}'           | '/book{/id}/author{/authorId}'
        '{var}/'       | '{var2}'                       | '{var}/{var2}'
        '{var}'        | '/{var2}'                      | '{var}/{var2}'
        '{var}{?q}'    | '/{var2}'                      | '{var}/{var2}{?q}'
        '{var}{#hash}' | '{var2}'                       | '{var}/{var2}{#hash}'
        '/foo'         | '/find{?year*}'                | '/foo/find{?year*}'


    }

    @Unroll
    void "Test expand URI template #template with arguments #arguments for path"() {
        given:
        UriTemplate uriTemplate = new UriTemplate(template)

        expect: 'See https://tools.ietf.org/html/rfc6570#section-2.4.1'
        uriTemplate.expand(arguments) == result

        where:
        template              | arguments                                          | result
        ''                    | [:]                                                | ''
        '/'                   | [:]                                                | '/'
        '{var}'               | [var: 'value']                                     | 'value' // Section 2.4.1 - Prefix Values
        '{var:20}'            | [var: 'value']                                     | 'value'
        '{var:3}'             | [var: 'value']                                     | 'val'
        '{semi}'              | [semi: ';']                                        | '%3B'
        '{semi:2}'            | [semi: ';']                                        | '%3B'
        'find{?year*}'        | [year: ["1965", "2000", "2012"]]                   | 'find?year=1965&year=2000&year=2012' // Section 2.4.2.  Composite Values
        'find{?year*}'        | [:]                                                | 'find' // Section 2.4.2.  Composite Values
        'www{.dom*}'          | [dom: ["example", "com"]]                          | 'www.example.com'
        '{count}'             | [count: ['one', 'two', 'three']]                   | 'one,two,three' // Section 3.2.1  Variable Expansion
        '{count*}'            | [count: ['one', 'two', 'three']]                   | 'one,two,three'
        '{/count}'            | [count: ['one', 'two', 'three']]                   | '/one,two,three'
        '{/count*}'           | [count: ['one', 'two', 'three']]                   | '/one/two/three'
        '{;count}'            | [count: ['one', 'two', 'three']]                   | ';count=one,two,three'
        '{;count*}'           | [count: ['one', 'two', 'three']]                   | ';count=one;count=two;count=three'
        '{?count}'            | [count: ['one', 'two', 'three']]                   | '?count=one,two,three'
        '{?count*}'           | [count: ['one', 'two', 'three']]                   | '?count=one&count=two&count=three'
        '{&count*}'           | [count: ['one', 'two', 'three']]                   | '&count=one&count=two&count=three'
        '{var}'               | [var: ['value']]                                   | 'value' // Section 3.2.2 - Level 1 - Simple String Expansion: {var}
        '{hello}'             | [hello: "Hello World!"]                            | 'Hello%20World%21'
        '{half}'              | [half: '50%']                                      | '50%25'
        'O{empty}X'           | [empty: '']                                        | 'OX'
        'O{undef}X'           | [:]                                                | 'OX'
        '{x,y}'               | [x: 1024, y: 768]                                  | '1024,768'
        '?{x,empty}'          | [x: 1024, empty: '']                               | '?1024,'
        '?{x,undef}'          | [x: 1024, empty: '']                               | '?1024'
        '?{undef,y}'          | [y: 768]                                           | '?768'
        'map?{x,y}'           | [x: 1024, y: 768]                                  | 'map?1024,768' // Section 3.2.2 - Simple String Expansion: {var}
        '{x,hello,y}'         | [x: 1024, y: 768, hello: "Hello World!"]           | '1024,Hello%20World%21,768'
        '{var:30}'            | [var: 'value']                                     | 'value' // Section 3.2.2 - Level 4 - Simple String Expansion: {var}
        '{list}'              | [list: ['red', 'green', 'blue']]                   | 'red,green,blue'
        '{list*}'             | [list: ['red', 'green', 'blue']]                   | 'red,green,blue'
        '{keys}'              | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'semi,%3B,dot,.,comma,%2C'
        '{keys*}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'semi=%3B,dot=.,comma=%2C'
        '{+var}'              | [var: 'value']                                     | 'value' // Section 3.2.3 - Level 2 - Reserved Expansion: {+var}
        '{+hello}'            | [hello: "Hello World!"]                            | 'Hello%20World!'
        '{+half}'             | [half: '50%']                                      | '50%25'
        '{base}index'         | [base: 'http://example.com/home/']                 | 'http%3A%2F%2Fexample.com%2Fhome%2Findex'
        '{+base}index'        | [base: 'http://example.com/home/']                 | 'http://example.com/home/index'
        '{+base}{hello}'      | [base: 'http://example.com/home/', hello: "Hello World!"] | 'http://example.com/home/Hello%20World%21'
        'O{+empty}X'          | [empty: '']                                        | 'OX'
        'O{+undef}X'          | [:]                                                | 'OX'
        '{+path}/here'        | [path: "/foo/bar"]                                 | '/foo/bar/here'
        'here?ref={+path}'    | [path: "/foo/bar"]                                 | 'here?ref=/foo/bar'
        'up{+path}{var}/here' | [path: "/foo/bar", var: 'value']                   | 'up/foo/barvalue/here'
        '{+x,hello,y}'        | [x: 1024, y: 768, hello: "Hello World!"]           | '1024,Hello%20World!,768'
        '{+path,x}/here'      | [path: "/foo/bar", x: 1024]                        | '/foo/bar,1024/here'
        '{+path:6}/here'      | [path: "/foo/bar"]                                 | '/foo/b/here' // Section 3.2.3 - Level 4 - Reserved expansion with value modifiers
        '{+list}'             | [list: ['red', 'green', 'blue']]                   | 'red,green,blue'
        '{+list*}'            | [list: ['red', 'green', 'blue']]                   | 'red,green,blue'
        '{+keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'semi,;,dot,.,comma,,'
        '{+keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'semi=;,dot=.,comma=,'
        '{#var}'              | [var: 'value']                                     | '#value' // Section 3.2.4 - Level 2 - Fragment Expansion: {#var}
        '{#hello}'            | [hello: "Hello World!"]                            | '#Hello%20World!'
        '{#half}'             | [half: '50%']                                      | '#50%25'
        'foo{#empty}'         | [empty: '']                                        | 'foo#'
        'foo{#undef}'         | [:]                                                | 'foo'
        'X{#var}'             | [var: 'value']                                     | 'X#value'
        'X{#hello}'           | [hello: "Hello World!", var: 'value']              | 'X#Hello%20World!'
        '{#x,hello,y}'        | [x: 1024, y: 768, hello: "Hello World!"]           | '#1024,Hello%20World!,768' // Section 3.2.4 - Level 3 - Fragment Expansion: {#var}
        '{#path,x}/here'      | [path: "/foo/bar", x: 1024]                        | '#/foo/bar,1024/here'
        '{#path:6}/here'      | [path: "/foo/bar"]                                 | '#/foo/b/here' // Section 3.2.4 - Level 4 - Fragment expansion with value modifiers
        '{#list}'             | [list: ['red', 'green', 'blue']]                   | '#red,green,blue'
        '{#list*}'            | [list: ['red', 'green', 'blue']]                   | '#red,green,blue'
        '{#keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '#semi,;,dot,.,comma,,'
        '{#keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '#semi=;,dot=.,comma=,'
        'X{.var}'             | [var: 'value']                                     | 'X.value' // Section 3.2.5 - Level 3 - Label Expansion with Dot-Prefix: {.var}
        'X{.empty}'           | [empty: '']                                        | 'X.'
        'X{.undef}'           | [:]                                                | 'X'
        'X{.x,y}'             | [x: 1024, y: 768]                                  | 'X.1024.768'
        '{.who}'              | [who: 'fred']                                      | '.fred'
        '{.who,who}'          | [who: 'fred']                                      | '.fred.fred'
        '{.half,who}'         | [half: '50%', who: 'fred']                         | '.50%25.fred'
        'www{.dom*}'          | [dom: ["example", "com"]]                          | 'www.example.com'
        'X{.var:3}'           | [var: 'value']                                     | 'X.val' // Section 3.2.5 - Level 4 - Label expansion, dot-prefixed
        'X{.list*}'           | [list: ['red', 'green', 'blue']]                   | 'X.red.green.blue'
        'X{.list}'            | [list: ['red', 'green', 'blue']]                   | 'X.red,green,blue'
        'X{.keys}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'X.semi,%3B,dot,.,comma,%2C'
        'X{.keys*}'           | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'X.semi=%3B.dot=..comma=%2C'
        'X{.empty_keys}'      | [empty_keys: [:]]                                  | 'X'
        'X{.empty_keys}'      | [empty_keys: []]                                   | 'X'
        '{/who}'              | [who: 'fred']                                      | '/fred' // Section 3.2.6 - Level 3 - Path Segment Expansion: {/var}
        '{/who,who}'          | [who: 'fred']                                      | '/fred/fred'
        '{/var,empty,empty}'  | [var: 'fred']                                      | '/fred'
        '{/half,who}'         | [half: '50%', who: 'fred']                         | '/50%25/fred'
        '{/who,dub}'          | [who: 'fred', dub: 'me/too']                       | '/fred/me%2Ftoo'
        '{/var}'              | [var: 'value']                                     | '/value'
        '{/var,undef}'        | [var: 'value']                                     | '/value'
        '{/var,empty}'        | [var: 'value', empty: '']                          | '/value/'
        '{/var,x}/here'       | [var: 'value', x: 1024]                            | '/value/1024/here'
        '{/var:1,var}'        | [var: 'value']                                     | '/v/value' // Section 3.2.6 - Level 4 - Path Segment Expansion: {/var}
        '{/list}'             | [list: ['red', 'green', 'blue']]                   | '/red,green,blue'
        '{/list*}'            | [list: ['red', 'green', 'blue']]                   | '/red/green/blue'
        '{/list*,path:4}'     | [list: ['red', 'green', 'blue'], path: "/foo/bar"] | '/red/green/blue/%2Ffoo'
        '/files/content{/path*}{/name}' | [name: "value"]                                                 | '/files/content/value'
        '{/keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '/semi,%3B,dot,.,comma,%2C'
        '{/keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '/semi=%3B/dot=./comma=%2C'
        '{;who}'              | [who: 'fred']                                      | ';who=fred' // Section 3.2.7 - Level 3 - Path-Style Parameter Expansion: {;var}
        '{;half}'             | [half: '50%']                                      | ';half=50%25'
        '{;empty}'            | [empty: '']                                        | ';empty'
        '{;v,empty,who}'      | [v: 6, empty: '', who: 'fred']                     | ';v=6;empty;who=fred'
        '{;v,undef,who}'      | [v: 6, who: 'fred']                                | ';v=6;who=fred'
        '{;x,y}'              | [x: 1024, y: 768]                                  | ';x=1024;y=768'
        '{;x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | ';x=1024;y=768;empty'
        '{;x,y,undef}'        | [x: 1024, y: 768, empty: '']                       | ';x=1024;y=768'
        '{;hello:5}'          | [hello: "Hello World!"]                            | ';hello=Hello' // Section 3.2.7 - Level 4 - Path-Style Parameter Expansion: {;var}
        '{;list}'             | [list: ['red', 'green', 'blue']]                   | ';list=red,green,blue'
        '{;list*}'            | [list: ['red', 'green', 'blue']]                   | ';list=red;list=green;list=blue'
        '{;keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | ';keys=semi,%3B,dot,.,comma,%2C'
        '{;keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | ';semi=%3B;dot=.;comma=%2C'
        '{?who}'              | [who: 'fred']                                      | '?who=fred' // Section 3.2.8 - Level 3 - Form-Style Query Expansion: {?var}
        '{?half}'             | [half: '50%']                                      | '?half=50%25'
        '{?x,y}'              | [x: 1024, y: 768, empty: '']                       | '?x=1024&y=768'
        '{?x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | '?x=1024&y=768&empty='
        '{?x,y,undef}'        | [x: 1024, y: 768, empty: '']                       | '?x=1024&y=768'
        '{?var:3}'            | [var: 'value']                                     | '?var=val' // Section 3.2.8 - Level 4 - Form-Style Query Expansion: {?var}
        '{?list}'             | [list: ['red', 'green', 'blue']]                   | '?list=red,green,blue'
        '{?list*}'            | [list: ['red', 'green', 'blue']]                   | '?list=red&list=green&list=blue'
        '{?keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '?keys=semi,%3B,dot,.,comma,%2C'
        '{?keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '?semi=%3B&dot=.&comma=%2C'
        '{?hello}'            | [hello: "Hello World!"]                            | '?hello=Hello+World%21'
        '?fixed=yes{&x}'      | [x: 1024]                                          | '?fixed=yes&x=1024' // Section 3.2.9 - Level 3 - Form-style query continuation
        '{&x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | '&x=1024&y=768&empty='
        '{&x,y,empty}'        | [x: 1024, y: 768, empty: null]                     | '&x=1024&y=768'
        '{&var:3}'            | [var: 'value']                                     | '&var=val' // Section 3.2.9 - Level 4 - Form-style query continuation
        '{&list}'             | [list: ['red', 'green', 'blue']]                   | '&list=red,green,blue'
        '{&list*}'            | [list: ['red', 'green', 'blue']]                   | '&list=red&list=green&list=blue'
        '{&keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '&keys=semi,%3B,dot,.,comma,%2C'
        '{&keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | '&semi=%3B&dot=.&comma=%2C'
        '{?list*,locale,currency}' | [list: ['red', 'green', 'blue'], locale: null, currency: 'USD'] | '?list=red&list=green&list=blue&currency=USD'
        '{?param[]*}'         | ['param[]': ['a', 'b', 'c']]                       | '?param[]=a&param[]=b&param[]=c'
    }


    @Unroll
    void "Test expand URI template #template with arguments #arguments for full URL"() {
        given:
        UriTemplate uriTemplate = new UriTemplate(template)

        expect: 'See https://tools.ietf.org/html/rfc6570#section-2.4.1'
        uriTemplate.expand(arguments) == result

        where:
        template                                 | arguments                                          | result
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value']                                       | 'http://example.com/v/value/p'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val']                             | 'http://example.com/v/value/p?o=val'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', m: 'val']                             | 'http://example.com/v/value/p?m=val'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', s: 'val']                             | 'http://example.com/v/value/p?s=val'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val1', m: 'val2', s: 'val3']      | 'http://example.com/v/value/p?o=val1&m=val2&s=val3'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', m: 'val2', s: 'val3']                 | 'http://example.com/v/value/p?m=val2&s=val3'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val1', s: 'val3']                 | 'http://example.com/v/value/p?o=val1&s=val3'
        'http://example.com/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val1', m: 'val2'           ]      | 'http://example.com/v/value/p?o=val1&m=val2'
        'http://example.com/{var}'               | [var: 'value']                                     | 'http://example.com/value' // Section 2.4.1 - Prefix Values
        'http://example.com/{var:20}'            | [var: 'value']                                     | 'http://example.com/value'
        'http://example.com/{var:3}'             | [var: 'value']                                     | 'http://example.com/val'
        'http://example.com/{semi}'              | [semi: ';']                                        | 'http://example.com/%3B'
        'http://example.com/{semi:2}'            | [semi: ';']                                        | 'http://example.com/%3B'
        'http://example.com/find{?year*}'        | [year: ["1965", "2000", "2012"]]                   | 'http://example.com/find?year=1965&year=2000&year=2012' // Section 2.4.2.  Composite Values
        'http://example.com/www{.dom*}'          | [dom: ["example", "com"]]                          | 'http://example.com/www.example.com'
        'http://example.com/{count}'             | [count: ['one', 'two', 'three']]                   | 'http://example.com/one,two,three' // Section 3.2.1  Variable Expansion
        'http://example.com/{count*}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com/one,two,three'
        'http://example.com/{/count}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com//one,two,three'
        'http://example.com/{/count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com//one/two/three'
        'http://example.com/{;count}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com/;count=one,two,three'
        'http://example.com/{;count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com/;count=one;count=two;count=three'
        'http://example.com/{?count}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com/?count=one,two,three'
        'http://example.com/{?count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com/?count=one&count=two&count=three'
        'http://example.com/{&count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com/&count=one&count=two&count=three'
        'http://example.com/{var}'               | [var: ['value']]                                   | 'http://example.com/value' // Section 3.2.2 - Level 1 - Simple String Expansion: {var}
        'http://example.com/{hello}'             | [hello: "Hello World!"]                            | 'http://example.com/Hello%20World%21'
        'http://example.com/{half}'              | [half: '50%']                                      | 'http://example.com/50%25'
        'http://example.com/O{empty}X'           | [empty: '']                                        | 'http://example.com/OX'
        'http://example.com/O{undef}X'           | [:]                                                | 'http://example.com/OX'
        'http://example.com/{x,y}'               | [x: 1024, y: 768]                                  | 'http://example.com/1024,768'
        'http://example.com/?{x,empty}'          | [x: 1024, empty: '']                               | 'http://example.com/?1024,'
        'http://example.com/?{x,undef}'          | [x: 1024, empty: '']                               | 'http://example.com/?1024'
        'http://example.com/?{undef,y}'          | [y: 768]                                           | 'http://example.com/?768'
        'http://example.com/map?{x,y}'           | [x: 1024, y: 768]                                  | 'http://example.com/map?1024,768' // Section 3.2.2 - Simple String Expansion: {var}
        'http://example.com/{x,hello,y}'         | [x: 1024, y: 768, hello: "Hello World!"]           | 'http://example.com/1024,Hello%20World%21,768'
        'http://example.com/{var:30}'            | [var: 'value']                                     | 'http://example.com/value' // Section 3.2.2 - Level 4 - Simple String Expansion: {var}
        'http://example.com/{list}'              | [list: ['red', 'green', 'blue']]                   | 'http://example.com/red,green,blue'
        'http://example.com/{list*}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/red,green,blue'
        'http://example.com/{keys}'              | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/semi,%3B,dot,.,comma,%2C'
        'http://example.com/{keys*}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/semi=%3B,dot=.,comma=%2C'
        'http://example.com/{+var}'              | [var: 'value']                                     | 'http://example.com/value' // Section 3.2.3 - Level 2 - Reserved Expansion: {+var}
        'http://example.com/{+hello}'            | [hello: "Hello World!"]                            | 'http://example.com/Hello%20World!'
        'http://example.com/{+hello}'            | [hello: "foo/bar"]                                 | 'http://example.com/foo/bar'
        'http://example.com/{+hello}'            | [hello: ""]                                        | 'http://example.com/'
        'http://example.com/{+half}'             | [half: '50%']                                      | 'http://example.com/50%25'
        'http://example.com/{base}index'         | [base: 'http://example.com/home/']                 | 'http://example.com/http%3A%2F%2Fexample.com%2Fhome%2Findex'
        'http://example.com/{+base}index'        | [base: 'http://example.com/home/']                 | 'http://example.com/http://example.com/home/index'
        'http://example.com/O{+empty}X'          | [empty: '']                                        | 'http://example.com/OX'
        'http://example.com/O{+undef}X'          | [:]                                                | 'http://example.com/OX'
        'http://example.com{+path}/here'         | [path: "/foo/bar"]                                 | 'http://example.com/foo/bar/here'
        'http://example.com/here?ref={+path}'    | [path: "/foo/bar"]                                 | 'http://example.com/here?ref=/foo/bar'
        'http://example.com/up{+path}{var}/here' | [path: "/foo/bar", var: 'value']                   | 'http://example.com/up/foo/barvalue/here'
        'http://example.com/{+x,hello,y}'        | [x: 1024, y: 768, hello: "Hello World!"]           | 'http://example.com/1024,Hello%20World!,768'
        'http://example.com{+path,x}/here'       | [path: "/foo/bar", x: 1024]                        | 'http://example.com/foo/bar,1024/here'
        'http://example.com{+path:6}/here'       | [path: "/foo/bar"]                                 | 'http://example.com/foo/b/here' // Section 3.2.3 - Level 4 - Reserved expansion with value modifiers
        'http://example.com/{+list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/red,green,blue'
        'http://example.com/{+list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com/red,green,blue'
        'http://example.com/{+keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/semi,;,dot,.,comma,,'
        'http://example.com/{+keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/semi=;,dot=.,comma=,'
        'http://example.com/{#var}'              | [var: 'value']                                     | 'http://example.com/#value' // Section 3.2.4 - Level 2 - Fragment Expansion: {#var}
        'http://example.com/{#hello}'            | [hello: "Hello World!"]                            | 'http://example.com/#Hello%20World!'
        'http://example.com/{#half}'             | [half: '50%']                                      | 'http://example.com/#50%25'
        'http://example.com/foo{#empty}'         | [empty: '']                                        | 'http://example.com/foo#'
        'http://example.com/foo{#undef}'         | [:]                                                | 'http://example.com/foo'
        'http://example.com/X{#var}'             | [var: 'value']                                     | 'http://example.com/X#value'
        'http://example.com/X{#hello}'           | [hello: "Hello World!", var: 'value']              | 'http://example.com/X#Hello%20World!'
        'http://example.com/{#x,hello,y}'        | [x: 1024, y: 768, hello: "Hello World!"]           | 'http://example.com/#1024,Hello%20World!,768' // Section 3.2.4 - Level 3 - Fragment Expansion: {#var}
        'http://example.com/{#path,x}/here'      | [path: "/foo/bar", x: 1024]                        | 'http://example.com/#/foo/bar,1024/here'
        'http://example.com/{#path:6}/here'      | [path: "/foo/bar"]                                 | 'http://example.com/#/foo/b/here' // Section 3.2.4 - Level 4 - Fragment expansion with value modifiers
        'http://example.com/{#list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/#red,green,blue'
        'http://example.com/{#list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com/#red,green,blue'
        'http://example.com/{#keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/#semi,;,dot,.,comma,,'
        'http://example.com/{#keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/#semi=;,dot=.,comma=,'
        'http://example.com/X{.var}'             | [var: 'value']                                     | 'http://example.com/X.value' // Section 3.2.5 - Level 3 - Label Expansion with Dot-Prefix: {.var}
        'http://example.com/X{.empty}'           | [empty: '']                                        | 'http://example.com/X.'
        'http://example.com/X{.undef}'           | [:]                                                | 'http://example.com/X'
        'http://example.com/X{.x,y}'             | [x: 1024, y: 768]                                  | 'http://example.com/X.1024.768'
        'http://example.com/{.who}'              | [who: 'fred']                                      | 'http://example.com/.fred'
        'http://example.com/{.who,who}'          | [who: 'fred']                                      | 'http://example.com/.fred.fred'
        'http://example.com/{.half,who}'         | [half: '50%', who: 'fred']                         | 'http://example.com/.50%25.fred'
        'http://example.com/www{.dom*}'          | [dom: ["example", "com"]]                          | 'http://example.com/www.example.com'
        'http://example.com/X{.var:3}'           | [var: 'value']                                     | 'http://example.com/X.val' // Section 3.2.5 - Level 4 - Label expansion, dot-prefixed
        'http://example.com/X{.list*}'           | [list: ['red', 'green', 'blue']]                   | 'http://example.com/X.red.green.blue'
        'http://example.com/X{.list}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com/X.red,green,blue'
        'http://example.com/X{.keys}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/X.semi,%3B,dot,.,comma,%2C'
        'http://example.com/X{.keys*}'           | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/X.semi=%3B.dot=..comma=%2C'
        'http://example.com/X{.empty_keys}'      | [empty_keys: [:]]                                  | 'http://example.com/X'
        'http://example.com/X{.empty_keys}'      | [empty_keys: []]                                   | 'http://example.com/X'
        'http://example.com{/who}'               | [who: 'fred']                                      | 'http://example.com/fred' // Section 3.2.6 - Level 3 - Path Segment Expansion: {/var}
        'http://example.com{/who,who}'           | [who: 'fred']                                      | 'http://example.com/fred/fred'
        'http://example.com{/half,who}'          | [half: '50%', who: 'fred']                         | 'http://example.com/50%25/fred'
        'http://example.com{/who,dub}'           | [who: 'fred', dub: 'me/too']                       | 'http://example.com/fred/me%2Ftoo'
        'http://example.com{/var}'               | [var: 'value']                                     | 'http://example.com/value'
        'http://example.com{/var,undef}'         | [var: 'value']                                     | 'http://example.com/value'
        'http://example.com{/var,empty}'         | [var: 'value', empty: '']                          | 'http://example.com/value/'
        'http://example.com{/var,x}/here'        | [var: 'value', x: 1024]                            | 'http://example.com/value/1024/here'
        'http://example.com{/var:1,var}'         | [var: 'value']                                     | 'http://example.com/v/value' // Section 3.2.6 - Level 4 - Path Segment Expansion: {/var}
        'http://example.com{/list}'              | [list: ['red', 'green', 'blue']]                   | 'http://example.com/red,green,blue'
        'http://example.com{/list*}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/red/green/blue'
        'http://example.com{/list*,path:4}'      | [list: ['red', 'green', 'blue'], path: "/foo/bar"] | 'http://example.com/red/green/blue/%2Ffoo'
        'http://example.com{/keys}'              | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/semi,%3B,dot,.,comma,%2C'
        'http://example.com{/keys*}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/semi=%3B/dot=./comma=%2C'
        'http://example.com/{;who}'              | [who: 'fred']                                      | 'http://example.com/;who=fred' // Section 3.2.7 - Level 3 - Path-Style Parameter Expansion: {;var}
        'http://example.com/{;half}'             | [half: '50%']                                      | 'http://example.com/;half=50%25'
        'http://example.com/{;empty}'            | [empty: '']                                        | 'http://example.com/;empty'
        'http://example.com/{;v,empty,who}'      | [v: 6, empty: '', who: 'fred']                     | 'http://example.com/;v=6;empty;who=fred'
        'http://example.com/{;v,undef,who}'      | [v: 6, who: 'fred']                                | 'http://example.com/;v=6;who=fred'
        'http://example.com/{;x,y}'              | [x: 1024, y: 768]                                  | 'http://example.com/;x=1024;y=768'
        'http://example.com/{;x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com/;x=1024;y=768;empty'
        'http://example.com/{;x,y,undef}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com/;x=1024;y=768'
        'http://example.com/{;hello:5}'          | [hello: "Hello World!"]                            | 'http://example.com/;hello=Hello' // Section 3.2.7 - Level 4 - Path-Style Parameter Expansion: {;var}
        'http://example.com/{;list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/;list=red,green,blue'
        'http://example.com/{;list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com/;list=red;list=green;list=blue'
        'http://example.com/{;keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/;keys=semi,%3B,dot,.,comma,%2C'
        'http://example.com/{;keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/;semi=%3B;dot=.;comma=%2C'
        'http://example.com/{?who}'              | [who: 'fred']                                      | 'http://example.com/?who=fred' // Section 3.2.8 - Level 3 - Form-Style Query Expansion: {?var}
        'http://example.com/{?half}'             | [half: '50%']                                      | 'http://example.com/?half=50%25'
        'http://example.com/{?x,y}'              | [x: 1024, y: 768, empty: '']                       | 'http://example.com/?x=1024&y=768'
        'http://example.com/{?x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com/?x=1024&y=768&empty='
        'http://example.com/{?x,y,undef}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com/?x=1024&y=768'
        'http://example.com/{?var:3}'            | [var: 'value']                                     | 'http://example.com/?var=val' // Section 3.2.8 - Level 4 - Form-Style Query Expansion: {?var}
        'http://example.com/{?list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/?list=red,green,blue'
        'http://example.com/{?list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com/?list=red&list=green&list=blue'
        'http://example.com/{?keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/?keys=semi,%3B,dot,.,comma,%2C'
        'http://example.com/{?keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/?semi=%3B&dot=.&comma=%2C'
        'http://example.com/?fixed=yes{&x}'      | [x: 1024]                                          | 'http://example.com/?fixed=yes&x=1024' // Section 3.2.9 - Level 3 - Form-style query continuation
        'http://example.com/{&x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com/&x=1024&y=768&empty='
        'http://example.com/{&var:3}'            | [var: 'value']                                     | 'http://example.com/&var=val' // Section 3.2.9 - Level 4 - Form-style query continuation
        'http://example.com/{&list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com/&list=red,green,blue'
        'http://example.com/{&list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com/&list=red&list=green&list=blue'
        'http://example.com/{&keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/&keys=semi,%3B,dot,.,comma,%2C'
        'http://example.com/{&keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com/&semi=%3B&dot=.&comma=%2C'
        'http://example.com/foo{?query,number}'  | [query: 'mycelium', number: 100]                   | 'http://example.com/foo?query=mycelium&number=100'
        'http://example.com/foo{?query,number}'  | [number: 100]                                      | 'http://example.com/foo?number=100'
        'http://example.com/foo{?req*}'          | [req: [number: 100, name: null]]                   | 'http://example.com/foo?number=100'
    }


    @Unroll
    void "Test expand URI template #template with arguments #arguments for full URL and port"() {
        given:
        UriTemplate uriTemplate = new UriTemplate(template)

        expect: 'See https://tools.ietf.org/html/rfc6570#section-2.4.1'
        uriTemplate.expand(arguments) == result

        where:
        template                                      | arguments                                          | result
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value']                                       | 'http://example.com:8080/v/value/p'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val']                             | 'http://example.com:8080/v/value/p?o=val'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', m: 'val']                             | 'http://example.com:8080/v/value/p?m=val'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', s: 'val']                             | 'http://example.com:8080/v/value/p?s=val'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val1', m: 'val2', s: 'val3']      | 'http://example.com:8080/v/value/p?o=val1&m=val2&s=val3'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', m: 'val2', s: 'val3']                 | 'http://example.com:8080/v/value/p?m=val2&s=val3'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val1', s: 'val3']                 | 'http://example.com:8080/v/value/p?o=val1&s=val3'
        'http://example.com:8080/v/{v}/p{?o,m,s}'     | [v: 'value', o: 'val1', m: 'val2'           ]      | 'http://example.com:8080/v/value/p?o=val1&m=val2'
        'http://example.com:8080{+path,x}/here'       | [path: "/foo/bar", x: 1024]                        | 'http://example.com:8080/foo/bar,1024/here'
        'http://example.com:8080/{var}'               | [var: 'value']                                     | 'http://example.com:8080/value' // Section 2.4.1 - Prefix Values
        'http://example.com:8080/{var:20}'            | [var: 'value']                                     | 'http://example.com:8080/value'
        'http://example.com:8080/{var:3}'             | [var: 'value']                                     | 'http://example.com:8080/val'
        'http://example.com:8080/{semi}'              | [semi: ';']                                        | 'http://example.com:8080/%3B'
        'http://example.com:8080/{semi:2}'            | [semi: ';']                                        | 'http://example.com:8080/%3B'
        'http://example.com:8080/find{?year*}'        | [year: ["1965", "2000", "2012"]]                   | 'http://example.com:8080/find?year=1965&year=2000&year=2012' // Section 2.4.2.  Composite Values
        'http://example.com:8080/www{.dom*}'          | [dom: ["example", "com"]]                          | 'http://example.com:8080/www.example.com'
        'http://example.com:8080/{count}'             | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/one,two,three' // Section 3.2.1  Variable Expansion
        'http://example.com:8080/{count*}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/one,two,three'
        'http://example.com:8080/{/count}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080//one,two,three'
        'http://example.com:8080/{/count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080//one/two/three'
        'http://example.com:8080/{;count}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/;count=one,two,three'
        'http://example.com:8080/{;count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/;count=one;count=two;count=three'
        'http://example.com:8080/{?count}'            | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/?count=one,two,three'
        'http://example.com:8080/{?count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/?count=one&count=two&count=three'
        'http://example.com:8080/{&count*}'           | [count: ['one', 'two', 'three']]                   | 'http://example.com:8080/&count=one&count=two&count=three'
        'http://example.com:8080/{var}'               | [var: ['value']]                                   | 'http://example.com:8080/value' // Section 3.2.2 - Level 1 - Simple String Expansion: {var}
        'http://example.com:8080/{hello}'             | [hello: "Hello World!"]                            | 'http://example.com:8080/Hello%20World%21'
        'http://example.com:8080/{half}'              | [half: '50%']                                      | 'http://example.com:8080/50%25'
        'http://example.com:8080/O{empty}X'           | [empty: '']                                        | 'http://example.com:8080/OX'
        'http://example.com:8080/O{undef}X'           | [:]                                                | 'http://example.com:8080/OX'
        'http://example.com:8080/{x,y}'               | [x: 1024, y: 768]                                  | 'http://example.com:8080/1024,768'
        'http://example.com:8080/?{x,empty}'          | [x: 1024, empty: '']                               | 'http://example.com:8080/?1024,'
        'http://example.com:8080/?{x,undef}'          | [x: 1024, empty: '']                               | 'http://example.com:8080/?1024'
        'http://example.com:8080/?{undef,y}'          | [y: 768]                                           | 'http://example.com:8080/?768'
        'http://example.com:8080/map?{x,y}'           | [x: 1024, y: 768]                                  | 'http://example.com:8080/map?1024,768' // Section 3.2.2 - Simple String Expansion: {var}
        'http://example.com:8080/{x,hello,y}'         | [x: 1024, y: 768, hello: "Hello World!"]           | 'http://example.com:8080/1024,Hello%20World%21,768'
        'http://example.com:8080/{var:30}'            | [var: 'value']                                     | 'http://example.com:8080/value' // Section 3.2.2 - Level 4 - Simple String Expansion: {var}
        'http://example.com:8080/{list}'              | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/red,green,blue'
        'http://example.com:8080/{list*}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/red,green,blue'
        'http://example.com:8080/{keys}'              | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/semi,%3B,dot,.,comma,%2C'
        'http://example.com:8080/{keys*}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/semi=%3B,dot=.,comma=%2C'
        'http://example.com:8080/{+var}'              | [var: 'value']                                     | 'http://example.com:8080/value' // Section 3.2.3 - Level 2 - Reserved Expansion: {+var}
        'http://example.com:8080/{+hello}'            | [hello: "Hello World!"]                            | 'http://example.com:8080/Hello%20World!'
        'http://example.com:8080/{+half}'             | [half: '50%']                                      | 'http://example.com:8080/50%25'
        'http://example.com:8080/{base}index'         | [base: 'http://example.com/home/']                 | 'http://example.com:8080/http%3A%2F%2Fexample.com%2Fhome%2Findex'
        'http://example.com:8080/{+base}index'        | [base: 'http://example.com/home/']                 | 'http://example.com:8080/http://example.com/home/index'
        'http://example.com:8080/O{+empty}X'          | [empty: '']                                        | 'http://example.com:8080/OX'
        'http://example.com:8080/O{+undef}X'          | [:]                                                | 'http://example.com:8080/OX'
        'http://example.com:8080{+path}/here'         | [path: "/foo/bar"]                                 | 'http://example.com:8080/foo/bar/here'
        'http://example.com:8080/here?ref={+path}'    | [path: "/foo/bar"]                                 | 'http://example.com:8080/here?ref=/foo/bar'
        'http://example.com:8080/up{+path}{var}/here' | [path: "/foo/bar", var: 'value']                   | 'http://example.com:8080/up/foo/barvalue/here'
        'http://example.com:8080/{+x,hello,y}'        | [x: 1024, y: 768, hello: "Hello World!"]           | 'http://example.com:8080/1024,Hello%20World!,768'
        'http://example.com:8080{+path,x}/here'       | [path: "/foo/bar", x: 1024]                        | 'http://example.com:8080/foo/bar,1024/here'
        'http://example.com:8080{+path:6}/here'       | [path: "/foo/bar"]                                 | 'http://example.com:8080/foo/b/here' // Section 3.2.3 - Level 4 - Reserved expansion with value modifiers
        'http://example.com:8080/{+list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/red,green,blue'
        'http://example.com:8080/{+list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/red,green,blue'
        'http://example.com:8080/{+keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/semi,;,dot,.,comma,,'
        'http://example.com:8080/{+keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/semi=;,dot=.,comma=,'
        'http://example.com:8080/{#var}'              | [var: 'value']                                     | 'http://example.com:8080/#value' // Section 3.2.4 - Level 2 - Fragment Expansion: {#var}
        'http://example.com:8080/{#hello}'            | [hello: "Hello World!"]                            | 'http://example.com:8080/#Hello%20World!'
        'http://example.com:8080/{#half}'             | [half: '50%']                                      | 'http://example.com:8080/#50%25'
        'http://example.com:8080/foo{#empty}'         | [empty: '']                                        | 'http://example.com:8080/foo#'
        'http://example.com:8080/foo{#undef}'         | [:]                                                | 'http://example.com:8080/foo'
        'http://example.com:8080/X{#var}'             | [var: 'value']                                     | 'http://example.com:8080/X#value'
        'http://example.com:8080/X{#hello}'           | [hello: "Hello World!", var: 'value']              | 'http://example.com:8080/X#Hello%20World!'
        'http://example.com:8080/{#x,hello,y}'        | [x: 1024, y: 768, hello: "Hello World!"]           | 'http://example.com:8080/#1024,Hello%20World!,768' // Section 3.2.4 - Level 3 - Fragment Expansion: {#var}
        'http://example.com:8080/{#path,x}/here'      | [path: "/foo/bar", x: 1024]                        | 'http://example.com:8080/#/foo/bar,1024/here'
        'http://example.com:8080/{#path:6}/here'      | [path: "/foo/bar"]                                 | 'http://example.com:8080/#/foo/b/here' // Section 3.2.4 - Level 4 - Fragment expansion with value modifiers
        'http://example.com:8080/{#list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/#red,green,blue'
        'http://example.com:8080/{#list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/#red,green,blue'
        'http://example.com:8080/{#keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/#semi,;,dot,.,comma,,'
        'http://example.com:8080/{#keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/#semi=;,dot=.,comma=,'
        'http://example.com:8080/X{.var}'             | [var: 'value']                                     | 'http://example.com:8080/X.value' // Section 3.2.5 - Level 3 - Label Expansion with Dot-Prefix: {.var}
        'http://example.com:8080/X{.empty}'           | [empty: '']                                        | 'http://example.com:8080/X.'
        'http://example.com:8080/X{.undef}'           | [:]                                                | 'http://example.com:8080/X'
        'http://example.com:8080/X{.x,y}'             | [x: 1024, y: 768]                                  | 'http://example.com:8080/X.1024.768'
        'http://example.com:8080/{.who}'              | [who: 'fred']                                      | 'http://example.com:8080/.fred'
        'http://example.com:8080/{.who,who}'          | [who: 'fred']                                      | 'http://example.com:8080/.fred.fred'
        'http://example.com:8080/{.half,who}'         | [half: '50%', who: 'fred']                         | 'http://example.com:8080/.50%25.fred'
        'http://example.com:8080/www{.dom*}'          | [dom: ["example", "com"]]                          | 'http://example.com:8080/www.example.com'
        'http://example.com:8080/X{.var:3}'           | [var: 'value']                                     | 'http://example.com:8080/X.val' // Section 3.2.5 - Level 4 - Label expansion, dot-prefixed
        'http://example.com:8080/X{.list*}'           | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/X.red.green.blue'
        'http://example.com:8080/X{.list}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/X.red,green,blue'
        'http://example.com:8080/X{.keys}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/X.semi,%3B,dot,.,comma,%2C'
        'http://example.com:8080/X{.keys*}'           | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/X.semi=%3B.dot=..comma=%2C'
        'http://example.com:8080/X{.empty_keys}'      | [empty_keys: [:]]                                  | 'http://example.com:8080/X'
        'http://example.com:8080/X{.empty_keys}'      | [empty_keys: []]                                   | 'http://example.com:8080/X'
        'http://example.com:8080{/who}'               | [who: 'fred']                                      | 'http://example.com:8080/fred' // Section 3.2.6 - Level 3 - Path Segment Expansion: {/var}
        'http://example.com:8080{/who,who}'           | [who: 'fred']                                      | 'http://example.com:8080/fred/fred'
        'http://example.com:8080{/half,who}'          | [half: '50%', who: 'fred']                         | 'http://example.com:8080/50%25/fred'
        'http://example.com:8080{/who,dub}'           | [who: 'fred', dub: 'me/too']                       | 'http://example.com:8080/fred/me%2Ftoo'
        'http://example.com:8080{/var}'               | [var: 'value']                                     | 'http://example.com:8080/value'
        'http://example.com:8080{/var,undef}'         | [var: 'value']                                     | 'http://example.com:8080/value'
        'http://example.com:8080{/var,empty}'         | [var: 'value', empty: '']                          | 'http://example.com:8080/value/'
        'http://example.com:8080{/var,x}/here'        | [var: 'value', x: 1024]                            | 'http://example.com:8080/value/1024/here'
        'http://example.com:8080{/var:1,var}'         | [var: 'value']                                     | 'http://example.com:8080/v/value' // Section 3.2.6 - Level 4 - Path Segment Expansion: {/var}
        'http://example.com:8080{/list}'              | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/red,green,blue'
        'http://example.com:8080{/list*}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/red/green/blue'
        'http://example.com:8080{/list*,path:4}'      | [list: ['red', 'green', 'blue'], path: "/foo/bar"] | 'http://example.com:8080/red/green/blue/%2Ffoo'
        'http://example.com:8080{/keys}'              | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/semi,%3B,dot,.,comma,%2C'
        'http://example.com:8080{/keys*}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/semi=%3B/dot=./comma=%2C'
        'http://example.com:8080/{;who}'              | [who: 'fred']                                      | 'http://example.com:8080/;who=fred' // Section 3.2.7 - Level 3 - Path-Style Parameter Expansion: {;var}
        'http://example.com:8080/{;half}'             | [half: '50%']                                      | 'http://example.com:8080/;half=50%25'
        'http://example.com:8080/{;empty}'            | [empty: '']                                        | 'http://example.com:8080/;empty'
        'http://example.com:8080/{;v,empty,who}'      | [v: 6, empty: '', who: 'fred']                     | 'http://example.com:8080/;v=6;empty;who=fred'
        'http://example.com:8080/{;v,undef,who}'      | [v: 6, who: 'fred']                                | 'http://example.com:8080/;v=6;who=fred'
        'http://example.com:8080/{;x,y}'              | [x: 1024, y: 768]                                  | 'http://example.com:8080/;x=1024;y=768'
        'http://example.com:8080/{;x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com:8080/;x=1024;y=768;empty'
        'http://example.com:8080/{;x,y,undef}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com:8080/;x=1024;y=768'
        'http://example.com:8080/{;hello:5}'          | [hello: "Hello World!"]                            | 'http://example.com:8080/;hello=Hello' // Section 3.2.7 - Level 4 - Path-Style Parameter Expansion: {;var}
        'http://example.com:8080/{;list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/;list=red,green,blue'
        'http://example.com:8080/{;list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/;list=red;list=green;list=blue'
        'http://example.com:8080/{;keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/;keys=semi,%3B,dot,.,comma,%2C'
        'http://example.com:8080/{;keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/;semi=%3B;dot=.;comma=%2C'
        'http://example.com:8080/{?who}'              | [who: 'fred']                                      | 'http://example.com:8080/?who=fred' // Section 3.2.8 - Level 3 - Form-Style Query Expansion: {?var}
        'http://example.com:8080/{?half}'             | [half: '50%']                                      | 'http://example.com:8080/?half=50%25'
        'http://example.com:8080/{?x,y}'              | [x: 1024, y: 768, empty: '']                       | 'http://example.com:8080/?x=1024&y=768'
        'http://example.com:8080/{?x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com:8080/?x=1024&y=768&empty='
        'http://example.com:8080/{?x,y,undef}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com:8080/?x=1024&y=768'
        'http://example.com:8080/{?var:3}'            | [var: 'value']                                     | 'http://example.com:8080/?var=val' // Section 3.2.8 - Level 4 - Form-Style Query Expansion: {?var}
        'http://example.com:8080/{?list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/?list=red,green,blue'
        'http://example.com:8080/{?list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/?list=red&list=green&list=blue'
        'http://example.com:8080/{?keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/?keys=semi,%3B,dot,.,comma,%2C'
        'http://example.com:8080/{?keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/?semi=%3B&dot=.&comma=%2C'
        'http://example.com:8080/?fixed=yes{&x}'      | [x: 1024]                                          | 'http://example.com:8080/?fixed=yes&x=1024' // Section 3.2.9 - Level 3 - Form-style query continuation
        'http://example.com:8080/{&x,y,empty}'        | [x: 1024, y: 768, empty: '']                       | 'http://example.com:8080/&x=1024&y=768&empty='
        'http://example.com:8080/{&var:3}'            | [var: 'value']                                     | 'http://example.com:8080/&var=val' // Section 3.2.9 - Level 4 - Form-style query continuation
        'http://example.com:8080/{&list}'             | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/&list=red,green,blue'
        'http://example.com:8080/{&list*}'            | [list: ['red', 'green', 'blue']]                   | 'http://example.com:8080/&list=red&list=green&list=blue'
        'http://example.com:8080/{&keys}'             | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/&keys=semi,%3B,dot,.,comma,%2C'
        'http://example.com:8080/{&keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: ',']]      | 'http://example.com:8080/&semi=%3B&dot=.&comma=%2C'
        'http://example.com:8080/{&keys*}'            | [keys: ['semi': ';', 'dot': '.', comma: null]]     | 'http://example.com:8080/&semi=%3B&dot=.'
    }

}
