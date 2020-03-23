package miniJava.SyntacticAnalyzer;

public class SourcePosition {
	
	private int lineNum;
	private int colNum;
	
	public SourcePosition() {
		this.lineNum = 1;
		this.colNum = 1;
	}
	public SourcePosition(int lineNum, int colNum) {
		this.lineNum = lineNum;
		this.colNum = colNum;
	}

	public String toString() {
		return " | L" + lineNum;
	}
	
	public int getLine() {
		return lineNum;
	}
	
	public int getCol() {
		return colNum;
	}
}
