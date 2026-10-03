package dev.livingrealms.sim.civilization;

/** Derived calendar over canonical Minecraft/LivingRealms days; no duplicate time authority. */
public final class CivilizationCalendar {
    public enum Season { SPRING, SUMMER, AUTUMN, WINTER }
    private CivilizationCalendar(){}
    public static int year(long day){return (int)Math.floorDiv(Math.max(0,day),360)+1;} public static int dayOfYear(long day){return Math.floorMod((int)Math.min(Integer.MAX_VALUE,Math.max(0,day)),360);} public static Season season(long day){return Season.values()[Math.min(3,dayOfYear(day)/90)];} public static int dayOfSeason(long day){return dayOfYear(day)%90+1;} public static String dateLabel(long day){return season(day).name().toLowerCase()+" "+dayOfSeason(day)+", year "+year(day);}
}
