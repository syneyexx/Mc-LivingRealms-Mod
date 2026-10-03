#!/usr/bin/env python3
"""Build the bundled Living Realms species datapack.

Biological numeric values are gameplay-tuned defaults, not a scientific dataset. The source rows keep
identity, ecology, morphology, locomotion and food-web relationships reviewable while JSON is generated.
"""
from __future__ import annotations
import json, math
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/data/livingrealms/livingrealms/species'
OUT.mkdir(parents=True,exist_ok=True)

# Preserve the hand-tuned original 20, but make their physical traits explicit.
def infer_morph(i,tags):
    v=i.lower()
    if any(x in v for x in ('wolf','fox','coyote','jackal','wild_dog')): return 'CANID'
    if any(x in v for x in ('lion','tiger','leopard','jaguar','cheetah','lynx','bobcat','cougar')): return 'FELID'
    if 'bear' in v or 'panda' in v: return 'URSID'
    if 'elephant' in v: return 'PROBOSCIDEAN'
    if any(x in v for x in ('boar','pig')): return 'SUID'
    if 'hippo' in v: return 'HIPPOPOTAMID'
    if any(x in v for x in ('rabbit','hare')): return 'LAGOMORPH'
    if any(x in v for x in ('crocodile','alligator','caiman','gharial')): return 'CROCODILIAN'
    if 'shark' in v: return 'SHARK'
    if any(x in v for x in ('orca','whale','dolphin','porpoise')): return 'CETACEAN'
    if any(x in v for x in ('seal','walrus','sea_lion')): return 'PINNIPED'
    if any(x in v for x in ('eagle','hawk','falcon','owl','vulture')): return 'RAPTOR_BIRD'
    if 'flying' in tags: return 'BIRD'
    if 'aquatic' in tags and 'amphibious' not in tags: return 'FISH'
    if any(x in v for x in ('deer','bison','zebra','wildebeest','moose','antelope','gazelle','impala','kudu','giraffe','rhino','buffalo','camel','llama','alpaca','horse','yak','goat','sheep','reindeer','elk')): return 'UNGULATE'
    if any(x in v for x in ('capybara','beaver','squirrel','marmot','porcupine')): return 'RODENT'
    return 'GENERIC_QUADRUPED'

def infer_loco(tags):
    if 'flying' in tags:return 'FLYING'
    if 'aquatic' in tags and 'amphibious' in tags:return 'AMPHIBIOUS'
    if 'aquatic' in tags:return 'AQUATIC'
    if 'amphibious' in tags:return 'AMPHIBIOUS'
    return 'TERRESTRIAL'

def physical(d):
    loco=d.get('locomotion') or infer_loco(set(d['habitatTags']))
    d['morphology']=d.get('morphology') or infer_morph(d['id'],set(d['habitatTags']))
    d['locomotion']=loco
    d['swimSpeedFactor']=d.get('swimSpeedFactor',1.0 if loco=='AQUATIC' else .72 if loco=='AMPHIBIOUS' else .20)
    d['flightSpeedFactor']=d.get('flightSpeedFactor',1.0 if loco=='FLYING' else 0.0)
    return d

ORIGINAL_IDS={
    'bison','brown_bear','elephant','golden_eagle','gray_wolf','great_white_shark','hippopotamus','lion','moose','orca',
    'polar_bear','rabbit','red_deer','red_fox','salmon','saltwater_crocodile','seal','wild_boar','wildebeest','zebra'
}
base={}
for p in OUT.glob('*.json'):
    d=json.loads(p.read_text())
    if d.get('id') in ORIGINAL_IDS:
        base[d['id']]=physical(d)

