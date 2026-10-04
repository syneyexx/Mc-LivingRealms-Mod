package dev.livingrealms.sim.ui;

/** Strict tiny command envelope for server-validated dashboard actions. */
public record DashboardActionCommand(Action action, long targetId, String argument) {
    public enum Action {
        BOUNTY_ACCEPT, BOUNTY_ABANDON,
        CONFIG_PERFORMANCE, CONFIG_BALANCED, CONFIG_IMMERSIVE, CONFIG_CINEMATIC,
        FACTION_JOIN_LOCAL, FACTION_LEAVE,
        TAX_LOWER, TAX_RAISE,
        SETTLEMENT_BALANCED, SETTLEMENT_FOOD, SETTLEMENT_HOUSING, SETTLEMENT_INDUSTRY, SETTLEMENT_DEFENSE,
        MARKET_BUY, MARKET_SELL,
        /** Influence unlocks — targetId is the faction id. */
        REQUEST_AUDIENCE, PROPOSE_PROJECT, REQUEST_MILITARY_SUPPORT, PETITION_TRADE, PETITION_CLERGY,
        /** Found a player realm at the actor position; argument is the settlement name. */
        FOUND_SETTLEMENT
    }

    public DashboardActionCommand(Action action, long targetId) {
        this(action, targetId, "");
    }

    public DashboardActionCommand {
        if (action == null) throw new IllegalArgumentException("action");
        if (targetId <= 0) throw new IllegalArgumentException("targetId");
        argument = argument == null ? "" : argument.strip();
        if (argument.length() > 40) argument = argument.substring(0, 40).strip();
        if (argument.indexOf(':') >= 0 || argument.indexOf('\n') >= 0 || argument.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("argument");
        }
    }

    public String encode() {
        if (argument.isBlank()) return action.name() + ":" + targetId;
        return action.name() + ":" + targetId + ":" + argument;
    }

    public static DashboardActionCommand parse(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > 96) throw new IllegalArgumentException("command");
        int first = encoded.indexOf(':');
        if (first <= 0 || first == encoded.length() - 1) throw new IllegalArgumentException("command format");
        Action action;
        try {
            action = Action.valueOf(encoded.substring(0, first));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown action", ex);
        }
        int second = encoded.indexOf(':', first + 1);
        String idPart = second < 0 ? encoded.substring(first + 1) : encoded.substring(first + 1, second);
        String arg = second < 0 ? "" : encoded.substring(second + 1);
        long id;
        try {
            id = Long.parseLong(idPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("targetId", ex);
        }
        return new DashboardActionCommand(action, id, arg);
    }
}
