package dev.livingrealms.sim.faction;

import dev.livingrealms.sim.military.*;
import dev.livingrealms.sim.util.DeterministicRng;
import dev.livingrealms.sim.util.Mathx;
import dev.livingrealms.sim.world.*;
import static dev.livingrealms.sim.world.SettlementTransfer.transfer;
import java.util.*;

/** Deterministic strategic economy, diplomacy, movement, battle, siege and conquest simulation. */
public final class FactionEngine {
    private static final double ARMY_MOVE_PER_DAY = 85.0;
    private static final double BATTLE_RANGE = 90.0;
    private static final double CAPTURE_RANGE = 55.0;

    public void simulateDay(List<Faction> factions, DeterministicRng rng) {
        Objects.requireNonNull(factions,"factions");Objects.requireNonNull(rng,"rng");
        simulateEconomy(null,factions);simulateDiplomacy(factions,rng);simulateWar(null,factions,rng);cleanupDestroyedArmies(factions);
    }
    public void simulateDay(SimulationState state, DeterministicRng rng) {
        Objects.requireNonNull(state,"state");Objects.requireNonNull(rng,"rng");
        List<Faction> factions=state.factions();simulateEconomy(state,factions);simulateDiplomacy(factions,rng);simulateWar(state,factions,rng);cleanupDestroyedArmies(factions);
    }

    private void simulateEconomy(SimulationState state,List<Faction> factions) {
        for (Faction f : factions) {
            int pop = f.population();
            // Tithe from settlements is the treasury income; army upkeep drains it. No pop*constant gold mint.
            double militaryUpkeep = f.armies().stream().mapToDouble(a -> a.totalPersonnel() * .002).sum();
            double titheGold = 0;
            for (Settlement s : f.settlements()) {
                // Convert a slice of tithed grain/bread value as cash proxy for court payroll.
                double localGrain = s.stockpile().get(ResourceType.GRAIN) + s.stockpile().get(ResourceType.BREAD);
                titheGold += Math.min(12.0, localGrain * 0.0008 * f.government().taxRate());
            }
            f.addTreasury(titheGold - militaryUpkeep);
            double foodNeed = pop * .04;
            double fed = f.stockpile().take(ResourceType.BREAD, foodNeed);
            if (fed < foodNeed) fed += f.stockpile().take(ResourceType.GRAIN, foodNeed - fed);
            if (fed < foodNeed) fed += f.stockpile().take(ResourceType.FOOD, foodNeed - fed);
            double foodRatio = Mathx.clamp(Mathx.safeDiv(fed + localFood(f), Math.max(1, pop * .20)), 0, 1);
            for (Settlement s : f.settlements()) {
                if(state==null){double growth=.00012*(foodRatio-.45)*(1-s.unrest()*.7);if(s.foodSecurity()<0.55)growth=Math.min(growth,0);int delta=(int)Math.round(s.population()*growth);if(s.housingShortage()>0)delta=Math.min(delta,0);s.addPopulation(delta);}
                double woodAvail=s.stockpile().get(ResourceType.WOOD)+f.stockpile().get(ResourceType.WOOD);
                double stoneAvail=s.stockpile().get(ResourceType.STONE)+f.stockpile().get(ResourceType.STONE);
                // Housing only rises when both wood and stone can actually be paid.
                // PLAYER_LED settlements never receive abstract auto-housing — capacity comes from
                // player registration / intentional civic projects only.
                if (s.developmentMode() == DevelopmentMode.PLAYER_LED) continue;
                if (s.housingShortage() > Math.max(5, s.population() * .05) && woodAvail > 20 && stoneAvail > 8) {
                    int build = (int)Math.min(s.housingShortage() + 10, 25 + f.technology() * 25);
                    double woodCost=build*.4,stoneCost=build*.15;
                    if(woodAvail<woodCost||stoneAvail<stoneCost)continue;
                    drawBuildMaterials(f,s,woodCost,stoneCost);
                    s.addHousing(build);s.improveInfrastructure(.001 * build);
                }
                if(state!=null&&s.developmentMode()!=DevelopmentMode.PLAYER_LED
                        &&s.housing()-s.population()<Math.max(10,s.population()/12)&&woodAvail>45&&stoneAvail>18&&Math.floorMod(state.clock().day()+s.id(),7L)==0L){
                    int build=Math.min(36,Math.max(12,s.population()/30));
                    double woodCost=build*.45,stoneCost=build*.18;
                    if(woodAvail<woodCost||stoneAvail<stoneCost)continue;
                    drawBuildMaterials(f,s,woodCost,stoneCost);
                    s.addHousing(build);s.improveInfrastructure(.0007*build);
                }
            }
            // Technology only from schools/workshops/scholarly structures — no free sqrt(pop) drip.
            boolean scholarly=f.settlements().stream().anyMatch(s->
                    s.countProductionPrefix("school:")>0||s.countProductionPrefix("workshop:")>0||s.countProductionPrefix("library:")>0||s.countProductionPrefix("university:")>0);
            if(scholarly)f.advanceTechnology(Math.min(.002,.00015*(.6+.6*f.government().ruler().stewardship())));
        }
    }

