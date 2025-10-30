# Nebula Hub — Atelier de Programmation Concurrente (Java)

Mini-plateforme pédagogique pour **réviser et visualiser** les concepts de concurrence en Java :
- **Allocateur de ressources FIFO** (multi-ressources, équitable strictement)
- **File d’événements bornée** via **Lock/Condition** (SCC) avec **mode équitable** optionnel
- **Limiteur de concurrence (Parking)** via **Semaphore fair**
- **Lecteurs–Rédacteurs équitable** (tourniquet fair + regroupement de lecteurs)
- **Dashboard Swing** pour **voir** le comportement (back-pressure, FIFO, batching lecteurs, etc.)

> Cible : Java 21, Gradle (wrapper fourni). Pas de dépendances externes pour l’UI.

---

## Lancer l’application (Dashboard)

```bash
# Cloner
git clone <URL_DU_REPO> && cd nebula-hub

# Lancer l'UI (Swing)
./gradlew run

L’UI s’ouvre avec 4 onglets :

Alloc FIFO — Montre l’ordre de service strictement FIFO malgré des demandes de tailles différentes.

Queue SCC — File bornée (prod/cons) avec back-pressure ; coche Fair lock pour réduire le “barge-in”.

Parking — Limite de concurrence (max N tâches actives).

R/W Fair — Lecteurs en parallèle quand possible, Writer exclusif et pas de famine.

Chaque onglet propose Start/Stop, des compteurs et un journal en temps réel.

#Tests 
./gradlew clean test

Allocateur FIFO : capacité, ordre FIFO avec demandes multi-k

Queue SCC : blocage quand plein/vide, reprise après signal

Parking : concurrence max respectée, tryEnter/timeout

Lecteurs–Rédacteurs Fair : lecteurs en parallèle, writer exclusif, anti-famine


##Structure du code
src/
├─ main/java/nebula/
│  ├─ alloc/
│  │  ├─ ResourcePool.java              # interface
│  │  └─ ResourcePoolFifo.java          # moniteur + file FIFO stricte
│  ├─ queue/
│  │  ├─ EventQueue.java                # interface
│  │  └─ EventQueueSCC.java             # Lock/Condition (+ fair option)
│  ├─ rate/
│  │  └─ Parking.java                   # Semaphore fair (limiteur de concurrence)
│  ├─ rw/
│  │  ├─ RW.java                        # interface
│  │  └─ RWFair.java                    # tourniquet fair + roomEmpty
│  └─ ui/
│     └─ DashboardApp.java              # UI Swing (4 onglets)
└─ test/java/nebula/
   ├─ alloc/ ResourcePoolFifoTest.java
   ├─ queue/ EventQueueSCCTest.java, EventQueueFairOptionTest.java
   ├─ rate/  ParkingTest.java
   └─ rw/    RWFairTest.java



