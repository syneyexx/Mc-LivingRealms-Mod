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
        Objects.requireNonNull(route,"route");Objects.requireNonNull(from,"from");Objects.requireNonNull(to,"to");Objects.requireNonNull(observers,"observers");
        if(radius<=0||maxPoints<0)throw new IllegalArgumentException("budget");if(maxPoints==0||observers.isEmpty()||!route.operational())return List.of();
        double distance=from.distanceTo(to);if(distance<1)return List.of();
        int steps=Math.max(1,(int)Math.ceil(distance));double radius2=radius*radius;
        LinkedHashMap<Long,RoutePoint> out=new LinkedHashMap<>();
        int previousX=(int)Math.round(from.x()),previousZ=(int)Math.round(from.z());
        for(int i=0;i<=steps&&out.size()<maxPoints;i++){
            double t=i/(double)steps;SimPosition p=curved(route.id(),from,to,t);int x=(int)Math.round(p.x()),z=(int)Math.round(p.z());
            if(x==previousX&&z==previousZ&&i>0)continue;int dx=Integer.compare(x,previousX),dz=Integer.compare(z,previousZ);if(dx==0&&dz==0){dx=Integer.compare((int)Math.round(to.x()),x);dz=Integer.compare((int)Math.round(to.z()),z);}previousX=x;previousZ=z;
            boolean near=false;for(SimPosition observer:observers){double ox=observer.x()-x,oz=observer.z()-z;if(ox*ox+oz*oz<=radius2){near=true;break;}}if(!near)continue;
            long key=((long)x<<32)^(z&0xffffffffL);out.putIfAbsent(key,new RoutePoint(x,z,dx,dz,route.mode(),route.id()));
        }
        return List.copyOf(out.values());
    }

    /** Strategic corridors are geometrically straight; the Minecraft adapter may make small local detours around terrain. */
    static SimPosition curved(long routeId,SimPosition a,SimPosition b,double t){
        return new SimPosition(a.x()+(b.x()-a.x())*t,a.z()+(b.z()-a.z())*t);
    }
}
