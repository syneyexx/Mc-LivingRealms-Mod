package dev.livingrealms.sim.persistence;

import dev.livingrealms.sim.ecology.SpeciesCatalog;
import dev.livingrealms.sim.ecology.SpeciesDefinition;
import dev.livingrealms.sim.persistence.codec.*;
import dev.livingrealms.sim.validation.SimulationValidator;
import dev.livingrealms.sim.world.SimulationState;
import java.io.*;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * Versioned binary codec for canonical Living Realms state. No Minecraft classes required.
 * Domain section layout is delegated to {@code dev.livingrealms.sim.persistence.codec.*};
 * this class owns the envelope, schema gate, and encode/decode orchestration order.
 */
public final class SimulationStateCodec {
    private static final int MAGIC = 0x4C52534D; // LRSM
    public static final int MIN_SUPPORTED_SCHEMA = 1;
    public static final int SCHEMA_VERSION = 21;
    /** Hard ceiling for one canonical world-state payload. Prevents corrupt/local saves from driving unbounded decode work. */
    public static final int MAX_STATE_BYTES = 32 * 1024 * 1024;
    /** Individual canonical text fields are metadata, identifiers or bounded event text; 64 KiB is intentionally generous. */
    public static final int MAX_STRING_BYTES = 64 * 1024;
    private SimulationStateCodec() {}

    /** Returns the embedded schema after validating the binary header. */
    public static int inspectSchema(byte[] data) {
        Objects.requireNonNull(data,"data");
        if(data.length<8)throw new IllegalArgumentException("Living Realms payload is too short");
        if(data.length>MAX_STATE_BYTES)throw new IllegalArgumentException("Living Realms payload exceeds hard size limit: "+data.length);
        int magic=((data[0]&0xFF)<<24)|((data[1]&0xFF)<<16)|((data[2]&0xFF)<<8)|(data[3]&0xFF);
        if(magic!=MAGIC)throw new IllegalArgumentException("Not a Living Realms state payload");
        int version=((data[4]&0xFF)<<24)|((data[5]&0xFF)<<16)|((data[6]&0xFF)<<8)|(data[7]&0xFF);
        if(version<MIN_SUPPORTED_SCHEMA||version>SCHEMA_VERSION)throw new IllegalArgumentException("Unsupported Living Realms schema "+version);
        return version;
    }

    /** Non-zero CRC32 token; zero is reserved for pre-checksum Minecraft SavedData. */
    public static long integrityToken(byte[] data) {
        Objects.requireNonNull(data,"data");
        CRC32 crc=new CRC32();crc.update(data);return crc.getValue()+1L;
    }

    public static byte[] encode(SimulationState state) {
        Objects.requireNonNull(state,"state");
        SimulationValidator.validate(state).throwIfInvalid();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(bytes))) {
                out.writeInt(MAGIC);out.writeInt(SCHEMA_VERSION);out.writeLong(state.seed());out.writeLong(state.clock().gameTicks());out.writeLong(state.peekNextId());
                EcologyCodec.writeRegions(out,state);
                FactionCodec.writeFactions(out,state);
                EconomyCodec.writeShipments(out,state);
                WarfareCodec.writeV4Strategic(out,state);
                LawCodec.writeV5Law(out,state);
                WarfareCodec.writeV6NavalAndPlayers(out,state);
                EconomyCodec.writeV7Industry(out,state);
                EconomyCodec.writeV9Config(out,state);
                SocietyCodec.writeV11Social(out,state);
                SocietyCodec.writeV12Civilization(out,state);
                SocietyCodec.writeV13Humanity(out,state);
                SocietyCodec.writeV14PirateHideouts(out,state);
                WarfareCodec.writeV15SiegeEquipment(out,state);
                SettlementCodec.writeV16SettlementEconomy(out,state);
                ConstructionCodec.writeV17FinalProduct(out,state);
                SettlementCodec.writeV18GoodsAndOrigins(out,state);
                SettlementCodec.writeV19ProvenanceAndSites(out,state);
                UnderworldCodec.writeV20UnderworldContracts(out,state);
                HistoryCodec.writeHistory(out,state);
            }
            byte[] payload=bytes.toByteArray();
            if(payload.length>MAX_STATE_BYTES)throw new IllegalStateException("Living Realms state exceeds hard size limit: "+payload.length);
            return payload;
        } catch(IOException e){throw new UncheckedIOException(e);}
    }

    public static SimulationState decode(byte[] data){return decode(data,SpeciesCatalog.starter());}
    public static SimulationState decode(byte[] data,Map<String,SpeciesDefinition> speciesCatalog){
        Objects.requireNonNull(data,"data");Objects.requireNonNull(speciesCatalog,"speciesCatalog");
        if(data.length>MAX_STATE_BYTES)throw new IllegalArgumentException("Living Realms payload exceeds hard size limit: "+data.length);
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new ByteArrayInputStream(data)))){
            if(in.readInt()!=MAGIC)throw new IOException("Not a Living Realms state file");int version=in.readInt();if(version<MIN_SUPPORTED_SCHEMA||version>SCHEMA_VERSION)throw new IOException("Unsupported schema "+version);
            long seed=in.readLong();long ticks=in.readLong();long nextId=in.readLong();
            if(ticks<0)throw new IOException("Negative simulation clock: "+ticks);if(nextId<1)throw new IOException("Invalid nextId: "+nextId);
            SimulationState state=new SimulationState(seed,speciesCatalog);state.clock().restore(ticks);state.restoreNextId(nextId);
            EcologyCodec.readRegions(in,state,version);
            FactionCodec.readFactions(in,state,version);
            if(version>=3)EconomyCodec.readShipments(in,state,version);
            if(version>=4)WarfareCodec.readV4Strategic(in,state);
            if(version>=5)LawCodec.readV5Law(in,state);
            if(version>=6)WarfareCodec.readV6NavalAndPlayers(in,state);
            if(version>=7)EconomyCodec.readV7Industry(in,state);
            if(version>=9)EconomyCodec.readV9Config(in,state);
            if(version>=11)SocietyCodec.readV11Social(in,state);
            if(version>=12)SocietyCodec.readV12Civilization(in,state);
            if(version>=13)SocietyCodec.readV13Humanity(in,state,version);
            if(version>=14)SocietyCodec.readV14PirateHideouts(in,state);
            if(version>=15)WarfareCodec.readV15SiegeEquipment(in,state);
            if(version>=16)SettlementCodec.readV16SettlementEconomy(in,state,version);
            else SaveMigrationRegistry.migrate15to16(state);
            if(version>=17)ConstructionCodec.readV17FinalProduct(in,state);
            else SaveMigrationRegistry.migrate16to17(state);
            if(version>=18)SettlementCodec.readV18GoodsAndOrigins(in,state);
            else SaveMigrationRegistry.migrate17to18(state);
            if(version>=19)SettlementCodec.readV19ProvenanceAndSites(in,state);
            else SaveMigrationRegistry.migrate18to19(state);
            if(version>=20)UnderworldCodec.readV20UnderworldContracts(in,state);
            else SaveMigrationRegistry.migrate19to20(state);
            if(version<21)SaveMigrationRegistry.migrate20to21(state);
            HistoryCodec.readHistory(in,state);
            if(in.read()!=-1)throw new IOException("Trailing bytes after Living Realms state");
            state.repairNextIdWatermark();
            if(version==SCHEMA_VERSION){try{SimulationValidator.validate(state).throwIfInvalid();}catch(IllegalStateException invalid){throw new IOException("Current-schema Living Realms state failed semantic validation",invalid);}}
            return state;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
}
