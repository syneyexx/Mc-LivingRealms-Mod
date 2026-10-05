package dev.livingrealms.sim.transport;

import dev.livingrealms.sim.world.SimPosition;
import java.util.*;

/** Pure, deterministic route corridor planner. Minecraft adapters decide terrain height and blocks. */
public final class RouteProjectionPlanner {
    public record RoutePoint(int x,int z,int dx,int dz,TransportMode mode,long routeId){
        public RoutePoint { Objects.requireNonNull(mode,"mode"); if(routeId<=0)throw new IllegalArgumentException("routeId"); }
    }
    private RouteProjectionPlanner(){}

    public static List<RoutePoint> plan(TransportRoute route,SimPosition from,SimPosition to,Collection<SimPosition> observers,double radius,int maxPoints){
        return plan(route,from,to,observers,radius,maxPoints,null);
    }

    /**
     * @param terrain when non-null, regional roads follow a bounded terrain-cost corridor instead of a pure geometric line
     */
    public static List<RoutePoint> plan(TransportRoute route,SimPosition from,SimPosition to,Collection<SimPosition> observers,double radius,int maxPoints,TerrainCorridorPlanner.TerrainSample terrain){
        Objects.requireNonNull(route,"route");Objects.requireNonNull(from,"from");Objects.requireNonNull(to,"to");Objects.requireNonNull(observers,"observers");
        if(radius<=0||maxPoints<0)throw new IllegalArgumentException("budget");if(maxPoints==0||observers.isEmpty()||!route.operational())return List.of();
        double distance=from.distanceTo(to);if(distance<1)return List.of();
        List<TerrainCorridorPlanner.Cell> corridor=corridor(route,from,to,terrain);
        double radius2=radius*radius;
        LinkedHashMap<Long,RoutePoint> out=new LinkedHashMap<>();
        int previousX=(int)Math.round(from.x()),previousZ=(int)Math.round(from.z());
        for(int i=0;i<corridor.size()&&out.size()<maxPoints;i++){
            TerrainCorridorPlanner.Cell cell=corridor.get(i);
            int x=cell.x(),z=cell.z();
            if(x==previousX&&z==previousZ&&i>0)continue;
            int dx=Integer.compare(x,previousX),dz=Integer.compare(z,previousZ);
            if(dx==0&&dz==0){dx=Integer.compare((int)Math.round(to.x()),x);dz=Integer.compare((int)Math.round(to.z()),z);}
            previousX=x;previousZ=z;
            boolean near=false;for(SimPosition observer:observers){double ox=observer.x()-x,oz=observer.z()-z;if(ox*ox+oz*oz<=radius2){near=true;break;}}if(!near)continue;
            long key=((long)x<<32)^(z&0xffffffffL);out.putIfAbsent(key,new RoutePoint(x,z,dx,dz,route.mode(),route.id()));
            // Densify long corridor segments near observers so materialization does not leave gaps.
            if(i+1<corridor.size()){
                TerrainCorridorPlanner.Cell next=corridor.get(i+1);
                int span=(int)Math.hypot(next.x()-x,next.z()-z);
                int samples=Math.min(span,16);
                for(int s=1;s<samples&&out.size()<maxPoints;s++){
                    double t=s/(double)samples;
                    int ix=(int)Math.round(x+(next.x()-x)*t),iz=(int)Math.round(z+(next.z()-z)*t);
                    boolean n2=false;for(SimPosition observer:observers){double ox=observer.x()-ix,oz=observer.z()-iz;if(ox*ox+oz*oz<=radius2){n2=true;break;}}
                    if(!n2)continue;
                    int idx=Integer.compare(ix,previousX),idz=Integer.compare(iz,previousZ);if(idx==0&&idz==0)continue;
                    previousX=ix;previousZ=iz;
                    long k2=((long)ix<<32)^(iz&0xffffffffL);out.putIfAbsent(k2,new RoutePoint(ix,iz,idx,idz,route.mode(),route.id()));
                }
            }
        }
        return List.copyOf(out.values());
    }

