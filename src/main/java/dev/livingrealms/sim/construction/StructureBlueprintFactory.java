package dev.livingrealms.sim.construction;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates compact vanilla-safe structure geometry. Visual palettes are resolved later by the
 * Minecraft adapter, so the same strategic blueprint can have different faction architecture.
 */
public final class StructureBlueprintFactory {
    private StructureBlueprintFactory() {}

    public static StructureBlueprint create(ConstructionIntent intent) {
        return switch (intent.role()) {
            case KEEP -> keep(intent.width(), intent.depth());
            case HOUSE -> house(intent);
            case FARM -> farm(intent.width(), intent.depth());
            case ROAD -> road(intent.width(), intent.depth());
            case MARKET -> market(intent);
            case PLAZA -> plaza(intent.width(), intent.depth());
            case WAREHOUSE -> warehouse(intent.width(), intent.depth());
            case WORKSHOP -> workshop(intent.width(), intent.depth());
            case BARRACKS -> barracks(intent.width(), intent.depth());
            case WALL -> wall(intent.width(), intent.depth());
            case FACTORY -> factory(intent.width(), intent.depth());
            case AIRFIELD -> airfield(intent.width(), intent.depth());
            case DOCK -> dock(intent.width(), intent.depth());
            case MINE -> mine(intent.width(), intent.depth());
            case LUMBER_CAMP -> lumberCamp(intent.width(), intent.depth());
            case FISHERY -> fishery(intent.width(), intent.depth());
            case TAVERN -> tavern(intent.width(), intent.depth());
            case TEMPLE -> temple(intent.width(), intent.depth());
            case CLINIC -> clinic(intent.width(), intent.depth());
            case SCHOOL -> school(intent.width(), intent.depth());
            case COURTHOUSE -> courthouse(intent.width(), intent.depth());
            case PRISON -> prison(intent.width(), intent.depth());
            case ORPHANAGE -> orphanage(intent.width(), intent.depth());
            case WELL -> well(intent.width(), intent.depth());
            case GATE -> gate(intent.width(), intent.depth());
            case MONUMENT -> monument(intent.width(), intent.depth());
            case OBSERVATORY -> observatory(intent.width(), intent.depth());
            case WIZARD_HALL -> wizardHall(intent.width(), intent.depth());
            case WIZARD_GROVE -> wizardGrove(intent.width(), intent.depth());
            case WIZARD_HOME -> wizardHome(intent.width(), intent.depth());
            case WIZARD_TUNNEL -> wizardTunnel(intent.width(), intent.depth());
            case IRRIGATION -> irrigation(intent.width(), intent.depth());
            case AQUEDUCT -> aqueduct(intent.width(), intent.depth());
            case MILL -> mill(intent.width(), intent.depth());
            case BAKERY -> bakery(intent.width(), intent.depth());
            case BREWERY -> brewery(intent.width(), intent.depth());
            case PASTURE -> pasture(intent.width(), intent.depth());
        };
    }

