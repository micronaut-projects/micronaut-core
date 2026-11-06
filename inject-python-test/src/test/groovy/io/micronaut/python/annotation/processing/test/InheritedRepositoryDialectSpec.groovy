/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test

import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.python.compiler.PyronautCompiler

class InheritedRepositoryDialectSpec extends AbstractPythonTypeElementSpec {

    void 'an inherited annotated repository method retains each owning dialect (#firstDialect first)'() {
        given:
        def source = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, Join, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import CrudRepository

@dataclass
@MappedEntity
class Manufacturer:
    id: Annotated[int, Id]
    name: str

@dataclass
@MappedEntity
class Product:
    id: Annotated[int, Id]
    manufacturer: Manufacturer

class ProductRepository(CrudRepository[Product, int], ABC):
    @Join(value="manufacturer", type=Join.Type.FETCH)
    @abstractmethod
    def getById(self, id: int) -> Product | None:
        pass
'''
        [firstDialect, secondDialect].each { dialect ->
            source += """
@JdbcRepository(dialect="$dialect")
class ${dialect}ProductRepository(ProductRepository):
    pass
"""
        }

        when: 'both sibling repositories are processed in the same compiler invocation'
        def classLoader = PyronautCompiler.builder().pythonCode(source).build().buildClassLoader()
        def h2 = classLoader.loadClass('python.$H2ProductRepository$Intercepted$Definition').getDeclaredConstructor().newInstance()
        def oracle = classLoader.loadClass('python.$ORACLEProductRepository$Intercepted$Definition').getDeclaredConstructor().newInstance()
        def h2Method = h2.getRequiredMethod('getById', Integer.TYPE)
        def oracleMethod = oracle.getRequiredMethod('getById', Integer.TYPE)

        then: 'generated method metadata carries the owning repository dialect, not its sibling query'
        h2Method.stringValue(Query).get() == 'SELECT product_.`id`,product_.`manufacturer_id`,product_manufacturer_.`name` AS manufacturer_name FROM `product` product_ INNER JOIN `manufacturer` product_manufacturer_ ON product_.`manufacturer_id`=product_manufacturer_.`id` WHERE (product_.`id` = ?)'
        oracleMethod.stringValue(Query).get() == 'SELECT product_."ID",product_."MANUFACTURER_ID",product_manufacturer_."NAME" AS manufacturer_name FROM "PRODUCT" product_ INNER JOIN "MANUFACTURER" product_manufacturer_ ON product_."MANUFACTURER_ID"=product_manufacturer_."ID" WHERE (product_."ID" = ?)'
        !oracleMethod.stringValue(Query).get().contains('`')

        and: 'both inherited methods keep their join and nullable return'
        h2Method.getAnnotationValuesByType(Join)*.stringValue().collect { it.get() } == ['manufacturer']
        oracleMethod.getAnnotationValuesByType(Join)*.stringValue().collect { it.get() } == ['manufacturer']
        h2Method.returnType.asArgument().nullable
        oracleMethod.returnType.asArgument().nullable

        where:
        firstDialect | secondDialect
        'H2'         | 'ORACLE'
        'ORACLE'     | 'H2'
    }
}
