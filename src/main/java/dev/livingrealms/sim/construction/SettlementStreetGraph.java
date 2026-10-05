package dev.livingrealms.sim.construction;

import dev.livingrealms.sim.faction.Faction;
import dev.livingrealms.sim.faction.Settlement;
import dev.livingrealms.sim.world.SimPosition;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Topological source of truth for settlement streets.
 *
 * <p>Morphology creates graph edges and true polyline centerlines first. ROAD construction intents
 * are downstream projections of this graph; parcels also consume the same centerlines.</p>
 */
public final class SettlementStreetGraph {
    public record RoadNode(long id, SimPosition position) {
        public RoadNode {
            if (id <= 0) throw new IllegalArgumentException("id");
            position = Objects.requireNonNull(position, "position");
        }
    }

    public record RoadSegment(
            String key,
            StreetType streetType,
            int width,
            SimPosition start,
            SimPosition end,
            List<SimPosition> centerline,
            int priority,
            SettlementGrowthLayer growthLayer
    ) {
        public RoadSegment {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("key");
            streetType = Objects.requireNonNull(streetType, "streetType");
            if (width <= 0) throw new IllegalArgumentException("width");
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
            centerline = List.copyOf(Objects.requireNonNull(centerline, "centerline"));
            if (centerline.size() < 2) throw new IllegalArgumentException("centerline");
            growthLayer = Objects.requireNonNullElse(growthLayer, SettlementGrowthLayer.HISTORIC_CORE);
        }

        public double length() {
            double sum = 0;
            for (int i = 1; i < centerline.size(); i++) sum += centerline.get(i - 1).distanceTo(centerline.get(i));
            return sum;
        }

        public boolean axisAlignedAlongX() {
            double z = centerline.getFirst().z();
            return centerline.stream().allMatch(p -> Math.abs(p.z() - z) < 0.5);
        }

        public boolean hasNonAxisGeometry() {
            for (int i = 1; i < centerline.size(); i++) {
                SimPosition a = centerline.get(i - 1), b = centerline.get(i);
                if (Math.abs(a.x() - b.x()) > 0.5 && Math.abs(a.z() - b.z()) > 0.5) return true;
            }
            return false;
        }

        /** World-space point at a fraction of total polyline length. */
        public SimPosition pointAt(double fraction) {
            double target = length() * Math.max(0.0, Math.min(1.0, fraction));
            double walked = 0.0;
            for (int i = 1; i < centerline.size(); i++) {
                SimPosition a = centerline.get(i - 1), b = centerline.get(i);
                double span = a.distanceTo(b);
                if (span <= 1.0e-9) continue;
                if (walked + span >= target) return a.lerp(b, (target - walked) / span);
                walked += span;
            }
            return centerline.getLast();
        }

        /** Unit tangent at a fraction of total polyline length. */
        public Tangent tangentAt(double fraction) {
            double target = length() * Math.max(0.0, Math.min(1.0, fraction));
            double walked = 0.0;
            for (int i = 1; i < centerline.size(); i++) {
                SimPosition a = centerline.get(i - 1), b = centerline.get(i);
                double dx = b.x() - a.x(), dz = b.z() - a.z();
                double span = Math.hypot(dx, dz);
                if (span <= 1.0e-9) continue;
                if (walked + span >= target || i == centerline.size() - 1) {
                    return new Tangent(dx / span, dz / span);
                }
                walked += span;
            }
            return new Tangent(1.0, 0.0);
        }
    }

    public record Tangent(double dx, double dz) {
        public Tangent {
            double len = Math.hypot(dx, dz);
            if (!(len > 0.0) || !Double.isFinite(len)) throw new IllegalArgumentException("tangent");
            dx /= len;
            dz /= len;
        }
    }

