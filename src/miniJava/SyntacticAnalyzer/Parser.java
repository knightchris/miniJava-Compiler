package miniJava.SyntacticAnalyzer;

import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.SyntacticAnalyzer.Scanner;
import miniJava.ErrorReporter;

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
	public void parse() {
		token = scanner.scan();
		try {
			parseProgram();
		}
		catch (SyntaxError e) { }
	}

	// Program ::= (ClassDeclaration)* eot
	private void parseProgram() throws SyntaxError {
		while (token.kind != TokenKind.EOT) {
			parseClassDeclaration();
		}
		accept(TokenKind.EOT);
	}

	// ClassDeclaration ::= class id { (FieldOrMethDec)* }
	private void parseClassDeclaration() throws SyntaxError {
		accept(TokenKind.CLASS);
		accept(TokenKind.ID);
		accept(TokenKind.LBRACE);
		while (token.kind != TokenKind.RBRACE) {
			parseFieldOrMethDec();
		}
		accept(TokenKind.RBRACE);
		}
	
	
	// FieldOrMethDec ::= ( public | private )? Access
	private void parseFieldOrMethDec() throws SyntaxError {
		switch (token.kind) {
		case PUBLIC: case PRIVATE:
			acceptIt();
			parseAccess();
			break;
		default:
			parseAccess();
		}
	}
	
	// Access ::= static? ( ( int ( [] )? | id ( [] )? | Boolean ) TypeReturn | void VoidReturn)
	private void parseAccess() throws SyntaxError {
		if (token.kind == TokenKind.STATIC) {
			acceptIt();	
		}
		switch (token.kind) {
		case INT: case ID:
			acceptIt();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
			}
			parseTypeReturn();
			break;
		case BOOLEAN:
			acceptIt();
			parseTypeReturn();
			break;
		case VOID:
			acceptIt();
			parseVoidReturn();
			break;
		default:
			parseError("Invalid Term - expecting TYPE or VOID but found " + token.kind);
		}
	}
	
	// TypeReturn ::= id ( ; | ( ParameterList? ) {Statement*} )
	private void parseTypeReturn() throws SyntaxError {
		accept(TokenKind.ID); 
		switch (token.kind) {
		case SEMICOL:
			acceptIt();
			return;
		case LPAREN:
			acceptIt();
			if (token.kind != TokenKind.RPAREN) {
				parseParameterList();
				accept(TokenKind.RPAREN);
			} else {
				accept(TokenKind.RPAREN);
			}
			accept(TokenKind.LBRACE);
			while (token.kind != TokenKind.RBRACE) {
				parseStatement();
			}
			accept(TokenKind.RBRACE);
			break;
		default:
			parseError("Invalid Term - expecting SEMICOL or LPAREN but found " + token.kind);
		}
	}

	// VoidReturn ::= id ( ParameterList? ) {Statement*}
	private void parseVoidReturn() throws SyntaxError {
		accept(TokenKind.ID); 
		accept(TokenKind.LPAREN);
		if (token.kind != TokenKind.RPAREN) {
			parseParameterList();
			accept(TokenKind.RPAREN);
		} else {
			accept(TokenKind.RPAREN);
		}
		accept(TokenKind.LBRACE);
		while (token.kind != TokenKind.RBRACE) {
			parseStatement();
		}
		accept(TokenKind.RBRACE);
	}
	
	// ParameterList ::= ( int ( [] )? | id ( [] )? | Boolean ) id ( , ( int ( [] )? | id ( [] )? | Boolean ) id)*
	private void parseParameterList() throws SyntaxError {
		switch (token.kind) {
		case INT: case ID:
			acceptIt();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
			}
			break;
		case BOOLEAN:
			acceptIt();
			break;
		default:
			parseError("Invalid Term - expecting TYPE but found " + token.kind);
		}	
		accept(TokenKind.ID);
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			switch (token.kind) {
			case INT: case ID:
				acceptIt();
				if (token.kind == TokenKind.LBRACKET) {
					acceptIt();
					accept(TokenKind.RBRACKET);
				}
				break;	
			case BOOLEAN:
				acceptIt();
				break;
			default:
				parseError("Invalid Term - expecting TYPE but found " + token.kind);
			}	
			accept(TokenKind.ID);
		}	
	}
	
	// Reference ::= ( id | this ) ( . id )*
	private void parseReference() throws SyntaxError {
		switch(token.kind) {
		case ID: case THIS:
			acceptIt();
			break;
		default: 
			parseError("Invalid Term - expecting ID or THIS but found " + token.kind);
		}
		while (token.kind == TokenKind.DOT) {
			acceptIt();
			accept(TokenKind.ID);
		}
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
	private void parseStatement() throws SyntaxError {
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
	
	
	/*
	 Expression ::= Reference BinaryExpression?  
                        | Reference [ Expression ] BinaryExpression?  
                        | Reference ( ArgumentList? ) BinaryExpression?  
                        | unop Expression BinaryExpression?  
                        | ( Expression ) BinaryExpression?  
                        | num | true | false BinaryExpression?  
                        | new ( id() | int [ Expression ] | id [ Expression ] ) BinaryExpression?  
	 */
	private void parseExpression() throws SyntaxError {
		switch(token.kind) {
		case ID: case THIS:
			parseReference();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				parseExpression();
				accept(TokenKind.RBRACKET);
				parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			} else if (token.kind == TokenKind.LPAREN) {
				acceptIt(); 
				if (token.kind == TokenKind.RPAREN) {
					acceptIt();
				} else {
					parseArgumentList();
					accept(TokenKind.RPAREN);
				}
				parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			} else {
				parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			}
			break;
		case NOT: case MINUS:
			acceptIt();
			parseExpression();
			parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			break;
		case LPAREN:
			acceptIt();
			parseExpression();
			accept(TokenKind.RPAREN);
			parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			break;
		case NUM: case TRUE: case FALSE:
			acceptIt();
			parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			break;
		case NEW:
			acceptIt();
			if (token.kind == TokenKind.ID) {
				acceptIt();
				if (token.kind == TokenKind.LPAREN) {
					acceptIt();
					accept(TokenKind.RPAREN);
				} else if (token.kind == TokenKind.LBRACKET) {
					acceptIt();
					parseExpression();
					accept(TokenKind.RBRACKET);
				} else {
					parseError("Invalid Term - expecting LPAREN or LBRACKET but found " + token.kind);
				}
			} else if (token.kind == TokenKind.INT) {
				acceptIt();
				accept(TokenKind.LBRACKET);
				parseExpression();
				accept(TokenKind.RBRACKET);
			} else {
				parseError("Invalid Term - expecting ID or INT but found " + token.kind);
			}
			parseBinaryExpression(); // BinaryExpression? Parse if exists. Checks if exists within method
			break;
		default:
			parseError("Invalid Term - expecting ID/THIS/NOT/MINUS/LPAREN/NUM/TRUE/FALSE/NEW "
					+ "but found " + token.kind);
		}
	}
	
	// BinaryExpression ::= binop Expression  
	private void parseBinaryExpression() throws SyntaxError {
		if (token.kind == TokenKind.GREATER              // BinaryExpression?
				|| token.kind == TokenKind.LESS
				|| token.kind == TokenKind.EQUAL
				|| token.kind == TokenKind.LESSEQUAL
				|| token.kind == TokenKind.GREATEREQUAL
				|| token.kind == TokenKind.NOTEQUAL
				|| token.kind == TokenKind.MINUS
				|| token.kind == TokenKind.PLUS
				|| token.kind == TokenKind.TIMES
				|| token.kind == TokenKind.DIVIDE
				|| token.kind == TokenKind.AND
				|| token.kind == TokenKind.OR) {
			acceptIt();
			parseExpression();
		} else {       // no binary expression following
			return; 
		}
	}
	
	// ArgumentList ::= Expression ( , Expression )*
	private void parseArgumentList() throws SyntaxError {
		parseExpression();
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			parseExpression();
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
