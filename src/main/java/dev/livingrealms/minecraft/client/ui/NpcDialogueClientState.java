package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.minecraft.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Physical-client dialogue state. All interpretation and consequences remain server authoritative. */
public final class NpcDialogueClientState {
    private static long citizenId; private static String citizenName=""; private static String role="";
    private NpcDialogueClientState(){}
    public static void receive(DialogueClientBridge.Event event){Minecraft mc=Minecraft.getInstance();if(event instanceof DialogueClientBridge.Open open){var p=open.payload();citizenId=p.citizenId();citizenName=p.citizenName();role=p.role();mc.setScreen(new NpcDialogueScreen(citizenId,citizenName,role,p.greeting()));}else if(event instanceof DialogueClientBridge.Response response&&mc.screen instanceof NpcDialogueScreen screen&&response.payload().citizenId()==citizenId)screen.appendNpc(response.payload().response());}
    public static void send(long target,String message){if(target<=0||message==null||message.isBlank())return;PacketDistributor.sendToServer(new DialogueMessagePayload(target,message.trim()));}
    public static void clear(){citizenId=0;citizenName="";role="";}
}
