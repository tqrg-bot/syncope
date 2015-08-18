/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.persistence.jpa.outer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.syncope.common.lib.types.IntMappingType;
import org.apache.syncope.common.lib.types.MappingPurpose;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.ConnInstanceDAO;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.PolicyDAO;
import org.apache.syncope.core.persistence.api.dao.TaskDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.ConnInstance;
import org.apache.syncope.core.persistence.api.entity.resource.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.PasswordPolicy;
import org.apache.syncope.core.persistence.api.entity.resource.Mapping;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.persistence.api.entity.task.PropagationTask;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.persistence.jpa.AbstractTest;
import org.apache.syncope.core.persistence.jpa.entity.resource.JPAMappingItem;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional("Master")
public class ResourceTest extends AbstractTest {

    @Autowired
    private ExternalResourceDAO resourceDAO;

    @Autowired
    private ConnInstanceDAO connInstanceDAO;

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private TaskDAO taskDAO;

    @Autowired
    private PolicyDAO policyDAO;

    @Test
    public void createWithPasswordPolicy() {
        final String resourceName = "resourceWithPasswordPolicy";

        PasswordPolicy policy = (PasswordPolicy) policyDAO.find(4L);
        ExternalResource resource = entityFactory.newEntity(ExternalResource.class);
        resource.setKey(resourceName);
        resource.setPasswordPolicy(policy);

        ConnInstance connector = connInstanceDAO.find(100L);
        assertNotNull("connector not found", connector);
        resource.setConnector(connector);

        ExternalResource actual = resourceDAO.save(resource);
        assertNotNull(actual);

        actual = resourceDAO.find(actual.getKey());
        assertNotNull(actual);
        assertNotNull(actual.getPasswordPolicy());

        resourceDAO.delete(resourceName);
        assertNull(resourceDAO.find(resourceName));

        assertNotNull(policyDAO.find(4L));
    }

    @Test
    public void save() {
        ExternalResource resource = entityFactory.newEntity(ExternalResource.class);
        resource.setKey("ws-target-resource-save");

        // specify the connector
        ConnInstance connector = connInstanceDAO.find(100L);
        assertNotNull("connector not found", connector);

        resource.setConnector(connector);

        Provision provision = entityFactory.newEntity(Provision.class);
        provision.setAnyType(anyTypeDAO.findUser());
        provision.setObjectClass(ObjectClass.ACCOUNT);
        provision.setResource(resource);
        resource.add(provision);

        Mapping mapping = entityFactory.newEntity(Mapping.class);
        mapping.setProvision(provision);
        provision.setMapping(mapping);

        // specify mappings
        for (int i = 0; i < 3; i++) {
            MappingItem item = entityFactory.newEntity(MappingItem.class);
            item.setExtAttrName("test" + i);
            item.setIntAttrName("nonexistent" + i);
            item.setIntMappingType(IntMappingType.UserPlainSchema);
            item.setMandatoryCondition("false");
            item.setPurpose(MappingPurpose.SYNCHRONIZATION);
            mapping.add(item);
            item.setMapping(mapping);
        }
        MappingItem connObjectKey = entityFactory.newEntity(MappingItem.class);
        connObjectKey.setExtAttrName("username");
        connObjectKey.setIntAttrName("username");
        connObjectKey.setIntMappingType(IntMappingType.UserKey);
        connObjectKey.setPurpose(MappingPurpose.PROPAGATION);
        mapping.setConnObjectKeyItem(connObjectKey);
        connObjectKey.setMapping(mapping);

        // map a derived attribute
        MappingItem derived = entityFactory.newEntity(MappingItem.class);
        derived.setConnObjectKey(false);
        derived.setExtAttrName("fullname");
        derived.setIntAttrName("cn");
        derived.setIntMappingType(IntMappingType.UserDerivedSchema);
        derived.setPurpose(MappingPurpose.PROPAGATION);
        mapping.add(derived);
        derived.setMapping(mapping);

        // save the resource
        ExternalResource actual = resourceDAO.save(resource);
        assertNotNull(actual);
        assertNotNull(actual.getProvision(anyTypeDAO.findUser()).getMapping());

        resourceDAO.flush();
        resourceDAO.detach(actual);
        connInstanceDAO.detach(connector);

        // assign the new resource to an user
        User user = userDAO.find(1L);
        assertNotNull("user not found", user);

        user.add(actual);

        resourceDAO.flush();

        // retrieve resource
        resource = resourceDAO.find(actual.getKey());
        assertNotNull(resource);
        resourceDAO.refresh(resource);

        // check connector
        connector = connInstanceDAO.find(100L);
        assertNotNull(connector);
        assertNotNull(connector.getResources());

        assertNotNull(resource.getConnector());
        assertTrue(resource.getConnector().equals(connector));

        // check mappings
        List<? extends MappingItem> items = resource.getProvision(anyTypeDAO.findUser()).getMapping().getItems();
        assertNotNull(items);
        assertEquals(5, items.size());

        // check user
        user = userDAO.find(1L);
        assertNotNull(user);
        assertNotNull(user.getResources());
        assertTrue(user.getResources().contains(actual));
    }

