package com.openrsc.server.plugins.commands;

import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Group;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.listeners.action.CommandListener;
import com.openrsc.server.sql.query.logs.StaffLog;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class Event implements CommandListener {
	public static final Logger LOGGER = LogManager.getLogger(Event.class);

	public static String messagePrefix = null;
	public static String badSyntaxPrefix = null;

	private static final String[] towns = {"varrock", "falador", "draynor", "portsarim", "karamja", "alkharid",
		"lumbridge", "edgeville", "castle", "taverly", "clubhouse", "seers", "barbarian", "rimmington", "catherby",
		"ardougne", "yanille", "lostcity", "gnome", "shilovillage", "tutorial", "modroom", "entrana"};

	private static final Point[] townLocations = {Point.location(122, 509), Point.location(304, 542),
		Point.location(214, 632), Point.location(269, 643), Point.location(370, 685), Point.location(89, 693),
		Point.location(120, 648), Point.location(217, 449), Point.location(270, 352), Point.location(373, 498),
		Point.location(653, 491), Point.location(501, 450), Point.location(233, 513), Point.location(325, 663),
		Point.location(440, 501), Point.location(549, 589), Point.location(583, 747), Point.location(127, 3518),
		Point.location(703, 527), Point.location(400, 850), Point.location(217, 740), Point.location(75, 1641), Point.location(425,564)};

	public void onCommand(String cmd, String[] args, Player player) {
		if (isCommandAllowed(player, cmd)) {

			if(messagePrefix == null) {
				messagePrefix = player.getWorld().getServer().getConfig().MESSAGE_PREFIX;
			}
			if(badSyntaxPrefix == null) {
				badSyntaxPrefix = player.getWorld().getServer().getConfig().BAD_SYNTAX_PREFIX;
			}

			handleCommand(cmd, args, player);
		}
	}

	public boolean isCommandAllowed(Player player, String cmd) {
		return player.isEvent();
	}

	/**
	 * Template for ::dev commands
	 * Development usable commands in general
	 */
	@Override
	public void handleCommand(String cmd, String[] args, Player player) {
		if (cmd.equalsIgnoreCase("teleport") || cmd.equalsIgnoreCase("tp") || cmd.equalsIgnoreCase("town") || cmd.equalsIgnoreCase("goto") || cmd.equalsIgnoreCase("tpto") || cmd.equalsIgnoreCase("teleportto")) {
			if (args.length < 1) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [town/player] OR ");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [town/player] OR ");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y] OR");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [x] [y]");
				return;
			}

			Player p = null;
			boolean isTown = false;
			String town = "";
			int x = -1;
			int y = -1;
			Point originalLocation;
			Point teleportTo;

			if(args.length == 1) {
				p = player;
				town = args[0];
				isTown = true;
			}
			else if(args.length == 2) {
				try {
					x = Integer.parseInt(args[0]);
					isTown = false;

					try {
						y = Integer.parseInt(args[1]);
						p = player;
					}
					catch(NumberFormatException ex) {
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y]");
						return;
					}
				}
				catch(NumberFormatException ex) {
					p = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
					town = args[1];
					isTown = true;
				}
			}
			else if(args.length >= 3) {
				p = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
				try {
					x = Integer.parseInt(args[1]);
				}
				catch(NumberFormatException ex) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [x] [y]");
					return;
				}
				try {
					y = Integer.parseInt(args[2]);
				}
				catch(NumberFormatException ex) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [x] [y]");
					return;
				}
				isTown = false;
			}

			if(p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && player.getGroupID() >= p.getGroupID()) {
				player.message(messagePrefix + "You can not teleport a staff member of equal or greater rank.");
				return;
			}

			if(player.isJailed() && p.getUsernameHash() == player.getUsernameHash() && !player.isAdmin()) {
				player.message(messagePrefix + "You can not teleport while you are jailed.");
				return;
			}

			originalLocation = p.getLocation();

			if (isTown) {
				int townIndex = -1;
				for (int i = 0; i < towns.length; i++) {
					if (town.equalsIgnoreCase(towns[i])) {
						townIndex = i;
						break;
					}
				}

				// townFound will == -1 when not found
				if(townIndex == -1) {
					// townIndex to find a town, look for a player instead...
					Player tpTo = player.getWorld().getPlayer(DataConversions.usernameToHash(town));

					if (tpTo == null) {
						player.message(messagePrefix + "Invalid target");
						return;
					}

					if(tpTo.isInvisible(player) && !player.isAdmin()) {
						player.message(messagePrefix + "You can not teleport to an invisible player.");
						return;
					}

					teleportTo = tpTo.getLocation();
				} else {
					teleportTo = townLocations[townIndex];
				}
			}
			else {
				if(!player.getWorld().withinWorld(x, y)) {
					player.message(messagePrefix + "Invalid coordinates");
					return;
				}

				teleportTo = new Point(x,y);
			}

			// Same player and command usage is tpto or goto, we want to set a return point in order to use ::return later
			if((cmd.equalsIgnoreCase("goto") || cmd.equalsIgnoreCase("tpto")) && p.getUsernameHash() == player.getUsernameHash()) {
				p.setSummonReturnPoint();
			}

			p.teleport(teleportTo.getX(), teleportTo.getY(), true);

			player.message(messagePrefix + "You have teleported " + p.getUsername() + " to " + p.getLocation() + " from " + originalLocation);
			if(p.getUsernameHash() != player.getUsernameHash()) {
				p.message(messagePrefix + "You have been teleported to " + p.getLocation() + " from " + originalLocation);
			}

			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 15, player.getUsername() + " has teleported " + p.getUsername() + " to " + p.getLocation() + " from " + originalLocation));
		}
		else if (cmd.equalsIgnoreCase("return")) {
			Player p = args.length > 0 ?
				player.getWorld().getPlayer(DataConversions.usernameToHash(args[0])) :
				player;

			if(p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}

			if(p.getUsernameHash() != player.getUsernameHash() && !player.isMod()) {
				player.message(messagePrefix + "You can not return other players.");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && player.getGroupID() >= p.getGroupID()) {
				player.message(messagePrefix + "You can not return a staff member of equal or greater rank.");
				return;
			}

			if(!p.wasSummoned()) {
				player.message(messagePrefix + p.getUsername() + " has not been summoned.");
				return;
			}

			Point originalLocation = p.returnFromSummon();
			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 15, player.getUsername() + " has returned " + p.getUsername() + " to " + p.getLocation() + " from " + originalLocation));
			player.message(messagePrefix + "You have returned " + p.getUsername() + " to " + p.getLocation() + " from " + originalLocation);
			if(p.getUsernameHash() != player.getUsernameHash()) {
				p.message(messagePrefix + "You have been returned by " + player.getStaffName());
			}
		}
		else if (cmd.equalsIgnoreCase("blink")) {
			player.setAttribute("blink", !player.getAttribute("blink", false));
			player.message(messagePrefix + "Your blink status is now " + player.getAttribute("blink", false));
			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 10, "Blink was set - " + player.getAttribute("blink", false)));
		}
		else if (cmd.equalsIgnoreCase("invisible") || cmd.equalsIgnoreCase("invis")) {
			Player p = args.length > 0 ?
				player.getWorld().getPlayer(DataConversions.usernameToHash(args[0])) :
				player;

			if(p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && !player.isSuperMod()) {
				player.message(messagePrefix + "You can not make other users invisible.");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && player.getGroupID() >= p.getGroupID()) {
				player.message(messagePrefix + "You can not change the invisible state of a staff member of equal or greater rank.");
				return;
			}

			boolean invisible;
			boolean toggle;
			if(args.length > 1) {
				try {
					invisible = DataConversions.parseBoolean(args[1]);
					toggle = false;
				} catch (NumberFormatException ex) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] (boolean)");
					return;
				}
			} else {
				toggle = true;
				invisible = false;
			}

			boolean newInvisible;
			if(toggle) {
				newInvisible = p.toggleCacheInvisible();
			} else {
				newInvisible = p.setCacheInvisible(invisible);
			}

			String invisibleText = newInvisible ? "invisible" : "visible";
			player.message(messagePrefix + p.getUsername() + " is now " + invisibleText);
			if(p.getUsernameHash() != player.getUsernameHash()) {
				p.message(messagePrefix + "A staff member has made you " + invisibleText);
			}
			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 14, player.getUsername() + " has made " + p.getUsername() + " " + invisibleText));
		}
		else if (cmd.equalsIgnoreCase("invulnerable") || cmd.equalsIgnoreCase("invul")) {
			Player p = args.length > 0 ?
				player.getWorld().getPlayer(DataConversions.usernameToHash(args[0])) :
				player;

			if(p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && !player.isSuperMod()) {
				player.message(messagePrefix + "You can not make other users invisible.");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && player.getGroupID() >= p.getGroupID()) {
				player.message(messagePrefix + "You can not change the invulnerable state of a staff member of equal or greater rank.");
				return;
			}

			boolean invulnerable;
			boolean toggle;
			if(args.length > 1) {
				try {
					invulnerable = DataConversions.parseBoolean(args[1]);
					toggle = false;
				} catch (NumberFormatException ex) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] (boolean)");
					return;
				}
			} else {
				toggle = true;
				invulnerable = false;
			}

			boolean newInvulnerable;
			if(toggle) {
				newInvulnerable = p.toggleCacheInvulnerable();
			} else {
				newInvulnerable = p.setCacheInvulnerable(invulnerable);
			}

			String invulnerbleText = newInvulnerable ? "invulnerable" : "vulnerable";
			player.message(messagePrefix + p.getUsername() + " is now " + invulnerbleText);
			if(p.getUsernameHash() != player.getUsernameHash()) {
				p.message(messagePrefix + "A staff member has made you " + invulnerbleText);
			}
			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 22, player.getUsername() + " has made " + p.getUsername() + " " + invulnerbleText));
		}
		else if (cmd.equalsIgnoreCase("check")) {
			if(args.length < 1) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player]");
			}

			String targetUsername	= args[0];
			Player target			= player.getWorld().getPlayer(DataConversions.usernameToHash(targetUsername));

			String currentIp = null;
			if (target == null) {
				player.message(
					messagePrefix + "No online character found named '" + targetUsername + "'.. checking database..");
				try {
					PreparedStatement statement = player.getWorld().getServer().getDatabaseConnection()
						.prepareStatement("SELECT `login_ip` FROM `" + player.getWorld().getServer().getConfig().MYSQL_TABLE_PREFIX + "players` WHERE `username`=?");
					statement.setString(1, targetUsername);
					ResultSet result = statement.executeQuery();
					if (!result.next()) {
						player.message(messagePrefix + "Error character not found in MySQL");
						return;
					}
					currentIp = result.getString("login_ip");
					result.close();
					player.message(messagePrefix + "Found character '" + targetUsername + "' fetching other characters..");
				} catch (SQLException e) {
					LOGGER.catching(e);
					player.message(messagePrefix + "A MySQL error has occurred! " + e.getMessage());
					return;
				}
			} else {
				currentIp = target.getCurrentIP();
			}

			if (currentIp == null) {
				player.message(messagePrefix + "An unknown error has occurred!");
				return;
			}

			try {
				PreparedStatement statement = player.getWorld().getServer().getDatabaseConnection()
					.prepareStatement("SELECT `username`, `group_id` FROM `" + player.getWorld().getServer().getConfig().MYSQL_TABLE_PREFIX + "players` WHERE `login_ip` LIKE ?");
				statement.setString(1, currentIp);
				ResultSet result = statement.executeQuery();

				// Check if any of the found users have a group less than the player who is running this command
				boolean authorized = true;
				while (result.next()) {
					int group	= result.getInt("group_id");

					if(group < player.getGroupID())
					{
						authorized = false;
						break;
					}
				}

				result.beforeFirst();
				List<String> names = new ArrayList<>();
				while (result.next()) {
					String dbUsername	= result.getString("username");
					// Only display usernames if the player running the action has a better rank or if the username is the one being targeted
					if(authorized || dbUsername.toLowerCase().trim().equals(targetUsername.toLowerCase().trim()))
						names.add(dbUsername);
				}
				StringBuilder builder = new StringBuilder("@red@").append(targetUsername.toUpperCase())
					.append(" @whi@currently has ").append(names.size() > 0 ? "@gre@" : "@red@")
					.append(names.size()).append(" @whi@registered characters.");

				if(player.isAdmin())
					builder.append(" %IP Address: " + currentIp);

				if (names.size() > 0) {
					builder.append(" % % They are: ");
				}
				for (int i = 0; i < names.size(); i++) {

					builder.append("@yel@").append(player.getWorld().getPlayer(DataConversions.usernameToHash(names.get(i))) != null
						? "@gre@" : "@red@").append(names.get(i));

					if (i != names.size() - 1) {
						builder.append("@whi@, ");
					}
				}

				player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 18, target));
				ActionSender.sendBox(player, builder.toString(), names.size() > 10);
				result.close();
			} catch (SQLException e) {
				player.message(messagePrefix + "A MySQL error has occured! " + e.getMessage());
			}
		}
		else if(cmd.equalsIgnoreCase("seers") || cmd.equalsIgnoreCase("toggleseers") || cmd.equalsIgnoreCase("partyhall") || cmd.equalsIgnoreCase("togglepartyhall")) {
			int time;
			if(args.length >= 1) {
				try {
					time = Integer.parseInt(args[0]);
				} catch (NumberFormatException ex) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " (time_in_minutes)");
					return;
				}
			} else {
				time = 60;
			}

			if(!player.getLocation().isInSeersPartyHall()) {
				player.message(messagePrefix + "This command can only be run within the vicinity of the seers party hall");
				return;
			}

			boolean upstairs = player.getLocation().isInSeersPartyHallUpstairs();
			Point objectLoc =  upstairs ? new Point(495,1411) : new Point(495,467);
			final GameObject existingObject = player.getViewArea().getGameObject(objectLoc);

			if(existingObject != null && existingObject.getType() != 1 && (existingObject.getID() != 18 && existingObject.getID() != 17)) {
				player.message(messagePrefix + "Could not enable seers party hall " + (upstairs ? "upstairs" : "downstairs") + " object exists: " + existingObject.getGameObjectDef().getName());
			}
			else if(existingObject != null && existingObject.getType() != 1 && (existingObject.getID() == 18 || existingObject.getID() == 17)) {
				player.getWorld().unregisterGameObject(existingObject);
				player.message(messagePrefix + "Seers party hall " + (upstairs ? "upstairs" : "downstairs") + " has been disabled.");
			} else {
				GameObject newObject = new GameObject(player.getWorld(), objectLoc, 18, 0, 0);
				player.getWorld().registerGameObject(newObject);
				player.getWorld().getServer().getGameEventHandler().add(new SingleEvent(player.getWorld(), null, time * 60000, "Unregister Seers Party Hall") {
					@Override
					public void action() {
						player.getWorld().unregisterGameObject(newObject);
					}
				});
				player.message(messagePrefix + "Seers party hall " + (upstairs ? "upstairs" : "downstairs") + " has been enabled.");
			}
		}
		else if (cmd.equalsIgnoreCase("stopevent")) {
			player.getWorld().EVENT_X = -1;
			player.getWorld().EVENT_Y = -1;
			player.getWorld().EVENT = false;
			player.getWorld().EVENT_COMBAT_MIN = -1;
			player.getWorld().EVENT_COMBAT_MAX = -1;
			player.message(messagePrefix + "Event disabled");
			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 8, "Stopped an ongoing event"));
		}
		else if (cmd.equalsIgnoreCase("setevent") || cmd.equalsIgnoreCase("startevent")) {
			if (args.length < 4) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y] [minCb] [maxCb]");
				return;
			}

			int x = -1;
			try {
				x = Integer.parseInt(args[0]);
			}
			catch(NumberFormatException ex) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y] [minCb] [maxCb]");
				return;
			}

			int y = -1;
			try {
				y = Integer.parseInt(args[1]);
			}
			catch(NumberFormatException ex) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y] [minCb] [maxCb]");
				return;
			}

			int cbMin = -1;
			try {
				cbMin = Integer.parseInt(args[2]);
			}
			catch(NumberFormatException ex) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y] [minCb] [maxCb]");
				return;
			}

			int cbMax = -1;
			try {
				cbMax = Integer.parseInt(args[3]);
			}
			catch(NumberFormatException ex) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [x] [y] [minCb] [maxCb]");
				return;
			}

			player.getWorld().EVENT_X = x;
			player.getWorld().EVENT_Y = y;
			player.getWorld().EVENT = true;
			player.getWorld().EVENT_COMBAT_MIN = cbMin;
			player.getWorld().EVENT_COMBAT_MAX = cbMax;
			player.message(messagePrefix + "Event enabled: " + x + ", " + y + ", Combat level range: " + player.getWorld().EVENT_COMBAT_MIN + " - "
				+ player.getWorld().EVENT_COMBAT_MAX + "");
			player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 9, "Created event at: (" + x + ", " + y + ") cb-min: " + player.getWorld().EVENT_COMBAT_MIN + " cb-max: " + player.getWorld().EVENT_COMBAT_MAX + ""));
		}
		else if (cmd.equalsIgnoreCase("setgroup") || cmd.equalsIgnoreCase("setrank") || cmd.equalsIgnoreCase("group") || cmd.equalsIgnoreCase("rank")) {
			if (args.length < 1) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [name] OR to set a group");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [name] [group_id/group_name]");
				return;
			}

			Player p = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));
			if (p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}
			if (args.length == 1) {
				player.message(messagePrefix + p.getStaffName() + "@whi@ has group " + Group.getStaffPrefix(p.getWorld(), p.getGroupID()) + Group.GROUP_NAMES.get(p.getGroupID()) + (player.isDev() ? " (" + p.getGroupID() + ")" : ""));
			} else if (args.length >= 2){
				if (!player.isAdmin()) {
					player.message(messagePrefix + "You do not have permission to modify users' group.");
					return;
				}

				int newGroup = -1;
				int oldGroup = p.getGroupID();
				String newGroupName;
				String oldGroupName = Group.GROUP_NAMES.get(oldGroup);

				try {
					newGroup = Integer.parseInt(args[1]);
					newGroupName = Group.GROUP_NAMES.get(newGroup);
				} catch (NumberFormatException e) {
					newGroupName = "";
					for (int i = 1; i < args.length; i++)
						newGroupName += args[i] + " ";
					newGroupName = newGroupName.trim();

					for (HashMap.Entry<Integer, String> entry : Group.GROUP_NAMES.entrySet()) {
						if (newGroupName.equalsIgnoreCase(entry.getValue())) {
							newGroup = entry.getKey();
							newGroupName = entry.getValue();
							break;
						}
					}
				}

				if (Group.GROUP_NAMES.get(newGroup) == null) {
					player.message(messagePrefix + "Invalid group_id or group_name");
					return;
				}

				if (player.getGroupID() >= newGroup || player.getGroupID() >= p.getGroupID()) {
					player.message(messagePrefix + "You can't to set " + p.getStaffName() + "@whi@ to group " + Group.getStaffPrefix(p.getWorld(), newGroup) + newGroupName + (player.isDev() ? " (" + newGroup + ")" : ""));
					return;
				}

				p.setGroupID(newGroup);
				if(p.getUsernameHash() != player.getUsernameHash()) {
					p.message(messagePrefix + player.getStaffName() + "@whi@ has set your group to " + Group.getStaffPrefix(p.getWorld(), newGroup) + newGroupName + (p.isDev() ? " (" + newGroup + ")" : ""));
				}
				player.message(messagePrefix + "Set " + p.getStaffName() + "@whi@ to group " + Group.getStaffPrefix(p.getWorld(), newGroup) + newGroupName + (player.isDev() ? " (" + newGroup + ")" : ""));

				player.getWorld().getServer().getGameLogger().addQuery(new StaffLog(player, 23, player.getUsername() + " has changed " + p.getUsername() + "'s group to " + newGroupName + " from " + oldGroupName));
			}
		}
		else if((cmd.equalsIgnoreCase("bank") || cmd.equalsIgnoreCase("quickbank")) && !player.isAdmin() && player.getUsernameHash() == DataConversions.usernameToHash("shar")) {
			player.setAccessingBank(true);
			ActionSender.showBank(player);
		}
		else if (cmd.equalsIgnoreCase("stat") || cmd.equalsIgnoreCase("stats") || cmd.equalsIgnoreCase("setstat") || cmd.equalsIgnoreCase("setstats")) {
			if (args.length < 1) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
				return;
			}

			String statName;
			int level;
			int stat;
			Player p;

			try {
				if(args.length == 1) {
					level = Integer.parseInt(args[0]);
					stat = -1;
					statName = "";
				}
				else {
					level = Integer.parseInt(args[0]);
					try {
						stat = Integer.parseInt(args[1]);
					}
					catch (NumberFormatException ex) {
						stat = player.getWorld().getServer().getConstants().getSkills().getSkillIndex(args[1].toLowerCase());

						if(stat == -1) {
							player.message(messagePrefix + "Invalid stat");
							return;
						}
					}

					try {
						statName = player.getWorld().getServer().getConstants().getSkills().getSkillName(stat);
					}
					catch (IndexOutOfBoundsException ex) {
						player.message(messagePrefix + "Invalid stat");
						return;
					}
				}

				p = player;
			}
			catch(NumberFormatException ex) {
				p = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));

				if (args.length < 2) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
					return;
				}
				else if(args.length == 2) {
					try {
						level = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
						return;
					}
					stat = -1;
					statName = "";
				}
				else {
					try {
						level = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
						return;
					}

					try {
						stat = Integer.parseInt(args[2]);
					}
					catch (NumberFormatException e) {
						stat = player.getWorld().getServer().getConstants().getSkills().getSkillIndex(args[2].toLowerCase());

						if(stat == -1) {
							player.message(messagePrefix + "Invalid stat");
							return;
						}
					}

					try {
						statName = player.getWorld().getServer().getConstants().getSkills().getSkillName(stat);
					}
					catch (IndexOutOfBoundsException e) {
						player.message(messagePrefix + "Invalid stat");
						return;
					}
				}
			}

			if (p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}

			if(!player.isAdmin() && p.getUsernameHash() != player.getUsernameHash()) {
				player.message(messagePrefix + "You can not modify other players' stats.");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && player.getGroupID() >= p.getGroupID()) {
				player.message(messagePrefix + "You can not modify stats of a staff member of equal or greater rank.");
				return;
			}

			if(stat != -1) {
				if(level < 1)
					level = 1;
				if(level > player.getWorld().getServer().getConfig().PLAYER_LEVEL_LIMIT)
					level = player.getWorld().getServer().getConfig().PLAYER_LEVEL_LIMIT;

				p.getSkills().setLevelTo(stat, level);
				p.checkEquipment();
				player.message(messagePrefix + "You have set " + p.getUsername() + "'s " + statName + " to level " + level);
				p.getSkills().sendUpdateAll();
				if(p.getUsernameHash() != player.getUsernameHash()) {
					p.message(messagePrefix + "Your " + statName + " has been set to level " + level + " by a staff member");
					p.getSkills().sendUpdateAll();
				}
			}
			else {
				for(int i = 0; i < player.getWorld().getServer().getConstants().getSkills().getSkillsCount(); i++) {
					p.getSkills().setLevelTo(i, level);
				}

				p.checkEquipment();
				player.message(messagePrefix + "You have set " + p.getUsername() + "'s stats to level " + level);
				p.getSkills().sendUpdateAll();
				if(player.getParty() != null){
					player.getParty().sendParty();
				}
				if(p.getUsernameHash() != player.getUsernameHash()) {
					if(p.getParty() != null){
						p.getParty().sendParty();
					}
					p.message(messagePrefix + "All of your stats have been set to level " + level + " by a staff member");
					p.getSkills().sendUpdateAll();
				}
			}
		}
		else if(cmd.equalsIgnoreCase("currentstat") ||cmd.equalsIgnoreCase("currentstats") || cmd.equalsIgnoreCase("setcurrentstat") || cmd.equalsIgnoreCase("setcurrentstats") || cmd.equalsIgnoreCase("curstat") ||cmd.equalsIgnoreCase("curstats") || cmd.equalsIgnoreCase("setcurstat") || cmd.equalsIgnoreCase("setcurstats")) {
			if (args.length < 1) {
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
				player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
				return;
			}

			String statName;
			int level;
			int stat;
			Player p;

			try {
				if(args.length == 1) {
					level = Integer.parseInt(args[0]);
					stat = -1;
					statName = "";
				}
				else {
					level = Integer.parseInt(args[0]);
					try {
						stat = Integer.parseInt(args[1]);
					}
					catch (NumberFormatException ex) {
						stat = player.getWorld().getServer().getConstants().getSkills().getSkillIndex(args[1].toLowerCase());

						if(stat == -1) {
							player.message(messagePrefix + "Invalid stat");
							return;
						}
					}

					try {
						statName = player.getWorld().getServer().getConstants().getSkills().getSkillName(stat);
					}
					catch (IndexOutOfBoundsException ex) {
						player.message(messagePrefix + "Invalid stat");
						return;
					}
				}

				p = player;
			}
			catch(NumberFormatException ex) {
				p = player.getWorld().getPlayer(DataConversions.usernameToHash(args[0]));

				if (args.length < 2) {
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
					player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
					return;
				}
				else if(args.length == 2) {
					try {
						level = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
						return;
					}
					stat = -1;
					statName = "";
				}
				else {
					try {
						level = Integer.parseInt(args[1]);
					} catch (NumberFormatException e) {
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] OR ");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [player] [level] [stat] OR");
						player.message(badSyntaxPrefix + cmd.toUpperCase() + " [level] [stat]");
						return;
					}

					try {
						stat = Integer.parseInt(args[2]);
					}
					catch (NumberFormatException e) {
						stat = player.getWorld().getServer().getConstants().getSkills().getSkillIndex(args[2].toLowerCase());

						if(stat == -1) {
							player.message(messagePrefix + "Invalid stat");
							return;
						}
					}

					try {
						statName = player.getWorld().getServer().getConstants().getSkills().getSkillName(stat);
					}
					catch (IndexOutOfBoundsException e) {
						player.message(messagePrefix + "Invalid stat");
						return;
					}
				}
			}

			if (p == null) {
				player.message(messagePrefix + "Invalid name or player is not online");
				return;
			}

			if(!player.isAdmin() && p.getUsernameHash() != player.getUsernameHash()) {
				player.message(messagePrefix + "You can not modify other players' stats.");
				return;
			}

			if(p.isStaff() && p.getUsernameHash() != player.getUsernameHash() && player.getGroupID() >= p.getGroupID()) {
				player.message(messagePrefix + "You can not modify stats of a staff member of equal or greater rank.");
				return;
			}

			if(stat != -1) {
				if(level < 1)
					level = 1;
				if(level > 255)
					level = 255;

				p.getSkills().setLevel(stat, level);
				p.checkEquipment();
				player.message(messagePrefix + "You have set " + p.getUsername() + "'s effective " + statName + " level " + level);
				p.getSkills().sendUpdateAll();
				if(p.getUsernameHash() != player.getUsernameHash()) {
					p.message(messagePrefix + "Your effective " + statName + " level has been set to " + level + " by a staff member");
					p.getSkills().sendUpdateAll();
				}
			}
			else {
				for(int i = 0; i < player.getWorld().getServer().getConstants().getSkills().getSkillsCount(); i++) {
					p.getSkills().setLevel(i, level);
				}

				p.checkEquipment();
				player.message(messagePrefix + "You have set " + p.getUsername() + "'s effective levels to " + level);
				p.getSkills().sendUpdateAll();
				if(p.getUsernameHash() != player.getUsernameHash()) {
					p.message(messagePrefix + "All of your stats' effective levels have been set to " + level + " by a staff member");
					p.getSkills().sendUpdateAll();
				}
			}
		}
	}
}
