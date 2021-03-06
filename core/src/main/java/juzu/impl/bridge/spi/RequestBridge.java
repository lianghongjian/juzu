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

package juzu.impl.bridge.spi;

import juzu.PropertyType;
import juzu.Response;
import juzu.Scope;
import juzu.asset.AssetLocation;
import juzu.impl.common.Logger;
import juzu.impl.common.RunMode;
import juzu.impl.request.ContextualParameter;
import juzu.impl.request.ControlParameter;
import juzu.request.ClientContext;
import juzu.request.RequestParameter;
import juzu.impl.common.MethodHandle;
import juzu.impl.request.Request;
import juzu.request.ApplicationContext;
import juzu.request.HttpContext;
import juzu.request.Phase;
import juzu.request.ResponseParameter;
import juzu.request.SecurityContext;
import juzu.request.UserContext;
import juzu.request.WindowContext;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public interface RequestBridge extends Closeable {

  /**
   * Returns the default request encoding when no one is specified.
   *
   * @return the defualt encoding
   */
  Charset getDefaultRequestEncoding();

  /**
   * @return the current run mode
   */
  RunMode getRunMode();

  /**
   * @return the current phase
   */
  Phase getPhase();

  /**
   * Returns a logger for the specified name.
   *
   * @param name the logger name
   * @return the logger
   */
  Logger getLogger(String name);

  /**
   * @return the request method target
   */
  MethodHandle getTarget();

  Map<ContextualParameter, Object> getContextualArguments(Set<ContextualParameter> parameters);

  /**
   * @return the request parameters
   */
  Map<String, RequestParameter> getRequestArguments();

  <T> T getProperty(PropertyType<T> propertyType);

  ScopedContext getScopedContext(Scope scope, boolean create);

  ClientContext getClientContext();

  HttpContext getHttpContext();

  SecurityContext getSecurityContext();

  WindowContext getWindowContext();

  UserContext getUserContext();

  ApplicationContext getApplicationContext();


  /**
   * Set the specified response on the bridge.
   *
   *
   * @param response the response
   * @throws IllegalArgumentException if the response cannot be honoured
   * @throws IOException              any io exception
   */
  void setResponse(Response response) throws IllegalArgumentException, IOException;

  /**
   * Signals the beginning of a request.
   *
   * @param request the request
   */
  void begin(Request request);

  /**
   * Signals the end of a request. During this time, the request bridge should terminate any activity
   * in relation with the request (such as closing opened scoped context).
   */
  void end();

  /**
   * Terminates the life cycle of the request bridge.
   */
  void close();

  /**
   * Execute the runnable in a thread.
   *
   * @param runnable the runnable to execute
   * @throws RejectedExecutionException if the operation is not supported
   */
  void execute(Runnable runnable) throws RejectedExecutionException;

  /**
   * Create a dispatch for the specified phase, target and parameters.
   *
   * @param phase the dispatch phase
   * @param target the dispatch target
   * @param parameters the dispatch parameters
   * @return the dispatch object
   * @throws IllegalArgumentException if any parameter is not valid
   * @throws NullPointerException if any argument is null
   */
  DispatchBridge createDispatch(Phase phase, MethodHandle target, Map<String, ResponseParameter> parameters) throws NullPointerException, IllegalArgumentException;

  /**
   * Renders the url of the specified asset to the specified appendable.
   *
   * @param location the asset location
   * @param uri the asset uri
   * @param appendable the buffer to append to
   * @throws NullPointerException if any argument is null
   * @throws UnsupportedOperationException if the current request is not appropriate for rendering the asset
   */
  void renderAssetURL(AssetLocation location, String uri, Appendable appendable) throws NullPointerException, UnsupportedOperationException, IOException;

}
