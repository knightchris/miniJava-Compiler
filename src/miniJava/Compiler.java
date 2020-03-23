package miniJava;

import java.io.FileInputStream; // may want filereader
import java.io.FileNotFoundException;
import java.io.InputStream;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.ContextualAnalysis.Identification;
import miniJava.ContextualAnalysis.TypeChecker;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;

public class Compiler {

	public static void main(String[] args) {
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(args[0]);
		} catch (FileNotFoundException e) {
			System.out.println("Input file " + args[0] + " not found");
			System.exit(3);
		}
		
		ErrorReporter errorReporter = new ErrorReporter();
		Scanner scanner = new Scanner(inputStream, errorReporter);
		Parser parser = new Parser(scanner, errorReporter);
		Identification identifier = new Identification(errorReporter);
		TypeChecker typeChecker = new TypeChecker(errorReporter);
		
		System.out.println("Syntactic analysis ... ");
		AST ast = parser.parse();
		identifier.beginIdentification(ast);
		typeChecker.beginTypeChecking(ast);
		
		if (errorReporter.hasErrors()) {
			//System.out.println("Invalid miniJava program");
			System.exit(4);
		} else {
			//System.out.println("Valid miniJava program");
			//ASTDisplay display = new ASTDisplay();
			//display.showTree(ast);
			System.exit(0);
		}
	}
}
