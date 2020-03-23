package miniJava.ContextualAnalysis;

import java.util.HashMap;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.AST;
import miniJava.AbstractSyntaxTrees.ArrayType;
import miniJava.AbstractSyntaxTrees.AssignStmt;
import miniJava.AbstractSyntaxTrees.BaseType;
import miniJava.AbstractSyntaxTrees.BinaryExpr;
import miniJava.AbstractSyntaxTrees.BlockStmt;
import miniJava.AbstractSyntaxTrees.BooleanLiteral;
import miniJava.AbstractSyntaxTrees.CallExpr;
import miniJava.AbstractSyntaxTrees.CallStmt;
import miniJava.AbstractSyntaxTrees.ClassDecl;
import miniJava.AbstractSyntaxTrees.ClassType;
import miniJava.AbstractSyntaxTrees.FieldDecl;
import miniJava.AbstractSyntaxTrees.IdRef;
import miniJava.AbstractSyntaxTrees.Identifier;
import miniJava.AbstractSyntaxTrees.IfStmt;
import miniJava.AbstractSyntaxTrees.IntLiteral;
import miniJava.AbstractSyntaxTrees.IxAssignStmt;
import miniJava.AbstractSyntaxTrees.IxExpr;
import miniJava.AbstractSyntaxTrees.LiteralExpr;
import miniJava.AbstractSyntaxTrees.MethodDecl;
import miniJava.AbstractSyntaxTrees.NewArrayExpr;
import miniJava.AbstractSyntaxTrees.NewObjectExpr;
import miniJava.AbstractSyntaxTrees.NullLiteral;
import miniJava.AbstractSyntaxTrees.Operator;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.AbstractSyntaxTrees.ParameterDecl;
import miniJava.AbstractSyntaxTrees.QualRef;
import miniJava.AbstractSyntaxTrees.RefExpr;
import miniJava.AbstractSyntaxTrees.ReturnStmt;
import miniJava.AbstractSyntaxTrees.ThisRef;
import miniJava.AbstractSyntaxTrees.UnaryExpr;
import miniJava.AbstractSyntaxTrees.VarDecl;
import miniJava.AbstractSyntaxTrees.VarDeclStmt;
import miniJava.AbstractSyntaxTrees.Visitor;
import miniJava.AbstractSyntaxTrees.WhileStmt;
import miniJava.AbstractSyntaxTrees.*;

public class Identification implements Visitor<Object, Object> {

	public ErrorReporter reporter;
	private IdentificationTable table;
	private String currentClassName = null;
	public MethodDecl currentMethod = null;
	public String varDeclName = null;
	
	public Identification(ErrorReporter reporter) {
		this.reporter = reporter;
	}
	
