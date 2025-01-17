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

package eu.cloudnetservice.modules.bridge.platform.fabric.util;

import com.mojang.authlib.properties.Property;
import java.net.SocketAddress;
import java.util.UUID;
import lombok.NonNull;

public interface BridgedClientConnection {

  void addr(@NonNull SocketAddress address);

  @NonNull UUID forwardedUniqueId();

  void forwardedUniqueId(@NonNull UUID uuid);

  @NonNull Property[] forwardedProfile();

  void forwardedProfile(@NonNull Property[] profile);
}
