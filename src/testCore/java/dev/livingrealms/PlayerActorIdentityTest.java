package dev.livingrealms;

import dev.livingrealms.sim.player.PlayerActorIdentity;
import java.util.UUID;

/**
 * Wave 1 regression: founder, network, structure registration and crime must share one
 * UUID-based actor key. Display names must never become identity.
 */
public final class PlayerActorIdentityTest {
    private PlayerActorIdentityTest() {}

    public static void main(String[] args) {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String canonical = PlayerActorIdentity.of(uuid);
        check(canonical.equals("player:550e8400-e29b-41d4-a716-446655440000"), "uuid form");
        check(PlayerActorIdentity.isCanonical(canonical), "canonical accepted");
        check(PlayerActorIdentity.ofUuidString(uuid.toString()).equals(canonical), "uuid string form");

        // Display-name keys are NOT canonical — this is the bug class that broke registration.
        check(!PlayerActorIdentity.isCanonical("player:Steve"), "display name rejected");
        check(!PlayerActorIdentity.isCanonical("player:builder"), "legacy test name rejected");
        check(!PlayerActorIdentity.isCanonical("Steve"), "bare name rejected");

        // Same UUID must produce identical keys for founder / crime / registration / network.
        String founder = PlayerActorIdentity.of(uuid);
        String crime = PlayerActorIdentity.of(uuid);
        String registration = PlayerActorIdentity.of(uuid);
        String network = PlayerActorIdentity.of(uuid);
        check(founder.equals(crime) && crime.equals(registration) && registration.equals(network),
                "founder == crime == registration == network identity");

        check(PlayerActorIdentity.uuidOrNull(canonical).equals(uuid), "round-trip uuid");
        check(PlayerActorIdentity.uuidOrNull("player:NotAUuid") == null, "bad uuid null");

        System.out.println("PASS PlayerActorIdentityTest: UUID identity shared across founder/crime/registration/network");
    }

    private static void check(boolean ok, String msg) {
        if (!ok) throw new AssertionError(msg);
    }
}
