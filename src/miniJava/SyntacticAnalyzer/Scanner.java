package miniJava.SyntacticAnalyzer;

import java.io.*;

import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.ErrorReporter;

public class Scanner {

	private InputStream inputStream;
	private ErrorReporter reporter;

	private char currentChar;
	private StringBuilder currentSpelling;
	
	// true when end of line is found
	private boolean eot = false; 

	public Scanner(InputStream inputStream, ErrorReporter reporter) {
		this.inputStream = inputStream;
		this.reporter = reporter;

		// initialize scanner state
		readChar();
	}
	
	/**
	 * skip whitespace and scan next token
	 */
	public Token scan() {
		
		
		// skip whitespace
		while (!eot && isWhiteSpace(currentChar)) {
			skipIt();
		}
		
		// start of a token: collect spelling and identify token kind
		currentSpelling = new StringBuilder();
		TokenKind kind = scanToken();
		String spelling = currentSpelling.toString();
		
		while (kind == TokenKind.DIVIDE && currentChar == '*' || currentChar == '/') {  // found a comment, can find multiple in a row
			if (currentChar == '*') {  // multi-line comment
				skipIt();
				while (!eot) { // source file continues
					if (currentChar == '*') {  // found potential end
						skipIt();
						if (currentChar == '/') {  // found end
							skipIt();
							break;
						}
					} else { // skip comment content
						skipIt();
					}
				}
				if (eot) {
					scanError("Comment ran off source file");
					return new Token(TokenKind.ERROR, "Comment ran off source file");
				}
			} else { // single line comment
				skipIt();
				while (!eot && currentChar != eolUnix && currentChar != eolWindows) {
					skipIt();
				}
			}
			currentSpelling = new StringBuilder();  // original token scanned was a comment, scan next token and check for comment again
			kind = scanToken();                     // loop continues until valid token found
			spelling = currentSpelling.toString();
		}
		
		// return new token
		return new Token(kind, spelling);
	}
	
	public TokenKind scanToken() {
		if (eot) {
			return(TokenKind.EOT);
		} else if (isAlpha(currentChar)) { // keyword or ID
			takeIt();
			while(isAlpha(currentChar) || currentChar == '_' || isDigit(currentChar)) {
				takeIt();
			}
			switch(currentSpelling.toString()) {
			case "class":
				return TokenKind.CLASS;
			case "void":
				return TokenKind.VOID;
			case "public":
				return TokenKind.PUBLIC;
			case "private":
				return TokenKind.PRIVATE;
			case "static":
				return TokenKind.STATIC;
			case "int":
				return TokenKind.INT;
			case "boolean":
				return TokenKind.BOOLEAN;
			case "this":
				return TokenKind.THIS;
			case "return":
				return TokenKind.RETURN;
			case "if":
				return TokenKind.IF;
			case "else":
				return TokenKind.ELSE;
			case "while":
				return TokenKind.WHILE;
			case "true":
				return TokenKind.TRUE;
			case "false":
				return TokenKind.FALSE;
			case "new":
				return TokenKind.NEW;
			default:
				return TokenKind.ID;
			}
		} else if (isDigit(currentChar)) { // num
			takeIt();
			while(isDigit(currentChar)) {
				takeIt();
			}
			return TokenKind.NUM;
		} else {                         // rest of tokens
			switch(currentChar) {
			case '>':
				takeIt();
				if (currentChar == '=') {
					takeIt();
					return TokenKind.GREATEREQUAL;
				} 
				return TokenKind.GREATER;
			case '<':
				takeIt();
				if (currentChar == '=') {
					takeIt();
					return TokenKind.LESSEQUAL;
				}
				return TokenKind.LESS;
			case '=':
				takeIt();
				if (currentChar == '=') {
					takeIt();
					return TokenKind.EQUAL;
				}
				return TokenKind.ASSIGN;
			case '!':
				takeIt();
				if (currentChar == '=') {
					takeIt();
					return TokenKind.NOTEQUAL;
				}
				return TokenKind.NOT;
			case '&':
				takeIt();
				if (currentChar == '&') {
					takeIt();
					return TokenKind.AND;
				}
				scanError("Scanned '" + currentChar + "' in input when looking for second '&'");
				return TokenKind.ERROR;
			case '|':
				takeIt();
				if (currentChar == '|') {
					takeIt();
					return TokenKind.OR;
				}
				scanError("Scanned '" + currentChar + "' in input when looking for second '|'");
				return TokenKind.ERROR;
			case '+':
				takeIt();
				return TokenKind.PLUS;
			case '-': 
				takeIt();
				return TokenKind.MINUS;
			case '*':
				takeIt();
				return TokenKind.TIMES;
			case '/':
				takeIt();
				return TokenKind.DIVIDE;
			case '(':
				takeIt();
				return TokenKind.LPAREN;
			case ')':
				takeIt();
				return TokenKind.RPAREN;
			case '{':
				takeIt();
				return TokenKind.LBRACE;
			case '}':
				takeIt();
				return TokenKind.RBRACE;
			case ',':
				takeIt();
				return TokenKind.COMMA;
			case ';':
				takeIt();
				return TokenKind.SEMICOL;
			case '.':
				takeIt();
				return TokenKind.DOT;
			case '[':
				takeIt();
				return TokenKind.LBRACKET;
			case ']':
				takeIt();
				return TokenKind.RBRACKET;
			default:
				scanError("Unrecognized character '" + currentChar + "' in input");
				return(TokenKind.ERROR);
			}
		}
		
	}
	
	private void takeIt() {
		currentSpelling.append(currentChar);
		nextChar();
	}

	private void skipIt() {
		nextChar();
	}

	private boolean isDigit(char c) {
		return (c >= '0') && (c <= '9');
	}
	
	private boolean isAlpha(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}
	
	private boolean isWhiteSpace(char c) {
		return c == eolUnix || c == eolWindows || c == tab || c == space;
	}

	private void scanError(String m) {
		reporter.reportError("Scan Error:  " + m);
	}
	
	private final static char eolUnix = '\n';
	private final static char eolWindows = '\r';
	private final static char tab = '\t';
	private final static char space = ' ';

	/**
	 * advance to next char in inputstream
	 * detect end of file or end of line as end of input
	 */
	private void nextChar() {
		if (!eot)
			readChar();
	}

	private void readChar() {
		try {
			int c = inputStream.read();
			currentChar = (char) c;
			if (c == -1) {
				eot = true;
			}
		} catch (IOException e) {
			scanError("I/O Exception!");
			eot = true;
		}
	}
}
