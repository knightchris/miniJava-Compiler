package miniJava.CodeGenerator;

import java.util.ArrayList;
import java.util.Stack;

import mJAM.Disassembler;
import mJAM.Interpreter;
import mJAM.Machine;
import mJAM.ObjectFile;
import mJAM.Machine.Op;
import mJAM.Machine.Prim;
import mJAM.Machine.Reg;
import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.TokenKind;

public class CodeGenerator implements Visitor<Object, Object> {

	ErrorReporter reporter;
	int staticSegTopAddr;
	int mainAddr;
	int currentParaCount;
	int localOffset;
	int currentLocalVarCount;
	ArrayList<MethodPatchInfo> methodsToPatch;
	
	public CodeGenerator(ErrorReporter reporter) {
		this.reporter = reporter;
		methodsToPatch = new ArrayList<MethodPatchInfo>(); // patch all after traversal
	}
	
	public void beginCodeGen(AST ast, String inputFileName) {
		Machine.initCodeGen();
		// static seg space
		staticSegTopAddr = Machine.nextInstrAddr();
		Machine.emit(Op.PUSH, 0);
		
		Machine.emit(Op.LOADL,0);            // array length 0
		Machine.emit(Prim.newarr);           // empty String array argument
		
		mainAddr = Machine.nextInstrAddr();  // record instr addr where main is called                                                // "main" is called
		Machine.emit(Op.CALL,Reg.CB, 0);     // static call main (address to be patched)
		Machine.emit(Op.HALT,0,0,0);         // end execution
		
		ast.visit(this, null);
		
		String objectCodeFileName = inputFileName.replace(".java", ".mJAM");
		ObjectFile objF = new ObjectFile(objectCodeFileName);
		System.out.print("Writing object code file " + objectCodeFileName + " ... ");
		if (objF.write()) {
			System.out.println("FAILED!");
			return;
		}
		else
			System.out.println("SUCCEEDED");	
		
	}
	@Override
	public Object visitPackage(Package prog, Object arg) {
		/*
		 * Traverse all Declarations creating a runtime entity descriptor (RED)
			for each declaration
			 offset relative to LB for local variables and parameter variables
			 offset relative to SB for static fields
			 offset relative to OB for instance variables
			 offset relative to CB for methods 
		 */
		int staticSegOffset = 0;
		for (ClassDecl cd: prog.classDeclList) {
			int instanceOffset = 3;
			for (FieldDecl fd: cd.fieldDeclList) {
				if (!fd.isStatic) {
					fd.red = new RuntimeEntityDescription(instanceOffset);
					instanceOffset++;
				} else {
					fd.red = new RuntimeEntityDescription(staticSegOffset);
					staticSegOffset++;
				}
			}
			cd.red = new RuntimeEntityDescription(instanceOffset); // size is # of fields
		}
		Machine.patch(staticSegTopAddr, staticSegOffset);
		Machine.patch(mainAddr, Machine.nextInstrAddr());
		
		boolean seenMain = false;
		for (ClassDecl cd: prog.classDeclList) {
			for (MethodDecl md: cd.methodDeclList) {
				if (md.name.equals("main") && !seenMain) {
					seenMain = true;
					if (!md.isStatic || md.type.typeKind != TypeKind.VOID || md.isPrivate) {
						reporter.reportError("*** Incorrect main method");
						System.exit(4);
					}
					if (md.parameterDeclList.size() != 1) {
						reporter.reportError("*** Incorrect main method parameter number");
						System.exit(4);
					} else if (!(md.parameterDeclList.get(0).type instanceof ArrayType)) {
						reporter.reportError("*** Parameter to main is not an array");
						System.exit(4);
					} else {
						ArrayType paraType = ((ArrayType)md.parameterDeclList.get(0).type);
						if (!(paraType.eltType instanceof ClassType)) {
							reporter.reportError("*** Parameter to main is not a class array");
							System.exit(4);
						} else {
							ClassType arrayType = ((ClassType)paraType.eltType);
							if (!arrayType.className.spelling.equals("String")) {
								reporter.reportError("*** Parameter to main is not a string array");
								System.exit(4);
							}
						}
					}
					methodsToPatch.add(new MethodPatchInfo(md, mainAddr));
				} else if (md.name.equals("main")) {
					reporter.reportError("*** Non-unique main method");
					System.exit(4);
				}
			}
		}
		
		if (!seenMain) {
			reporter.reportError("*** No main method present");
			System.exit(4);
		}
		/*
				 * (i) If you have not already done this in PA3, add a check in contextual analysis that the
		last statement in a non-void method is a return statement If not, issue an error and
		terminate via exit(4). (ii) For a void method, no error should be issued if the last
		statement in the program is not a return statement. However the mJAM code for a
		return statement should be generated at the end of the program, if not already present. 
		 */
		for (ClassDecl cd: prog.classDeclList) {
			for (MethodDecl md: cd.methodDeclList) {
				if (md.statementList.size() == 0) {  // empty method body 
					md.statementList.add(new ReturnStmt(null, md.posn)); 
				}
				Statement endStmt = md.statementList.get(md.statementList.size() - 1);
				if (md.type.typeKind != TypeKind.VOID) {
					if (!(endStmt instanceof ReturnStmt)) {
						reporter.reportError("*** No return statement in non-void method");
						System.exit(4);
					}
				} else {
					md.statementList.add(new ReturnStmt(null, endStmt.posn));
				}
			}
		}
		
		// regular traversal
		for (ClassDecl cd: prog.classDeclList) {
			cd.visit(this, null);
		}
		
		// patch method addresses
		for (MethodPatchInfo mpi: methodsToPatch) {
			Machine.patch(mpi.addr, mpi.method.red.offsetSize);
		}
		
	return null;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Object arg) {
		for (FieldDecl fd: cd.fieldDeclList) {
			fd.visit(this, null);
		}
		for (MethodDecl md: cd.methodDeclList) {
			md.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object arg) {
		fd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		localOffset = 3;
		currentParaCount = md.parameterDeclList.size();
		md.type.visit(this, null);
		int paraOffsetLB = -currentParaCount;
		for (ParameterDecl pd: md.parameterDeclList) {
			pd.visit(this, null);
			pd.red = new RuntimeEntityDescription(paraOffsetLB);
			paraOffsetLB++;
		}
		// method location in code segment
		md.red = new RuntimeEntityDescription(Machine.nextInstrAddr());
		for (Statement stmt: md.statementList) {
			stmt.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		pd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		decl.red = new RuntimeEntityDescription(localOffset);
		localOffset++;
		decl.type.visit(this, null);
		return null;
	}

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

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		currentLocalVarCount = 0;
		for (Statement stmnt: stmt.sl) {
			stmnt.visit(this, null);
		}
		if (currentLocalVarCount > 0) {
			localOffset = localOffset - currentLocalVarCount;
			Machine.emit(Op.POP, currentLocalVarCount);
		}
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		currentLocalVarCount++;
		stmt.varDecl.visit(this, null);
		stmt.initExp.visit(this, null);
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		if (stmt.ref.decl instanceof FieldDecl && ((FieldDecl)stmt.ref.decl).isStatic) {
			stmt.val.visit(this, null);
			Machine.emit(Op.STORE, Machine.Reg.SB, stmt.ref.decl.red.offsetSize);
		} else if (stmt.ref instanceof IdRef) {
			if (((IdRef)stmt.ref).decl instanceof FieldDecl) {
				Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
				Machine.emit(Op.LOADL, ((IdRef)stmt.ref).id.decl.red.offsetSize);
				stmt.val.visit(this, null);
				Machine.emit(Prim.fieldupd);
			} else {
				stmt.val.visit(this, null);
				if (stmt.ref.decl instanceof FieldDecl) {
					if (((FieldDecl)stmt.ref.decl).isStatic) {
						Machine.emit(Op.STORE, Machine.Reg.SB, ((IdRef)stmt.ref).id.decl.red.offsetSize);
					}
				} else {
					Machine.emit(Op.STORE, Machine.Reg.LB, ((IdRef)stmt.ref).id.decl.red.offsetSize);
				}
			}
		} else if (stmt.ref instanceof QualRef) {
			QualRef ref = ((QualRef)stmt.ref);
			Stack<Integer> fieldOffSets = new Stack<Integer>();
			if (ref.id.decl.red != null) {
				fieldOffSets.push(ref.id.decl.red.offsetSize);
				while (ref.ref instanceof QualRef) {
					ref = (QualRef) ref.ref;
					fieldOffSets.push(ref.decl.red.offsetSize);
				}
				ref.ref.visit(this, null);
				int fieldOffSetsSize = fieldOffSets.size();
				for (int i = 0; i < fieldOffSetsSize; i++) {
					int offSet = fieldOffSets.pop();
					Machine.emit(Op.LOADL, offSet);
					if (i < fieldOffSetsSize-1) {
						Machine.emit(Prim.fieldref);
					}
				}
			}
			stmt.val.visit(this, null);
			Machine.emit(Prim.fieldupd);
		}
		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		stmt.ref.visit(this, null);
		stmt.ix.visit(this, null);
		stmt.exp.visit(this, null);
		Machine.emit(Prim.arrayupd);
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		for (Expression e: stmt.argList) {
			e.visit(this, null);
		}
		if (stmt.methodRef.decl instanceof MethodDecl && ((MethodDecl)stmt.methodRef.decl).name.equals("println")) {
			Machine.emit(Prim.putintnl);
		} else {
			int callAddr = Machine.nextInstrAddr();
			if (!((MethodDecl)stmt.methodRef.decl).isStatic) {
				stmt.methodRef.visit(this, null);
				if (stmt.methodRef instanceof QualRef) {
					((QualRef)stmt.methodRef).ref.visit(this, null);
				} else {
					visitThisRef((ThisRef)stmt.methodRef, null);
				}
				callAddr = Machine.nextInstrAddr();
				Machine.emit(Op.CALLI, Machine.Reg.CB, 0);
				methodsToPatch.add(new MethodPatchInfo(((MethodDecl)stmt.methodRef.decl), callAddr));
			} else {
				Machine.emit(Op.CALL, Machine.Reg.CB, 0);
				methodsToPatch.add(new MethodPatchInfo(((MethodDecl)stmt.methodRef.decl), callAddr));
			}
		}
		if (stmt.methodRef.decl.type.typeKind != TypeKind.VOID) {
			Machine.emit(Op.POP, 1);
		}
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		if (stmt.returnExpr != null) {
			stmt.returnExpr.visit(this, null);
			Machine.emit(Op.RETURN, 1, 0, currentParaCount);
		} else {
			Machine.emit(Op.RETURN, 0, 0, currentParaCount);
		}
		
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		int addrJump1 = Machine.nextInstrAddr();
		Machine.emit(Op.JUMPIF, 0, Machine.Reg.CB, 0);
		stmt.thenStmt.visit(this, null);
		int addrJump2 = Machine.nextInstrAddr();
		Machine.emit(Op.JUMP, 0, Machine.Reg.CB, 0);
		Machine.patch(addrJump1, Machine.nextInstrAddr());
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, null);
		}
		Machine.patch(addrJump2, Machine.nextInstrAddr());
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		int addrJump1 = Machine.nextInstrAddr();
		Machine.emit(Op.JUMP, 0, Machine.Reg.CB, 0);
		stmt.body.visit(this, null);
		int addrJump2 = Machine.nextInstrAddr();
		stmt.cond.visit(this, null);
		Machine.emit(Op.JUMPIF, 1, Machine.Reg.CB, addrJump1+1);
		Machine.patch(addrJump1, addrJump2);
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		if (expr.operator.kind == TokenKind.MINUS) {
			Machine.emit(Op.LOADL, 0);
		}
		expr.expr.visit(this, null);
		expr.operator.visit(this, null);
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		int skipCondition;
		switch (expr.operator.kind) {
		case OR:
			expr.left.visit(this, null);
			Machine.emit(Op.LOAD, Machine.Reg.ST, -1);
			skipCondition = Machine.nextInstrAddr();
			Machine.emit(Op.JUMPIF, 1, Machine.Reg.CB, 0);
			expr.right.visit(this, null);
			expr.operator.visit(this, null);
			Machine.patch(skipCondition, Machine.nextInstrAddr());
			break;
		case AND:
			expr.left.visit(this, null);
			Machine.emit(Op.LOAD, Machine.Reg.ST, -1);
			skipCondition = Machine.nextInstrAddr();
			Machine.emit(Op.JUMPIF, 0, Machine.Reg.CB, 0);
			expr.right.visit(this, null);
			expr.operator.visit(this, null);
			Machine.patch(skipCondition, Machine.nextInstrAddr());
			break;
		default:
			expr.left.visit(this, null);
			expr.right.visit(this, null);
			expr.operator.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		if (expr.ref.decl instanceof FieldDecl && ((FieldDecl)expr.ref.decl).isStatic) {
			Machine.emit(Op.LOAD, Machine.Reg.SB, expr.ref.decl.red.offsetSize);
		} else if (expr.ref instanceof ThisRef) {
			Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
		} else if (expr.ref instanceof IdRef || expr.ref instanceof QualRef) {
			expr.ref.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		expr.ref.visit(this, null);
		expr.ixExpr.visit(this, null);
		Machine.emit(Prim.arrayref);
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		for (Expression e: expr.argList) {
			e.visit(this, null);
		}
		if (expr.functionRef.decl instanceof MethodDecl && !((MethodDecl)expr.functionRef.decl).name.equals("println")) {
			expr.functionRef.visit(this, null);
			int callAddr = Machine.nextInstrAddr();
			if (!((MethodDecl)expr.functionRef.decl).isStatic) {
				if (expr.functionRef instanceof QualRef) {
					((QualRef)expr.functionRef).visit(this, null);
				} else {
					Machine.emit(Op.LOADA, Machine.Reg.OB, 0);
				}
				callAddr = Machine.nextInstrAddr();
				Machine.emit(Op.CALLI, Machine.Reg.OB, 0);
				methodsToPatch.add(new MethodPatchInfo((MethodDecl)expr.functionRef.decl, callAddr));
			} else {
				Machine.emit(Op.CALL, Machine.Reg.CB, 0);
				methodsToPatch.add(new MethodPatchInfo((MethodDecl)expr.functionRef.decl, callAddr));
			}
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
		Machine.emit(Op.LOADL, -1);
		Machine.emit(Op.LOADL, expr.classtype.className.decl.red.offsetSize);
		Machine.emit(Prim.newobj);
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		expr.sizeExpr.visit(this, null);
		Machine.emit(Prim.newarr);
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		Machine.emit(Op.LOAD, Machine.Reg.OB, 0);
		return null;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		if (ref.decl instanceof FieldDecl) { 
			if (((FieldDecl)ref.decl).isStatic) {
				Machine.emit(Op.LOAD, Machine.Reg.SB, ref.id.decl.red.offsetSize);
			} else {
				Machine.emit(Op.LOAD, Machine.Reg.OB, ref.id.decl.red.offsetSize);
			}
		} else if (ref.id.decl.red != null) {
			if (ref.id.decl instanceof MethodDecl && ((MethodDecl)ref.id.decl).isStatic) {
				Machine.emit(Op.LOAD, Machine.Reg.SB, ref.id.decl.red.offsetSize);
			} else if (!(ref.id.decl instanceof MethodDecl)) {
				Machine.emit(Op.LOAD, Machine.Reg.LB, ref.id.decl.red.offsetSize);
			}
		}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		if (ref.ref.decl.type.typeKind == TypeKind.ARRAY && ref.id.spelling.equals("length")) {
			IdRef qPort = ((IdRef)ref.ref);
			if (qPort.decl instanceof FieldDecl) { 
				if (((FieldDecl)qPort.decl).isStatic) {
					Machine.emit(Op.LOAD, Machine.Reg.SB, qPort.id.decl.red.offsetSize);
				} else {
					Machine.emit(Op.LOAD, Machine.Reg.OB, qPort.id.decl.red.offsetSize);
				}
			} else if (qPort.id.decl.red != null) {
				if (qPort.id.decl instanceof MethodDecl && ((MethodDecl)qPort.id.decl).isStatic) {
					Machine.emit(Op.LOAD, Machine.Reg.SB, qPort.id.decl.red.offsetSize);
				} else if (!(qPort.id.decl instanceof MethodDecl)) {
					Machine.emit(Op.LOAD, Machine.Reg.LB, qPort.id.decl.red.offsetSize);
				}
			}
			Machine.emit(Prim.arraylen);
		} else if (ref.id.decl.red != null) {
			Stack<Integer> fieldOffSets = new Stack<Integer>();
			if (ref.id.decl.red != null) {
				fieldOffSets.push(ref.id.decl.red.offsetSize);
				while (ref.ref instanceof QualRef) {
					ref = (QualRef) ref.ref;
					fieldOffSets.push(ref.decl.red.offsetSize);
				}
				ref.ref.visit(this, null);
				int fieldOffSetsSize = fieldOffSets.size();
				for (int i = 0; i < fieldOffSetsSize; i++) {
					int offSet = fieldOffSets.pop();
					Machine.emit(Op.LOADL, offSet);
					if (i < fieldOffSetsSize-1) {
						Machine.emit(Prim.fieldref);
					}
				}
			}
			Machine.emit(Prim.fieldref);
		}
		return null;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		return null;
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		switch (op.kind) {
		case OR:
			Machine.emit(Prim.or);
			break;
		case AND:
			Machine.emit(Prim.and);
			break;
		case EQUAL:
			Machine.emit(Prim.eq);
			break;
		case NOTEQUAL:
			Machine.emit(Prim.ne);
			break;
		case LESSEQUAL:
			Machine.emit(Prim.le);
			break;
		case LESS:
			Machine.emit(Prim.lt);
			break;
		case GREATEREQUAL:
			Machine.emit(Prim.ge);
			break;
		case GREATER:
			Machine.emit(Prim.gt);
			break;
		case PLUS:
			Machine.emit(Prim.add);
			break;
		case MINUS:
			Machine.emit(Prim.sub);
			break;
		case TIMES:
			Machine.emit(Prim.mult);
			break;
		case DIVIDE:
			Machine.emit(Prim.div);
			break;
		case NOT:
			Machine.emit(Prim.not);
			break;
		default:
			reporter.reportError("*** Failed to identify an operator in code generation");
			System.exit(4);
		}
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		Machine.emit(Op.LOADL, Integer.parseInt(num.spelling));
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		if (bool.spelling.equals("false")) {
			Machine.emit(Op.LOADL, Machine.falseRep);
		} else {
			Machine.emit(Op.LOADL, Machine.trueRep);
		}
		return null;
	}

	@Override
	public Object visitNullLiteral(NullLiteral nul, Object arg) {
		Machine.emit(Op.LOADL, Machine.nullRep);
		return null;
	}
	

}