    public record StreetEdge(long fromNodeId, long toNodeId, String segmentKey) {
        public StreetEdge {
            if (fromNodeId <= 0 || toNodeId <= 0 || fromNodeId == toNodeId) throw new IllegalArgumentException("node ids");
            if (segmentKey == null || segmentKey.isBlank()) throw new IllegalArgumentException("segmentKey");
        }
    }

    private final long settlementId;
    private final List<RoadNode> nodes;
    private final List<RoadSegment> segments;
    private final List<StreetEdge> edges;

    public SettlementStreetGraph(long settlementId, List<RoadNode> nodes, List<RoadSegment> segments, List<StreetEdge> edges) {
        if (settlementId <= 0) throw new IllegalArgumentException("settlementId");
        this.settlementId = settlementId;
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        this.segments = List.copyOf(Objects.requireNonNull(segments, "segments"));
        this.edges = List.copyOf(Objects.requireNonNull(edges, "edges"));
    }

    public long settlementId() { return settlementId; }
    public List<RoadNode> nodes() { return nodes; }
    public List<RoadSegment> segments() { return segments; }
    public List<StreetEdge> edges() { return edges; }
    public boolean isEmpty() { return segments.isEmpty(); }

    /** Build the street topology directly from morphology and stable settlement state. */
    public static SettlementStreetGraph plan(Faction faction, Settlement settlement,
                                             SettlementMorphology morphology, int baseRotation) {
        Objects.requireNonNull(faction, "faction");
        Objects.requireNonNull(settlement, "settlement");
        Objects.requireNonNull(morphology, "morphology");
        Builder b = new Builder(settlement, morphology, baseRotation);
        int tier = settlement.tier().ordinal();
        double r = switch (settlement.tier()) {
            case CAMP -> 42; case HAMLET -> 56; case VILLAGE -> 76;
            case TOWN -> 112; case CITY -> 164; case METROPOLIS -> 214;
        };

        if (tier <= Settlement.Tier.HAMLET.ordinal()) {
            if (morphology == SettlementMorphology.LINEAR_VALLEY) {
                b.add(StreetType.RESIDENTIAL_LANE, 132, SettlementGrowthLayer.HISTORIC_CORE,
                        p(0,-r), p(3,-r*.25), p(0,r*.35), p(-4,r));
            } else {
                double j = b.jitter(1, 8);
                b.add(StreetType.RESIDENTIAL_LANE, 132, SettlementGrowthLayer.HISTORIC_CORE,
                        p(-r,-j), p(-r*.28,j*.7), p(0,0), p(r*.45,-j*.5), p(r,j*.35));
                b.add(StreetType.FOOTPATH, 102, SettlementGrowthLayer.HISTORIC_CORE,
                        p(0,0), p(r*.25,r*.35), p(r*.48,r*.58));
            }
            return b.build();
        }

        switch (morphology) {
            case ORGANIC_MEDIEVAL -> organic(b, r, tier);
            case MARKET_CROSS -> marketCross(b, r, tier);
            case RADIAL_CAPITAL, WALLED_CORE -> radial(b, r, tier);
            case RIVER_TOWN, LINEAR_VALLEY -> river(b, r, tier);
            case HILL_TOWN -> hill(b, r, tier);
            case COASTAL_PORT -> coastal(b, r, tier);
            case INDUSTRIAL_EDGE -> industrial(b, r, tier);
            case PLANNED_BOULEVARD -> planned(b, r, tier);
        }
        return b.build();
    }

