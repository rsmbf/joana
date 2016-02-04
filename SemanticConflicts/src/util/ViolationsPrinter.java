package util;

import edu.kit.joana.api.sdg.SDGActualParameter;
import edu.kit.joana.api.sdg.SDGCall;
import edu.kit.joana.api.sdg.SDGCallPart;
import edu.kit.joana.api.sdg.SDGCallReturnNode;
import edu.kit.joana.api.sdg.SDGInstruction;
import edu.kit.joana.api.sdg.SDGMethod;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.ClassifiedViolation;
import edu.kit.joana.ifc.sdg.core.violations.ClassifiedViolation.Chop;
import edu.kit.joana.ifc.sdg.core.violations.IConflictLeak;
import edu.kit.joana.ifc.sdg.core.violations.IIllegalFlow;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.core.violations.IllegalFlow;
import edu.kit.joana.ifc.sdg.core.violations.paths.ViolationChop;
import edu.kit.joana.ifc.sdg.core.violations.paths.ViolationPath;
import edu.kit.joana.ifc.sdg.core.violations.paths.ViolationPathes;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import gnu.trove.map.TObjectIntMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import main.ViolationResult;

public class ViolationsPrinter {
	private static void printViolations(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart, String reportFilePath) throws IOException
	{
		for(Object key : resultByProgramPart.keys())
		{
			FileUtils.write(reportFilePath,"Key: "+key);
			FileUtils.writeNewLine(reportFilePath,", Value: "+resultByProgramPart.get(key));
		}
	}

