package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;
import miniJava.SyntacticAnalyzer.Token;

public class NullLiteral extends Terminal {

  public NullLiteral(Token t, SourcePosition posn) {
    super (t);
    this.posn = posn;
  }
 
  public <A,R> R visit(Visitor<A,R> v, A o) {
      return v.visitNullLiteral(this, o);
  }
}