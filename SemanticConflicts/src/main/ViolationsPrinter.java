package main;

import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import gnu.trove.map.TObjectIntMap;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import util.FileUtils;

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
		Map<String, Integer> msgs = new HashMap<String, Integer>();
		String base_msg = "Illegal flow from '";
		for(Object violation : resultByProgramPart.keys())
		{
			String[] msg = violation.toString().split(" to ");
			String str_from = msg[0].toString().split(" from ")[1];
			str_from = str_from.substring(1).split("\\) ")[0]; 
			//System.out.println(str_from);
			int lastColonIndex = str_from.lastIndexOf(':');				
			SDGProgramPart from = program.getPart((JavaMethodSignature.fromString(str_from.substring(0, lastColonIndex)).toBCString().replace("(L;)", "()") + str_from.substring(lastColonIndex)));	
			//System.out.println(from);
			int from_line = parts_map.get(from);

			String str_to =  msg[1].toString().substring(1);
			str_to = str_to.split("\\) ")[0]; 

			lastColonIndex = str_to.lastIndexOf(':');
			SDGProgramPart to = program.getPart(JavaMethodSignature.fromString(str_to.substring(0, lastColonIndex)).toBCString().replace("(L;)", "()") + str_to.substring(lastColonIndex));
			int to_line = parts_map.get(to);
			String error_msg = base_msg + from.getOwningMethod().getSignature() + "' (line " + from_line + ") to '" +to.getOwningMethod().getSignature() +"' (line "+to_line+")";
			int value = resultByProgramPart.get(violation);
			if(msgs.containsKey(error_msg))
			{
				value += msgs.get(error_msg);
			}
			msgs.put(error_msg, value);
		}
		//System.out.println("Lines Summary");
		for(String msg : msgs.keySet())
		{
			FileUtils.write(reportFilePath, "Key: "+msg);			
			FileUtils.writeNewLine(reportFilePath, ", Value: "+msgs.get(msg));
		}

		
		
	}
	
	public static void printAllViolationsByLine(List<TObjectIntMap<IViolation<SDGProgramPart>>> results, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException
	{
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		for(int i = 0; i < results.size(); i++)
		{
			resultsByPart = results.get(i);
			if(!resultsByPart.isEmpty()){
				printViolationsByLine(resultsByPart, program, parts_map, reportFilePath);
			}				
		}
	}
		
	public static int printAllViolations(List<TObjectIntMap<IViolation<SDGProgramPart>>> results, String reportFilePath)
			throws IOException {
		int violations = 0;
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		for(int i = 0; i < results.size(); i++)
		{
			resultsByPart = results.get(i);	
			if(!resultsByPart.isEmpty()){
				printViolations(resultsByPart, reportFilePath);
				violations += resultsByPart.size();
			}
		}
		return violations;
	}

	public static void printAllMethodsViolations(
			Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results, String reportFilePath) throws IOException {		

		int violations = 0;
		FileUtils.writeNewLine(reportFilePath, "VIOLATIONS");
		for(String method : results.keySet())
		{
			violations += printAllViolations(results.get(method), reportFilePath);
		}
		FileUtils.writeNewLine(reportFilePath, "TOTAL VIOLATIONS: "+violations);

	}
	
	public static void printAllMethodsViolationsByLine(Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "LINE violations");
		for(String method : results.keySet())
		{
			printAllViolationsByLine(results.get(method), program, parts_map, reportFilePath);
		}
	}
}
