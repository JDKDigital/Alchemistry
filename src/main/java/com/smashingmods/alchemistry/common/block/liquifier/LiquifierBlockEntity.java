package com.smashingmods.alchemistry.common.block.liquifier;

import com.smashingmods.alchemistry.Config;
import com.smashingmods.alchemistry.api.blockentity.*;
import com.smashingmods.alchemistry.api.blockentity.handler.AutomationStackHandler;
import com.smashingmods.alchemistry.api.blockentity.handler.CustomEnergyStorage;
import com.smashingmods.alchemistry.api.blockentity.handler.CustomFluidStorage;
import com.smashingmods.alchemistry.api.blockentity.handler.CustomItemStackHandler;
import com.smashingmods.alchemistry.common.recipe.liquifier.LiquifierRecipe;
import com.smashingmods.alchemistry.registry.BlockEntityRegistry;
import com.smashingmods.alchemistry.registry.RecipeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.jetbrains.annotations.Nullable;

public class LiquifierBlockEntity extends AbstractAlchemistryBlockEntity implements InventoryBlockEntity, EnergyBlockEntity, FluidBlockEntity, ProcessingBlockEntity {
    protected final ContainerData data;

    private int progress = 0;
    private final int maxProgress = Config.Common.liquifierTicksPerOperation.get();

    private final CustomItemStackHandler inputHandler = initializeInputHandler();
    private final CustomItemStackHandler outputHandler = initializeOutputHandler();

    private final AutomationStackHandler automationInputHandler = getAutomationInputHandler(getInputHandler());
    private final AutomationStackHandler automationOutputHandler = getAutomationOutputHandler(getOutputHandler());

    private final CombinedInvWrapper combinedInvWrapper = new CombinedInvWrapper(automationInputHandler, automationOutputHandler);
    private final LazyOptional<IItemHandler> lazyItemHandler = LazyOptional.of(() -> combinedInvWrapper);

    private final CustomEnergyStorage energyHandler = initializeEnergyStorage();
    private final LazyOptional<IEnergyStorage> lazyEnergyHandler = LazyOptional.of(() -> energyHandler);

    private final CustomFluidStorage fluidHandler = initializeFluidStorage();
    private final LazyOptional<IFluidHandler> lazyFluidHandler = LazyOptional.of(() -> fluidHandler);

    private LiquifierRecipe currentRecipe;

