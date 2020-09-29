package miniJava.ContextualAnalysis;



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
import miniJava.AbstractSyntaxTrees.Statement;
import miniJava.AbstractSyntaxTrees.ThisRef;
import miniJava.AbstractSyntaxTrees.TypeDenoter;
import miniJava.AbstractSyntaxTrees.TypeKind;
import miniJava.AbstractSyntaxTrees.UnaryExpr;
import miniJava.AbstractSyntaxTrees.VarDecl;
import miniJava.AbstractSyntaxTrees.VarDeclStmt;
import miniJava.AbstractSyntaxTrees.Visitor;
import miniJava.AbstractSyntaxTrees.WhileStmt;
import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.AbstractSyntaxTrees.*;

public class TypeChecker implements Visitor<Object, TypeDenoter> {

	public ErrorReporter reporter;
	
	public TypeChecker(ErrorReporter reporter) {
		this.reporter = reporter;
	}
	
	public void beginTypeChecking(AST ast) {
		ast.visit(this, null);
	}
	
	private boolean checkTypeEquality(TypeDenoter typeOne, TypeDenoter typeTwo) {
		if (typeOne == null || typeTwo == null) {
			return false;
		}
		if (typeOne.typeKind == TypeKind.ERROR || typeTwo.typeKind == TypeKind.ERROR) {
			return true;
		} else if (typeOne.typeKind == TypeKind.UNSUPPORTED || typeTwo.typeKind == TypeKind.UNSUPPORTED) {
			return false;
		} else if (typeOne instanceof ArrayType || typeTwo instanceof ArrayType) {
			if (typeOne.typeKind == TypeKind.NULL || typeTwo.typeKind == TypeKind.NULL) {
				return true;
			} else if ((typeOne instanceof ArrayType) && ((ArrayType)typeOne).eltType.typeKind == typeTwo.typeKind 
					|| (typeTwo instanceof ArrayType) && ((ArrayType)typeTwo).eltType.typeKind == typeOne.typeKind) {
				return true;
			} else if (!(typeOne instanceof ArrayType) || !(typeTwo instanceof ArrayType)) {
				return false;
			} else { // compare elt types
				ArrayType typeOneArr = (ArrayType) typeOne;
				ArrayType typeTwoArr = (ArrayType) typeTwo;
				return checkTypeEquality(typeOneArr.eltType, typeTwoArr.eltType);
			}
		} else if (typeOne instanceof ClassType || typeTwo instanceof ClassType) {
			if (typeOne.typeKind == TypeKind.NULL || typeTwo.typeKind == TypeKind.NULL) {
				return true;
			} else if (!(typeOne instanceof ClassType) || !(typeTwo instanceof ClassType)) {
				return false;
			} else {
				Identifier classNameOne = ((ClassType)typeOne).className;
				Identifier classNameTwo = ((ClassType)typeTwo).className;
				if (classNameOne.decl != null && classNameTwo.decl != null && classNameOne.decl.type != null && classNameTwo.decl.type != null) {	
					if (classNameOne.decl.type.typeKind == TypeKind.UNSUPPORTED || classNameTwo.decl.type.typeKind == TypeKind.UNSUPPORTED) {
						return false;
					}
				}
				return classNameOne.spelling.equals(classNameTwo.spelling);
			}
		} else {
			return typeOne.typeKind == typeTwo.typeKind;
		}
	}
	@Override
	public TypeDenoter visitPackage(Package prog, Object arg) {
		for (ClassDecl cd: prog.classDeclList) {
			cd.visit(this, null);
		}
		return new BaseType(TypeKind.UNSUPPORTED, prog.posn);
	}

	@Override
	public TypeDenoter visitClassDecl(ClassDecl cd, Object arg) {
		for (FieldDecl fd: cd.fieldDeclList) {
			fd.visit(this, null);
		}
		for (MethodDecl md: cd.methodDeclList) {
			md.visit(this, null);
		}
		return cd.type;
	}

	@Override
	public TypeDenoter visitFieldDecl(FieldDecl fd, Object arg) {
		return fd.type;
	}

