package execResult;

public class Util {
	protected static String booleanToStr(boolean bool)
	{
		String str;
		if(bool)
		{
			str = "Yes";
		}else{
			str = "No";
		}
		return str;
	}
	
	
	protected static String getNSlashes(int n, String sep)
	{
		String spacedSep = sep + " ";
		String str = "";
		if(n > 0)
		{
			str = "-";
		}
		for(int i = 1; i < n; i++)
		{
			str += spacedSep + "-";
		}
		return str;
	}
}
