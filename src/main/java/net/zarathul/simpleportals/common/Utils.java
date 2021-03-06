package net.zarathul.simpleportals.common;

import java.lang.reflect.Field;
import java.util.ArrayList;

import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.*;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.zarathul.simpleportals.SimplePortals;

/**
 * General utility class.
 */
public final class Utils {
	private static Field invulnerableDimensionChange;

	/**
	 * A predicate that returns <code>true</code> if passed string is neither
	 * <code>null</code> nor empty.
	 */
	private static final Predicate<String> stringNotNullOrEmpty = new Predicate<String>() {
		@Override
		public boolean apply(String item) {
			return !Strings.isNullOrEmpty(item);
		}
	};

	/**
	 * Checks a list of strings for <code>null</code> and empty elements.
	 * 
	 * @param items
	 *            The list of strings to check.
	 * @return <code>true</code> if the list neither contains <code>null</code>
	 *         elements nor empty strings, otherwise <code>false</code>.
	 */
	public static final boolean notNullorEmpty(Iterable<String> items) {
		return Iterables.all(items, stringNotNullOrEmpty);
	}

	/**
	 * Gets the localized formatted strings for the specified key and formatting
	 * arguments.
	 * 
	 * @param key
	 *            The base key without an index (e.g. "myKey" gets "myKey0",
	 *            "myKey1" ... etc.).
	 * @param args
	 *            Formatting arguments.
	 * @return
	 */
	public static final ArrayList<String> multiLineTranslateToLocal(String key, Object... args) {
		ArrayList<String> lines = new ArrayList<String>();

		if (key != null) {
			int x = 0;
			String currentKey = key + x;

			while (I18n.canTranslate(currentKey)) {
				lines.add(I18n.translateToLocalFormatted(currentKey, args));
				currentKey = key + ++x;
			}
		}

		return lines;
	}

	/**
	 * Gets the coordinate component of a BlockPos for the specified axis.
	 * 
	 * @param pos
	 *            The coordinate to choose the component from.
	 * @param axis
	 *            The axis representing the coordinate component to choose.
	 * @return <code>0</code> if either pos or axis are <code>null</code>, otherwise
	 *         the chosen coordinate component.
	 */
	public static final int getAxisValue(BlockPos pos, Axis axis) {
		if (pos != null || axis != null) {
			if (axis == Axis.X)
				return pos.getX();
			if (axis == Axis.Y)
				return pos.getY();
			if (axis == Axis.Z)
				return pos.getZ();
		}

		return 0;
	}

	/**
	 * Gets the relative direction from one {@link BlockPos} to another.
	 * 
	 * @param from
	 *            The starting point.
	 * @param to
	 *            The end point.
	 * @return One of the {@link EnumFacing} values or <code>null</code> if one of
	 *         the arguments was <code>null</code>.
	 */
	public static final EnumFacing getRelativeDirection(BlockPos from, BlockPos to) {
		if (from == null || to == null)
			return null;

		BlockPos directionVec = to.subtract(from);

		return EnumFacing.getFacingFromVector(directionVec.getX(), directionVec.getY(), directionVec.getZ());
	}

	/**
	 * Gets the axis that is orthogonal to, and on the same plane as the specified
	 * one.
	 * 
	 * @param axis
	 *            The starting axis.
	 * @return One of the {@link Axis} values or <code>null</code> if the specified
	 *         axis was <code>null</code> or there is no other axis on the same
	 *         plane.
	 */
	public static final Axis getOrthogonalTo(Axis axis) {
		if (axis == null || axis == Axis.Y)
			return null;

		return (axis == Axis.X) ? Axis.Z : Axis.X;
	}

