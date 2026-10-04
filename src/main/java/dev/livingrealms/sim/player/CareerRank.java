package dev.livingrealms.sim.player;

import java.util.Objects;

/** Named ranks and service thresholds for each {@link CareerTrack}. */
public final class CareerRank {
    private final CareerTrack track; private final String title; private final double serviceRequired;
    private CareerRank(CareerTrack track,String title,double serviceRequired){
        if(track==null||title==null||title.isBlank()||serviceRequired<0||!Double.isFinite(serviceRequired))throw new IllegalArgumentException("career rank");
        this.track=track;this.title=title;this.serviceRequired=serviceRequired;
    }
    public CareerTrack track(){return track;} public String title(){return title;} public double serviceRequired(){return serviceRequired;}

    public static final CareerRank[] MILITARY={
            r(CareerTrack.MILITARY,"Recruit",0),r(CareerTrack.MILITARY,"Private",40),r(CareerTrack.MILITARY,"Corporal",120),
            r(CareerTrack.MILITARY,"Sergeant",280),r(CareerTrack.MILITARY,"Lieutenant",550),r(CareerTrack.MILITARY,"Captain",1000),
            r(CareerTrack.MILITARY,"Colonel",1800),r(CareerTrack.MILITARY,"General",3200),r(CareerTrack.MILITARY,"Marshal",5000)
    };
    public static final CareerRank[] POLITICAL={
            r(CareerTrack.POLITICAL,"Citizen",0),r(CareerTrack.POLITICAL,"Clerk",50),r(CareerTrack.POLITICAL,"Magistrate",180),
            r(CareerTrack.POLITICAL,"Councilor",450),r(CareerTrack.POLITICAL,"Minister",900),r(CareerTrack.POLITICAL,"Chancellor",2200)
    };
    public static final CareerRank[] ECONOMIC={
            r(CareerTrack.ECONOMIC,"Trader",0),r(CareerTrack.ECONOMIC,"Factor",60),r(CareerTrack.ECONOMIC,"Guildsman",200),
            r(CareerTrack.ECONOMIC,"MerchantLord",500),r(CareerTrack.ECONOMIC,"Tycoon",1100),r(CareerTrack.ECONOMIC,"Magnate",2500)
    };
    public static final CareerRank[] RELIGIOUS={
            r(CareerTrack.RELIGIOUS,"Follower",0),r(CareerTrack.RELIGIOUS,"Acolyte",45),r(CareerTrack.RELIGIOUS,"Deacon",150),
            r(CareerTrack.RELIGIOUS,"Priest",400),r(CareerTrack.RELIGIOUS,"Bishop",900),r(CareerTrack.RELIGIOUS,"HighClergy",2000)
    };

    public static CareerRank[] ranks(CareerTrack track){
        return switch(Objects.requireNonNull(track,"track")){
            case MILITARY -> MILITARY; case POLITICAL -> POLITICAL; case ECONOMIC -> ECONOMIC; case RELIGIOUS -> RELIGIOUS;
        };
    }
    public static CareerRank at(CareerTrack track,int index){
        CareerRank[] all=ranks(track);if(index<0||index>=all.length)throw new IllegalArgumentException("career rank index");return all[index];
    }
    public static int resolveIndex(CareerTrack track,double service){
        if(!Double.isFinite(service)||service<0)throw new IllegalArgumentException("career service");
        CareerRank[] all=ranks(track);int idx=0;for(int i=0;i<all.length;i++)if(service>=all[i].serviceRequired())idx=i;return idx;
    }
    private static CareerRank r(CareerTrack t,String title,double svc){return new CareerRank(t,title,svc);}
}
