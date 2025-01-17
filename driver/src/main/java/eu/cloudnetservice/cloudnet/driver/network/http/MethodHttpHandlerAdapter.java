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

package eu.cloudnetservice.cloudnet.driver.network.http;

import lombok.NonNull;

/**
 * An adapter of a method http handler allowing to only override the needed http method handlers.
 *
 * @since 4.0
 */
public class MethodHttpHandlerAdapter implements MethodHttpHandler {

  /**
   * {@inheritDoc}
   */
  @Override
  public void handlePost(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleGet(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handlePut(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleHead(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleDelete(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handlePatch(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleTrace(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleOptions(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void handleConnect(@NonNull String path, @NonNull HttpContext context) throws Exception {
  }
}
