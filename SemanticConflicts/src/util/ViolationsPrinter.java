package util;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.sdg.SDGCallPart;
import edu.kit.joana.api.sdg.SDGInstruction;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.ClassifiedViolation;
import edu.kit.joana.ifc.sdg.core.violations.IIllegalFlow;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.core.violations.paths.ViolationChop;
import edu.kit.joana.ifc.sdg.core.violations.paths.ViolationPath;
import edu.kit.joana.ifc.sdg.core.violations.paths.ViolationPathes;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGEdge;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import execResult.DetailedLineVio;
import execResult.ExecutionResult;
import execResult.LineVio;
import execResult.MethodExecutionResult;
import execResult.MethodLine;
import execResult.SdgConfigValues;
import gnu.trove.map.TObjectIntMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import main.LineInterferencesPoints;
import main.ViolationPathCollector;
import main.ViolationResult;

public class ViolationsPrinter {
	private static int printViolationsByPart(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart, String reportFilePath) throws IOException
	{
		int total = 0;
		for(Object key : resultByProgramPart.keys())
		{			
			FileUtils.write(reportFilePath,"Key: "+key);
			int current = resultByProgramPart.get(key);
			total += current;
			FileUtils.writeNewLine(reportFilePath,", Value: "+current);
		}
		return total;
	}

