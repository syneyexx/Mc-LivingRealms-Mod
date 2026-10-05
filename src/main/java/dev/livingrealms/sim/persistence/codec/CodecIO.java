package dev.livingrealms.sim.persistence.codec;

import dev.livingrealms.sim.faction.ResourceType;
import dev.livingrealms.sim.persistence.SimulationStateCodec;
import dev.livingrealms.sim.world.SimPosition;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Shared binary I/O helpers for domain codecs. */
public final class CodecIO {
    private CodecIO() {}

public static void writePosition(DataOutput out,SimPosition p)throws IOException{out.writeDouble(p.x());out.writeDouble(p.z());}

public static SimPosition readPosition(DataInput in)throws IOException{return new SimPosition(in.readDouble(),in.readDouble());}

public static int enumOrdinal(int v,int length,String name)throws IOException{if(v<0||v>=length)throw new IOException("bad "+name+" ordinal: "+v);return v;}

public static int checkedCount(int v,int max,String name)throws IOException{if(v<0||v>max)throw new IOException("Invalid "+name+" count: "+v);return v;}

public static void writeString(DataOutput out,String s)throws IOException{
        byte[] b=Objects.requireNonNullElse(s,"").getBytes(StandardCharsets.UTF_8);
        if(b.length>SimulationStateCodec.MAX_STRING_BYTES)throw new IOException("string too long: "+b.length);
        out.writeInt(b.length);out.write(b);
    }

public static String readString(DataInput in)throws IOException{
        int n=CodecIO.checkedCount(in.readInt(),SimulationStateCodec.MAX_STRING_BYTES,"string bytes");byte[] b=new byte[n];in.readFully(b);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}
        catch(CharacterCodingException malformed){throw new IOException("Malformed UTF-8 in Living Realms save",malformed);}
    }

public static int resourceCount(int version){
        return version>=18?ResourceType.values().length:ResourceType.LEGACY_COUNT;
    }

}
