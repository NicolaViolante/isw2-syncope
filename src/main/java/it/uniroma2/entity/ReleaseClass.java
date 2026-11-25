package it.uniroma2.entity;

/**
 * Una classe Java di produzione presente in una release: è una riga del dataset.
 *
 * @param releaseId ID della release nella linea principale (1, 2, 3…)
 * @param path      percorso completo del file nella release (es. "core/src/main/java/…/UserService.java")
 * @param classId   identità stabile della classe: il percorso con cui è comparsa la prima volta,
 *                  mantenuto anche quando il file viene spostato o rinominato
 */
public record ReleaseClass(int releaseId, String path, String classId) {
}