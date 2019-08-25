package net.glowstone.block.blocktype;

import net.glowstone.block.GlowBlock;
import net.glowstone.entity.livingentity.GlowLivingEntity;
import net.glowstone.inventory.ToolType;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent;

public class BlockMagma extends BlockDirectDrops {

    public BlockMagma() {
        super(Material.MAGMA, ToolType.PICKAXE);
    }

    @Override
    public void onEntityStep(GlowBlock block, GlowLivingEntity entity) {
        entity.damage(1.0, EntityDamageEvent.DamageCause.FIRE);
    }
}
