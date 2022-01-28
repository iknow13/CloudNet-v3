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

package eu.cloudnetservice.cloudnet.node.database.sql;

import eu.cloudnetservice.cloudnet.common.function.ThrowableFunction;
import eu.cloudnetservice.cloudnet.common.log.LogManager;
import eu.cloudnetservice.cloudnet.common.log.Logger;
import eu.cloudnetservice.cloudnet.node.database.AbstractDatabaseProvider;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public abstract class SQLDatabaseProvider extends AbstractDatabaseProvider {

  protected static final Logger LOGGER = LogManager.logger(SQLDatabaseProvider.class);

  protected final ExecutorService executorService;
  protected final boolean autoShutdownExecutorService;
  protected final Map<String, SQLDatabase> cachedDatabaseInstances;

  public SQLDatabaseProvider(@Nullable ExecutorService executorService) {
    this.cachedDatabaseInstances = new ConcurrentHashMap<>();
    this.autoShutdownExecutorService = executorService == null;
    this.executorService = executorService == null ? Executors.newCachedThreadPool() : executorService;
  }

  @Override
  public boolean containsDatabase(@NonNull String name) {
    this.removedOutdatedEntries();
    for (var database : this.databaseNames()) {
      if (database.equalsIgnoreCase(name)) {
        return true;
      }
    }

    return false;
  }

  protected void removedOutdatedEntries() {
    for (var entry : this.cachedDatabaseInstances.entrySet()) {
      if (entry.getValue().cacheTimeoutTime < System.currentTimeMillis()) {
        this.cachedDatabaseInstances.remove(entry.getKey());
      }
    }
  }

  @Override
  public void close() throws Exception {
    if (this.autoShutdownExecutorService) {
      this.executorService.shutdownNow();
    }
  }

  public abstract @NonNull Connection connection();

  public abstract int executeUpdate(@NonNull String query, @NonNull Object... objects);

  public abstract <T> @Nullable T executeQuery(
    @NonNull String query,
    @NonNull ThrowableFunction<ResultSet, T, SQLException> callback,
    @NonNull Object... objects);
}