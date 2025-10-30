# Nebula Hub
Mini-plateforme concurrente (Java) pour verrous, moniteurs, sémaphores FIFO, lecteurs-rédacteurs, prod/cons, rendez-vous, rate-limiter, scheduler à priorités, tests & benchs.

# Plan des packages 
nebula-hub/
└─ src/
   ├─ main/java/nebula/
   │  ├─ core/         # utilitaires: naming threads, métriques, horloge
   │  ├─ alloc/        # pools/allocateurs (direct, FIFO, priorités, multi-k)
   │  ├─ queue/        # files d’événements: moniteur puis Lock/Condition
   │  ├─ rw/           # lecteurs-rédacteurs: direct vs FIFO
   │  ├─ rate/         # "parking" (limiteur de débit) sémaphore/moniteur
   │  ├─ rendezvous/   # barrières / rendez-vous
   │  ├─ sched/        # scheduler à priorités (anti-starvation)
   │  ├─ server/       # (bonus) mini-serveur TCP
   │  └─ app/          # scénarios de démonstration (MainDemo)
   └─ test/java/nebula/
      ├─ core/
      ├─ alloc/
      ├─ queue/
      ├─ rw/
      ├─ rate/
      ├─ rendezvous/
      ├─ sched/
      ├─ server/
      └─ app/
