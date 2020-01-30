package miniJava.SyntacticAnalyzer;

import miniJava.SyntacticAnalyzer.TokenKind;

/**
 *  A token has a kind and a spelling
 *  TODO: add source position
 */
public class Token {
	public TokenKind kind;
	public String spelling;

	public Token(TokenKind kind, String spelling) {
		this.kind = kind;
		this.spelling = spelling;
	}
	
}
