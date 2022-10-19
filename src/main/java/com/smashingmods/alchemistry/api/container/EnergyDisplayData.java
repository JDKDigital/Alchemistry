package com.smashingmods.alchemistry.api.container;

import com.smashingmods.alchemistry.api.blockentity.AbstractProcessingBlockEntity;

import java.text.NumberFormat;
import java.util.Locale;

public class EnergyDisplayData extends DisplayData {

    AbstractProcessingBlockEntity blockEntity;

    public EnergyDisplayData(AbstractProcessingBlockEntity pBlockEntity, int pX, int pY, int pWidth, int pHeight) {
        super(pX, pY, pWidth, pHeight);
        this.blockEntity = pBlockEntity;
    }

    @Override
    public int getValue() {
        return blockEntity.getEnergyHandler().getEnergyStored();
    }

    @Override
    public int getMaxValue() {
        return blockEntity.getEnergyHandler().getMaxEnergyStored();
    }

    @Override
    public String toString() {
        NumberFormat numFormat = NumberFormat.getInstance(Locale.US);
        String stored = numFormat.format(getValue());
        String capacity = numFormat.format(getMaxValue());
        return stored + "/" + capacity + " FE";
    }
}
