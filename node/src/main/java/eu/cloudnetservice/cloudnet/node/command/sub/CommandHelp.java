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

package eu.cloudnetservice.cloudnet.node.command.sub;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.context.CommandContext;
import eu.cloudnetservice.cloudnet.common.column.ColumnFormatter;
import eu.cloudnetservice.cloudnet.common.column.RowBasedFormatter;
import eu.cloudnetservice.cloudnet.common.language.I18n;
import eu.cloudnetservice.cloudnet.driver.command.CommandInfo;
import eu.cloudnetservice.cloudnet.node.command.CommandProvider;
import eu.cloudnetservice.cloudnet.node.command.annotation.CommandAlias;
import eu.cloudnetservice.cloudnet.node.command.annotation.Description;
import eu.cloudnetservice.cloudnet.node.command.exception.ArgumentNotAvailableException;
import eu.cloudnetservice.cloudnet.node.command.source.CommandSource;
import java.util.Queue;
import lombok.NonNull;

@CommandAlias({"ask", "?"})
@CommandPermission("cloudnet.command.help")
@Description("Shows all commands and their description")
public final class CommandHelp {

  private static final RowBasedFormatter<CommandInfo> HELP_LIST_FORMATTER = RowBasedFormatter.<CommandInfo>builder()
    .defaultFormatter(ColumnFormatter.builder().columnTitles("Name(s)", "Description", "Permission").build())
    .column(info -> info.joinNameToAliases(", "))
    .column(CommandInfo::description)
    .column(CommandInfo::permission)
    .build();

  private final CommandProvider commandProvider;

  public CommandHelp(@NonNull CommandProvider commandProvider) {
    this.commandProvider = commandProvider;
  }

  @Parser
  public @NonNull CommandInfo defaultCommandInfoParser(@NonNull CommandContext<?> $, @NonNull Queue<String> input) {
    var command = input.remove();
    var commandInfo = this.commandProvider.command(command);
    if (commandInfo == null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-not-found"));
    }

    return commandInfo;
  }

  @CommandMethod("help|ask|?")
  public void displayHelp(@NonNull CommandSource source) {
    source.sendMessage(HELP_LIST_FORMATTER.format(this.commandProvider.commands()));
  }

  @CommandMethod("help|ask|? <command>")
  public void displaySpecificHelp(@NonNull CommandSource source, @NonNull @Argument("command") CommandInfo command) {
    source.sendMessage(" ");

    source.sendMessage("Names: " + command.joinNameToAliases(", "));
    source.sendMessage("Description: " + command.description());
    source.sendMessage("Usage: ");
    for (var usage : command.usage()) {
      source.sendMessage(" - " + usage);
    }
  }

}
