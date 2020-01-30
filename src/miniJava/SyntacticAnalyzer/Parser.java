package miniJava.SyntacticAnalyzer;

import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.SyntacticAnalyzer.Scanner;
import miniArith.SyntacticAnalyzer.Parser.SyntaxError;
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
	private void parseClassDeclaration() {
		accept(TokenKind.CLASS);
		accept(TokenKind.ID);
		accept(TokenKind.LBRACE);
		if (token.kind == TokenKind.RBRACE) {
			acceptIt();
		} else {
			while (token.kind != TokenKind.RBRACE) {
				parseFieldOrMethDec();
			}
			accept(TokenKind.RBRACE);
		}
	}
	
	// FieldOrMethDec ::= ( public | private )? Access
	private void parseFieldOrMethDec() {
		switch (token.kind) {
		case PUBLIC: case PRIVATE:
			acceptIt();
			parseAccess();
		default:
			parseAccess();
		}
	}
	
	// Access ::= static? ( ( int ( [] )? | id ( [] )? | Boolean ) TypeReturn | void VoidReturn)
	private void parseAccess() {
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
		case BOOLEAN:
			acceptIt();
			parseTypeReturn();
		case VOID:
			acceptIt();
			parseVoidReturn();
		default:
			parseError("Invalid Term - expecting TYPE or VOID but found " + token.kind);
		}
	}
	
	// TypeReturn ::= id ( ; | ( ParameterList? ) {Statement*} )
	private void parseTypeReturn() {
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
			/* TODO 
			 *  parse statement* and following (confirm RBRACE can follow statement)
			 */
		default:
			parseError("Invalid Term - expecting SEMICOL or LPAREN but found " + token.kind);
		}
	}

	// VoidReturn ::= id ( ParameterList? ) {Statement*}
	private void parseVoidReturn() {
		accept(TokenKind.ID); 
		accept(TokenKind.LPAREN);
		if (token.kind != TokenKind.RPAREN) {
			parseParameterList();
			accept(TokenKind.RPAREN);
		} else {
			accept(TokenKind.RPAREN);
		}
		accept(TokenKind.LBRACE);
		/* TODO 
		 *  parse statement* and following (confirm RBRACE can follow statement)
		 */
	}
	
	// ParameterList ::= ( int ( [] )? | id ( [] )? | Boolean ) id ( , ( int ( [] )? | id ( [] )? | Boolean ) id)*
	private void parseParameterList() {
		switch (token.kind) {
		case INT: case ID:
			acceptIt();
			if (token.kind == TokenKind.LBRACKET) {
				acceptIt();
				accept(TokenKind.RBRACKET);
			}
		case BOOLEAN:
			acceptIt();
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
				}
				if (token.kind == TokenKind.RBRACKET) {
					acceptIt();
				}		
			case BOOLEAN:
				acceptIt();
			default:
				parseError("Invalid Term - expecting TYPE but found " + token.kind);
			}	
		}	
	}
	
	// Reference ::= ( id | this ) ( . id )*
	private void parseReference() {
		switch(token.kind) {
		case ID: case THIS:
			acceptIt();
		default: 
			parseError("Invalid Term - expecting ID or THIS but found " + token.kind);
		}
		while (token.kind == TokenKind.DOT) {
			acceptIt();
			accept(TokenKind.ID);
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
