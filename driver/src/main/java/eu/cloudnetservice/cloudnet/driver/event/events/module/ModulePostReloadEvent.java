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

package eu.cloudnetservice.cloudnet.driver.event.events.module;

import eu.cloudnetservice.cloudnet.driver.module.ModuleLifeCycle;
import eu.cloudnetservice.cloudnet.driver.module.ModuleProvider;
import eu.cloudnetservice.cloudnet.driver.module.ModuleWrapper;
import lombok.NonNull;

/**
 * This event is being called after a module has been reloaded and the tasks with the lifecycle {@link
 * ModuleLifeCycle#RELOADING} of this module have been fired. {@link ModuleWrapper#moduleLifeCycle()} is still {@link
 * ModuleLifeCycle#STARTED}.
 *
 * @since 4.0
 */
public final class ModulePostReloadEvent extends ModuleEvent {

  /**
   * Constructs a new module post reload event.
   *
   * @param moduleProvider the provider in which the module is loaded.
   * @param module         the module which is associated with this event.
   * @throws NullPointerException if either the provider or wrapper is null.
   */
  public ModulePostReloadEvent(@NonNull ModuleProvider moduleProvider, @NonNull ModuleWrapper module) {
    super(moduleProvider, module);
  }
}