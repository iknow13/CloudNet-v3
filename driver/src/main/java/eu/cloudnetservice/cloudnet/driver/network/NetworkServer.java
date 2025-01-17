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

package eu.cloudnetservice.cloudnet.driver.network;

import lombok.NonNull;

/**
 * Represents a network component which can accept connection from other network components within the network.
 *
 * @since 4.0
 */
public interface NetworkServer extends NetworkComponent, AutoCloseable {

  /**
   * Binds this network server to the given port on any local address (ipv6 form: {@code ::/0}) if no other listener is
   * already listening to that port.
   *
   * @param port the port to which the listener should get bound.
   * @return true if the listener was added to the given port, false otherwise.
   * @throws IllegalArgumentException if the given port exceeds the port range.
   */
  boolean addListener(int port);

  /**
   * Binds this network server to the given host and port if no listener is already listening on the given address.
   *
   * @param hostAndPort the address to which a listener should get bound.
   * @return true if the listener was added to the given address, false otherwise.
   * @throws NullPointerException if the given host and port is null.
   */
  boolean addListener(@NonNull HostAndPort hostAndPort);
}
