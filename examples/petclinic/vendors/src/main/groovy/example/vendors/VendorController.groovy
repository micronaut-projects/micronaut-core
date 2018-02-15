/*
 * Copyright 2018 original authors
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
package example.vendors

import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Post
import org.particleframework.validation.Validated

import javax.inject.Singleton
import javax.validation.constraints.NotBlank

/**
 * @author graemerocher
 * @since 1.0
 */
@Controller('/${vendors.api.version}/vendors')
@Singleton
@Validated
class VendorController {

    final VendorService vendorService

    VendorController(VendorService vendorService) {
        this.vendorService = vendorService
    }

    @Get('/')
    List<Vendor> list() {
        vendorService.list()
    }

    @Post('/')
    Vendor save(@NotBlank String name) {
        vendorService.save(name)
    }
}
