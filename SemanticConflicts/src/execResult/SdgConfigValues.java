package execResult;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.sdg.SDGInstruction;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;

public class SdgConfigValues {
	private PointsToPrecision precision;
	private boolean ignoreExceptions;
	private int cgNodes;
	private int cgEdges;
//	private boolean sdgCreated;
//	private long sdgNodes;
//	private long sdgEdges;
	private long time;
	private long memory;
	private SDGProgram program;
	private IFCAnalysis ana;
	private Map<SDGProgramPart, Integer> parts_map;
	private List<SDGProgramPart> leftParts, rightParts, otherParts;
	private String reportFilePath; 
	private String sdgReportFilePath; 
	private String sdgInfoFilePath;
	private boolean sdgLoaded;

	public SdgConfigValues(PointsToPrecision prec, boolean exceptions, String reportFolderPath, String sdgsFolderPath)
	{
		this.precision = prec;
		this.ignoreExceptions = exceptions;
		//this.sdgCreated = false;
		parts_map = new HashMap<SDGProgramPart, Integer>();	
		leftParts = new ArrayList<SDGProgramPart>();
		rightParts = new ArrayList<SDGProgramPart>();
		otherParts = new ArrayList<SDGProgramPart>();
		String excep;
		if(ignoreExceptions)
		{
			excep = "noExcep";
		}else{
			excep = "excep";
		}
		reportFilePath = reportFolderPath + File.separator + prec.toString() + "_" +excep +".txt";
		sdgReportFilePath = reportFolderPath + File.separator + prec.toString() + "_" +excep + "_sdgDetails.txt";
		sdgInfoFilePath = sdgsFolderPath + File.separator + prec.toString() + "_" +excep + "_sdgInfo.txt";
	}

	public String getReportFilePath()
	{
		return reportFilePath;
	}

	public String getSdgReportFilePath()
	{
		return sdgReportFilePath;
	}

	public String getSdgInfoFilePath()
	{
		return sdgInfoFilePath;
	}

	public String toString(String sep)
	{
		String spacedSep = sep + " ";
		String res = precision.toString();
		res += spacedSep + Util.booleanToStr(!ignoreExceptions);
		boolean sdgCreated = getSdgCreated();
		res += spacedSep + Util.booleanToStr(sdgCreated); //seems unnecessary for now
		if(sdgCreated)
		{
			res += spacedSep + cgNodes;
			res += spacedSep + cgEdges;
			SDG sdg = program.getSDG();
			res += spacedSep + sdg.vertexSet().size();
			res += spacedSep + sdg.edgeSet().size();
			//res += spacedSep + sdgNodes;
			//res += spacedSep + sdgEdges;
			res += spacedSep + time;
			res += spacedSep + memory;
		}else{
			res += spacedSep + Util.getNSlashes(6, sep);
		}
		return res;
	}

	public static String getHeader(String sep) {
		String spacedSep = sep + " ";
		String res = "Precision";
		res += spacedSep + "Exceptions";
		res += spacedSep + "SdgCreated";
		res += spacedSep + "CgNodes";
		res += spacedSep + "CgEdges";
		res += spacedSep + "SdgNodes";
		res += spacedSep + "SdgEdges";
		res += spacedSep + "Time (ms)";
		res += spacedSep + "Memory (M)";
		return res;
	}
	/*
	public void setSdgCreated(boolean bool)
	{
		this.sdgCreated = bool;
	}

	public boolean getSdgCreated(){
		return sdgCreated;
	}*/
	
	public void setCgInfo(int cgNodes, int cgEdges)
	{
		this.cgNodes = cgNodes;
		this.cgEdges = cgEdges;
	}
	/*
	public void setSdgInfo(int sdgNodes, int sdgEdges)
	{
		this.sdgNodes = sdgNodes;
		this.sdgEdges = sdgEdges;
	}
	*/

	public void setTimeAndMemory(long t, long m) {
		this.time = t;
		this.memory = m;
	}

	public void setProgram(SDGProgram prog) {
		this.program = prog;
	}
	
	public void setIFCAnalysis(IFCAnalysis ifc)
	{
		this.ana = ifc;
	}

	public SDGProgram getProgram() {
		return program;
	}

	public IFCAnalysis getIFCAnalysis() {
		return ana;
	}
	
	public List<SDGProgramPart> getLeftParts()
	{
		return leftParts;
	}
	
	public List<SDGProgramPart> getRightParts()
	{
		return rightParts;
	}
	
	public List<SDGProgramPart> getOtherParts()
	{
		return otherParts;
	}

	public List<Integer> getPartsIndexes(String method, List<SDGProgramPart> parts)
	{
		List<Integer> indexes = new ArrayList<Integer>();
		for(SDGProgramPart part : parts)
		{			
			if(part instanceof SDGInstruction)
			{
				SDGInstruction partInst = (SDGInstruction) part;
				String instMethod = partInst.getOwningMethod().toString();
				if(instMethod.equals(method))
				{
					int index = partInst.getBytecodeIndex();
					indexes.add(index);
				}

			}
		}
		return indexes;
	}

	public void addPartToLeft(SDGProgramPart part)
	{
		leftParts.add(part);
	}
	
	public void addPartToRight(SDGProgramPart part)
	{
		rightParts.add(part);
	}
	
	public void addPartToOther(SDGProgramPart part)
	{
		otherParts.add(part);
	}

	public void addToPartsMap(SDGProgramPart instruction, int line) {
		parts_map.put(instruction, line);
	}
	
	public Map<SDGProgramPart, Integer> getPartsMap()
	{
		return parts_map;
	}

	public boolean getSdgCreated() {
		return program != null && program.getSDG() != null;
	}

	public void setSdgLoaded(boolean loaded)
	{
		this.sdgLoaded = loaded;
	}

	public boolean getSdgLoaded()
	{
		return this.sdgLoaded;
	}

	public int getCGNodes()
	{
		return this.cgNodes;
	}

	public int getCGEdges()
	{
		return this.cgEdges;
	}

	public long getTime()
	{
		return this.time;
	}

	public long getMemory()
	{
		return this.memory;
	}
}
