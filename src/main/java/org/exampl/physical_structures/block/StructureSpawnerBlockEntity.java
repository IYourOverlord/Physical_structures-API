package org.exampl.physical_structures.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.exampl.physical_structures.init.ModBlockEntities;

import javax.annotation.Nullable;

/**
 * Хранит id структуры, которую нужно разместить при активации.
 * id сохраняется в NBT блока, поэтому выживает после F5 и перезагрузки чанка.
 */
public class StructureSpawnerBlockEntity extends BlockEntity {

    private static final String KEY = "structure_id";

    /** null = не настроен */
    @Nullable
    private ResourceLocation structureId;

    public StructureSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STRUCTURE_SPAWNER.get(), pos, state);
    }

    // ---------------------------------------------------------------- id

    @Nullable
    public ResourceLocation getStructureId() { return structureId; }

    public void setStructureId(@Nullable ResourceLocation id) {
        this.structureId = id;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---------------------------------------------------------------- persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (structureId != null) tag.putString(KEY, structureId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structureId = tag.contains(KEY)
                ? ResourceLocation.tryParse(tag.getString(KEY))
                : null;
    }

    // ---------------------------------------------------------------- sync to client (tooltip, etc.)

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