    private static void organic(Builder b, double r, int tier) {
        double j1=b.jitter(11,12),j2=b.jitter(12,10),j3=b.jitter(13,12);
        b.add(StreetType.ARTERIAL,134,SettlementGrowthLayer.HISTORIC_CORE,
                p(-r,-j1),p(-r*.48,j2),p(0,0),p(r*.42,-j3),p(r,j1*.5));
        b.add(StreetType.COMMERCIAL_STREET,128,SettlementGrowthLayer.HISTORIC_CORE,
                p(0,0),p(-r*.18,r*.28),p(r*.08,r*.58),p(r*.2,r*.88));
        b.add(StreetType.RESIDENTIAL_STREET,112,SettlementGrowthLayer.EARLY_EXPANSION,
                p(-r*.48,j2),p(-r*.56,-r*.22),p(-r*.35,-r*.55));
        b.add(StreetType.RESIDENTIAL_STREET,110,SettlementGrowthLayer.EARLY_EXPANSION,
                p(r*.42,-j3),p(r*.56,r*.2),p(r*.5,r*.52));
        if(tier>=Settlement.Tier.TOWN.ordinal()){
            b.add(StreetType.RESIDENTIAL_LANE,98,SettlementGrowthLayer.SUBURBAN_EXPANSION,
                    p(-r*.35,-r*.55),p(0,-r*.72),p(r*.44,-r*.55));
            b.add(StreetType.RESIDENTIAL_LANE,98,SettlementGrowthLayer.SUBURBAN_EXPANSION,
                    p(r*.2,r*.88),p(r*.55,r*.72),p(r*.72,r*.42));
        }
    }

    private static void marketCross(Builder b, double r, int tier) {
        SimPosition c=p(0,0);
        b.add(StreetType.MARKET_STREET,138,SettlementGrowthLayer.HISTORIC_CORE,c,p(r*.48,2),p(r,0));
        b.add(StreetType.MARKET_STREET,138,SettlementGrowthLayer.HISTORIC_CORE,c,p(-r*.45,-3),p(-r,0));
        b.add(StreetType.MARKET_STREET,134,SettlementGrowthLayer.HISTORIC_CORE,c,p(2,r*.5),p(0,r));
        b.add(StreetType.MARKET_STREET,134,SettlementGrowthLayer.HISTORIC_CORE,c,p(-3,-r*.5),p(0,-r));
        if(tier>=Settlement.Tier.TOWN.ordinal()){
            double q=r*.62;
            b.add(StreetType.RESIDENTIAL_STREET,108,SettlementGrowthLayer.EARLY_EXPANSION,p(-q,0),p(-q*.9,q*.7),p(0,q));
            b.add(StreetType.RESIDENTIAL_STREET,108,SettlementGrowthLayer.EARLY_EXPANSION,p(0,q),p(q*.9,q*.7),p(q,0));
            b.add(StreetType.RESIDENTIAL_STREET,108,SettlementGrowthLayer.EARLY_EXPANSION,p(q,0),p(q*.9,-q*.7),p(0,-q));
            b.add(StreetType.RESIDENTIAL_STREET,108,SettlementGrowthLayer.EARLY_EXPANSION,p(0,-q),p(-q*.9,-q*.7),p(-q,0));
        }
    }

    private static void radial(Builder b, double r, int tier) {
        int spokes=tier>=Settlement.Tier.CITY.ordinal()?8:6;
        List<SimPosition> outer=new ArrayList<>();
        for(int i=0;i<spokes;i++){
            double a=Math.PI*2*i/spokes;
            SimPosition o=p(Math.cos(a)*r,Math.sin(a)*r);
            outer.add(o);
            b.add(i%2==0?StreetType.BOULEVARD:StreetType.ARTERIAL,142-i,SettlementGrowthLayer.HISTORIC_CORE,
                    p(0,0),p(Math.cos(a)*r*.48,Math.sin(a)*r*.48),
                    p(Math.cos(a)*r*.68,Math.sin(a)*r*.68),o);
        }
        double ringRadius=r*.68;
        for(int i=0;i<spokes;i++){
            double a1=Math.PI*2*i/spokes,a2=Math.PI*2*(i+1)/spokes,am=(a1+a2)*.5;
            SimPosition a=p(Math.cos(a1)*ringRadius,Math.sin(a1)*ringRadius);
            SimPosition m=p(Math.cos(am)*ringRadius*1.05,Math.sin(am)*ringRadius*1.05);
            SimPosition z=p(Math.cos(a2)*ringRadius,Math.sin(a2)*ringRadius);
            b.add(StreetType.RESIDENTIAL_STREET,112,SettlementGrowthLayer.EARLY_EXPANSION,a,m,z);
        }
    }