	public void beginIdentification(AST ast) {
		table = new IdentificationTable(reporter);
		ast.visit(this, null);
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//
	// PACKAGE
	//
	/////////////////////////////////////////////////////////////////////////////// 
	
	@Override
	public Object visitPackage(Package prog, Object arg) {
		
		// initial pass for classes and members
		for (ClassDecl cd: prog.classDeclList) {
			if (table.retrieveClass(cd.name) == null) { // add fields/methods to table if class not already declared
				table.classes.put(cd.name, cd);
				table.classFields.put(cd.name, new HashMap<String,Declaration>());  // add fields if not previously declared as member name
				for (FieldDecl fd: cd.fieldDeclList) { 
					if (table.retrieveClassField(cd.name, fd.name) != null) {
						reporter.reportError("*** line " + fd.posn.getLine() + ": " + "column " + fd.posn.getCol() + " " + fd.name + " member name previously declared");
						System.exit(4);
					}
					table.classFields.get(cd.name).put(fd.name, fd);
				}
				table.classMethods.put(cd.name, new HashMap<String,Declaration>());  // add methods if not previously declared as member name
				for (MethodDecl md: cd.methodDeclList) {
					if (table.retrieveClassMethod(cd.name, md.name) != null) {
						reporter.reportError("*** line " + md.posn.getLine() + ": " + "column " + md.posn.getCol() + " " + md.name + " member name previously declared");
						System.exit(4);
					}
					table.classMethods.get(cd.name).put(md.name, md);
				}
			} else { // class previously declared
				reporter.reportError("*** line " + cd.posn.getLine() + ": " + "column " + cd.posn.getCol() + " " + cd.name + " class previously declared");
				System.exit(4);
			}
		}
		
		for (ClassDecl cd: prog.classDeclList) {
			currentClassName = cd.name;
			cd.visit(this, null);
		}
		
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// DECLARATIONS
	//
	///////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object visitClassDecl(ClassDecl cd, Object arg) {
		table.openScope(); // SCOPE 1
		table.enter(cd.name, cd);
		table.openScope(); // SCOPE 2
		for (FieldDecl fd: cd.fieldDeclList) { // look for field/method name collisions (already checked field/field and method/method collisions)
			for (MethodDecl md: cd.methodDeclList) {
				if (fd.name.equals(md.name) && fd.posn.getLine() > md.posn.getLine()) {
					reporter.reportError("*** line " + fd.posn.getLine() + ": " + "column " + fd.posn.getCol() + " " + fd.name + " member name previously declared");
					System.exit(4);
				} else if (fd.name.equals(md.name) && fd.posn.getLine() < md.posn.getLine()) {
					reporter.reportError("*** line " + md.posn.getLine() + ": " + "column " + md.posn.getCol() + " " + md.name + " member name previously declared");
					System.exit(4);
				}
			}
		}
		for (FieldDecl fd: cd.fieldDeclList) {
			fd.visit(this, null);
		}
		for (MethodDecl md: cd.methodDeclList) {
			currentMethod = md;
			md.visit(this, null);
			currentMethod = null;
		}
		table.closeScope(); // SCOPE 1
		table.closeScope(); // SCOPE 0
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object arg) {
		table.enter(fd.name, fd);
		fd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		if (md.type instanceof ClassType) { // check if return type class is declared
			ClassType classReturnType = ((ClassType) md.type);
			String classReturnTypeName = classReturnType.className.spelling;
			Declaration classReturnTypeDecl = table.retrieveClass(classReturnTypeName);
			if (classReturnTypeDecl == null) {	// no such declared class
				reporter.reportError("*** line " + md.posn.getLine() + ": " + "column " + md.posn.getCol() + " " + md.name + " method return type not declared");
				System.exit(4);
			}
		}
		table.enter(md.name, md);
		table.openScope(); // SCOPE 3
		for (ParameterDecl pd: md.parameterDeclList) {
			pd.visit(this, null);
		}
		table.openScope(); // SCOPE 4 
		for (Statement stmt: md.statementList) {
			stmt.visit(this, null);
		}
		table.closeScope(); // SCOPE 3
		table.closeScope(); // SCOPE 2
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		table.enter(pd.name, pd);
		pd.type.visit(this, null);
		if (pd.type instanceof ClassType) {
			ClassType paraType = ((ClassType) pd.type);
			String paraTypeName = paraType.className.spelling;
			Declaration paraTypeDecl = table.retrieveClass(paraTypeName);
			if (paraTypeDecl == null) {
				reporter.reportError("*** line " + pd.posn.getLine() + ": " + "column " + pd.posn.getCol() + " parameter " + pd.name + "'s type has not declared");
				System.exit(4);
			}
		}
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		table.enter(decl.name, decl);
		decl.type.visit(this, null);
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// TYPES
	//
	///////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object visitBaseType(BaseType type, Object arg) {
		return null;
	}

	@Override
	public Object visitClassType(ClassType type, Object arg) {
		type.className.visit(this, null);
		return null;
	}

	@Override
	public Object visitArrayType(ArrayType type, Object arg) {
		type.eltType.visit(this, null);
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// STATEMENTS
	//
	///////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		table.openScope();
		for (Statement innerstmt: stmt.sl) {
			innerstmt.visit(this, null);
		}
		table.closeScope();
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		varDeclName = stmt.varDecl.name; // cannot use varDeclName in expression
		stmt.initExp.visit(this, null);
		varDeclName = null;
		stmt.varDecl.visit(this, null);
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		stmt.ref.visit(this, null);
		stmt.val.visit(this, null);
		if (stmt.val instanceof RefExpr) {
			RefExpr refExpr = ((RefExpr)stmt.val);
			if (refExpr.ref instanceof IdRef) {
				IdRef idRef = ((IdRef)refExpr.ref);
				String identifierName = idRef.id.spelling;
				if (table.getHighestScopeOccurence(identifierName) == 0 || table.getHighestScopeOccurence(identifierName) == 1) {
					reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " cannot assign to class " + identifierName);
					System.exit(4);
				}
			}
		}
		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		stmt.ref.visit(this, null);
		stmt.ix.visit(this, null);
		stmt.exp.visit(this, null);
		if (stmt.exp instanceof RefExpr) {
			RefExpr refExpr = ((RefExpr)stmt.exp);
			if (refExpr.ref instanceof IdRef) {
				IdRef idRef = ((IdRef)refExpr.ref);
				String identifierName = idRef.id.spelling;
				if (table.getHighestScopeOccurence(identifierName) == 0 || table.getHighestScopeOccurence(identifierName) == 1) {
					reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " cannot assign to class " + identifierName);
					System.exit(4);
				}
			}
		}
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		stmt.methodRef.visit(this, null);
		for (Expression argExpr: stmt.argList) {
			argExpr.visit(this, null);
		}
		if (!(stmt.methodRef.decl instanceof MethodDecl)) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " can only have call statement of method");
			System.exit(4);
		}
		MethodDecl methodCalled = ((MethodDecl) stmt.methodRef.decl);
		if (currentMethod.isStatic && !methodCalled.isStatic && !(stmt.methodRef instanceof QualRef)) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " static access context conflict between methods");
			System.exit(4);
		}
		if (stmt.methodRef instanceof QualRef) {
			QualRef qualRef = ((QualRef)stmt.methodRef);
			if (qualRef.ref instanceof IdRef) {
				IdRef idRef = ((IdRef)qualRef.ref);
				Declaration idDecl = idRef.decl;
				if (idDecl.type instanceof ClassType && idDecl instanceof VarDecl) {               
					idDecl = table.retrieveClass(((ClassType)idDecl.type).className.spelling);
					methodCalled = ((MethodDecl) table.retrieveClassMethod(idDecl.name, methodCalled.name));
					if (methodCalled.isPrivate) {
						reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " cannot call private method of " + idDecl.name);
						System.exit(4);
					}
				} else if (idDecl instanceof ClassDecl) {
					if (table.retrieveClassMethod(idDecl.name, methodCalled.name) instanceof MethodDecl) {
						methodCalled = ((MethodDecl)table.retrieveClassMethod(idDecl.name, methodCalled.name));
						if (!methodCalled.isStatic) {
							reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " method does not have static access");
							System.exit(4);
						}
						if (methodCalled.isPrivate) {
							reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " cannot call private method of " + idDecl.name);
							System.exit(4);
						}
					} else {
						reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " method not found in " + idDecl.name);
						System.exit(4);
					}
				}
			}
		}
		
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		if (stmt.returnExpr != null) {
			stmt.returnExpr.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		stmt.thenStmt.visit(this, null);
		if (stmt.thenStmt instanceof VarDeclStmt) {
			reporter.reportError("*** line " + stmt.thenStmt.posn.getLine() + ": " + "column " + stmt.thenStmt.posn.getCol() + " variable declaration cannot be the solitary statement in a branch of a conditional statement");
			System.exit(4);
		}
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, null);
			if (stmt.elseStmt instanceof VarDeclStmt) {
				reporter.reportError("*** line " + stmt.elseStmt.posn.getLine() + ": " + "column " + stmt.elseStmt.posn.getCol() + " variable declaration cannot be the solitary statement in a branch of a conditional statement");
				System.exit(4);
			}
		}
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		stmt.body.visit(this, null);
		if (stmt.body instanceof VarDeclStmt) {
			reporter.reportError("*** line " + stmt.body.posn.getLine() + ": " + "column " + stmt.body.posn.getCol() + " variable declaration cannot be the solitary statement in a branch of a conditional statement");
			System.exit(4);
		}
		return null;
	}
	
	///////////////////////////////////////////////////////////////////////////////
	//
	// EXPRESSIONS
	//
	///////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		expr.operator.visit(this, null);
		expr.expr.visit(this, null);
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		expr.operator.visit(this, null);
		expr.left.visit(this, null);
		expr.right.visit(this,null);
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		expr.ref.visit(this,null);
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		expr.ref.visit(this, null);
		expr.ixExpr.visit(this, null);
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		expr.functionRef.visit(this, null);
		for (Expression argExpr: expr.argList) {
			argExpr.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		expr.lit.visit(this, null);
		return null;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		expr.classtype.visit(this, null);
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		expr.eltType.visit(this, null);
		expr.sizeExpr.visit(this, null);
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// REFERENCES
	//
	///////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		ref.decl = table.retrieveClass(currentClassName);
		if (currentMethod.isStatic) { // can only reference static members and methods
			reporter.reportError("*** line " + ref.posn.getLine() + ": " + "column " + ref.posn.getCol() + " cannot have 'this' reference in static method");
			System.exit(4);
		}
		return null;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		if (ref.id.spelling.equals(varDeclName)) {
			reporter.reportError("*** line " + ref.posn.getLine() + ": " + "column " + ref.posn.getCol() + " cannot use " + ref.id.spelling + " in it's initilizing expression");
			System.exit(4);
		}
		ref.id.visit(this, null);
		ref.decl = ref.id.decl;
		if (ref.decl instanceof FieldDecl) {
			FieldDecl fd = ((FieldDecl)ref.decl);
			if (fd.isStatic != currentMethod.isStatic) {
				reporter.reportError("*** line " + ref.posn.getLine() + ": " + "column " + ref.posn.getCol() + " reference made in incorrect static context");
				System.exit(4);
			}
		} else if (ref.decl instanceof MethodDecl) {
			MethodDecl md = ((MethodDecl)ref.decl);
			if (md.isStatic != currentMethod.isStatic) {
				reporter.reportError("*** line " + ref.posn.getLine() + ": " + "column " + ref.posn.getCol() + " reference made in incorrect static context");
				System.exit(4);
			}
		}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		ref.ref.visit(this, null);
		if (ref.ref.decl.type instanceof ClassType) { 
			currentClassName = ((ClassType)ref.ref.decl.type).className.spelling;
		}
		ref.id.visit(this, null);
		ref.decl = ref.id.decl;
		currentClassName = null;
		if (ref.ref instanceof IdRef) {
			IdRef idRef = ((IdRef)ref.ref);
			String identifierName = idRef.id.spelling;
			if (table.getHighestScopeOccurence(identifierName) == 0 || table.getHighestScopeOccurence(identifierName) == 1) {
				if (ref.id.decl instanceof FieldDecl) {
					FieldDecl fd = ((FieldDecl)ref.id.decl);
					if (!fd.isStatic && currentMethod.isStatic) {
						reporter.reportError("*** line " + ref.posn.getLine() + ": " + "column " + ref.posn.getCol() + " reference made in incorrect static context");
						System.exit(4);
					}
				}
			}	
		}
		if (ref.ref.decl instanceof MethodDecl) {
			reporter.reportError("*** line " + ref.posn.getLine() + ": " + "column " + ref.posn.getCol() + " method call embedded in qualified reference");
			System.exit(4);
		}
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////
	//
	// TERMINALS
	//
	///////////////////////////////////////////////////////////////////////////////
	
	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		id.decl = table.retrieve(id.spelling);
		if (id.decl == null) {
			reporter.reportError("*** line " + id.posn.getLine() + ": " + "column " + id.posn.getCol() + " " + id.spelling + " identifier has not been declared");
			System.exit(4);
		}
		if (id.decl instanceof FieldDecl && table.retrieveClassField(currentClassName, id.spelling) == null) { // field of another class
			if (((FieldDecl) id.decl).isPrivate) {
				reporter.reportError("*** line " + id.posn.getLine() + ": " + "column " + id.posn.getCol() + " " + id.spelling + " cannot access private field in other class");
				System.exit(4);
			}
		}
		return null;
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		return null;
	}

	@Override
	public Object visitNullLiteral(NullLiteral nul, Object arg) {
		return null;
	}

}