    @Test
    public void delete() {
        ExternalResource resource = resourceDAO.find("resource-testdb");
        assertNotNull("find to delete did not work", resource);

        // -------------------------------------
        // Get originally associated connector
        // -------------------------------------
        ConnInstance connector = resource.getConnector();
        assertNotNull(connector);
        // -------------------------------------

        // -------------------------------------
        // Get originally associated users
        // -------------------------------------
        List<User> users = userDAO.findByResource(resource);
        assertNotNull(users);

        Set<Long> userIds = new HashSet<>();
        for (User user : users) {
            userIds.add(user.getKey());
        }
        // -------------------------------------

        // Get tasks
        List<PropagationTask> propagationTasks = taskDAO.findAll(resource, TaskType.PROPAGATION);
        assertFalse(propagationTasks.isEmpty());

        // delete resource
        resourceDAO.delete(resource.getKey());

        // close the transaction
        resourceDAO.flush();

        // resource must be removed
        ExternalResource actual = resourceDAO.find("resource-testdb");
        assertNull("delete did not work", actual);

        // resource must be not referenced any more from users
        for (Long id : userIds) {
            User actualUser = userDAO.find(id);
            assertNotNull(actualUser);
            for (ExternalResource res : userDAO.findAllResources(actualUser)) {
                assertFalse(res.getKey().equalsIgnoreCase(resource.getKey()));
            }
        }

        // resource must be not referenced any more from the connector
        ConnInstance actualConnector = connInstanceDAO.find(connector.getKey());
        assertNotNull(actualConnector);
        for (ExternalResource res : actualConnector.getResources()) {
            assertFalse(res.getKey().equalsIgnoreCase(resource.getKey()));
        }

        // there must be no tasks
        for (PropagationTask task : propagationTasks) {
            assertNull(taskDAO.find(task.getKey()));
        }
    }

    @Test
    public void emptyMapping() {
        ExternalResource ldap = resourceDAO.find("resource-ldap");
        assertNotNull(ldap);
        assertNotNull(ldap.getProvision(anyTypeDAO.findUser()).getMapping());
        assertNotNull(ldap.getProvision(anyTypeDAO.findGroup()).getMapping());

        List<? extends MappingItem> items = ldap.getProvision(anyTypeDAO.findGroup()).getMapping().getItems();
        assertNotNull(items);
        assertFalse(items.isEmpty());
        List<Long> itemKeys = new ArrayList<>(items.size());
        for (MappingItem item : items) {
            itemKeys.add(item.getKey());
        }

        ldap.remove(ldap.getProvision(anyTypeDAO.findGroup()));

        // need to avoid any class not defined in this Maven module
        ldap.getPropagationActionsClassNames().clear();

        resourceDAO.save(ldap);
        resourceDAO.flush();

        for (Long itemId : itemKeys) {
            assertNull(entityManager().find(JPAMappingItem.class, itemId));
        }
    }

    @Test
    public void issue243() {
        ExternalResource csv = resourceDAO.find("resource-csv");
        assertNotNull(csv);

        int origMapItems = csv.getProvision(anyTypeDAO.findUser()).getMapping().getItems().size();

        MappingItem newMapItem = entityFactory.newEntity(MappingItem.class);
        newMapItem.setIntMappingType(IntMappingType.Username);
        newMapItem.setExtAttrName("TEST");
        newMapItem.setPurpose(MappingPurpose.PROPAGATION);
        csv.getProvision(anyTypeDAO.findUser()).getMapping().add(newMapItem);

        resourceDAO.save(csv);
        resourceDAO.flush();

        csv = resourceDAO.find("resource-csv");
        assertNotNull(csv);
        assertEquals(origMapItems + 1, csv.getProvision(anyTypeDAO.findUser()).getMapping().getItems().size());
    }
}
