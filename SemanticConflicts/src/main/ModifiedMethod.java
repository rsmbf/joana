import java.util.List;

import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;


public class ModifiedMethod {
	private JavaMethodSignature signature;
	private List<List<Integer>> contribLines;
	private List<String> defaultConstructorArgs;

	public ModifiedMethod(String signature, List<String> defaultConstructorArgs, List<List<Integer>> contribLines)
	{
		this.signature = JavaMethodSignature.fromString("void "+signature);
		this.contribLines = contribLines;
		this.defaultConstructorArgs = defaultConstructorArgs;
	}

	public JavaMethodSignature getMethodSignature() {
		return signature;
	}

	public List<List<Integer>> getContribLines() {
		return contribLines;
	}

	public List<String> getDefaultConstructorArgs()
	{
		return defaultConstructorArgs;
	}




}