    public LiquifierBlockEntity(BlockPos pWorldPosition, BlockState pBlockState) {
        super(BlockEntityRegistry.LIQUIFIER_BLOCK_ENTITY.get(), pWorldPosition, pBlockState);

        this.data = new ContainerData() {
            @Override
            public int get(int pIndex) {
                return switch (pIndex) {
                    case 0 -> progress;
                    case 1 -> maxProgress;
                    case 2 -> energyHandler.getEnergyStored();
                    case 3 -> energyHandler.getMaxEnergyStored();
                    case 4 -> fluidHandler.getFluidAmount();
                    case 5 -> fluidHandler.getCapacity();
                    default -> 0;
                };
            }

            @Override
            public void set(int pIndex, int pValue) {
                switch (pIndex) {
                    case 0 -> progress= pValue;
                    case 2 -> energyHandler.setEnergy(pValue);
                    case 4 -> fluidHandler.setAmount(pValue);
                }
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide()) {
            updateRecipe();
            if (canProcessRecipe()) {
                processRecipe();
            }
        } else {
            progress = 0;
        }
    }

    @Override
    public void updateRecipe() {
        if (level != null && !level.isClientSide()) {
            currentRecipe = RecipeRegistry.getRecipesByType(RecipeRegistry.LIQUIFIER_TYPE, level).stream()
                    .filter(recipe -> ItemStack.isSameItemSameTags(recipe.getInput(), inputHandler.getStackInSlot(0)))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Override
    public boolean canProcessRecipe() {
        if (currentRecipe != null) {
            return energyHandler.getEnergyStored() >= Config.Common.liquifierEnergyPerTick.get()
                    && fluidHandler.getFluidStack().isFluidEqual(currentRecipe.getOutput()) || fluidHandler.isEmpty()
                    && fluidHandler.getFluidAmount() <= fluidHandler.getFluidAmount() + currentRecipe.getOutput().getAmount()
                    && ItemStack.isSameItemSameTags(currentRecipe.getInput().copy(), getInputHandler().getStackInSlot(0))
                    && getInputHandler().getStackInSlot(0).getCount() >= currentRecipe.getInput().getCount();
        } else {
            return false;
        }
    }

    @Override
    public void processRecipe() {
        if (progress < maxProgress) {
            progress++;
        } else {
            progress = 0;
            inputHandler.decrementSlot(0, currentRecipe.getInput().getCount());
            fluidHandler.fill(currentRecipe.getOutput().copy(), IFluidHandler.FluidAction.EXECUTE);
        }
        energyHandler.extractEnergy(Config.Common.liquifierEnergyPerTick.get(), false);
        setChanged();
    }

    @Override
    public CustomEnergyStorage initializeEnergyStorage() {
        return new CustomEnergyStorage(Config.Common.liquifierEnergyCapacity.get()) {
            @Override
            protected void onEnergyChanged() {
                super.onEnergyChanged();
                setChanged();
            }
        };
    }

    @Override
    public CustomFluidStorage initializeFluidStorage() {
        return new CustomFluidStorage(Config.Common.liquifierFluidCapacity.get(), FluidStack.EMPTY) {
            @Override
            protected void onContentsChanged() {
                super.onContentsChanged();
                setChanged();
            }
        };
    }

    @Override
    public CustomItemStackHandler initializeInputHandler() {
        return new CustomItemStackHandler(1);
    }

    @Override
    public CustomItemStackHandler initializeOutputHandler() {
        return new CustomItemStackHandler(1) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
    }

    @Override
    public CustomItemStackHandler getInputHandler() {
        return inputHandler;
    }

    @Override
    public CustomItemStackHandler getOutputHandler() {
        return outputHandler;
    }

    @Override
    public AutomationStackHandler getAutomationInputHandler(IItemHandlerModifiable pHandler) {
        return new AutomationStackHandler(pHandler) {
            @Override
            public ItemStack extractItem(int pSlot, int pAmount, boolean pSimulate) {
                return ItemStack.EMPTY;
            }
        };
    }

    @Override
    public AutomationStackHandler getAutomationOutputHandler(IItemHandlerModifiable pHandler) {
        return new AutomationStackHandler(pHandler) {
            @Override
            public ItemStack extractItem(int pSlot, int pAmount, boolean pSimulate) {
                if (!getStackInSlot(pSlot).isEmpty()) {
                    return super.extractItem(pSlot, pAmount, pSimulate);
                } else {
                    return ItemStack.EMPTY;
                }
            }
        };
    }

    @Override
    public CombinedInvWrapper getAutomationInventory() {
        return combinedInvWrapper;
    }

    public boolean onBlockActivated(Level pLevel, BlockPos pBlockPos, Player pPlayer, InteractionHand pHand) {
        return FluidUtil.interactWithFluidHandler(pPlayer, pHand, pLevel, pBlockPos, null);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction pDirection) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return lazyItemHandler.cast();
        } else if (cap == CapabilityEnergy.ENERGY) {
            return lazyEnergyHandler.cast();
        } else if (cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return lazyFluidHandler.cast();
        }
        return super.getCapability(cap, pDirection);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lazyItemHandler.invalidate();
        lazyEnergyHandler.invalidate();
        lazyFluidHandler.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("progress", progress);
        pTag.put("input", inputHandler.serializeNBT());
        pTag.put("output", outputHandler.serializeNBT());
        pTag.put("energy", energyHandler.serializeNBT());
        pTag.put("fluid", fluidHandler.writeToNBT(new CompoundTag()));
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        progress = pTag.getInt("progress");
        inputHandler.deserializeNBT(pTag.getCompound("input"));
        outputHandler.deserializeNBT(pTag.getCompound("output"));
        energyHandler.deserializeNBT(pTag.get("energy"));
        fluidHandler.readFromNBT(pTag.getCompound("fluid"));
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int pContainerId, Inventory pInventory, Player pPlayer) {
        return new LiquifierMenu(pContainerId, pInventory, this, this.data);
    }
}
