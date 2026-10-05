package dev.livingrealms.sim.ui;

/** Strict tiny command envelope for server-validated dashboard actions. */
public record DashboardActionCommand(Action action, long targetId, long secondaryTargetId, String argument) {
    public enum Action {
        BOUNTY_ACCEPT, BOUNTY_ABANDON,
        CONFIG_PERFORMANCE, CONFIG_BALANCED, CONFIG_IMMERSIVE, CONFIG_CINEMATIC,
        FACTION_JOIN_LOCAL, FACTION_LEAVE,
        TAX_LOWER, TAX_RAISE,
        SETTLEMENT_BALANCED, SETTLEMENT_FOOD, SETTLEMENT_HOUSING, SETTLEMENT_INDUSTRY, SETTLEMENT_DEFENSE,
        /** targetId = settlement. argument = AUTO|HYBRID|PLAYER_LED */
        SET_DEVELOPMENT_MODE,
        /** targetId = settlement. argument = role name (HOUSE, TOWN_HALL, …). Survey runs server-side. */
        REGISTER_BUILDING,
        MARKET_BUY, MARKET_SELL,
        /** Influence unlocks — targetId is the faction id. */
        REQUEST_AUDIENCE, PROPOSE_PROJECT, REQUEST_MILITARY_SUPPORT, PETITION_TRADE, PETITION_CLERGY,
        /** Found a player realm at the actor position; argument is the settlement name. */
        FOUND_SETTLEMENT,
        /** Ruler escape hatch: abdicate and hand court presentation to a successor. targetId = member faction. */
        ABDICATE,
        /** Diplomacy: targetId = other faction. secondaryTargetId = optional war-goal settlement. */
        PETITION_PEACE, PROPOSE_TRADE_PACT, DECLARE_WAR,
        /** Army orders: targetId = army id. secondaryTargetId = settlement/shipment when required. */
        ARMY_DEFEND_HOME, ARMY_RALLY, ARMY_STAND_DOWN,
        ARMY_CAPTURE, ARMY_SIEGE, ARMY_RAID, ARMY_ESCORT, ARMY_PATROL,
        /** Crime mitigation: targetId = local faction id. */
        SURRENDER, PAY_FINE
    }

    public DashboardActionCommand(Action action, long targetId) {
        this(action, targetId, 0, "");
    }

    public DashboardActionCommand(Action action, long targetId, String argument) {
        this(action, targetId, 0, argument);
    }

    public DashboardActionCommand(Action action, long targetId, long secondaryTargetId) {
        this(action, targetId, secondaryTargetId, "");
    }

    public DashboardActionCommand {
        if (action == null) throw new IllegalArgumentException("action");
        if (targetId <= 0) throw new IllegalArgumentException("targetId");
        if (secondaryTargetId < 0) throw new IllegalArgumentException("secondaryTargetId");
        argument = argument == null ? "" : argument.strip();
        if (argument.length() > 40) argument = argument.substring(0, 40).strip();
        if (argument.indexOf(':') >= 0 || argument.indexOf('\n') >= 0 || argument.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("argument");
        }
    }

    public String encode() {
        if (secondaryTargetId <= 0 && argument.isBlank()) return action.name() + ":" + targetId;
        if (secondaryTargetId <= 0) return action.name() + ":" + targetId + ":" + argument;
        if (argument.isBlank()) return action.name() + ":" + targetId + ":" + secondaryTargetId;
        return action.name() + ":" + targetId + ":" + secondaryTargetId + ":" + argument;
    }

    public static DashboardActionCommand parse(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 120) throw new IllegalArgumentException("command");
        int first = encoded.indexOf(':');
        if (first <= 0 || first == encoded.length() - 1) throw new IllegalArgumentException("command format");
        Action action;
        try {
            action = Action.valueOf(encoded.substring(0, first));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown action", ex);
        }
        String rest = encoded.substring(first + 1);
        int second = rest.indexOf(':');
        if (second < 0) {
            return new DashboardActionCommand(action, parseId(rest, "targetId"), 0, "");
        }
        long targetId = parseId(rest.substring(0, second), "targetId");
        String after = rest.substring(second + 1);
        int third = after.indexOf(':');
        if (third < 0) {
            if (usesSecondary(action)) {
                return new DashboardActionCommand(action, targetId, parseId(after, "secondaryTargetId"), "");
            }
            return new DashboardActionCommand(action, targetId, 0, after);
        }
        long secondary = parseId(after.substring(0, third), "secondaryTargetId");
        String arg = after.substring(third + 1);
        return new DashboardActionCommand(action, targetId, secondary, arg);
    }

    private static boolean usesSecondary(Action action) {
        return switch (action) {
            case DECLARE_WAR, ARMY_CAPTURE, ARMY_SIEGE, ARMY_RAID, ARMY_ESCORT, ARMY_PATROL -> true;
            default -> false;
        };
    }

    private static long parseId(String raw, String label) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label, ex);
        }
    }
}
