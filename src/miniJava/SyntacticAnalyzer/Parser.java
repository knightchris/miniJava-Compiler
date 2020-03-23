package miniJava.SyntacticAnalyzer;

import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

public class Parser {
	
	private Scanner scanner;
	private ErrorReporter reporter;
	private Token token;
	private boolean trace = true;
	
	public Parser(Scanner scanner, ErrorReporter reporter) {
		this.scanner = scanner;
		this.reporter = reporter;
	}
	
	/**
	 * SyntaxError is used to unwind parse stack when parse fails
	 *
	 */
	class SyntaxError extends Error {
		private static final long serialVersionUID = 1L;	
	}
	
	/**
	 *  parse input, catch possible parse error
	 */
	public Package parse() {
		token = scanner.scan();
		try {
			return parseProgram();
		}
		catch (SyntaxError e) {return null;}
	}

	// Program ::= (ClassDeclaration)* eot
	private Package parseProgram() throws SyntaxError {
		ClassDeclList classList = new ClassDeclList();
		SourcePosition posn = scanner.getPos();
		while (token.kind != TokenKind.EOT) {
			classList.add(parseClassDeclaration());
		}
		accept(TokenKind.EOT);
		return new Package(classList, posn);
	}

	/*
	ClassDeclaration ::= class id { ( ( public | private )? static? 
			(Type id ( ; | ( ParameterList? ) {Statement*} )
			   |  void id ( ParameterList? ) {Statement*}  ) )* 
	*/
	private ClassDecl parseClassDeclaration() throws SyntaxError {
		accept(TokenKind.CLASS);
		String className = token.spelling;
		MethodDeclList methodList = new MethodDeclList();
		FieldDeclList fieldList = new FieldDeclList();
		SourcePosition posn = scanner.getPos();
		accept(TokenKind.ID);
		accept(TokenKind.LBRACE);
		while (token.kind != TokenKind.RBRACE) {
			boolean isPrivate = false;
			boolean isStatic = false;
			StatementList stmtList = new StatementList();
			ParameterDeclList paraList = new ParameterDeclList();
			SourcePosition fieldOrMethPosn = scanner.getPos();
			if (token.kind == TokenKind.PRIVATE) {
				acceptIt();
				isPrivate = true;
			} else if (token.kind == TokenKind.PUBLIC) {
				acceptIt();
			}
			if (token.kind == TokenKind.STATIC) {
				acceptIt();	
				isStatic = true;
			}
			switch (token.kind) {
			case INT: case ID: case BOOLEAN: // type field/method
				TypeDenoter type = parseType();
				String fieldName = token.spelling;
				accept(TokenKind.ID); 
				switch (token.kind) {
				case SEMICOL: // fielddecl
					fieldList.add(new FieldDecl(isPrivate, isStatic, type, fieldName, fieldOrMethPosn));
					acceptIt();
					break;
				case LPAREN: // methoddecl
					acceptIt();
					if (token.kind != TokenKind.RPAREN) {
						paraList = parseParameterList();
						accept(TokenKind.RPAREN);
					} else {
						// no parameters
						accept(TokenKind.RPAREN);
					}
					accept(TokenKind.LBRACE);
					while (token.kind != TokenKind.RBRACE) {
						stmtList.add(parseStatement());
					}
					accept(TokenKind.RBRACE);
					MemberDecl methodField = new FieldDecl(isPrivate, isStatic, type, fieldName, fieldOrMethPosn);
					methodList.add(new MethodDecl(methodField, paraList, stmtList, fieldOrMethPosn));
					break;
				default:
					parseError("Invalid Term - expecting SEMICOL or LPAREN but found " + token.kind);
				}
				break;
			case VOID: // void method
				acceptIt();
				TypeDenoter voidType = new BaseType(TypeKind.VOID, scanner.getPos());
				String methName = token.spelling;
				accept(TokenKind.ID); 
				accept(TokenKind.LPAREN);
				if (token.kind != TokenKind.RPAREN) {
					paraList = parseParameterList();
					accept(TokenKind.RPAREN);
				} else {
					// no parameters
					accept(TokenKind.RPAREN);
				}
				accept(TokenKind.LBRACE);
				while (token.kind != TokenKind.RBRACE) {
					stmtList.add(parseStatement());
				}
				accept(TokenKind.RBRACE);
				MemberDecl methodField = new FieldDecl(isPrivate, isStatic, voidType, methName, fieldOrMethPosn);
				methodList.add(new MethodDecl(methodField, paraList, stmtList, fieldOrMethPosn));
				break;
			default:
				parseError("Invalid Term - expecting TYPE or VOID but found " + token.kind);
			}
		}
		accept(TokenKind.RBRACE);
		return new ClassDecl(className, fieldList, methodList, posn);
		}
	
