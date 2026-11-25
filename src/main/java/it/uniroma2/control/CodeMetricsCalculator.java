package it.uniroma2.control;

import com.github.javaparser.JavaParser;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import it.uniroma2.entity.CodeMetrics;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcola le metriche di codice di una classe con JavaParser, sul testo del file nello snapshot.
 *
 * <ul>
 *   <li>LOC: righe che contengono codice (escluse righe vuote e di solo commento);</li>
 *   <li>NOM: metodi + costruttori di tutti i tipi del file, interni e anonimi compresi;</li>
 *   <li>CyclomaticComplexity: somma di McCabe sugli stessi metodi e costruttori (1 per ciascuno + un punto
 *       per ogni if, for, foreach, while, do, case non default, catch, ?:, &amp;&amp;, ||), con ogni punto di
 *       decisione contato una sola volta;</li>
 *   <li>FanOut: tipi distinti usati nel codice, esclusi i tipi dichiarati nel file, i parametri generici e
 *       i tipi di java.lang (a meno che un tipo omonimo sia importato esplicitamente).</li>
 * </ul>
 */
public final class CodeMetricsCalculator {

    /** I tipi pubblici di java.lang, usabili senza import. */
    private static final Set<String> JAVA_LANG = Set.of(
            "Object", "String", "StringBuilder", "StringBuffer", "CharSequence", "Character", "Boolean", "Byte",
            "Short", "Integer", "Long", "Float", "Double", "Number", "Void", "Math", "StrictMath", "System",
            "Runtime", "Process", "ProcessBuilder", "Thread", "ThreadGroup", "ThreadLocal",
            "InheritableThreadLocal", "Runnable", "Class", "ClassLoader", "Package", "Enum", "Iterable",
            "Comparable", "Cloneable", "AutoCloseable", "Appendable", "Readable", "StackTraceElement",
            "SecurityManager", "Compiler", "ClassValue", "Override", "Deprecated", "SuppressWarnings",
            "SafeVarargs", "FunctionalInterface", "Throwable", "Exception", "Error", "RuntimeException",
            "ArithmeticException", "ArrayIndexOutOfBoundsException", "ArrayStoreException", "ClassCastException",
            "ClassNotFoundException", "CloneNotSupportedException", "EnumConstantNotPresentException",
            "IllegalAccessException", "IllegalArgumentException", "IllegalMonitorStateException",
            "IllegalStateException", "IllegalThreadStateException", "IndexOutOfBoundsException",
            "InstantiationException", "InterruptedException", "NegativeArraySizeException",
            "NoSuchFieldException", "NoSuchMethodException", "NullPointerException", "NumberFormatException",
            "ReflectiveOperationException", "SecurityException", "StringIndexOutOfBoundsException",
            "TypeNotPresentException", "UnsupportedOperationException", "AbstractMethodError", "AssertionError",
            "BootstrapMethodError", "ClassCircularityError", "ClassFormatError", "ExceptionInInitializerError",
            "IllegalAccessError", "IncompatibleClassChangeError", "InstantiationError", "InternalError",
            "LinkageError", "NoClassDefFoundError", "NoSuchFieldError", "NoSuchMethodError", "OutOfMemoryError",
            "StackOverflowError", "UnknownError", "UnsatisfiedLinkError", "UnsupportedClassVersionError",
            "VerifyError", "VirtualMachineError");

    private final JavaParser parser;

    public CodeMetricsCalculator() {
        // Syncope 1.x è scritto in Java 6/7: il livello Java 8 legge quel codice senza ambiguità
        this.parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8));
    }

    /** Le metriche del file; vuoto se JavaParser non riesce a leggerlo. */
    public Optional<CodeMetrics> compute(String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            return Optional.empty();
        }
        CompilationUnit cu = result.getResult().get();
        int nom = cu.findAll(MethodDeclaration.class).size() + cu.findAll(ConstructorDeclaration.class).size();
        return Optional.of(new CodeMetrics(loc(cu), nom, nom + decisionPoints(cu), fanOut(cu)));
    }

    /** Righe su cui compare almeno un token che non sia spazio o commento. */
    static int loc(CompilationUnit cu) {
        Set<Integer> lines = new HashSet<>();
        cu.getTokenRange().ifPresent(tokens -> {
            for (JavaToken t : tokens) {
                if (t.getCategory().isWhitespaceOrComment() || t.getKind() == JavaToken.Kind.EOF.getKind()) {
                    continue;
                }
                Optional<Range> r = t.getRange();
                if (r.isPresent()) {
                    for (int line = r.get().begin.line; line <= r.get().end.line; line++) {
                        lines.add(line);
                    }
                }
            }
        });
        return lines.size();
    }

    /** Punti di decisione che stanno dentro un metodo o un costruttore, ognuno contato una volta. */
    static int decisionPoints(CompilationUnit cu) {
        int count = 0;
        for (Class<? extends Node> kind : List.of(IfStmt.class, ForStmt.class, ForEachStmt.class,
                WhileStmt.class, DoStmt.class, CatchClause.class, ConditionalExpr.class)) {
            count += (int) cu.findAll(kind).stream().filter(CodeMetricsCalculator::insideCallable).count();
        }
        count += (int) cu.findAll(SwitchEntry.class).stream()
                .filter(e -> !e.getLabels().isEmpty()) // il ramo default non ha etichette
                .filter(CodeMetricsCalculator::insideCallable).count();
        count += (int) cu.findAll(BinaryExpr.class).stream()
                .filter(e -> e.getOperator() == BinaryExpr.Operator.AND || e.getOperator() == BinaryExpr.Operator.OR)
                .filter(CodeMetricsCalculator::insideCallable).count();
        return count;
    }

    private static boolean insideCallable(Node node) {
        return node.findAncestor(MethodDeclaration.class).isPresent()
                || node.findAncestor(ConstructorDeclaration.class).isPresent();
    }

    /** Tipi distinti usati nel codice, con le esclusioni descritte nella classe. */
    static int fanOut(CompilationUnit cu) {
        Set<String> ownTypes = cu.findAll(TypeDeclaration.class).stream()
                .map(TypeDeclaration::getNameAsString).collect(Collectors.toSet());
        Set<String> typeParameters = cu.findAll(TypeParameter.class).stream()
                .map(TypeParameter::getNameAsString).collect(Collectors.toSet());
        Set<String> explicitImports = cu.getImports().stream()
                .filter(i -> !i.isAsterisk() && !i.isStatic())
                .map(ImportDeclaration::getName).map(n -> n.getIdentifier()).collect(Collectors.toSet());
        Set<String> used = new HashSet<>();
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if (!Character.isUpperCase(name.charAt(0))) {
                continue; // segmento di package in un nome qualificato (es. "java" in java.util.List)
            }
            if (ownTypes.contains(name) || typeParameters.contains(name)) {
                continue;
            }
            if (JAVA_LANG.contains(name) && !explicitImports.contains(name)) {
                continue;
            }
            used.add(name);
        }
        return used.size();
    }
}