	/**
	 * Teleport an entity to the specified position in the specified dimension
	 * facing the specified direction.
	 * 
	 * @param entity
	 *            The entity to teleport. Can be any entity (item, mob, player).
	 * @param dimension
	 *            The dimension to port to.
	 * @param destination
	 *            The position to port to.
	 * @param facing
	 *            The direction the entity should face after porting.
	 * @return
	 */
	public static final void teleportTo(Entity entity, int dimension, BlockPos destination, EnumFacing facing) {
		if (entity == null || destination == null || entity.isBeingRidden() || entity.isRiding() || !entity.isNonBoss())
			return;

		EntityPlayerMP player = (entity instanceof EntityPlayerMP) ? (EntityPlayerMP) entity : null;
		boolean interdimensional = (entity.dimension != dimension);

		if (player != null) {
			if (!setInvulnerableDimensionChange(player)) {
				SimplePortals.log.error("InvulnerableDimensionChange flag could not be set. Aborting teleportation.");
				return;
			}

			if (interdimensional) {
				if (ForgeHooks.onTravelToDimension(player, dimension)) {
					teleportPlayerToDimension(player, dimension, destination, getYaw(facing));
				} else {
					SimplePortals.log.warn(
							String.format("Teleportation of player %s [%s] to dimension %d canceled by other mod.",
									player.getName(), player.getPosition(), dimension));
				}
			} else {
				player.connection.setPlayerLocation(destination.getX() + 0.5d, destination.getY(),
						destination.getZ() + 0.5d, getYaw(facing), player.rotationPitch);
			}
		} else {
			// setVelocity is marked to be client side only for some reason, so set velocity
			// manually
			entity.motionX = 0;
			entity.motionY = 0;
			entity.motionZ = 0;

			if (interdimensional) {
				teleportNonPlayerEntityToDimension(entity, dimension, destination, getYaw(facing));
			} else {
				entity.setLocationAndAngles(destination.getX() + 0.5d, destination.getY(), destination.getZ() + 0.5d,
						getYaw(facing), entity.rotationPitch);
			}
		}
	}

	/**
	 * Teleport a player entity to the specified position in the specified dimension
	 * facing the specified direction. (Combination of
	 * {@link EntityPlayerMP#changeDimension(int)} and
	 * {@link PlayerList#changePlayerDimension(EntityPlayerMP, int)} without the
	 * hardcoded dimension specific vanilla code)
	 * 
	 * @param player
	 *            The player to teleport.
	 * @param dimension
	 *            The dimension to port to.
	 * @param destination
	 *            The position to port to.
	 * @param yaw
	 *            The rotation yaw the entity should have after porting.
	 */
	private static final void teleportPlayerToDimension(EntityPlayerMP player, int dimension, BlockPos destination,
			float yaw) {
		int startDimension = player.dimension;
		MinecraftServer server = player.getServer();
		PlayerList playerList = server.getPlayerList();
		WorldServer startWorld = server.getWorld(startDimension);
		WorldServer destinationWorld = server.getWorld(dimension);

		player.dimension = dimension;
		player.connection.sendPacket(new SPacketRespawn(dimension, destinationWorld.getDifficulty(),
				destinationWorld.getWorldInfo().getTerrainType(), player.interactionManager.getGameType()));

		playerList.updatePermissionLevel(player);
		startWorld.removeEntityDangerously(player);
		player.isDead = false;

		player.setLocationAndAngles(destination.getX() + 0.5d, destination.getY(), destination.getZ() + 0.5d, yaw,
				player.rotationPitch);

		destinationWorld.spawnEntity(player);
		destinationWorld.updateEntityWithOptionalForce(player, false);
		player.setWorld(destinationWorld);

		playerList.preparePlayer(player, startWorld);
		player.connection.setPlayerLocation(destination.getX() + 0.5d, destination.getY(), destination.getZ() + 0.5d,
				yaw, player.rotationPitch);

		player.interactionManager.setWorld(destinationWorld);
		player.connection.sendPacket(new SPacketPlayerAbilities(player.capabilities));
		playerList.updateTimeAndWeatherForPlayer(player, destinationWorld);
		playerList.syncPlayerInventory(player);

		// Reapply potion effects

		for (PotionEffect potionEffect : player.getActivePotionEffects()) {
			player.connection.sendPacket(new SPacketEntityEffect(player.getEntityId(), potionEffect));
		}

		// Resend player XP otherwise the XP bar won't show up until XP is either gained
		// or lost
		player.connection.sendPacket(
				new SPacketSetExperience(player.experience, player.experienceTotal, player.experienceLevel));

		FMLCommonHandler.instance().firePlayerChangedDimensionEvent(player, startDimension, dimension);
	}

