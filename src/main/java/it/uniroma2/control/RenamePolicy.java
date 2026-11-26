package it.uniroma2.control;

/**
 * Quando una rinomina riconosciuta da Git viene accettata come "stessa classe".
 *
 * <p>Git deduce le rinomine dalla somiglianza dei contenuti. In Syncope molti file sono corti (TO, enum, piccole
 * interfacce) e condividono circa 18 righe di licenza Apache più import e getter ripetitivi: classi diverse
 * superano facilmente la soglia (es. UserRequestDAO → SecurityQuestionDAO). Per questo accettiamo solo gli
 * spostamenti in cui il nome del file, cioè il nome della classe, non cambia; con un nome diverso la classe
 * vecchia finisce e ne nasce una nuova. Regola usata sia nell'inventario (Fase 3) sia nella storia dei commit
 * (Fase 4).
 */
public final class RenamePolicy {

    private RenamePolicy() {
    }

    public static boolean accepts(String oldPath, String newPath) {
        return fileName(oldPath).equals(fileName(newPath));
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}