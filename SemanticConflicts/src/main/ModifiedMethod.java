package main;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;


public class ModifiedMethod {
	private JavaMethodSignature signature;
	private List<Integer> leftContribs;
	private List<Integer> rightContribs;
	private List<String> defaultConstructorArgs;
	private List<String> importsList;
	private Map<String, ModifiedMethod> anomModMethods;
	
	public ModifiedMethod(String signature, List<String> defaultConstructorArgs, List<Integer> leftContribs, List<Integer> rightContribs, List<String> classImportsList)
	{
		this(signature, defaultConstructorArgs, leftContribs, rightContribs, classImportsList, null);
	}

	public ModifiedMethod(String signature, List<String> defaultConstructorArgs, List<Integer> leftContribs, 
			List<Integer> rightContribs, List<String> classImportsList, Map<String, ModifiedMethod> anomMethods)
	{
		this.signature = JavaMethodSignature.fromString(signature);
		this.leftContribs = leftContribs;
		this.rightContribs = rightContribs;
		this.defaultConstructorArgs = defaultConstructorArgs;
		this.importsList = classImportsList;
		this.anomModMethods = anomMethods;
	}
	
	public ModifiedMethod(String signature, List<Integer> leftContribs, List<Integer> rightContribs)
	{
		this(signature, leftContribs, rightContribs, null);
	}
	
	public ModifiedMethod(String signature, List<Integer> leftContribs, List<Integer> rightContribs, Map<String, ModifiedMethod> anomMethods){
		this(signature, new ArrayList<String>(), leftContribs, rightContribs, new ArrayList<String>(), anomMethods);
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

	public Map<String, ModifiedMethod> getAnomModMethods()
	{
		return anomModMethods;
	}
	
}
