/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery.route53

import com.amazonaws.AmazonWebServiceRequest
import com.amazonaws.ResponseMetadata
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync
import com.amazonaws.services.servicediscovery.model.CreatePrivateDnsNamespaceRequest
import com.amazonaws.services.servicediscovery.model.CreatePrivateDnsNamespaceResult
import com.amazonaws.services.servicediscovery.model.CreatePublicDnsNamespaceRequest
import com.amazonaws.services.servicediscovery.model.CreatePublicDnsNamespaceResult
import com.amazonaws.services.servicediscovery.model.CreateServiceRequest
import com.amazonaws.services.servicediscovery.model.CreateServiceResult
import com.amazonaws.services.servicediscovery.model.DeleteNamespaceRequest
import com.amazonaws.services.servicediscovery.model.DeleteNamespaceResult
import com.amazonaws.services.servicediscovery.model.DeleteServiceRequest
import com.amazonaws.services.servicediscovery.model.DeleteServiceResult
import com.amazonaws.services.servicediscovery.model.DeregisterInstanceRequest
import com.amazonaws.services.servicediscovery.model.DeregisterInstanceResult
import com.amazonaws.services.servicediscovery.model.GetInstanceRequest
import com.amazonaws.services.servicediscovery.model.GetInstanceResult
import com.amazonaws.services.servicediscovery.model.GetInstancesHealthStatusRequest
import com.amazonaws.services.servicediscovery.model.GetInstancesHealthStatusResult
import com.amazonaws.services.servicediscovery.model.GetNamespaceRequest
import com.amazonaws.services.servicediscovery.model.GetNamespaceResult
import com.amazonaws.services.servicediscovery.model.GetOperationRequest
import com.amazonaws.services.servicediscovery.model.GetOperationResult
import com.amazonaws.services.servicediscovery.model.GetServiceRequest
import com.amazonaws.services.servicediscovery.model.GetServiceResult
import com.amazonaws.services.servicediscovery.model.InstanceSummary
import com.amazonaws.services.servicediscovery.model.ListInstancesRequest
import com.amazonaws.services.servicediscovery.model.ListInstancesResult
import com.amazonaws.services.servicediscovery.model.ListNamespacesRequest
import com.amazonaws.services.servicediscovery.model.ListNamespacesResult
import com.amazonaws.services.servicediscovery.model.ListOperationsRequest
import com.amazonaws.services.servicediscovery.model.ListOperationsResult
import com.amazonaws.services.servicediscovery.model.ListServicesRequest
import com.amazonaws.services.servicediscovery.model.ListServicesResult
import com.amazonaws.services.servicediscovery.model.Operation
import com.amazonaws.services.servicediscovery.model.RegisterInstanceRequest
import com.amazonaws.services.servicediscovery.model.RegisterInstanceResult
import com.amazonaws.services.servicediscovery.model.ServiceSummary
import com.amazonaws.services.servicediscovery.model.UpdateInstanceCustomHealthStatusRequest
import com.amazonaws.services.servicediscovery.model.UpdateInstanceCustomHealthStatusResult
import com.amazonaws.services.servicediscovery.model.UpdateServiceRequest
import com.amazonaws.services.servicediscovery.model.UpdateServiceResult
import io.micronaut.context.annotation.Requires
import spock.mock.DetachedMockFactory

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.function.Supplier

@Requires(env = 'SILLY')
class AWSServiceDiscoveryAsyncMock implements AWSServiceDiscoveryAsync {


        String namespaceId = "asdb123"
        String serviceId = "123abcdf"
        String createdInstanceId = "i-12123321"
        DetachedMockFactory mockFactory = new DetachedMockFactory()


        @Override
        Future<CreatePrivateDnsNamespaceResult> createPrivateDnsNamespaceAsync(CreatePrivateDnsNamespaceRequest createPrivateDnsNamespaceRequest) {
            return null
        }

        @Override
        Future<CreatePrivateDnsNamespaceResult> createPrivateDnsNamespaceAsync(CreatePrivateDnsNamespaceRequest createPrivateDnsNamespaceRequest, AsyncHandler<CreatePrivateDnsNamespaceRequest, CreatePrivateDnsNamespaceResult> asyncHandler) {
            return null
        }

