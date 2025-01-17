package com.openrsc.server.model.entity;

import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.ItemLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

import java.util.Objects;

public class GroundItem extends Entity {
	/**
	 * Amount (for stackables)
	 */
	private int amount;

	/**
	 * Location definition of the item
	 */
	private ItemLoc loc = null;

	/**
	 * Contains the player that the item belongs to, if any
	 */
	private long ownerUsernameHash;
	/**
	 * The time that the item was spawned
	 */
	private long spawnedTime = 0L;

	public GroundItem(World world, int id, Point location) { // used for ::masks
		super(world);
		super.id = id;
		super.location.set(location);
		amount = 1;
	}

	public GroundItem(World world, int id, int x, int y, int amount, Player owner) {
		super(world);
		setID(id);
		setAmount(amount);
		//this.ownerUsernameHash = owner.getUsernameHash();
		this.ownerUsernameHash = owner == null ? 0 : owner.getUsernameHash();
		spawnedTime = System.currentTimeMillis();
		setLocation(Point.location(x, y));
		if (owner != null && owner.getIronMan() >= IronmanMode.Ironman.id() && owner.getIronMan() <= IronmanMode.Transfer.id())
			this.setAttribute("isIronmanItem", true);
	}

	public GroundItem(World world, int id, int x, int y, int amount, Npc owner) {
		super(world);
		setID(id);
		setAmount(amount);
		//this.ownerUsernameHash = owner == null ? 0 : owner.getUsernameHash();
		spawnedTime = System.currentTimeMillis();
		setLocation(Point.location(x, y));
	}

	public GroundItem(World world, int id, int x, int y, int amount, Player owner, long spawntime) {
		super(world);
		setID(id);
		setAmount(amount);
		this.ownerUsernameHash = owner == null ? 0 : owner.getUsernameHash();
		spawnedTime = spawntime;
		setLocation(Point.location(x, y));
	}

	public GroundItem(World world, int id, int x, int y, int amount, Npc owner, long spawntime) {
		super(world);
		setID(id);
		setAmount(amount);
		//this.ownerUsernameHash = owner == null ? 0 : owner.getUsernameHash();
		spawnedTime = spawntime;
		setLocation(Point.location(x, y));
	}

	public GroundItem(World world, ItemLoc loc) {
		super(world);
		this.loc = loc;
		setID(loc.id);
		setAmount(loc.amount);
		spawnedTime = System.currentTimeMillis();
		setLocation(Point.location(loc.x, loc.y));
	}

	public boolean belongsTo(Player p) {
		if (p.getParty() != null) {
			for (Player p2 : getWorld().getPlayers()) {
				if (Objects.requireNonNull(p.getParty()).getPlayers().size() > 1 && p.getParty() != null && p.getParty() == p2.getParty()) {
					PartyPlayer p3 = p2.getParty().getLeader();
					if (p3.getShareLoot() > 0) {
						p = p2;
						p.getUsernameHash();
						p2.getUsernameHash();
						return true;
					}
				}
			}
		}
		return p.getUsernameHash() == ownerUsernameHash || ownerUsernameHash == 0;
	}

	public long getOwnerUsernameHash() {
		return ownerUsernameHash;
	}

	public boolean is(Object o) {
		if (o instanceof GroundItem) {
			GroundItem item = (GroundItem) o;
			return item.getID() == getID() && item.getAmount() == getAmount()
				&& item.getSpawnedTime() == getSpawnedTime()
				&& (item.getOwnerUsernameHash() == getOwnerUsernameHash())
				&& item.getLocation().equals(getLocation());
		}
		return false;
	}

	public int getAmount() {
		return amount;
	}

	public void setAmount(int amount) {
		if (getDef() != null) {
			if (getDef().isStackable()) {
				this.amount = amount;
			} else {
				this.amount = 1;
			}
		}
	}

	public ItemDefinition getDef() {
		return getWorld().getServer().getEntityHandler().getItemDef(id);
	}

	public ItemLoc getLoc() {
		return loc;
	}

	private long getSpawnedTime() {
		return spawnedTime;
	}

	boolean isOn(int x, int y) {
		return x == getX() && y == getY();
	}

	public void remove() {
		if (!removed && loc != null && loc.getRespawnTime() > 0) {
			getWorld().getServer().getGameEventHandler().add(new GameTickEvent(getWorld(), null, loc.getRespawnTime(), "Respawn Ground Item") {
				public void run() {
					getWorld().registerItem(new GroundItem(getWorld(), loc));
					stop();
				}
			});
		}
		super.remove();
	}

	public boolean visibleTo(Player p) {
		if (belongsTo(p))
			return true;
		if (getDef().isMembersOnly() && !getWorld().getServer().getConfig().MEMBER_WORLD)
			return false;
		if (getDef().isUntradable())
			return false;
		if (!belongsTo(p) && p.getIronMan() >= IronmanMode.Ironman.id() && p.getIronMan() <= IronmanMode.Transfer.id())
			return false;

		// One minute and four seconds to show to all.
		return System.currentTimeMillis() - spawnedTime > 64000;
	}

	@Override
	public String toString() {
		return "Item(" + this.id + ", " + this.amount + ") location = " + location.toString();
	}
}
