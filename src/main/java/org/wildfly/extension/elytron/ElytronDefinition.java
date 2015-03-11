/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.ManagementResourceRegistration;

/**
 * Top level {@link ResourceDefinition} for the Elytron subsystem.
 *
 * @author <a href="mailto:tcerar@redhat.com">Tomaz Cerar</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronDefinition extends SimpleResourceDefinition {
    public static final ElytronDefinition INSTANCE = new ElytronDefinition();

    private ElytronDefinition() {
        super(ElytronExtension.SUBSYSTEM_PATH,
                ElytronExtension.getResourceDescriptionResolver(),
                //We always need to add an 'add' operation
                ElytronAdd.INSTANCE,
                //Every resource that is added, normally needs a remove operation
                ElytronRemove.INSTANCE);
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
    }


    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        // Provider Loader
        resourceRegistration.registerSubModel(new ProviderLoaderDefinition());

        // Domain and Realm Model
        resourceRegistration.registerSubModel(new DomainDefinition());
        resourceRegistration.registerSubModel(new RealmDefinition());

        // TLS Building Blocks
        resourceRegistration.registerSubModel(new KeyStoreDefinition());
    }

    @Override
    public void registerOperations(ManagementResourceRegistration resourceRegistration) {
        super.registerOperations(resourceRegistration);
    }

}