    private static double localFood(Faction f){
        double t=0;
        for(Settlement s:f.settlements())t+=s.edibleStock();
        return t;
    }
    private static void drawBuildMaterials(Faction f,Settlement s,double wood,double stone){
        double fromLocalWood=s.stockpile().take(ResourceType.WOOD,wood);if(fromLocalWood<wood)f.stockpile().take(ResourceType.WOOD,wood-fromLocalWood);
        double fromLocalStone=s.stockpile().take(ResourceType.STONE,stone);if(fromLocalStone<stone)f.stockpile().take(ResourceType.STONE,stone-fromLocalStone);
    }

    private static void simulateDiplomacy(List<Faction> factions, DeterministicRng rng) {
        for (int i = 0; i < factions.size(); i++) for (int j = i + 1; j < factions.size(); j++) {
            Faction a = factions.get(i), b = factions.get(j);DiplomaticRelation ar = a.relationWith(b.id()), br = b.relationWith(a.id());
            if (ar.status() != RelationStatus.WAR) {double diplomacy=(a.government().ruler().diplomacy()+b.government().ruler().diplomacy())*.5;double drift = rng.between(-.35, .35)+(diplomacy-.5)*.08;ar.adjust(drift); br.adjust(drift);if (ar.status().ordinal() <= RelationStatus.FRIENDLY.ordinal() && ar.opinion() >= 35 && rng.chance(.01+.01*diplomacy)) {ar.setTradeAgreement(true); br.setTradeAgreement(true);}maybeDeclareWar(a, b, ar, br, rng);}
        }
    }

    private static void maybeDeclareWar(Faction a, Faction b, DiplomaticRelation ar, DiplomaticRelation br, DeterministicRng rng) {
        if (ar.status() != RelationStatus.HOSTILE || br.status() == RelationStatus.WAR) return;double aPower = militaryPower(a), bPower = militaryPower(b);if (aPower <= 0 || bPower <= 0) return;double strongest = Math.max(aPower, bPower), weakest = Math.min(aPower, bPower);double imbalance = Mathx.clamp(strongest / Math.max(1.0, weakest), 1.0, 4.0);double pressure = .001 + (imbalance - 1.0) * .0015;pressure*=1+(1-Math.min(a.government().stability(),b.government().stability()))*.4;if (rng.chance(pressure)) { ar.declareWar(); br.declareWar(); }
    }

    private static void simulateWar(SimulationState state,List<Faction> factions, DeterministicRng rng) {
        Map<Long,Faction> byId=new HashMap<>();for(Faction faction:factions)byId.put(faction.id(),faction);
        for(Faction owner:factions){
            List<Faction> enemies=owner.relations().entrySet().stream().filter(e->e.getValue().status()==RelationStatus.WAR).map(e->byId.get(e.getKey())).filter(Objects::nonNull).toList();
            for(Army army:new ArrayList<>(owner.armies())){
                if(army.destroyed())continue;
                MilitaryObjective objective=activeObjective(state,army.id());
                Army opposingArmy=nearestEnemyArmy(army,enemies);
                if(opposingArmy!=null&&army.position().distanceTo(opposingArmy.position())<=BATTLE_RANGE){resolveBattle(state,army,opposingArmy,rng);continue;}
                if(objective!=null&&executeObjective(state,owner,army,objective,byId,enemies))continue;
                if(enemies.isEmpty())continue;
                EnemyTarget target=nearestTarget(army,enemies);if(target==null)continue;
                advanceOnSettlement(state,owner,army,target);
            }
        }
    }

