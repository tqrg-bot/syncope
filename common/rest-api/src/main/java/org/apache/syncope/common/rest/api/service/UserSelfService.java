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
package org.apache.syncope.common.rest.api.service;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.cxf.jaxrs.model.wadl.Description;
import org.apache.cxf.jaxrs.model.wadl.Descriptions;
import org.apache.cxf.jaxrs.model.wadl.DocTarget;
import org.apache.syncope.common.lib.mod.UserMod;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.rest.api.RESTHeaders;

/**
 * REST operations for user self-management.
 */
@Path("users/self")
public interface UserSelfService extends JAXRSService {

    /**
     * Returns the user making the service call.
     *
     * @return calling user data, including owned entitlements as header value {@link RESTHeaders#OWNED_ENTITLEMENTS}
     */
    @GET
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    Response read();

    /**
     * Self-registration for new user.
     *
     * @param userTO user to be created
     * @param storePassword whether password shall be stored internally
     * @return <tt>Response</tt> object featuring <tt>Location</tt> header of self-registered user as well as the user
     * itself - {@link UserTO} as <tt>Entity</tt>
     */
    @Descriptions({
        @Description(target = DocTarget.RESPONSE,
                value = "Featuring <tt>Location</tt> header of self-registered user as well "
                + "as the user itself - {@link UserTO} as <tt>Entity</tt>")
    })
    @POST
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    Response create(@NotNull UserTO userTO,
            @DefaultValue("true") @QueryParam("storePassword") boolean storePassword);

    /**
     * Self-updates user.
     *
     * @param userKey id of user to be updated
     * @param userMod modification to be applied to user matching the provided userKey
     * @return <tt>Response</tt> object featuring the updated user - {@link UserTO} as <tt>Entity</tt>
     */
    @Descriptions({
        @Description(target = DocTarget.RESPONSE,
                value = "Featuring the updated user - <tt>UserTO</tt> as <tt>Entity</tt>")
    })
    @POST
    @Path("{userKey}")
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    Response update(@NotNull @PathParam("userKey") Long userKey, @NotNull UserMod userMod);

    /**
     * Self-deletes user.
     *
     * @return <tt>Response</tt> object featuring the deleted user - {@link UserTO} as <tt>Entity</tt>
     */
    @Descriptions({
        @Description(target = DocTarget.RESPONSE,
                value = "Featuring the deleted user - <tt>UserTO</tt> as <tt>Entity</tt>")
    })
    @DELETE
    @Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
    Response delete();

    /**
     * Provides answer for the security question configured for user matching the given username, if any.
     * If provided anwser matches the one stored for that user, a password reset token is internally generated,
     * otherwise an error is returned.
     *
     * @param username username for which the security answer is provided
     * @param securityAnswer actual answer text
     */
    @POST
    @Path("requestPasswordReset")
    void requestPasswordReset(@NotNull @QueryParam("username") String username, String securityAnswer);

    /**
     * Reset the password value for the user matching the provided token, if available and still valid.
     * If the token actually matches one of users, and if it is still valid at the time of submission, the matching
     * user's password value is set as provided. The new password value will need anyway to comply with all relevant
     * password policies.
     *
     * @param token password reset token
     * @param password new password to be set
     */
    @POST
    @Path("confirmPasswordReset")
    void confirmPasswordReset(@NotNull @QueryParam("token") String token, String password);
}
