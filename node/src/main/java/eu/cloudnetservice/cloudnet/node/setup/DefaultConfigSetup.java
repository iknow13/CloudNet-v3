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

package eu.cloudnetservice.cloudnet.node.setup;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import eu.cloudnetservice.cloudnet.common.io.FileUtil;
import eu.cloudnetservice.cloudnet.common.language.I18n;
import eu.cloudnetservice.cloudnet.driver.module.DefaultModuleProvider;
import eu.cloudnetservice.cloudnet.driver.network.HostAndPort;
import eu.cloudnetservice.cloudnet.driver.network.cluster.NetworkClusterNode;
import eu.cloudnetservice.cloudnet.driver.service.ProcessSnapshot;
import eu.cloudnetservice.cloudnet.node.Node;
import eu.cloudnetservice.cloudnet.node.console.animation.setup.ConsoleSetupAnimation;
import eu.cloudnetservice.cloudnet.node.console.animation.setup.answer.Parsers;
import eu.cloudnetservice.cloudnet.node.console.animation.setup.answer.Parsers.ParserException;
import eu.cloudnetservice.cloudnet.node.console.animation.setup.answer.QuestionAnswerType;
import eu.cloudnetservice.cloudnet.node.console.animation.setup.answer.QuestionListEntry;
import eu.cloudnetservice.cloudnet.node.module.ModuleEntry;
import eu.cloudnetservice.cloudnet.node.util.NetworkUtil;
import eu.cloudnetservice.ext.updater.util.ChecksumUtil;
import java.net.Inet6Address;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import kong.unirest.Unirest;
import lombok.NonNull;

public class DefaultConfigSetup extends DefaultClusterSetup {

  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").omitEmptyStrings();
  private static final Collection<String> DEFAULT_WHITELIST = ImmutableSet.<String>builder()
    .add("127.0.0.1")
    .add("127.0.1.1")
    .addAll(NetworkUtil.availableIPAddresses())
    .build();