    private static MilitaryObjective activeObjective(SimulationState state,long armyId){
        if(state==null)return null;
        return state.objectives().stream().filter(o->!o.complete()&&o.armyId()==armyId).max(Comparator.comparingInt(MilitaryObjective::priority).thenComparingLong(MilitaryObjective::createdDay)).orElse(null);
    }

    private static boolean executeObjective(SimulationState state,Faction owner,Army army,MilitaryObjective objective,Map<Long,Faction> byId,List<Faction> enemies){
        return switch(objective.type()){
            case CAPTURE_SETTLEMENT,SIEGE -> {
                Faction targetFaction=byId.get(objective.targetFactionId());
                Settlement settlement=state==null?null:state.findSettlement(objective.targetSettlementId()).orElse(null);
                boolean hostile=targetFaction!=null&&owner.relations().get(targetFaction.id())!=null&&owner.relations().get(targetFaction.id()).status()==RelationStatus.WAR;
                boolean stillOwned=settlement!=null&&targetFaction!=null&&targetFaction.settlements().stream().anyMatch(s->s.id()==settlement.id());
                if(!hostile||!stillOwned){objective.markComplete();yield false;}
                advanceOnSettlement(state,owner,army,new EnemyTarget(targetFaction,settlement));
                if(state!=null&&owner.settlements().stream().anyMatch(s->s.id()==settlement.id()))objective.markComplete();
                yield true;
            }
            case DEFEND -> {
                Settlement settlement=state==null?null:state.findSettlement(objective.targetSettlementId()).orElse(null);
                boolean owned=settlement!=null&&owner.settlements().stream().anyMatch(s->s.id()==settlement.id());
                if(!owned){objective.markComplete();yield false;}
                double d=army.position().distanceTo(settlement.position());
                if(d>60)army.moveToward(settlement.position(),ARMY_MOVE_PER_DAY*(.35+.65*army.supply()));
                resupplyNearFriendlySettlement(owner,army);
                yield true;
            }
            case RETREAT,ESCORT,PATROL_BORDER,RAID -> {
                double d=army.position().distanceTo(objective.targetPosition());
                if(d>45)army.moveToward(objective.targetPosition(),ARMY_MOVE_PER_DAY*(.35+.65*army.supply()));
                else if(objective.type()==MilitaryObjectiveType.RETREAT||objective.type()==MilitaryObjectiveType.ESCORT)objective.markComplete();
                resupplyNearFriendlySettlement(owner,army);
                yield true;
            }
        };
    }

    private static void advanceOnSettlement(SimulationState state,Faction attacker,Army army,EnemyTarget target){
        army.moveToward(target.settlement().position(),ARMY_MOVE_PER_DAY*(.35+.65*army.supply()));resupplyNearFriendlySettlement(attacker,army);
        if(!army.destroyed()&&army.position().distanceTo(target.settlement().position())<=CAPTURE_RANGE&&noDefendingArmyNearby(target.owner(),target.settlement())){
            if(state!=null&&fortified(target.settlement()))resolveSiege(state,attacker,target.owner(),army,target.settlement());else capture(state,attacker,target.owner(),army,target.settlement());
        }
    }

