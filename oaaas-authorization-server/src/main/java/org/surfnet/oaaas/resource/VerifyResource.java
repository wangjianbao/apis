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
package org.surfnet.oaaas.resource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.surfnet.oaaas.basic.UserPassCredentials;
import org.surfnet.oaaas.model.AccessToken;
import org.surfnet.oaaas.model.ResourceServer;
import org.surfnet.oaaas.model.VerifyTokenResponse;
import org.surfnet.oaaas.repository.AccessTokenRepository;
import org.surfnet.oaaas.repository.ResourceServerRepository;

/**
 * Resource for handling the call from resource servers to validate an access
 * token. As this is not part of the oauth2 <a
 * href="http://tools.ietf.org/html/draft-ietf-oauth-v2">spec</a>, we have taken
 * the Google <a href=
 * "https://developers.google.com/accounts/docs/OAuth2Login#validatingtoken"
 * >specification</a> as basis.
 * 
 */
@Named
@Path("/v1/tokeninfo")
@Produces(MediaType.APPLICATION_JSON)
public class VerifyResource {

  private static final Logger LOG = LoggerFactory.getLogger(VerifyResource.class);

  @Inject
  private AccessTokenRepository accessTokenRepository;

  @Inject
  private ResourceServerRepository resourceServerRepository;

  @GET
  public Response verifyToken(@HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
                              @QueryParam("access_token") String accessToken) {

    UserPassCredentials credentials = new UserPassCredentials(authorization);
    if (!credentials.isValid() || StringUtils.isBlank(accessToken)) {
      LOG.info("responding with Unauthorized, for username '{}' and accessToken '{}'", credentials.getUsername(), accessToken);
      return unauthorized();
    }
    String key = credentials.getUsername();
    ResourceServer resourceServer = resourceServerRepository.findByKey(key);
    if (resourceServer == null) {
      LOG.warn(String.format("ResourceServer(key='%s') not found", key));
      return unauthorized();
    }
    String secret = credentials.getPassword();
    if (!resourceServer.getSecret().equals(secret)) {
      LOG.warn(String.format(
          "ResourceServer(key='%s') is accessing VerifyResource#verifyToken with the wrong secret('%s')", key, secret));
      return unauthorized();
    }
    AccessToken token = accessTokenRepository.findByToken(accessToken);
    if (token == null) {
      LOG.info("responding with Not Found, for username '{}' and accessToken '{}'",
          credentials.getUsername(),accessToken);
      return Response.status(Status.NOT_FOUND).entity(new VerifyTokenResponse("not_found")).build();
    } else if (token.getExpires() != 0 && token.getExpires() < System.currentTimeMillis()) {
      LOG.info("responding with Gone, for username '{}' and accessToken '{}'",
          credentials.getUsername(), accessToken);
      return Response.status(Status.GONE).entity(new VerifyTokenResponse("token_expired")).build();
    }

    final VerifyTokenResponse verifyTokenResponse = new VerifyTokenResponse(
        token.getClient().getName(), token.getScopes(),
        token.getRoles(), token.getPrincipal(), token.getExpires());

    LOG.info("responding with 'OK' response, for username '{}' and accessToken '{}': {}",
        new Object[] { credentials.getUsername(),accessToken, verifyTokenResponse});

    return Response.ok(verifyTokenResponse).build();
  }

  private Response unauthorized() {
    return Response.status(Status.UNAUTHORIZED).header("WWW-Authenticate", "Basic realm=\"Authorization Server\"")
        .build();
  }

  /**
   * @param accessTokenRepository
   *          the accessTokenRepository to set
   */
  public void setAccessTokenRepository(AccessTokenRepository accessTokenRepository) {
    this.accessTokenRepository = accessTokenRepository;
  }

  /**
   * @param resourceServerRepository
   *          the resourceServerRepository to set
   */
  public void setResourceServerRepository(ResourceServerRepository resourceServerRepository) {
    this.resourceServerRepository = resourceServerRepository;
  }

}
