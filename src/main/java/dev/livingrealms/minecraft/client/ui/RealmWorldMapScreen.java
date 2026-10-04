package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.sim.ui.RealmDashboardSnapshot;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Full-screen strategic world map opened with M. Painted like a compact Xaero-style local atlas:
 * player-centred zoom, elevation-shaded terrain from loaded chunks, ecology tint for unloaded land.
 */
public final class RealmWorldMapScreen extends Screen {
    /** Local view radius around the player in blocks (Xaero-style readable zoom). */
    private static final double LOCAL_RADIUS = 2_400.0;
    private RealmDashboardSnapshot snapshot;
    private double zoom = 1.0;
    private double panX;
    private double panZ;

    public RealmWorldMapScreen(RealmDashboardSnapshot snapshot){
        super(Component.literal("Living Realms World Map"));
        this.snapshot=snapshot;
    }

    @Override protected void init(){DashboardClientState.requestRefresh();}

    public void replaceSnapshot(RealmDashboardSnapshot snapshot){this.snapshot=snapshot;}

    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        LivingRealmsScreens.clearBackground(g,mouseX,mouseY,partialTick);
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        LivingRealmsScreens.paintBackdrop(g,width,height,0xFF05080B);
        int margin=18,legendWidth=Math.min(220,Math.max(155,width/5));
        int mapX=margin,mapY=34,mapW=Math.max(120,width-legendWidth-margin*3),mapH=Math.max(100,height-58);
        g.fill(mapX,mapY,mapX+mapW,mapY+mapH,0xFF081018);
        g.fill(mapX,mapY,mapX+mapW,mapY+1,0xFF52606E);g.fill(mapX,mapY+mapH-1,mapX+mapW,mapY+mapH,0xFF52606E);
        g.fill(mapX,mapY,mapX+1,mapY+mapH,0xFF52606E);g.fill(mapX+mapW-1,mapY,mapX+mapW,mapY+mapH,0xFF52606E);
        renderMap(g,snapshot,mapX,mapY,mapW,mapH);
        renderLegend(g,snapshot,mapX+mapW+12,mapY,legendWidth,mapH);
        g.drawString(font,"Living Realms World Map • M",margin,12,0xFFFFFFFF,false);
        g.drawString(font,"scroll zoom • drag pan • biomes • realms • settlements • trade • threats",margin+190,12,0xFFB8C0CA,false);
        super.render(g,mouseX,mouseY,partialTick);
    }

    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY){
        if(scrollY!=0){
            zoom=Math.max(0.35,Math.min(6.0,zoom*(scrollY>0?1.12:1/1.12)));
            return true;
        }
        return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
    }

    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double dragX,double dragY){
        if(button==0||button==1){
            var map=snapshot.map();
            double span=viewSpan(map);
            int mapW=Math.max(120,width-Math.min(220,Math.max(155,width/5))-54);
            int mapH=Math.max(100,height-58);
            panX-=dragX/Math.max(1,mapW)*span;
            panZ-=dragY/Math.max(1,mapH)*span;
            return true;
        }
        return super.mouseDragged(mouseX,mouseY,button,dragX,dragY);
    }

    private double viewSpan(RealmDashboardSnapshot.StrategicMapView map){
        return Math.max(512.0,LOCAL_RADIUS*2.0/zoom);
    }

    private double viewMinX(RealmDashboardSnapshot.StrategicMapView map){return map.playerX()+panX-viewSpan(map)*.5;}
    private double viewMaxX(RealmDashboardSnapshot.StrategicMapView map){return map.playerX()+panX+viewSpan(map)*.5;}
    private double viewMinZ(RealmDashboardSnapshot.StrategicMapView map){return map.playerZ()+panZ-viewSpan(map)*.5;}
    private double viewMaxZ(RealmDashboardSnapshot.StrategicMapView map){return map.playerZ()+panZ+viewSpan(map)*.5;}

    private void renderMap(GuiGraphics g,RealmDashboardSnapshot s,int x,int y,int w,int h){
        var map=s.map();
        double spanX=Math.max(1,viewMaxX(map)-viewMinX(map)),spanZ=Math.max(1,viewMaxZ(map)-viewMinZ(map));
        renderTerrainBase(g,s,x,y,w,h);
        for(var claim:map.claims())drawClaim(g,map,claim,x,y,w,h,spanX,spanZ,factionColor(claim.factionId(),0x60));
        for(var route:map.routes())drawLine(g,mapX(map,route.fromX(),x,w),mapY(map,route.fromZ(),y,h),mapX(map,route.toX(),x,w),mapY(map,route.toZ(),y,h),route.operational()?0xFFBDA66A:0xFF525861);
        for(var resource:map.resourceClaims()){int rx=mapX(map,resource.x(),x,w),rz=mapY(map,resource.z(),y,h);int c=resource.contestedByFactionId()>0?0xFFFF8A65:0xFF8BC34A;g.fill(rx-1,rz-1,rx+2,rz+2,c);}
        for(var shipment:map.shipments()){int sx=mapX(map,shipment.x(),x,w),sz=mapY(map,shipment.z(),y,h);g.fill(sx-1,sz-1,sx+2,sz+2,0xFF4DD0E1);}
        for(var migration:map.migrations()){int mx=mapX(map,migration.x(),x,w),mz=mapY(map,migration.z(),y,h);g.fill(mx-2,mz-1,mx+3,mz+2,0xFFE0E0E0);}
        for(var raid:map.raids()){int rx=mapX(map,raid.x(),x,w),rz=mapY(map,raid.z(),y,h);g.fill(rx-2,rz-2,rx+3,rz+3,raid.bandit()?0xFFD84315:0xFFE53935);}
        for(var front:map.fronts())drawLine(g,mapX(map,front.fromX(),x,w),mapY(map,front.fromZ(),y,h),mapX(map,front.toX(),x,w),mapY(map,front.toZ(),y,h),0xFFFF5E57);
        for(var settlement:map.settlements()){
            int sx=mapX(map,settlement.x(),x,w),sz=mapY(map,settlement.z(),y,h);if(sx<x||sx>=x+w||sz<y||sz>=y+h)continue;
            int size=switch(settlement.tier()){case "METROPOLIS"->5;case "CITY"->4;case "TOWN"->3;default->2;};
            g.fill(sx-size,sz-size,sx+size+1,sz+size+1,factionColor(settlement.factionId(),0xFF));
            // Declutter: only label towns+ or nearby settlements.
            double dist=Math.hypot(settlement.x()-map.playerX(),settlement.z()-map.playerZ());
            if(size>=3||dist<900)g.drawString(font,settlement.name(),sx+size+2,sz-4,0xFFE8EDF2,false);
        }
        for(var port:map.ports()){int px0=mapX(map,port.x(),x,w),pz0=mapY(map,port.z(),y,h);g.fill(px0-2,pz0-2,px0+3,pz0+3,port.operational()?0xFF42A5F5:0xFF607D8B);}
        for(var fleet:map.fleets()){int fx=mapX(map,fleet.x(),x,w),fz=mapY(map,fleet.z(),y,h);g.fill(fx-3,fz-1,fx+4,fz+2,0xFF26C6DA);}
        for(var air:map.airWings()){int ax0=mapX(map,air.x(),x,w),az0=mapY(map,air.z(),y,h);g.fill(ax0-2,az0,ax0+3,az0+1,0xFFB39DDB);g.fill(ax0,az0-2,ax0+1,az0+3,0xFFB39DDB);}
        for(var pirate:map.pirates()){int bx=mapX(map,pirate.x(),x,w),bz=mapY(map,pirate.z(),y,h);g.fill(bx-2,bz-2,bx+3,bz+3,0xFF8D6E63);}
        for(var epidemic:map.epidemics()){int ex=mapX(map,epidemic.x(),x,w),ez=mapY(map,epidemic.z(),y,h);g.fill(ex-3,ez-3,ex+4,ez-2,0xFFAB47BC);g.fill(ex-3,ez+2,ex+4,ez+3,0xFFAB47BC);}
        for(var cache:map.caches()){int cx=mapX(map,cache.x(),x,w),cz=mapY(map,cache.z(),y,h);g.fill(cx-1,cz-1,cx+2,cz+2,0xFFFFD54F);}
        for(var ruin:map.ruins()){int rx0=mapX(map,ruin.x(),x,w),rz0=mapY(map,ruin.z(),y,h);g.fill(rx0-2,rz0-1,rx0+3,rz0+2,ruin.looted()?0xFF5D4037:0xFF8D6E63);}
        for(var army:map.armies()){
            int ax=mapX(map,army.x(),x,w),az=mapY(map,army.z(),y,h);g.fill(ax-2,az-2,ax+3,az+3,0xFFFFC857);
        }
        int px=mapX(map,map.playerX(),x,w),pz=mapY(map,map.playerZ(),y,h);
        g.fill(px-5,pz,px+6,pz+1,0xFFFFFFFF);g.fill(px,pz-5,px+1,pz+6,0xFFFFFFFF);
    }

    private void renderTerrainBase(GuiGraphics g,RealmDashboardSnapshot s,int x,int y,int w,int h){
        var regions=s.ecology().regions();
        final int tile=4;
        var map=s.map();
        double minX=viewMinX(map),maxX=viewMaxX(map),minZ=viewMinZ(map),maxZ=viewMaxZ(map);
        for(int py=y;py<y+h;py+=tile){
            double wz=minZ+((py-y+tile*.5)/Math.max(1.0,h))*(maxZ-minZ);
            for(int px=x;px<x+w;px+=tile){
                double wx=minX+((px-x+tile*.5)/Math.max(1.0,w))*(maxX-minX);
                ClientTerrainMapCache.Sample surface=ClientTerrainMapCache.sample((int)Math.round(wx),(int)Math.round(wz));
                int color;
                if(surface!=null){
                    color=surface.color();
                }else if(regions.isEmpty()){
                    // Deterministic procedural relief so unloaded land still shows hills/valleys.
                    color=proceduralTerrain((int)Math.round(wx),(int)Math.round(wz));
                }else{
                    RealmDashboardSnapshot.RegionEcologyView nearest=null;double best=Double.POSITIVE_INFINITY;
                    for(var region:regions){double dx=region.x()-wx,dz=region.z()-wz,d=dx*dx+dz*dz;if(d<best){best=d;nearest=region;}}
                    color=nearest==null?proceduralTerrain((int)Math.round(wx),(int)Math.round(wz)):opaque(biomeColor(nearest.biome()));
                    if(nearest!=null){
                        double b=Math.max(0,Math.min(1,nearest.plantBiomass()/1200.0));
                        double relief=reliefNoise(wx,wz);
                        color=shade(color,.72+b*.16+relief*.18);
                    }
                }
                g.fill(px,py,Math.min(x+w,px+tile),Math.min(y+h,py+tile),color);
            }
        }
    }

    private static int proceduralTerrain(int x,int z){
        double n=reliefNoise(x,z);
        int base=n>.62?0xFF6E7468:n>.38?0xFF4F6B45:0xFF3A5A3E;
        if(n>.78)base=0xFF8A8E88;
        return shade(base,.85+n*.2);
    }

    private static double reliefNoise(double x,double z){
        // Cheap layered value noise — readable ridges without needing heightmaps for unloaded chunks.
        double n=Math.sin(x*0.0031+z*0.0027)*0.5+Math.sin(x*0.0011-z*0.0017)*0.3+Math.sin((x+z)*0.0007)*0.2;
        return Math.max(0,Math.min(1,(n+1)*.5));
    }

    private static int opaque(int argb){return 0xFF000000|(argb&0x00FFFFFF);}
    private static int shade(int argb,double factor){int r=(int)(((argb>>16)&255)*factor),gg=(int)(((argb>>8)&255)*factor),b=(int)((argb&255)*factor);return 0xFF000000|(Math.min(255,r)<<16)|(Math.min(255,gg)<<8)|Math.min(255,b);}

    private void renderLegend(GuiGraphics g,RealmDashboardSnapshot s,int x,int y,int w,int h){
        g.fill(x,y,x+w,y+h,0xD910151B);g.drawString(font,"Kingdoms",x+8,y+8,0xFFFFFFFF,false);
        int yy=y+23;Map<Long,String> names=new HashMap<>();for(var f:s.factions())names.put(f.id(),f.name());
        int shown=0;for(var f:s.factions()){
            if(yy>y+h-62)break;g.fill(x+8,yy+2,x+16,yy+10,factionColor(f.id(),0xFF));g.drawString(font,f.name(),x+21,yy+2,0xFFDDE5EC,false);yy+=13;shown++;
        }
        if(shown<s.factions().size()){g.drawString(font,"+"+(s.factions().size()-shown)+" more",x+8,yy+2,0xFF9DA8B3,false);yy+=14;}
        yy=Math.max(yy+8,y+h-52);g.drawString(font,"Terrain + elevation (Xaero-style zoom)",x+8,yy,0xFF9EC9A9,false);yy+=12;
        g.drawString(font,"Routes • trade • migration • resources",x+8,yy,0xFFBDA66A,false);yy+=12;
        g.drawString(font,"Red threats • blue ports • purple disease",x+8,yy,0xFFE0A0A0,false);yy+=12;
        g.drawString(font,"X "+Math.round(s.map().playerX())+"  Z "+Math.round(s.map().playerZ())+"  zoom "+String.format(Locale.ROOT,"%.1f",zoom),x+8,yy,0xFFFFFFFF,false);
    }

    private int mapX(RealmDashboardSnapshot.StrategicMapView map,double wx,int x,int w){return x+(int)Math.round((wx-viewMinX(map))/Math.max(1,viewMaxX(map)-viewMinX(map))*(w-1));}
    private int mapY(RealmDashboardSnapshot.StrategicMapView map,double wz,int y,int h){return y+(int)Math.round((wz-viewMinZ(map))/Math.max(1,viewMaxZ(map)-viewMinZ(map))*(h-1));}
    private static void drawLine(GuiGraphics g,int x0,int y0,int x1,int y1,int color){int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,err=dx+dy;for(;;){g.fill(x0,y0,x0+1,y0+1,color);if(x0==x1&&y0==y1)break;int e2=2*err;if(e2>=dy){err+=dy;x0+=sx;}if(e2<=dx){err+=dx;y0+=sy;}}}
    private void drawClaim(GuiGraphics g,RealmDashboardSnapshot.StrategicMapView map,RealmDashboardSnapshot.MapClaim c,int x,int y,int w,int h,double spanX,double spanZ,int color){int cx=mapX(map,c.x(),x,w),cy=mapY(map,c.z(),y,h);double rx=c.radius()/spanX*(w-1),ry=c.radius()/spanZ*(h-1);int px=0,py=0;for(int i=0;i<=24;i++){double a=Math.PI*2*i/24.0;int nx=cx+(int)Math.round(Math.cos(a)*rx),ny=cy+(int)Math.round(Math.sin(a)*ry);if(i>0)drawLine(g,px,py,nx,ny,color);px=nx;py=ny;}}
    private static int factionColor(long id,int alpha){int[] colors={0xD95C5C,0x5C8DD9,0x65B96E,0xC69A4B,0x9A6DD1,0x4CB6B0,0xD47AA5,0xA0A85A,0xD9784A,0x6C7FD1,0x6FB09B,0xB27A52};int rgb=colors[Math.floorMod(Long.hashCode(id),colors.length)];return (alpha<<24)|rgb;}
    private static int biomeColor(String biome){String b=biome==null?"":biome.toLowerCase(Locale.ROOT);if(b.contains("ocean")||b.contains("river")||b.contains("aquatic"))return 0xAA245E87;if(b.contains("desert")||b.contains("arid"))return 0xAA9B7C3E;if(b.contains("snow")||b.contains("ice")||b.contains("polar"))return 0xAAAFC8D6;if(b.contains("forest")||b.contains("jungle")||b.contains("wood"))return 0xAA2E6E45;if(b.contains("swamp")||b.contains("wet"))return 0xAA496B52;if(b.contains("mountain")||b.contains("alpine"))return 0xAA777C82;return 0xAA55735C;}
    @Override public boolean isPauseScreen(){return false;}
}
