package com.example.examplemod;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.PaneBlock;

class TestBlock extends PaneBlock {
	public TestBlock(AbstractBlock.Settings settings) {
		super(settings);
	}

	// Test that our access transformer works
	@Override
	public boolean connectsTo(BlockState state, boolean sideSolidFullSquare) {
		return false;
	}
}
