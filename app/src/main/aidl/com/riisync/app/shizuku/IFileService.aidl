// AIDL - interfaccia del servizio privilegiato eseguito tramite Shizuku (uid shell)
package com.riisync.app.shizuku;

import com.riisync.app.shizuku.IFileProgressCallback;

interface IFileService {

    // Crea un symlink: linkPath -> targetPath. Ritorna un messaggio di esito
    // (vuoto = successo, altrimenti testo errore).
    String createSymlink(String targetPath, String linkPath) = 1;

    // Rimuove un symlink o file/cartella (usato per pulizia / re-link)
    String remove(String path) = 2;

    // Fallback: copia ricorsiva target -> dest (usato se il symlink non è supportato)
    String copyRecursive(String targetPath, String destPath, IFileProgressCallback callback) = 3;

    // Sincronizzazione intelligente: copia solo i file modificati o mancanti
    String syncIncremental(String targetPath, String destPath, IFileProgressCallback callback) = 6;

    // Pulisce il contenuto di una cartella senza eliminarla
    String deleteDirectoryContent(String path) = 7;

    // Verifica se un path esiste ed è un symlink
    boolean isSymlink(String path) = 4;

    // Elenca i file in una directory (debug / verifica percorsi)
    String[] list(String path) = 5;

    // Verifica se un path esiste
    boolean exists(String path) = 8;

    // Chiude il processo del servizio
    void destroy() = 16777114; // codice speciale richiesto da Shizuku per la destroy
}
