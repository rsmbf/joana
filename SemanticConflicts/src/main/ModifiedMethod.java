package main;
import java.util.List;

import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;


public class ModifiedMethod {
	private JavaMethodSignature signature;
	private List<Integer> leftContribs;
	private List<Integer> rightContribs;
	private List<String> defaultConstructorArgs;

	public ModifiedMethod(String signature, List<String> defaultConstructorArgs, List<Integer> leftContribs, List<Integer> rightContribs)
	{
		this.signature = JavaMethodSignature.fromString("void "+signature);
		this.leftContribs = leftContribs;
		this.rightContribs = rightContribs;
		this.defaultConstructorArgs = defaultConstructorArgs;
	}

	public JavaMethodSignature getMethodSignature() {
		return signature;
	}

	public List<Integer> getLeftContribs() {
		return leftContribs;
	}
	
	public List<Integer> getRightContribs()
	{
		return rightContribs;
	}

	public List<String> getDefaultConstructorArgs()
	{
		return defaultConstructorArgs;
	}




}
