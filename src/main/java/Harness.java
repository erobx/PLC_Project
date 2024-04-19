// Calls Lexer -> Parser -> Interpreter -> Analyzer -> Generator

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import plc.project.*;

/**
 *
 * Place this file in the same directory as
 *  -> plc directory
 *  -> harness.py
 *  -> source1.plc
 *  -> source2.plc
 *
 *  at the terminal, compile Harness.java
 *    -> javac Harness.java
 */

public class Harness {
    public static void main(String[] args) {
        String source_file = args[0];
        String java_file = new String("Main.java");
        String source = new String();
        Scanner scanner;
        String javac = new String("javac Main.java");
        String java = new String("java Main");

        System.out.println(" -> Generating Java source from " + source_file);

        try {
            File file = new File(source_file);
            scanner = new Scanner(file);

            StringBuilder source_contents = new StringBuilder((int) file.length());

            while (scanner.hasNextLine()) {
                source_contents.append(scanner.nextLine() + System.lineSeparator());
            }
            source = source_contents.toString();
            scanner.close();
        } catch (IOException ioe) {}

        Lexer lexer = new Lexer(source);
        System.out.println(" -> Lexing Complete");

        Parser parser = new Parser(lexer.lex());
        Ast ast = parser.parseSource();
        System.out.println(" -> Parsing Complete");

        Analyzer analyzer = new Analyzer(null);
        analyzer.visit(ast);
        System.out.println(" -> Analyzer Complete");

        try {
            FileWriter fw = new FileWriter(java_file);
            PrintWriter pw = new PrintWriter(fw);

            Generator generator = new Generator(pw);
            generator.visit(ast);
            pw.flush();
            pw.close();

            System.out.println(" -> Generating Complete");
        } catch (IOException ioe) {}

        System.out.println();
    }
}
