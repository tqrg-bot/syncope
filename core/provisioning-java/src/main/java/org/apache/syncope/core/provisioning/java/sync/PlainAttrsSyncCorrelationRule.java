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
package org.apache.syncope.core.provisioning.java.sync;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.syncope.common.lib.types.MappingPurpose;
import org.apache.syncope.core.misc.MappingUtils;
import org.apache.syncope.core.persistence.api.dao.search.AnyCond;
import org.apache.syncope.core.persistence.api.dao.search.AttributeCond;
import org.apache.syncope.core.persistence.api.dao.search.SearchCond;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.provisioning.api.sync.SyncCorrelationRule;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.ConnectorObject;

public class PlainAttrsSyncCorrelationRule implements SyncCorrelationRule {

    private final List<String> plainSchemaNames;

    private final Provision provision;

    public PlainAttrsSyncCorrelationRule(final String[] plainSchemaNames, final Provision provision) {
        this.plainSchemaNames = Arrays.asList(plainSchemaNames);
        this.provision = provision;
    }

    @Override
    public SearchCond getSearchCond(final ConnectorObject connObj) {
        // search for external attribute's name/value of each specified name
        Map<String, Attribute> extValues = new HashMap<>();

        for (MappingItem item : MappingUtils.getMappingItems(provision, MappingPurpose.SYNCHRONIZATION)) {
            extValues.put(item.getIntAttrName(), connObj.getAttributeByName(item.getExtAttrName()));
        }

        // search for user/group by attribute(s) specified in the policy
        SearchCond searchCond = null;

        for (String schema : plainSchemaNames) {
            Attribute value = extValues.get(schema);

            if (value == null) {
                throw new IllegalArgumentException(
                        "Connector object does not contains the attributes to perform the search: " + schema);
            }

            AttributeCond.Type type;
            String expression = null;

            if (value.getValue() == null || value.getValue().isEmpty()
                    || (value.getValue().size() == 1 && value.getValue().get(0) == null)) {

                type = AttributeCond.Type.ISNULL;
            } else {
                type = AttributeCond.Type.EQ;
                expression = value.getValue().size() > 1
                        ? value.getValue().toString()
                        : value.getValue().get(0).toString();
            }

            SearchCond nodeCond;
            // users: just id or username can be selected to be used
            // groups: just id or name can be selected to be used
            if ("key".equalsIgnoreCase(schema)
                    || "username".equalsIgnoreCase(schema) || "name".equalsIgnoreCase(schema)) {

                AnyCond cond = new AnyCond();
                cond.setSchema(schema);
                cond.setType(type);
                cond.setExpression(expression);

                nodeCond = SearchCond.getLeafCond(cond);
            } else {
                AttributeCond cond = new AttributeCond();
                cond.setSchema(schema);
                cond.setType(type);
                cond.setExpression(expression);

                nodeCond = SearchCond.getLeafCond(cond);
            }

            searchCond = searchCond == null
                    ? nodeCond
                    : SearchCond.getAndCond(searchCond, nodeCond);
        }

        return searchCond;
    }

}
