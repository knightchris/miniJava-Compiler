package miniJava.CodeGenerator;

import miniJava.AbstractSyntaxTrees.MethodDecl;

public class MethodPatchInfo {
	
	public MethodDecl method;
	public int addr;
	
	public MethodPatchInfo(MethodDecl method, int addr) {
		this.method = method;
		this.addr = addr;
	}
}