    private static void river(Builder b, double r, int tier) {
        SimPosition a=p(-r*.12,-r),q1=p(r*.08,-r*.45),c=p(0,0),q2=p(-r*.1,r*.45),z=p(r*.08,r);
        b.add(StreetType.ARTERIAL,136,SettlementGrowthLayer.HISTORIC_CORE,a,q1,c,q2,z);
        b.add(StreetType.RESIDENTIAL_STREET,112,SettlementGrowthLayer.EARLY_EXPANSION,q1,p(-r*.45,-r*.38),p(-r*.72,-r*.28));
        b.add(StreetType.RESIDENTIAL_STREET,112,SettlementGrowthLayer.EARLY_EXPANSION,q2,p(r*.45,r*.36),p(r*.72,r*.26));
        b.add(StreetType.COMMERCIAL_STREET,120,SettlementGrowthLayer.HISTORIC_CORE,c,p(r*.4,2),p(r*.7,4));
        if(tier>=Settlement.Tier.TOWN.ordinal())
            b.add(StreetType.RESIDENTIAL_LANE,100,SettlementGrowthLayer.SUBURBAN_EXPANSION,p(-r*.1,r*.45),p(-r*.5,r*.6),p(-r*.75,r*.52));
    }

    private static void hill(Builder b, double r, int tier) {
        SimPosition p0=p(-r*.7,-r),p1=p(r*.55,-r*.58),p2=p(-r*.5,-r*.16),p3=p(r*.48,r*.24),p4=p(-r*.34,r*.62),p5=p(0,r);
        b.add(StreetType.ARTERIAL,136,SettlementGrowthLayer.HISTORIC_CORE,p0,p1,p2,p3,p4,p5);
        b.add(StreetType.RESIDENTIAL_STREET,112,SettlementGrowthLayer.EARLY_EXPANSION,p2,p(-r*.78,-r*.05),p(-r*.86,r*.16));
        b.add(StreetType.RESIDENTIAL_STREET,112,SettlementGrowthLayer.EARLY_EXPANSION,p3,p(r*.78,r*.16),p(r*.86,r*.38));
        if(tier>=Settlement.Tier.TOWN.ordinal())
            b.add(StreetType.RESIDENTIAL_LANE,100,SettlementGrowthLayer.SUBURBAN_EXPANSION,p4,p(-r*.68,r*.72),p(-r*.82,r*.58));
    }

    private static void coastal(Builder b, double r, int tier) {
        SimPosition a=p(-r,r*.42),c=p(0,r*.38),z=p(r,r*.44);
        b.add(StreetType.BOULEVARD,138,SettlementGrowthLayer.HISTORIC_CORE,a,p(-r*.5,r*.36),c,p(r*.5,r*.37),z);
        b.add(StreetType.ARTERIAL,134,SettlementGrowthLayer.HISTORIC_CORE,c,p(0,0),p(0,-r));
        b.add(StreetType.COMMERCIAL_STREET,120,SettlementGrowthLayer.EARLY_EXPANSION,p(-r*.5,r*.36),p(-r*.45,0),p(-r*.38,-r*.48));
        b.add(StreetType.COMMERCIAL_STREET,120,SettlementGrowthLayer.EARLY_EXPANSION,p(r*.5,r*.37),p(r*.46,0),p(r*.38,-r*.48));
        if(tier>=Settlement.Tier.TOWN.ordinal())
            b.add(StreetType.RESIDENTIAL_STREET,104,SettlementGrowthLayer.SUBURBAN_EXPANSION,p(-r*.38,-r*.48),p(0,-r*.62),p(r*.38,-r*.48));
    }

