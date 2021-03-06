package net.mohron.skyclaims.island;

import com.flowpowered.math.vector.Vector3i;
import me.ryanhamshire.griefprevention.DataStore;
import me.ryanhamshire.griefprevention.claim.Claim;
import net.mohron.skyclaims.Region;
import net.mohron.skyclaims.SkyClaims;
import net.mohron.skyclaims.config.type.GlobalConfig;
import net.mohron.skyclaims.util.ConfigUtil;
import net.mohron.skyclaims.util.WorldUtil;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Optional;
import java.util.UUID;

public class Island {
	private static final SkyClaims PLUGIN = SkyClaims.getInstance();
	private static GlobalConfig config = PLUGIN.getConfig();
	private static final DataStore claimSystem = PLUGIN.getGriefPrevention().dataStore;

	private UUID owner;
	private Claim claim;
	private Location<World> spawn;

	public Island(Player owner, Claim claim, String schematic) {
		this.owner = owner.getUniqueId();
		this.claim = claim;
		this.spawn = getCenter();

		GenerateIslandTask generateIsland = new GenerateIslandTask(owner, this, schematic);
		PLUGIN.getGame().getScheduler().createTaskBuilder().execute(generateIsland).submit(PLUGIN);

		save();
	}

	public Island(UUID owner, UUID worldId, UUID claimId, Vector3i spawnLocation) {
		World world = PLUGIN.getGame().getServer().getWorld(worldId).orElseGet(WorldUtil::getDefaultWorld);

		this.owner = owner;
		this.claim = claimSystem.getClaim(world.getProperties(), claimId);
		this.spawn = new Location<>(world, spawnLocation);
	}

	public UUID getOwner() {
		return owner;
	}

	public String getOwnerName() {
		return (getUser().isPresent()) ? getUser().get().getName() : "Unknown";
	}

	public Optional<User> getUser() {
		Optional<UserStorageService> optStorage = Sponge.getServiceManager().provide(UserStorageService.class);
		if (optStorage.isPresent()) {
			UserStorageService storage = optStorage.get();
			return (storage.get(owner).isPresent()) ? Optional.of(storage.get(owner).get()) : Optional.empty();
		}
		return Optional.empty();
	}

	public Claim getClaim() {
		return claim;
	}

	public UUID getClaimId() {
		return claim.getID();
	}

	public String getDateCreated() {
		return claim.getClaimData().getDateCreated();
	}

	public World getWorld() {
		return ConfigUtil.getWorld();
	}

	public Location<World> getSpawn() {
		return spawn;
	}

	public void setSpawn(Location<World> spawn) {
		this.spawn = spawn;
		save();
	}

	public boolean isWithinIsland(Location<World> location) {
		return claim.contains(location, true, false);
	}

	public int getRadius() {
		return (claim.getGreaterBoundaryCorner().getBlockX() - claim.getLesserBoundaryCorner().getBlockX()) / 2;
	}

	public Location<World> getCenter() {
		int radius = this.getRadius();
		return new Location<>(ConfigUtil.getWorld(),
				claim.getLesserBoundaryCorner().getX() + radius,
				ConfigUtil.get(config.world.defaultHeight, 72),
				claim.getLesserBoundaryCorner().getZ() + radius);
	}

	public boolean hasPermissions(Player player) {
		return player.getUniqueId().equals(claim.getOwnerUniqueId()) ||
				claim.getClaimData().getContainers().contains(player.getUniqueId()) ||
				claim.getClaimData().getBuilders().contains(player.getUniqueId()) ||
				claim.getClaimData().getManagers().contains(player.getUniqueId());
	}

	public Region getRegion() {
		return new Region(getCenter().getChunkPosition().getX() >> 5, getCenter().getChunkPosition().getZ() >> 5);
	}

	public void save() {
		SkyClaims.islands.put(owner, this);
		PLUGIN.getDatabase().saveIsland(this);
	}

	public void delete() {
		RegenerateRegionTask regenerateRegionTask = new RegenerateRegionTask(getRegion());
		PLUGIN.getGame().getScheduler().createTaskBuilder().execute(regenerateRegionTask).submit(PLUGIN);
		SkyClaims.islands.remove(owner);
		PLUGIN.getDatabase().removeIsland(this);
	}
}