package com.smashingmods.alchemistry.api.blockentity;

import com.smashingmods.alchemistry.api.blockentity.handler.AutomationStackHandler;
import com.smashingmods.alchemistry.api.blockentity.handler.CustomStackHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;

public interface InventoryBlockEntity {

    CustomStackHandler getInputHandler();

    CustomStackHandler getOutputHandler();

    AutomationStackHandler getAutomationInputHandler(IItemHandlerModifiable input);

    AutomationStackHandler getAutomationOutputHandler(IItemHandlerModifiable output);

    CombinedInvWrapper getAutomationInventory();

    CustomStackHandler initializeInputHandler();

    CustomStackHandler initializeOutputHandler();

    void getDrops();
}