    /**
     * Deterministic chunk/window projection used by persistent world fabric.
     *
     * <p>No observer list is involved. The strategic route supplies a stable centerline and local
     * materialization adapts that centerline to terrain when the window is actually available.</p>
     */
    public static List<RoutePoint> planInBounds(TransportRoute route,SimPosition from,SimPosition to,
                                                 int minX,int minZ,int maxX,int maxZ,int maxPoints){
        Objects.requireNonNull(route,"route");Objects.requireNonNull(from,"from");Objects.requireNonNull(to,"to");
        if(maxX<minX||maxZ<minZ||maxPoints<0)throw new IllegalArgumentException("bounds");
        if(maxPoints==0||!route.operational())return List.of();
        double vx=to.x()-from.x(),vz=to.z()-from.z(),distance=Math.hypot(vx,vz);
        if(distance<1)return List.of();

        double centerX=(minX+maxX)*.5,centerZ=(minZ+maxZ)*.5;
        double len2=vx*vx+vz*vz;
        double tCenter=((centerX-from.x())*vx+(centerZ-from.z())*vz)/len2;
        double halfDiagonal=Math.hypot(maxX-minX+1,maxZ-minZ+1)*.5+3.0;
        double tSpan=halfDiagonal/distance;
        int steps=Math.max(1,(int)Math.ceil(distance));
        int start=Math.max(0,(int)Math.floor((tCenter-tSpan)*steps)-2);
        int end=Math.min(steps,(int)Math.ceil((tCenter+tSpan)*steps)+2);
        int dirX=Integer.compare((int)Math.round(to.x()),(int)Math.round(from.x()));
        int dirZ=Integer.compare((int)Math.round(to.z()),(int)Math.round(from.z()));

        LinkedHashMap<Long,RoutePoint> out=new LinkedHashMap<>();
        for(int i=start;i<=end&&out.size()<maxPoints;i++){
            double t=i/(double)steps;
            int x=(int)Math.round(from.x()+vx*t),z=(int)Math.round(from.z()+vz*t);
            if(x<minX||x>maxX||z<minZ||z>maxZ)continue;
            long key=((long)x<<32)^(z&0xffffffffL);
            out.putIfAbsent(key,new RoutePoint(x,z,dirX,dirZ,route.mode(),route.id()));
        }
        return List.copyOf(out.values());
    }

    private static List<TerrainCorridorPlanner.Cell> corridor(TransportRoute route,SimPosition from,SimPosition to,TerrainCorridorPlanner.TerrainSample terrain){
        int fx=(int)Math.round(from.x()),fz=(int)Math.round(from.z()),tx=(int)Math.round(to.x()),tz=(int)Math.round(to.z());
        if(terrain==null||route.mode()==TransportMode.RAIL){
            // Rails keep geometric corridors; roads without a sampler stay straight (legacy/tests).
            return densifyStraight(fx,fz,tx,tz);
        }
        return TerrainCorridorPlanner.plan(fx,fz,tx,tz,16,2_500,terrain);
    }

    private static List<TerrainCorridorPlanner.Cell> densifyStraight(int fromX,int fromZ,int toX,int toZ){
        double distance=Math.hypot(toX-fromX,toZ-fromZ);
        int steps=Math.max(1,(int)Math.ceil(distance));
        ArrayList<TerrainCorridorPlanner.Cell> out=new ArrayList<>(steps+1);
        for(int i=0;i<=steps;i++){
            double t=i/(double)steps;
            out.add(new TerrainCorridorPlanner.Cell((int)Math.round(fromX+(toX-fromX)*t),(int)Math.round(fromZ+(toZ-fromZ)*t)));
        }
        return out;
    }

    /** Strategic fallback when no terrain sampler is available. */
    static SimPosition curved(long routeId,SimPosition a,SimPosition b,double t){
        return new SimPosition(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t);
    }
}