    private static void industrial(Builder b, double r, int tier) {
        planned(b,r,tier);
        b.add(StreetType.BOULEVARD,124,SettlementGrowthLayer.INDUSTRIAL_EXPANSION,p(0,0),p(r*.55,r*.25),p(r,r*.42));
        b.add(StreetType.RESIDENTIAL_STREET,102,SettlementGrowthLayer.INDUSTRIAL_EXPANSION,p(r*.55,r*.25),p(r*.52,-r*.25),p(r,-r*.4));
    }

    private static void planned(Builder b, double r, int tier) {
        b.add(StreetType.BOULEVARD,140,SettlementGrowthLayer.HISTORIC_CORE,p(-r,0),p(0,0),p(r,0));
        b.add(StreetType.ARTERIAL,136,SettlementGrowthLayer.HISTORIC_CORE,p(0,-r),p(0,0),p(0,r));
        double q=r*.55;
        b.add(StreetType.RESIDENTIAL_STREET,110,SettlementGrowthLayer.EARLY_EXPANSION,p(-q,-q),p(-q,0),p(-q,q));
        b.add(StreetType.RESIDENTIAL_STREET,110,SettlementGrowthLayer.EARLY_EXPANSION,p(q,-q),p(q,0),p(q,q));
        if(tier>=Settlement.Tier.TOWN.ordinal()){
            b.add(StreetType.RESIDENTIAL_STREET,106,SettlementGrowthLayer.EARLY_EXPANSION,p(-q,-q),p(0,-q),p(q,-q));
            b.add(StreetType.RESIDENTIAL_STREET,106,SettlementGrowthLayer.EARLY_EXPANSION,p(-q,q),p(0,q),p(q,q));
        }
    }

    private static SimPosition p(double x,double z){return new SimPosition(x,z);}

    /** Return a new graph with additional graph-authored segments; existing topology is preserved. */
    public SettlementStreetGraph withAdditionalSegments(List<RoadSegment> additional) {
        Objects.requireNonNull(additional, "additional");
        if (additional.isEmpty()) return this;
        BuilderRaw b = new BuilderRaw(settlementId);
        for (RoadSegment segment : segments) b.add(segment);
        for (RoadSegment segment : additional) b.add(Objects.requireNonNull(segment, "segment"));
        return b.build();
    }

    /** Compatibility import for legacy tests/tools; new production planning must use {@link #plan}. */
    public static SettlementStreetGraph fromRoadIntents(long settlementId, List<ConstructionIntent> roadIntents) {
        Objects.requireNonNull(roadIntents, "roadIntents");
        BuilderRaw b=new BuilderRaw(settlementId);
        for(ConstructionIntent intent:roadIntents){
            if(intent.role()!=StructureRole.ROAD)continue;
            RoadSegment segment=segmentFromIntent(intent);
            b.add(segment);
        }
        return b.build();
    }

    static RoadSegment segmentFromIntent(ConstructionIntent intent) {
        List<SimPosition> line;
        SimPosition start,end;
        if(intent.hasPath()){
            line=intent.path();
            start=line.getFirst();end=line.getLast();
        }else{
            int turns=Math.floorMod(intent.rotationQuarterTurns(),4),halfDepth=intent.depth()/2;
            start=offset(intent.center(),turns,0,-halfDepth);
            end=offset(intent.center(),turns,0,halfDepth);
            List<SimPosition> points=new ArrayList<>();
            int steps=Math.max(1,intent.depth()/4);
            for(int i=0;i<=steps;i++)points.add(start.lerp(end,i/(double)steps));
            line=List.copyOf(points);
        }
        StreetType type=StreetType.forWidth(intent.width(),false,intent.key().contains("market"),
                intent.key().contains("regional")||intent.key().contains("royal"));
        return new RoadSegment(intent.key(),type,intent.width(),start,end,line,intent.priority(),
                growthLayerFromKey(intent.key()));
    }

