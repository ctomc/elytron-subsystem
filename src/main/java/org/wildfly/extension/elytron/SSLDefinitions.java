/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static org.jboss.as.controller.capability.RuntimeCapability.buildDynamicCapabilityName;
import static org.wildfly.extension.elytron.Capabilities.KEYSTORE_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGERS_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.KEY_MANAGERS_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.PROVIDERS_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronExtension.ELYTRON_1_0_0;
import static org.wildfly.extension.elytron.ElytronExtension.asStringIfDefined;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;

/**
 * Definitions for resources used to configure SSLContexts.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SSLDefinitions {

    static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition PROVIDER_LOADER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PROVIDER_LOADER, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition KEYSTORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.KEYSTORE, ModelType.STRING, false)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .build();

    static final SimpleAttributeDefinition PASSWORD = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PASSWORD, ModelType.STRING, true)
            .setAllowExpression(true)
            .setMinSize(1)
            .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
            .setDeprecated(ELYTRON_1_0_0) // Deprecate immediately as to be supplied by the vault.
            .build();

    static ResourceDefinition getKeyManagerDefinition() {
        final SimpleAttributeDefinition providerLoaderDefinition = setCapabilityReference(PROVIDERS_CAPABILITY, KEY_MANAGERS_CAPABILITY, PROVIDER_LOADER);
        final SimpleAttributeDefinition keystoreDefinition = setCapabilityReference(KEYSTORE_CAPABILITY, KEY_MANAGERS_CAPABILITY, KEYSTORE);

        AttributeDefinition[] attributes = new AttributeDefinition[] { ALGORITHM, providerLoaderDefinition, keystoreDefinition, PASSWORD };

        AbstractAddStepHandler add = new TrivialAddHandler<KeyManager[]>(KEY_MANAGERS_RUNTIME_CAPABILITY, KeyManager[].class, attributes) {

            @Override
            protected ValueSupplier<KeyManager[]> getValueSupplier(ServiceBuilder<KeyManager[]> serviceBuilder, OperationContext context, ModelNode model) throws OperationFailedException {
                final String algorithm = ALGORITHM.resolveModelAttribute(context, model).asString();
                final String password = asStringIfDefined(context, PASSWORD, model);

                String providerLoader = asStringIfDefined(context, providerLoaderDefinition, model);
                final InjectedValue<Provider[]> providersInjector = new InjectedValue<>();
                if (providerLoader != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                            Provider[].class, providersInjector);
                }

                String keyStore = asStringIfDefined(context, keystoreDefinition, model);
                final InjectedValue<KeyStore> keyStoreInjector = new InjectedValue<>();
                if (keyStore != null) {
                    serviceBuilder.addDependency(context.getCapabilityServiceName(
                            buildDynamicCapabilityName(PROVIDERS_CAPABILITY, providerLoader), Provider[].class),
                            Provider[].class, providersInjector);
                }

                return () -> {
                    Provider[] providers = providersInjector.getOptionalValue();
                    KeyManagerFactory keyManagerFactory = null;
                    if (providers != null) {
                        for (Provider current : providers) {
                            try {
                                // TODO - We could check the Services within each Provider to check there is one of the required type/algorithm
                                // However the same loop would need to remain as it is still possible a specific provider can't create it.
                                keyManagerFactory = KeyManagerFactory.getInstance(algorithm, current);
                                break;
                            } catch (NoSuchAlgorithmException ignored) {
                            }
                        }
                        throw ROOT_LOGGER.unableToCreateManagerFactory(KeyManagerFactory.class.getSimpleName(), algorithm);
                    } else {
                        try {
                            keyManagerFactory = KeyManagerFactory.getInstance(algorithm);
                        } catch (NoSuchAlgorithmException e) {
                            throw new StartException(e);
                        }
                    }

                    try {
                        keyManagerFactory.init(keyStoreInjector.getOptionalValue(), password != null ? password.toCharArray() : null);
                    } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
                        throw new StartException(e);
                    }

                    return keyManagerFactory.getKeyManagers();
                };
            }
        };

        return new TrivialResourceDefinition<>(ElytronDescriptionConstants.KEY_MANAGERS, KEY_MANAGERS_RUNTIME_CAPABILITY, KeyManager[].class, add, attributes);

    }

    private static SimpleAttributeDefinition setCapabilityReference(String referencedCapability, String dependentCapability, SimpleAttributeDefinition attribute) {
        return new SimpleAttributeDefinitionBuilder(attribute)
                .setCapabilityReference(referencedCapability, dependentCapability, true)
                .build();
    }


}
