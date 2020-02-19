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
		while (token.kind != TokenKind.EOT) {
			classList.add(parseClassDeclaration());
		}
		accept(TokenKind.EOT);
		return new Package(classList, null);
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
		accept(TokenKind.ID);
		accept(TokenKind.LBRACE);
		while (token.kind != TokenKind.RBRACE) {
			boolean isPrivate = false;
			boolean isStatic = false;
			StatementList stmtList = new StatementList();
			ParameterDeclList paraList = new ParameterDeclList();
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
					fieldList.add(new FieldDecl(isPrivate, isStatic, type, fieldName, null));
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
					MemberDecl methodField = new FieldDecl(isPrivate, isStatic, type, fieldName, null);
					methodList.add(new MethodDecl(methodField, paraList, stmtList, null));
					break;
				default:
					parseError("Invalid Term - expecting SEMICOL or LPAREN but found " + token.kind);
				}
				break;
			case VOID: // void method
				acceptIt();
				TypeDenoter voidType = new BaseType(TypeKind.VOID, null);
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
				MemberDecl methodField = new FieldDecl(isPrivate, isStatic, voidType, methName, null);
				methodList.add(new MethodDecl(methodField, paraList, stmtList, null));
				break;
			default:
				parseError("Invalid Term - expecting TYPE or VOID but found " + token.kind);
			}
		}
		accept(TokenKind.RBRACE);
		return new ClassDecl(className, fieldList, methodList, null);
		}
	
	// Type ::= ( int ( [] )? | id ( [] )? | Boolean )
	private TypeDenoter parseType() throws SyntaxError {
		TypeDenoter type = null;
		switch(token.kind) {
		case INT:  
			acceptIt();
			type = new BaseType(TypeKind.INT, null);
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
				type = new ArrayType(type, null);
			}
			break;
		case ID:
			type = new ClassType(new Identifier(token), null);
			acceptIt();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
				type = new ArrayType(type, null);
			}
			break;
		case BOOLEAN:
			acceptIt();
			type = new BaseType(TypeKind.BOOLEAN, null);
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
		paraList.add(new ParameterDecl(type, paraName, null));
		accept(TokenKind.ID);
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			type = parseType();
			paraName = token.spelling;
			paraList.add(new ParameterDecl(type, paraName, null));
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
			ref = new IdRef(new Identifier(token), null);
			acceptIt();
			while (token.kind == TokenKind.DOT) {
				acceptIt();
				Identifier id = new Identifier(token);
				ref = new QualRef(ref, id, null);
				accept(TokenKind.ID);
			}
			break;
		case THIS:
			ref = new ThisRef(null);
			acceptIt();
			while (token.kind == TokenKind.DOT) {
				acceptIt();
				Identifier id = new Identifier(token);
				ref = new QualRef(ref, id, null);
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
		switch(token.kind) {
		case LBRACE:
			acceptIt();
			while (token.kind != TokenKind.RBRACE) {
				parseStatement();
			}
			accept(TokenKind.RBRACE);
			break;
		case INT:
			acceptIt();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
			}
			accept(TokenKind.ID);
			accept(TokenKind.ASSIGN);
			parseExpression();
			accept(TokenKind.SEMICOL);
			break;
		case BOOLEAN:
			acceptIt();
			accept(TokenKind.ID);
			accept(TokenKind.ASSIGN);
			parseExpression();
			accept(TokenKind.SEMICOL);
			break;
		case RETURN:
			acceptIt();
			if (token.kind == TokenKind.SEMICOL) {
				acceptIt();
			} else {
				parseExpression();
				accept(TokenKind.SEMICOL);
			}
			break;
		case IF:
			acceptIt();
			accept(TokenKind.LPAREN);
			parseExpression();
			accept(TokenKind.RPAREN);
			parseStatement();
			if (token.kind == TokenKind.ELSE) {
				acceptIt();
				parseStatement();
			}
			break;
		case WHILE:
			acceptIt();
			accept(TokenKind.LPAREN);
			parseExpression();
			accept(TokenKind.RPAREN);
			parseStatement();
			break;
		case THIS:
			parseReference();
			switch(token.kind) {
			case ASSIGN:
				acceptIt();
				parseExpression();
				accept(TokenKind.SEMICOL);
				break;
			case LBRACKET:
				acceptIt();
				parseExpression();
				accept(TokenKind.RBRACKET);
				accept(TokenKind.ASSIGN);
				parseExpression();
				accept(TokenKind.SEMICOL);
				break;
			case LPAREN:
				acceptIt();
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				accept(TokenKind.SEMICOL);
				break;
			default:
				parseError("Invalid Term - expecting ASSIGN/LBRACKET/LPAREN but found " + token.kind);
			}
			break;
		case ID:
			acceptIt();
			switch(token.kind) {
			case ID:
				acceptIt();
				accept(TokenKind.ASSIGN);
				parseExpression();
				accept(TokenKind.SEMICOL);
				break;
			case ASSIGN:
				acceptIt();
				parseExpression();
				accept(TokenKind.SEMICOL);
				break;
			case LPAREN:
				acceptIt();
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				accept(TokenKind.SEMICOL);
				break;
			case LBRACKET:
				acceptIt();
				if (token.kind == TokenKind.RBRACKET) {
					acceptIt();
					accept(TokenKind.ID);
					accept(TokenKind.ASSIGN);
					parseExpression();
					accept(TokenKind.SEMICOL);
				} else {
					parseExpression();
					accept(TokenKind.RBRACKET);
					accept(TokenKind.ASSIGN);
					parseExpression();
					accept(TokenKind.SEMICOL);
				}
				break;
			case DOT:
				while (token.kind == TokenKind.DOT) {
					acceptIt();
					accept(TokenKind.ID);
				}
				if (token.kind == TokenKind.ASSIGN) {
					acceptIt();
					parseExpression();
					accept(TokenKind.SEMICOL);
				} else if (token.kind == TokenKind.LBRACKET) {
					acceptIt();
					parseExpression();
					accept(TokenKind.RBRACKET);
					accept(TokenKind.ASSIGN);
					parseExpression();
					accept(TokenKind.SEMICOL);
				} else if (token.kind == TokenKind.LPAREN) {
					acceptIt();
					if (token.kind == TokenKind.RPAREN) {
						acceptIt();
					} else {
						parseArgumentList();
						accept(TokenKind.RPAREN);
					}
					accept(TokenKind.SEMICOL);
				} else {
					parseError("Invalid Term - expecting ASSIGN/LBRACKET/LPAREN but found " + token.kind);
				}
				break;
			default:
				parseError("Invalid Term - expecting ID/ASSIGN/LBRACKET/LPAREN/DOT but found " + token.kind);
			}
			break;
		default:
			parseError("Invalid Term - expecting ID/THIS/LBRACE/INT/RETURN/IF/WHILE/BOOLEAN but found " + token.kind);
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
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseConjunction();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}

	// Conjunction ::= Equality ( && Equality)* 
	private Expression parseConjunction() throws SyntaxError {
		Expression resultExpr = parseEquality();
		while (token.kind == TokenKind.AND) {
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseEquality();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}

	// Equality ::= Relational ( ( == | != ) Relational)* 
	private Expression parseEquality() throws SyntaxError {
		Expression resultExpr = parseRelational();
		while (token.kind == TokenKind.EQUAL || token.kind == TokenKind.NOTEQUAL) {
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseRelational();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}

	// Relational ::= Additive ( ( <= | < | > |  >= ) Additive)* 
	private Expression parseRelational() throws SyntaxError {
		Expression resultExpr = parseAdditive();
		while (token.kind == TokenKind.LESSEQUAL || token.kind == TokenKind.LESS
			  || token.kind == TokenKind.GREATER || token.kind == TokenKind.GREATEREQUAL) {
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseAdditive();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}

	// Additive ::= Multiplicative ( ( + | - ) Multiplicative)* 
	private Expression parseAdditive() throws SyntaxError {
		Expression resultExpr = parseMultiplicative();
		while (token.kind == TokenKind.PLUS || token.kind == TokenKind.MINUS) {
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseMultiplicative();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}

	// Multiplicative ::= Unary ( ( * | / ) Unary)* 
	private Expression parseMultiplicative() throws SyntaxError {
		Expression resultExpr = parseUnary();
		while (token.kind == TokenKind.TIMES || token.kind == TokenKind.DIVIDE) {
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseUnary();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}
	
	// Unary ::= EndExpression ( ( - | ! ) EndExpression )* 
	private Expression parseUnary() {
		Expression resultExpr = parseEndExpression();
		while (token.kind == TokenKind.MINUS || token.kind == TokenKind.NOT) {
			Operator op = new Operator(token);
			acceptIt();
			Expression additionalExpr = parseEndExpression();
			resultExpr = new BinaryExpr(op, resultExpr, additionalExpr, null);
		}
		return resultExpr;
	}

	/*
		EndExpression ::= Reference  
                        | Reference [ Expression ]  
                        | Reference ( ArgumentList? )  
                        | ( Expression )  
                        | num | true | false  
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
				return new IxExpr(ref, resultExpr, null);
			} else if (token.kind == TokenKind.LPAREN) { // CallExpr
				acceptIt(); 
				ExprList argList = new ExprList();
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					argList = parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				return new CallExpr(ref, argList, null);
			} else {
				return new RefExpr(ref, null); // RefExpr
			}
		case LPAREN:
			acceptIt();
			resultExpr = parseExpression();
			accept(TokenKind.RPAREN);
			return resultExpr;
		case TRUE: case FALSE:
			terminal = new BooleanLiteral(token);
			acceptIt();
			return new LiteralExpr(terminal, null);
		case NUM:
			terminal = new IntLiteral(token);
			acceptIt();
			return new LiteralExpr(terminal, null);
		case NEW:
			acceptIt();
			if (token.kind == TokenKind.ID) {
				id = new Identifier(token);
				acceptIt();
				if (token.kind == TokenKind.LPAREN) { // NewObjectExpr
					acceptIt();
					accept(TokenKind.RPAREN);
					return new NewObjectExpr(new ClassType(id, null), null);
				} else if (token.kind == TokenKind.LBRACKET) { // NewArrayExpr
					acceptIt();
					resultExpr = parseExpression();
					accept(TokenKind.RBRACKET);
					return new NewArrayExpr(new ClassType(id, null), resultExpr, null);
				} else {
					parseError("Invalid Term - expecting LPAREN or LBRACKET but found " + token.kind);
					return null;
				}
			} else if (token.kind == TokenKind.INT) { // NewArrayExpr
				acceptIt();
				accept(TokenKind.LBRACKET);
				resultExpr = parseExpression();
				accept(TokenKind.RBRACKET);
				return new NewArrayExpr(new BaseType(TypeKind.INT, null), resultExpr, null);
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
