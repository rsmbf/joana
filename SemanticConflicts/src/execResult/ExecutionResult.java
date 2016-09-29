package execResult;

import java.util.Set;


public abstract class ExecutionResult {
	private SdgConfigValues config;
	private boolean hasSourceAndSink;
	private boolean hasLeftToRightVio;
	private boolean hasRightToLeftVio;
	private int totalVios;
	private int instVios;
	private Set<LineVio> lineVios;

	public ExecutionResult(SdgConfigValues config, boolean hasSourceAndSink)
	{
		this.config = config;
		this.hasSourceAndSink = hasSourceAndSink;
		this.hasLeftToRightVio = false;
		this.hasRightToLeftVio = false;
	}

	public ExecutionResult(SdgConfigValues config)
	{
		this(config, false);
	}
	/*
	public void addLineVio(LineVio vio) {		
		if(lineVios.size() == 0)
		{
			lineVios.add(vio);
		}else{
			int index = 0;

			while(index < lineVios.size() && vio.getSource().toString().compareTo(lineVios.get(index).getSource().toString()) > 0)
			{
				index++;
			}
			while(index < lineVios.size() && vio.getSource().toString().equals(lineVios.get(index).getSource().toString()) 
					&& vio.getTarget().toString().compareTo(lineVios.get(index).getTarget().toString()) > 0){
				index++;
			}
			if(index < lineVios.size())
			{
				lineVios.add(index, vio);
			}else{
				lineVios.add(vio);
			}
		}
	}
	 */
	public void setHasSourceAndSink(boolean bool)
	{
		this.hasSourceAndSink = bool;
	}

	public String toString(String sep) {
		String spacedSep = sep + " ";
		String str = config.toString(sep);

		if(config.getSdgCreated())
		{
			str += spacedSep + Util.booleanToStr(hasSourceAndSink);
			if(hasSourceAndSink)
			{
				str += spacedSep + Util.booleanToStr(hasLeftToRightVio);
				str += spacedSep + Util.booleanToStr(hasRightToLeftVio);
				str += spacedSep + totalVios;
				str += spacedSep + instVios;
				if(lineVios != null)
				{
					str += spacedSep + lineVios.size();
					str += spacedSep + lineVios;
				}else{
					str += spacedSep + 0;
					str += spacedSep + "[]";
				}

			}else{
				str += spacedSep + Util.getNSlashes(6, sep);
			}
		}else{
			str += spacedSep + Util.getNSlashes(7, sep);
		}

		return str;
	}

	public static String getHeader(String sep)
	{
		String spacedSep = sep + " ";
		String str = SdgConfigValues.getHeader(sep);
		str += spacedSep + "HasSourcedAndSink";
		str += spacedSep + "HasLeftToRightVio";
		str += spacedSep + "HasRightToLeftVio";
		str += spacedSep + "TotalVios";
		str += spacedSep + "InstVios";
		str += spacedSep + "LineVios";
		str += spacedSep + "DetailedLineVios";
		return str;
	}

	public SdgConfigValues getSdgConfigValues()
	{
		return config;
	}

	public boolean getHasLeftToRightVio() {
		return hasLeftToRightVio;
	}

	public boolean getHasRightToLeftVio() {
		return hasRightToLeftVio;
	}

	public void setHasLeftToRightVio(boolean hasLeftToRightVio) {
		this.hasLeftToRightVio = hasLeftToRightVio;
	}

	public void setHasRightToLeftVio(boolean hasRightToLeftVio) {
		this.hasRightToLeftVio = hasRightToLeftVio;
	}

	public static void main(String[] args) {
		ExecutionResult test = null;// = new ExecutionResult();
		/*
		test.addLineVio(new MethodLine(1,"m"), new MethodLine(2,"m"));
		test.addLineVio(new MethodLine(1,"m"), new MethodLine(3,"m"));
		test.addLineVio(new MethodLine(3,"m"), new MethodLine(1,"m"));
		test.addLineVio(new MethodLine(2,"m"), new MethodLine(3,"m"));
		test.addLineVio(new MethodLine(5,"m"), new MethodLine(1,"m"));
		test.addLineVio(new MethodLine(2,"m"),new MethodLine(1,"m"));
		test.addLineVio(new MethodLine(2,"m"),new MethodLine(4,"m"));
		 */
		System.out.println(test.toString(";"));
	}

	public void setLineVios(Set<LineVio> keySet) {
		lineVios = keySet;
	}

	public void setInstVios(int vios) {
		this.instVios = vios;
	}

	public void setTotalVios(int vios) {
		this.totalVios = vios;
	}
}