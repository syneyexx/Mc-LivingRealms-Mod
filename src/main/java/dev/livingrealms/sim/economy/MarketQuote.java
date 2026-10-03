package dev.livingrealms.sim.economy;

import dev.livingrealms.sim.faction.ResourceType;

public record MarketQuote(ResourceType resource,double unitPrice,double supplyDays,double scarcity) {}