        @Override
        Future<CreatePublicDnsNamespaceResult> createPublicDnsNamespaceAsync(CreatePublicDnsNamespaceRequest createPublicDnsNamespaceRequest) {
            return null
        }

        @Override
        Future<CreatePublicDnsNamespaceResult> createPublicDnsNamespaceAsync(CreatePublicDnsNamespaceRequest createPublicDnsNamespaceRequest, AsyncHandler<CreatePublicDnsNamespaceRequest, CreatePublicDnsNamespaceResult> asyncHandler) {
            return null
        }

        @Override
        Future<CreateServiceResult> createServiceAsync(CreateServiceRequest createServiceRequest) {
            return null
        }

        @Override
        Future<CreateServiceResult> createServiceAsync(CreateServiceRequest createServiceRequest, AsyncHandler<CreateServiceRequest, CreateServiceResult> asyncHandler) {
            return null
        }

        @Override
        Future<DeleteNamespaceResult> deleteNamespaceAsync(DeleteNamespaceRequest deleteNamespaceRequest) {
            return null
        }

        @Override
        Future<DeleteNamespaceResult> deleteNamespaceAsync(DeleteNamespaceRequest deleteNamespaceRequest, AsyncHandler<DeleteNamespaceRequest, DeleteNamespaceResult> asyncHandler) {
            return null
        }

        @Override
        Future<DeleteServiceResult> deleteServiceAsync(DeleteServiceRequest deleteServiceRequest) {
            return null
        }

        @Override
        Future<DeleteServiceResult> deleteServiceAsync(DeleteServiceRequest deleteServiceRequest, AsyncHandler<DeleteServiceRequest, DeleteServiceResult> asyncHandler) {
            return null
        }

