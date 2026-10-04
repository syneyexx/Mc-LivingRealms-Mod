package dev.livingrealms.minecraft.client.ui;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Free-text NPC conversation UI. No LLM is used; messages are interpreted on the integrated server. */
public final class NpcDialogueScreen extends Screen {
    private static final int MAX_LINES=120;
    private final long citizenId; private final String citizenName,role; private final List<Line> transcript=new ArrayList<>(); private EditBox input;
    public NpcDialogueScreen(long citizenId,String citizenName,String role,String greeting){super(Component.literal(citizenName));this.citizenId=citizenId;this.citizenName=citizenName;this.role=role;appendNpc(greeting);}
    @Override protected void init(){int panel=Math.min(620,Math.max(360,width-40)),left=(width-panel)/2;input=new EditBox(font,left+8,height-48,panel-104,20,Component.literal("Type freely..."));input.setMaxLength(512);addRenderableWidget(input);addRenderableWidget(Button.builder(Component.literal("Send"),b->send()).bounds(left+panel-88,height-48,80,20).build());setInitialFocus(input);}
    private void send(){if(input==null)return;String msg=input.getValue().trim();if(msg.isEmpty())return;appendPlayer(msg);NpcDialogueClientState.send(citizenId,msg);input.setValue("");}
    public void appendNpc(String text){append("NPC",text);} private void appendPlayer(String text){append("You",text);} private void append(String who,String text){transcript.add(new Line(who,Objects.requireNonNullElse(text,"")));while(transcript.size()>MAX_LINES)transcript.removeFirst();}
    @Override public boolean keyPressed(int keyCode,int scanCode,int modifiers){if(keyCode==257||keyCode==335){send();return true;}return super.keyPressed(keyCode,scanCode,modifiers);}
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        LivingRealmsScreens.clearBackground(g,mouseX,mouseY,partialTick);
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        // No vanilla world blur: dialogue must stay sharp over the living world.
        LivingRealmsScreens.paintBackdrop(g,width,height,0xC805080B);
        int panel=Math.min(620,Math.max(360,width-40)),left=(width-panel)/2,top=24,bottom=height-58;g.fill(left,top,left+panel,bottom,0xCC101418);g.drawCenteredString(font,citizenName+" • "+pretty(role),width/2,top+8,0xFFFFFFFF);int y=top+28,maxWidth=panel-24;List<Rendered> rendered=new ArrayList<>();for(Line line:transcript){List<FormattedCharSequence> pieces=font.split(Component.literal(line.who()+": "+line.text()),maxWidth);rendered.add(new Rendered(line.who(),pieces));}int total=0;for(Rendered r:rendered)total+=r.lines().size()*10+4;y=Math.max(y,bottom-12-total);for(Rendered r:rendered){int color=r.who().equals("You")?0xFF9ED8FF:0xFFE6E0C8;for(FormattedCharSequence piece:r.lines()){if(y>=top+24&&y<bottom-8)g.drawString(font,piece,left+12,y,color,false);y+=10;}y+=4;}super.render(g,mouseX,mouseY,partialTick);g.drawString(font,"Free text • follow-up questions keep context • Esc closes",left+8,height-24,0xFFAEB8C2,false);}
    @Override public boolean isPauseScreen(){return false;} private static String pretty(String value){return value==null?"":value.toLowerCase(Locale.ROOT).replace('_',' ');} private record Line(String who,String text){} private record Rendered(String who,List<FormattedCharSequence> lines){}
}
