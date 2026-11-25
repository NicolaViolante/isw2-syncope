package it.uniroma2.control;

/**
 * Regola della Fase 3: quali file sono classi Java di produzione.
 *
 * <p>Sono di produzione i file .java che stanno in una cartella {@code src/main/java} (convenzione
 * Maven per il codice di produzione). Restano quindi esclusi i test ({@code src/test/java}).
 * Si escludono esplicitamente i modelli di codice degli archetipi Maven, che stanno sotto
 * {@code src/main/resources} pur avendo a loro volta un percorso {@code src/main/java}.
 * Si escludono anche {@code package-info.java} e {@code module-info.java}, che non contengono classi.
 */
public final class ProductionFiles {

    private static final String PRODUCTION_DIR = "src/main/java/";
    private static final String RESOURCES_DIR = "src/main/resources/";
    private static final String JAVA_EXTENSION = ".java";

    private ProductionFiles() {
    }

    public static boolean isProduction(String path) {
        if (!path.endsWith(JAVA_EXTENSION) || !path.contains(PRODUCTION_DIR) || path.contains(RESOURCES_DIR)) {
            return false;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return !fileName.equals("package-info.java") && !fileName.equals("module-info.java");
    }

    /** Il modulo Maven della classe: la parte di percorso prima di "src/main/java" (es. "core", "client/lib"). */
    public static String module(String path) {
        int idx = path.indexOf(PRODUCTION_DIR);
        if (idx <= 0) {
            return "(root)";
        }
        return path.substring(0, idx - 1);
    }
}