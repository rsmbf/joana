package execResult;


public class LineVio implements Comparable{
	private MethodLine source;
	private MethodLine target;
	
	public LineVio(MethodLine source, MethodLine target)
	{
		this.source = source;
		this.target = target;
	}

	public MethodLine getSource()
	{
		return source;
	}

	public MethodLine getTarget()
	{
		return target;
	}

	public String toString()
	{
		return source+" -> "+target;
	}

	public String getMessage()
	{
		return "Illegal flow from " + source.toString() + " to " + target.toString();
	}

	public int compareTo(Object o) {
		LineVio vio = (LineVio) o;
		MethodLine source2 = vio.getSource();
		if(source.equals(source2))
		{
			return target.compareTo(vio.getTarget());
		}else{
			return source.compareTo(source2);
		}
	}
	
	public static void main(String[] args) {
		LineVio a = new LineVio(new MethodLine(10, "m()"), new MethodLine(11, "m()"));
		LineVio b = new LineVio(new MethodLine(10, "m()"), new MethodLine(12, "m()"));
		LineVio c = new LineVio(new MethodLine(9, "m()"), new MethodLine(12, "m()"));
		LineVio d = new LineVio(new MethodLine(11, "m()"), new MethodLine(12, "m()"));
		LineVio e = new LineVio(new MethodLine(10, "m()"), new MethodLine(9, "m()"));
		System.out.println(a.compareTo(b));
		//System.out.println(b.compareTo(a));
		System.out.println(a.compareTo(c));
		System.out.println(a.compareTo(d));
		System.out.println(a.compareTo(e));
	}
}