    private static void resolveBattle(SimulationState state,Army a, Army b, DeterministicRng rng) {
        int beforeA=a.totalPersonnel(),beforeB=b.totalPersonnel();double pa = Math.max(.1, a.combatPower()) * rng.between(.85, 1.15);double pb = Math.max(.1, b.combatPower()) * rng.between(.85, 1.15);double total = pa + pb;double lossA = Mathx.clamp((pb / total) * rng.between(.10, .34), .02, .45);double lossB = Mathx.clamp((pa / total) * rng.between(.10, .34), .02, .45);a.applyLossFraction(lossA);b.applyLossFraction(lossB);if (a.combatPower() < b.combatPower() * .55) a.moveToward(a.position().lerp(b.position(), -0.15), 55);if (b.combatPower() < a.combatPower() * .55) b.moveToward(b.position().lerp(a.position(), -0.15), 55);
        if(state!=null){int casualties=(beforeA-a.totalPersonnel())+(beforeB-b.totalPersonnel());state.history().add(new WorldEvent(state.clock().day(),"battle","army="+a.id()+" vs "+b.id()+", casualties="+Math.max(0,casualties)));state.activeWar(a.factionId(),b.factionId()).ifPresent(w->{w.addExhaustion(a.factionId(),lossA*.08);w.addExhaustion(b.factionId(),lossB*.08);w.adjustScore((lossA-lossB)*-20);});}
    }

    private static boolean fortified(Settlement s){return s.completedConstruction().stream().anyMatch(k->k.startsWith("wall:")||k.startsWith("keep:"));}
    private static void resolveSiege(SimulationState state,Faction attacker,Faction defender,Army army,Settlement settlement){
        SiegeState siege=state.sieges().stream().filter(SiegeState::active).filter(x->x.settlementId()==settlement.id()&&x.attackerFactionId()==attacker.id()).findFirst().orElse(null);
        if(siege==null){siege=new SiegeState(state.nextId(),attacker.id(),defender.id(),settlement.id(),state.clock().day());state.addSiege(siege);state.history().add(new WorldEvent(state.clock().day(),"siege_started",attacker.name()+" besieged "+settlement.name()));}
        provisionSiegeEquipment(attacker,army,siege);
        double fort=.35+Math.min(.5,settlement.completedConstruction().stream().filter(k->k.startsWith("wall:")).count()*.035);double blockade=Mathx.clamp(.35+army.totalPersonnel()/Math.max(50.0,settlement.population())*.25,0,1);double engineering=Math.min(.45,siege.rams()*.035+siege.ladders()*.012+siege.artilleryPieces()*.06);double ammoNeed=siege.artilleryPieces()*.12;double ammo=ammoNeed<=0?0:attacker.stockpile().take(ResourceType.AMMUNITION,ammoNeed);double artilleryOperational=ammoNeed<=0?0:Mathx.clamp(ammo/ammoNeed,0,1);double breachGain=Math.max(0,(siege.rams()*.004+siege.artilleryPieces()*.006*artilleryOperational)*(1-siege.defenderCountermeasures()*.55));double breachBefore=siege.breach();siege.addBreach(breachGain);applySiegeStructureDamage(state,settlement,siege,breachBefore);double progress=Math.max(.0005,(army.combatPower()/Math.max(50,settlement.population()*1.5))*.022+engineering*.015+siege.breach()*.018-fort*.006);siege.advance(progress,blockade);settlement.adjustUnrest(.0015*blockade);settlement.setFoodSecurity(settlement.foodSecurity()-.0025*blockade);
        if(state.clock().day()%7==0&&siege.active()){double defend=Mathx.clamp(settlement.publicOrder()*.35+settlement.infrastructure()*.08,0,1);siege.adjustCountermeasures((defend-.5)*.025);if(defend>.62){int ladderLoss=siege.ladders()>0?1:0;int ramLoss=defend>.78&&siege.rams()>0?1:0;siege.damageEquipment(ramLoss,ladderLoss,0);}}
        if(!siege.active()){capture(state,attacker,defender,army,settlement);state.history().add(new WorldEvent(state.clock().day(),"siege_won",attacker.name()+" captured "+settlement.name()));}
    }
    /** Damages LivingRealms-authored walls/gates/keeps when breach crosses thresholds — never player/foreign builds. */
    private static void applySiegeStructureDamage(SimulationState state,Settlement settlement,SiegeState siege,double breachBefore){
        if(siege.breach()<.25)return;
        String[] prefixes=siege.breach()>.75?new String[]{"gate:","wall:","keep:","barracks:"}:siege.breach()>.5?new String[]{"gate:","wall:"}:new String[]{"wall:"};
        // Fire once per threshold crossing.
        boolean crossedLow=breachBefore<.25&&siege.breach()>=.25;
        boolean crossedMid=breachBefore<.5&&siege.breach()>=.5;
        boolean crossedHigh=breachBefore<.75&&siege.breach()>=.75;
        if(!(crossedLow||crossedMid||crossedHigh))return;
        for(String prefix:prefixes){
            String hit;
            while((hit=settlement.damageAuthoredStructure(prefix))!=null){
                state.history().add(new WorldEvent(state.clock().day(),"siege_structure_damage",settlement.name()+" lost "+hit+" (breach="+String.format(java.util.Locale.ROOT,"%.2f",siege.breach())+")"));
            }
        }
    }
    private static void provisionSiegeEquipment(Faction attacker,Army army,SiegeState siege){
        if(siege.rams()<2&&attacker.stockpile().get(ResourceType.WOOD)>=18&&attacker.stockpile().get(ResourceType.TOOLS)>=3){attacker.stockpile().take(ResourceType.WOOD,18);attacker.stockpile().take(ResourceType.TOOLS,3);siege.addEquipment(1,0,0);}
        if(siege.ladders()<6&&attacker.stockpile().get(ResourceType.WOOD)>=6){attacker.stockpile().take(ResourceType.WOOD,6);siege.addEquipment(0,2,0);}
        if(siege.artilleryPieces()<Math.max(0,Math.min(6,army.artillery()/12))&&attacker.stockpile().get(ResourceType.IRON)>=8&&attacker.stockpile().get(ResourceType.MACHINERY)>=4){attacker.stockpile().take(ResourceType.IRON,8);attacker.stockpile().take(ResourceType.MACHINERY,4);siege.addEquipment(0,0,1);}
    }
    private static void capture(SimulationState state,Faction attacker,Faction defender,Army army,Settlement settlement){
        Settlement captured=state==null?defender.removeSettlement(settlement.id()):transfer(state,settlement,defender,attacker);
        if(captured==null)return;
        if(state==null)attacker.addSettlement(captured);
        attacker.relationWith(defender.id()).adjust(-8);defender.relationWith(attacker.id()).adjust(-8);army.resupply(-.12);captured.adjustUnrest(.18);
        if(state!=null){state.history().add(new WorldEvent(state.clock().day(),"settlement_captured",attacker.name()+" captured "+captured.name()+" from "+defender.name()));state.activeWar(attacker.id(),defender.id()).ifPresent(w->w.adjustScore(18));}
    }

