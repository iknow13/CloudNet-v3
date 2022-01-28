/*
 * Copyright 2019-2022 CloudNetService team & contributors
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

package eu.cloudnetservice.cloudnet.node.http;

import eu.cloudnetservice.cloudnet.common.document.gson.JsonDocument;
import eu.cloudnetservice.cloudnet.common.log.LogManager;
import eu.cloudnetservice.cloudnet.common.log.Logger;
import eu.cloudnetservice.cloudnet.driver.CloudNetDriver;
import eu.cloudnetservice.cloudnet.driver.network.http.HttpContext;
import eu.cloudnetservice.cloudnet.driver.network.http.HttpHandler;
import eu.cloudnetservice.cloudnet.driver.network.http.HttpRequest;
import eu.cloudnetservice.cloudnet.driver.network.http.HttpResponse;
import eu.cloudnetservice.cloudnet.driver.network.http.HttpResponseCode;
import eu.cloudnetservice.cloudnet.driver.permission.Permission;
import eu.cloudnetservice.cloudnet.driver.permission.PermissionUser;
import eu.cloudnetservice.cloudnet.node.CloudNet;
import eu.cloudnetservice.cloudnet.node.config.AccessControlConfiguration;
import eu.cloudnetservice.cloudnet.node.config.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public abstract class V2HttpHandler implements HttpHandler {

  protected static final Logger LOGGER = LogManager.logger(V2HttpHandler.class);
  protected static final V2HttpAuthentication DEFAULT_AUTH = new V2HttpAuthentication();

  protected final String requiredPermission;
  protected final String[] supportedRequestMethods;
  protected final String supportedRequestMethodsString;

  protected final V2HttpAuthentication authentication;
  protected final AccessControlConfiguration accessControlConfiguration;

  public V2HttpHandler(@Nullable String requiredPermission, @NonNull String... supportedRequestMethods) {
    this(requiredPermission, DEFAULT_AUTH, CloudNet.instance().config().accessControlConfig(), supportedRequestMethods);
  }

  public V2HttpHandler(
    @Nullable String requiredPermission,
    @NonNull V2HttpAuthentication authentication,
    @NonNull AccessControlConfiguration accessControlConfiguration,
    @NonNull String... supportedRequestMethods
  ) {
    this.requiredPermission = requiredPermission;
    this.authentication = authentication;
    this.accessControlConfiguration = accessControlConfiguration;

    this.supportedRequestMethods = supportedRequestMethods;
    // needed to use a binary search later
    Arrays.sort(this.supportedRequestMethods);
    this.supportedRequestMethodsString = supportedRequestMethods.length == 0
      ? "*" : String.join(", ", supportedRequestMethods);
  }

  @Override
  public void handle(@NonNull String path, @NonNull HttpContext context) throws Exception {
    if (context.request().method().equalsIgnoreCase("OPTIONS")) {
      this.sendOptions(context);
    } else {
      if (this.supportedRequestMethods.length > 0
        && Arrays.binarySearch(this.supportedRequestMethods, context.request().method().toUpperCase()) < 0) {
        this.response(context, HttpResponseCode.METHOD_NOT_ALLOWED)
          .header("Allow", this.supportedRequestMethodsString)
          .context()
          .cancelNext(true)
          .closeAfter();
      } else if (context.request().hasHeader("Authorization")) {
        // try the more often used bearer auth first
        var session = this.authentication
          .handleBearerLoginRequest(context.request());
        if (session.succeeded()) {
          if (this.testPermission(session.result().user(), context.request())) {
            this.handleBearerAuthorized(path, context, session.result());
          } else {
            this.send403(context, String.format("Required permission %s not set", this.requiredPermission));
          }
          return;
        } else if (session.hasErrorMessage()) {
          this.send403(context, session.errorMessage());
          return;
        }
        // try the basic auth method
        var user = this.authentication
          .handleBasicLoginRequest(context.request());
        if (user.succeeded()) {
          if (this.testPermission(user.result(), context.request())) {
            this.handleBasicAuthorized(path, context, user.result());
          } else {
            this.send403(context, String.format("Required permission %s not set", this.requiredPermission));
          }
          return;
        } else if (user.hasErrorMessage()) {
          this.send403(context, user.errorMessage());
          return;
        }
        // send an unauthorized response
        this.send403(context, "No supported authentication method provided. Supported: Basic, Bearer");
      } else {
        // there was no authorization given, try without one
        this.handleUnauthorized(path, context);
      }
    }
  }

  protected void handleUnauthorized(@NonNull String path, @NonNull HttpContext context) throws Exception {
    this.send403(context, "Authentication required");
  }

  protected void handleBasicAuthorized(@NonNull String path, @NonNull HttpContext context,
    @NonNull PermissionUser user) {
  }

  protected void handleBearerAuthorized(@NonNull String path, @NonNull HttpContext context,
    @NonNull HttpSession session) {
  }

  protected boolean testPermission(@NonNull PermissionUser user, @NonNull HttpRequest request) {
    if (this.requiredPermission == null || this.requiredPermission.isEmpty()) {
      return true;
    } else {
      return CloudNetDriver.instance().permissionManagement().hasPermission(
        user,
        Permission.of(this.requiredPermission + '.' + request.method().toLowerCase()));
    }
  }

  protected void send403(@NonNull HttpContext context, @NonNull String reason) {
    this.response(context, HttpResponseCode.FORBIDDEN)
      .body(this.failure().append("reason", reason).toString().getBytes(StandardCharsets.UTF_8))
      .context()
      .closeAfter(true)
      .cancelNext();
  }

  protected void sendOptions(@NonNull HttpContext context) {
    context
      .cancelNext(true)
      .response()
      .status(HttpResponseCode.OK)
      .header("Access-Control-Max-Age", Integer.toString(this.accessControlConfiguration.accessControlMaxAge()))
      .header("Access-Control-Allow-Origin", this.accessControlConfiguration.corsPolicy())
      .header("Access-Control-Allow-Headers", "*")
      .header("Access-Control-Expose-Headers", "Accept, Origin, if-none-match, Access-Control-Allow-Headers, " +
        "Access-Control-Allow-Origin, Content-Type, Content-Length, Accept-Encoding, X-CSRF-Token, Authorization")
      .header("Access-Control-Allow-Credentials", "true")
      .header("Access-Control-Allow-Methods", this.supportedRequestMethodsString);
  }

  protected HttpResponse ok(@NonNull HttpContext context) {
    return this.response(context, HttpResponseCode.OK);
  }

  protected HttpResponse badRequest(@NonNull HttpContext context) {
    return this.response(context, HttpResponseCode.BAD_REQUEST);
  }

  protected HttpResponse notFound(@NonNull HttpContext context) {
    return this.response(context, HttpResponseCode.NOT_FOUND);
  }

  protected HttpResponse response(@NonNull HttpContext context, @NonNull HttpResponseCode statusCode) {
    return context.response()
      .status(statusCode)
      .header("Content-Type", "application/json")
      .header("Access-Control-Allow-Origin", this.accessControlConfiguration.corsPolicy());
  }

  protected @NonNull JsonDocument body(@NonNull HttpRequest request) {
    return JsonDocument.fromJsonBytes(request.body());
  }

  protected @NonNull JsonDocument success() {
    return JsonDocument.newDocument("success", true);
  }

  protected @NonNull JsonDocument failure() {
    return JsonDocument.newDocument("success", false);
  }

  protected @NonNull CloudNet node() {
    return CloudNet.instance();
  }

  protected @NonNull Configuration configuration() {
    return this.node().config();
  }
}