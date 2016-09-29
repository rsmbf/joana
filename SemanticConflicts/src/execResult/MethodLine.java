package execResult;

public class MethodLine {
	private int line;
	private String method;
	
	public MethodLine(int line, String method)
	{
		this.line = line;
		this.method = method;
	}
	
	public String toString()
	{
		return method + " (line " + line + ")";
	}
	
	public int getLine()
	{
		return line;
	}
	
	public String getMethod()
	{
		return method;
	}
		
	public boolean equals(MethodLine meth)
	{
		return method.equals(meth.getMethod()) && line == meth.getLine();
	}

	public int compareTo(MethodLine obj) {
		int res = method.compareTo(obj.getMethod());
		if(res == 0)
		{
			res = line - obj.getLine();
		}
		return res;
	}
}