    private static StructureBlueprint mill(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
        for(int y=1;y<=4;y++)for(int z=-hz+1;z<=hz-1;z++)for(int x=-hx+1;x<=hx-1;x++){
            boolean edge=x==-hx+1||x==hx-1||z==-hz+1||z==hz-1;
            if(edge)add(p,x,y,z,PaletteSlot.WALL,y==4?ConstructionPhase.SHELL:ConstructionPhase.FRAME);
        }
        add(p,0,5,0,PaletteSlot.ROOF,ConstructionPhase.SHELL);
        add(p,0,6,0,PaletteSlot.ROOF,ConstructionPhase.DETAIL);
        add(p,1,5,0,PaletteSlot.ROOF,ConstructionPhase.DETAIL);add(p,-1,5,0,PaletteSlot.ROOF,ConstructionPhase.DETAIL);
        add(p,0,5,1,PaletteSlot.ROOF,ConstructionPhase.DETAIL);add(p,0,5,-1,PaletteSlot.ROOF,ConstructionPhase.DETAIL);
        return bp("mill",w,d,7,p);
    }
    private static StructureBlueprint bakery(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
        for(int y=1;y<=3;y++)for(int z=-hz+1;z<=hz-1;z++)for(int x=-hx+1;x<=hx-1;x++){
            boolean edge=x==-hx+1||x==hx-1||z==-hz+1||z==hz-1;
            if(edge)add(p,x,y,z,PaletteSlot.WALL,ConstructionPhase.FRAME);
            else if(y==1&&x==0&&z==0)add(p,x,y,z,PaletteSlot.MACHINE,ConstructionPhase.DETAIL);
        }
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,4,z,PaletteSlot.ROOF,ConstructionPhase.SHELL);
        return bp("bakery",w,d,5,p);
    }
    private static StructureBlueprint brewery(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
        for(int y=1;y<=3;y++)for(int z=-hz+1;z<=hz-1;z++)for(int x=-hx+1;x<=hx-1;x++){
            boolean edge=x==-hx+1||x==hx-1||z==-hz+1||z==hz-1;
            if(edge)add(p,x,y,z,PaletteSlot.WALL,ConstructionPhase.FRAME);
            else if(y==1&&Math.abs(x)+Math.abs(z)==1)add(p,x,y,z,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        }
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,4,z,PaletteSlot.ROOF,ConstructionPhase.SHELL);
        return bp("brewery",w,d,5,p);
    }
    private static StructureBlueprint pasture(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++){
            boolean edge=Math.abs(x)==hx||Math.abs(z)==hz;
            if(edge)add(p,x,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
            else add(p,x,0,z,PaletteSlot.FARMLAND,ConstructionPhase.FOUNDATION);
        }
        add(p,0,0,0,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("pasture",w,d,2,p);
    }

    private static StructureBlueprint irrigation(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++){
            if(Math.abs(x)<=1)add(p,x,0,z,PaletteSlot.WATER,ConstructionPhase.DETAIL);
            else if(Math.abs(x)==2)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
            else add(p,x,0,z,PaletteSlot.PATH,ConstructionPhase.FOUNDATION);
        }
        for(int z=-hz;z<=hz;z+=6){add(p,-hx,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);add(p,hx,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);}
        return bp("irrigation_canal",w,d,2,p);
    }

    private static StructureBlueprint aqueduct(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++){
            boolean pier=Math.floorMod(z+hz,6)==0;
            if(pier)for(int x=-hx;x<=hx;x+=Math.max(1,hx*2))for(int y=0;y<=5;y++)add(p,x,y,z,PaletteSlot.FOUNDATION,y==0?ConstructionPhase.FOUNDATION:ConstructionPhase.FRAME);
            for(int x=-hx;x<=hx;x++)add(p,x,6,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);
            for(int x=-1;x<=1;x++)add(p,x,7,z,PaletteSlot.WATER,ConstructionPhase.DETAIL);
            add(p,-2,7,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);add(p,2,7,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);
        }
        return bp("aqueduct",w,d,8,p);
    }

    private static StructureBlueprint house(ConstructionIntent intent) {
        int w=intent.width(),d=intent.depth();
        if(w>=11||d>=11)return apartmentBlock(w,d,variant(intent,3));
        int variant=variant(intent,4);
        return switch(variant){
            case 0 -> cottage(w,d);
            case 1 -> longhouse(w,d);
            case 2 -> townhouse(w,d);
            default -> porchHouse(w,d);
        };
    }

    /** Dense multi-storey housing used by towns/cities so growth is visible vertically as well as horizontally. */
    private static StructureBlueprint apartmentBlock(int w,int d,int variant){
        List<BlockPlacement> p=new ArrayList<>();int floors=variant==0?3:variant==1?4:5;int wallTop=floors*3+1;
        clear(p,w,d,wallTop+2);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);
        shell(p,w,d,1,wallTop,PaletteSlot.WALL);beamsAtCorners(p,w,d,1,wallTop+1);doorway(p,w,d);
        for(int level=0;level<floors;level++){
            int floorY=level*3;if(level>0){int hx=w/2-1,hz=d/2-1;for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,floorY,z,PaletteSlot.FLOOR,ConstructionPhase.FRAME);}
            windows(p,w,d,floorY+2);
            add(p,Math.max(-w/2+1,-2),floorY+1,Math.max(-d/2+1,1),PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        }
        flatRoof(p,w,d,wallTop+1,PaletteSlot.ROOF);
        int hx=w/2,hz=d/2;for(int x=-hx;x<=hx;x+=2){add(p,x,wallTop+2,-hz,PaletteSlot.FENCE,ConstructionPhase.DETAIL);add(p,x,wallTop+2,hz,PaletteSlot.FENCE,ConstructionPhase.DETAIL);}
        return bp("apartment_block_"+floors,w,d,wallTop+3,p);
    }

    private static StructureBlueprint cottage(int w,int d) {
        List<BlockPlacement> p = new ArrayList<>();
        clear(p,w,d,6); foundation(p,w,d); floor(p,w,d,PaletteSlot.FLOOR);
        shell(p,w,d,1,3,PaletteSlot.WALL); beamsAtCorners(p,w,d,1,4);
        doorway(p,w,d); windows(p,w,d,2); pitchedRoof(p,w,d,4);
        add(p,0,2,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("cottage",w,d,6,p);
    }

    private static StructureBlueprint longhouse(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();
        clear(p,w,d,7);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);
        shell(p,w,d,1,4,PaletteSlot.WALL);beamsAtCorners(p,w,d,1,5);doorway(p,w,d);windows(p,w,d,2);windows(p,w,d,3);pitchedRoof(p,w,d,5);
        int hx=w/2,hz=d/2;
        for(int x=-Math.max(1,hx-2);x<=Math.max(1,hx-2);x+=2)add(p,x,1,-hz-1,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
        add(p,Math.max(-hx+1,hx-1),2,Math.max(-hz+1,hz-1),PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        add(p,0,2,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("longhouse",w,d,7,p);
    }

    private static StructureBlueprint townhouse(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();
        clear(p,w,d,9);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);
        shell(p,w,d,1,6,PaletteSlot.WALL);beamsAtCorners(p,w,d,1,7);doorway(p,w,d);windows(p,w,d,2);windows(p,w,d,5);
        flatRoof(p,w,d,7,PaletteSlot.ROOF);
        int hx=w/2,hz=d/2;for(int x=-hx;x<=hx;x+=2){add(p,x,8,-hz,PaletteSlot.FENCE,ConstructionPhase.DETAIL);add(p,x,8,hz,PaletteSlot.FENCE,ConstructionPhase.DETAIL);}
        add(p,0,2,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("townhouse",w,d,9,p);
    }

    private static StructureBlueprint porchHouse(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();
        clear(p,w,d,7);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);
        shell(p,w,d,1,4,PaletteSlot.WALL);beamsAtCorners(p,w,d,1,5);doorway(p,w,d);windows(p,w,d,2);pitchedRoof(p,w,d,5);
        int hz=d/2;for(int x=-Math.min(2,w/2);x<=Math.min(2,w/2);x++)add(p,x,0,-hz-1,PaletteSlot.FLOOR,ConstructionPhase.DETAIL);
        add(p,-2,1,-hz-1,PaletteSlot.BEAM,ConstructionPhase.DETAIL);add(p,2,1,-hz-1,PaletteSlot.BEAM,ConstructionPhase.DETAIL);
        add(p,-2,2,-hz-1,PaletteSlot.BEAM,ConstructionPhase.DETAIL);add(p,2,2,-hz-1,PaletteSlot.BEAM,ConstructionPhase.DETAIL);
        add(p,0,2,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("porch_house",w,d,7,p);
    }

    private static StructureBlueprint keep(int w, int d) {
        if(w>=23||d>=21)return castle(w,d);
        List<BlockPlacement> p = new ArrayList<>();
        clear(p,w,d,10); foundation(p,w,d); floor(p,w,d,PaletteSlot.FOUNDATION);
        shell(p,w,d,1,6,PaletteSlot.FOUNDATION);
        doorway(p,w,d); windows(p,w,d,3); windows(p,w,d,5);
        int hx=w/2,hz=d/2;
        for(int y=1;y<=8;y++) {
            add(p,-hx,y,-hz,PaletteSlot.FOUNDATION,ConstructionPhase.FRAME);add(p,hx,y,-hz,PaletteSlot.FOUNDATION,ConstructionPhase.FRAME);
            add(p,-hx,y,hz,PaletteSlot.FOUNDATION,ConstructionPhase.FRAME);add(p,hx,y,hz,PaletteSlot.FOUNDATION,ConstructionPhase.FRAME);
        }
        flatRoof(p,w,d,7,PaletteSlot.FOUNDATION);battlements(p,w,d,8);
        add(p,0,3,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);add(p,-2,2,0,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);add(p,2,2,0,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("keep",w,d,9,p);
    }

    /** Proper capital castle: walled court, four towers, gatehouse and a raised great hall. */
    private static StructureBlueprint castle(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;clear(p,w,d,15);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);
        // Curtain wall and battlements.
        for(int y=1;y<=7;y++){for(int x=-hx;x<=hx;x++){add(p,x,y,-hz,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);add(p,x,y,hz,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);}for(int z=-hz+1;z<hz;z++){add(p,-hx,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);add(p,hx,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);}}
        battlements(p,w,d,8);
        // Four square corner towers rise above the curtain wall.
        int tr=3;for(int[] c:new int[][]{{-hx+tr,-hz+tr},{hx-tr,-hz+tr},{-hx+tr,hz-tr},{hx-tr,hz-tr}}){for(int y=1;y<=12;y++)for(int x=-tr;x<=tr;x++)for(int z=-tr;z<=tr;z++)if(Math.abs(x)==tr||Math.abs(z)==tr)add(p,c[0]+x,y,c[1]+z,PaletteSlot.FOUNDATION,ConstructionPhase.FRAME);for(int x=-tr;x<=tr;x++)for(int z=-tr;z<=tr;z++)add(p,c[0]+x,13,c[1]+z,PaletteSlot.ROOF,ConstructionPhase.SHELL);}
        // Gate through the south wall with a short internal avenue.
        for(int x=-2;x<=2;x++)for(int y=1;y<=4;y++)add(p,x,y,-hz,PaletteSlot.AIR,ConstructionPhase.CLEAR);
        for(int z=-hz;z<=-4;z++)for(int x=-2;x<=2;x++)add(p,x,0,z,PaletteSlot.PATH,ConstructionPhase.FOUNDATION);
        // Great hall in the inner court.
        int hallW=Math.max(11,w-12),hallD=Math.max(9,d-14),hhx=hallW/2,hhz=hallD/2;
        for(int y=1;y<=7;y++){for(int x=-hhx;x<=hhx;x++){add(p,x,y,-hhz,PaletteSlot.WALL,ConstructionPhase.SHELL);add(p,x,y,hhz,PaletteSlot.WALL,ConstructionPhase.SHELL);}for(int z=-hhz+1;z<hhz;z++){add(p,-hhx,y,z,PaletteSlot.WALL,ConstructionPhase.SHELL);add(p,hhx,y,z,PaletteSlot.WALL,ConstructionPhase.SHELL);}}
        for(int z=-hhz+1;z<hhz;z++)for(int x=-hhx+1;x<hhx;x++)add(p,x,0,z,PaletteSlot.FLOOR,ConstructionPhase.FRAME);
        for(int x=-2;x<=2;x++)for(int y=1;y<=3;y++)add(p,x,y,-hhz,PaletteSlot.AIR,ConstructionPhase.CLEAR);
        pitchedRoof(p,hallW,hallD,8);windows(p,hallW,hallD,3);windows(p,hallW,hallD,6);
        add(p,0,2,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);add(p,-3,1,2,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);add(p,3,1,2,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("capital_castle",w,d,15,p);
    }

    private static StructureBlueprint hall(String id,int w,int d,int wallHeight,boolean windows) {
        List<BlockPlacement> p=new ArrayList<>();
        clear(p,w,d,wallHeight+3); foundation(p,w,d); floor(p,w,d,PaletteSlot.FLOOR);
        shell(p,w,d,1,wallHeight,PaletteSlot.WALL); beamsAtCorners(p,w,d,1,wallHeight+1);
        doorway(p,w,d); if(windows){windows(p,w,d,2);windows(p,w,d,wallHeight-1);} pitchedRoof(p,w,d,wallHeight+1);
        add(p,0,2,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp(id,w,d,wallHeight+3,p);
    }

    private static StructureBlueprint market(ConstructionIntent intent) {
        int w=intent.width(),d=intent.depth();
        if(variant(intent,2)==0){
            List<BlockPlacement> p=new ArrayList<>(hall("market_hall",w,d,5,true).placements());
            add(p,0,2,Math.max(-1,d/2-2),PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
            return bp("market_hall",w,d,8,p);
        }
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,0,z,PaletteSlot.PATH,ConstructionPhase.FOUNDATION);
        for(int x=-hx+1;x<=hx-1;x+=3){add(p,x,1,-hz+1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);add(p,x,1,hz-1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);}
        for(int[] corner:new int[][]{{-hx+1,-hz+1},{hx-1,-hz+1},{-hx+1,hz-1},{hx-1,hz-1}}){for(int y=1;y<=4;y++)add(p,corner[0],y,corner[1],PaletteSlot.BEAM,ConstructionPhase.FRAME);}
        for(int x=-hx+1;x<=hx-1;x++) {add(p,x,4,-hz+1,PaletteSlot.ROOF,ConstructionPhase.SHELL);add(p,x,4,hz-1,PaletteSlot.ROOF,ConstructionPhase.SHELL);}
        add(p,0,1,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("open_market",w,d,5,p);
    }

    private static StructureBlueprint warehouse(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("warehouse",w,d,5,false).placements());
        int hx=Math.max(2,w/2-2),hz=Math.max(2,d/2-2);
        add(p,-hx,2,-hz,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        add(p,hx,2,-hz,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        add(p,-hx,2,hz,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        add(p,hx,2,hz,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("warehouse",w,d,8,p);
    }

    private static StructureBlueprint barracks(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("barracks",w,d,6,true).placements());
        add(p,-2,2,0,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        add(p,2,2,0,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("barracks",w,d,9,p);
    }

    private static StructureBlueprint workshop(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("workshop",w,d,5,true).placements());
        add(p,0,1,0,PaletteSlot.MACHINE,ConstructionPhase.DETAIL);
        add(p,1,1,0,PaletteSlot.METAL,ConstructionPhase.DETAIL);
        return bp("workshop",w,d,8,p);
    }

    private static StructureBlueprint factory(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();
        clear(p,w,d,9); foundation(p,w,d); floor(p,w,d,PaletteSlot.FOUNDATION);
        shell(p,w,d,1,6,PaletteSlot.WALL); doorway(p,w,d); windows(p,w,d,3); flatRoof(p,w,d,7,PaletteSlot.ROOF);
        for(int x=-w/2+2;x<=w/2-2;x+=4) add(p,x,1,0,PaletteSlot.MACHINE,ConstructionPhase.DETAIL);
        int sx=Math.max(-w/2+1,w/2-2),sz=Math.max(-d/2+1,d/2-2);
        for(int y=7;y<=10;y++) add(p,sx,y,sz,PaletteSlot.METAL,ConstructionPhase.DETAIL);
        return bp("factory",w,d,11,p);
    }

    private static StructureBlueprint farm(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++) for(int x=-hx;x<=hx;x++) {
            boolean edge=x==-hx||x==hx||z==-hz||z==hz;
            if(edge) add(p,x,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
            else if(x==0) add(p,x,0,z,PaletteSlot.WATER,ConstructionPhase.DETAIL);
            else {
                add(p,x,0,z,PaletteSlot.FARMLAND,ConstructionPhase.DETAIL);
                add(p,x,1,z,PaletteSlot.CROP,ConstructionPhase.DETAIL);
            }
        }
        add(p,-hx,1,0,PaletteSlot.AIR,ConstructionPhase.DETAIL);
        return bp("farm",w,d,2,p);
    }

    private static StructureBlueprint road(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        StreetType street=StreetType.forWidth(w,false,w>=7,false);
        // Carriageway with sidewalks; lamps/benches/clutter stay on sidewalk edges so the centerline stays clear.
        for(int z=-hz;z<=hz;z++) for(int x=-hx;x<=hx;x++) {
            boolean edge=Math.abs(x)>=Math.max(1,hx-(street.sidewalk()?0:1));
            PaletteSlot slot=edge?PaletteSlot.FOUNDATION:PaletteSlot.PATH;
            add(p,x,0,z,slot,ConstructionPhase.FOUNDATION);
        }
        if(w>=3 && street.sidewalk()){
            int lampStep=street.lighting()?6:10;
            for(int z=-hz+2;z<=hz-2;z+=lampStep){
                if(street.lighting()){
                    add(p,-hx,1,z,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
                    add(p,hx,1,z,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
                }
                // Benches / sign posts as fence slots on sidewalk.
                if(z+3<=hz-2){
                    add(p,-hx,1,z+3,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
                    add(p,hx,1,z+3,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
                }
                // Market/arterial clutter: barrels/crates as storage on wide streets only.
                if(w>=7 && z+5<=hz-2 && ((z/lampStep)&1)==0){
                    add(p,-hx,1,z+5,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
                    if(w>=9) add(p,hx,1,z+5,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
                }
            }
        }
        return bp("road_"+street.name().toLowerCase(java.util.Locale.ROOT),w,d,2,p);
    }

    /** Open civic plaza: paved square, corner lamps, edge seating, central well/fountain — keeps the center traversable. */
    private static StructureBlueprint plaza(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++){
            boolean rim=Math.abs(x)==hx||Math.abs(z)==hz;
            add(p,x,0,z,rim?PaletteSlot.FOUNDATION:PaletteSlot.PATH,ConstructionPhase.FOUNDATION);
        }
        for(int[] c:new int[][]{{-hx,-hz},{hx,-hz},{-hx,hz},{hx,hz}}){
            add(p,c[0],1,c[1],PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
            add(p,c[0],1,c[1]==0?1:c[1]+Integer.signum(-c[1]),PaletteSlot.FENCE,ConstructionPhase.DETAIL);
        }
        // Central well / fountain base and notice-board posts on the rim midpoints.
        add(p,0,0,0,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
        add(p,0,1,0,PaletteSlot.WATER,ConstructionPhase.DETAIL);
        add(p,0,2,0,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
        if(hx>=4){
            add(p,-hx,1,0,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
            add(p,hx,1,0,PaletteSlot.FENCE,ConstructionPhase.DETAIL);
            add(p,0,1,-hz,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
            add(p,0,1,hz,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        }
        return bp("civic_plaza",w,d,3,p);
    }

    private static StructureBlueprint wall(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++) for(int x=-hx;x<=hx;x++) for(int y=0;y<=4;y++)
            add(p,x,y,z,PaletteSlot.FOUNDATION,y==0?ConstructionPhase.FOUNDATION:ConstructionPhase.SHELL);
        for(int z=-hz;z<=hz;z+=2) for(int x=-hx;x<=hx;x+=2) add(p,x,5,z,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);
        return bp("wall",w,d,6,p);
    }

    private static StructureBlueprint airfield(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++) for(int x=-hx;x<=hx;x++) {
            PaletteSlot slot=Math.abs(x)<=3?PaletteSlot.RUNWAY:PaletteSlot.PATH;
            add(p,x,0,z,slot,ConstructionPhase.FOUNDATION);
        }
        for(int z=-hz+4;z<=hz-4;z+=8){add(p,-4,1,z,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);add(p,4,1,z,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);}
        return bp("airfield",w,d,2,p);
    }

    private static StructureBlueprint dock(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++) for(int x=-hx;x<=hx;x++) add(p,x,0,z,PaletteSlot.FLOOR,ConstructionPhase.FOUNDATION);
        for(int z=-hz;z<=hz;z+=4){add(p,-hx,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);add(p,hx,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);}
        return bp("dock",w,d,2,p);
    }




    private static StructureBlueprint wizardHall(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();clear(p,w,d,8);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);shell(p,w,d,1,6,PaletteSlot.WALL);
        for(int x=-w/2+2;x<=w/2-2;x+=4){add(p,x,1,0,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,x,4,0,PaletteSlot.REDSTONE_LIGHT,ConstructionPhase.DETAIL);}
        add(p,0,1,3,PaletteSlot.MACHINE,ConstructionPhase.DETAIL);add(p,0,2,-3,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);return bp("wizard_hall",w,d,9,p);
    }
    private static StructureBlueprint wizardGrove(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();clear(p,w,d,7);foundation(p,w,d);int hx=w/2,hz=d/2;
        for(int z=-hz+2;z<=hz-2;z++)for(int x=-hx+2;x<=hx-2;x++){if(x%4==0)add(p,x,0,z,PaletteSlot.WATER,ConstructionPhase.DETAIL);else{add(p,x,0,z,PaletteSlot.FARMLAND,ConstructionPhase.DETAIL);add(p,x,1,z,PaletteSlot.CROP,ConstructionPhase.DETAIL);}}
        for(int z=-hz+2;z<=hz-2;z+=5)for(int x=-hx+2;x<=hx-2;x+=5)add(p,x,4,z,PaletteSlot.REDSTONE_LIGHT,ConstructionPhase.DETAIL);
        for(int x=-hx+1;x<=hx-1;x++)add(p,x,0,0,PaletteSlot.PATH,ConstructionPhase.FOUNDATION);return bp("redstone_grow_chamber",w,d,8,p);
    }
    private static StructureBlueprint wizardHome(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();clear(p,w,d,6);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);shell(p,w,d,1,4,PaletteSlot.WALL);doorway(p,w,d);
        add(p,0,3,0,PaletteSlot.REDSTONE_LIGHT,ConstructionPhase.DETAIL);add(p,2,1,2,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);return bp("wizard_home",w,d,6,p);
    }
    private static StructureBlueprint wizardTunnel(int w,int d){
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++){add(p,x,0,z,PaletteSlot.PATH,ConstructionPhase.FOUNDATION);for(int y=1;y<=4;y++)add(p,x,y,z,PaletteSlot.AIR,ConstructionPhase.CLEAR);}
        for(int z=-hz+2;z<=hz-2;z+=7){add(p,-hx+1,3,z,PaletteSlot.REDSTONE_LIGHT,ConstructionPhase.DETAIL);add(p,hx-1,3,z,PaletteSlot.REDSTONE_LIGHT,ConstructionPhase.DETAIL);}return bp("wizard_tunnel",w,d,5,p);
    }

    private static StructureBlueprint tavern(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("tavern",w,d,5,true).placements());
        add(p,-2,2,1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);add(p,2,2,1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        for(int x=-2;x<=2;x++)add(p,x,1,-1,PaletteSlot.FLOOR,ConstructionPhase.DETAIL);
        return bp("tavern",w,d,8,p);
    }

    private static StructureBlueprint temple(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("temple",w,d,7,true).placements());
        int hz=d/2;for(int y=1;y<=9;y++)add(p,0,y,hz-1,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);
        for(int x=-2;x<=2;x++)add(p,x,1,Math.max(-1,hz-3),PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);
        add(p,0,3,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("temple",w,d,10,p);
    }

    private static StructureBlueprint clinic(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("clinic",w,d,5,true).placements());
        add(p,-2,1,1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);add(p,2,1,1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("clinic",w,d,8,p);
    }

    private static StructureBlueprint school(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("school",w,d,5,true).placements());
        for(int z=-2;z<=2;z+=2)for(int x=-3;x<=3;x+=3)add(p,x,1,z,PaletteSlot.FLOOR,ConstructionPhase.DETAIL);
        return bp("school",w,d,8,p);
    }

    private static StructureBlueprint courthouse(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("courthouse",w,d,6,true).placements());
        int hz=d/2;for(int x=-3;x<=3;x++)add(p,x,1,hz-2,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);
        add(p,0,2,hz-2,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);
        return bp("courthouse",w,d,9,p);
    }

    private static StructureBlueprint prison(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();clear(p,w,d,7);foundation(p,w,d);floor(p,w,d,PaletteSlot.FOUNDATION);shell(p,w,d,1,5,PaletteSlot.FOUNDATION);doorway(p,w,d);
        int hx=w/2,hz=d/2;for(int z=-hz+2;z<=hz-2;z+=3){for(int y=1;y<=4;y++){add(p,-1,y,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);add(p,1,y,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);}}
        flatRoof(p,w,d,6,PaletteSlot.FOUNDATION);return bp("prison",w,d,7,p);
    }

    private static StructureBlueprint orphanage(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(hall("orphanage",w,d,5,true).placements());
        add(p,-3,1,1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);add(p,3,1,1,PaletteSlot.STORAGE,ConstructionPhase.DETAIL);
        return bp("orphanage",w,d,8,p);
    }

    private static StructureBlueprint well(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)if(Math.abs(x)==hx||Math.abs(z)==hz)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
        add(p,0,0,0,PaletteSlot.WATER,ConstructionPhase.DETAIL);for(int y=1;y<=3;y++){add(p,-hx,y,0,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,hx,y,0,PaletteSlot.BEAM,ConstructionPhase.FRAME);}
        for(int x=-hx;x<=hx;x++)add(p,x,4,0,PaletteSlot.ROOF,ConstructionPhase.SHELL);return bp("well",w,d,5,p);
    }

    private static StructureBlueprint gate(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();int hx=w/2,hz=d/2;
        for(int x=-hx;x<=hx;x++)for(int z=-hz;z<=hz;z++)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);
        for(int y=1;y<=7;y++)for(int z=-hz;z<=hz;z++){for(int x=-hx;x<=-2;x++)add(p,x,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);for(int x=2;x<=hx;x++)add(p,x,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.SHELL);}
        for(int x=-1;x<=1;x++)for(int z=-hz;z<=hz;z++)for(int y=1;y<=4;y++)add(p,x,y,z,PaletteSlot.AIR,ConstructionPhase.CLEAR);
        for(int x=-1;x<=1;x++)for(int z=-hz;z<=hz;z++)add(p,x,5,z,PaletteSlot.BEAM,ConstructionPhase.FRAME);
        battlements(p,w,d,8);return bp("city_gate",w,d,9,p);
    }

    private static StructureBlueprint monument(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();foundation(p,w,d);for(int y=1;y<=8;y++){int r=y<3?2:1;for(int z=-r;z<=r;z++)for(int x=-r;x<=r;x++)add(p,x,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);}
        add(p,0,9,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);return bp("monument",w,d,10,p);
    }

    private static StructureBlueprint observatory(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>();clear(p,w,d,9);foundation(p,w,d);floor(p,w,d,PaletteSlot.FLOOR);shell(p,w,d,1,5,PaletteSlot.WALL);doorway(p,w,d);windows(p,w,d,3);flatRoof(p,w,d,6,PaletteSlot.ROOF);
        int r=Math.min(w,d)/4;for(int y=7;y<=9;y++)for(int z=-r;z<=r;z++)for(int x=-r;x<=r;x++)if(Math.abs(x)==r||Math.abs(z)==r)add(p,x,y,z,PaletteSlot.GLASS,ConstructionPhase.DETAIL);
        add(p,0,7,0,PaletteSlot.METAL,ConstructionPhase.DETAIL);add(p,0,8,0,PaletteSlot.LIGHT,ConstructionPhase.DETAIL);return bp("observatory",w,d,10,p);
    }

    private static StructureBlueprint mine(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        clear(p,w,d,6); foundation(p,w,d);
        for(int x=-hx;x<=hx;x++) for(int z=-hz;z<=hz;z++) if(Math.abs(x)>=hx-1||Math.abs(z)>=hz-1) add(p,x,1,z,PaletteSlot.FOUNDATION,ConstructionPhase.FRAME);
        for(int z=-2;z<=2;z++) for(int y=1;y<=4;y++){add(p,-2,y,z,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,2,y,z,PaletteSlot.BEAM,ConstructionPhase.FRAME);}
        for(int z=-hz;z<=hz;z++) add(p,0,0,z,PaletteSlot.PATH,ConstructionPhase.FOUNDATION);
        add(p,0,1,hz-1,PaletteSlot.MACHINE,ConstructionPhase.DETAIL); add(p,1,1,hz-1,PaletteSlot.METAL,ConstructionPhase.DETAIL);
        return bp("mine",w,d,7,p);
    }

    private static StructureBlueprint lumberCamp(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        clear(p,w,d,6); foundation(p,w,d); floor(p,Math.max(5,w-4),Math.max(5,d-4),PaletteSlot.FLOOR);
        for(int x=-hx+1;x<=hx-1;x+=3) for(int z=-hz+1;z<=hz-1;z+=4){add(p,x,1,z,PaletteSlot.BEAM,ConstructionPhase.DETAIL);add(p,x,2,z,PaletteSlot.BEAM,ConstructionPhase.DETAIL);}
        add(p,0,1,0,PaletteSlot.MACHINE,ConstructionPhase.DETAIL);
        return bp("lumber_camp",w,d,7,p);
    }

    private static StructureBlueprint fishery(int w,int d) {
        List<BlockPlacement> p=new ArrayList<>(); int hx=w/2,hz=d/2;
        for(int z=-hz;z<=hz;z++) for(int x=-hx;x<=hx;x++){if(Math.abs(x)<=2)add(p,x,0,z,PaletteSlot.FLOOR,ConstructionPhase.FOUNDATION);if(Math.abs(x)==2&&z%3==0)add(p,x,1,z,PaletteSlot.FENCE,ConstructionPhase.DETAIL);}
        for(int x=-2;x<=2;x++) for(int y=1;y<=3;y++){add(p,x,y,-hz,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,x,y,-hz+4,PaletteSlot.BEAM,ConstructionPhase.FRAME);}
        add(p,0,1,-hz+2,PaletteSlot.MACHINE,ConstructionPhase.DETAIL);
        return bp("fishery",w,d,5,p);
    }

    private static void clear(List<BlockPlacement> p,int w,int d,int h){int hx=w/2,hz=d/2;for(int y=1;y<=h;y++)for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,y,z,PaletteSlot.AIR,ConstructionPhase.CLEAR);}
    private static void foundation(List<BlockPlacement> p,int w,int d){int hx=w/2,hz=d/2;for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,0,z,PaletteSlot.FOUNDATION,ConstructionPhase.FOUNDATION);}
    private static void floor(List<BlockPlacement> p,int w,int d,PaletteSlot s){int hx=w/2-1,hz=d/2-1;for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,0,z,s,ConstructionPhase.FRAME);}
    private static void shell(List<BlockPlacement> p,int w,int d,int y0,int y1,PaletteSlot s){int hx=w/2,hz=d/2;for(int y=y0;y<=y1;y++){for(int x=-hx;x<=hx;x++){add(p,x,y,-hz,s,ConstructionPhase.SHELL);add(p,x,y,hz,s,ConstructionPhase.SHELL);}for(int z=-hz+1;z<hz;z++){add(p,-hx,y,z,s,ConstructionPhase.SHELL);add(p,hx,y,z,s,ConstructionPhase.SHELL);}}}
    private static void beamsAtCorners(List<BlockPlacement> p,int w,int d,int y0,int y1){int hx=w/2,hz=d/2;for(int y=y0;y<=y1;y++){add(p,-hx,y,-hz,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,hx,y,-hz,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,-hx,y,hz,PaletteSlot.BEAM,ConstructionPhase.FRAME);add(p,hx,y,hz,PaletteSlot.BEAM,ConstructionPhase.FRAME);}}
    private static void doorway(List<BlockPlacement> p,int w,int d){int hz=d/2;add(p,0,1,-hz,PaletteSlot.DOOR,ConstructionPhase.DETAIL);add(p,0,2,-hz,PaletteSlot.DOOR,ConstructionPhase.DETAIL);}
    private static void windows(List<BlockPlacement> p,int w,int d,int y){int hx=w/2,hz=d/2;if(w>=7){add(p,-2,y,-hz,PaletteSlot.GLASS,ConstructionPhase.DETAIL);add(p,2,y,-hz,PaletteSlot.GLASS,ConstructionPhase.DETAIL);add(p,-2,y,hz,PaletteSlot.GLASS,ConstructionPhase.DETAIL);add(p,2,y,hz,PaletteSlot.GLASS,ConstructionPhase.DETAIL);}if(d>=7){add(p,-hx,y,-2,PaletteSlot.GLASS,ConstructionPhase.DETAIL);add(p,-hx,y,2,PaletteSlot.GLASS,ConstructionPhase.DETAIL);add(p,hx,y,-2,PaletteSlot.GLASS,ConstructionPhase.DETAIL);add(p,hx,y,2,PaletteSlot.GLASS,ConstructionPhase.DETAIL);}}
    private static void pitchedRoof(List<BlockPlacement> p,int w,int d,int baseY){int hx=w/2,hz=d/2;int layers=Math.max(1,Math.min(hx,hz));for(int layer=0;layer<=layers;layer++){int x0=-hx+layer,x1=hx-layer,z0=-hz+layer,z1=hz-layer;if(x0>x1||z0>z1)break;for(int x=x0;x<=x1;x++){add(p,x,baseY+layer,z0,PaletteSlot.ROOF,ConstructionPhase.SHELL);add(p,x,baseY+layer,z1,PaletteSlot.ROOF,ConstructionPhase.SHELL);}for(int z=z0+1;z<z1;z++){add(p,x0,baseY+layer,z,PaletteSlot.ROOF,ConstructionPhase.SHELL);add(p,x1,baseY+layer,z,PaletteSlot.ROOF,ConstructionPhase.SHELL);}}}
    private static void flatRoof(List<BlockPlacement> p,int w,int d,int y,PaletteSlot s){int hx=w/2,hz=d/2;for(int z=-hz;z<=hz;z++)for(int x=-hx;x<=hx;x++)add(p,x,y,z,s,ConstructionPhase.SHELL);}
    private static void battlements(List<BlockPlacement> p,int w,int d,int y){int hx=w/2,hz=d/2;for(int x=-hx;x<=hx;x+=2){add(p,x,y,-hz,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);add(p,x,y,hz,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);}for(int z=-hz+2;z<hz;z+=2){add(p,-hx,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);add(p,hx,y,z,PaletteSlot.FOUNDATION,ConstructionPhase.DETAIL);}}

    private static int variant(ConstructionIntent intent,int bound){
        long z=intent.settlementId()*0x9E3779B97F4A7C15L ^ intent.key().hashCode()*0xD1B54A32D192ED03L;
        z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;z=(z^(z>>>27))*0x94D049BB133111EBL;z^=z>>>31;
        return Math.floorMod((int)z,bound);
    }
    private static void add(List<BlockPlacement> p,int x,int y,int z,PaletteSlot s,ConstructionPhase phase){p.add(new BlockPlacement(x,y,z,s,phase));}
    private static StructureBlueprint bp(String id,int w,int d,int h,List<BlockPlacement> p){return new StructureBlueprint(id,w,d,h,p);}
}
