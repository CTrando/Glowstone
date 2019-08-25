package net.glowstone.entity;

import java.util.function.Function;

import net.glowstone.entity.livingentity.GlowLivingEntity;
import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.Test;

public abstract class GlowLivingEntityTest<T extends GlowLivingEntity> extends GlowEntityTest<T> {
    protected GlowLivingEntityTest(
            Function<Location, ? extends T> entityCreator) {
        super(entityCreator);
    }

    @Test
    public void testEntityDamageByBlockEvent() {
        entity.damage(1, null, EntityDamageEvent.DamageCause.CONTACT);
    }
}
