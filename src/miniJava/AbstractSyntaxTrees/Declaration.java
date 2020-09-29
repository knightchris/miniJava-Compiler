/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.CodeGenerator.RuntimeEntityDescription;
import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class Declaration extends AST {
	
	public Declaration(String name, TypeDenoter type, SourcePosition posn) {
		super(posn);
		this.name = name;
		this.type = type;
	}
	
	public Declaration(String name, TypeDenoter type, RuntimeEntityDescription red, SourcePosition posn) {
		super(posn);
		this.name = name;
		this.type = type;
		this.red = red;
	}
	
	public String name;
	public TypeDenoter type;
	public RuntimeEntityDescription red;
}
