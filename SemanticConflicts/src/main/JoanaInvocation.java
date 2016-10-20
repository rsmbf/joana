package main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.annotations.IFCAnnotation;
import edu.kit.joana.api.lattice.BuiltinLattices;
import edu.kit.joana.api.sdg.SDGAttribute;
import edu.kit.joana.api.sdg.SDGBuildPreparation;
import edu.kit.joana.api.sdg.SDGClass;
import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGInstruction;
import edu.kit.joana.api.sdg.SDGMethod;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.ifc.sdg.util.JavaType;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import edu.kit.joana.wala.util.NotImplementedException;
import execResult.DetailedLineVio;
import execResult.ExecutionResult;
import execResult.LineVio;
import execResult.MethodExecutionResult;
import execResult.SdgConfigValues;
import gnu.trove.map.TObjectIntMap;
import util.FileUtils;
import util.ViolationsPrinter;

public class JoanaInvocation {
	private Map<String, ModifiedMethod> modMethods;
	private String classPath;
	private String[] libPaths;
	private String reportFolderPath;
	private String sdgsFolderPath;
	private Map<String, Map<SdgConfigValues, ExecutionResult>> execResults;
	private boolean saveSdgs;

	private static final PointsToPrecision[] precisions = new PointsToPrecision[] {
		PointsToPrecision.TYPE_BASED, PointsToPrecision.INSTANCE_BASED, PointsToPrecision.OBJECT_SENSITIVE,
		PointsToPrecision.N1_OBJECT_SENSITIVE, PointsToPrecision.UNLIMITED_OBJECT_SENSITIVE, 
		PointsToPrecision.N1_CALL_STACK, PointsToPrecision.N2_CALL_STACK, PointsToPrecision.N3_CALL_STACK };

	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods)
	{	
		this(projectPath, System.getProperty("user.dir")+File.separator + "reports", System.getProperty("user.dir")+File.separator + "sdgs", modMethods);
	}
	
	public JoanaInvocation(String projectPath, String reportFolderPath, String sdgsFolderPath, Map<String, ModifiedMethod> modMethods)
	{
		this(projectPath, modMethods, "", null, reportFolderPath, sdgsFolderPath);
	}
	
	public JoanaInvocation(String projectPath, String reportFolderPath, String sdgsFolderPath, String libPaths, Map<String, ModifiedMethod> modMethods)
	{
		this(projectPath, modMethods, "", libPaths, reportFolderPath, sdgsFolderPath);
	}
	
	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods, String binPath, String libPaths, String reportFolderPath, String sdgsFolderPath)
	{
		this.saveSdgs = sdgsFolderPath != null && !sdgsFolderPath.equals("");
		this.classPath = projectPath + binPath;
		//this.srcPath = projectPath + srcPath;
		if(libPaths != null)
		{
			this.libPaths = libPaths.split(":");
			for(int i = 0; i < this.libPaths.length; i++)
			{
				if(!this.libPaths[i].contains(projectPath))
				{
					this.libPaths[i] = projectPath + this.libPaths[i];
				}

			}
		}
		this.modMethods = modMethods;		
		
		//parts_map = new HashMap<SDGProgramPart, Integer>();	
		//leftInstructions = new ArrayList<SDGInstruction>();
		//rightInstructions = new ArrayList<SDGInstruction>();
		//otherInstructions = new ArrayList<SDGInstruction>();
		this.reportFolderPath = reportFolderPath;
		this.sdgsFolderPath = sdgsFolderPath;
		execResults = new HashMap<String, Map<SdgConfigValues, ExecutionResult>>();
	}

	private void printSourcesAndSinks(Collection<IFCAnnotation> sources, Collection<IFCAnnotation> sinks, String reportFilePath) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "Sources: "+sources.size());
		for(IFCAnnotation source : sources)
		{
			FileUtils.writeNewLine(reportFilePath,"	SOURCE: "+ source.toString());
		}
		FileUtils.writeNewLine(reportFilePath,"Sinks: "+sinks.size());
		for(IFCAnnotation sink : sinks)
		{
			FileUtils.writeNewLine(reportFilePath,"	SINK: "+sink.toString());		
		}
	}

	private static boolean signaturesMatch(JavaMethodSignature methodEvaluated, JavaMethodSignature currentMethod)
	{
		boolean match = false;
		List<JavaType> evaluatedArgTypes = methodEvaluated.getArgumentTypes();
		List<JavaType> currentArgTypes = currentMethod.getArgumentTypes();

		if(methodEvaluated.getDeclaringType().equals(currentMethod.getDeclaringType()) 
				&& (methodEvaluated.getMethodName().equals(currentMethod.getMethodName())
						|| (methodEvaluated.getMethodName().equals(methodEvaluated.getDeclaringType().toHRStringShort())
								&& currentMethod.getMethodName().equals("<init>")
								)
						))
		{
			if((evaluatedArgTypes.size() == currentArgTypes.size()) || 
					(evaluatedArgTypes.size() == 1 && currentArgTypes.size() == 0 
					&& evaluatedArgTypes.get(0).toHRString().equals("")))
			{
				int i = 0;
				boolean argsMatch = true;
				JavaType evaluatedType, currentType;
				while(argsMatch && i < currentArgTypes.size())
				{
					evaluatedType = evaluatedArgTypes.get(i);
					currentType = currentArgTypes.get(i);
					if(evaluatedType.toHRString().equals(evaluatedType.toHRStringShort()))
					{
						argsMatch = evaluatedType.toHRStringShort().equals(currentType.toHRStringShort());
					}else if(currentType.toHRStringShort().contains("$") && !evaluatedType.toHRStringShort().contains("$"))
					{
						String currTypeStr = currentType.toHRString();
						String currTypeShortStr = currentType.toHRStringShort();
						String evalTypeStr = evaluatedType.toHRString();
						String evalTypeShortStr = evaluatedType.toHRStringShort();
						int lastIndexShort = currTypeShortStr.lastIndexOf("$");
						String innerClass = currTypeShortStr.substring(lastIndexShort + 1);						
						argsMatch = evalTypeShortStr.equals(innerClass) && 
								(evalTypeStr.equals(currTypeStr.replace("$",".")) 
										|| evalTypeStr.equals(currTypeShortStr.replace("$",".")));
					}
					else{
						argsMatch = evaluatedType.equals(currentType);
					}

					i++;
				}
				match = argsMatch;
			}
		}
		return match;
	}

	private int getMethodLineIndex(String method, List<String> lines)
	{
		int i = 0;
		while(i < lines.size() && !lines.get(i).split(";")[0].trim().equals(method))
		{
			i++;
		}
		if(i >= lines.size())
		{
			i = -1;
		}
		return i;
	}
	
	private void addSourcesAndSinks(SdgConfigValues confValues, String methodEvaluated, Map<String, ModifiedMethod> methodsWithSrcOrSink ) throws IOException {		

		SDGProgram program = confValues.getProgram();
		IFCAnalysis ana = confValues.getIFCAnalysis();
		Collection<SDGClass> classes = program.getClasses();
		boolean sdgLoaded = confValues.getSdgLoaded();
		//System.out.println(classes);
		Iterator<SDGClass> classesIt = classes.iterator();
		boolean methodFound = false;
		JavaMethodSignature methodSignature = methodsWithSrcOrSink.get(methodEvaluated).getMethodSignature();
		JavaType declaringClassType = methodSignature.getDeclaringType();
		//System.out.println("Searched method: "+methodEvaluated);
		Map<Integer, Integer> bytecodeIndexToLine = new TreeMap<Integer, Integer>();
		while(!methodFound && classesIt.hasNext())
		{
			SDGClass SdgClass = classesIt.next();
			//System.out.println(SdgClass.getTypeName().toHRString());
			//System.out.println(SdgClass.getMethods());
			if(SdgClass.getTypeName().equals(declaringClassType)){
				Collection<SDGMethod> methods = SdgClass.getMethods();
				Iterator<SDGMethod> methIt = methods.iterator();
				while(!methodFound && methIt.hasNext())
				{
					SDGMethod method = methIt.next();
					IMethod meth = method.getMethod();

					JavaMethodSignature meth_signature = method.getSignature();
					methodFound = signaturesMatch(methodSignature, meth_signature);
					//System.out.println("Mod sign: "+mod_sign + " , "+methodFound);
					if(methodFound)
					{					
						ModifiedMethod modMethod = methodsWithSrcOrSink.get(methodEvaluated);
						List<Integer> left_cont = modMethod.getLeftContribs();
						//System.out.println(left_cont);
						List<Integer> right_cont = modMethod.getRightContribs();
						//System.out.println(right_cont);
						Collection<SDGInstruction> instructions = method.getInstructions();
						System.out.println("Instructions: "+instructions.size());
						List<Integer> leftIndexes = new ArrayList<Integer>();
						List<Integer> rightIndexes = new ArrayList<Integer>();
						
						if(sdgLoaded)
						{
							List<String> lines = FileUtils.getFileLines(confValues.getSdgInfoFilePath());
							int index = getMethodLineIndex(methodEvaluated, lines);
							String line = lines.get(index);
							String[] lineInfo = line.split(";");
							leftIndexes = toIntegerList(lineInfo[5].trim());
							rightIndexes = toIntegerList(lineInfo[6].trim());
							bytecodeIndexToLine = toIntegersMap(lineInfo[7].trim());
						}
						for(SDGInstruction instruction : instructions ){ 
							boolean leftInst = false;
							boolean rightInst = false;
							int bytecodeIndex = instruction.getBytecodeIndex();
							int line_number = 0;
							if(sdgLoaded)
							{
								leftInst = leftIndexes.contains(bytecodeIndex);
								rightInst = rightIndexes.contains(bytecodeIndex);
								Integer lineVal = bytecodeIndexToLine.get(bytecodeIndex);
								if(lineVal != null)
									line_number = lineVal;
							}else{
								line_number = meth.getLineNumber(bytecodeIndex);	
								bytecodeIndexToLine.put(bytecodeIndex, line_number);
								leftInst = left_cont.contains(line_number);
								rightInst = right_cont.contains(line_number);
							}
							FileUtils.writeNewLine(confValues.getReportFilePath(), "    LINE "+line_number+": "+instruction);
							if(leftInst)							
							{
								//System.out.println("Adding source...");
								ana.addSourceAnnotation(instruction, BuiltinLattices.STD_SECLEVEL_HIGH);
								confValues.addPartToLeft(methodEvaluated, instruction);
							}else if(rightInst)
							{
								//System.out.println("Adding sink...");
								ana.addSinkAnnotation(instruction, BuiltinLattices.STD_SECLEVEL_LOW);
								confValues.addPartToRight(methodEvaluated, instruction);
							}else{
								confValues.addPartToOther(methodEvaluated, instruction);
							}
							confValues.addToPartsMap(instruction, line_number);
						}
					}
				}
			}

		}
		if(!sdgLoaded && saveSdgs && methodFound)
		{
			writeSdgInfo(methodEvaluated, confValues, bytecodeIndexToLine);
		}
	}

	private void writeSdgInfo(String method, SdgConfigValues confValues, Map<Integer, Integer> bytecodeToLine) throws IOException {
		String line = method;
		line += "; " + confValues.getCGNodes();
		line += "; " + confValues.getCGEdges();
		line += "; " + confValues.getTime();
		line += "; " + confValues.getMemory();
		line += "; " + confValues.getPartsIndexes(method, confValues.getLeftParts());
		line += "; " + confValues.getPartsIndexes(method, confValues.getRightParts());
		line += "; " + bytecodeToLine;
		FileUtils.writeNewLine(confValues.getSdgInfoFilePath(), line);
	}

	public void run() throws ClassNotFoundException, ClassHierarchyException, IOException, UnsoundGraphException, CancelException
	{
		Map<String, String> configs = new HashMap<String, String>();
		run(configs);
	}

	public void run(Map<String, String> configs) throws ClassNotFoundException, ClassHierarchyException, IOException, UnsoundGraphException, CancelException
	{
		run(configs, modMethods);
	}

	public void run(Map<String, String> configs, Map<String, ModifiedMethod> methodsWithSrcOrSink) throws ClassNotFoundException, ClassHierarchyException, IOException, UnsoundGraphException, CancelException
	{
		if(!configs.containsKey("methodLevelAnalysis"))
		{
			configs.put("methodLevelAnalysis", "true");
		}
		if(!configs.containsKey("allPrecisions"))
		{
			configs.put("allPrecisions", "false");
		}
		if(!configs.containsKey("allExceptions"))
		{
			configs.put("allExceptions", "false");
		}
		if(!configs.containsKey("initialPrecision"))
		{
			int initial = configs.get("allPrecisions").equals("true") ? 0 : 1;
			configs.put("initialPrecision", initial + "");
		}	
		if(!configs.containsKey("violationPathes"))
		{
			configs.put("violationPathes", "false");
		}
		if(!configs.containsKey("ignoreExceptions"))
		{
			configs.put("ignoreExceptions", "false");
		}

		boolean allExceptions = configs.get("allExceptions").equals("true");
		boolean allPrecisions = configs.get("allPrecisions").equals("true");
		boolean violationPathes = configs.get("violationPathes").equals("true");		
		int initialPrecision = Integer.parseInt(configs.get("initialPrecision"));
		
		if(allExceptions)
		{
			for(int exceptionsInt = 0; exceptionsInt < 2; exceptionsInt++)
			{				
				boolean ignoreExceptions = exceptionsInt == 1;
				configs.put("ignoreExceptions", ignoreExceptions + "");
				runForEachPrecision(configs, methodsWithSrcOrSink, allPrecisions,
						violationPathes, initialPrecision);
			}
		}else{
			runForEachPrecision(configs, methodsWithSrcOrSink, allPrecisions,
					violationPathes, initialPrecision);
		}
		String reportFile = reportFolderPath + File.separator + "executionSummary.csv";
		if(!new File(reportFile).exists())
		{
			FileUtils.createFile(reportFile);
		}
		ViolationsPrinter.printAllExecutionsSummary(execResults, reportFile, ";");		
	}

	private void runForEachPrecision(Map<String, String> configs,
			Map<String, ModifiedMethod> methodsWithSrcOrSink,
			boolean allPrecisions, boolean violationPathes,
			int initialPrecision/*, boolean ignoreExceptions*/)
					throws IOException, ClassHierarchyException, UnsoundGraphException,
					CancelException, FileNotFoundException {
		//int ignoreExceptionsInt = ignoreExceptions ? 1 : 0;
		//SDGConfig config;
		if(allPrecisions)
		{
			for(int i = initialPrecision; i < precisions.length; i++){
				//currentReportFilePath = reportFilePaths[ignoreExceptionsInt].get(precisions[i]);
				//FileUtils.createFile(currentReportFilePath);
				//config = setConfig(ignoreExceptions, precisions[i]);
				runForSpecificPrecision(configs, violationPathes, precisions[i], methodsWithSrcOrSink);
				System.out.println();
			}
		}else{
			//currentReportFilePath = reportFilePaths[ignoreExceptionsInt].get(precisions[initialPrecision]);
			//FileUtils.createFile(currentReportFilePath);
			//config = setConfig(ignoreExceptions, precisions[initialPrecision]);
			runForSpecificPrecision(configs, violationPathes, precisions[initialPrecision], methodsWithSrcOrSink);
		}
	}

	private void printSdgDetails(SdgConfigValues confValues) throws IOException
	{
		SDGProgram program = confValues.getProgram();
		String reportFilePath = confValues.getSdgReportFilePath();
		FileUtils.writeNewLine(reportFilePath, "SDG INFO");
		for(SDGClass sdgClass : program.getClasses())
		{
			FileUtils.writeNewLine(reportFilePath, sdgClass.getTypeName().toHRString());
			Set<SDGAttribute> sdgAttributes = sdgClass.getAttributes();
			FileUtils.writeNewLine(reportFilePath, "    Attributes: "+sdgAttributes.size());
			for(SDGAttribute sdgAttribute : sdgAttributes)
			{
				FileUtils.writeNewLine(reportFilePath, "        "+sdgAttribute.getType().toString() + " "+sdgAttribute.getName());
			}

			Set<SDGMethod> sdgMethods = sdgClass.getMethods();
			FileUtils.writeNewLine(reportFilePath, "    Methods: "+sdgMethods.size());
			for(SDGMethod sdgMethod : sdgMethods){
				FileUtils.write(reportFilePath,  "        "+sdgMethod.getSignature().toHRString());
				FileUtils.writeNewLine(reportFilePath, " - Instructions: "+sdgMethod.getInstructions().size());
				IMethod method = sdgMethod.getMethod();
				for(SDGInstruction inst : sdgMethod.getInstructions())
				{
					FileUtils.writeNewLine(reportFilePath, "            LINE "+method.getLineNumber(inst.getBytecodeIndex())+": "+inst);
					//System.out.println("            LINE "+method.getLineNumber(inst.getBytecodeIndex())+": "+inst);
				}
			}
		}
	}

		private void runForSpecificPrecision(Map<String, String> configs, boolean violationPathes,
				PointsToPrecision precision, Map<String, ModifiedMethod> methodsWithSrcOrSink) throws ClassHierarchyException,IOException, UnsoundGraphException, CancelException,FileNotFoundException {
			boolean methodLevelAnalysis = configs.get("methodLevelAnalysis").equals("true");
			boolean ignoreExceptions = configs.get("ignoreExceptions").equals("true");

		SdgConfigValues confValues = new SdgConfigValues(precision, ignoreExceptions, reportFolderPath, sdgsFolderPath);
		createExecutionResults(methodLevelAnalysis, confValues, methodsWithSrcOrSink);

		String reportFilePath = confValues.getReportFilePath();
		FileUtils.createFile(reportFilePath);
		SDGConfig config = setConfig(ignoreExceptions, precision);

		/** build the PDG */
		SDGProgram program = null;
		IFCAnalysis ana;
		String pdgFileName = "";
		boolean loadSdg = false;
		if(saveSdgs){
			pdgFileName = sdgsFolderPath + File.separator + precision.toString();
			String excep = "_excep";
			if(ignoreExceptions)
			{
				excep = "_noExcep";
			}
			pdgFileName += excep + ".pdg";
			loadSdg = new File(pdgFileName).exists() && new File(confValues.getSdgInfoFilePath()).exists();
		}
		
		if(loadSdg)
		{
			FileUtils.writeNewLine(reportFilePath, "Loading SDG...");
			program = SDGProgram.loadSDG(pdgFileName);
		}else{
			FileUtils.writeNewLine(reportFilePath, "Creating SDG...");
			try{
				String sdgReportFilePath = confValues.getSdgReportFilePath();
				FileUtils.createFile(sdgReportFilePath);
				program = SDGProgram.createSDGProgram(config, new PrintStream(new FileOutputStream(sdgReportFilePath)) , new NullProgressMonitor());
			}catch(Exception e)
			{
				FileUtils.writeNewLine(reportFilePath, "");
				FileUtils.writeNewLine(reportFilePath, "Error Message: "+e.getMessage());
				FileUtils.write(reportFilePath, "Stacktrace: ");
				for(StackTraceElement el : e.getStackTrace())
				{
					FileUtils.writeNewLine(reportFilePath, el.toString());
				}
			}
		}

		boolean sdgBuilt = program != null && program.getSDG() != null;
		confValues.setProgram(program);
		//confValues.setSdgCreated(sdgBuilt);
		if(sdgBuilt)
		{
			confValues.setSdgLoaded(loadSdg);
			/*parts_map = new HashMap<SDGProgramPart, Integer>();	
			leftInstructions = new ArrayList<SDGInstruction>();
			rightInstructions = new ArrayList<SDGInstruction>();
			otherInstructions = new ArrayList<SDGInstruction>();*/
			
			int sdgNodes = program.getSDG().vertexSet().size();
			int sdgEdges = program.getSDG().edgeSet().size();
			FileUtils.writeNewLine(reportFilePath, "SDG: "+ sdgNodes + " nodes and " + sdgEdges + " edges" );
			FileUtils.writeNewLine(reportFilePath, "");
			//confValues.setSdgInfo(sdgNodes, sdgEdges);	
			
			int cgNodes, cgEdges;
			long[] timeAndMem;
			if(loadSdg){
				String sdgInfoFilePath = confValues.getSdgInfoFilePath();
				String firstLine = FileUtils.readNLines(sdgInfoFilePath, 2).get(1);
				String[] lineInfo = firstLine.split(";");
				cgNodes = Integer.parseInt(lineInfo[1].trim());
				cgEdges = Integer.parseInt(lineInfo[2].trim());
				timeAndMem = new long[2];
				timeAndMem[0] = Integer.parseInt(lineInfo[3].trim());
				timeAndMem[1] = Integer.parseInt(lineInfo[4].trim());
			}else{
				printSdgDetails(confValues);
				int[] cgNodesAndEdges = program.getSDGBuilder().getCgNodesAndEdges();
				cgNodes = cgNodesAndEdges[0];
				cgEdges = cgNodesAndEdges[1];
				timeAndMem = SDGBuildPreparation.getTimeAndMemory();
				
				/** optional: save PDG to disk */
				if(saveSdgs)
				{
					FileUtils.mkdirs(new File(pdgFileName));
					SDGSerializer.toPDGFormat(program.getSDG(), new FileOutputStream(pdgFileName));
					String sdgInfoFilePath = confValues.getSdgInfoFilePath();
					FileUtils.createFile(sdgInfoFilePath);
					FileUtils.writeNewLine(sdgInfoFilePath, "Method; CGNodes; CGEdges; Time; Memory; LeftIndexes; RightIndexes; BytecodeToLine");
					String info = cgNodes + "";
					info += "; " + cgEdges;
					info += "; " + timeAndMem[0];
					info += "; " + timeAndMem[1];
					FileUtils.writeNewLine(sdgInfoFilePath, "-; "+ info + "; -; -; -"); 
				}
				FileUtils.writeNewLine(reportFilePath, "");
			}
			confValues.setCgInfo(cgNodes, cgEdges);
			confValues.setTimeAndMemory(timeAndMem[0], timeAndMem[1]);
			
			ana = new IFCAnalysis(program);
			confValues.setIFCAnalysis(ana);
			/** annotate sources and sinks */
			// for example: fields
			//ana.addSourceAnnotation(program.getPart("foo.bar.MyClass.secretField"), BuiltinLattices.STD_SECLEVEL_HIGH);
			//ana.addSinkAnnotation(program.getPart("foo.bar.MyClass.publicField"), BuiltinLattices.STD_SECLEVEL_LOW);
			FileUtils.writeNewLine(reportFilePath, "ANALYSIS");
			if(methodLevelAnalysis)
			{
				Map<String, Map<String, ViolationResult>> methodsWithViosByAnnotation = runAnalysisPerMethod(methodsWithSrcOrSink, confValues);
				Map<String, List<ViolationResult>> results = new HashMap<String, List<ViolationResult>>();
				Map<String, Map<Integer, LineInterferencesPoints>> bothAffectResults = new HashMap<String, Map<Integer, LineInterferencesPoints>>();
				for(String method : methodsWithViosByAnnotation.keySet()){

					ViolationResult leftToRight = methodsWithViosByAnnotation.get(method).get("LEFT->RIGHT");
					ViolationResult rightToLeft = methodsWithViosByAnnotation.get(method).get("RIGHT->LEFT");

					List<ViolationResult> violations = new ArrayList<ViolationResult>();
					if(leftToRight != null || rightToLeft != null)
					{
						if(leftToRight != null)
						{
							violations.add(leftToRight);
						}
						if(rightToLeft != null){
							violations.add(rightToLeft);
						}
						results.put(method, violations);
					}else if(methodsWithViosByAnnotation.get(method).get("LEFT->OTHERS") != null
							&& methodsWithViosByAnnotation.get(method).get("RIGHT->OTHERS") != null)
					{
						Map<Integer,LineInterferencesPoints> interferencesByLine = BothAffect.getInterferencesByLine(confValues.getPartsMap(), 
								methodsWithViosByAnnotation.get(method).get("LEFT->OTHERS").getResultByProgramPart(), 
								methodsWithViosByAnnotation.get(method).get("RIGHT->OTHERS").getResultByProgramPart());
						if(!interferencesByLine.isEmpty())
						{
							bothAffectResults.put(method, interferencesByLine);			
						}
					}


				}
				if(results.size() > 0)
				{
					if(violationPathes)
					{
						ViolationsPrinter.printAllMethodsViolationsPaths(results, program.getSDG(), reportFilePath);
					}

					Map<String, int[]> vios = ViolationsPrinter.printAllMethodsViolations(results, reportFilePath);
					Map<String, Map<LineVio, DetailedLineVio>> lineViosPerMethod = ViolationsPrinter.printAllMethodsViolationsByLine(results, program, confValues.getPartsMap(), reportFilePath);
					for(String method : lineViosPerMethod.keySet())
					{
						ExecutionResult execRes = execResults.get(method).get(confValues);
						execRes.setLineVios(lineViosPerMethod.get(method).keySet());
						execRes.setInstVios(vios.get(method)[0]);
						execRes.setTotalVios(vios.get(method)[1]);
					}
					ViolationsPrinter.printAllViolations(results, reportFilePath, null);
				}else{
					FileUtils.writeNewLine(reportFilePath, "NO FLOW FROM LEFT TO RIGHT OR RIGHT TO LEFT!");
					System.out.println();
					if(bothAffectResults.size() > 0)
					{
						ViolationsPrinter.printAllMethodsWithBothAffect(bothAffectResults,reportFilePath);
					}else{
						FileUtils.writeNewLine(reportFilePath, "NO FLOW FROM LEFT AND RIGHT TO A THIRD POINT!");
					}
				}

			}else{
				Map<String, ViolationResult> viosByAnnotation = runAnalysisForAllMethods(methodsWithSrcOrSink, confValues);
				ViolationResult leftToRight = viosByAnnotation.get("LEFT->RIGHT");
				ViolationResult rightToLeft = viosByAnnotation.get("RIGHT->LEFT");
				List<ViolationResult> results = new ArrayList<ViolationResult>();

				if(leftToRight != null)
				{
					results.add(leftToRight);
				}
				if(rightToLeft != null){
					results.add(rightToLeft);
				}

				if(results.size() > 0)
				{
					if(violationPathes)
					{
						FileUtils.writeNewLine(reportFilePath, "VIOLATIONS PATHS");
						ViolationsPrinter.printAllViolationsPaths(results, program.getSDG(), reportFilePath);
					}

					FileUtils.writeNewLine(reportFilePath, "VIOLATIONS");
					FileUtils.writeNewLine(reportFilePath, "TOTAL VIOLATIONS: " + ViolationsPrinter.printAllViolationsByPart(results, reportFilePath)[1]);
					FileUtils.writeNewLine(reportFilePath, "LINE violations");
					Map<LineVio, DetailedLineVio> msgs = ViolationsPrinter.printAllViolationsByLine(results, program, confValues.getPartsMap(), reportFilePath);
					FileUtils.writeNewLine(reportFilePath, "Total Line Violations: "+msgs.keySet().size());
				}else{
					FileUtils.writeNewLine(reportFilePath, "NO FLOW FROM LEFT TO RIGHT OR RIGHT TO LEFT!");
				}	
			}
		}else{
			FileUtils.writeNewLine(reportFilePath, "FAILED TO BUILD SDG!");
			/*if(methodLevelAnalysis)
				{

				}*/
		}
	}



	private void createExecutionResults(boolean methodLevelAnalysis,
			SdgConfigValues confValues,
			Map<String, ModifiedMethod> methodsWithSrcOrSink) {
		if(methodLevelAnalysis)
		{
			for(String method : methodsWithSrcOrSink.keySet())
			{
				ModifiedMethod modMethod = methodsWithSrcOrSink.get(method);
				ExecutionResult methExecResult = new MethodExecutionResult(confValues, method, modMethod.getLeftContribs(), modMethod.getRightContribs());
				put(method, methExecResult);
			}
		}else{
			throw new NotImplementedException();
		}
	}

	private Map<String, ViolationResult> runAnalysisForAllMethods(Map<String, ModifiedMethod> methodsWithSrcOrSink, SdgConfigValues confValues)
			throws IOException {
		IFCAnalysis ana = confValues.getIFCAnalysis();
		String reportFilePath = confValues.getReportFilePath();
		for(String method : methodsWithSrcOrSink.keySet())
		{
			ModifiedMethod modMethod = methodsWithSrcOrSink.get(method);

			FileUtils.writeNewLine(reportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0 || modMethod.getAnomModMethods() != null){
				if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0){
					addSourcesAndSinks(confValues, method, methodsWithSrcOrSink);
				}
				if(modMethod.getAnomModMethods() != null)
				{
					Map<String, ModifiedMethod> anomMethods = modMethod.getAnomModMethods();
					for(String anomMethod : anomMethods.keySet())
					{
						ModifiedMethod anomModMethod = anomMethods.get(anomMethod);
						if(anomModMethod.getLeftContribs().size() > 0 || anomModMethod.getRightContribs().size() > 0 )
						{
							addSourcesAndSinks(confValues, anomMethod, anomMethods);
						}
					}
				}

			}else{
				FileUtils.writeNewLine(reportFilePath, "LEFT AND RIGHT CONTRIBUTIONS ARE EMPTY");
			}
		}
		Collection<IFCAnnotation> sinks = ana.getSinks();
		Collection<IFCAnnotation> sources = ana.getSources();	
		return runAnalysis(sinks, sources, confValues, confValues.getAllLeftParts(), confValues.getAllRightParts(), confValues.getAllOtherParts());
	}

	private Map<String, Map<String, ViolationResult>> runAnalysisPerMethod(Map<String, ModifiedMethod> methodsWithSrcOrSink, SdgConfigValues configValues)
			throws IOException {
		Map<String, Map<String, ViolationResult>> results = new HashMap<String, Map<String, ViolationResult>>();
		IFCAnalysis ana = configValues.getIFCAnalysis();
		String reportFilePath = configValues.getReportFilePath();
		for(String method : methodsWithSrcOrSink.keySet())
		{
			ModifiedMethod modMethod = methodsWithSrcOrSink.get(method);
			//ExecutionResult methExecResult = new MethodExecutionResult(configValues, method, modMethod.getLeftContribs(), modMethod.getRightContribs());
			//put(method, methExecResult);
			FileUtils.writeNewLine(reportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if((modMethod.getLeftContribs().size() > 0 && modMethod.getRightContribs().size() > 0) || modMethod.getAnomModMethods() != null)
			{
				if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0 )
				{
					addSourcesAndSinks(configValues, method, methodsWithSrcOrSink);
				}
				if(modMethod.getAnomModMethods() != null)
				{
					Map<String, ModifiedMethod> anomMethods = modMethod.getAnomModMethods();
					for(String anomMethod : anomMethods.keySet())
					{
						ModifiedMethod anomModMethod = anomMethods.get(anomMethod);
						if(anomModMethod.getLeftContribs().size() > 0 || anomModMethod.getRightContribs().size() > 0 )
						{
							addSourcesAndSinks(configValues, anomMethod, anomMethods);
						}
					}
				}
				Collection<IFCAnnotation> sinks = ana.getSinks();
				Collection<IFCAnnotation> sources = ana.getSources();
				ExecutionResult methExecResult = execResults.get(method).get(configValues);
				methExecResult.setHasSourceAndSink(sources.size() > 0 && sinks.size() > 0);
				Map<String, ViolationResult> methodResults = runAnalysis(sinks, sources, configValues, configValues.getLeftParts().get(method),
						configValues.getRightParts().get(method), configValues.getOtherParts().get(method));
				if(methodResults.size() > 0)
				{
					methExecResult.setHasLeftToRightVio(methodResults.get("LEFT->RIGHT") != null);
					methExecResult.setHasRightToLeftVio(methodResults.get("RIGHT->LEFT") != null);
					results.put(method, methodResults);
				}
			}else{
				FileUtils.writeNewLine(reportFilePath, "LEFT AND/OR RIGHT CONTRIBUTION IS EMPTY");
			}
			FileUtils.writeNewLine(reportFilePath, "");
		}
		return results;
	}

	private void put(String method, ExecutionResult execResult) {
		Map<SdgConfigValues, ExecutionResult> confValuesMap;
		if(execResults.containsKey(method))
		{
			confValuesMap = execResults.get(method);

		}else{
			confValuesMap = new LinkedHashMap<SdgConfigValues, ExecutionResult>();
			execResults.put(method, confValuesMap);
		}
		confValuesMap.put(execResult.getSdgConfigValues(), execResult);
	}

	private Map<String, ViolationResult> runAnalysis(
			Collection<IFCAnnotation> sinks, Collection<IFCAnnotation> sources, SdgConfigValues confValues, Collection<SDGProgramPart> leftParts, Collection<SDGProgramPart> rightParts, Collection<SDGProgramPart> otherParts) throws IOException {		
		Map<String, ViolationResult> resultsByAnnotation = new HashMap<String, ViolationResult>();
		IFCAnalysis ana = confValues.getIFCAnalysis();
		String reportFilePath = confValues.getReportFilePath();
		resultsByAnnotation.put("LEFT->RIGHT", null);
		resultsByAnnotation.put("RIGHT->LEFT", null);
		resultsByAnnotation.put("LEFT->OTHERS", null);
		resultsByAnnotation.put("RIGHT->OTHERS", null);
		if(sources.size() > 0 && sinks.size() > 0)
		{
			FileUtils.writeNewLine(reportFilePath,"1.1.a analysis");
			printSourcesAndSinks(ana.getSources(), ana.getSinks(), reportFilePath);
			/** run the analysis */
			Collection<? extends IViolation<SecurityNode>> result_1_1_a = ana.doIFC();	
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart_1_1_a = ana.groupByPPPart(result_1_1_a);	

			/** do something with result */

			FileUtils.writeNewLine(reportFilePath, "1.1.b analysis");
			invertSourceAndSinks(sinks, sources, ana);
			printSourcesAndSinks(ana.getSources(), ana.getSinks(), reportFilePath);
			Collection<? extends IViolation<SecurityNode>> result_1_1_b = ana.doIFC();
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart_1_1_b = ana.groupByPPPart(result_1_1_b);	

			if(result_1_1_a.isEmpty() && result_1_1_b.isEmpty())
			{
				FileUtils.writeNewLine(reportFilePath, "1.2.a analysis");
				addSourcesAndSinks_1_2(leftParts, otherParts, confValues);
				printSourcesAndSinks(ana.getSources(), ana.getSinks(), reportFilePath);
				Collection<? extends IViolation<SecurityNode>> result_1_2_a = ana.doIFC();
				TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart_1_2_a = ana.groupByPPPart(result_1_2_a);

				FileUtils.writeNewLine(reportFilePath, "1.2.b analysis");
				addSourcesAndSinks_1_2(rightParts, otherParts, confValues);
				printSourcesAndSinks(ana.getSources(), ana.getSinks(), reportFilePath);
				Collection<? extends IViolation<SecurityNode>> result_1_2_b = ana.doIFC();
				TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart_1_2_b = ana.groupByPPPart(result_1_2_b);
				if(!result_1_2_a.isEmpty())
				{
					resultsByAnnotation.put("LEFT->OTHERS", new ViolationResult(result_1_2_a, resultByProgramPart_1_2_a));
				}
				if(!result_1_2_b.isEmpty())
				{
					resultsByAnnotation.put("RIGHT->OTHERS", new ViolationResult(result_1_2_b, resultByProgramPart_1_2_b));
				}
			}else{
				if(!result_1_1_a.isEmpty()){
					//results_1_1.add(new ViolationResult(result_1_1_a, ana.groupByPPPart(result_1_1_a)));
					resultsByAnnotation.put("LEFT->RIGHT", new ViolationResult(result_1_1_a, resultByProgramPart_1_1_a));
				}
				if(!result_1_1_b.isEmpty())
				{
					//results_1_1.add(new ViolationResult(result_1_1_b, ana.groupByPPPart(result_1_1_b)));
					resultsByAnnotation.put("RIGHT->LEFT", new ViolationResult(result_1_1_b, resultByProgramPart_1_1_b));
				}
			}

		}else{
			FileUtils.writeNewLine(reportFilePath,"0 SOURCES AND/OR SINKS");
		}
		ana.clearAllAnnotations();
		return resultsByAnnotation;
	}

	private void addSourcesAndSinks_1_2(Collection<SDGProgramPart> toMarkAsSource, Collection<SDGProgramPart> toMarkAsSink, SdgConfigValues confValues) {
		IFCAnalysis ana = confValues.getIFCAnalysis();
		ana.clearAllAnnotations();
		for(SDGProgramPart inst : toMarkAsSource)
		{
			ana.addSourceAnnotation(inst, BuiltinLattices.STD_SECLEVEL_HIGH);
		}
		for(SDGProgramPart inst : toMarkAsSink)
		{
			ana.addSinkAnnotation(inst, BuiltinLattices.STD_SECLEVEL_LOW);
		}
	}

	private void invertSourceAndSinks(Collection<IFCAnnotation> sinks,
			Collection<IFCAnnotation> sources, IFCAnalysis ana) {
		ana.clearAllAnnotations();
		for(IFCAnnotation sink : sinks)
		{
			//System.out.println("Adding source...");
			ana.addSourceAnnotation(sink.getProgramPart(), BuiltinLattices.STD_SECLEVEL_HIGH);
		}

		for(IFCAnnotation source : sources)
		{
			//System.out.println("Adding sink...");
			ana.addSinkAnnotation(source.getProgramPart(), BuiltinLattices.STD_SECLEVEL_LOW);
		}
	}

	private SDGConfig setConfig(boolean ignoreExceptions, PointsToPrecision pointerAnalysis) {
		/** the class path is either a directory or a jar containing all the classes of the program which you want to analyze */
		//String classPath = projectPath + "/bin";//"/data1/mmohr/git/CVJMultithreading/bin";

		///Users/Roberto/Documents/UFPE/Msc/Projeto/joana_rcaa/joana/example/joana.example.tiny-special-tests/bin
		//COMPILAR PROJETO (PELO MENOS A CLASSE ADICIONADA)
		//javac -sourcepath src src/JoanaEntryPoint.java -d bin		
		/** the entry method is the main method which starts the program you want to analyze */	

		List<String> entryMethods = new ArrayList<String>();
		for(String method : modMethods.keySet())
		{
			JavaMethodSignature methodSignature = JavaMethodSignature.fromString(method);
			String fullDeclaringType = methodSignature.getDeclaringType().toHRStringShort();
			String[] splitDeclType = fullDeclaringType.split("\\$");
			String declaringType = splitDeclType[splitDeclType.length - 1];
			if(methodSignature.getMethodName().equals(declaringType)){
				String signature = methodSignature.getReturnType().toHRString() + " "+methodSignature.getDeclaringType().toHRString() + ".<init>(";
				for(JavaType arg : methodSignature.getArgumentTypes()){
					signature += arg.toHRString() + " , " ;
				}
				if(methodSignature.getArgumentTypes().size() > 0){
					signature = signature.substring(0, signature.length() - 3);
				}
				signature += ")";		
				methodSignature = JavaMethodSignature.fromString(signature);
			}
			entryMethods.add(methodSignature.toBCString());
		}
		/** For multi-threaded programs, it is currently neccessary to use the jdk 1.4 stubs */
		Stubs stubs = Stubs.JRE_15;
		System.out.println("Stubs version: "+stubs.toString());
		SDGConfig config = new SDGConfig(classPath, null, stubs);
		config.setEntryMethods(entryMethods);
		/** compute interference edges to model dependencies between threads (set to false if your program does not use threads) */
		config.setComputeInterferences(false);

		/** additional MHP analysis to prune interference edges (does not matter for programs without multiple threads) */
		//config.setMhpType(MHPType.PRECISE);

		config.setPointsToPrecision(pointerAnalysis);

		/** exception analysis is used to detect exceptional control-flow which cannot happen */
		config.setExceptionAnalysis(ignoreExceptions ? ExceptionAnalysis.IGNORE_ALL : ExceptionAnalysis.INTERPROC);			
		config.setThirdPartyLibsPath(libPaths != null ? String.join(System.getProperty("path.separator"), libPaths) : null);

		return config;
	}

	private static List<Integer> toIntegerList(String str) {
		List<Integer> result = new ArrayList<Integer>();
		if(!str.equals("[]"))
		{
			String[] lines = str.substring(1, str.length() - 1).split(", ");
			if(lines.length > 1 || (lines.length == 1 && !lines[0].trim().isEmpty()))
			{
				for(String line : lines)
				{
					result.add(Integer.parseInt(line.trim()));
				}
			}				
		}

		return result;
	}
	
	private static Map<Integer, Integer> toIntegersMap(String str)
	{
		Map<Integer, Integer> intToIntMap = new HashMap<Integer, Integer>();
		if(!str.equals("{}"))
		{
			String[] items = str.substring(1, str.length() - 1).split(", ");
			if(items.length > 1 || (items.length == 1 && !items[0].trim().isEmpty()))
			{
				for(String item : items)
				{
					String[] kvPair = item.split("=");
					intToIntMap.put(Integer.parseInt(kvPair[0].trim()), Integer.parseInt(kvPair[1].trim()));
				}
			}
		}
		return intToIntMap;
	}

	public static Map<String, String> getConfigs(String[] args, int begin) {
		Map<String, String> configs = new HashMap<String, String>();
		if(args != null && args.length >= 1)
		{
			for(int i = begin; i < args.length; i++)
			{
				if(args[i] != null)
				{
					String[] kv = args[i].split("=");
					if(kv.length == 2)
						configs.put(kv[0].trim(), kv[1].trim());
				}

			}
		}
		return configs;
	}

	public static void main(String[] args) throws ClassHierarchyException, IOException, UnsoundGraphException, CancelException, ClassNotFoundException {				
		Map<String, ModifiedMethod> methods = new HashMap<String, ModifiedMethod>();	
		System.out.println("Called joana - args: "+args.length);
		System.out.println(JoanaInvocation.toIntegerList("[]"));
		for(String arg : args)
		{
			System.out.println("	Arg: "+arg);
		}
		String git_path = args[0].trim();
		String reports_path = args[1].trim();
		String sdgs_path = args[2].trim();
		String[] contribs = args[3].trim().split("\n");

		for(String contrib : contribs)
		{
			String[] contribElems = contrib.split(";");
			System.out.println("Contrib: "+contrib);
			if(contribElems.length > 1)
			{
				String method = contribElems[4].trim();
				String leftStr = contribElems[6].trim();
				String rightStr = contribElems[7].trim();
				System.out.println("Signature: "+method);
				System.out.println("Left: "+toIntegerList(leftStr));
				System.out.println("Right: "+toIntegerList(rightStr));
				methods.put(method, new ModifiedMethod(method, toIntegerList(leftStr), toIntegerList(rightStr)));
			}
		}
		String libPaths = null;
		if(!args[4].trim().equals(""))
		{
			String fullLibPaths = args[4].trim();
			System.out.println("LibPaths: "+fullLibPaths);
			String[] libPathsList = fullLibPaths.split(":");
			String[] fullLibPathsList = new String[libPathsList.length];

			for(int i = 0; i < fullLibPathsList.length; i++)
			{
				if(!libPathsList[i].contains(git_path))
				{
					fullLibPathsList[i] = git_path + libPathsList[i];
				}else{
					fullLibPathsList[i] = libPathsList[i];
				}
			}
			String firstLib = fullLibPathsList[0];
			String basePath = "";
			if(firstLib.endsWith("*") /*&& new File(firstLib.substring(0, firstLib.length() - 1)).exists()*/)
			{
				//basePath = new File(firstLib.substring(0, firstLib.length() - 1)).getAbsolutePath();
				firstLib = firstLib.substring(0, firstLib.length() - 1);
			} 

			if(new File(firstLib).exists() && new File(firstLib).isDirectory())
			{
				basePath = new File(firstLib).getAbsolutePath();
			}
			else if(new File(firstLib).exists() && new File(firstLib).isFile()){
				basePath = new File(firstLib).getParent();
			}
			System.out.println("BasePath: "+basePath);
			libPaths = String.join(":",FileUtils.getAllJarFiles(basePath, String.join(":", fullLibPathsList)));
			System.out.println("FullLibPaths: "+libPaths);
		}
		JoanaInvocation joana = new JoanaInvocation(git_path, reports_path, sdgs_path, libPaths, methods);
		joana.run(getConfigs(args, 5));
		/*
			String base_path = args[0]; //* /"/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/";
			//String rev = base_path ;+ "RxJava/revs/rev_fd9b6-4350f";
			//String rev = base_path + "RxJava/revs/rev_29060-15e64";
			//String rev = base_path + "RxJava/revs/rev_e30a3-cdb74";

			//String rev = base_path + "voldemort/revs/rev_df73c_dc509/rev_df73c-dc509";// + "voldemort/revs/rev_df73c_dc509/rev_df73c-dc509";//"RxJava/revs/rev_fd9b6-4350f";// + "voldemort/revs/rev_df73c_dc509/rev_df73c-dc509";
			//String rev = base_path + "voldemort/revs/rev_24c82_649c0/rev_24c82-64c90";
			//String rev = base_path;// + "voldemort/revs/rev_e0f18_d44ca/rev_e0f18-d44ca";

			String rev = base_path;

			//String rev = base_path + "OpenRefine/revs/rev_f8376-f87b8";

			String projectPath = rev;// + "/git"; 
			String src = "/src/main/java";//"/src";//"/src/java";//"/src/main/java";
			//String fullSrc = projectPath + src;
			String reportsPath = rev + "/reports";
			String bin = "/bin";//"/bin";//"/dist/classes";//"/build/classes/main";
			JoanaInvocation joana = new JoanaInvocation(projectPath, methods, bin, src, null/*"/main/webapp/WEB-INF/lib/json-20100208.jar"*//*"/lib/*:/dist"* /, reportsPath);

			/*
		joana.compilePaths(new ArrayList<String>(
				Arrays.asList(new String[]{fullSrc + "/rx/internal/operators/Anon_Subscriber.java",
						fullSrc + "/rx/internal/operators/Anon_Producer.java",
						fullSrc + "/rx/internal/operators/OperatorOnBackPressureDrop.java"
				})), "anon_comp_report.txt");

			 */
		///*
		//right = new ArrayList<Integer>();
		//left = new ArrayList<Integer>();
		//right.add(13);
		//methods.put("void rx.internal.operators.Anon_Producer.request(long)", new ModifiedMethod("void rx.internal.operators.Anon_Producer.request(long)", new ArrayList<String>(Arrays.asList(new String[]{"AtomicLong"})), left, right, new ArrayList<String>()));

		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(37);
		left.add(38);
		left.add(39);
		left.add(40);
		left.add(41);
		methods.put("rx.internal.operators.Anon_Subscriber.onNext(Object)", new ModifiedMethod("rx.internal.operators.Anon_Subscriber.onNext(Object)", new ArrayList<String>(Arrays.asList(new String[]{"Subscriber","AtomicLong", "Action1"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"java.util.concurrent.atomic.AtomicLong","rx.Observable.Operator","rx.Producer","rx.Subscriber", "rx.functions.Action1"}))));
		// */
		//methods.put("void rx.internal.operators.Anon_Subscriber.onNext(Object)", new ModifiedMethod("void rx.internal.operators.Anon_Subscriber.onNext(Object)", new ArrayList<String>(Arrays.asList(new String[]{"Subscriber","AtomicLong", "Action1"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"java.util.concurrent.atomic.AtomicLong","rx.Observable.Operator","rx.Producer","rx.Subscriber", "rx.functions.Action1"}))));		
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(61);
		left.add(68);
		right.add(69);
		 */
		/*
		Map<String, ModifiedMethod> anomModMethods = new HashMap<String, ModifiedMethod>();
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(37);
		left.add(38);
		left.add(39);
		left.add(40);
		left.add(41);
		//anomModMethods.put("void rx.internal.operators.Anon_Subscriber.onNext(Object)", new ModifiedMethod("void rx.internal.operators.Anon_Subscriber.onNext(Object)", new ArrayList<String>(Arrays.asList(new String[]{"Subscriber","AtomicLong", "Action1"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"java.util.concurrent.atomic.AtomicLong","rx.Observable.Operator","rx.Producer","rx.Subscriber", "rx.functions.Action1"}))));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		right.add(13);

		//anomModMethods.put("void rx.internal.operators.Anon_Producer.request(long)", new ModifiedMethod("void rx.internal.operators.Anon_Producer.request(long)", new ArrayList<String>(Arrays.asList(new String[]{"AtomicLong"})), left, right, new ArrayList<String>()));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		methods.put("Subscriber rx.internal.operators.OperatorOnBackpressureDrop.call(Subscriber)", new ModifiedMethod("Subscriber rx.internal.operators.OperatorOnBackpressureDrop.call(Subscriber)", new ArrayList<String>(Arrays.asList(new String[]{ "Action1"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"java.util.concurrent.atomic.AtomicLong","rx.Observable.Operator","rx.Producer","rx.Subscriber", "rx.functions.Action1"})), anomModMethods));

		/*
		joana.compilePaths(new ArrayList<String>(Arrays.asList(new String[] {
				fullSrc + "/rx/Anon_Subscriber.java",
				fullSrc + "/rx/Anon_Subscriber_Obs.java",
				fullSrc + "/rx/observers/Subscribers.java",
				fullSrc + "/rx/internal/operators/OperatorMulticast.java"
		})), "anon_comp_report.txt");
		 */
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		right.add(116);
		left.add(139);
		left.add(140);
		left.add(156);
		methods.put("void rx.internal.operators.OperatorMulticast.connect(Action1)", new ModifiedMethod("void rx.internal.operators.OperatorMulticast.connect(Action1)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Observable","rx.functions.Func0"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"rx.functions.Action1"}))));
		//*/
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(12);
		left.add(13);
		left.add(14);
		left.add(15);
		 */
		//methods.put("void rx.internal.operators.Anon_Subscriber.onNext(java.lang.Object)", new ModifiedMethod("void rx.internal.operators.Anon_Subscriber.onNext(java.lang.Object)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right, new ArrayList<String>()));
		/*
		left = new ArrayList<Integer>();
		left.add(17);
		left.add(18);
		left.add(19);
		left.add(20);
		methods.put("rx.Anon_Subscriber.onError(Throwable)", new ModifiedMethod("rx.Anon_Subscriber.onError(Throwable)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right,new ArrayList<String>()));

		left = new ArrayList<Integer>();
		left.add(22);
		left.add(23);
		left.add(24);
		left.add(25);
		methods.put("rx.Anon_Subscriber.onCompleted()", new ModifiedMethod("rx.Anon_Subscriber.onCompleted()", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right, new ArrayList<String>()));
		 */
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(820);
		left.add(827);
		right.add(731);
		methods.put("void voldemort.server.VoldemortConfig.VoldemortConfig(voldemort.utils.Props)", new ModifiedMethod("void voldemort.server.VoldemortConfig.VoldemortConfig(voldemort.utils.Props)",new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"})),left, right, new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"}))));			
		 //*/
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(145);
		left.add(146);
		left.add(147);
		right.add(141);
		methods.put("void voldemort.VoldemortClientShell.VoldemortClientShell(ClientConfig, String, BufferedReader, PrintStream, PrintStream)", 
				new ModifiedMethod("void voldemort.VoldemortClientShell.VoldemortClientShell(ClientConfig, String, BufferedReader, PrintStream, "
						+ "PrintStream)",new ArrayList<String>(Arrays.asList(new String[] {})),left, right, 
						new ArrayList<String>(Arrays.asList(new String[] {"voldemort.client.ClientConfig", "java.io.BufferedReader", 
								"java.io.PrintStream"}))));
		 // */
		/*

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.addAll(new ArrayList<Integer>(Arrays.asList(new Integer[] {301, 302, 303, 304, 356, 374, 376, 377, 378, 
				379, 381, 382, 383, 637, 639, 640, 641, 643, 644, 645, 646})));
		right.addAll(new ArrayList<Integer>(Arrays.asList(new Integer[]{126, 127, 128, 129, 534, 535, 536, 537, 538, 
				539, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 579, 580, 581, 582, 583, 584, 585, 586, 
				587, 588, 589, 590, 591, 592, 593, 594})));
		methods.put("void voldemort.VoldemortAdminTool.main(String[])", new ModifiedMethod("void voldemort.VoldemortAdminTool.main(String[])",
				new ArrayList<String>(), left, right, new ArrayList<String>()));

		// * /

			right = new ArrayList<Integer>();
			left = new ArrayList<Integer>();
			//left.add(18);
			//right.add(23);
			//right.add(29);
			//methods.put("void TestFlow.<init>(Props)", new ModifiedMethod("void TestFlow.<init>(Props)", new ArrayList<String>(), left, right, new ArrayList<String>()));

			// * /
			/// *



			left.add(18);
			right.add(17);
			right.add(19);
			methods.put("void TestFlow2.<init>(Props)", new ModifiedMethod("void TestFlow2.<init>(Props)", new ArrayList<String>(), left, right, new ArrayList<String>()));
			//right = new ArrayList<Integer>();
			//left = new ArrayList<Integer>();
			//left.add(59);
			//right.add(60);
			//methods.put("void MyMap.main(java.lang.String[])", new ModifiedMethod("void MyMap.main(java.lang.String[])", left, right));
			//methods.put("void Props.main(java.lang.String[])", new ModifiedMethod("void Props.main(java.lang.String[])", left, right));
			//methods.put("paramsEx.TestParamsExample.main(String[])", new ModifiedMethod("paramsEx.TestParamsExample.main(String[])", left, right));
			//methods.put("void paramsEx.A.m(paramsEx.Param)", new ModifiedMethod("void paramsEx.A.m(paramsEx.Param)", left, right));
			//methods.put("void paramsEx.A.m2(paramsEx.Param)", new ModifiedMethod("void paramsEx.A.m2(paramsEx.Param)", left, right));
			//methods.put("void paramsEx.A.m3(paramsEx.Param)", new ModifiedMethod("void paramsEx.A.m3(paramsEx.Param)", left, right));

			//methods.put("Object paramsEx.TestParamsExample.teste(String)", new ModifiedMethod("Object paramsEx.TestParamsExample.teste(String)", left, right));

			//joana.run(true, true);
			//joana.run(false, false);
			//methods.put("void returnObjEx.OperatorOnBackpressureDrop.main(java.lang.String[])", new ModifiedMethod("void returnObjEx.OperatorOnBackpressureDrop.main(java.lang.String[])", left, right));
			//methods.put("returnObjEx.Anon_Subscriber returnObjEx.OperatorOnBackpressureDrop.call()", new ModifiedMethod("returnObjEx.Anon_Subscriber returnObjEx.OperatorOnBackpressureDrop.call()", left, right));
			//left.add(9);
			//right.add(16);
			//right.add(13);
			//right.add(17);
			//right.add(28);
			//right.add(30);
			//methods.put("void Fig2_1.main(java.lang.String[])", new ModifiedMethod("void Fig2_1.main(java.lang.String[])", left, right));
			//left.add(821);
			//right.add(842);
			//methods.put("void rx.internal.operators.OperatorConcatTest.testIssue2890NoStackoverflow()", new ModifiedMethod("void rx.internal.operators.OperatorConcatTest.testIssue2890NoStackoverflow()", left, right));
			//
			/*
		left.add(109);
		right.add(106);
		right.add(111);
		right.add(112);
		methods.put("void com.google.refine.importers.SeparatorBasedImporter.parseOneFile(com.google.refine.model.Project,com.google.refine.ProjectMetadata,com.google.refine.importing.ImportingJob,String,java.io.Reader,int,org.json.JSONObject,java.util.List)", new ModifiedMethod("void com.google.refine.importers.SeparatorBasedImporter.parseOneFile(com.google.refine.model.Project,com.google.refine.ProjectMetadata,com.google.refine.importing.ImportingJob,String,java.io.Reader,int,org.json.JSONObject,java.util.List)",left, right));
		// * /
			left = new ArrayList<Integer>();
			right = new ArrayList<Integer>();
			/*
		left.add(50);
		right.add(51);
		right.add(52);
		/*
		left.add(56);
		right.add(57);
		right.add(58);
		 *////*
		//left.add(62);
		//right.add(63);
		//right.add(64);
		//*/
		//methods.put("void ExceptionExample.m()", new ModifiedMethod("void ExceptionExample.m()", new ArrayList<String>(), left, right, new ArrayList<String>()));
		/*
		left = new ArrayList<Integer>();
		right = new ArrayList<Integer>();
		left.add(70);
		right.add(69);
		right.add(71);
		methods.put("void ExceptionExample.n()", new ModifiedMethod("void ExceptionExample.n()", new ArrayList<String>(), left, right, new ArrayList<String>()));* /

			left = new ArrayList<Integer>();
			right = new ArrayList<Integer>();
			//	left.add(11);
			//	right.add(12);
			//	right.add(13);
			//	methods.put("void ExceptionExample.ExceptionExample(int)", new ModifiedMethod("void ExceptionExample.ExceptionExample(int)", new ArrayList<String>(), left, right, new ArrayList<String>()));
			//	methods.put("void ExceptionExample.ExceptionExample()", new ModifiedMethod("void ExceptionExample.ExceptionExample()", new ArrayList<String>(), left, right, new ArrayList<String>()));
			//Map<String, Map<String, ModifiedMethod>> methodsWithSrcOrSink = new HashMap<String, Map<String, ModifiedMethod>>();

			left = new ArrayList<Integer>();
			right = new ArrayList<Integer>();
			//left.add(6);
			//right.add(7);
			//methods.put("void one.two.MurtaExample.main(java.lang.String[])", new ModifiedMethod("void one.two.MurtaExample.main(java.lang.String[])", left, right));
			/*
		left = new ArrayList<Integer>();
		right = new ArrayList<Integer>();
		left.add(20);
		right.add(21);
		methods.put("void one.two.MurtaExample2.main(java.lang.String[])", new ModifiedMethod("void one.two.MurtaExample2.main(java.lang.String[])", left, right));
		 */
		/*
		left = new ArrayList<Integer>();
		right = new ArrayList<Integer>();
		left.add(6);
		left.add(7);
		right.add(8);
		methods.put("void one.two.MurtaExample3.main(java.lang.String[])", new ModifiedMethod("void one.two.MurtaExample3.main(java.lang.String[])", left, right));
		 */
		/*
		left = new ArrayList<Integer>();
		right = new ArrayList<Integer>();		
		left.add(10);
		right.add(12);
		methods.put("void two.cOne.flowToReturn.A.A(int)", new ModifiedMethod("void two.cOne.flowToReturn.A.A(int)", left, right));
		//left.add(17);
		//right.add(19);
		//methods.put("two.cOne.flowToReturn.A two.cOne.flowToReturn.A.m()", new ModifiedMethod("two.cOne.flowToReturn.A two.cOne.flowToReturn.A.m()", left, right));
		//left.add(6);
		//right.add(8);
		//methods.put("void two.cOne.bothWrite.BothWriteMerged.main(java.lang.String[])", new ModifiedMethod("void two.cOne.bothWrite.BothWriteMerged.main(java.lang.String[])", left, right));
		 * /
			//methodsWithSrcOrSink.put("Subscriber rx.internal.operators.OperatorOnBackpressureDrop.call(Subscriber)", anomModMethods);
			//joana.run(false, false, methodsWithSrcOrSink);
			//joana.run(true, true, Integer.parseInt(args[1]));
			joana.run();
			//joana.run(false, true, false, methodsWithSrcOrSink, args != null && args.length >= 2 ? Integer.parseInt(args[1]) : 0);
			//joana.run(true, true);
		 * 
		 */
	}
}
