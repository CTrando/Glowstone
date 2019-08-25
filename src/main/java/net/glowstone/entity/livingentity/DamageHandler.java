package net.glowstone.entity.livingentity;

import net.glowstone.EventFactory;
import net.glowstone.GlowWorld;
import net.glowstone.entity.AttributeManager;
import net.glowstone.entity.GlowPlayer;
import net.glowstone.entity.GlowTntPrimed;
import net.glowstone.entity.passive.GlowWolf;
import net.glowstone.util.RayUtil;
import org.bukkit.EntityEffect;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

class DamageHandler {

    private GlowLivingEntity entity;
    private GlowWorld world;

    public DamageHandler(GlowLivingEntity e) {
        entity = e;
        world = e.getWorld();
    }

    void damage(double rawAmount, Entity source, EntityDamageEvent.DamageCause cause) {
        if (isInvulnerable(cause)) {
            return;
        }

        entity.setNoDamageTicks(entity.getMaximumNoDamageTicks());
        double damageAmount = normalizeAmount(rawAmount);
        EntityDamageEvent eventKind = source == null ?
                new EntityDamageEvent(entity, cause, damageAmount) :
                new EntityDamageByEntityEvent(source, entity, cause, damageAmount);
        damage(source, cause, eventKind);
    }

    private void damage(Entity source, EntityDamageEvent.DamageCause cause, EntityDamageEvent eventKind) {
        if (fireResistanceApplies(source, cause)) {
            return;
        }

        // Fire EntityDamageEvent
        EntityDamageEvent event = EventFactory.getInstance().onEntityDamage(eventKind);
        if (event.isCancelled()) {
            return;
        }
        // Apply damage
        final double finalDamageAmount = event.getFinalDamage();
        entity.setLastDamage(finalDamageAmount);

        handleEntityDamaged(source, finalDamageAmount);
        handleKnockback(source, cause, event.getDamage());
    }

    private boolean fireResistanceApplies(Entity source, EntityDamageEvent.DamageCause cause) {
        // fire resistance
        if (cause != null && entity.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
            if (source instanceof Fireball) {
                return true;
            } else {
                switch (cause) {
                    case FIRE:
                    case FIRE_TICK:
                    case HOT_FLOOR:
                    case LAVA:
                        return true;
                }
            }
        }
        return false;
    }

    private void handleEntityDamaged(Entity source, double damageAmount) {
        double resultingHealth = entity.getHealth() - damageAmount;
        if (isPlayerHit(source)) {
            entity.setPlayerDamageTick(entity.getTicksLived());
            if (resultingHealth <= 0) {
                Player player = determinePlayer(source);
                entity.setKiller(player);
                if (player != null) {
                    player.incrementStatistic(Statistic.KILL_ENTITY, entity.getType());
                }
            }
        }

        Sound hurtSound = entity.getHurtSound();
        if (hurtSound != null && !entity.isSilent()) {
            world.playSound(entity.getLocation(), hurtSound, entity.getSoundVolume(), entity.getSoundPitch());
        }

        entity.setHealth(resultingHealth);
        entity.playEffectKnownAndSelf(EntityEffect.HURT);
        entity.setLastDamager(source);
    }

    private void handleKnockback(Entity source, EntityDamageEvent.DamageCause cause, double rawDamageAmount) {
        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK && source != null) {
            Vector distance = RayUtil
                    .getRayBetween(entity.getLocation(), ((LivingEntity) source).getEyeLocation());

            Vector rayLength = RayUtil.getVelocityRay(distance).normalize();

            Vector currentVelocity = source.getVelocity();
            currentVelocity.add(rayLength.multiply(((rawDamageAmount + 1) / 2d)));
            entity.setVelocity(currentVelocity);
        }
    }

    private double normalizeAmount(double amount) {
        AttributeManager manager = entity.getAttributeManager();
        // armor damage protection
        // formula source: http://minecraft.gamepedia.com/Armor#Damage_Protection
        double defensePoints = manager.getPropertyValue(AttributeManager.Key.KEY_ARMOR);
        double toughness = manager.getPropertyValue(AttributeManager.Key.KEY_ARMOR_TOUGHNESS);
        return amount * (1 - Math.min(20.0,
                Math.max(defensePoints / 5.0,
                        defensePoints - amount / (2.0 + toughness / 4.0))) / 25);
    }

    /**
     * Checks if the source of damage was caused by a player.
     *
     * @param source The source of damage
     * @return true if the source of damage was caused by a player, false otherwise.
     */
    private boolean isPlayerHit(Entity source) {
        // If directly damaged by a player
        if (source instanceof GlowPlayer) {
            return true;
        }

        // If damaged by a TNT ignited by a player
        if (source instanceof GlowTntPrimed) {
            GlowPlayer player = (GlowPlayer) ((GlowTntPrimed) source).getSource();
            return
                    player != null
                            && (player.getGameMode() == GameMode.SURVIVAL
                            || player.getGameMode() == GameMode.ADVENTURE);
        }

        // If damaged by a tamed wolf
        if (source instanceof GlowWolf) {
            return ((GlowWolf) source).isTamed();
        }

        // All other cases
        return false;
    }

    /**
     * Determines the player who did the damage from source of damage.
     *
     * @param source The incoming source of damage
     * @return Player object if the source of damage was caused by a player, null otherwise.
     */
    private Player determinePlayer(Entity source) {
        // If been killed by an ignited tnt
        if (source instanceof GlowTntPrimed) {
            return (Player) ((GlowTntPrimed) source).getSource();
        }

        // If been killed by a player
        if (source instanceof GlowPlayer) {
            return (Player) source;
        }

        // If been killed by a tamed wolf
        if (source instanceof GlowWolf) {
            return (Player) ((GlowWolf) source).getOwner();
        }

        // All other cases
        return null;
    }

    private boolean isInvulnerable(EntityDamageEvent.DamageCause cause) {
        // invincibility timer
        return entity.getNoDamageTicks() > 0
                || entity.getHealth() <= 0
                || !entity.canTakeDamage(cause)
                || entity.isInvulnerable();
    }
}
