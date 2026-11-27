package it.uniroma2.boundary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import weka.core.Instances;
import weka.core.converters.ArffSaver;

/** Scrive i dati di Weka in formato ARFF, per esempio per aprirli nell'Explorer. */
public final class WekaDataWriter {

    private WekaDataWriter() {
    }

    public static void writeArff(Instances data, Path file) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        ArffSaver saver = new ArffSaver();
        saver.setInstances(data);
        saver.setFile(file.toFile());
        saver.writeBatch();
    }
}