        @Override
        Future<DeregisterInstanceResult> deregisterInstanceAsync(DeregisterInstanceRequest deregisterInstanceRequest) {
            return CompletableFuture.supplyAsync(new Supplier<DeregisterInstanceResult>() {
                @Override
                public DeregisterInstanceResult get() {
                    try {
                        DeregisterInstanceResult deregisterInstanceResult = new DeregisterInstanceResult()
                        deregisterInstanceResult.operationId = "123123123213"
                        return deregisterInstanceResult
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e)
                    }

                }

            })

        }


        @Override
        Future<DeregisterInstanceResult> deregisterInstanceAsync(DeregisterInstanceRequest deregisterInstanceRequest, AsyncHandler<DeregisterInstanceRequest, DeregisterInstanceResult> asyncHandler) {
            return deregisterInstanceAsync(deregisterInstanceRequest)
        }

        @Override
        Future<GetInstanceResult> getInstanceAsync(GetInstanceRequest getInstanceRequest) {
            return null
        }

        @Override
        Future<GetInstanceResult> getInstanceAsync(GetInstanceRequest getInstanceRequest, AsyncHandler<GetInstanceRequest, GetInstanceResult> asyncHandler) {
            return null
        }

        @Override
        Future<GetInstancesHealthStatusResult> getInstancesHealthStatusAsync(GetInstancesHealthStatusRequest getInstancesHealthStatusRequest) {
            return null
        }

        @Override
        Future<GetInstancesHealthStatusResult> getInstancesHealthStatusAsync(GetInstancesHealthStatusRequest getInstancesHealthStatusRequest, AsyncHandler<GetInstancesHealthStatusRequest, GetInstancesHealthStatusResult> asyncHandler) {
            return null
        }

        @Override
        Future<GetNamespaceResult> getNamespaceAsync(GetNamespaceRequest getNamespaceRequest) {
            return null
        }

        @Override
        Future<GetNamespaceResult> getNamespaceAsync(GetNamespaceRequest getNamespaceRequest, AsyncHandler<GetNamespaceRequest, GetNamespaceResult> asyncHandler) {
            return null
        }

        @Override
        Future<GetOperationResult> getOperationAsync(GetOperationRequest getOperationRequest) {

            return CompletableFuture.supplyAsync(new Supplier<GetOperationResult>() {
                @Override
                public GetOperationResult get() {
                    try {
                        GetOperationResult operationResult = new GetOperationResult()
                        Operation operation = new Operation()
                        operation.id = "123456"
                        operation.status = "SUCCESS"
                        operationResult.operation = operation
                        return operationResult
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e)
                    }

                }

            })


        }

        @Override
        Future<GetOperationResult> getOperationAsync(GetOperationRequest getOperationRequest, AsyncHandler<GetOperationRequest, GetOperationResult> asyncHandler) {
            return getOperationAsync(getOperationRequest)
        }

        @Override
        Future<GetServiceResult> getServiceAsync(GetServiceRequest getServiceRequest) {
            return null
        }

        @Override
        Future<GetServiceResult> getServiceAsync(GetServiceRequest getServiceRequest, AsyncHandler<GetServiceRequest, GetServiceResult> asyncHandler) {
            return null
        }

        @Override
        Future<ListInstancesResult> listInstancesAsync(ListInstancesRequest listInstancesRequest) {
            return listInstancesAsync(listInstancesRequest,null)
        }

        @Override
        Future<ListInstancesResult> listInstancesAsync(ListInstancesRequest listInstancesRequest, AsyncHandler<ListInstancesRequest, ListInstancesResult> asyncHandler) {

            return CompletableFuture.supplyAsync(new Supplier<ListInstancesResult>() {
                @Override
                public ListInstancesResult get() {
                    try {
                        ListInstancesResult listInstancesResult = new ListInstancesResult()
                        InstanceSummary instanceSummary = new InstanceSummary()
                        instanceSummary.id = createdInstanceId
                        instanceSummary.addAttributesEntry(("URI"), "/v1")
                        listInstancesResult.instances = [instanceSummary] as List<InstanceSummary>
                        return listInstancesResult
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e)
                    }

                }

            })


        }

        @Override
        Future<ListNamespacesResult> listNamespacesAsync(ListNamespacesRequest listNamespacesRequest) {
            return null
        }

        @Override
        Future<ListNamespacesResult> listNamespacesAsync(ListNamespacesRequest listNamespacesRequest, AsyncHandler<ListNamespacesRequest, ListNamespacesResult> asyncHandler) {
            return null
        }

        @Override
        Future<ListOperationsResult> listOperationsAsync(ListOperationsRequest listOperationsRequest) {
            return null
        }

        @Override
        Future<ListOperationsResult> listOperationsAsync(ListOperationsRequest listOperationsRequest, AsyncHandler<ListOperationsRequest, ListOperationsResult> asyncHandler) {
            return null
        }

        @Override
        Future<ListServicesResult> listServicesAsync(ListServicesRequest listServicesRequest) {

            return CompletableFuture.supplyAsync(new Supplier<ListServicesResult>() {
                @Override
                public ListServicesResult get() {
                    try {
                        ListServicesResult listServicesResult = new ListServicesResult()
                        ServiceSummary serviceSummary = new ServiceSummary()
                        serviceSummary.instanceCount = 1
                        serviceSummary.name = "123456"
                        serviceSummary.id = serviceId
                        listServicesResult.services = [serviceSummary] as List<ServiceSummary>
                        listServicesResult
                        return listServicesResult
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e)
                    }

                }

            })
        }

        @Override
        Future<ListServicesResult> listServicesAsync(ListServicesRequest listServicesRequest, AsyncHandler<ListServicesRequest, ListServicesResult> asyncHandler) {
            return listServicesAsync(listServicesRequest)
        }

        @Override
        Future<RegisterInstanceResult> registerInstanceAsync(RegisterInstanceRequest registerInstanceRequest) {

            return CompletableFuture.supplyAsync(new Supplier<RegisterInstanceResult>() {
                @Override
                public RegisterInstanceResult get() {
                    try {
                        RegisterInstanceResult registerInstanceResult = new RegisterInstanceResult()
                        registerInstanceResult.operationId = "adslkdfaskljfdsaklj"
                        return registerInstanceResult
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e)
                    }

                }

            })
        }

        @Override
        Future<RegisterInstanceResult> registerInstanceAsync(RegisterInstanceRequest registerInstanceRequest, AsyncHandler<RegisterInstanceRequest, RegisterInstanceResult> asyncHandler) {
            return registerInstanceAsync(registerInstanceRequest)
        }

        @Override
        Future<UpdateInstanceCustomHealthStatusResult> updateInstanceCustomHealthStatusAsync(UpdateInstanceCustomHealthStatusRequest updateInstanceCustomHealthStatusRequest) {
            return null
        }

        @Override
        Future<UpdateInstanceCustomHealthStatusResult> updateInstanceCustomHealthStatusAsync(UpdateInstanceCustomHealthStatusRequest updateInstanceCustomHealthStatusRequest, AsyncHandler<UpdateInstanceCustomHealthStatusRequest, UpdateInstanceCustomHealthStatusResult> asyncHandler) {
            return null
        }

        @Override
        Future<UpdateServiceResult> updateServiceAsync(UpdateServiceRequest updateServiceRequest) {
            return null
        }

        @Override
        Future<UpdateServiceResult> updateServiceAsync(UpdateServiceRequest updateServiceRequest, AsyncHandler<UpdateServiceRequest, UpdateServiceResult> asyncHandler) {
            return null
        }

        @Override
        CreatePrivateDnsNamespaceResult createPrivateDnsNamespace(CreatePrivateDnsNamespaceRequest createPrivateDnsNamespaceRequest) {
            return null
        }

        @Override
        CreatePublicDnsNamespaceResult createPublicDnsNamespace(CreatePublicDnsNamespaceRequest createPublicDnsNamespaceRequest) {
            return null
        }

        @Override
        CreateServiceResult createService(CreateServiceRequest createServiceRequest) {
            return null
        }

        @Override
        DeleteNamespaceResult deleteNamespace(DeleteNamespaceRequest deleteNamespaceRequest) {
            return null
        }

        @Override
        DeleteServiceResult deleteService(DeleteServiceRequest deleteServiceRequest) {
            return null
        }

        @Override
        DeregisterInstanceResult deregisterInstance(DeregisterInstanceRequest deregisterInstanceRequest) {
            return null
        }

        @Override
        GetInstanceResult getInstance(GetInstanceRequest getInstanceRequest) {
            return null
        }

        @Override
        GetInstancesHealthStatusResult getInstancesHealthStatus(GetInstancesHealthStatusRequest getInstancesHealthStatusRequest) {
            return null
        }

        @Override
        GetNamespaceResult getNamespace(GetNamespaceRequest getNamespaceRequest) {
            return null
        }

        @Override
        GetOperationResult getOperation(GetOperationRequest getOperationRequest) {
            try {
                GetOperationResult operationResult = new GetOperationResult()
                Operation operation = new Operation()
                operation.id = "123456"
                operation.status = "SUCCESS"
                operationResult.operation = operation
                return operationResult
            } catch (InterruptedException e) {
                throw new IllegalStateException(e)
            }

        }

        @Override
        GetServiceResult getService(GetServiceRequest getServiceRequest) {
            return null
        }

        @Override
        ListInstancesResult listInstances(ListInstancesRequest listInstancesRequest) {
            return null
        }

        @Override
        ListNamespacesResult listNamespaces(ListNamespacesRequest listNamespacesRequest) {
            return null
        }

        @Override
        ListOperationsResult listOperations(ListOperationsRequest listOperationsRequest) {
            return null
        }

        @Override
        ListServicesResult listServices(ListServicesRequest listServicesRequest) {
            return null
        }

        @Override
        RegisterInstanceResult registerInstance(RegisterInstanceRequest registerInstanceRequest) {
            return null
        }

        @Override
        UpdateInstanceCustomHealthStatusResult updateInstanceCustomHealthStatus(UpdateInstanceCustomHealthStatusRequest updateInstanceCustomHealthStatusRequest) {
            return null
        }

        @Override
        UpdateServiceResult updateService(UpdateServiceRequest updateServiceRequest) {
            return null
        }

        @Override
        void shutdown() {

        }

        @Override
        ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request) {
            return null
        }
    }

