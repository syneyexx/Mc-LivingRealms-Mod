package dev.livingrealms.sim.ui;

/** Strict tiny command envelope for server-validated dashboard actions. */
public record DashboardActionCommand(Action action,long targetId) {
    public enum Action { BOUNTY_ACCEPT, BOUNTY_ABANDON, CONFIG_PERFORMANCE, CONFIG_BALANCED, CONFIG_IMMERSIVE, CONFIG_CINEMATIC, FACTION_JOIN_LOCAL, FACTION_LEAVE, TAX_LOWER, TAX_RAISE, SETTLEMENT_BALANCED, SETTLEMENT_FOOD, SETTLEMENT_HOUSING, SETTLEMENT_INDUSTRY, SETTLEMENT_DEFENSE, MARKET_BUY, MARKET_SELL }

    public DashboardActionCommand {
        if(action==null) throw new IllegalArgumentException("action");
        if(targetId<=0) throw new IllegalArgumentException("targetId");
    }

    public String encode(){return action.name()+":"+targetId;}

    public static DashboardActionCommand parse(String encoded){
        if(encoded==null||encoded.isBlank()||encoded.length()>64) throw new IllegalArgumentException("command");
        int colon=encoded.indexOf(':');
        if(colon<=0||colon!=encoded.lastIndexOf(':')||colon==encoded.length()-1) throw new IllegalArgumentException("command format");
        Action action;
        try{action=Action.valueOf(encoded.substring(0,colon));}catch(IllegalArgumentException ex){throw new IllegalArgumentException("unknown action",ex);}
        long id;
        try{id=Long.parseLong(encoded.substring(colon+1));}catch(NumberFormatException ex){throw new IllegalArgumentException("targetId",ex);}
        return new DashboardActionCommand(action,id);
    }
}
