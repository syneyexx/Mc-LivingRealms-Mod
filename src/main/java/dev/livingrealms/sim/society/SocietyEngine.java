package dev.livingrealms.sim.society;

import dev.livingrealms.sim.faction.*;

/** Converts strategic resources, housing and governance into prosperity/unrest and labor allocation. */
public final class SocietyEngine {
    public void simulateDay(Faction faction){
        for(Settlement s:faction.settlements()){
            SocietyAssessment assessment=SocietyDiagnostics.assess(faction,s);
            SettlementNeeds needs=assessment.needs();
            double targetProsperity=assessment.satisfaction()*(.65+.35*Math.min(1,s.infrastructure()));
            s.adjustProsperity((targetProsperity-s.prosperity())*.035);
            double grievance=(1-assessment.satisfaction())*.035+Math.max(0,faction.government().taxRate()-.20)*.025;
            double calming=s.prosperity()*.014+faction.government().legitimacy()*.009;
            s.adjustUnrest(grievance-calming);
            s.setFoodSecurity(needs.food());
            s.setPublicOrder(needs.safety());
            s.setEmployment(needs.employment());
        }
    }
}
