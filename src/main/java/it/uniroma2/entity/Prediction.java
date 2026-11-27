package it.uniroma2.entity;

/**
 * La predizione per una riga del test, quanto serve a NPofB20.
 *
 * @param probabilityBuggy probabilità di "yes" data dal classificatore
 * @param loc              LOC originali della classe (prima del log10)
 * @param buggy            etichetta vera
 */
public record Prediction(double probabilityBuggy, long loc, boolean buggy) {
}