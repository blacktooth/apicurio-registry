/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.mt;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.apicurio.multitenant.api.datamodel.RegistryTenant;
import io.apicurio.multitenant.client.TenantManagerClient;
import io.apicurio.multitenant.client.TenantManagerClientImpl;
import io.apicurio.multitenant.client.exception.RegistryTenantNotFoundException;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ProfileManager;

/**
 * @author Fabian Martinez
 */
@ApplicationScoped
public class TenantMetadataService {

    @Inject
    @Current
    RegistryStorage storage;

    @Inject
    @ConfigProperty(name = "registry.enable.multitenancy")
    boolean multitenancyEnabled;

    @Inject
    @ConfigProperty(name = "registry.tenant.manager.url")
    Optional<String> tenantManagerUrl;

    private TenantManagerClient tenantManagerClient;

    public void init(@Observes StartupEvent ev) {
        System.out.println("Current profile " + ProfileManager.getActiveProfile());
        //we check if profile is prod, because some tests will fail to start quarkus app.
        //Those tests checks if multitenancy is supported and abort the test if needed
        if ("prod".equals(ProfileManager.getActiveProfile()) && multitenancyEnabled && !storage.supportsMultiTenancy()) {
            throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled "
                    + "but the storage implementation being used (" + storage.storageName() + ") does not support multitenancy");
        }
        if (multitenancyEnabled && tenantManagerUrl.isEmpty()) {
            throw new DeploymentException("Unsupported configuration, \"registry.enable.multitenancy\" is enabled "
                    + "but the no \"registry.tenant.manager.url\" is provided");
        }
        if (multitenancyEnabled) {
            this.tenantManagerClient = new TenantManagerClientImpl(tenantManagerUrl.get());
        }
    }

    public RegistryTenant getTenant(String tenantId) throws TenantNotFoundException {
        if (!multitenancyEnabled) {
            throw new UnsupportedOperationException("Multitenancy is not enabled");
        }

        try {
            return tenantManagerClient.getTenant(tenantId);
        } catch (RegistryTenantNotFoundException e) {
            throw new TenantNotFoundException(e.getMessage());
        }
    }

}