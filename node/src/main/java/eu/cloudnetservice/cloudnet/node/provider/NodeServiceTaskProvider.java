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

package eu.cloudnetservice.cloudnet.node.provider;

import eu.cloudnetservice.cloudnet.common.JavaVersion;
import eu.cloudnetservice.cloudnet.common.Nameable;
import eu.cloudnetservice.cloudnet.common.document.gson.JsonDocument;
import eu.cloudnetservice.cloudnet.common.io.FileUtil;
import eu.cloudnetservice.cloudnet.common.language.I18n;
import eu.cloudnetservice.cloudnet.common.log.LogManager;
import eu.cloudnetservice.cloudnet.common.log.Logger;
import eu.cloudnetservice.cloudnet.driver.channel.ChannelMessage;
import eu.cloudnetservice.cloudnet.driver.event.EventManager;
import eu.cloudnetservice.cloudnet.driver.network.buffer.DataBuf;
import eu.cloudnetservice.cloudnet.driver.network.def.NetworkConstants;
import eu.cloudnetservice.cloudnet.driver.provider.ServiceTaskProvider;
import eu.cloudnetservice.cloudnet.driver.service.ServiceTask;
import eu.cloudnetservice.cloudnet.node.CloudNet;
import eu.cloudnetservice.cloudnet.node.cluster.sync.DataSyncHandler;
import eu.cloudnetservice.cloudnet.node.event.task.LocalServiceTaskAddEvent;
import eu.cloudnetservice.cloudnet.node.event.task.LocalServiceTaskRemoveEvent;
import eu.cloudnetservice.cloudnet.node.network.listener.message.TaskChannelMessageListener;
import eu.cloudnetservice.cloudnet.node.setup.DefaultTaskSetup;
import eu.cloudnetservice.cloudnet.node.util.JavaVersionResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public class NodeServiceTaskProvider implements ServiceTaskProvider {

  private static final Path TASKS_DIRECTORY = Path.of(
    System.getProperty("cloudnet.config.tasks.directory.path", "local/tasks"));

  private static final Logger LOGGER = LogManager.logger(NodeServiceTaskProvider.class);

  private final EventManager eventManager;
  private final Map<String, ServiceTask> serviceTasks = new ConcurrentHashMap<>();

  public NodeServiceTaskProvider(@NonNull CloudNet nodeInstance) {
    this.eventManager = nodeInstance.eventManager();
    this.eventManager.registerListener(new TaskChannelMessageListener(this.eventManager, this));

    // rpc
    nodeInstance.rpcProviderFactory().newHandler(ServiceTaskProvider.class, this).registerToDefaultRegistry();
    // cluster data sync
    nodeInstance.dataSyncRegistry().registerHandler(
      DataSyncHandler.<ServiceTask>builder()
        .key("task")
        .nameExtractor(Nameable::name)
        .convertObject(ServiceTask.class)
        .writer(this::addPermanentServiceTaskSilently)
        .dataCollector(this::permanentServiceTasks)
        .currentGetter(task -> this.serviceTask(task.name()))
        .build());

    if (Files.exists(TASKS_DIRECTORY)) {
      this.loadServiceTasks();
    } else {
      FileUtil.createDirectory(TASKS_DIRECTORY);
      nodeInstance.installation().registerSetup(new DefaultTaskSetup());
    }
  }

  @Override
  public void reload() {
    // clear the cache
    this.serviceTasks.clear();
    // load all service tasks
    this.loadServiceTasks();
  }

  @Override
  public @NonNull Collection<ServiceTask> permanentServiceTasks() {
    return Collections.unmodifiableCollection(this.serviceTasks.values());
  }

  @Override
  public void permanentServiceTasks(@NonNull Collection<ServiceTask> serviceTasks) {
    this.permanentServiceTasksSilently(serviceTasks);
    // notify the cluster
    ChannelMessage.builder()
      .targetNodes()
      .message("set_service_tasks")
      .channel(NetworkConstants.INTERNAL_MSG_CHANNEL)
      .buffer(DataBuf.empty().writeObject(serviceTasks))
      .build()
      .send();
  }

  @Override
  public @Nullable ServiceTask serviceTask(@NonNull String name) {
    return this.serviceTasks.get(name);
  }

  @Override
  public boolean serviceTaskPresent(@NonNull String name) {
    return this.serviceTasks.containsKey(name);
  }

  @Override
  public boolean addPermanentServiceTask(@NonNull ServiceTask serviceTask) {
    if (!this.eventManager.callEvent(new LocalServiceTaskAddEvent(serviceTask)).cancelled()) {
      this.addPermanentServiceTaskSilently(serviceTask);
      // notify the cluster
      ChannelMessage.builder()
        .targetAll()
        .message("add_service_task")
        .channel(NetworkConstants.INTERNAL_MSG_CHANNEL)
        .buffer(DataBuf.empty().writeObject(serviceTask))
        .build()
        .send();
      return true;
    }
    return false;
  }

  @Override
  public void removePermanentServiceTaskByName(@NonNull String name) {
    var task = this.serviceTask(name);
    if (task != null) {
      this.removePermanentServiceTask(task);
    }
  }

  @Override
  public void removePermanentServiceTask(@NonNull ServiceTask serviceTask) {
    if (!this.eventManager.callEvent(new LocalServiceTaskRemoveEvent(serviceTask)).cancelled()) {
      this.removePermanentServiceTaskSilently(serviceTask);
      // notify the whole network
      ChannelMessage.builder()
        .targetAll()
        .message("remove_service_task")
        .channel(NetworkConstants.INTERNAL_MSG_CHANNEL)
        .buffer(DataBuf.empty().writeObject(serviceTask))
        .build()
        .send();
    }
  }

  public void addPermanentServiceTaskSilently(@NonNull ServiceTask serviceTask) {
    // cache locally
    this.serviceTasks.put(serviceTask.name(), serviceTask);
    this.writeServiceTask(serviceTask);
  }

  public void removePermanentServiceTaskSilently(@NonNull ServiceTask serviceTask) {
    // remove from cache
    this.serviceTasks.remove(serviceTask.name());
    // remove the local file if it exists
    FileUtil.delete(this.taskFile(serviceTask));
  }

  public void permanentServiceTasksSilently(@NonNull Collection<ServiceTask> serviceTasks) {
    // update the cache
    this.serviceTasks.clear();
    serviceTasks.forEach(task -> this.serviceTasks.put(task.name(), task));
    // store all tasks
    this.writeAllServiceTasks();
  }

  protected @NonNull Path taskFile(@NonNull ServiceTask task) {
    return TASKS_DIRECTORY.resolve(task.name() + ".json");
  }

  protected void writeServiceTask(@NonNull ServiceTask serviceTask) {
    JsonDocument.newDocument(serviceTask).write(this.taskFile(serviceTask));
  }

  protected void writeAllServiceTasks() {
    // write all service tasks
    for (var serviceTask : this.serviceTasks.values()) {
      this.writeServiceTask(serviceTask);
    }
    // delete all service task files which do not exist anymore
    FileUtil.walkFileTree(TASKS_DIRECTORY, ($, file) -> {
      // check if we know the file name
      var taskName = file.getFileName().toString().replace(".json", "");
      if (!this.serviceTasks.containsKey(taskName)) {
        FileUtil.delete(file);
      }
    }, false, "*.json");
  }

  protected void loadServiceTasks() {
    FileUtil.walkFileTree(TASKS_DIRECTORY, ($, file) -> {
      // load the service task
      var task = JsonDocument.newDocument(file).toInstanceOf(ServiceTask.class);
      // check if the file name is still up-to-date
      var taskName = file.getFileName().toString().replace(".json", "");
      if (!taskName.equals(task.name())) {
        // rename the file
        FileUtil.move(file, this.taskFile(task), StandardCopyOption.REPLACE_EXISTING);
      }

      // Wrap the command to a path and unwrap it again to ensure that the command is os specific
      // This allows for example Windows users to use '/' in the task file which is way easier as there
      // is no need to escape. ('\' must be escaped)
      if (task.javaCommand() != null && !task.javaCommand().equals("java")) {
        var command = Path.of(task.javaCommand()).toAbsolutePath().normalize().toString();
        // validate if the task java command needs an update
        if (!task.javaCommand().equals(command)) {
          task = ServiceTask.builder(task).javaCommand(command).build();
        }

        // remove all custom java paths that do not support Java 17
        var javaVersion = JavaVersionResolver.resolveFromJavaExecutable(task.javaCommand());
        if (javaVersion == null) {
          LOGGER.severe(I18n.trans("cloudnet-load-task-unknown-java-version", taskName));
        } else if (!javaVersion.isSupportedByMin(JavaVersion.JAVA_17)) {
          task = ServiceTask.builder(task).javaCommand(null).build();
        }
      }

      // cache the task
      this.addPermanentServiceTask(task);
    }, false, "*.json");
  }
}