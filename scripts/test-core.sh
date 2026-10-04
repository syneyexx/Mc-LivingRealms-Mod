#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/core-test"
rm -rf "$OUT" && mkdir -p "$OUT"
find "$ROOT/src/main/java/dev/livingrealms/sim" "$ROOT/src/testCore/java" -name '*.java' -print0 | xargs -0 javac --release 21 -Xlint:all -Werror -d "$OUT"
java -cp "$OUT" dev.livingrealms.CoreSimulationTest
java -cp "$OUT" dev.livingrealms.SpeciesPackAuditTest
java -cp "$OUT" dev.livingrealms.SystemCompletenessTest
java -cp "$OUT" dev.livingrealms.SettlementEconomyTest
java -cp "$OUT" dev.livingrealms.ProjectionStressTest
java -cp "$OUT" dev.livingrealms.SaveMigrationMatrixTest
java -cp "$OUT" dev.livingrealms.SaveIntegrityTest
java -cp "$OUT" dev.livingrealms.SaveMutationFuzzTest
java -cp "$OUT" dev.livingrealms.ProductionHardeningTest
java -cp "$OUT" dev.livingrealms.LivingWorldDensityTest
java -cp "$OUT" dev.livingrealms.WorldgenQualityTest
java -cp "$OUT" dev.livingrealms.ProductionQualityTest
java -cp "$OUT" dev.livingrealms.SocietyDialogueTest
java -cp "$OUT" dev.livingrealms.RumorNetworkTest
java -cp "$OUT" dev.livingrealms.SocietyInfrastructureTest
java -cp "$OUT" dev.livingrealms.CivilizationLayerTest
java -cp "$OUT" dev.livingrealms.FaithAndInfrastructureTest
java -cp "$OUT" dev.livingrealms.HumanityLifecycleTest
java -cp "$OUT" dev.livingrealms.CitizenConversationTest
java -cp "$OUT" dev.livingrealms.ResourceDominanceTest
java -cp "$OUT" dev.livingrealms.CartographicKnowledgeTest
java -cp "$OUT" dev.livingrealms.PlayerRulershipTest
java -cp "$OUT" dev.livingrealms.WizardTreesTest
java -cp "$OUT" dev.livingrealms.LongRunSoakTest
