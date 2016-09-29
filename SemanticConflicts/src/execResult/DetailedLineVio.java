package execResult;

public class DetailedLineVio extends LineVio {
	private int insts;
	private int total;
	
	public DetailedLineVio(MethodLine source, MethodLine target, int insts, int total)
	{
		super(source, target);
		this.insts = insts;
		this.total = total;
	}
	
	public int getInstVios() {
		return insts;
	}

	public int getTotalVios() {
		return total;
	}
	
	public String getMessage()
	{
		return super.getMessage() + ", Instructions: "+insts + ", Violations: "+total;
	}

	public void incInstVio() {
		insts++;
	}

	public void addVios(int value) {
		total += value;
	}
}