# compact profiles; individual rows can override any numeric field.
P={
'small_herb':dict(diet='HERBIVORE',activityCycle='CREPUSCULAR',socialPattern='FAMILY',adultMassKg=4,lifespanDays=3650,maturityDays=300,gestationDays=40,offspringPerBirth=3,birthsPerYear=2,dailyFoodKg=.45,dailyWaterLitres=.3,movementKmPerDay=7,aggression=.08,fearfulness=.85,huntSkill=0,defense=.15,minGroup=2,maxGroup=16),
'medium_herb':dict(diet='HERBIVORE',activityCycle='CREPUSCULAR',socialPattern='HERD',adultMassKg=120,lifespanDays=6200,maturityDays=600,gestationDays=210,offspringPerBirth=1,birthsPerYear=1,dailyFoodKg=4,dailyWaterLitres=7,movementKmPerDay=12,aggression=.15,fearfulness=.75,huntSkill=0,defense=.5,minGroup=3,maxGroup=35),
'large_herb':dict(diet='HERBIVORE',activityCycle='DIURNAL',socialPattern='HERD',adultMassKg=550,lifespanDays=8000,maturityDays=1000,gestationDays=300,offspringPerBirth=1,birthsPerYear=.8,dailyFoodKg=13,dailyWaterLitres=30,movementKmPerDay=14,aggression=.3,fearfulness=.55,huntSkill=0,defense=.86,minGroup=3,maxGroup=80),
'mega_herb':dict(diet='HERBIVORE',activityCycle='CATHEMERAL',socialPattern='HERD',adultMassKg=2200,lifespanDays=16000,maturityDays=2400,gestationDays=480,offspringPerBirth=1,birthsPerYear=.35,dailyFoodKg=60,dailyWaterLitres=65,movementKmPerDay=16,aggression=.45,fearfulness=.28,huntSkill=0,defense=.98,minGroup=2,maxGroup=35),
'small_omni':dict(diet='OMNIVORE',activityCycle='NOCTURNAL',socialPattern='SOLITARY',adultMassKg=8,lifespanDays=4300,maturityDays=360,gestationDays=60,offspringPerBirth=3,birthsPerYear=1.3,dailyFoodKg=.6,dailyWaterLitres=.5,movementKmPerDay=10,aggression=.28,fearfulness=.58,huntSkill=.35,defense=.25,minGroup=1,maxGroup=5),
'medium_omni':dict(diet='OMNIVORE',activityCycle='CATHEMERAL',socialPattern='FAMILY',adultMassKg=80,lifespanDays=6500,maturityDays=700,gestationDays=120,offspringPerBirth=2,birthsPerYear=.8,dailyFoodKg=3,dailyWaterLitres=4,movementKmPerDay=12,aggression=.48,fearfulness=.45,huntSkill=.45,defense=.55,minGroup=1,maxGroup=8),
'small_pred':dict(diet='CARNIVORE',activityCycle='CREPUSCULAR',socialPattern='SOLITARY',adultMassKg=14,lifespanDays=4500,maturityDays=500,gestationDays=65,offspringPerBirth=3,birthsPerYear=1,dailyFoodKg=.8,dailyWaterLitres=.7,movementKmPerDay=20,aggression=.5,fearfulness=.5,huntSkill=.7,defense=.3,minGroup=1,maxGroup=4),
'medium_pred':dict(diet='CARNIVORE',activityCycle='CATHEMERAL',socialPattern='PACK',adultMassKg=55,lifespanDays=5200,maturityDays=700,gestationDays=70,offspringPerBirth=4,birthsPerYear=.8,dailyFoodKg=2.5,dailyWaterLitres=2,movementKmPerDay=30,aggression=.7,fearfulness=.28,huntSkill=.8,defense=.5,minGroup=2,maxGroup=12),
'large_pred':dict(diet='CARNIVORE',activityCycle='CATHEMERAL',socialPattern='SOLITARY',adultMassKg=160,lifespanDays=6200,maturityDays=1000,gestationDays=105,offspringPerBirth=2,birthsPerYear=.55,dailyFoodKg=5,dailyWaterLitres=4,movementKmPerDay=25,aggression=.82,fearfulness=.18,huntSkill=.88,defense=.7,minGroup=1,maxGroup=3),
'raptor':dict(diet='CARNIVORE',activityCycle='DIURNAL',socialPattern='PAIR',adultMassKg=3.5,lifespanDays=7300,maturityDays=900,gestationDays=40,offspringPerBirth=2,birthsPerYear=1,dailyFoodKg=.25,dailyWaterLitres=.08,movementKmPerDay=55,aggression=.4,fearfulness=.5,huntSkill=.78,defense=.18,minGroup=1,maxGroup=3),
'bird':dict(diet='OMNIVORE',activityCycle='DIURNAL',socialPattern='FLOCK',adultMassKg=1.2,lifespanDays=4200,maturityDays=280,gestationDays=28,offspringPerBirth=4,birthsPerYear=1.4,dailyFoodKg=.12,dailyWaterLitres=.08,movementKmPerDay=35,aggression=.12,fearfulness=.75,huntSkill=.2,defense=.1,minGroup=3,maxGroup=60),
'fish':dict(diet='OMNIVORE',activityCycle='CATHEMERAL',socialPattern='SCHOOL',adultMassKg=3,lifespanDays=3000,maturityDays=500,gestationDays=30,offspringPerBirth=200,birthsPerYear=.8,dailyFoodKg=.06,dailyWaterLitres=0,movementKmPerDay=18,aggression=.05,fearfulness=.88,huntSkill=.2,defense=.08,minGroup=20,maxGroup=1200),
'fish_pred':dict(diet='PISCIVORE',activityCycle='CATHEMERAL',socialPattern='SCHOOL',adultMassKg=30,lifespanDays=5000,maturityDays=900,gestationDays=60,offspringPerBirth=40,birthsPerYear=.5,dailyFoodKg=1.2,dailyWaterLitres=0,movementKmPerDay=35,aggression=.6,fearfulness=.4,huntSkill=.75,defense=.35,minGroup=2,maxGroup=80),
'marine_pred':dict(diet='CARNIVORE',activityCycle='CATHEMERAL',socialPattern='SOLITARY',adultMassKg=450,lifespanDays=9000,maturityDays=1800,gestationDays=300,offspringPerBirth=4,birthsPerYear=.25,dailyFoodKg=12,dailyWaterLitres=0,movementKmPerDay=60,aggression=.78,fearfulness=.12,huntSkill=.9,defense=.78,minGroup=1,maxGroup=4),
'cetacean':dict(diet='PISCIVORE',activityCycle='CATHEMERAL',socialPattern='PACK',adultMassKg=1800,lifespanDays=15000,maturityDays=2500,gestationDays=360,offspringPerBirth=1,birthsPerYear=.3,dailyFoodKg=45,dailyWaterLitres=0,movementKmPerDay=80,aggression=.22,fearfulness=.15,huntSkill=.82,defense=.85,minGroup=2,maxGroup=25),
'amphib_reptile':dict(diet='CARNIVORE',activityCycle='CATHEMERAL',socialPattern='SOLITARY',adultMassKg=35,lifespanDays=7000,maturityDays=900,gestationDays=70,offspringPerBirth=18,birthsPerYear=.5,dailyFoodKg=.9,dailyWaterLitres=.2,movementKmPerDay=5,aggression=.62,fearfulness=.28,huntSkill=.66,defense=.62,minGroup=1,maxGroup=5),
'primate':dict(diet='OMNIVORE',activityCycle='DIURNAL',socialPattern='FAMILY',adultMassKg=55,lifespanDays=12000,maturityDays=1800,gestationDays=230,offspringPerBirth=1,birthsPerYear=.4,dailyFoodKg=3,dailyWaterLitres=3,movementKmPerDay=8,aggression=.4,fearfulness=.4,huntSkill=.25,defense=.45,minGroup=3,maxGroup=35),
}

