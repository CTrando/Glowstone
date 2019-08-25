package net.glowstone.entity.livingentity;

import net.glowstone.EventFactory;
import net.glowstone.GlowWorld;
import net.glowstone.constants.GameRules;
import net.glowstone.entity.GlowEntity;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.entity.meta.MetadataIndex;
import net.glowstone.entity.monster.GlowSlime;
import net.glowstone.entity.objects.GlowExperienceOrb;
import net.glowstone.util.ExperienceSplitter;
import net.glowstone.util.InventoryUtil;
import net.glowstone.util.loot.LootData;
import net.glowstone.util.loot.LootingManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Criterias;
import org.bukkit.scoreboard.Objective;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

class HealthHandler {
    private GlowLivingEntity entity;
    private GlowWorld world;

    public HealthHandler(GlowLivingEntity e) {
        entity = e;
        world = e.getWorld();
    }

    void setHealth(double rawHealth) {
        double health = normalizeHealth(rawHealth);
        entity.health = health;

        entity.getMetadata().set(MetadataIndex.HEALTH, (float) health);

        for (Objective objective : entity.getServer().getScoreboardManager().getMainScoreboard()
                .getObjectivesByCriteria(Criterias.HEALTH)) {
            objective.getScore(entity.getName()).setScore((int) health);
        }

        // Entity survives or used totem, bail
        if (survived()) {
            return;
        }

        // Entity died
        entity.setActive(false);
        playDeathSound();

        if (entity instanceof GlowPlayer) {
            GlowPlayer player = (GlowPlayer) entity;
            handlePlayerDeath(player);
        }
        handleEntityDeath(entity);

        // TODO: Add a die method to GlowEntity class and override in
        // various subclasses depending on the actions needed to be run
        // to help keep code maintainable
        if (entity instanceof GlowSlime) {
            GlowSlime slime = (GlowSlime) entity;
            handleSlimeDeath(slime);
        }
    }

    private double normalizeHealth(double health) {
        if (health < 0) {
            return 0;
        }

        if (health > entity.getMaxHealth()) {
            return entity.getMaxHealth();
        }
        return health;
    }

    private boolean survived() {
        return entity.getHealth() > 0 || entity.tryUseTotem();
    }

    private void playDeathSound() {
        Sound deathSound = entity.getDeathSound();
        if (deathSound != null && !entity.isSilent()) {
            entity.getWorld().playSound(entity.getLocation(), deathSound, entity.getSoundVolume(), entity.getSoundPitch());
        }

        entity.playEffectKnownAndSelf(EntityEffect.DEATH);
    }

    private void handlePlayerDeath(GlowPlayer player) {
        List<ItemStack> items = null;
        boolean dropInventory = !world.getGameRuleMap().getBoolean(GameRules.KEEP_INVENTORY);
        if (dropInventory) {
            items = Arrays.stream(player.getInventory().getContents())
                    .filter(stack -> !InventoryUtil.isEmpty(stack))
                    .collect(Collectors.toList());
            player.getInventory().clear();
        }

        PlayerDeathEvent event = new PlayerDeathEvent(player, items, 0,
                player.getDisplayName() + " died.");
        EventFactory.getInstance().callEvent(event);
        player.getServer().broadcastMessage(event.getDeathMessage());

        if (dropInventory) {
            for (ItemStack item : items) {
                world.dropItemNaturally(player.getLocation(), item);
            }
        }
        player.setShoulderEntityRight(null);
        player.setShoulderEntityLeft(null);
        player.incrementStatistic(Statistic.DEATHS);
    }

    private void handleEntityDeath(GlowLivingEntity entity) {
        EntityDeathEvent deathEvent = new EntityDeathEvent(entity, new ArrayList<>());
        if (world.getGameRuleMap().getBoolean(GameRules.DO_MOB_LOOT)) {
            LootData data = LootingManager.generate(entity);
            deathEvent.getDrops().addAll(data.getItems());
            // Only drop experience when hit by a player within 5 seconds (100 game ticks)
            if (entity.getTicksLived() - entity.getPlayerDamageTick() <= 100 && data.getExperience() > 0) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                ExperienceSplitter.forEachCut(data.getExperience(), exp -> {
                    double modX = random.nextDouble() - 0.5;
                    double modZ = random.nextDouble() - 0.5;

                    Location location = entity.getLocation();
                    Location xpLocation = new Location(world,
                            location.getBlockX() + 0.5 + modX, location.getY(),
                            location.getBlockZ() + 0.5 + modZ);

                    GlowExperienceOrb orb = (GlowExperienceOrb) world
                            .spawnEntity(xpLocation, EntityType.EXPERIENCE_ORB);
                    orb.setExperience(exp);
                    orb.setSourceEntityId(entity.getUniqueId());
                    if (entity.getLastDamager() != null) {
                        orb.setTriggerEntityId(entity.getLastDamager().getUniqueId());
                    }
                });
            }
        }

        deathEvent = EventFactory.getInstance().callEvent(deathEvent);
        for (ItemStack item : deathEvent.getDrops()) {
            world.dropItemNaturally(entity.getLocation(), item);
        }
    }

    private void handleSlimeDeath(GlowSlime slime) {
        int size = slime.getSize();
        if (size > 1) {
            int count = 2 + ThreadLocalRandom.current().nextInt(3);

            SlimeSplitEvent event = EventFactory.getInstance().callEvent(
                    new SlimeSplitEvent(slime, count));
            if (event.isCancelled() || event.getCount() <= 0) {
                return;
            }

            count = event.getCount();
            for (int i = 0; i < count; ++i) {
                Location spawnLoc = slime.getLocation().clone();
                spawnLoc.add(
                        ThreadLocalRandom.current().nextDouble(0.5, 3),
                        0,
                        ThreadLocalRandom.current().nextDouble(0.5, 3)
                );

                GlowSlime splitSlime = (GlowSlime) world.spawnEntity(
                        spawnLoc, EntityType.SLIME);

                // Make the split slime the same name as the killed slime.
                if (!slime.getCustomName().isEmpty()) {
                    splitSlime.setCustomName(slime.getCustomName());
                }

                splitSlime.setSize(size / 2);
            }
        }
    }
}
