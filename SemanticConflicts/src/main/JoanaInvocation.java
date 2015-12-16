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
	private Map<PointsToPrecision, String> reportFilePaths;
	private String currentReportFilePath;
	
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
				this.libPaths[i] = projectPath + this.libPaths[i];
			}
		}
		this.modMethods = modMethods;		
		reportFilePaths = new HashMap<PointsToPrecision, String>();
		for(int i = 0; i < precisions.length; i++)
		{
			reportFilePaths.put(precisions[i], reportFilePath + File.separator + "joana_"+precisions[i].toString() +"_report.txt" );
		}
		currentReportFilePath = reportFilePaths.get(precisions[0]);	
		parts_map = new HashMap<SDGProgramPart, Integer>();	
	}

	

	private void printSourcesAndSinks(Collection<IFCAnnotation> sources, Collection<IFCAnnotation> sinks) throws IOException {
		FileUtils.writeNewLine(currentReportFilePath, "Sources: "+sources.size());
		for(IFCAnnotation source : sources)
		{
			FileUtils.write(currentReportFilePath,"	SOURCE: "+ source.toString());
			FileUtils.write(currentReportFilePath,"	- PROGRAM PART: "+source.getProgramPart());
			FileUtils.write(currentReportFilePath," - CONTEXT: "+source.getContext());
			FileUtils.writeNewLine(currentReportFilePath," - TYPE: "+source.getType());
		}
		FileUtils.writeNewLine(currentReportFilePath,"Sinks: "+sinks.size());
		for(IFCAnnotation sink : sinks)
		{
			FileUtils.write(currentReportFilePath,"	SINK: "+sink.toString());
			FileUtils.write(currentReportFilePath,"	- PROGRAM PART: "+sink.getProgramPart());
			FileUtils.write(currentReportFilePath," - CONTEXT: "+sink.getContext());
			FileUtils.writeNewLine(currentReportFilePath," - TYPE: "+sink.getType());			
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

	private void addSourcesAndSinks(String methodEvaluated) throws IOException {		

		Collection<SDGClass> classes = program.getClasses();
		//System.out.println(classes);
		Iterator<SDGClass> classesIt = classes.iterator();
		boolean methodFound = false;
		JavaMethodSignature methodSignature = modMethods.get(methodEvaluated).getMethodSignature();
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
						ModifiedMethod modMethod = modMethods.get(methodEvaluated);
						List<Integer> left_cont = modMethod.getLeftContribs();
						//System.out.println(left_cont);
						List<Integer> right_cont = modMethod.getRightContribs();
						//System.out.println(right_cont);
						Collection<SDGInstruction> instructions = method.getInstructions();
						for(SDGInstruction instruction : instructions ){
							int line_number = meth.getLineNumber(instruction.getBytecodeIndex());
							FileUtils.writeNewLine(currentReportFilePath, "    LINE "+line_number+": "+instruction);
							if(left_cont.contains(line_number))							
							{
								//System.out.println("Adding source...");
								ana.addSourceAnnotation(instruction, BuiltinLattices.STD_SECLEVEL_HIGH);
								parts_map.put(instruction, line_number);
							}else if(right_cont.contains(line_number))
							{
								//System.out.println("Adding sink...");
								ana.addSinkAnnotation(instruction, BuiltinLattices.STD_SECLEVEL_LOW);
								parts_map.put(instruction, line_number);
							}
						}
					}
				}
			}

		}


	}
	
	public void run() throws ClassNotFoundException, ClassHierarchyException, IOException, UnsoundGraphException, CancelException
	{
		run(true, false);
	}
	
	public void run(boolean methodLevelAnalysis, boolean allPrecisions) throws ClassNotFoundException, IOException, ClassHierarchyException, UnsoundGraphException, CancelException
	{
		EntryPoint entryPoint = new EntryPoint(srcPath, modMethods);
		List<String> paths = entryPoint.createEntryPoint();
		String reportFolderPath = new File(currentReportFilePath).getParent();
		String entryPointBuildPath = reportFolderPath + File.separator + "entryPointBuild_report.txt";
		if(entryPoint.compilePaths(paths, entryPointBuildPath, classPath, libPaths) == 0)
		{			
			File entryPointBuild = new File(entryPointBuildPath);
			if(entryPointBuild.length() == 0)
			{
				entryPointBuild.delete();
			}
			SDGConfig config = setConfig();
			if(allPrecisions)
			{
				for(int i = 0; i < precisions.length; i++){
					currentReportFilePath = reportFilePaths.get(precisions[i]);
					FileUtils.createFile(currentReportFilePath);
					parts_map = new HashMap<SDGProgramPart, Integer>();	
					runForSpecificPrecision(methodLevelAnalysis, config, precisions[i]);
					System.out.println();
				}
			}else{
				runForSpecificPrecision(methodLevelAnalysis, config, precisions[0]);
			}
						
		}else{
			FileUtils.writeNewLine(currentReportFilePath, "FAILED TO BUILD ENTRY POINT!");
			new File(currentReportFilePath).delete();
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
			}
		}
	}

	private void runForSpecificPrecision(boolean methodLevelAnalysis,
			SDGConfig config,
			PointsToPrecision precision) throws ClassHierarchyException,IOException, UnsoundGraphException, CancelException,FileNotFoundException {
		/** precision of the used points-to analysis - INSTANCE_BASED is a good value for simple examples */
		config.setPointsToPrecision(precision);

		System.out.println("Creating SDG...");
		
		/** build the PDG */
		program = SDGProgram.createSDGProgram(config, new PrintStream(new FileOutputStream(currentReportFilePath)) , new NullProgressMonitor());

		FileUtils.printFileContent(currentReportFilePath);
		FileUtils.writeNewLine(currentReportFilePath, "");
		printSdgInfo();
		FileUtils.writeNewLine(currentReportFilePath, "");
		/** optional: save PDG to disk */
		SDGSerializer.toPDGFormat(program.getSDG(), new FileOutputStream(new File(currentReportFilePath).getParent() + File.separator + precision.toString() + ".pdg"));

		ana = new IFCAnalysis(program);
		/** annotate sources and sinks */
		// for example: fields
		//ana.addSourceAnnotation(program.getPart("foo.bar.MyClass.secretField"), BuiltinLattices.STD_SECLEVEL_HIGH);
		//ana.addSinkAnnotation(program.getPart("foo.bar.MyClass.publicField"), BuiltinLattices.STD_SECLEVEL_LOW);
		FileUtils.writeNewLine(currentReportFilePath, "ANALYSIS");
		if(methodLevelAnalysis)
		{
			Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results = runAnalysisPerMethod();
			if(results.size() > 0)
			{
				ViolationsPrinter.printAllMethodsViolations(results, currentReportFilePath);
				ViolationsPrinter.printAllMethodsViolationsByLine(results, program, parts_map, currentReportFilePath);
			}else{
				FileUtils.writeNewLine(currentReportFilePath, "NO VIOLATION FOUND!");
			}	
		}else{
			List<TObjectIntMap<IViolation<SDGProgramPart>>> results = runAnalysisForAllMethods();
			if(results.size() > 0)
			{
				FileUtils.writeNewLine(currentReportFilePath, "VIOLATIONS");
				FileUtils.writeNewLine(currentReportFilePath, "TOTAL VIOLATIONS: " + ViolationsPrinter.printAllViolations(results, currentReportFilePath));
				FileUtils.writeNewLine(currentReportFilePath, "LINE violations");
				ViolationsPrinter.printAllViolationsByLine(results, program, parts_map, currentReportFilePath);
			}else{
				FileUtils.writeNewLine(currentReportFilePath, "NO VIOLATION FOUND!");
			}	
		}
	}
	
	
	
	private List<TObjectIntMap<IViolation<SDGProgramPart>>> runAnalysisForAllMethods()
			throws IOException {
		for(String method : modMethods.keySet())
		{
			ModifiedMethod modMethod = modMethods.get(method);
			FileUtils.writeNewLine(currentReportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0){
				addSourcesAndSinks(method);
				
			}else{
				FileUtils.writeNewLine(currentReportFilePath, "LEFT AND RIGHT CONTRIBUTIONS ARE EMPTY");
			}
		}
		Collection<IFCAnnotation> sinks = ana.getSinks();
		Collection<IFCAnnotation> sources = ana.getSources();
		printSourcesAndSinks(sources, sinks);		
		return runAnalysis(sinks, sources);
	}
	
	private Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> runAnalysisPerMethod()
			throws IOException {
		Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results = new HashMap<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>>();
		for(String method : modMethods.keySet())
		{
			ModifiedMethod modMethod = modMethods.get(method);
			FileUtils.writeNewLine(currentReportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if(modMethod.getLeftContribs().size() > 0 && modMethod.getRightContribs().size() > 0)
			{
				addSourcesAndSinks(method);
				Collection<IFCAnnotation> sinks = ana.getSinks();
				Collection<IFCAnnotation> sources = ana.getSources();
				printSourcesAndSinks(sources, sinks);
				List<TObjectIntMap<IViolation<SDGProgramPart>>> methodResults = runAnalysis(sinks, sources);
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

	private List<TObjectIntMap<IViolation<SDGProgramPart>>> runAnalysis(
			Collection<IFCAnnotation> sinks,Collection<IFCAnnotation> sources) throws IOException {
		List<TObjectIntMap<IViolation<SDGProgramPart>>> results = new ArrayList<TObjectIntMap<IViolation<SDGProgramPart>>>();
		if(sources.size() > 0 || sinks.size() > 0)
		{
			FileUtils.writeNewLine(currentReportFilePath,"FIRST ANALYSIS: ");
			/** run the analysis */
			Collection<? extends IViolation<SecurityNode>> result = ana.doIFC();		
			
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);			

			/** do something with result */

			invertSourceAndSinks(sinks, sources);
			printSourcesAndSinks(ana.getSources(), ana.getSinks());
			FileUtils.writeNewLine(currentReportFilePath, "SECOND ANALYSIS: ");

			result = ana.doIFC();
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart2 = ana.groupByPPPart(result);	
			if(!resultByProgramPart.isEmpty() || !resultByProgramPart2.isEmpty()){
				results.add(resultByProgramPart);
				results.add(resultByProgramPart2);
			}
		}else{
			FileUtils.writeNewLine(currentReportFilePath,"0 SOURCES AND SINKS");
		}
		ana.clearAllAnnotations();
		return results;
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

	private SDGConfig setConfig() {
		/** the class path is either a directory or a jar containing all the classes of the program which you want to analyze */
		//String classPath = projectPath + "/bin";//"/data1/mmohr/git/CVJMultithreading/bin";

		///Users/Roberto/Documents/UFPE/Msc/Projeto/joana_rcaa/joana/example/joana.example.tiny-special-tests/bin
		//COMPILAR PROJETO (PELO MENOS A CLASSE ADICIONADA)
		//javac -sourcepath src src/JoanaEntryPoint.java -d bin		
		/** the entry method is the main method which starts the program you want to analyze */
		JavaMethodSignature entryMethod = JavaMethodSignature.mainMethodOfClass("JoanaEntryPoint");

		/** For multi-threaded programs, it is currently neccessary to use the jdk 1.4 stubs */
		SDGConfig config = new SDGConfig(classPath, entryMethod.toBCString(), Stubs.JRE_14);

		/** compute interference edges to model dependencies between threads (set to false if your program does not use threads) */
		config.setComputeInterferences(false);

		/** additional MHP analysis to prune interference edges (does not matter for programs without multiple threads) */
		config.setMhpType(MHPType.PRECISE);

		/** exception analysis is used to detect exceptional control-flow which cannot happen */
		config.setExceptionAnalysis(ExceptionAnalysis.INTERPROC);
		return config;
	}

	public static void main(String[] args) throws ClassHierarchyException, IOException, UnsoundGraphException, CancelException, ClassNotFoundException {				
		Map<String, ModifiedMethod> methods = new HashMap<String, ModifiedMethod>();
		List<Integer> right = new ArrayList<Integer>();
		List<Integer> left = new ArrayList<Integer>();		

		/*
		left.add(51);
		right.add(53);
		right.add(55);		
		methods.put("cin.ufpe.br.Teste3.main(String[])",new ModifiedMethod("cin.ufpe.br.Teste3.main(String[])", new ArrayList<String>(), left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(10);
		left.add(12);
		right.add(14);		
		methods.put("cin.ufpe.br.Teste3.Teste3()", new ModifiedMethod("cin.ufpe.br.Teste3.Teste3()", new ArrayList<String>(), left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(52);
		left.add(54);
		right.add(53);		
		methods.put("cin.ufpe.br.Teste2.g(int, boolean, java.lang.String, int[])", new ModifiedMethod("cin.ufpe.br.Teste2.g(int, boolean, java.lang.String, int[])", new ArrayList<String>(),  left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(11);
		right.add(13);
		List<String> argsList = new ArrayList<String>();
		argsList.add("int");
		argsList.add("char");
		argsList.add("Teste2");
		methods.put("cin.ufpe.br.Teste4.m()", new ModifiedMethod("cin.ufpe.br.Teste4.m()", argsList, left, right));

		methods.put("cin.ufpe.br.Teste4.Teste4(int, char, Teste2)", new ModifiedMethod("cin.ufpe.br.Teste4.Teste4(int, char, Teste2)", new ArrayList<String>(), left, right));

		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(27);
		right.add(28);
		methods.put("cin.ufpe.br.Teste4.m2()", new ModifiedMethod("cin.ufpe.br.Teste4.m2()", argsList,  left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(33);
		right.add(36);
		methods.put("cin.ufpe.br.Teste4.m3()", new ModifiedMethod("cin.ufpe.br.Teste4.m3()", argsList,  left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(19);
		right.add(22);
		methods.put("cin.ufpe.br.Teste4.n(int)", new ModifiedMethod("cin.ufpe.br.Teste4.n(int)", argsList, left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(52);
		right.add(54);
		methods.put("cin.ufpe.br.Teste4.n2(int)", new ModifiedMethod("cin.ufpe.br.Teste4.n2(int)", argsList, left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(59);
		right.add(60);
		methods.put("cin.ufpe.br.Teste4.n3(int)", new ModifiedMethod("cin.ufpe.br.Teste4.n3(int)", argsList,left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(65);
		right.add(68);
		methods.put("cin.ufpe.br.Teste4.nm(int)", new ModifiedMethod("cin.ufpe.br.Teste4.nm(int)", argsList, left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(73);
		right.add(76);
		methods.put("cin.ufpe.br.Teste4.nm2(int)", new ModifiedMethod("cin.ufpe.br.Teste4.nm2(int)", argsList, left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(89);
		right.add(92);
		methods.put("cin.ufpe.br.Teste4.k()", new ModifiedMethod("cin.ufpe.br.Teste4.k()", argsList, left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(81);
		right.add(84);
		methods.put("cin.ufpe.br.Teste4.nm3(int)", new ModifiedMethod("cin.ufpe.br.Teste4.nm3(int)", argsList, left, right));
		right.remove(0);
		methods.put("cin.ufpe.br.Teste4.nm3(int)", new ModifiedMethod("cin.ufpe.br.Teste4.nm3(int)", argsList, left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(6);
		right.add(9);
		methods.put("cin.ufpe.br2.Teste5.m()", new ModifiedMethod("cin.ufpe.br2.Teste5.m()", new ArrayList<String>(),left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(60);
		right.add(62);
		right.add(64);
		methods.put("Test2.main(String[])", new ModifiedMethod("Test2.main(String[])", new ArrayList<String>(),left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(11);
		right.add(12);
		methods.put("cin.ufpe.br2.Teste6.m()", new ModifiedMethod("cin.ufpe.br2.Teste6.m()", new ArrayList<String>(),left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(17);
		right.add(18);
		methods.put("cin.ufpe.br2.Teste6.n()", new ModifiedMethod("cin.ufpe.br2.Teste6.n()", new ArrayList<String>(),left, right));
		 
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(17);
		right.add(18);
		methods.put("cin.ufpe.br2.Teste7.n()", new ModifiedMethod("cin.ufpe.br2.Teste7.n()", new ArrayList<String>(),left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(17);
		right.add(18);
		methods.put("cin.ufpe.br2.Teste8.n()", new ModifiedMethod("cin.ufpe.br2.Teste8.n()", new ArrayList<String>(),left, right));
		 */

		/*
		String projectPath = "/Users/Roberto/Documents/UFPE/Msc/Projeto/joana/joana/example/joana.example.tiny-special-tests";	
		JoanaInvocation joana = new JoanaInvocation(projectPath, methods);
		 */

		 /*
		left.add(186);
		right.add(193);
		methods.put("rx.plugins.RxJavaPlugins.getSchedulersHook()", new ModifiedMethod("rx.plugins.RxJavaPlugins.getSchedulersHook()", new ArrayList<String>(), left, right ));
		JoanaInvocation joana = new JoanaInvocation("/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava", methods, "/build/classes/main", "/src/main/java");
		 */
		String base_path = args[0]; //"/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/";
		//String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava/revs/rev_fd9b6-4350f";
		//String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava/revs/rev_29060-15e64";
		
		String rev = base_path;// + "voldemort/revs/rev_df73c_dc509/rev_df73c-dc509";
		//String rev = base_path + "voldemort/revs/rev_24c82_649c0/rev_24c82-64c90";
		//String rev = base_path + "voldemort/revs/rev_e0f18_d44ca/rev_e0f18-d44ca";

		String projectPath = rev;// + "/git"; 
		String src = "/src";//"/src/java";//"/src/main/java";
		//String fullSrc = projectPath + src;
		String reportsPath = rev + "/reports";
		String bin = "/bin";///dist/classes";//"/build/classes/main";
		JoanaInvocation joana = new JoanaInvocation(projectPath, methods, bin, src, null/*"/lib/*:/dist/*"*/, reportsPath);
		
		
		/*
		joana.compilePaths(new ArrayList<String>(
				Arrays.asList(new String[]{fullSrc + "/rx/internal/operators/Anon_Subscriber.java",
						fullSrc + "/rx/internal/operators/Anon_Producer.java",
						fullSrc + "/rx/internal/operators/OperatorOnBackPressureDrop.java"
				})), "anon_comp_report.txt");

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		right.add(13);
		methods.put("rx.internal.operators.Anon_Producer.request(long)", new ModifiedMethod("rx.internal.operators.Anon_Producer.request(long)", new ArrayList<String>(Arrays.asList(new String[]{"AtomicLong"})), left, right, new ArrayList<String>()));
		
		
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(37);
		left.add(38);
		left.add(39);
		left.add(40);
		left.add(41);
		methods.put("rx.internal.operators.Anon_Subscriber.onNext(Object)", new ModifiedMethod("rx.internal.operators.Anon_Subscriber.onNext(Object)", new ArrayList<String>(Arrays.asList(new String[]{"Subscriber","AtomicLong", "Action1"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"java.util.concurrent.atomic.AtomicLong","rx.Observable.Operator","rx.Producer","rx.Subscriber", "rx.functions.Action1"}))));
		*/
		/*
		joana.compilePaths(new ArrayList<String>(Arrays.asList(new String[] {
				fullSrc + "/rx/Anon_Subscriber.java",
				fullSrc + "/rx/Anon_Subscriber_Obs.java",
				fullSrc + "/rx/observers/Subscribers.java",
				fullSrc + "/rx/internal/operators/OperatorMulticast.java"
		})), "anon_comp_report.txt");
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		right.add(116);
		left.add(139);
		left.add(140);
		left.add(156);
		methods.put("rx.internal.operators.OperatorMulticast.connect(rx.functions.Action1)", new ModifiedMethod("rx.internal.operators.OperatorMulticast.connect(rx.functions.Action1)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Observable","rx.functions.Func0"})), left, right, new ArrayList<String>(Arrays.asList(new String[] {"rx.functions.Action1"}))));
		
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(12);
		left.add(13);
		left.add(14);
		left.add(15);
		methods.put("rx.Anon_Subscriber.onNext(java.lang.Object)", new ModifiedMethod("rx.Anon_Subscriber.onNext(java.lang.Object)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right, new ArrayList<String>()));
		
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
		methods.put("voldemort.server.VoldemortConfig.VoldemortConfig(Props)", new ModifiedMethod("voldemort.server.VoldemortConfig.VoldemortConfig(Props)",new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"})),left, right, new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"}))));	
		 */
		
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(145);
		left.add(146);
		left.add(147);
		right.add(141);
		methods.put("voldemort.VoldemortClientShell.VoldemortClientShell(ClientConfig, String, BufferedReader, PrintStream, PrintStream)", 
				new ModifiedMethod("voldemort.VoldemortClientShell.VoldemortClientShell(ClientConfig, String, BufferedReader, PrintStream, "
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
		methods.put("voldemort.VoldemortAdminTool.main(String[])", new ModifiedMethod("voldemort.VoldemortAdminTool.main(String[])",
				new ArrayList<String>(), left, right, new ArrayList<String>()));
		
		 
		// */
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(17);
		right.add(18);
		methods.put("TestFlow.test()", new ModifiedMethod("TestFlow.test()", new ArrayList<String>(), left, right, new ArrayList<String>()));
		
		joana.run(true, true);
		//joana.run(false);
	}
	
}
