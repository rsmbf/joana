package execResult;

import java.util.List;

public class MethodExecutionResult extends ExecutionResult {
	private String methodSignature;
	private List<Integer> left;
	private List<Integer> right;
	
	public MethodExecutionResult(SdgConfigValues config, String methodSignature, List<Integer> left, List<Integer> right)
	{
		super(config);
		this.methodSignature = methodSignature;
		this.left = left;
		this.right = right;
	}
		
	@Override
	public String toString(String sep)
	{
		String spacedSep = sep + " ";
		String str = methodSignature;
		str += spacedSep + super.toString(sep);
		str += spacedSep + left;
		str += spacedSep + right;
		return str;
	}
	
	public static String getHeader(String sep)
	{
		String spacedSep = sep + " ";
		String str = "Method";
		str += spacedSep + ExecutionResult.getHeader(sep);
		str += spacedSep + "Left";
		str += spacedSep + "Right";
		return str;
	}
	
}
