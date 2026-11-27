package it.uniroma2.boundary;

import it.uniroma2.entity.BalancingKind;
import it.uniroma2.entity.ClassifierKind;
import it.uniroma2.entity.EvaluationPhase;
import it.uniroma2.entity.FeatureSelectionKind;
import it.uniroma2.entity.ModelMetrics;
import it.uniroma2.entity.Recipe;
import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Rilegge le run scritte da {@link ResultsCsvWriter#writeRuns}, per disegnare i grafici senza rifare l'esperimento. */
public final class ResultsCsvReader {

    private static final int COLUMNS = 24;

    private ResultsCsvReader() {
    }

    public static List<RunResult> readRuns(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<RunResult> runs = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] f = line.split(",", -1);
            if (f.length != COLUMNS) {
                throw new IOException("Riga con " + f.length + " colonne invece di " + COLUMNS + ": " + line);
            }
            Recipe recipe = new Recipe(byLabel(FeatureSelectionKind.values(), FeatureSelectionKind::label, f[5]),
                    byLabel(BalancingKind.values(), BalancingKind::label, f[6]));
            ModelMetrics metrics = new ModelMetrics(Double.parseDouble(f[11]), Double.parseDouble(f[12]),
                    Double.parseDouble(f[13]), Double.parseDouble(f[14]), Double.parseDouble(f[15]),
                    Double.parseDouble(f[16]), Double.parseDouble(f[17]), Integer.parseInt(f[18]),
                    Integer.parseInt(f[19]), Integer.parseInt(f[20]), Integer.parseInt(f[21]));
            List<String> features = f[23].isEmpty() ? List.of() : Arrays.asList(f[23].split(";"));
            runs.add(new RunResult(byLabel(ValidationKind.values(), ValidationKind::label, f[0]),
                    Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                    byLabel(EvaluationPhase.values(), EvaluationPhase::label, f[3]),
                    byLabel(ClassifierKind.values(), ClassifierKind::label, f[4]), recipe, Integer.parseInt(f[7]),
                    Integer.parseInt(f[8]), features, "yes".equals(f[9]), "yes".equals(f[10]), metrics));
        }
        return runs;
    }

    private static <E> E byLabel(E[] values, Function<E, String> label, String text) throws IOException {
        for (E value : values) {
            if (label.apply(value).equals(text)) {
                return value;
            }
        }
        throw new IOException("Valore sconosciuto: " + text);
    }
}