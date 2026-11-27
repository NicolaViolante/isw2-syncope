package it.uniroma2.control;

import it.uniroma2.entity.RunResult;
import it.uniroma2.entity.ValidationKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import weka.core.Instances;

/**
 * 10 times 10-fold cross validation stratificata, come nella specifica della Milestone 2.
 *
 * <p>Ripetizione r (1–10): il dataset viene mescolato con seed r e stratificato (ogni fold mantiene la stessa
 * percentuale di righe buggy); ogni fold è a turno il test. Come in Weka, il training di ogni fold viene rimescolato,
 * qui con un seed che dipende da ripetizione e fold: i risultati sono identici a ogni esecuzione, anche in parallelo.
 */
public final class CrossValidationRunner {

    public static final int REPETITIONS = 10;
    public static final int FOLDS = 10;

    private final int threads;
    private final int repetitions;

    public CrossValidationRunner(int threads) {
        this(threads, REPETITIONS);
    }

    /** @param repetitions ripetizioni da eseguire (10 per i risultati; meno solo per stimare i tempi) */
    public CrossValidationRunner(int threads, int repetitions) {
        this.threads = threads;
        this.repetitions = repetitions;
    }

    public List<RunResult> run(Instances data, SplitTask task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<List<RunResult>>> futures = new ArrayList<>();
        try {
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                Instances shuffled = new Instances(data);
                shuffled.randomize(new Random(repetition));
                shuffled.stratify(FOLDS);
                for (int fold = 0; fold < FOLDS; fold++) {
                    final int r = repetition;
                    final int f = fold;
                    futures.add(executor.submit(() -> {
                        Instances train = shuffled.trainCV(FOLDS, f, new Random(100L * r + f));
                        Instances test = shuffled.testCV(FOLDS, f);
                        return task.evaluate(ValidationKind.CROSS_VALIDATION_10X10, r, f + 1, train, test);
                    }));
                }
            }
            List<RunResult> results = new ArrayList<>();
            for (Future<List<RunResult>> future : futures) {
                results.addAll(future.get());
            }
            results.sort(Comparator.comparingInt(RunResult::repetition).thenComparingInt(RunResult::fold));
            return results;
        } catch (ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        } finally {
            executor.shutdownNow();
        }
    }
}