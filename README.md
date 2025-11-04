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
# 1) Cloner
git clone <URL_DU_REPO>
cd nebula-hub

# 2) Lancer l'interface Swing
./gradlew run

L’UI s’ouvre avec 4 onglets :

Alloc FIFO — Montre l’ordre de service strictement FIFO malgré des demandes de tailles différentes.

Queue SCC — File bornée (prod/cons) avec back-pressure ; coche Fair lock pour réduire le “barge-in”.

Parking — Limite de concurrence (max N tâches actives).

R/W Fair — Lecteurs en parallèle quand possible, Writer exclusif et pas de famine.

Chaque onglet propose Start/Stop, des compteurs et un journal en temps réel.

#Tests 
# Toutes les démos enchaînées
./gradlew runAll

# Une démo précise
./gradlew runFifo
./gradlew runQueue
./gradlew runParking
./gradlew runRW
./gradlew runDirect

#Tests 
./gradlew clean test

Allocateur FIFO : capacité, ordre FIFO avec demandes multi-k

Queue SCC : blocage quand plein/vide, reprise après signal

Parking : concurrence max respectée, tryEnter/timeout

Lecteurs–Rédacteurs Fair : lecteurs en parallèle, writer exclusif, anti-famine


##Structure du code
src/main/java/nebula/
├── alloc/      (ResourcePool, FIFO, Direct)
├── app/        (DemoFifo, DemoQueueSCC, DemoParking, DemoRWFair, Demos)
├── core/       (Naming, Metrics)
├── queue/      (EventQueue, Monitor, SCC)
├── rate/       (Parking)
├── rendezvous/ (RendezVous)
├── rw/         (RWDirect, RWFair)
└── ui/         (MainFrame, panneaux Swing)


#Exigences & notes

Java 21 requis (le wrapper Gradle configure la toolchain).

L’UI est standalone (Swing pur), sans dépendance externe.

Les métriques de file/permits sont instantanées (lecture approximative sous contention).

Les implémentations sont équité-aware : Semaphore(fair), tourniquet readers/writers, FIFO stricte côté allocateur.


