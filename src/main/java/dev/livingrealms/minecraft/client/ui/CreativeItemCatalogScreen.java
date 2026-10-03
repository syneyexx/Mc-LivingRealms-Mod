package dev.livingrealms.minecraft.client.ui;

import dev.livingrealms.minecraft.network.CreativeSpawnItemPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Searchable creative-only browser for spawnable items supplied by the fixed Living Realms target modpack. */
public final class CreativeItemCatalogScreen extends Screen {
    private static final int PAGE_SIZE=12;
    private final List<Entry> allEntries=new ArrayList<>();
    private final List<Entry> filteredEntries=new ArrayList<>();
    private int page;
    private int spawnCount=1;
    private String query="";

    public CreativeItemCatalogScreen(){super(Component.literal("Living Realms Mod Catalog"));}

    @Override protected void init(){
        if(allEntries.isEmpty()){
            for(Item item:BuiltInRegistries.ITEM){
                if(item==Items.AIR)continue;
                ResourceLocation id=BuiltInRegistries.ITEM.getKey(item);if(id==null||"minecraft".equals(id.getNamespace()))continue;
                allEntries.add(new Entry(id,item.getDescription().getString()));
            }
            allEntries.sort(Comparator.comparing((Entry e)->e.id().getNamespace()).thenComparing(e->e.name().toLowerCase(Locale.ROOT)).thenComparing(e->e.id().toString()));
        }
        applyFilter();
        rebuildPage();
    }

    private void applyFilter(){
        filteredEntries.clear();String needle=query.trim().toLowerCase(Locale.ROOT);
        for(Entry entry:allEntries)if(needle.isEmpty()||entry.name().toLowerCase(Locale.ROOT).contains(needle)||entry.id().toString().toLowerCase(Locale.ROOT).contains(needle))filteredEntries.add(entry);
        page=Math.min(page,maxPage());
    }

    private void rebuildPage(){
        clearWidgets();
        int panelWidth=Math.min(560,Math.max(360,width-40)),left=(width-panelWidth)/2,top=54;
        EditBox search=new EditBox(font,left,28,panelWidth-112,20,Component.literal("Search mod items"));
        search.setValue(query);search.setResponder(value->{query=value;page=0;applyFilter();rebuildPage();});
        addRenderableWidget(search);
        addRenderableWidget(Button.builder(Component.literal("Count: "+spawnCount),b->{spawnCount=spawnCount==1?16:spawnCount==16?64:1;rebuildPage();}).bounds(left+panelWidth-104,28,104,20).build());
        int start=page*PAGE_SIZE,end=Math.min(filteredEntries.size(),start+PAGE_SIZE);
        for(int i=start;i<end;i++){
            Entry e=filteredEntries.get(i);int row=i-start;
            String label=e.name()+"  ["+e.id()+"]";
            if(label.length()>76)label=label.substring(0,73)+"...";
            addRenderableWidget(Button.builder(Component.literal(label),b->PacketDistributor.sendToServer(new CreativeSpawnItemPayload(e.id().toString(),spawnCount))).bounds(left,top+row*20,panelWidth,18).build());
        }
        int navY=top+PAGE_SIZE*20+8;
        addRenderableWidget(Button.builder(Component.literal("< Prev"),b->{if(page>0){page--;rebuildPage();}}).bounds(left,navY,90,20).build());
        addRenderableWidget(Button.builder(Component.literal("Next >"),b->{if(page<maxPage()){page++;rebuildPage();}}).bounds(left+panelWidth-90,navY,90,20).build());
        setInitialFocus(search);
    }

    private int maxPage(){return Math.max(0,(filteredEntries.size()-1)/PAGE_SIZE);}

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        // No vanilla world blur behind Living Realms screens.
        graphics.fill(0,0,width,height,0xC805080B);super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,10,0xFFFFFFFF);
        int infoY=Math.min(height-18,54+PAGE_SIZE*20+34);
        graphics.drawCenteredString(font,"Creative only • search by name/mod id • click to spawn • page "+(page+1)+"/"+(maxPage()+1)+" • "+filteredEntries.size()+"/"+allEntries.size()+" items",width/2,infoY,0xFFB8C0CA);
    }

    @Override public boolean isPauseScreen(){return false;}
    private record Entry(ResourceLocation id,String name){}
}
