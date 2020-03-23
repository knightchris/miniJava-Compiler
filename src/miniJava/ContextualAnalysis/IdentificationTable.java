package miniJava.ContextualAnalysis;

import java.util.*;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenKind;


/* Scopes:
 * 0 Predefined Names (separated from class names to assist stack indexing)
 * 1 Class Names
 * 2 Member names within a class
 * 3 Parameter names within a method
 * 4+ Local variable names in successively nested scopes within a method
 */

public class IdentificationTable {
	
	public ErrorReporter reporter;
	public Stack<HashMap<String, Declaration>> table = new Stack<HashMap<String, Declaration>>();
	public HashMap<String, Declaration> classes = new HashMap<String, Declaration>();
	public HashMap<String, HashMap<String, Declaration>> classFields = new HashMap<String, HashMap<String, Declaration>>();
	public HashMap<String, HashMap<String, Declaration>> classMethods = new HashMap<String, HashMap<String, Declaration>>();
	public String currentClass = null;
	
	
	public IdentificationTable(ErrorReporter reporter) {
		this.reporter = reporter;
		openScope();
		
		// Predefined names SCOPE 0 
		// class String { }
		ClassDecl stringClassDecl = new ClassDecl("String", new FieldDeclList(), new MethodDeclList(), null);
		stringClassDecl.type = new BaseType(TypeKind.UNSUPPORTED, null);
		enter("String", stringClassDecl);
		classes.put("String", stringClassDecl);
		
		// class _PrintStream { public void println(int n){}; }
		MemberDecl printlnField = new FieldDecl(false, false, new BaseType(TypeKind.VOID, null), "println", null);
		ParameterDecl printlnParameterN = new ParameterDecl(new BaseType(TypeKind.INT, null), "n", null);
		ParameterDeclList printlnParameters = new ParameterDeclList();
		printlnParameters.add(printlnParameterN);
		MethodDecl printlnMethod = new MethodDecl(printlnField, printlnParameters, new StatementList(), null);
		MethodDeclList _PrintStreamMethods = new MethodDeclList();
		_PrintStreamMethods.add(printlnMethod);
		ClassDecl _PrintStreamClassDecl = new ClassDecl("_PrintStream", new FieldDeclList(), _PrintStreamMethods, null);
		Identifier _PrintStreamId = new Identifier(new Token(TokenKind.ID, "_PrintStream"), null);
		_PrintStreamClassDecl.type = new ClassType(_PrintStreamId, null);
		enter("_PrintStream", _PrintStreamClassDecl);
		classes.put("_PrintStream", _PrintStreamClassDecl);
		classMethods.put("_PrintStream", new HashMap<String,Declaration>());
		classMethods.get("_PrintStream").put("println", printlnMethod);
		
		// class System { public static _PrintStream out; }
		FieldDecl outField = new FieldDecl(false, true, new ClassType(_PrintStreamId, null), "out", null);
		FieldDeclList systemFields = new FieldDeclList();
		systemFields.add(outField);
		Identifier systemId = new Identifier(new Token(TokenKind.ID, "System"), null);
		ClassDecl systemClassDecl = new ClassDecl("System", systemFields, new MethodDeclList(), null);
		systemClassDecl.type = new ClassType(systemId, null);
		enter("System", systemClassDecl);
		classes.put("System", systemClassDecl);
		classFields.put("System", new HashMap<String,Declaration>());
		classFields.get("System").put("out", outField);
		
		
		
	
		
	}
	
	public void enter(String s, Declaration d) {
		if (declaredInCurrentScope(s)) {
			reporter.reportError("*** line " + d.posn.getLine() + ": " + "column " + d.posn.getCol() + " " + d.name + " name already defined in current scope");
			System.exit(4);
		} else if (declaredInUnhideableScope(s)) {
			reporter.reportError("*** line " + d.posn.getLine() + ": " + "column " + d.posn.getCol() + " " + d.name + " name attempts to hide variable in unhideable scope" );
			System.exit(4);
		} else {
			table.peek().put(s, d);
		}
	}
	
	public Declaration retrieve(String s) {
		if (currentClass == null) {
			int scopeLevel = getHighestScopeOccurence(s);
			if (scopeLevel == 0 && classes.containsKey(s)) {   
				return classes.get(s);
			} else if (table.get(scopeLevel).containsKey(s)){
				return table.get(scopeLevel).get(s);
			} else {
				for (String cn: classes.keySet()) {
					if (s.equals(cn)) {
						return classes.get(cn);
					} else if (classMethods.get(cn) != null && classMethods.get(cn).containsKey(s)) {
						return classMethods.get(cn).get(s);
					} else if (classFields.get(cn) != null && classFields.get(cn).containsKey(s)) {
						return classFields.get(cn).get(s);
					}
				}
			}
			return null;
		} else { // only look in current class
			if (classMethods.get(currentClass) != null && classMethods.get(currentClass).containsKey(s)) {
				return classMethods.get(currentClass).get(s);
			} else if (classFields.get(currentClass) != null && classFields.get(currentClass).containsKey(s)) {
				return classFields.get(currentClass).get(s);
			} else {
				return null; 
			}
		}
	}
	
	public int getHighestScopeOccurence(String s) {
		for (int i = table.size()-1; i >= 0; i--) {     
			if (table.get(i).containsKey(s)) {
				return i;
			}
		}
		for (String cn: classes.keySet()) {   // hasn't been entered into scoped id table, check class fields/methods
			if (s.equals(cn)) {
				return 1;
			} else if ((classFields.containsKey(cn) && classFields.get(cn).containsKey(s)) 
					|| ((classMethods.containsKey(cn) && classMethods.get(cn).containsKey(s)))) {
				return 2;
			}
		}
		
		return 0; 
	}
	
	public int scopeLevel() {
		return table.size() - 1;
	}
	
	public void setCurrentClass(String s) {
		currentClass = s;
	}
	
	public void resetCurrentClass() {
		currentClass = null;
	}
	
	public Declaration retrieveClass(String s) {
		if (classes.containsKey(s)) {
			return classes.get(s);
		}
		return null;
	}
	
	public Declaration retrieveClassField(String className, String fieldName) {
		if (classes.containsKey(className)) {
			if (classFields.get(className).containsKey(fieldName)) {
				return classFields.get(className).get(fieldName);
			}
		}
		return null;
	}
	
	public Declaration retrieveClassMethod(String className, String methodName) {
		if (classes.containsKey(className)) {
			if (classMethods.get(className).containsKey(methodName)) {
				return classMethods.get(className).get(methodName);
			}
		}
		return null;
	}
	
	public void openScope() {
		table.push(new HashMap<String, Declaration>());
	}
	
	public void closeScope() {
		table.pop();
	}
	
	public boolean declaredInUnhideableScope(String s) {
		for (int i = 3; i < table.size(); i++) {
			if (table.get(i).containsKey(s)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean declaredInCurrentScope(String s) {
		return table.peek().containsKey(s);
	}
}