    private static SettlementGrowthLayer growthLayerFromKey(String key) {
        String k=key==null?"":key.toLowerCase(java.util.Locale.ROOT);
        for(SettlementGrowthLayer layer:SettlementGrowthLayer.values())
            if(k.contains(layer.name().toLowerCase(java.util.Locale.ROOT)))return layer;
        return SettlementGrowthLayer.HISTORIC_CORE;
    }

    private static SimPosition offset(SimPosition origin,int quarterTurns,double x,double z){
        return switch(Math.floorMod(quarterTurns,4)){
            case 0->new SimPosition(origin.x()+x,origin.z()+z);
            case 1->new SimPosition(origin.x()-z,origin.z()+x);
            case 2->new SimPosition(origin.x()-x,origin.z()-z);
            default->new SimPosition(origin.x()+z,origin.z()-x);
        };
    }

    public boolean hasConnectedCore() {
        if(segments.isEmpty()||nodes.isEmpty())return false;
        ArrayDeque<Long> q=new ArrayDeque<>();
        HashSet<Long> seen=new HashSet<>();
        q.add(nodes.getFirst().id());seen.add(nodes.getFirst().id());
        while(!q.isEmpty()){
            long cur=q.removeFirst();
            for(StreetEdge edge:edges){
                long other=edge.fromNodeId()==cur?edge.toNodeId()
                        :edge.toNodeId()==cur?edge.fromNodeId():-1;
                if(other>0&&seen.add(other))q.add(other);
            }
        }
        return seen.size()==nodes.size();
    }

    public Map<String,RoadSegment> segmentByKey(){
        Map<String,RoadSegment> map=new LinkedHashMap<>();
        for(RoadSegment segment:segments)map.put(segment.key(),segment);
        return Collections.unmodifiableMap(map);
    }

    private static final class Builder {
        private final Settlement settlement;
        private final SettlementMorphology morphology;
        private final int baseRotation;
        private final BuilderRaw raw;
        private int index;

        Builder(Settlement settlement,SettlementMorphology morphology,int baseRotation){
            this.settlement=settlement;this.morphology=morphology;this.baseRotation=baseRotation;
            this.raw=new BuilderRaw(settlement.id());
        }

        double jitter(int salt,double amplitude){
            long m=mix(settlement.id()^(salt*0x9E3779B97F4A7C15L));
            return ((((m>>>18)&0xFFFFL)/65535.0)*2.0-1.0)*amplitude;
        }

        void add(StreetType type,int priority,SettlementGrowthLayer layer,SimPosition... localPoints){
            List<SimPosition> world=new ArrayList<>(localPoints.length);
            for(SimPosition local:localPoints)world.add(local(local.x(),local.z()));
            String key="roadgraph:"+morphology.wireName()+":"+layer.name().toLowerCase(java.util.Locale.ROOT)+":"+(index++);
            raw.add(new RoadSegment(key,type,type.width(),world.getFirst(),world.getLast(),world,priority,layer));
        }

        SimPosition local(double x,double z){
            return switch(Math.floorMod(baseRotation,4)){
                case 0->new SimPosition(settlement.position().x()+x,settlement.position().z()+z);
                case 1->new SimPosition(settlement.position().x()-z,settlement.position().z()+x);
                case 2->new SimPosition(settlement.position().x()-x,settlement.position().z()-z);
                default->new SimPosition(settlement.position().x()+z,settlement.position().z()-x);
            };
        }

        SettlementStreetGraph build(){return raw.build();}
    }

    private static final class BuilderRaw {
        private final long settlementId;
        private final List<RoadNode> nodes=new ArrayList<>();
        private final List<RoadSegment> segments=new ArrayList<>();
        private final List<StreetEdge> edges=new ArrayList<>();
        private final Map<String,Long> nodeIndex=new LinkedHashMap<>();
        private long nextNode=1;

        BuilderRaw(long settlementId){this.settlementId=settlementId;}