	@Override
	public TypeDenoter visitMethodDecl(MethodDecl md, Object arg) {
		TypeDenoter methodReturnType = md.type;
		for (ParameterDecl pd: md.parameterDeclList) {
			pd.visit(this, null);
		}
		for (Statement stmt: md.statementList) {
			TypeDenoter stmtType = stmt.visit(this, null);
			if (stmt instanceof ReturnStmt && !checkTypeEquality(methodReturnType, stmtType)) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "method return type does not match return statement type");
			}
		}
		return md.type;
	}

	@Override
	public TypeDenoter visitParameterDecl(ParameterDecl pd, Object arg) {
		return pd.type;
	}

	@Override
	public TypeDenoter visitVarDecl(VarDecl decl, Object arg) {
		return decl.type;
	}

	@Override
	public TypeDenoter visitBaseType(BaseType type, Object arg) {
		return type.type;
	}

	@Override
	public TypeDenoter visitClassType(ClassType type, Object arg) {
		return type.type;
	}

	@Override
	public TypeDenoter visitArrayType(ArrayType type, Object arg) {
		return type.type;
	}

	@Override
	public TypeDenoter visitBlockStmt(BlockStmt stmt, Object arg) {
		for (Statement statement: stmt.sl) {
			statement.visit(this, null);
		}
		return new BaseType(TypeKind.UNSUPPORTED, stmt.posn);
	}

	@Override
	public TypeDenoter visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		TypeDenoter varType = stmt.varDecl.visit(this, null);
		TypeDenoter exprType = stmt.initExp.visit(this, null);
		if (stmt.initExp instanceof RefExpr) {
			RefExpr expr = ((RefExpr)stmt.initExp);
			if (expr.ref.decl instanceof ClassDecl) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "cannot declare variable as a class");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			} else if (expr.ref.decl instanceof MethodDecl) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "cannot declare variable as a method");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			}
		} else if (!checkTypeEquality(varType, exprType)) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "variable type not equal to expression type");
			return new BaseType(TypeKind.ERROR, stmt.posn);
		}
		return varType;
	}

	@Override
	public TypeDenoter visitAssignStmt(AssignStmt stmt, Object arg) {
		TypeDenoter varType = stmt.ref.visit(this,null);
		TypeDenoter exprType = stmt.val.visit(this, null);
		if (stmt.val instanceof RefExpr) {
			RefExpr expr = ((RefExpr)stmt.val);
			if (expr.ref.decl instanceof ClassDecl) {
				if (expr.ref instanceof ThisRef) {
					return varType;
				}
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "cannot assign a class to variable");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			} else if (expr.ref.decl instanceof MethodDecl) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "cannot assign a method to variable");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			}
		} else if (stmt.ref instanceof QualRef) {
			QualRef qRef = (QualRef) stmt.ref;
			if (qRef.ref.decl.type instanceof ArrayType && qRef.id.spelling.equals("length")) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " length field can only be read not assigned");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			}
		} else if (!checkTypeEquality(varType, exprType)) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "variable type not equal to expression type");
			return new BaseType(TypeKind.ERROR, stmt.posn);
		}
		return varType;
	}

	@Override
	public TypeDenoter visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		TypeDenoter varType = stmt.ref.visit(this, null);
		TypeDenoter ixType = stmt.ix.visit(this, null);
		TypeDenoter exprType = stmt.exp.visit(this, null);
		if (stmt.exp instanceof RefExpr) {
			RefExpr expr = ((RefExpr)stmt.exp);
			if (expr.ref.decl instanceof ClassDecl) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "cannot assign a class to variable");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			} else if (expr.ref.decl instanceof MethodDecl) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "cannot assign a method to variable");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			}
		} else if (!checkTypeEquality(varType, exprType)) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "variable type not equal to expression type");
			return new BaseType(TypeKind.ERROR, stmt.posn);
		}
		return varType;
	}

	@Override
	public TypeDenoter visitCallStmt(CallStmt stmt, Object arg) {
		if (!(stmt.methodRef.decl instanceof MethodDecl)) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "attempts to call a non-method");
			return new BaseType(TypeKind.ERROR, stmt.posn);
		} else {
			TypeDenoter methodReturnType = stmt.methodRef.visit(this, null);
			ParameterDeclList methodParameters = ((MethodDecl)stmt.methodRef.decl).parameterDeclList;
			ExprList providedParameters = stmt.argList;
			if (providedParameters.size() != methodParameters.size()) {
				reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "incorrect number of arguments provided to method");
				return new BaseType(TypeKind.ERROR, stmt.posn);
			} else {
				for (int i = 0; i < methodParameters.size(); i++) {
					TypeDenoter paraType = methodParameters.get(i).type;
					TypeDenoter providedType = providedParameters.get(i).visit(this, null);
					if (!checkTypeEquality(paraType, providedType)) {
						reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "a provided parameter to method has incorrect type");
						return new BaseType(TypeKind.ERROR, stmt.posn);
					}
				}
				return methodReturnType;
			}
		}
	}

	@Override
	public TypeDenoter visitReturnStmt(ReturnStmt stmt, Object arg) {
		if (stmt.returnExpr == null) {
			return new BaseType(TypeKind.VOID, stmt.posn);
		} else {
			return stmt.returnExpr.visit(this, null);
		}
	}

	@Override
	public TypeDenoter visitIfStmt(IfStmt stmt, Object arg) {
		TypeDenoter condition = stmt.cond.visit(this, null);
		if (condition.typeKind != TypeKind.BOOLEAN) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "condition does not evaluate to BOOLEAN");
			return new BaseType(TypeKind.ERROR, stmt.posn);
		}
		stmt.thenStmt.visit(this, null);
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, null);
		}
		return new BaseType(TypeKind.UNSUPPORTED, stmt.posn);
	}

	@Override
	public TypeDenoter visitWhileStmt(WhileStmt stmt, Object arg) {
		TypeDenoter condition = stmt.cond.visit(this, null);
		if (condition.typeKind != TypeKind.BOOLEAN) {
			reporter.reportError("*** line " + stmt.posn.getLine() + ": " + "column " + stmt.posn.getCol() + " Type Error - " + "condition does not evaluate to BOOLEAN");
			return new BaseType(TypeKind.ERROR, stmt.posn);
		}
		stmt.body.visit(this, null);
		return new BaseType(TypeKind.UNSUPPORTED, stmt.posn);
	}

	@Override
	public TypeDenoter visitUnaryExpr(UnaryExpr expr, Object arg) {
		TypeDenoter exprType = expr.expr.visit(this, null);
		if (expr.operator.kind == TokenKind.NOT) {
			if (exprType.typeKind != TypeKind.BOOLEAN) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "can only apply NOT operator to type BOOLEAN");
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.BOOLEAN, expr.posn);
		} else if (expr.operator.kind == TokenKind.MINUS){ 
			if (exprType.typeKind != TypeKind.INT) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "can only apply MINUS operator to type INT");
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.INT, expr.posn);
		} else {
			return new BaseType(TypeKind.ERROR, expr.posn);
		}
	}

	@Override
	public TypeDenoter visitBinaryExpr(BinaryExpr expr, Object arg) {
		TypeDenoter lExpType = expr.left.visit(this, null);
		TypeDenoter rExpType = expr.right.visit(this, null);
		switch (expr.operator.kind) {
		case MINUS: case PLUS: case TIMES: case DIVIDE:
			if (lExpType.typeKind != TypeKind.INT || rExpType.typeKind != TypeKind.INT) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + " Type Error - " + "can only perform arithmetic operation between two INT");
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.INT, expr.posn);
		case AND: case OR:
			if (lExpType.typeKind != TypeKind.BOOLEAN || rExpType.typeKind != TypeKind.BOOLEAN) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + " Type Error - " + "can only perform logical operation between two BOOLEAN");
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.BOOLEAN, expr.posn);
		case GREATER: case LESS: case LESSEQUAL: case GREATEREQUAL:
			if (lExpType.typeKind != TypeKind.INT || rExpType.typeKind != TypeKind.INT) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + " Type Error - " + "can only perform arithmetic comparison operation between two INT");
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.BOOLEAN, expr.posn);
		case EQUAL: case NOTEQUAL:
			if (!checkTypeEquality(lExpType, rExpType)) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + " Type Error - " + "can only test for equality on matching types");
				return new BaseType(TypeKind.ERROR, expr.posn);
			}
			return new BaseType(TypeKind.BOOLEAN, expr.posn);
		default:
			return new BaseType(TypeKind.ERROR, expr.posn);
		}
	}

	@Override
	public TypeDenoter visitRefExpr(RefExpr expr, Object arg) {
		return expr.ref.visit(this, null);
	}

	@Override
	public TypeDenoter visitIxExpr(IxExpr expr, Object arg) {
		TypeDenoter refType = expr.ref.visit(this, null);
		TypeDenoter ixType = expr.ixExpr.visit(this, null);
		if (ixType.typeKind != TypeKind.INT) {
			reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "index expression must be type INT");
			return new BaseType(TypeKind.ERROR, expr.posn);
		} else if (expr.ref.decl.type instanceof ArrayType) {
			return (((ArrayType)expr.ref.decl.type)).eltType;
		} else {
			reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "reference is not of type ARRAY");
			return new BaseType(TypeKind.ERROR, expr.posn);
		}
	}

	@Override
	public TypeDenoter visitCallExpr(CallExpr expr, Object arg) {
		if (!(expr.functionRef.decl instanceof MethodDecl)) {
			reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "attempts to call a non-method");
			return new BaseType(TypeKind.ERROR, expr.posn);
		} else {
			TypeDenoter methodReturnType = expr.functionRef.visit(this, null);
			ParameterDeclList methodParameters = ((MethodDecl)expr.functionRef.decl).parameterDeclList;
			ExprList providedParameters = expr.argList;
			if (providedParameters.size() != methodParameters.size()) {
				reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "incorrect number of arguments provided to method");
				return new BaseType(TypeKind.ERROR, expr.posn);
			} else {
				for (int i = 0; i < methodParameters.size(); i++) {
					TypeDenoter paraType = methodParameters.get(i).type;
					TypeDenoter providedType = providedParameters.get(i).visit(this, null);
					if (!checkTypeEquality(paraType, providedType)) {
						reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "a provided parameter to method has incorrect type");
						return new BaseType(TypeKind.ERROR, expr.posn);
					}
				}
				return methodReturnType;
			}
		}
	}

	@Override
	public TypeDenoter visitLiteralExpr(LiteralExpr expr, Object arg) {
		return expr.lit.visit(this, null);
	}

	@Override
	public TypeDenoter visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		return expr.classtype;
	}

	@Override
	public TypeDenoter visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		TypeDenoter arraySizeExpType = expr.sizeExpr.visit(this, null);
		if (arraySizeExpType.typeKind != TypeKind.INT) {
			reporter.reportError("*** line " + expr.posn.getLine() + ": " + "column " + expr.posn.getCol() + " Type Error - " + "array size expression is not type INT");
			return new BaseType(TypeKind.ERROR, expr.posn);
		}
		return new ArrayType(expr.eltType, expr.posn);
	}

	@Override
	public TypeDenoter visitThisRef(ThisRef ref, Object arg) {
		if (ref.decl != null) {
			return ref.decl.type;
		}
		return new BaseType(TypeKind.ERROR, ref.posn);
	}

	@Override
	public TypeDenoter visitIdRef(IdRef ref, Object arg) {
		return ref.decl.type;
	}

	@Override
	public TypeDenoter visitQRef(QualRef ref, Object arg) {
		return ref.id.visit(this, null);
	}

	@Override
	public TypeDenoter visitIdentifier(Identifier id, Object arg) {
		return id.decl.type;
	}

	@Override
	public TypeDenoter visitOperator(Operator op, Object arg) {
		return new BaseType(TypeKind.UNSUPPORTED, op.posn);
	}

	@Override
	public TypeDenoter visitIntLiteral(IntLiteral num, Object arg) {
		return new BaseType(TypeKind.INT, num.posn);
	}

	@Override
	public TypeDenoter visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		return new BaseType(TypeKind.BOOLEAN, bool.posn);
	}

	@Override
	public TypeDenoter visitNullLiteral(NullLiteral nul, Object arg) {
		return new BaseType(TypeKind.NULL, nul.posn);
	}

}
