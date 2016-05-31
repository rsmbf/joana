package main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.annotations.IFCAnnotation;
import edu.kit.joana.api.lattice.BuiltinLattices;
import edu.kit.joana.api.sdg.SDGAttribute;
import edu.kit.joana.api.sdg.SDGClass;
import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGInstruction;
import edu.kit.joana.api.sdg.SDGMethod;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.mhpoptimization.MHPType;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.ifc.sdg.util.JavaType;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import gnu.trove.map.TObjectIntMap;
import util.FileUtils;
import util.ViolationsPrinter;

public class JoanaInvocation {
	private Map<SDGProgramPart, Integer> parts_map;
	private SDGProgram program;
	private IFCAnalysis ana;
	private Map<String, ModifiedMethod> modMethods;
	private String classPath;
	private String srcPath;
	private String[] libPaths;
	private Map<PointsToPrecision, String> reportFilePaths[];
	private String currentReportFilePath;
	private List<SDGInstruction> leftInstructions, rightInstructions, otherInstructions;

	private static final PointsToPrecision[] precisions = new PointsToPrecision[] {
		PointsToPrecision.TYPE_BASED, PointsToPrecision.INSTANCE_BASED, PointsToPrecision.OBJECT_SENSITIVE,
		PointsToPrecision.N1_OBJECT_SENSITIVE, PointsToPrecision.UNLIMITED_OBJECT_SENSITIVE, 
		PointsToPrecision.N1_CALL_STACK, PointsToPrecision.N2_CALL_STACK, PointsToPrecision.N3_CALL_STACK };

	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods)
	{	
		this(projectPath, modMethods, "/bin", "/src", null, System.getProperty("user.dir")+File.separator+"reports");
	}

	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods, String binPath, String srcPath, String libPaths, String reportFilePath)
	{
		this.classPath = projectPath + binPath;
		this.srcPath = projectPath + srcPath;
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
		reportFilePaths = new Map[2];
		String[] exceps = new String[]{"_excep", "_noExcep"};
		for(int j = 0; j < reportFilePaths.length; j++){
			reportFilePaths[j] = new HashMap<PointsToPrecision, String>();
			for(int i = 0; i < precisions.length; i++)
			{
				reportFilePaths[j].put(precisions[i], reportFilePath + File.separator + precisions[i].toString()+ exceps[j] +".txt" );
			}	
		}

		currentReportFilePath = reportFilePaths[0].get(precisions[0]);	
		parts_map = new HashMap<SDGProgramPart, Integer>();	
		leftInstructions = new ArrayList<SDGInstruction>();
		rightInstructions = new ArrayList<SDGInstruction>();
		otherInstructions = new ArrayList<SDGInstruction>();
	}



	private void printSourcesAndSinks(Collection<IFCAnnotation> sources, Collection<IFCAnnotation> sinks) throws IOException {
		FileUtils.writeNewLine(currentReportFilePath, "Sources: "+sources.size());
		for(IFCAnnotation source : sources)
		{
			FileUtils.writeNewLine(currentReportFilePath,"	SOURCE: "+ source.toString());
		}
		FileUtils.writeNewLine(currentReportFilePath,"Sinks: "+sinks.size());
		for(IFCAnnotation sink : sinks)
		{
			FileUtils.writeNewLine(currentReportFilePath,"	SINK: "+sink.toString());		
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
					}else{
						argsMatch = evaluatedType.equals(currentType);
					}

					i++;
				}
				match = argsMatch;
			}
		}
		return match;
	}

	private void addSourcesAndSinks(String methodEvaluated, Map<String, ModifiedMethod> methodsWithSrcOrSink ) throws IOException {		

		Collection<SDGClass> classes = program.getClasses();
		//System.out.println(classes);
		Iterator<SDGClass> classesIt = classes.iterator();
		boolean methodFound = false;
		JavaMethodSignature methodSignature = methodsWithSrcOrSink.get(methodEvaluated).getMethodSignature();
		JavaType declaringClassType = methodSignature.getDeclaringType();
		//System.out.println("Searched method: "+methodEvaluated);
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
						for(SDGInstruction instruction : instructions ){
							int line_number = meth.getLineNumber(instruction.getBytecodeIndex());
							FileUtils.writeNewLine(currentReportFilePath, "    LINE "+line_number+": "+instruction);
							if(left_cont.contains(line_number))							
							{
								//System.out.println("Adding source...");
								ana.addSourceAnnotation(instruction, BuiltinLattices.STD_SECLEVEL_HIGH);
								leftInstructions.add(instruction);
							}else if(right_cont.contains(line_number))
							{
								//System.out.println("Adding sink...");
								ana.addSinkAnnotation(instruction, BuiltinLattices.STD_SECLEVEL_LOW);
								rightInstructions.add(instruction);
							}else{
								otherInstructions.add(instruction);
							}
							parts_map.put(instruction, line_number);
						}
					}
				}
			}

		}


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
			for(int exceptionsInt = 0; exceptionsInt < reportFilePaths.length; exceptionsInt++)
			{				
				boolean ignoreExceptions = exceptionsInt == 1;
				SDGConfig config = setConfig(ignoreExceptions);
				configs.put("ignoreExceptions", ignoreExceptions + "");
				runForEachPrecision(configs, methodsWithSrcOrSink, allPrecisions,
						violationPathes, initialPrecision, exceptionsInt, config);
			}
		}else{
			boolean ignoreExceptions = configs.get("ignoreExceptions").equals("true");
			SDGConfig config = setConfig(ignoreExceptions);
			int ignoreExceptionsInt = ignoreExceptions ? 1 : 0;
			runForEachPrecision(configs, methodsWithSrcOrSink, allPrecisions,
					violationPathes, initialPrecision, ignoreExceptionsInt, config);
		}
		

	}

	private void runForEachPrecision(Map<String, String> configs,
			Map<String, ModifiedMethod> methodsWithSrcOrSink,
			boolean allPrecisions, boolean violationPathes,
			int initialPrecision, int ignoreExceptionsInt, SDGConfig config)
			throws IOException, ClassHierarchyException, UnsoundGraphException,
			CancelException, FileNotFoundException {
		if(allPrecisions)
		{
			for(int i = initialPrecision; i < precisions.length; i++){
				currentReportFilePath = reportFilePaths[ignoreExceptionsInt].get(precisions[i]);
				FileUtils.createFile(currentReportFilePath);
				parts_map = new HashMap<SDGProgramPart, Integer>();	
				runForSpecificPrecision(configs, violationPathes, config, precisions[i], methodsWithSrcOrSink);
				System.out.println();
			}
		}else{
			currentReportFilePath = reportFilePaths[ignoreExceptionsInt].get(precisions[initialPrecision]);
			FileUtils.createFile(currentReportFilePath);
			runForSpecificPrecision(configs, violationPathes, config, precisions[initialPrecision], methodsWithSrcOrSink);
		}
	}

	private void printSdgInfo() throws IOException
	{
		FileUtils.writeNewLine(currentReportFilePath, "SDG INFO");
		for(SDGClass sdgClass : program.getClasses())
		{
			FileUtils.writeNewLine(currentReportFilePath, sdgClass.getTypeName().toHRString());
			Set<SDGAttribute> sdgAttributes = sdgClass.getAttributes();
			FileUtils.writeNewLine(currentReportFilePath, "    Attributes: "+sdgAttributes.size());
			for(SDGAttribute sdgAttribute : sdgAttributes)
			{
				FileUtils.writeNewLine(currentReportFilePath, "        "+sdgAttribute.getType().toString() + " "+sdgAttribute.getName());
			}

			Set<SDGMethod> sdgMethods = sdgClass.getMethods();
			FileUtils.writeNewLine(currentReportFilePath, "    Methods: "+sdgMethods.size());
			for(SDGMethod sdgMethod : sdgMethods){
				FileUtils.write(currentReportFilePath,  "        "+sdgMethod.getSignature().toHRString());
				FileUtils.writeNewLine(currentReportFilePath, " - Instructions: "+sdgMethod.getInstructions().size());
				IMethod method = sdgMethod.getMethod();
				for(SDGInstruction inst : sdgMethod.getInstructions())
				{
					FileUtils.writeNewLine(currentReportFilePath, "            LINE "+method.getLineNumber(inst.getBytecodeIndex())+": "+inst);
					//System.out.println("            LINE "+method.getLineNumber(inst.getBytecodeIndex())+": "+inst);
				}
			}
		}
	}

	private void runForSpecificPrecision(Map<String, String> configs, boolean violationPathes,
			SDGConfig config,
			PointsToPrecision precision, Map<String, ModifiedMethod> methodsWithSrcOrSink) throws ClassHierarchyException,IOException, UnsoundGraphException, CancelException,FileNotFoundException {
		boolean methodLevelAnalysis = configs.get("methodLevelAnalysis").equals("true");
		boolean ignoreExceptions = configs.get("ignoreExceptions").equals("true");
		/** precision of the used points-to analysis - INSTANCE_BASED is a good value for simple examples */
		config.setPointsToPrecision(precision);

		System.out.println("Creating SDG...");

		/** build the PDG */
		program = SDGProgram.createSDGProgram(config, new PrintStream(new FileOutputStream(currentReportFilePath)) , new NullProgressMonitor());
		FileUtils.writeNewLine(currentReportFilePath, "SDG: "+program.getSDG().vertexSet().size() + " nodes and "+program.getSDG().edgeSet().size()+" edges" );
		FileUtils.writeNewLine(currentReportFilePath, "");
		printSdgInfo();
		FileUtils.writeNewLine(currentReportFilePath, "");
		/** optional: save PDG to disk */
		String pdgFileName = new File(currentReportFilePath).getParent() + File.separator + precision.toString();
		String excep = "_excep";
		if(ignoreExceptions)
		{
			excep = "_noExcep";
		}
		pdgFileName += excep + ".pdg";
		SDGSerializer.toPDGFormat(program.getSDG(), new FileOutputStream(pdgFileName));

		ana = new IFCAnalysis(program);
		/** annotate sources and sinks */
		// for example: fields
		//ana.addSourceAnnotation(program.getPart("foo.bar.MyClass.secretField"), BuiltinLattices.STD_SECLEVEL_HIGH);
		//ana.addSinkAnnotation(program.getPart("foo.bar.MyClass.publicField"), BuiltinLattices.STD_SECLEVEL_LOW);
		FileUtils.writeNewLine(currentReportFilePath, "ANALYSIS");
		if(methodLevelAnalysis)
		{
			Map<String, Map<String, ViolationResult>> methodsWithViosByAnnotation = runAnalysisPerMethod(methodsWithSrcOrSink);
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
					Map<Integer,LineInterferencesPoints> interferencesByLine = BothAffect.getInterferencesByLine(parts_map, 
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
					ViolationsPrinter.printAllMethodsViolationsPaths(results, program.getSDG(), currentReportFilePath);
				}

				ViolationsPrinter.printAllMethodsViolations(results, currentReportFilePath);
				ViolationsPrinter.printAllMethodsViolationsByLine(results, program, parts_map, currentReportFilePath);
			}else{
				FileUtils.writeNewLine(currentReportFilePath, "NO FLOW FROM LEFT TO RIGHT OR RIGHT TO LEFT!");
				System.out.println();
				if(bothAffectResults.size() > 0)
				{
					ViolationsPrinter.printAllMethodsWithBothAffect(bothAffectResults,currentReportFilePath);
				}else{
					FileUtils.writeNewLine(currentReportFilePath, "NO FLOW FROM LEFT AND RIGHT TO A THIRD POINT!");
				}
			}

		}else{
			Map<String, ViolationResult> viosByAnnotation = runAnalysisForAllMethods(methodsWithSrcOrSink);
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
					FileUtils.writeNewLine(currentReportFilePath, "VIOLATIONS PATHS");
					ViolationsPrinter.printAllViolationsPaths(results, program.getSDG(), currentReportFilePath);
				}

				FileUtils.writeNewLine(currentReportFilePath, "VIOLATIONS");
				FileUtils.writeNewLine(currentReportFilePath, "TOTAL VIOLATIONS: " + ViolationsPrinter.printAllViolations(results, currentReportFilePath));
				FileUtils.writeNewLine(currentReportFilePath, "LINE violations");
				ViolationsPrinter.printAllViolationsByLine(results, program, parts_map, currentReportFilePath);
			}else{
				FileUtils.writeNewLine(currentReportFilePath, "NO FLOW FROM LEFT TO RIGHT OR RIGHT TO LEFT!");
			}	
		}
	}



	private Map<String, ViolationResult> runAnalysisForAllMethods(Map<String, ModifiedMethod> methodsWithSrcOrSink)
			throws IOException {
		for(String method : methodsWithSrcOrSink.keySet())
		{
			ModifiedMethod modMethod = methodsWithSrcOrSink.get(method);
			FileUtils.writeNewLine(currentReportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0 || modMethod.getAnomModMethods() != null){
				if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0){
					addSourcesAndSinks(method, methodsWithSrcOrSink);
				}
				if(modMethod.getAnomModMethods() != null)
				{
					Map<String, ModifiedMethod> anomMethods = modMethod.getAnomModMethods();
					for(String anomMethod : anomMethods.keySet())
					{
						ModifiedMethod anomModMethod = anomMethods.get(anomMethod);
						if(anomModMethod.getLeftContribs().size() > 0 || anomModMethod.getRightContribs().size() > 0 )
						{
							addSourcesAndSinks(anomMethod, anomMethods);
						}
					}
				}

			}else{
				FileUtils.writeNewLine(currentReportFilePath, "LEFT AND RIGHT CONTRIBUTIONS ARE EMPTY");
			}
		}
		Collection<IFCAnnotation> sinks = ana.getSinks();
		Collection<IFCAnnotation> sources = ana.getSources();	
		return runAnalysis(sinks, sources);
	}

	private Map<String, Map<String, ViolationResult>> runAnalysisPerMethod(Map<String, ModifiedMethod> methodsWithSrcOrSink)
			throws IOException {
		Map<String, Map<String, ViolationResult>> results = new HashMap<String, Map<String, ViolationResult>>();
		for(String method : methodsWithSrcOrSink.keySet())
		{
			ModifiedMethod modMethod = methodsWithSrcOrSink.get(method);
			FileUtils.writeNewLine(currentReportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if((modMethod.getLeftContribs().size() > 0 && modMethod.getRightContribs().size() > 0) || modMethod.getAnomModMethods() != null)
			{
				if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0 )
				{
					addSourcesAndSinks(method, methodsWithSrcOrSink);
				}
				if(modMethod.getAnomModMethods() != null)
				{
					Map<String, ModifiedMethod> anomMethods = modMethod.getAnomModMethods();
					for(String anomMethod : anomMethods.keySet())
					{
						ModifiedMethod anomModMethod = anomMethods.get(anomMethod);
						if(anomModMethod.getLeftContribs().size() > 0 || anomModMethod.getRightContribs().size() > 0 )
						{
							addSourcesAndSinks(anomMethod, anomMethods);
						}
					}
				}
				Collection<IFCAnnotation> sinks = ana.getSinks();
				Collection<IFCAnnotation> sources = ana.getSources();
				Map<String, ViolationResult> methodResults = runAnalysis(sinks, sources);
				if(methodResults.size() > 0)
				{
					results.put(method, methodResults);
				}
			}else{
				FileUtils.writeNewLine(currentReportFilePath, "LEFT AND/OR RIGHT CONTRIBUTION IS EMPTY");
			}
			FileUtils.writeNewLine(currentReportFilePath, "");
		}
		return results;
	}

	private Map<String, ViolationResult> runAnalysis(
			Collection<IFCAnnotation> sinks,Collection<IFCAnnotation> sources) throws IOException {		
		Map<String, ViolationResult> resultsByAnnotation = new HashMap<String, ViolationResult>();
		resultsByAnnotation.put("LEFT->RIGHT", null);
		resultsByAnnotation.put("RIGHT->LEFT", null);
		resultsByAnnotation.put("LEFT->OTHERS", null);
		resultsByAnnotation.put("RIGHT->OTHERS", null);
		if(sources.size() > 0 && sinks.size() > 0)
		{
			FileUtils.writeNewLine(currentReportFilePath,"1.1.a analysis");
			printSourcesAndSinks(ana.getSources(), ana.getSinks());
			/** run the analysis */
			Collection<? extends IViolation<SecurityNode>> result_1_1_a = ana.doIFC();		

			//TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);			

			/** do something with result */

			FileUtils.writeNewLine(currentReportFilePath, "1.1.b analysis");
			invertSourceAndSinks(sinks, sources);
			printSourcesAndSinks(ana.getSources(), ana.getSinks());
			Collection<? extends IViolation<SecurityNode>> result_1_1_b = ana.doIFC();
			//TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart2 = ana.groupByPPPart(result);	

			if(result_1_1_a.isEmpty() && result_1_1_b.isEmpty())
			{
				FileUtils.writeNewLine(currentReportFilePath, "1.2.a analysis");
				addSourcesAndSinks_1_2(leftInstructions);
				printSourcesAndSinks(ana.getSources(), ana.getSinks());
				Collection<? extends IViolation<SecurityNode>> result_1_2_a = ana.doIFC();

				FileUtils.writeNewLine(currentReportFilePath, "1.2.b analysis");
				addSourcesAndSinks_1_2(rightInstructions);
				printSourcesAndSinks(ana.getSources(), ana.getSinks());
				Collection<? extends IViolation<SecurityNode>> result_1_2_b = ana.doIFC();
				if(!result_1_2_a.isEmpty())
				{
					resultsByAnnotation.put("LEFT->OTHERS", new ViolationResult(result_1_2_a, ana.groupByPPPart(result_1_2_a)));
				}
				if(!result_1_2_b.isEmpty())
				{
					resultsByAnnotation.put("RIGHT->OTHERS", new ViolationResult(result_1_2_b, ana.groupByPPPart(result_1_2_b)));
				}
			}else{
				if(!result_1_1_a.isEmpty()){
					//results_1_1.add(new ViolationResult(result_1_1_a, ana.groupByPPPart(result_1_1_a)));
					resultsByAnnotation.put("LEFT->RIGHT", new ViolationResult(result_1_1_a, ana.groupByPPPart(result_1_1_a)));
				}
				if(!result_1_1_b.isEmpty())
				{
					//results_1_1.add(new ViolationResult(result_1_1_b, ana.groupByPPPart(result_1_1_b)));
					resultsByAnnotation.put("RIGHT->LEFT", new ViolationResult(result_1_1_b, ana.groupByPPPart(result_1_1_b)));
				}
			}

		}else{
			FileUtils.writeNewLine(currentReportFilePath,"0 SOURCES AND/OR SINKS");
		}
		ana.clearAllAnnotations();
		return resultsByAnnotation;
	}

	private void addSourcesAndSinks_1_2(Collection<SDGInstruction> toMarkAsSource) {
		ana.clearAllAnnotations();
		for(SDGInstruction inst : toMarkAsSource)
		{
			ana.addSourceAnnotation(inst, BuiltinLattices.STD_SECLEVEL_HIGH);
		}
		for(SDGInstruction inst : otherInstructions)
		{
			ana.addSinkAnnotation(inst, BuiltinLattices.STD_SECLEVEL_LOW);
		}
	}

	private void invertSourceAndSinks(Collection<IFCAnnotation> sinks,
			Collection<IFCAnnotation> sources) {
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

	private SDGConfig setConfig(boolean ignoreExceptions) {
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
			if(methodSignature.getMethodName().equals(methodSignature.getDeclaringType().toHRStringShort())){
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
		SDGConfig config = new SDGConfig(classPath, null, Stubs.JRE_14);
		config.setEntryMethods(entryMethods);
		/** compute interference edges to model dependencies between threads (set to false if your program does not use threads) */
		config.setComputeInterferences(false);

		/** additional MHP analysis to prune interference edges (does not matter for programs without multiple threads) */
		config.setMhpType(MHPType.PRECISE);

		/** exception analysis is used to detect exceptional control-flow which cannot happen */
		config.setExceptionAnalysis(ignoreExceptions ? ExceptionAnalysis.IGNORE_ALL : ExceptionAnalysis.INTERPROC);
		config.setThirdPartyLibsPath(libPaths != null ? String.join(System.getProperty("path.separator"), libPaths) : null);

		return config;
	}

	public static void main(String[] args) throws ClassHierarchyException, IOException, UnsoundGraphException, CancelException, ClassNotFoundException {				
		Map<String, ModifiedMethod> methods = new HashMap<String, ModifiedMethod>();
		List<Integer> right = new ArrayList<Integer>();
		List<Integer> left = new ArrayList<Integer>();		

		String base_path = args[0]; //*/"/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/";
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
		JoanaInvocation joana = new JoanaInvocation(projectPath, methods, bin, src, null/*"/main/webapp/WEB-INF/lib/json-20100208.jar"*//*"/lib/*:/dist"*/, reportsPath);

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

		// */

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		//left.add(18);
		//right.add(23);
		//right.add(29);
		//methods.put("void TestFlow.<init>(Props)", new ModifiedMethod("void TestFlow.<init>(Props)", new ArrayList<String>(), left, right, new ArrayList<String>()));

		// */
		///*



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
		// */
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
		methods.put("void ExceptionExample.n()", new ModifiedMethod("void ExceptionExample.n()", new ArrayList<String>(), left, right, new ArrayList<String>()));*/

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
		 */
		//methodsWithSrcOrSink.put("Subscriber rx.internal.operators.OperatorOnBackpressureDrop.call(Subscriber)", anomModMethods);
		//joana.run(false, false, methodsWithSrcOrSink);
		//joana.run(true, true, Integer.parseInt(args[1]));
		joana.run();
		//joana.run(false, true, false, methodsWithSrcOrSink, args != null && args.length >= 2 ? Integer.parseInt(args[1]) : 0);
		//joana.run(true, true);
	}

}
