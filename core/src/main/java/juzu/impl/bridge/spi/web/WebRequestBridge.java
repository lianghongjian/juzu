/*
 * Copyright 2013 eXo Platform SAS
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

package juzu.impl.bridge.spi.web;

import juzu.PropertyMap;
import juzu.PropertyType;
import juzu.Response;
import juzu.Scope;
import juzu.asset.AssetLocation;
import juzu.impl.bridge.Bridge;
import juzu.impl.common.Logger;
import juzu.impl.common.RunMode;
import juzu.impl.common.UriBuilder;
import juzu.impl.inject.spi.InjectorProvider;
import juzu.impl.request.ContextualParameter;
import juzu.impl.request.ControllerHandler;
import juzu.request.ClientContext;
import juzu.request.RequestParameter;
import juzu.request.ResponseParameter;
import juzu.impl.bridge.spi.DispatchBridge;
import juzu.impl.common.MimeType;
import juzu.impl.common.MethodHandle;
import juzu.impl.plugin.controller.ControllerService;
import juzu.impl.bridge.spi.ScopedContext;
import juzu.impl.request.Request;
import juzu.impl.bridge.spi.RequestBridge;
import juzu.impl.common.Tools;
import juzu.impl.router.PathParam;
import juzu.impl.router.Route;
import juzu.impl.router.RouteMatch;
import juzu.request.ApplicationContext;
import juzu.request.HttpContext;
import juzu.request.Phase;
import juzu.request.SecurityContext;
import juzu.request.UserContext;
import juzu.request.WindowContext;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public abstract class WebRequestBridge implements RequestBridge, WindowContext {

  /** . */
  final Bridge bridge;

  /** . */
  final juzu.impl.bridge.spi.web.Handler handler;

  /** . */
  final WebBridge http;

  /** . */
  final Phase phase;

  /** . */
  final ControllerHandler<?> target;

  /** . */
  protected Request request;

  /** . */
  protected Map<String, RequestParameter> requestParameters;

  /** . */
  protected Response response;

  WebRequestBridge(
      Bridge bridge,
      juzu.impl.bridge.spi.web.Handler handler,
      WebBridge http,
      Phase phase,
      ControllerHandler<?> target,
      Map<String, RequestParameter> requestParameters) {
    this.requestParameters = requestParameters;
    this.bridge = bridge;
    this.target = target;
    this.handler = handler;
    this.http = http;
    this.request = null;
    this.phase = phase;
  }

  //

  @Override
  public Charset getDefaultRequestEncoding() {
    return bridge.getConfig().requestEncoding;
  }

  @Override
  public Map<ContextualParameter, Object> getContextualArguments(Set<ContextualParameter> parameters) {
    return Collections.emptyMap();
  }

  public RunMode getRunMode() {
    return bridge.getRunMode();
  }

  public Phase getPhase() {
    return phase;
  }

  public Logger getLogger(String name) {
    return http.getLogger(name);
  }

  public Map<String, RequestParameter> getRequestArguments() {
    return requestParameters;
  }

  public MethodHandle getTarget() {
    return target.getHandle();
  }

  public <T> T getProperty(PropertyType<T> propertyType) {
    if (RunMode.PROPERTY.equals(propertyType)) {
      return propertyType.cast(bridge.getRunMode());
    } else if (InjectorProvider.PROPERTY.equals(propertyType)) {
      return propertyType.cast(bridge.getConfig().injectorProvider);
    } else if (PropertyType.PATH.equals(propertyType)) {
      return propertyType.cast(http.getRequestContext().getRequestURI());
    }
    return null;
  }

  //

  public final String getNamespace() {
    return "window_ns";
  }

  public final String getId() {
    return "window_id";
  }
  //

  public ClientContext getClientContext() {
    return phase == Phase.ACTION || phase == Phase.RESOURCE ? http.getClientContext() : null;
  }

  public final HttpContext getHttpContext() {
    return http.getHttpContext();
  }

  public final WindowContext getWindowContext() {
    return this;
  }

  public final SecurityContext getSecurityContext() {
    return http.getSecurityContext();
  }

  public UserContext getUserContext() {
    return http.getUserContext();
  }

  public ApplicationContext getApplicationContext() {
    return http.getApplicationContext();
  }

  public final ScopedContext getScopedContext(Scope scope, boolean create) {
    ScopedContext context;
    switch (scope) {
      case REQUEST:
        context = http.getRequestScope(create);
        break;
      case FLASH:
        context = http.getFlashScope(create);
        break;
      case SESSION:
        context = http.getSessionScope(create);
        break;
      default:
        throw new UnsupportedOperationException("Unsupported scope " + scope);
    }
    return context;
  }

  public final DispatchBridge createDispatch(Phase phase, final MethodHandle target, final Map<String, ResponseParameter> parameters) {
    ControllerHandler handler = bridge.getApplication().resolveBean(ControllerService.class).getDescriptor().getMethodByHandle(target);

    //
    Route route = this.handler.getRoute(handler.getHandle());
    if (route == null) {
      if (bridge.getApplication().resolveBean(ControllerService.class).getResolver().isIndex(handler)) {
        route = this.handler.getRoot();
      }
    }

    //
    if (route != null) {
      Map<String, String> params;
      if (parameters.isEmpty()) {
        params = Collections.emptyMap();
      } else {
        params = new HashMap<String, String>(parameters.size());
        for (ResponseParameter parameter : parameters.values()) {
          params.put(parameter.getName(), parameter.get(0));
        }
      }

      //
      final RouteMatch match = route.matches(params);
      if (match != null) {
        return new DispatchBridge() {

          public MethodHandle getTarget() {
            return target;
          }

          public Map<String, ResponseParameter> getParameters() {
            return parameters;
          }

          public <T> String checkPropertyValidity(PropertyType<T> propertyType, T propertyValue) {
            // For now we don't validate anything
            return null;
          }

          public void renderURL(PropertyMap properties, MimeType mimeType, Appendable appendable) throws IOException {

            // Render base URL
            http.renderRequestURL(appendable);

            // Render path
            UriBuilder writer = new UriBuilder(appendable, mimeType);
            match.render(writer);

            // Retain matched parameters for filtering later
            Set<String> matched = match.getMatched().isEmpty() ? Collections.<String>emptySet() : new HashSet<String>(match.getMatched().size());
            for (PathParam param : match.getMatched().keySet()) {
              matched.add(param.getName());
            }

            // Render remaining parameters which have not been rendered yet
            for (ResponseParameter parameter : parameters.values()) {
              if (!matched.contains(parameter.getName())) {
                for (int i = 0;i < parameter.size();i++) {
                  writer.appendQueryParameter(parameter.getEncoding(), parameter.getName(), parameter.get(i));
                }
              }
            }
          }
        };
      } else {
        throw new IllegalArgumentException("The parameters " + parameters + " are not valid");
      }
    } else {
      throw new UnsupportedOperationException("handle me gracefully method not mapped " + handler.getHandle());
    }
  }

  public void setResponse(Response response) throws IllegalArgumentException, IOException {
    this.response = response;
  }

  public final void begin(Request request) {
    this.request = request;
  }

  public void end() {
    this.request = null;
  }

  public void execute(Runnable runnable) throws RejectedExecutionException {
    http.execute(runnable);
  }

  public void close() {
  }

  void invoke() throws Exception {
    try {
      bridge.getApplication().resolveBean(ControllerService.class).invoke(this);
    } finally {
      Tools.safeClose(this);
    }
  }

  /**
   * Send the response to the client.
   */
  boolean send() throws Exception {
    if (response instanceof Response.Error) {
      Response.Error error = (Response.Error)response;
      http.getRequestContext().send(error, bridge.getRunMode().getPrettyFail());
      return true;
    } else if (response instanceof Response.View) {
      Response.View view = (Response.View)response;
      Phase.View.Dispatch update = (Phase.View.Dispatch)view;
      Boolean redirect = view.getProperties().getValue(PropertyType.REDIRECT_AFTER_ACTION);
      if (redirect != null && !redirect) {
        ControllerHandler<?> desc = this.bridge.getApplication().resolveBean(ControllerService.class).getDescriptor().getMethodByHandle(update.getTarget());
        Map<String, RequestParameter> rp = Collections.emptyMap();
        for (ResponseParameter parameter : update.getParameters().values()) {
          if (rp.isEmpty()) {
            rp = new HashMap<String, RequestParameter>();
          }
          RequestParameter requestParameter = RequestParameter.create(parameter.getName(), parameter.toArray());
          rp.put(requestParameter.getName(), requestParameter);
        }
        WebViewBridge requestBridge = new WebViewBridge(bridge, handler, http, desc, rp);
        requestBridge.invoke();
        return requestBridge.send();
      } else {
        String url = update.with(MimeType.PLAIN).with(update.getProperties()).toString();
        Iterable<Map.Entry<String, String[]>> headers = view.getProperties().getValues(PropertyType.HEADER);
        if (headers == null) {
          headers = Tools.emptyIterable();
        }
        http.getRequestContext().setHeaders(headers);
        http.getRequestContext().sendRedirect(url);
        return true;
      }
    }
    else if (response instanceof Response.Redirect) {
      Response.Redirect redirect = (Response.Redirect)response;
      String url = redirect.getLocation();
      http.getRequestContext().sendRedirect(url);
      return true;
    } else {
      return false;
    }
  }

  public void renderAssetURL(AssetLocation location, String uri, Appendable appendable) throws NullPointerException, UnsupportedOperationException, IOException {
    http.getRequestContext().renderAssetURL(location, uri, appendable);
  }
}
