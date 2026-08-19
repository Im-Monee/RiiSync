# Implementazione Avanzata Git e Miglioramento UI

Questo piano prevede l'aggiunta del monitoraggio del progresso per le operazioni Git, la visualizzazione della cronologia dei commit, e l'introduzione di una schermata impostazioni con supporto al tema scuro.

## Proposte di Modifica

### 1. Monitoraggio Progresso Git
Modificheremo `GitManager` per accettare un `ProgressMonitor` di JGit. Implementeremo un monitor personalizzato che aggiorna lo stato nella UI in tempo reale.

#### [MODIFY] [GitManager.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/git/GitManager.kt)
- Aggiunta di un parametro opzionale `progressMonitor` ai metodi `clone`, `pull` e `push`.
- Utilizzo del monitor nelle operazioni JGit.

#### [MODIFY] [GitScreen.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/ui/GitScreen.kt)
- Aggiunta di variabili di stato per la percentuale e il file corrente.
- Implementazione di un `ProgressMonitor` che aggiorna queste variabili tramite callback.
- Visualizzazione del progresso tramite una `LinearProgressIndicator` e un testo descrittivo.

### 2. Cronologia Commit
Creeremo una nuova schermata per visualizzare i log del repository.

#### [MODIFY] [GitManager.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/git/GitManager.kt)
- Nuovo metodo `getCommitHistory(localDir: File)` che restituisce una lista di oggetti commit (hash, autore, messaggio, data).

#### [NEW] [HistoryScreen.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/ui/HistoryScreen.kt)
- Schermata che visualizza la lista dei commit in una `LazyColumn`.

### 3. Schermata Impostazioni e Tema Scuro
Gestiremo la preferenza del tema e miglioreremo l'estetica generale.

#### [NEW] [SettingsManager.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/utils/SettingsManager.kt)
- Classe per salvare/caricare le impostazioni (es. tema scuro) tramite `SharedPreferences`.

#### [NEW] [SettingsScreen.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/ui/SettingsScreen.kt)
- Interfaccia per attivare il tema scuro e altre opzioni future.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/Mone/Downloads/RiivSync/app/src/main/java/com/riivsync/app/MainActivity.kt)
- Integrazione della navigazione per includere le nuove schermate.
- Aggiornamento del tema basato sulle impostazioni salvate.

## Piano di Verifica

### Test Automatici
- Non previsti in questa fase, ci affideremo alla verifica manuale.

### Verifica Manuale
1. **Progresso**: Avviare un clone di un repo corposo e verificare che la percentuale e i file vengano visualizzati correttamente.
2. **Cronologia**: Aprire la nuova tab/schermata History e verificare che i commit appaiano in ordine cronologico.
3. **Tema**: Cambiare il tema nelle impostazioni e verificare che l'app si aggiorni istantaneamente (o al riavvio).
