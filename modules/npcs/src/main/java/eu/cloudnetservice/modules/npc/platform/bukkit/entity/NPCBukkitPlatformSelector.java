/*
 * Copyright 2019-2021 CloudNetService team & contributors
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

package eu.cloudnetservice.modules.npc.platform.bukkit.entity;

import com.github.juliarn.npc.NPCPool;
import com.github.juliarn.npc.modifier.AnimationModifier.EntityAnimation;
import com.github.juliarn.npc.modifier.EquipmentModifier;
import com.github.juliarn.npc.modifier.MetadataModifier;
import com.github.juliarn.npc.modifier.MetadataModifier.EntityMetadata;
import com.github.juliarn.npc.modifier.NPCModifier;
import com.github.juliarn.npc.profile.Profile;
import com.github.juliarn.npc.profile.Profile.Property;
import eu.cloudnetservice.modules.npc.NPC;
import eu.cloudnetservice.modules.npc.platform.bukkit.BukkitPlatformNPCManagement;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class NPCBukkitPlatformSelector extends BukkitPlatformSelectorEntity {

  // See: https://wiki.vg/Entity_metadata#Entity
  private static final byte GLOWING_FLAGS = 1 << 6;
  private static final byte ELYTRA_FLYING_FLAGS = (byte) (1 << 7);
  private static final byte FLYING_AND_GLOWING = (byte) (GLOWING_FLAGS | ELYTRA_FLYING_FLAGS);

  protected final NPCPool npcPool;
  protected volatile com.github.juliarn.npc.NPC handleNpc;

  public NPCBukkitPlatformSelector(
    @NotNull BukkitPlatformNPCManagement npcManagement,
    @NotNull Plugin plugin,
    @NotNull NPC npc,
    @NotNull NPCPool pool
  ) {
    super(npcManagement, plugin, npc);
    this.npcPool = pool;
  }

  @Override
  public int getEntityId() {
    return this.handleNpc == null ? -1 : this.handleNpc.getEntityId();
  }

  @Override
  public @NotNull String getScoreboardRepresentation() {
    return this.handleNpc.getProfile().getName();
  }

  @Override
  public boolean removeWhenWorldSaving() {
    return false;
  }

  @Override
  public boolean isSpawned() {
    return this.handleNpc != null;
  }

  @Override
  protected void spawn0() {
    this.handleNpc = com.github.juliarn.npc.NPC.builder()
      .imitatePlayer(this.npc.isImitatePlayer())
      .lookAtPlayer(this.npc.isLookAtPlayer())
      .usePlayerProfiles(this.npc.isUsePlayerSkin())
      .profile(new Profile(
        new UUID(ThreadLocalRandom.current().nextLong(), 0),
        this.npc.getDisplayName(),
        this.npc.getProfileProperties().stream()
          .map(prop -> new Property(prop.getName(), prop.getValue(), prop.getSignature()))
          .collect(Collectors.toSet())
      ))
      .location(this.npcLocation)
      .spawnCustomizer((spawnedNpc, player) -> {
        // just because the client is stupid sometimes
        spawnedNpc.rotation().queueRotate(this.npcLocation.getYaw(), this.npcLocation.getPitch()).send(player);
        spawnedNpc.animation().queue(EntityAnimation.SWING_MAIN_ARM).send(player);
        MetadataModifier metadataModifier = spawnedNpc.metadata()
          .queue(EntityMetadata.SKIN_LAYERS, true)
          .queue(EntityMetadata.SNEAKING, false);
        // apply glowing effect if possible
        if (NPCModifier.MINECRAFT_VERSION >= 9) {
          if (this.npc.isGlowing() && this.npc.isFlyingWithElytra()) {
            metadataModifier.queue(0, FLYING_AND_GLOWING, Byte.class);
          } else if (this.npc.isGlowing()) {
            metadataModifier.queue(0, GLOWING_FLAGS, Byte.class);
          } else if (this.npc.isFlyingWithElytra()) {
            metadataModifier.queue(0, ELYTRA_FLYING_FLAGS, Byte.class);
          }
        }
        metadataModifier.send(player);
        // set the items
        EquipmentModifier modifier = spawnedNpc.equipment();
        for (Entry<Integer, String> entry : this.npc.getItems().entrySet()) {
          if (entry.getKey() >= 0 && entry.getKey() <= 5) {
            Material material = Material.matchMaterial(entry.getValue());
            if (material != null) {
              modifier.queue(entry.getKey(), new ItemStack(material));
            }
          }
        }
        modifier.send(player);
      }).build(this.npcPool);
  }

  @Override
  protected void remove0() {
    this.npcPool.removeNPC(this.handleNpc.getEntityId());
    this.handleNpc = null;
  }

  @Override
  protected void addGlowingEffect() {
    // no-op - we're doing this while spawning to the player
  }

  @Override
  protected double getHeightAddition(int lineNumber) {
    return 1.09 + super.getHeightAddition(lineNumber);
  }

  public @NotNull com.github.juliarn.npc.NPC getHandleNpc() {
    return this.handleNpc;
  }
}