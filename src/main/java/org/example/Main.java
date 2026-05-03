package org.example;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.Scanner;
public class Main {


    public static void main(String[] args) throws IOException {
        String directoryPath;

        if (args.length > 0) {
            // If user passed argument normally
            directoryPath = args[0];
        } else {
            // Ask user to input path manually
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter path to Java project directory: ");
            directoryPath = scanner.nextLine();
        }

        Path root = Paths.get(directoryPath);

        if (!Files.exists(root)) {
            System.out.println("Invalid directory path!");
            return;
        }

        List<Path> files = new ArrayList<>();

        Files.walk(root)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(files::add);

        double totalMI = 0;
        int fileCount = 0;

        for (Path file : files) {
            CompilationUnit cu = StaticJavaParser.parse(file);

            long loc = computeLOC(cu);
            int cc = computeCyclomaticComplexity(cu);
            HalsteadResult halstead = computeHalstead(cu);

            double mi = computeMI(halstead.volume, cc, (int) loc);

            System.out.printf("File: %s%n", file);
            System.out.printf("  LOC: %d, CC: %d, HV: %.2f, MI: %.2f%n%n",
                    loc, cc, halstead.volume, mi);

            totalMI += mi;
            fileCount++;
        }

        if (fileCount > 0) {
            System.out.printf("Average MI: %.2f%n", totalMI / fileCount);
        }
    }


    // -------------------------------
    // Maintainability Index
    // -------------------------------
    static double computeMI(double hv, int cc, int loc) {
        if (hv <= 0 || loc <= 0) return 0;

        double mi = 171 - 5.2 * Math.log(hv)
                - 0.23 * cc
                - 16.2 * Math.log(loc);

        return Math.max(0, mi * 100 / 171);
    }

    // -------------------------------
    // LOC (clean)
    // -------------------------------
    static long computeLOC(CompilationUnit cu) throws IOException {
        return cu.toString()
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("//.*", "")
                .lines()
                .filter(line -> !line.trim().isEmpty())
                .count();
    }

    // -------------------------------
    // Cyclomatic Complexity (AST)
    // -------------------------------
    static int computeCyclomaticComplexity(CompilationUnit cu) {
        int complexity = 1;

        complexity += cu.findAll(IfStmt.class).size();
        complexity += cu.findAll(ForStmt.class).size();
        complexity += cu.findAll(ForEachStmt.class).size();
        complexity += cu.findAll(WhileStmt.class).size();
        complexity += cu.findAll(DoStmt.class).size();
        complexity += cu.findAll(CatchClause.class).size();
        complexity += cu.findAll(SwitchEntry.class).size();
        complexity += cu.findAll(ConditionalExpr.class).size();

        // Count boolean operators
        complexity += cu.findAll(BinaryExpr.class).stream()
                .filter(b -> b.getOperator() == BinaryExpr.Operator.AND
                        || b.getOperator() == BinaryExpr.Operator.OR)
                .count();

        return complexity;
    }

    // -------------------------------
    // Halstead Metrics (AST-based)
    // -------------------------------
    static class HalsteadResult {
        int n1, n2, N1, N2;
        double volume;
    }

    static HalsteadResult computeHalstead(CompilationUnit cu) {
        Set<String> distinctOperators = new HashSet<>();
        Set<String> distinctOperands = new HashSet<>();

        List<String> operators = new ArrayList<>();
        List<String> operands = new ArrayList<>();

        cu.walk(node -> {

            // Operators
            if (node instanceof BinaryExpr) {
                String op = ((BinaryExpr) node).getOperator().asString();
                operators.add(op);
                distinctOperators.add(op);
            }

            else if (node instanceof UnaryExpr) {
                String op = ((UnaryExpr) node).getOperator().asString();
                operators.add(op);
                distinctOperators.add(op);
            }

            else if (node instanceof AssignExpr) {
                String op = ((AssignExpr) node).getOperator().asString();
                operators.add(op);
                distinctOperators.add(op);
            }

            // Operands
            else if (node instanceof NameExpr) {
                String name = ((NameExpr) node).getNameAsString();
                operands.add(name);
                distinctOperands.add(name);
            }

            else if (node instanceof LiteralExpr) {
                String val = node.toString();
                operands.add(val);
                distinctOperands.add(val);
            }
        });

        HalsteadResult result = new HalsteadResult();

        result.n1 = distinctOperators.size();
        result.n2 = distinctOperands.size();
        result.N1 = operators.size();
        result.N2 = operands.size();

        int n = result.n1 + result.n2;
        int N = result.N1 + result.N2;

        result.volume = (n == 0) ? 0 : N * (Math.log(n) / Math.log(2));

        return result;
    }
}