	/**
	 * Teleport a non-player entity to the specified position in the specified
	 * dimension facing the specified direction.
	 * ({@link Entity#changeDimension(int)} without the hardcoded dimension specific
	 * vanilla code)
	 * 
	 * @param entity
	 *            The entity to teleport. Can be any entity (item, mob, player).
	 * @param dimension
	 *            The dimension to port to.
	 * @param destination
	 *            The position to port to.
	 * @param yaw
	 *            The rotation yaw the entity should have after porting.
	 */
	private static final void teleportNonPlayerEntityToDimension(Entity entity, int dimension, BlockPos destination,
			float yaw) {
		MinecraftServer server = entity.getServer();
		WorldServer startWorld = server.getWorld(entity.dimension);
		WorldServer destinationWorld = server.getWorld(dimension);

		entity.dimension = dimension;
		startWorld.removeEntity(entity);
		entity.isDead = false;

		entity.setLocationAndAngles(destination.getX() + 0.5d, destination.getY(), destination.getZ() + 0.5d, yaw,
				entity.rotationPitch);

		startWorld.updateEntityWithOptionalForce(entity, false);

		// Why duplicate the entity and delete the one we just went through the trouble
		// of porting?
		// - Vanilla does it, and without it there are significantly more errors and
		// missing items.
		Entity portedEntity = EntityList.createEntityByIDFromName(EntityList.getKey(entity), destinationWorld);

		if (portedEntity != null) {
			copyEntityNBT(entity, portedEntity);
			portedEntity.setLocationAndAngles(destination.getX() + 0.5d, destination.getY(), destination.getZ() + 0.5d,
					yaw, entity.rotationPitch);

			boolean forceSpawn = portedEntity.forceSpawn;
			portedEntity.forceSpawn = true;
			destinationWorld.spawnEntity(portedEntity);
			portedEntity.forceSpawn = forceSpawn;
			destinationWorld.updateEntityWithOptionalForce(portedEntity, false);
		}

		entity.isDead = true;
		startWorld.resetUpdateEntityTick();
		destinationWorld.resetUpdateEntityTick();
	}

	/**
	 * Copies NBT data from one entity to another, excluding the "Dimension"
	 * tag.<br>
	 * (Copy of {@link Entity#copyDataFromOld(Entity)} because the method is private
	 * as of Minecraft 1.9)
	 * 
	 * @param source
	 *            The entity to read the NBT data from.
	 * @param target
	 *            The entity to write the NBT data to.
	 */
	private static final void copyEntityNBT(Entity source, Entity target) {
		NBTTagCompound tag = new NBTTagCompound();
		source.writeToNBT(tag);
		tag.removeTag("Dimension");
		target.readFromNBT(tag);
	}

	/**
	 * Sets the InvulnerableDimensionChange flag on the specified player. This is
	 * needed to circumvent the illegal movement checks on the server side.
	 * 
	 * @param player
	 *            The player to set the flag for.
	 * @return <code>true</code> if the flag was successfully set, otherwise
	 *         <code>false</code>.
	 */
	private static final boolean setInvulnerableDimensionChange(EntityPlayerMP player) {
		if (player == null)
			return false;

		try {
			if (invulnerableDimensionChange == null) {
				Class<EntityPlayerMP> playerClass = (Class<EntityPlayerMP>) Class
						.forName("net.minecraft.entity.player.EntityPlayerMP");
				invulnerableDimensionChange = playerClass.getDeclaredField("invulnerableDimensionChange"); // invulnerableDimensionChange
																											// field
				invulnerableDimensionChange.setAccessible(true);
			}

			invulnerableDimensionChange.set(player, true);

			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * Converts the specified facing to a degree value.
	 * 
	 * @param facing
	 *            The facing to convert.
	 * @return <code>0</code> if facing is <code>null</code>, otherwise a value
	 *         between <code>0</code> and <code>270</code> that is a multiple of
	 *         <code>90</code>.
	 */
	public static final float getYaw(EnumFacing facing) {
		if (facing == null)
			return 0;

		float yaw;

		switch (facing) {
		case EAST:
			yaw = 270.0f;
			break;

		case WEST:
			yaw = 90.0f;
			break;

		case NORTH:
			yaw = 180.0f;
			break;

		default:
			yaw = 0.0f;
			break;
		}

		return yaw;
	}
}