        void add(RoadSegment segment){
            segments.add(segment);
            long previous=-1;
            for(SimPosition point:segment.centerline()){
                long current=nodeId(point);
                if(previous>0&&previous!=current)edges.add(new StreetEdge(previous,current,segment.key()));
                previous=current;
            }
        }

        long nodeId(SimPosition pos){
            double qx=Math.rint(pos.x()*2.0)/2.0,qz=Math.rint(pos.z()*2.0)/2.0;
            String key=qx+":"+qz;
            Long existing=nodeIndex.get(key);
            if(existing!=null)return existing;
            long id=nextNode++;
            nodeIndex.put(key,id);
            nodes.add(new RoadNode(id,new SimPosition(qx,qz)));
            return id;
        }

        SettlementStreetGraph build(){
            connectOverlappingRoadSurfaces();
            return new SettlementStreetGraph(settlementId,nodes,segments,edges);
        }

        /**
         * A physical junction exists when the end of one authored centerline reaches the surface
         * width of another road, even when their centerline vertices are a few blocks apart.
         * Keep the authored polylines unchanged and add only topology edges/nodes, preserving
         * append-only road geometry while making graph connectivity match the actual carriageway.
         */
        private void connectOverlappingRoadSurfaces(){
            for(int ai=0;ai<segments.size();ai++){
                RoadSegment a=segments.get(ai);
                List<SimPosition> aLine=a.centerline();
                SimPosition[] endpoints={aLine.getFirst(),aLine.getLast()};
                for(int bi=0;bi<segments.size();bi++){
                    if(ai==bi)continue;
                    RoadSegment b=segments.get(bi);
                    double tolerance=(a.width()+b.width())*.5+.25;
                    List<SimPosition> bLine=b.centerline();
                    for(SimPosition endpoint:endpoints){
                        NearestSpan nearest=nearestSpan(endpoint,bLine);
                        if(nearest==null||nearest.distance()>tolerance)continue;
                        long endpointNode=nodeId(endpoint);
                        long junction=nodeId(nearest.point());
                        long from=nodeId(bLine.get(nearest.spanIndex()));
                        long to=nodeId(bLine.get(nearest.spanIndex()+1));
                        addEdgeIfDistinct(endpointNode,junction,a.key());
                        addEdgeIfDistinct(from,junction,b.key());
                        addEdgeIfDistinct(junction,to,b.key());
                    }
                }
            }
        }

        private void addEdgeIfDistinct(long from,long to,String key){
            if(from<=0||to<=0||from==to)return;
            for(StreetEdge edge:edges){
                if(edge.segmentKey().equals(key)
                        &&((edge.fromNodeId()==from&&edge.toNodeId()==to)
                        ||(edge.fromNodeId()==to&&edge.toNodeId()==from)))return;
            }
            edges.add(new StreetEdge(from,to,key));
        }

        private static NearestSpan nearestSpan(SimPosition point,List<SimPosition> line){
            NearestSpan best=null;
            for(int i=0;i+1<line.size();i++){
                SimPosition a=line.get(i),b=line.get(i+1);
                double vx=b.x()-a.x(),vz=b.z()-a.z();
                double len2=vx*vx+vz*vz;
                if(len2<=1.0e-9)continue;
                double t=((point.x()-a.x())*vx+(point.z()-a.z())*vz)/len2;
                t=Math.max(0.0,Math.min(1.0,t));
                SimPosition q=new SimPosition(a.x()+vx*t,a.z()+vz*t);
                double distance=point.distanceTo(q);
                if(best==null||distance<best.distance())best=new NearestSpan(i,q,distance);
            }
            return best;
        }

        private record NearestSpan(int spanIndex,SimPosition point,double distance){}
    }

    private static long mix(long z){
        z=(z^(z>>>30))*0xBF58476D1CE4E5B9L;
        z=(z^(z>>>27))*0x94D049BB133111EBL;
        return z^(z>>>31);
    }
}