    private static void resupplyNearFriendlySettlement(Faction owner, Army army) {for (Settlement settlement : owner.settlements()) if (army.position().distanceTo(settlement.position()) <= 120) {double food = owner.stockpile().take(ResourceType.FOOD, Math.max(1, army.totalPersonnel() * .01));if (food > 0) army.resupply(.08);return;}}
    private static EnemyTarget nearestTarget(Army army, List<Faction> enemies) {EnemyTarget best = null; double bestDistance = Double.POSITIVE_INFINITY;for (Faction enemy : enemies) for (Settlement settlement : enemy.settlements()) {double distance = army.position().distanceTo(settlement.position());if (distance < bestDistance) { bestDistance = distance; best = new EnemyTarget(enemy, settlement); }}return best;}
    private static Army nearestEnemyArmy(Army army, List<Faction> enemies) {Army best = null; double bestDistance = Double.POSITIVE_INFINITY;for (Faction enemy : enemies) for (Army candidate : enemy.armies()) {if (candidate.destroyed()) continue;double distance = army.position().distanceTo(candidate.position());if (distance < bestDistance) { bestDistance = distance; best = candidate; }}return best;}
    private static boolean noDefendingArmyNearby(Faction owner, Settlement settlement) {return owner.armies().stream().filter(a -> !a.destroyed()).noneMatch(a -> a.position().distanceTo(settlement.position()) <= 150);}
    private static double militaryPower(Faction faction) { return faction.armies().stream().mapToDouble(Army::combatPower).sum(); }
    private static void cleanupDestroyedArmies(List<Faction> factions) {for (Faction faction : factions) {List<Long> destroyed = faction.armies().stream().filter(Army::destroyed).map(Army::id).toList();for (long id : destroyed) faction.removeArmy(id);}}
    private record EnemyTarget(Faction owner, Settlement settlement) {}
}