def row(i,n,profile,morph,loco,climates,tags,prey=(),attacks=False,**over):
    d={'id':i,'commonName':n,**P[profile]}
    d.update(over)
    d['climates']=list(climates);d['habitatTags']=list(tags);d['preySpecies']=list(prey);d['predatorSpecies']=[];d['attacksHumans']=attacks
    d['morphology']=morph;d['locomotion']=loco
    d['swimSpeedFactor']=over.get('swimSpeedFactor',1.0 if loco=='AQUATIC' else .72 if loco=='AMPHIBIOUS' else .2)
    d['flightSpeedFactor']=over.get('flightSpeedFactor',1.0 if loco=='FLYING' else 0.0)
    return d

S=[]
a=S.append
# North America / Eurasia
a(row('coyote','Coyote','medium_pred','CANID','TERRESTRIAL',['TEMPERATE','ARID'],['grassland','open','dry'],['rabbit'],False,adultMassKg=16,minGroup=1,maxGroup=8))
a(row('canada_lynx','Canada Lynx','small_pred','FELID','TERRESTRIAL',['BOREAL'],['forest','cold'],['rabbit'],False,adultMassKg=11))
a(row('bobcat','Bobcat','small_pred','FELID','TERRESTRIAL',['TEMPERATE','ARID'],['forest','grassland','dry'],['rabbit'],False))
a(row('cougar','Cougar','large_pred','FELID','TERRESTRIAL',['TEMPERATE','ALPINE','ARID'],['forest','mountain','grassland'],['red_deer'],True,adultMassKg=70))
a(row('black_bear','American Black Bear','medium_omni','URSID','TERRESTRIAL',['TEMPERATE','BOREAL'],['forest','freshwater'],['salmon'],True,adultMassKg=130))
a(row('elk','Elk','medium_herb','UNGULATE','TERRESTRIAL',['TEMPERATE','BOREAL'],['forest','grassland'],(),False,adultMassKg=320,maxGroup=80))
a(row('reindeer','Reindeer','medium_herb','UNGULATE','TERRESTRIAL',['POLAR','BOREAL'],['cold','open','tundra'],(),False,adultMassKg=150,maxGroup=120))
a(row('roe_deer','Roe Deer','medium_herb','UNGULATE','TERRESTRIAL',['TEMPERATE'],['forest','grassland'],(),False,adultMassKg=28,minGroup=1,maxGroup=12))
a(row('beaver','Eurasian Beaver','small_herb','RODENT','AMPHIBIOUS',['TEMPERATE','BOREAL'],['river','freshwater','forest','amphibious'],(),False,adultMassKg=20,swimSpeedFactor=.85))
a(row('capybara','Capybara','medium_herb','RODENT','AMPHIBIOUS',['TROPICAL','SUBTROPICAL'],['wetland','river','warm','amphibious'],(),False,adultMassKg=50,minGroup=3,maxGroup=30,swimSpeedFactor=.8))
a(row('alpine_ibex','Alpine Ibex','medium_herb','UNGULATE','TERRESTRIAL',['ALPINE'],['mountain','rocky','cold'],(),False,adultMassKg=80))
a(row('bighorn_sheep','Bighorn Sheep','medium_herb','UNGULATE','TERRESTRIAL',['ALPINE','ARID'],['mountain','rocky','dry'],(),False,adultMassKg=90))
a(row('wild_horse','Wild Horse','large_herb','UNGULATE','TERRESTRIAL',['TEMPERATE','ARID'],['grassland','open'],(),False,adultMassKg=430,maxGroup=60))
a(row('yak','Wild Yak','large_herb','UNGULATE','TERRESTRIAL',['ALPINE','BOREAL'],['mountain','cold','open'],(),False,adultMassKg=650))
# Africa / Asia big fauna
a(row('tiger','Tiger','large_pred','FELID','TERRESTRIAL',['TROPICAL','TEMPERATE'],['forest','wetland'],['wild_boar','red_deer'],True,adultMassKg=210))
a(row('leopard','Leopard','large_pred','FELID','TERRESTRIAL',['TROPICAL','SUBTROPICAL'],['forest','grassland','warm'],['wild_boar'],True,adultMassKg=65))
a(row('jaguar','Jaguar','large_pred','FELID','TERRESTRIAL',['TROPICAL'],['forest','wetland','river','warm'],['capybara','wild_boar'],True,adultMassKg=90))
a(row('cheetah','Cheetah','large_pred','FELID','TERRESTRIAL',['TROPICAL'],['grassland','open','warm'],['impala','gazelle'],False,adultMassKg=55,movementKmPerDay=35,huntSkill=.92,defense=.5))
a(row('spotted_hyena','Spotted Hyena','medium_pred','GENERIC_QUADRUPED','TERRESTRIAL',['TROPICAL'],['grassland','warm'],['wildebeest','zebra','gazelle'],True,adultMassKg=60,minGroup=3,maxGroup=40))
a(row('african_wild_dog','African Wild Dog','medium_pred','CANID','TERRESTRIAL',['TROPICAL'],['grassland','warm'],['impala','gazelle'],False,adultMassKg=25,minGroup=4,maxGroup=30))
a(row('gazelle','Thomson Gazelle','medium_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['grassland','warm','open'],(),False,adultMassKg=23,maxGroup=120))
a(row('impala','Impala','medium_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['grassland','warm','open'],(),False,adultMassKg=50,maxGroup=100))
a(row('greater_kudu','Greater Kudu','medium_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['forest','grassland','warm'],(),False,adultMassKg=220))
a(row('giraffe','Giraffe','mega_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['grassland','warm','open'],(),False,adultMassKg=900,maxGroup=30))
a(row('white_rhinoceros','White Rhinoceros','mega_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['grassland','warm','open'],(),True,adultMassKg=2300,minGroup=1,maxGroup=8))
a(row('cape_buffalo','Cape Buffalo','large_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['grassland','warm','wetland'],(),True,adultMassKg=700,maxGroup=150,defense=.98))
a(row('dromedary_camel','Dromedary Camel','large_herb','UNGULATE','TERRESTRIAL',['ARID'],['arid','warm','open'],(),False,adultMassKg=500,dailyWaterLitres=18))
a(row('bactrian_camel','Bactrian Camel','large_herb','UNGULATE','TERRESTRIAL',['ARID'],['arid','cold','open'],(),False,adultMassKg=600,dailyWaterLitres=16))
a(row('giant_panda','Giant Panda','medium_herb','URSID','TERRESTRIAL',['TEMPERATE'],['forest','mountain','humid'],(),False,adultMassKg=100,socialPattern='SOLITARY'))
a(row('gorilla','Western Gorilla','primate','GENERIC_QUADRUPED','TERRESTRIAL',['TROPICAL'],['forest','humid','warm'],(),True,adultMassKg=140,minGroup=3,maxGroup=20))
a(row('chimpanzee','Chimpanzee','primate','GENERIC_QUADRUPED','TERRESTRIAL',['TROPICAL'],['forest','warm'],(),True,adultMassKg=45,minGroup=5,maxGroup=60))
a(row('orangutan','Bornean Orangutan','primate','GENERIC_QUADRUPED','TERRESTRIAL',['TROPICAL'],['forest','humid','warm'],(),False,adultMassKg=60,socialPattern='SOLITARY',minGroup=1,maxGroup=3))
a(row('olive_baboon','Olive Baboon','primate','GENERIC_QUADRUPED','TERRESTRIAL',['TROPICAL','SUBTROPICAL'],['grassland','forest','warm'],(),True,adultMassKg=24,minGroup=6,maxGroup=80))
# Australia
a(row('red_kangaroo','Red Kangaroo','medium_herb','GENERIC_QUADRUPED','TERRESTRIAL',['ARID','SUBTROPICAL'],['grassland','dry','open'],(),False,adultMassKg=55,minGroup=2,maxGroup=40))
a(row('koala','Koala','small_herb','GENERIC_QUADRUPED','TERRESTRIAL',['SUBTROPICAL'],['forest','warm'],(),False,adultMassKg=9,socialPattern='SOLITARY'))
a(row('common_wombat','Common Wombat','medium_herb','GENERIC_QUADRUPED','TERRESTRIAL',['TEMPERATE'],['forest','grassland'],(),False,adultMassKg=28,socialPattern='SOLITARY'))
a(row('emu','Emu','bird','BIRD','TERRESTRIAL',['ARID','SUBTROPICAL'],['grassland','dry','open'],(),False,adultMassKg=38,socialPattern='PAIR',movementKmPerDay=25,flightSpeedFactor=0))
# Birds
a(row('bald_eagle','Bald Eagle','raptor','RAPTOR_BIRD','FLYING',['TEMPERATE','BOREAL'],['coast','river','forest','flying'],['salmon'],False,adultMassKg=5,flightSpeedFactor=1.05))
a(row('peregrine_falcon','Peregrine Falcon','raptor','RAPTOR_BIRD','FLYING',['TEMPERATE','ALPINE','ARID'],['mountain','coast','flying'],(),False,adultMassKg=1.0,flightSpeedFactor=1.35))
a(row('great_horned_owl','Great Horned Owl','raptor','RAPTOR_BIRD','FLYING',['TEMPERATE','BOREAL'],['forest','flying'],['rabbit'],False,adultMassKg=1.4,activityCycle='NOCTURNAL'))
a(row('raven','Common Raven','bird','BIRD','FLYING',['TEMPERATE','BOREAL','ALPINE'],['forest','mountain','flying'],(),False,adultMassKg=1.1,minGroup=1,maxGroup=20))
a(row('canada_goose','Canada Goose','bird','BIRD','FLYING',['TEMPERATE','BOREAL'],['lake','river','wetland','flying'],(),False,adultMassKg=4,swimSpeedFactor=.45))
a(row('mallard','Mallard','bird','BIRD','FLYING',['TEMPERATE','BOREAL'],['lake','river','wetland','flying'],(),False,adultMassKg=1.1,swimSpeedFactor=.6))
a(row('greater_flamingo','Greater Flamingo','bird','BIRD','FLYING',['TROPICAL','SUBTROPICAL'],['wetland','estuary','warm','flying'],(),False,adultMassKg=3.2,minGroup=10,maxGroup=300))
a(row('ostrich','Common Ostrich','bird','BIRD','TERRESTRIAL',['TROPICAL','ARID'],['grassland','warm','open'],(),False,adultMassKg=105,flightSpeedFactor=0,minGroup=2,maxGroup=30))
a(row('emperor_penguin','Emperor Penguin','fish','BIRD','AMPHIBIOUS',['POLAR'],['coast','ice','cold','amphibious'],['salmon'],False,adultMassKg=30,swimSpeedFactor=1.0,minGroup=20,maxGroup=1000,offspringPerBirth=1,birthsPerYear=.8))
a(row('scarlet_macaw','Scarlet Macaw','bird','BIRD','FLYING',['TROPICAL'],['forest','warm','humid','flying'],(),False,adultMassKg=1.0,minGroup=2,maxGroup=30))
a(row('toco_toucan','Toco Toucan','bird','BIRD','FLYING',['TROPICAL'],['forest','warm','flying'],(),False,adultMassKg=.6,minGroup=2,maxGroup=15))
a(row('griffon_vulture','Griffon Vulture','raptor','RAPTOR_BIRD','FLYING',['TEMPERATE','ARID'],['mountain','grassland','flying'],(),False,adultMassKg=8,minGroup=2,maxGroup=40,huntSkill=.25))
# Reptiles/amphibians
a(row('american_alligator','American Alligator','amphib_reptile','CROCODILIAN','AMPHIBIOUS',['SUBTROPICAL'],['wetland','river','warm','amphibious'],['capybara'],True,adultMassKg=230,swimSpeedFactor=.9))
a(row('komodo_dragon','Komodo Dragon','amphib_reptile','OTHER_REPTILE','TERRESTRIAL',['TROPICAL'],['forest','dry','warm'],['wild_boar'],True,adultMassKg=75,aggression=.8,defense=.75))
a(row('green_iguana','Green Iguana','small_herb','OTHER_REPTILE','TERRESTRIAL',['TROPICAL'],['forest','warm','humid'],(),False,adultMassKg=5))
a(row('burmese_python','Burmese Python','small_pred','OTHER_REPTILE','TERRESTRIAL',['TROPICAL'],['wetland','forest','warm'],['rabbit'],True,adultMassKg=45,movementKmPerDay=3,defense=.45))
a(row('king_cobra','King Cobra','small_pred','OTHER_REPTILE','TERRESTRIAL',['TROPICAL'],['forest','warm'],(),True,adultMassKg=6,movementKmPerDay=4,aggression=.7))
a(row('green_sea_turtle','Green Sea Turtle','medium_herb','OTHER_REPTILE','AQUATIC',['AQUATIC','TROPICAL'],['ocean','reef','coast','aquatic'],(),False,adultMassKg=140,swimSpeedFactor=.75,dailyWaterLitres=0))
a(row('galapagos_tortoise','Galapagos Tortoise','medium_herb','OTHER_REPTILE','TERRESTRIAL',['TROPICAL','ARID'],['grassland','dry','warm'],(),False,adultMassKg=200,movementKmPerDay=1,defense=.9))
a(row('bullfrog','American Bullfrog','small_pred','AMPHIBIAN','AMPHIBIOUS',['TEMPERATE','SUBTROPICAL'],['wetland','river','freshwater','amphibious'],(),False,adultMassKg=.5,swimSpeedFactor=.75,minGroup=1,maxGroup=12))
a(row('giant_salamander','Chinese Giant Salamander','small_pred','AMPHIBIAN','AMPHIBIOUS',['TEMPERATE'],['river','freshwater','cold','amphibious'],(),False,adultMassKg=25,swimSpeedFactor=.8))
# Freshwater/ocean fish
a(row('rainbow_trout','Rainbow Trout','fish','FISH','AQUATIC',['AQUATIC','TEMPERATE'],['river','freshwater','aquatic'],(),False,adultMassKg=2))
a(row('atlantic_cod','Atlantic Cod','fish','FISH','AQUATIC',['AQUATIC','BOREAL','TEMPERATE'],['ocean','shelf','cold','aquatic'],(),False,adultMassKg=8))
a(row('bluefin_tuna','Atlantic Bluefin Tuna','fish_pred','FISH','AQUATIC',['AQUATIC','TEMPERATE','SUBTROPICAL'],['ocean','pelagic','aquatic'],['salmon','atlantic_cod'],False,adultMassKg=250,swimSpeedFactor=1.25))
a(row('piranha','Red-bellied Piranha','fish_pred','FISH','AQUATIC',['AQUATIC','TROPICAL'],['river','freshwater','warm','aquatic'],(),False,adultMassKg=1.2,minGroup=10,maxGroup=200))
a(row('barracuda','Great Barracuda','fish_pred','FISH','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['reef','coast','ocean','aquatic'],['salmon'],True,adultMassKg=20,swimSpeedFactor=1.2))
a(row('hammerhead_shark','Great Hammerhead Shark','marine_pred','SHARK','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['ocean','coast','aquatic'],['salmon','seal'],True,adultMassKg=450,swimSpeedFactor=1.1))
a(row('tiger_shark','Tiger Shark','marine_pred','SHARK','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['ocean','coast','aquatic'],['seal','salmon'],True,adultMassKg=500,swimSpeedFactor=1.0))
a(row('whale_shark','Whale Shark','fish','SHARK','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['ocean','pelagic','warm','aquatic'],(),False,adultMassKg=9000,diet='FILTER_FEEDER',dailyFoodKg=80,swimSpeedFactor=.7,minGroup=1,maxGroup=3))
a(row('manta_ray','Giant Manta Ray','fish','FISH','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['ocean','reef','pelagic','aquatic'],(),False,adultMassKg=1200,diet='FILTER_FEEDER',dailyFoodKg=15,swimSpeedFactor=.9,minGroup=1,maxGroup=8))
# marine mammals
a(row('bottlenose_dolphin','Bottlenose Dolphin','cetacean','CETACEAN','AQUATIC',['AQUATIC','TEMPERATE','TROPICAL'],['ocean','coast','aquatic'],['salmon'],False,adultMassKg=260,minGroup=3,maxGroup=30,swimSpeedFactor=1.2))
a(row('humpback_whale','Humpback Whale','cetacean','CETACEAN','AQUATIC',['AQUATIC','POLAR','TEMPERATE'],['ocean','pelagic','aquatic'],(),False,adultMassKg=28000,diet='FILTER_FEEDER',dailyFoodKg=500,minGroup=1,maxGroup=8,swimSpeedFactor=.85))
a(row('blue_whale','Blue Whale','cetacean','CETACEAN','AQUATIC',['AQUATIC','POLAR','TEMPERATE'],['ocean','pelagic','deep','aquatic'],(),False,adultMassKg=100000,diet='FILTER_FEEDER',dailyFoodKg=1200,minGroup=1,maxGroup=3,swimSpeedFactor=.8))
a(row('sperm_whale','Sperm Whale','cetacean','CETACEAN','AQUATIC',['AQUATIC','TEMPERATE','TROPICAL'],['ocean','deep','aquatic'],(),False,adultMassKg=35000,diet='CARNIVORE',dailyFoodKg=400,minGroup=1,maxGroup=15,swimSpeedFactor=.85))
a(row('manatee','West Indian Manatee','large_herb','CETACEAN','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['coast','estuary','warm','aquatic'],(),False,adultMassKg=450,dailyWaterLitres=0,swimSpeedFactor=.55,minGroup=1,maxGroup=8))
a(row('california_sea_lion','California Sea Lion','fish_pred','PINNIPED','AMPHIBIOUS',['AQUATIC','TEMPERATE'],['coast','ocean','amphibious'],['salmon'],False,adultMassKg=250,swimSpeedFactor=1.0,minGroup=3,maxGroup=80))
a(row('walrus','Walrus','medium_omni','PINNIPED','AMPHIBIOUS',['POLAR','AQUATIC'],['coast','ice','cold','amphibious'],(),True,adultMassKg=800,dailyWaterLitres=0,swimSpeedFactor=.8,minGroup=3,maxGroup=100))
# invertebrates - aggregate ecology can represent them; physical renderer uses coarse family until bespoke models arrive.
a(row('giant_pacific_octopus','Giant Pacific Octopus','small_pred','INVERTEBRATE','AQUATIC',['AQUATIC','TEMPERATE'],['ocean','coast','rocky','aquatic'],(),False,adultMassKg=25,swimSpeedFactor=.55))
a(row('giant_squid','Giant Squid','marine_pred','INVERTEBRATE','AQUATIC',['AQUATIC'],['ocean','deep','aquatic'],(),False,adultMassKg=180,swimSpeedFactor=.8,aggression=.45))
a(row('american_lobster','American Lobster','small_omni','INVERTEBRATE','AQUATIC',['AQUATIC','BOREAL','TEMPERATE'],['ocean','coast','rocky','aquatic'],(),False,adultMassKg=2,swimSpeedFactor=.25))
a(row('red_king_crab','Red King Crab','small_omni','INVERTEBRATE','AQUATIC',['AQUATIC','POLAR','BOREAL'],['ocean','cold','coast','aquatic'],(),False,adultMassKg=5,swimSpeedFactor=.18))
a(row('moon_jellyfish','Moon Jellyfish','fish','INVERTEBRATE','AQUATIC',['AQUATIC','TEMPERATE'],['ocean','coast','aquatic'],(),False,adultMassKg=.2,diet='FILTER_FEEDER',swimSpeedFactor=.25,minGroup=5,maxGroup=200))
# Buildfix10 biodiversity expansion: common companion species, insects, additional fish, birds,
# snakes and extinct-style megafauna requested for the living-world presentation.
a(row('domestic_dog','Domestic Dog','medium_omni','CANID','TERRESTRIAL',['TEMPERATE','SUBTROPICAL','BOREAL'],['settlement','grassland','forest'],['rabbit'],False,adultMassKg=24,minGroup=1,maxGroup=8,fearfulness=.28))
a(row('domestic_cat','Domestic Cat','small_pred','FELID','TERRESTRIAL',['TEMPERATE','SUBTROPICAL'],['settlement','grassland','forest'],['rabbit'],False,adultMassKg=4.5,minGroup=1,maxGroup=5,fearfulness=.35))
a(row('golden_hamster','Golden Hamster','small_herb','RODENT','TERRESTRIAL',['TEMPERATE','ARID'],['grassland','dry','settlement'],(),False,adultMassKg=.13,minGroup=1,maxGroup=4,movementKmPerDay=2))
a(row('field_grasshopper','Field Grasshopper','small_herb','INVERTEBRATE','TERRESTRIAL',['TEMPERATE','SUBTROPICAL','ARID'],['grassland','open'],(),False,adultMassKg=.002,minGroup=20,maxGroup=5000,dailyFoodKg=.0005,dailyWaterLitres=.0001,movementKmPerDay=1,offspringPerBirth=80,birthsPerYear=3))
a(row('saber_tooth_cat','Saber-toothed Cat','large_pred','FELID','TERRESTRIAL',['TEMPERATE','BOREAL'],['grassland','forest','open'],['red_deer','elk'],True,adultMassKg=220,aggression=.88,defense=.78))
a(row('red_tailed_hawk','Red-tailed Hawk','raptor','RAPTOR_BIRD','FLYING',['TEMPERATE','ARID'],['grassland','forest','flying'],['rabbit'],False,adultMassKg=1.2,flightSpeedFactor=1.18))
a(row('harpy_eagle','Harpy Eagle','raptor','RAPTOR_BIRD','FLYING',['TROPICAL'],['forest','warm','flying'],(),False,adultMassKg=7.5,flightSpeedFactor=1.05))
a(row('black_vulture','Black Vulture','raptor','RAPTOR_BIRD','FLYING',['TEMPERATE','SUBTROPICAL','TROPICAL'],['grassland','forest','flying'],(),False,adultMassKg=2.2,huntSkill=.22,minGroup=2,maxGroup=35))
a(row('vervet_monkey','Vervet Monkey','primate','GENERIC_QUADRUPED','TERRESTRIAL',['TROPICAL','SUBTROPICAL'],['forest','grassland','warm'],(),False,adultMassKg=5,minGroup=8,maxGroup=60))
a(row('macaque','Macaque','primate','GENERIC_QUADRUPED','TERRESTRIAL',['TEMPERATE','SUBTROPICAL','TROPICAL'],['forest','mountain'],(),False,adultMassKg=10,minGroup=5,maxGroup=70))
a(row('arctic_fox','Arctic Fox','small_pred','CANID','TERRESTRIAL',['POLAR','BOREAL'],['tundra','cold','open'],['rabbit'],False,adultMassKg=4))
a(row('fennec_fox','Fennec Fox','small_pred','CANID','TERRESTRIAL',['ARID'],['desert','dry','open'],(),False,adultMassKg=1.3,movementKmPerDay=12))
a(row('raccoon','Raccoon','small_omni','GENERIC_QUADRUPED','TERRESTRIAL',['TEMPERATE','SUBTROPICAL'],['forest','river','settlement'],(),False,adultMassKg=8,minGroup=1,maxGroup=6))
a(row('porcupine','Porcupine','small_herb','RODENT','TERRESTRIAL',['TEMPERATE','TROPICAL','SUBTROPICAL'],['forest','grassland'],(),False,adultMassKg=12,defense=.72))
a(row('meerkat','Meerkat','small_omni','GENERIC_QUADRUPED','TERRESTRIAL',['ARID'],['desert','grassland','dry'],(),False,adultMassKg=.8,minGroup=5,maxGroup=40))
a(row('pronghorn','Pronghorn','medium_herb','UNGULATE','TERRESTRIAL',['TEMPERATE','ARID'],['grassland','open'],(),False,adultMassKg=50,movementKmPerDay=35,maxGroup=100))
a(row('okapi','Okapi','medium_herb','UNGULATE','TERRESTRIAL',['TROPICAL'],['forest','warm','humid'],(),False,adultMassKg=250,minGroup=1,maxGroup=4))
a(row('asian_elephant','Asian Elephant','mega_herb','PROBOSCIDEAN','TERRESTRIAL',['TROPICAL','SUBTROPICAL'],['forest','grassland','warm'],(),False,adultMassKg=4000,minGroup=3,maxGroup=30))
a(row('reticulated_python','Reticulated Python','small_pred','OTHER_REPTILE','TERRESTRIAL',['TROPICAL'],['forest','wetland','warm'],['rabbit'],True,adultMassKg=55,movementKmPerDay=3,aggression=.62))
a(row('rattlesnake','Rattlesnake','small_pred','OTHER_REPTILE','TERRESTRIAL',['ARID','TEMPERATE'],['desert','grassland','dry'],(),True,adultMassKg=2,movementKmPerDay=2,aggression=.58))
a(row('sea_snake','Sea Snake','small_pred','OTHER_REPTILE','AQUATIC',['AQUATIC','TROPICAL'],['ocean','reef','coast','aquatic'],(),True,adultMassKg=1.5,swimSpeedFactor=.85))
a(row('clownfish','Clownfish','fish','FISH','AQUATIC',['AQUATIC','TROPICAL'],['reef','ocean','warm','aquatic'],(),False,adultMassKg=.25,minGroup=2,maxGroup=40))
a(row('sardine','Sardine','fish','FISH','AQUATIC',['AQUATIC','TEMPERATE','SUBTROPICAL'],['ocean','coast','aquatic'],(),False,adultMassKg=.12,minGroup=100,maxGroup=5000))
a(row('anchovy','Anchovy','fish','FISH','AQUATIC',['AQUATIC','TEMPERATE','SUBTROPICAL'],['ocean','coast','aquatic'],(),False,adultMassKg=.05,minGroup=100,maxGroup=5000))
a(row('swordfish','Swordfish','fish_pred','FISH','AQUATIC',['AQUATIC','TEMPERATE','TROPICAL'],['ocean','pelagic','aquatic'],['sardine','anchovy'],False,adultMassKg=180,swimSpeedFactor=1.3))
a(row('moray_eel','Moray Eel','fish_pred','FISH','AQUATIC',['AQUATIC','TROPICAL'],['reef','coast','aquatic'],['clownfish'],False,adultMassKg=15,minGroup=1,maxGroup=3))
a(row('catfish','Catfish','fish','FISH','AQUATIC',['AQUATIC','TEMPERATE','SUBTROPICAL'],['river','freshwater','aquatic'],(),False,adultMassKg=12,minGroup=2,maxGroup=80))
a(row('sturgeon','Sturgeon','fish','FISH','AQUATIC',['AQUATIC','TEMPERATE','BOREAL'],['river','freshwater','coast','aquatic'],(),False,adultMassKg=90,minGroup=2,maxGroup=25))
a(row('pelican','Pelican','bird','BIRD','FLYING',['TEMPERATE','SUBTROPICAL','TROPICAL'],['coast','wetland','flying'],['sardine'],False,adultMassKg=6,swimSpeedFactor=.55,minGroup=3,maxGroup=60))
a(row('kingfisher','Kingfisher','bird','BIRD','FLYING',['TEMPERATE','TROPICAL'],['river','wetland','forest','flying'],['rainbow_trout'],False,adultMassKg=.12,minGroup=1,maxGroup=4,flightSpeedFactor=1.1))
a(row('wild_turkey','Wild Turkey','bird','BIRD','TERRESTRIAL',['TEMPERATE'],['forest','grassland'],(),False,adultMassKg=8,minGroup=3,maxGroup=30,flightSpeedFactor=0))
a(row('peacock','Indian Peafowl','bird','BIRD','TERRESTRIAL',['TROPICAL','SUBTROPICAL'],['forest','grassland','warm'],(),False,adultMassKg=5,minGroup=2,maxGroup=20,flightSpeedFactor=0))
a(row('snowy_owl','Snowy Owl','raptor','RAPTOR_BIRD','FLYING',['POLAR','BOREAL'],['tundra','cold','flying'],['rabbit'],False,adultMassKg=1.8,activityCycle='CATHEMERAL'))
a(row('blacktip_reef_shark','Blacktip Reef Shark','marine_pred','SHARK','AQUATIC',['AQUATIC','TROPICAL'],['reef','coast','warm','aquatic'],['clownfish','sardine'],False,adultMassKg=70,swimSpeedFactor=1.05))
a(row('lemon_shark','Lemon Shark','marine_pred','SHARK','AQUATIC',['AQUATIC','TROPICAL','SUBTROPICAL'],['coast','mangrove','aquatic'],['sardine'],True,adultMassKg=180,swimSpeedFactor=.95))

for d in S:
    if d['id'] in base: raise SystemExit(f'duplicate source id {d["id"]}')
    base[d['id']]=physical(d)

# Derive reverse predator links so the strict food-web validator can catch genuine mistakes.
for d in base.values(): d['predatorSpecies']=[]
for predator in base.values():
    for prey in predator.get('preySpecies',[]):
        if prey not in base: raise SystemExit(f'{predator["id"]}: unknown prey {prey}')
        base[prey]['predatorSpecies'].append(predator['id'])
for d in base.values():
    d['preySpecies']=sorted(set(d.get('preySpecies',[])))
    d['predatorSpecies']=sorted(set(d.get('predatorSpecies',[])))
    d['climates']=sorted(set(d['climates']))
    d['habitatTags']=sorted(set(d['habitatTags']))

# Remove stale generated files and write deterministic pretty JSON.
for p in OUT.glob('*.json'): p.unlink()
for i in sorted(base):
    (OUT/f'{i}.json').write_text(json.dumps(base[i],indent=2,sort_keys=False)+'\n')
print(f'generated {len(base)} species')