	private static LineVio createLineVio(Object violation, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map)
	{
		SDGProgramPart from_part, to_part, from, to;
		from = null;
		to = null;
		int from_line = 0;
		int to_line = 0;
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

			//from_line = from.getOwningMethod().getMethod().getLineNumber(((SDGInstruction) from).getBytecodeIndex());
			//to_line = to.getOwningMethod().getMethod().getLineNumber(((SDGInstruction) to).getBytecodeIndex());

		}else if(program != null && parts_map != null){
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

			//from_line = parts_map.get(from);
			//to_line = parts_map.get(to);
		}
		from_line = parts_map.get(from);
		to_line = parts_map.get(to);
		//String error_msg = base_msg + from.getOwningMethod().getSignature() + "' (line " + from_line + ") to '" +to.getOwningMethod().getSignature() +"' (line "+to_line+")";
		if(from != null && to != null)
		{
			MethodLine source = new MethodLine(from_line, from.getOwningMethod().getSignature().toHRString());
			MethodLine target = new MethodLine(to_line, to.getOwningMethod().getSignature().toHRString());
			return new LineVio(source, target);
		}else{
			return null;
		}
		
	}

	private static void printViolationsByLine(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath, Map<LineVio, DetailedLineVio> lineVios) throws IOException
			{
		//String base_msg = "Illegal flow from '";
		//Integer[] values;
		DetailedLineVio detailedVio;
		for(Object violation : resultByProgramPart.keys())
		{
			LineVio vio = createLineVio(violation, program, parts_map);
			int value = resultByProgramPart.get(violation);
			//System.out.println("Line vios size: "+ lineVios.size()+" contains: "+lineVios.containsKey(vio) + " Vio: "+vio);
			if(lineVios.containsKey(vio))
			{				
				//values = lineVios.get(vio);
				//values[0]++;
				//values[1] += value;
				detailedVio = lineVios.get(vio);
				//System.out.println("   "+vio.getSource());
				//System.out.println("   "+detailedVio.getSource());
				//System.out.println("   "+(vio.getSource().equals(detailedVio.getSource()) && vio.getTarget().equals(detailedVio.getTarget())));
				//System.out.println("   "+vio.getTarget());
				//System.out.println("   "+detailedVio.getTarget());
				//System.out.println("   "+vio.getTarget().equals(detailedVio.getTarget()));

				detailedVio.incInstVio();
				detailedVio.addVios(value);
			}else{
				//values = new Integer[]{1, value};		
				lineVios.put(vio, new DetailedLineVio(vio.getSource(), vio.getTarget(), 1, value));
			}

			//lineVios.put(error_msg, values);
		}


		//System.out.println("Lines Summary");

			}

	public static Map<LineVio, DetailedLineVio> printAllViolationsByLine(List<ViolationResult> results, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException
			{
		ViolationResult result;
		Map<LineVio, DetailedLineVio> msgs = new TreeMap<LineVio, DetailedLineVio>();
		for(int i = 0; i < results.size(); i++)
		{
			result = results.get(i);
			printViolationsByLine(result.getResultByProgramPart(), program, parts_map, reportFilePath, msgs);			
		}
		return msgs;
			}

	public static void printAllViolations(Map<String, List<ViolationResult>> map, String reportFilePath, IFCAnalysis ana) throws IOException
	{
		for(String method : map.keySet())
		{
			FileUtils.writeNewLine(reportFilePath, method);
			List<ViolationResult> list = map.get(method);
			int i = 1;
			for(ViolationResult vioRes : list)
			{
				for(IViolation<SecurityNode> vio : vioRes.getResult()){
					FileUtils.writeNewLine(reportFilePath,"      "+i+": "+vio);
					if(vio instanceof IIllegalFlow)
					{
						IIllegalFlow<SecurityNode> flow = (IIllegalFlow<SecurityNode>) vio;
						FileUtils.writeNewLine(reportFilePath, "           "+flow.getSource().getSr() + "->"+flow.getSink().getSr());
						FileUtils.writeNewLine(reportFilePath, "           Source: "+flow.getSource().getId() + " - "+flow.getSource().getLabel() + " Kind: " + flow.getSource().kind + " Operator: "+flow.getSource().getOperation() + " Source: "+ flow.getSource().getSource() +" Sr: "+flow.getSource().getSr() + " Er: "+flow.getSource().getEr() + " Sc: "+flow.getSource().getSc()+ " Ec: "+flow.getSource().getEc());						
						FileUtils.writeNewLine(reportFilePath, "           Sink: "+flow.getSink().getId() + " - "+flow.getSink().getLabel() + " Kind: " + flow.getSink().kind + " Operator: "+flow.getSink().getOperation() + " Source: "+ flow.getSink().getSource() + " Sr: "+flow.getSink().getSr() + " Er: "+flow.getSink().getEr() + " Sc: "+flow.getSink().getSc()+ " Ec: "+flow.getSink().getEc());
						if(ana != null)
						{
							List<IViolation<SecurityNode>> singleVioList = new ArrayList<IViolation<SecurityNode>>();
							singleVioList.add(vio);
							TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(singleVioList);
							for(Object key : resultByProgramPart.keys())
							{			
								LineVio lineVio = createLineVio(key,null, null);
								FileUtils.writeNewLine(reportFilePath, "           "+lineVio.getSource().getLine() + "->"+lineVio.getTarget().getLine());
								FileUtils.write(reportFilePath,"           Key: "+key);
								FileUtils.writeNewLine(reportFilePath,", Value: "+resultByProgramPart.get(key));
							}
						}
					}
					i++;
				}
			}
		}

	}

	public static int[] printAllViolationsByPart(List<ViolationResult> list, String reportFilePath)
			throws IOException {
		int violations = 0;
		int total = 0;
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;

		for(int i = 0; i < list.size(); i++)
		{
			Collection<? extends IViolation<SecurityNode>> result = list.get(i).getResult();
			if(!result.isEmpty())
			{
				total += result.size();
			}
			resultsByPart = list.get(i).getResultByProgramPart();
			if(!resultsByPart.isEmpty()){
				printViolationsByPart(resultsByPart, reportFilePath);
				violations += resultsByPart.size();
			}
		}
		return new int[]{violations, total};
	}

	public static Map<String, int[]> printAllMethodsViolations(
			Map<String, List<ViolationResult>> results, String reportFilePath) throws IOException {		

		int violations = 0;
		int total = 0;
		FileUtils.writeNewLine(reportFilePath, "VIOLATIONS BETWEEN LEFT AND RIGHT");
		Map<String, int[]> methodsVios = new HashMap<String, int[]>();
		for(String method : results.keySet())
		{
			int[] vios = printAllViolationsByPart(results.get(method), reportFilePath);
			methodsVios.put(method, vios);
			violations += vios[0];
			total += vios[1];
		}
		FileUtils.writeNewLine(reportFilePath, "INSTRUCTION VIOLATIONS: "+violations);
		FileUtils.writeNewLine(reportFilePath, "TOTAL VIOLATIONS: "+total);
		return methodsVios;

	}

	public static Map<String, Map<LineVio, DetailedLineVio>> printAllMethodsViolationsByLine(Map<String, List<ViolationResult>> results, 
			SDGProgram program, Map<SDGProgramPart, Integer> parts_map,
			String reportFilePath) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "LINE violations between left and right");
		Map<LineVio, DetailedLineVio> msgs;
		Map<String, Map<LineVio, DetailedLineVio>> msgsPerMethod = new HashMap<String, Map<LineVio, DetailedLineVio>>();
		int total = 0;
		for(String method : results.keySet())
		{			
			msgs = printAllViolationsByLine(results.get(method), program, parts_map, reportFilePath);
			msgsPerMethod.put(method, msgs);
			FileUtils.writeNewLine(reportFilePath, "Method: "+method);
			int instVios = 0;
			int totalVios = 0;
			for(LineVio vio : msgs.keySet())
			{
				DetailedLineVio detailedVio = msgs.get(vio);
				FileUtils.writeNewLine(reportFilePath, "		"+detailedVio.getMessage());		
				instVios += detailedVio.getInstVios();
				totalVios += detailedVio.getTotalVios();
			}
			FileUtils.writeNewLine(reportFilePath, "	Method Line Violations: "+msgs.size());
			FileUtils.writeNewLine(reportFilePath, "	Method Instruction Violations: "+instVios);
			FileUtils.writeNewLine(reportFilePath, "	Method Total Violations: "+totalVios);

			total += msgs.size();
		}
		FileUtils.writeNewLine(reportFilePath, "Total Line Violations: "+total);
		return msgsPerMethod;
	}

	public static void printAllViolationsPaths(List<ViolationResult> results, SDG sdg, String reportFilePath) throws IOException
	{
		int v = 1;
		for(ViolationResult vioResult : results)
		{		
			Collection<? extends IViolation<SecurityNode>> vios = vioResult.getResult();
			Iterator<? extends IViolation<SecurityNode>> it = vios.iterator();
			Collection<ClassifiedViolation> classifVios = new ArrayList<ClassifiedViolation>();// = new 
			List<List<List<String>>> allViosLinePaths = new ArrayList<List<List<String>>>();
			while(it.hasNext())
			{
				IViolation<SecurityNode> vio = it.next();

				if(vio instanceof ClassifiedViolation)
				{
					classifVios.add((ClassifiedViolation) vio);

					/*
					ClassifiedViolation classifVio = new edu.kit.joana.ifc.sdg.core.violations.paths.PathGenerator(sdg).computeAllPaths((ClassifiedViolation) vio);
					ViolationPathes pathes = classifVio.getViolationPathes();
					FileUtils.writeNewLine(reportFilePath,"Path Violation "+v 
							+ " - Info Source: "
								+ classifVio.getSource().getLabel() 
								+" {"+ classifVio.getSource().getOperation() + "} "
								+ "["+classifVio.getSource().getSource()
								+":LINES "+classifVio.getSource().getSr()+"-"+classifVio.getSource().getEr()
							+"], Leak: "+classifVio.getSink().getLabel() 
								+ " {"+classifVio.getSink().getOperation()
								+"} "
								+ "["+classifVio.getSink().getSource()+
								":LINES "+classifVio.getSink().getSr()+"-"+classifVio.getSink().getEr()+"]"
					);
					List<List<String>> linePaths = new ArrayList<List<String>>();
					List<String> head = new ArrayList<String>();
					head.add("Path Violation "+v 
							+" - Info Source: ["+ classifVio.getSource().getSource()
							+":LINES "+classifVio.getSource().getSr()+"-"+classifVio.getSource().getEr()
							+ "], Leak: ["+classifVio.getSink().getSource()+
							":LINES "+classifVio.getSink().getSr()+"-"+classifVio.getSink().getEr() + "]");
					linePaths.add(head);
					v++;
					int p = 1;

					for(ViolationPath vioPath : pathes.getPathesListCopy())
					{
						LinkedList<SecurityNode> pathList = vioPath.getPathList();
						List<String> path = new ArrayList<String>();
						SecurityNode node;
						String line;
						if(vioPath.getPathList().size() > 0)
						{
							FileUtils.writeNewLine(reportFilePath,"      Path "+ p + " - ");
							path.add("      Path "+ p + " - ");
							p++;
						}
						for(int i = 0; i < pathList.size() - 1;i++)
						{
							node = pathList.get(i);
							line = "           ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr() +"] ->";
							if(i == 0 || !path.get(path.size() - 1).equals(line))
							{
								path.add(line);
							}

							FileUtils.writeNewLine(reportFilePath, "           ("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " +node.getLabel() + " {" + node.getOperation() +"} ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr()+"] -> ");
						}
						if(pathList.size() > 0)
						{
							node = pathList.get(pathList.size() - 1);
							line = "           ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr() +"]";

							if(pathList.size() == 1 || !path.get(path.size() - 1).equals(line + " ->"))
							{
								path.add(line);
							}

							FileUtils.writeNewLine(reportFilePath, "           ("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " +node.getLabel() + " {" + node.getOperation() +"} ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr()+"]");
						}
						linePaths.add(path);						
					}	
					allViosLinePaths.add(linePaths);
					//*/
				}
			}
			/*
			Collection<ClassifiedViolation> classVios = new edu.kit.joana.ifc.sdg.core.metrics.CallGraphMetrics().computeMetrics(sdg,classifVios);
			for(ClassifiedViolation classVio : classVios)
			{
				ClassifiedViolation classifVio = new edu.kit.joana.ifc.sdg.core.violations.paths.PathGenerator(sdg).computeAllPaths((ClassifiedViolation) classVio);
				System.out.println(classifVio.getSource());
				System.out.println(classifVio.getSink());
				System.out.println("Vios: "+classifVio.getViolationPathes());
				System.out.println();
			}
			 */
			for(ClassifiedViolation classifVio : ViolationChop.getInstance().addChop(classifVios, sdg)){				
				ViolationPathes pathes = classifVio.getViolationPathes();
				//Collection<Chop> aux = classifVio.getChops();
				FileUtils.writeNewLine(reportFilePath,"Path Violation "+v 
						+ " - Info Source: "
						+ classifVio.getSource().getLabel() 
						+" {"+ classifVio.getSource().getOperation() + "} "
						+ "["+classifVio.getSource().getSource()
						+":LINES "+classifVio.getSource().getSr()+"-"+classifVio.getSource().getEr()
						+"], Leak: "+classifVio.getSink().getLabel() 
						+ " {"+classifVio.getSink().getOperation()
						+"} "
						+ "["+classifVio.getSink().getSource()+
						":LINES "+classifVio.getSink().getSr()+"-"+classifVio.getSink().getEr()+"]"
						);

				v++;
				int p = 1;
				for(ViolationPath vioPath : pathes.getPathesListCopy())
				{											
					//Try calculate the path					
					Collection<SecurityNode> invNodes = vioPath.getAllInvolvedNodes();
					if(invNodes.size() > 0)
					{
						FileUtils.writeNewLine(reportFilePath,"      Path "+ p + " - ");
						System.out.println("           Path by Id: "+vioPath);
						p++;
						FileUtils.writeNewLine(reportFilePath, "           Involved nodes: "+invNodes);
						printPath(reportFilePath, invNodes, ",");
						printOrderedEdges(sdg, invNodes, classifVio.getSource(), classifVio.getSink(), reportFilePath);
					}


				}				
			}
			//*/
			//printViolationsLinePaths(allViosLinePaths, reportFilePath);
		}
	}

	private static void printOrderedEdges(SDG sdg, Collection<SecurityNode> invNodes, SecurityNode source, SecurityNode sink, String reportFilePath) throws IOException {
		ViolationPathCollector coll = new ViolationPathCollector(sdg);
		List<List<SDGEdge>> pathes = coll.getOrderedEdges(invNodes, source, sink);	
		List<List<List<SDGEdge>>> mergedPaths = coll.mergePaths(pathes);
		System.out.println("           Merged paths:");
		for(List<List<SDGEdge>> path : mergedPaths)
		{
			System.out.println("                 Merged path: "+coll.getOrderedNodes(coll.getFirstPath(path)));			
			for(List<SDGEdge> relatedNodesEdges : path)
			{
				System.out.print("                    "+relatedNodesEdges.get(0).getSource() + " -");
				for(int i = 0; i < relatedNodesEdges.size() - 1; i++)
				{
					System.out.print(relatedNodesEdges.get(i).getKind().name() + "/");
				}

				System.out.println(relatedNodesEdges.get(relatedNodesEdges.size() - 1).getKind().name()+
						"-> "+relatedNodesEdges.get(0).getTarget());
			}
		}
		FileUtils.writeNewLine(reportFilePath, "           Ordered paths: "+pathes);
		for(List<SDGEdge> path : pathes)
		{
			System.out.println("                 Ordered path: "+coll.getOrderedNodes(path));
			System.out.println("                    "+path);
			/*
			for(SDGEdge edge : path)
			{
				System.out.println("                    "+edge.getSource() + " -"+edge.getKind().name()+"-> "+edge.getTarget());
			}*/
		}
	}

	private static void printPath(String reportFilePath, Collection<SecurityNode> pathList, String sep) throws IOException {
		SecurityNode node;
		Iterator<SecurityNode> it = pathList.iterator();

		//for(int i = 0; i < pathList.size() - 1;i++)
		String lineContent = null;
		if(it.hasNext())
		{
			node = it.next();
			lineContent = "              "+node.getId() +":("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " 
					+node.getLabel() + " {" + node.getOperation() +"} ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr()
					+"] " + sep;
		}
		while(it.hasNext())		
		{
			FileUtils.writeNewLine(reportFilePath, lineContent);
			node = it.next();
			lineContent = "              "+node.getId()+":("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " 
					+node.getLabel() + " {" + node.getOperation() +"} ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr()
					+"] " + sep;
		}
		if(lineContent != null)
		{
			FileUtils.writeNewLine(reportFilePath, 
					lineContent.substring(0, lineContent.lastIndexOf(sep)));
		}

		/*
		if(pathList.size() > 0)
		{
			node = pathList.get(pathList.size() - 1);
			FileUtils.writeNewLine(reportFilePath, "              ("+ node.getBytecodeName()+":"+node.getBytecodeIndex()+") " 
					+node.getLabel() + " {" + node.getOperation() +"} ["+node.getSource()+":LINES "+node.getSr()+"-"+node.getEr()
					+"]");
		}
		 */
	}

	private static void printViolationsLinePaths(
			List<List<List<String>>> allViosLinePaths, String reportFilePath) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "");
		FileUtils.writeNewLine(reportFilePath, "VIOLATION PATHES BY SOURCE CODE LINE");
		for(List<List<String>> vioLinePaths : allViosLinePaths)
		{
			for(List<String> path : vioLinePaths)
			{
				for(String line : path)
				{
					FileUtils.writeNewLine(reportFilePath, line);
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

	public static void printAllMethodsWithBothAffect(
			Map<String, Map<Integer, LineInterferencesPoints>> allInterferencesByLine,
			String reportFilePath) {
		System.out.println("FLOWS FROM LEFT AND RIGHT TO OTHERS");
		for(String method : allInterferencesByLine.keySet())
		{
			if(!allInterferencesByLine.get(method).isEmpty())
			{
				System.out.println("Method "+method);
				System.out.println("	Affected lines by both: "+allInterferencesByLine.get(method).keySet());
				for(Integer sinkLine : allInterferencesByLine.get(method).keySet())
				{
					LineInterferencesPoints lineInterferences = allInterferencesByLine.get(method).get(sinkLine);
					System.out.println("		Affected line: "+ sinkLine);
					System.out.println("			Sinks: "+lineInterferences.getSinks());
					System.out.println("			LEFT SOURCES LINES: "+lineInterferences.getSourcesLeftMap().keySet());
					System.out.println("				LEFT SOURCES: "+lineInterferences.getSourcesLeftMap());
					System.out.println("			RIGHT SOURCES LINES: "+lineInterferences.getSourcesRightMap().keySet());
					System.out.println("				RIGHT SOURCES: "+lineInterferences.getSourcesRightMap()/*.keySet()*/);
				}
			}
		}


	}

	public static void printAllExecutionsSummary(
			Map<String, Map<SdgConfigValues, ExecutionResult>> execResults,
			String reportFilePath, String sep) throws IOException {
		String header = MethodExecutionResult.getHeader(sep);
		if(execResults.size() > 0 && !header.equals(FileUtils.readFirstLine(reportFilePath)))
		{
			FileUtils.writeNewLine(reportFilePath, header, false);
		}

		for(String method : execResults.keySet())
		{
			Map<SdgConfigValues, ExecutionResult> methExecResults = execResults.get(method);
			for(SdgConfigValues confVal : methExecResults.keySet())
			{
				ExecutionResult execRes = methExecResults.get(confVal);
				FileUtils.writeNewLine(reportFilePath, execRes.toString(sep));
			}
		}
	}
}