	// Type ::= ( int ( [] )? | id ( [] )? | Boolean )
	private TypeDenoter parseType() throws SyntaxError {
		TypeDenoter type = null;
		switch(token.kind) {
		case INT:  
			acceptIt();
			type = new BaseType(TypeKind.INT, scanner.getPos());
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
				type = new ArrayType(type, scanner.getPos());
			}
			break;
		case ID:
			type = new ClassType(new Identifier(token, scanner.getPos()), scanner.getPos());
			acceptIt();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
				type = new ArrayType(type, scanner.getPos());
			}
			break;
		case BOOLEAN:
			acceptIt();
			type = new BaseType(TypeKind.BOOLEAN, scanner.getPos());
			break;
		default:
			parseError("Invalid Term - expecting TYPE but found " + token.kind);
		}
		return type;
	}
	
	// ParameterList ::= Type id ( , Type id)*  
	private ParameterDeclList parseParameterList() throws SyntaxError {
		ParameterDeclList paraList = new ParameterDeclList();
		TypeDenoter type = parseType();
		String paraName = token.spelling;
		paraList.add(new ParameterDecl(type, paraName, scanner.getPos()));
		accept(TokenKind.ID);
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			type = parseType();
			paraName = token.spelling;
			paraList.add(new ParameterDecl(type, paraName, scanner.getPos()));
			accept(TokenKind.ID);
		}
		return paraList;
	}
	
	// ArgumentList ::= Expression ( , Expression )*
		private ExprList parseArgumentList() throws SyntaxError {
			ExprList argList = new ExprList();
			argList.add(parseExpression());
			while (token.kind == TokenKind.COMMA) {
				acceptIt();
				argList.add(parseExpression());
			}
			return argList;
		}
	
	// Reference ::= ( id | this ) ( . id )*
	private Reference parseReference() throws SyntaxError {
		Reference ref = null;
		switch(token.kind) {
		case ID: 
			ref = new IdRef(new Identifier(token, scanner.getPos()), scanner.getPos());
			acceptIt();
			while (token.kind == TokenKind.DOT) {
				acceptIt();
				Identifier id = new Identifier(token, scanner.getPos());
				ref = new QualRef(ref, id, scanner.getPos());
				accept(TokenKind.ID);
			}
			break;
		case THIS:
			ref = new ThisRef(scanner.getPos());
			acceptIt();
			while (token.kind == TokenKind.DOT) {
				acceptIt();
				Identifier id = new Identifier(token, scanner.getPos());
				ref = new QualRef(ref, id, scanner.getPos());
				accept(TokenKind.ID);
			}
			break;
		default: 
			parseError("Invalid Term - expecting ID or THIS but found " + token.kind);
		}
		return ref;
	}
	
	/*
	Statement ::= { Statement* }  
                        | ( int ( [] )? | id ( [] )? | Boolean ) id = Expression ;  
                        | Reference = Expression ;  
                        | Reference [ Expression ] = Expression ;  
                        | Reference ( ArgumentList? ) ;  
                        | return Expression? ;  
                        | if ( Expression ) Statement (else Statement)?  
                        | while ( Expression ) Statement  
	 */
	private Statement parseStatement() throws SyntaxError {
		StatementList stmtList;
		TypeDenoter type;
		Identifier id;
		Identifier typeId;
		Expression resultExpr;
		String varName;
		VarDecl variable;
		Expression condition;
		Statement thenStmt;
		Statement elseStmt;
		Statement whileBody;
		Reference ref;
		Expression idxExpr;
		ExprList argList;
		SourcePosition stmtPosn = scanner.getPos();
		switch(token.kind) {
		case LBRACE:
			acceptIt();
			stmtList = new StatementList();
			while (token.kind != TokenKind.RBRACE) {
				stmtList.add(parseStatement());
			}
			accept(TokenKind.RBRACE);
			return new BlockStmt(stmtList, stmtPosn);
		case INT:
			acceptIt();
			type = new BaseType(TypeKind.INT, scanner.getPos()); // int
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
				type = new ArrayType(type, scanner.getPos()); // int[]
			}
			varName = token.spelling;
			accept(TokenKind.ID);
			accept(TokenKind.ASSIGN);
			resultExpr = parseExpression();
			accept(TokenKind.SEMICOL);
			variable = new VarDecl(type, varName, stmtPosn);
			return new VarDeclStmt(variable, resultExpr, stmtPosn);
		case BOOLEAN:
			acceptIt();
			type = new BaseType(TypeKind.BOOLEAN, scanner.getPos());
			varName = token.spelling;
			accept(TokenKind.ID);
			accept(TokenKind.ASSIGN);
			resultExpr = parseExpression();
			accept(TokenKind.SEMICOL);
			variable = new VarDecl(type, varName, stmtPosn);
			return new VarDeclStmt(variable, resultExpr, stmtPosn);
		case RETURN:
			acceptIt();
			if (token.kind == TokenKind.SEMICOL) {
				acceptIt();
				return new ReturnStmt(null, stmtPosn);
			} else {
				resultExpr = parseExpression();
				accept(TokenKind.SEMICOL);
				return new ReturnStmt(resultExpr, stmtPosn);
			}
		case IF:
			acceptIt();
			accept(TokenKind.LPAREN);
			condition = parseExpression();
			accept(TokenKind.RPAREN);
			thenStmt = parseStatement();
			if (token.kind == TokenKind.ELSE) {
				acceptIt();
				elseStmt = parseStatement();
				return new IfStmt(condition, thenStmt, elseStmt, stmtPosn);
			}
			return new IfStmt(condition, thenStmt, stmtPosn);
		case WHILE:
			acceptIt();
			accept(TokenKind.LPAREN);
			condition = parseExpression();
			accept(TokenKind.RPAREN);
			whileBody = parseStatement();
			return new WhileStmt(condition, whileBody, stmtPosn);
		case THIS:
			ref = parseReference();
			switch(token.kind) {
			case ASSIGN:
				acceptIt();
				resultExpr = parseExpression();
				accept(TokenKind.SEMICOL);
				return new AssignStmt(ref, resultExpr, stmtPosn);
			case LBRACKET:
				acceptIt();
				idxExpr = parseExpression();
				accept(TokenKind.RBRACKET);
				accept(TokenKind.ASSIGN);
				resultExpr = parseExpression();
				accept(TokenKind.SEMICOL);
				return new IxAssignStmt(ref, idxExpr, resultExpr, stmtPosn);
			case LPAREN:
				acceptIt();
				argList = new ExprList();
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					argList = parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				accept(TokenKind.SEMICOL);
				return new CallStmt(ref, argList, stmtPosn);
			default:
				parseError("Invalid Term - expecting ASSIGN/LBRACKET/LPAREN but found " + token.kind);
				return null;
			}
		case ID:
			typeId = new Identifier(token, scanner.getPos());
			ref = new IdRef(typeId, scanner.getPos());
			acceptIt();
			switch(token.kind) {
			case ID: // id id = Expression;
				varName = token.spelling;
				acceptIt();
				accept(TokenKind.ASSIGN);
				resultExpr = parseExpression();
				accept(TokenKind.SEMICOL);
				variable = new VarDecl(new ClassType(typeId, scanner.getPos()), varName, stmtPosn);
				return new VarDeclStmt(variable, resultExpr, stmtPosn);
			case ASSIGN: // id = Expression;
				acceptIt();
				resultExpr = parseExpression();
				accept(TokenKind.SEMICOL);
				return new AssignStmt(ref, resultExpr, stmtPosn);
			case LPAREN: // id (Arglist?);
				acceptIt();
				argList = new ExprList();
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					argList = parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				accept(TokenKind.SEMICOL);
				return new CallStmt(ref, argList, stmtPosn);
			case LBRACKET: // id[] id = expression OR id[expression] = expression
				acceptIt();
				if (token.kind == TokenKind.RBRACKET) { // VarDeclStmt  id[] id = expression
					acceptIt();
					varName = token.spelling;
					accept(TokenKind.ID);
					accept(TokenKind.ASSIGN);
					resultExpr = parseExpression();
					accept(TokenKind.SEMICOL);
					type = new ClassType(typeId, scanner.getPos());
					type = new ArrayType(type, scanner.getPos());                       // had these 4 lines combined, very hard to read -> separate out despite causing more lines
					variable = new VarDecl(type, varName, stmtPosn);
					return new VarDeclStmt(variable, resultExpr, stmtPosn);
				} else {								// IxAssignStmt   id[expression] = expression
					idxExpr = parseExpression();
					accept(TokenKind.RBRACKET);
					accept(TokenKind.ASSIGN);
					resultExpr = parseExpression();
					accept(TokenKind.SEMICOL);
					return new IxAssignStmt(ref, idxExpr, resultExpr, stmtPosn);
				}
			case DOT:
				while (token.kind == TokenKind.DOT) {
					acceptIt();
					id = new Identifier(token, scanner.getPos());
					accept(TokenKind.ID);
					ref = new QualRef(ref, id, scanner.getPos());
				}
				if (token.kind == TokenKind.ASSIGN) { // id (.id)* = Expression;     AssignStmt
					acceptIt();
					resultExpr = parseExpression();
					accept(TokenKind.SEMICOL);
					return new AssignStmt(ref, resultExpr, stmtPosn);
				} else if (token.kind == TokenKind.LBRACKET) { // id (.id)*[Expression] = Expression;  IxAssignStmt
					acceptIt();
					idxExpr = parseExpression();
					accept(TokenKind.RBRACKET);
					accept(TokenKind.ASSIGN);
					resultExpr = parseExpression();
					accept(TokenKind.SEMICOL);
					return new IxAssignStmt(ref, idxExpr, resultExpr, stmtPosn);
				} else if (token.kind == TokenKind.LPAREN) {   // id (.id)* (ArgList?);    CallStmt
					acceptIt();
					argList = new ExprList();
					if (token.kind == TokenKind.RPAREN) {
						acceptIt();
					} else {
						argList = parseArgumentList();
						accept(TokenKind.RPAREN);
					}
					accept(TokenKind.SEMICOL);
					return new CallStmt(ref, argList, stmtPosn);
				} else {
					parseError("Invalid Term - expecting ASSIGN/LBRACKET/LPAREN but found " + token.kind);
					return null;
				}
			default:
				parseError("Invalid Term - expecting ID/ASSIGN/LBRACKET/LPAREN/DOT but found " + token.kind);
				return null;
			}
		default:
			parseError("Invalid Term - expecting ID/THIS/LBRACE/INT/RETURN/IF/WHILE/BOOLEAN but found " + token.kind);
			return null;
		}
	}
	
	// Expression ::= Disjunction  
	private Expression parseExpression() throws SyntaxError {
		return parseDisjunction();
	}
	
	// Disjunction ::= Conjunction ( || Conjunction)* 
	private Expression parseDisjunction() throws SyntaxError {
		Expression resultExpr = parseConjunction();
		while (token.kind == TokenKind.OR) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			Expression additionalExpr = parseConjunction();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, scanner.getPos());
		}
		return resultExpr;
	}

	// Conjunction ::= Equality ( && Equality)* 
	private Expression parseConjunction() throws SyntaxError {
		Expression resultExpr = parseEquality();
		while (token.kind == TokenKind.AND) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			Expression additionalExpr = parseEquality();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, scanner.getPos());
		}
		return resultExpr;
	}

	// Equality ::= Relational ( ( == | != ) Relational)* 
	private Expression parseEquality() throws SyntaxError {
		Expression resultExpr = parseRelational();
		while (token.kind == TokenKind.EQUAL || token.kind == TokenKind.NOTEQUAL) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			Expression additionalExpr = parseRelational();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, scanner.getPos());
		}
		return resultExpr;
	}

	// Relational ::= Additive ( ( <= | < | > |  >= ) Additive)* 
	private Expression parseRelational() throws SyntaxError {
		Expression resultExpr = parseAdditive();
		while (token.kind == TokenKind.LESSEQUAL || token.kind == TokenKind.LESS
			  || token.kind == TokenKind.GREATER || token.kind == TokenKind.GREATEREQUAL) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			Expression additionalExpr = parseAdditive();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, scanner.getPos());
		}
		return resultExpr;
	}

	// Additive ::= Multiplicative ( ( + | - ) Multiplicative)* 
	private Expression parseAdditive() throws SyntaxError {
		Expression resultExpr = parseMultiplicative();
		while (token.kind == TokenKind.PLUS || token.kind == TokenKind.MINUS) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			Expression additionalExpr = parseMultiplicative();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, scanner.getPos());
		}
		return resultExpr;
	}

	// Multiplicative ::= Unary ( ( * | / ) Unary)* 
	private Expression parseMultiplicative() throws SyntaxError {
		Expression resultExpr = parseUnary();
		while (token.kind == TokenKind.TIMES || token.kind == TokenKind.DIVIDE) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			Expression additionalExpr = parseUnary();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, scanner.getPos());
		}
		return resultExpr;
	}
	
	// Unary ::= ( - | ! )* EndExpression 
	private Expression parseUnary() {
		Expression resultExpr;
		if (token.kind == TokenKind.MINUS || token.kind == TokenKind.NOT) {
			Operator op = new Operator(token, scanner.getPos());
			acceptIt();
			resultExpr = new UnaryExpr(op, parseUnary(), scanner.getPos());
			return resultExpr;
		} else {
			return parseEndExpression();
		}
	}

	/*
		EndExpression ::= Reference  
                        | Reference [ Expression ]  
                        | Reference ( ArgumentList? )  
                        | ( Expression )  
                        | num | true | false | null
                        | new ( id() | int [ Expression ] | id [ Expression ] ) 

	 */
	private Expression parseEndExpression() throws SyntaxError {
		Terminal terminal;
		Expression resultExpr;
		Identifier id;
		switch(token.kind) {
		case ID: case THIS:
			Reference ref = parseReference();
			if (token.kind == TokenKind.LBRACKET) { // IxExpr
				acceptIt();
				resultExpr = parseExpression();
				accept(TokenKind.RBRACKET);
				return new IxExpr(ref, resultExpr, scanner.getPos());
			} else if (token.kind == TokenKind.LPAREN) { // CallExpr
				acceptIt(); 
				ExprList argList = new ExprList();
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					argList = parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				return new CallExpr(ref, argList, scanner.getPos());
			} else {
				return new RefExpr(ref, scanner.getPos()); // RefExpr
			}
		case LPAREN:
			acceptIt();
			resultExpr = parseExpression();
			accept(TokenKind.RPAREN);
			return resultExpr;
		case TRUE: case FALSE:
			terminal = new BooleanLiteral(token, scanner.getPos());
			acceptIt();
			return new LiteralExpr(terminal, scanner.getPos());
		case NUM:
			terminal = new IntLiteral(token, scanner.getPos());
			acceptIt();
			return new LiteralExpr(terminal, scanner.getPos());
		case NULL:
			terminal = new NullLiteral(token, scanner.getPos());
			acceptIt();
			return new LiteralExpr(terminal, scanner.getPos());
		case NEW:
			acceptIt();
			if (token.kind == TokenKind.ID) {
				id = new Identifier(token, scanner.getPos());
				acceptIt();
				if (token.kind == TokenKind.LPAREN) { // NewObjectExpr
					acceptIt();
					accept(TokenKind.RPAREN);
					return new NewObjectExpr(new ClassType(id, scanner.getPos()), scanner.getPos());
				} else if (token.kind == TokenKind.LBRACKET) { // NewArrayExpr
					acceptIt();
					resultExpr = parseExpression();
					accept(TokenKind.RBRACKET);
					return new NewArrayExpr(new ClassType(id, scanner.getPos()), resultExpr, scanner.getPos());
				} else {
					parseError("Invalid Term - expecting LPAREN or LBRACKET but found " + token.kind);
					return null;
				}
			} else if (token.kind == TokenKind.INT) { // NewArrayExpr
				acceptIt();
				accept(TokenKind.LBRACKET);
				resultExpr = parseExpression();
				accept(TokenKind.RBRACKET);
				return new NewArrayExpr(new BaseType(TypeKind.INT, scanner.getPos()), resultExpr, scanner.getPos());
			} else {
				parseError("Invalid Term - expecting ID or INT but found " + token.kind);
				return null;
			}
		default:
			parseError("Invalid Term - expecting ID/THIS/NOT/MINUS/LPAREN/NUM/TRUE/FALSE/NEW "
					+ "but found " + token.kind);
			return null;
		}
	}
	
	
	/**
	 * accept current token and advance to next token
	 */
	private void acceptIt() throws SyntaxError {
		accept(token.kind);
	}

	/**
	 * verify that current token in input matches expected token and advance to next token
	 * @param expectedToken
	 * @throws SyntaxError  if match fails
	 */
	private void accept(TokenKind expectedTokenKind) throws SyntaxError {
		if (token.kind == expectedTokenKind) {
			if (trace)
				pTrace();
			token = scanner.scan();
		}
		else
			parseError("expecting '" + expectedTokenKind +
					"' but found '" + token.kind + "'");
	}

	
	/**
	 * report parse error and unwind call stack to start of parse
	 * @param e  string with error detail
	 * @throws SyntaxError
	 */
	private void parseError(String e) throws SyntaxError {
		reporter.reportError("Parse error: " + e);
		throw new SyntaxError();
	}

	// show parse stack whenever terminal is  accepted
		private void pTrace() {
			StackTraceElement [] stl = Thread.currentThread().getStackTrace();
			for (int i = stl.length - 1; i > 0 ; i--) {
				if(stl[i].toString().contains("parse"))
					System.out.println(stl[i]);
			}
			System.out.println("accepting: " + token.kind + " (\"" + token.spelling + "\")");
			System.out.println();
		}

}
