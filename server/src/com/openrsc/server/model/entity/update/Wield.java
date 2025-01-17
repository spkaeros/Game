package com.openrsc.server.model.entity.update;

import com.openrsc.server.constants.Skills;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;

public class Wield	 {

	private Mob mob;
	private int index;
	private int wield;
	private int wield2;

	public Wield(Mob mob, int wield, int wield2) {
		this.mob = mob;
		this.setWield(wield);
		this.setWield2(wield2);
		this.setIndex(mob.getIndex());
	}

	public void setWield(int wield) {
		this.wield = wield;
	}
	public void setWield2(int wield2) {
		this.wield2 = wield2;
	}

	public int getWield() {
		return wield;
	}
	public int getWield2() {
		return wield2;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}
}
