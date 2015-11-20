package main;
import java.util.List;

import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;


public class ModifiedMethod {
	private JavaMethodSignature signature;
	private List<Integer> leftContribs;
	private List<Integer> rightContribs;
	private List<String> defaultConstructorArgs;
	private List<String> importsList;

	public ModifiedMethod(String signature, List<String> defaultConstructorArgs, List<Integer> leftContribs, List<Integer> rightContribs, List<String> classImportsList)
	{
		this.signature = JavaMethodSignature.fromString("void "+signature);
		this.leftContribs = leftContribs;
		this.rightContribs = rightContribs;
		this.defaultConstructorArgs = defaultConstructorArgs;
		this.importsList = classImportsList;
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

	public List<String> getImportsList()
	{
		return importsList;
	}


}