  @Override
  public void applyQuestions(@NonNull ConsoleSetupAnimation animation) {
    // pre-save all available ip addresses
    Collection<String> addresses = NetworkUtil.availableIPAddresses();
    // apply the questions
    animation.addEntries(
      // language
      QuestionListEntry.<String>builder()
        .key("language")
        .question(() -> "Welcome to the CloudNet Setup! Please choose the language you want to use")
        .answerType(QuestionAnswerType.<String>builder()
          .recommendation(I18n.language())
          .possibleResults(I18n.knownLanguages())
          .parser(input -> {
            if (I18n.knownLanguages().contains(input)) {
              return input;
            } else {
              throw ParserException.INSTANCE;
            }
          })
          .addResultListener((__, language) -> I18n.language(language)))
        .build(),
      // eula agreement
      QuestionListEntry.<Boolean>builder()
        .key("eula")
        .translatedQuestion("cloudnet-init-eula")
        .answerType(QuestionAnswerType.<Boolean>builder()
          .recommendation("yes")
          .possibleResults("yes")
          .translatedInvalidInputMessage("cloudnet-init-eula-not-accepted")
          .parser(input -> {
            // we can only proceed if the user accepts the eula
            if (input.equalsIgnoreCase("yes")) {
              return true;
            } else {
              throw ParserException.INSTANCE;
            }
          }))
        .build(),
      // network server host
      QuestionListEntry.<HostAndPort>builder()
        .key("internalHost")
        .translatedQuestion("cloudnet-init-setup-internal-host")
        .answerType(QuestionAnswerType.<HostAndPort>builder()
          .recommendation(NetworkUtil.localAddress() + ":1410")
          .possibleResults(addresses.stream().map(addr -> addr + ":1410").toList())
          .parser(Parsers.validatedHostAndPort(true)))
        .build(),
      // web server host
      QuestionListEntry.<HostAndPort>builder()
        .key("webHost")
        .translatedQuestion("cloudnet-init-setup-web-host")
        .answerType(QuestionAnswerType.<HostAndPort>builder()
          .recommendation(NetworkUtil.localAddress() + ":2812")
          .possibleResults(addresses.stream().map(addr -> addr + ":2812").toList())
          .parser(Parsers.validatedHostAndPort(true)))
        .build(),
      // service bind host address
      QuestionListEntry.<HostAndPort>builder()
        .key("hostAddress")
        .translatedQuestion("cloudnet-init-setup-host-address")
        .answerType(QuestionAnswerType.<HostAndPort>builder()
          .possibleResults(addresses)
          .recommendation(NetworkUtil.localAddress())
          .parser(Parsers.validatedHostAndPort(false)))
        .build(),
      // maximum memory usage
      QuestionListEntry.<Integer>builder()
        .key("memory")
        .translatedQuestion("cloudnet-init-setup-memory")
        .answerType(QuestionAnswerType.<Integer>builder()
          .parser(Parsers.ranged(128, Integer.MAX_VALUE))
          .possibleResults("128", "512", "1024", "2048", "4096", "8192", "16384", "32768", "65536")
          .recommendation((int) ((ProcessSnapshot.OS_BEAN.getTotalMemorySize() / (1024 * 1024)) - 512)))
        .build(),
      // default installed modules
      QuestionListEntry.<Collection<ModuleEntry>>builder()
        .key("initialModules")
        .translatedQuestion("cloudnet-init-default-modules")
        .answerType(QuestionAnswerType.<Collection<ModuleEntry>>builder()
          .parser(string -> {
            var entries = SPACE_SPLITTER.splitToList(string);
            if (entries.isEmpty()) {
              return Set.of();
            }
            // try to find each provided module
            Set<ModuleEntry> result = new HashSet<>();
            for (var entry : entries) {
              // get the associated entry
              var moduleEntry = Node.instance().modulesHolder()
                .findByName(entry)
                .orElseThrow(() -> ParserException.INSTANCE);
              // check for depending on modules
              if (!moduleEntry.dependingModules().isEmpty()) {
                moduleEntry.dependingModules().forEach(module -> {
                  // resolve and add the depending on module
                  var dependEntry = Node.instance().modulesHolder()
                    .findByName(module)
                    .orElseThrow(() -> ParserException.INSTANCE);
                  result.add(dependEntry);
                });
              }
              // register the module as installation target
              result.add(moduleEntry);
            }
            return result;
          })
          .possibleResults(Node.instance().modulesHolder().entries().stream()
            .map(ModuleEntry::name)
            .toList())
          .recommendation("CloudNet-Bridge CloudNet-Signs")
          .addResultListener((__, result) -> {
            // install all the modules
            result.forEach(entry -> {
              // ensure that the target path actually is there
              var targetPath = DefaultModuleProvider.DEFAULT_MODULE_DIR.resolve(entry.name() + ".jar");
              FileUtil.createDirectory(targetPath.getParent());
              // download the module file
              Unirest.get(entry.url()).asFile(targetPath.toString(), StandardCopyOption.REPLACE_EXISTING);
              // validate the downloaded file
              var checksum = ChecksumUtil.fileShaSum(targetPath);
              if (!checksum.equals(entry.sha3256()) && !Node.instance().dev() && !entry.official()) {
                // remove the file
                FileUtil.delete(targetPath);
                return;
              }
              // load the module
              Node.instance().moduleProvider().loadModule(targetPath);
            });
          }))
        .build()
    );
    // apply the cluster setup questions
    super.applyQuestions(animation);
  }

  @Override
  public void handleResults(@NonNull ConsoleSetupAnimation animation) {
    var config = Node.instance().config();
    // language
    config.language(animation.result("language"));

    // init the local node identity
    HostAndPort host = animation.result("internalHost");
    config.identity(new NetworkClusterNode(
      animation.hasResult("nodeId") ? animation.result("nodeId") : "Node-1",
      Lists.newArrayList(host)));
    // whitelist the host address
    config.ipWhitelist().add(host.host());

    // init the host address
    HostAndPort hostAddress = animation.result("hostAddress");
    config.hostAddress(hostAddress.host());

    // if the host address is a wildcard address we need to specify a loopback address as connect host address
    // this is because the connect address should be used by a proxy to connect a player to a downstream service
    var address = InetAddresses.forString(hostAddress.host());
    if (address.isAnyLocalAddress()) {
      // keep ipv6 address types
      config.connectHostAddress(address instanceof Inet6Address ? "::1" : "127.0.0.1");
    } else {
      config.connectHostAddress(hostAddress.host());
    }

    // init the web host address
    config.httpListeners().clear();
    config.httpListeners().add(animation.result("webHost"));

    // set the maximum memory
    config.maxMemory(animation.result("memory"));

    // whitelist all default addresses and finish by saving the config
    config.ipWhitelist().addAll(DEFAULT_WHITELIST);
    config.save();

    // apply animation results of the cluster setup
    super.handleResults(animation);
  }
}