	private static void printViolationsByLine(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException
			{
		Map<String, Integer[]> msgs = new HashMap<String, Integer[]>();
		String base_msg = "Illegal flow from '";
		Integer[] values;
		SDGProgramPart from_part, to_part, from, to;
		for(Object violation : resultByProgramPart.keys())
		{
			int from_line;
			int to_line;
			if(violation instanceof IIllegalFlow)
			{
				IIllegalFlow flow = (IIllegalFlow) violation;
				from_part = (SDGProgramPart) flow.getSource();
				
				if(from_part instanceof SDGCallPart)
				{
					from = ((SDGCallPart) from_part).getOwningCall();
				}else{
					from = from_part;
				}
				to_part = (SDGProgramPart) flow.getSink();
				if(to_part instanceof SDGCallPart)
				{
					to = ((SDGCallPart) to_part).getOwningCall();
				}else{
					to = to_part;
				}
				 
				from_line = from.getOwningMethod().getMethod().getLineNumber(((SDGInstruction) from).getBytecodeIndex());
				to_line = to.getOwningMethod().getMethod().getLineNumber(((SDGInstruction) to).getBytecodeIndex());

			}else{
				String[] msg = violation.toString().split(" to ");
				String str_from = msg[0].toString().split(" from ")[1];
				str_from = str_from.substring(1).split("\\) ")[0]; 
				//System.out.println(str_from);
				int lastColonIndex = str_from.lastIndexOf(':');				
				from = program.getPart((JavaMethodSignature.fromString(str_from.substring(0, lastColonIndex)).toBCString().replace("(L;)", "()") + str_from.substring(lastColonIndex)));	
				//System.out.println(from);


				String str_to =  msg[1].toString().substring(1);
				str_to = str_to.split("\\) ")[0]; 

				lastColonIndex = str_to.lastIndexOf(':');
				to = program.getPart(JavaMethodSignature.fromString(str_to.substring(0, lastColonIndex)).toBCString().replace("(L;)", "()") + str_to.substring(lastColonIndex));

				from_line = parts_map.get(from);
				to_line = parts_map.get(to);
			}

			String error_msg = base_msg + from.getOwningMethod().getSignature() + "' (line " + from_line + ") to '" +to.getOwningMethod().getSignature() +"' (line "+to_line+")";
			int value = resultByProgramPart.get(violation);

			if(msgs.containsKey(error_msg))
			{
				values = msgs.get(error_msg);	
				values[0]++;
				values[1] += value;

			}else{
				values = new Integer[]{1, value};			
			}

			msgs.put(error_msg, values);
		}


		//System.out.println("Lines Summary");
		for(String msg : msgs.keySet())
		{
			FileUtils.write(reportFilePath, "Key: "+msg);		
			FileUtils.write(reportFilePath, ", Violations: "+msgs.get(msg)[0]);
			FileUtils.writeNewLine(reportFilePath, ", Value: "+msgs.get(msg)[1]);
		}
			}

	public static void printAllViolationsByLine(List<ViolationResult> results, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException
			{
		ViolationResult result;
		for(int i = 0; i < results.size(); i++)
		{
			result = results.get(i);
			printViolationsByLine(result.getResultByProgramPart(), program, parts_map, reportFilePath);			
		}
			}

	public static int printAllViolations(List<ViolationResult> list, String reportFilePath)
			throws IOException {
		int violations = 0;
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;

		for(int i = 0; i < list.size(); i++)
		{
			resultsByPart = list.get(i).getResultByProgramPart();	
			if(!resultsByPart.isEmpty()){
				printViolations(resultsByPart, reportFilePath);
				violations += resultsByPart.size();
			}
		}
		return violations;
	}

	public static void printAllMethodsViolations(
			Map<String, List<ViolationResult>> results, String reportFilePath) throws IOException {		

		int violations = 0;
		FileUtils.writeNewLine(reportFilePath, "VIOLATIONS");
		for(String method : results.keySet())
		{
			violations += printAllViolations(results.get(method), reportFilePath);
		}
		FileUtils.writeNewLine(reportFilePath, "TOTAL VIOLATIONS: "+violations);

	}

	public static void printAllMethodsViolationsByLine(Map<String, List<ViolationResult>> results, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "LINE violations");
		for(String method : results.keySet())
		{			
			printAllViolationsByLine(results.get(method), program, parts_map, reportFilePath);
		}
	}
	
	public static void printAllViolationsPaths(List<ViolationResult> results, SDG sdg, String reportFilePath) throws IOException
	{
		int v = 1;
		for(ViolationResult vioResult : results)
		{		
			Collection<? extends IViolation<SecurityNode>> vios = vioResult.getResult();
			Iterator<? extends IViolation<SecurityNode>> it = vios.iterator();
			Collection<ClassifiedViolation> classifVios = new ArrayList<ClassifiedViolation>();// = new 
			while(it.hasNext())
			{
				IViolation<SecurityNode> vio = it.next();
				
				if(vio instanceof ClassifiedViolation)
				{
					classifVios.add((ClassifiedViolation) vio);
				}
			}
			
			for(ClassifiedViolation classifVio : ViolationChop.getInstance().addChop(classifVios, sdg)){
				ViolationPathes pathes = classifVio.getViolationPathes();
				FileUtils.writeNewLine(reportFilePath,"Path Violation "+v + " - Source: "+ classifVio.getSource().getLabel() +" {"+ classifVio.getSource().getOperation()+ "}, Sink: "+classifVio.getSink().getLabel() + " {"+classifVio.getSink().getOperation()+"}");
				v++;
				int p = 1;
				for(ViolationPath vioPath : pathes.getPathesListCopy())
				{
					LinkedList<SecurityNode> pathList = vioPath.getPathList();
					SecurityNode node;
					if(vioPath.getPathList().size() > 0)
					{
						FileUtils.write(reportFilePath,"      Path "+ p + " - ");
						p++;
					}
					for(int i = 0; i < pathList.size() - 1;i++)
					{
						node = pathList.get(i);
						FileUtils.write(reportFilePath,"("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " +node.getLabel() + " {" + node.getOperation() +"} -> ");
					}
					if(vioPath.getPathList().size() > 0)
					{
						node = pathList.get(pathList.size() - 1);
						FileUtils.writeNewLine(reportFilePath, "("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " +node.getLabel() + " {" + node.getOperation() +"}");
					}
				}				
			}
		}
	}
	
	public static void printAllMethodsViolationsPaths(
			Map<String, List<ViolationResult>> results, SDG sdg, String reportFilePath) throws IOException {
		FileUtils.writeNewLine(reportFilePath,"VIOLATIONS PATHES");
		for(String method : results.keySet())
		{
			printAllViolationsPaths(results.get(method), sdg, reportFilePath);
		}
		FileUtils.writeNewLine(reportFilePath, "");
	}
}
