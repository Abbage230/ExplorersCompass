package com.chaosthedude.explorerscompass.items;

import java.util.ArrayList;
import java.util.List;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ConfigHandler;
import com.chaosthedude.explorerscompass.gui.GuiWrapper;
import com.chaosthedude.explorerscompass.network.SyncPacket;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.PlayerUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.chaosthedude.explorerscompass.worker.SearchWorkerManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.network.PacketDistributor;

public class ExplorersCompassItem extends Item {

	public static final String NAME = "explorerscompass";
	
	public static final ResourceKey<Item> KEY = ResourceKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.fromNamespaceAndPath(ExplorersCompass.MODID, NAME));
	
	private SearchWorkerManager workerManager;

	public ExplorersCompassItem() {
		super(new Properties().setId(KEY).stacksTo(1));
		workerManager = new SearchWorkerManager();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!player.isCrouching()) {
			if (level.isClientSide()) {
				final ItemStack stack = ItemUtils.getHeldItem(player, ExplorersCompass.explorersCompass);
				GuiWrapper.openGUI(level, player, stack);
			} else {
				final ServerLevel serverLevel = (ServerLevel) level;
				final ServerPlayer serverPlayer = (ServerPlayer) player;
				final boolean canTeleport = ConfigHandler.GENERAL.allowTeleport.get() && PlayerUtils.canTeleport(player.getServer(), player);
				ExplorersCompass.network.send(new SyncPacket(canTeleport, StructureUtils.getAllowedStructureKeys(serverLevel), StructureUtils.getGeneratingDimensionsForAllowedStructures(serverLevel), StructureUtils.getStructureKeysToTypeKeys(serverLevel), StructureUtils.getTypeKeysToStructureKeys(serverLevel)), PacketDistributor.PLAYER.with(serverPlayer));
			}
		} else {
			workerManager.stop();
			workerManager.clear();
			setState(player.getItemInHand(hand), null, CompassState.INACTIVE, player);
		}
		return InteractionResult.CONSUME;
	}
	
	@Override
 	public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
 		if (getState(oldStack) == getState(newStack)) {
 			return false;
 		}
 		return super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
 	}

	public void searchForStructure(Level level, Player player, ResourceLocation categoryKey, List<ResourceLocation> structureKeys, BlockPos pos, ItemStack stack) {
		setSearching(stack, categoryKey, player);
		setSearchRadius(stack, 0, player);
		if (level instanceof ServerLevel) {
			ServerLevel serverLevel = (ServerLevel) level;
			List<Structure> structures = new ArrayList<Structure>();
			for (ResourceLocation key : structureKeys) {
				structures.add(StructureUtils.getStructureForKey(serverLevel, key));
			}
			workerManager.stop();
			workerManager.createWorkers(serverLevel, player, stack, structures, pos);
			boolean started = workerManager.start();
			if (!started) {
				setNotFound(stack, 0, 0);
			}
		}
	}
	
	public void succeed(ItemStack stack, ResourceLocation structureKey, int x, int z, int samples, boolean displayCoordinates) {
		setFound(stack, structureKey, x, z, samples);
		setDisplayCoordinates(stack, displayCoordinates);
		workerManager.clear();
	}
	
	public void fail(ItemStack stack, int radius, int samples) {
		workerManager.pop();
		boolean started = workerManager.start();
		if (!started) {
			setNotFound(stack, radius, samples);
		}
	}

	public boolean isActive(ItemStack stack) {
		if (ItemUtils.isCompass(stack)) {
			return getState(stack) != CompassState.INACTIVE;
		}

		return false;
	}

	public void setSearching(ItemStack stack, ResourceLocation structureKey, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.STRUCTURE_ID_COMPONENT, structureKey.toString());
			stack.set(ExplorersCompass.COMPASS_STATE_COMPONENT, CompassState.SEARCHING.getID());
		}
	}

	public void setFound(ItemStack stack, ResourceLocation structureKey, int x, int z, int samples) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.COMPASS_STATE_COMPONENT, CompassState.FOUND.getID());
			stack.set(ExplorersCompass.STRUCTURE_ID_COMPONENT, structureKey.toString());
			stack.set(ExplorersCompass.FOUND_X_COMPONENT, x);
			stack.set(ExplorersCompass.FOUND_Z_COMPONENT, z);
			stack.set(ExplorersCompass.SAMPLES_COMPONENT, samples);
		}
	}

	public void setNotFound(ItemStack stack, int searchRadius, int samples) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.COMPASS_STATE_COMPONENT, CompassState.NOT_FOUND.getID());
			stack.set(ExplorersCompass.SEARCH_RADIUS_COMPONENT, searchRadius);
			stack.set(ExplorersCompass.SAMPLES_COMPONENT, samples);
		}
	}

	public void setInactive(ItemStack stack, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.COMPASS_STATE_COMPONENT, CompassState.INACTIVE.getID());
		}
	}

	public void setState(ItemStack stack, BlockPos pos, CompassState state, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.COMPASS_STATE_COMPONENT, state.getID());
		}
	}

	public void setFoundStructureX(ItemStack stack, int x, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.FOUND_X_COMPONENT, x);
		}
	}

	public void setFoundStructureZ(ItemStack stack, int z, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.FOUND_Z_COMPONENT, z);
		}
	}

	public void setStructureKey(ItemStack stack, ResourceLocation structureKey, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.STRUCTURE_ID_COMPONENT, structureKey.toString());
		}
	}

	public void setSearchRadius(ItemStack stack, int searchRadius, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.SEARCH_RADIUS_COMPONENT, searchRadius);
		}
	}

	public void setSamples(ItemStack stack, int samples, Player player) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.SAMPLES_COMPONENT, samples);
		}
	}
	
	public void setDisplayCoordinates(ItemStack stack, boolean displayPosition) {
		if (ItemUtils.isCompass(stack)) {
			stack.set(ExplorersCompass.DISPLAY_COORDS_COMPONENT, displayPosition);
		}
	}

	public CompassState getState(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.COMPASS_STATE_COMPONENT)) {
			return CompassState.fromID(stack.get(ExplorersCompass.COMPASS_STATE_COMPONENT));
		}

		return null;
	}

	public int getFoundStructureX(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.FOUND_X_COMPONENT)) {
			return stack.get(ExplorersCompass.FOUND_X_COMPONENT);
		}

		return 0;
	}

	public int getFoundStructureZ(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.FOUND_Z_COMPONENT)) {
			return stack.get(ExplorersCompass.FOUND_Z_COMPONENT);
		}

		return 0;
	}

	public ResourceLocation getStructureKey(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.STRUCTURE_ID_COMPONENT)) {
			return ResourceLocation.parse(stack.get(ExplorersCompass.STRUCTURE_ID_COMPONENT));
		}

		return ResourceLocation.fromNamespaceAndPath("", "");
	}

	public int getSearchRadius(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.SEARCH_RADIUS_COMPONENT)) {
			return stack.get(ExplorersCompass.SEARCH_RADIUS_COMPONENT);
		}

		return -1;
	}

	public int getSamples(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.SAMPLES_COMPONENT)) {
			return stack.get(ExplorersCompass.SAMPLES_COMPONENT);
		}

		return -1;
	}

	public int getDistanceToBiome(Player player, ItemStack stack) {
		return StructureUtils.getHorizontalDistanceToLocation(player, getFoundStructureX(stack), getFoundStructureZ(stack));
	}
	
	public boolean shouldDisplayCoordinates(ItemStack stack) {
		if (ItemUtils.isCompass(stack) && stack.has(ExplorersCompass.DISPLAY_COORDS_COMPONENT)) {
			return stack.get(ExplorersCompass.DISPLAY_COORDS_COMPONENT);
		}

		return true;
	}

}
