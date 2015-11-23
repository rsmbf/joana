package main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import edu.kit.joana.ifc.sdg.util.JavaPackage;
import edu.kit.joana.ifc.sdg.util.JavaType;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import gnu.trove.map.TObjectIntMap;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import util.FileUtils;

public class JoanaInvocation {
	private Map<SDGProgramPart, Integer> parts_map;
	private SDGProgram program;
	private IFCAnalysis ana;
	private Map<String, ModifiedMethod> modMethods;
	private String classPath;
	private String srcPath;
	private String[] libPaths;
	private String reportFilePath;

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
		this.reportFilePath = reportFilePath + File.separator+"joana_report.txt";		
		parts_map = new HashMap<SDGProgramPart, Integer>();	
	}

	private void printViolations(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart) throws IOException
	{
		for(Object key : resultByProgramPart.keys())
		{
			FileUtils.write(reportFilePath,"Key: "+key);
			FileUtils.writeNewLine(reportFilePath,", Value: "+resultByProgramPart.get(key));
		}
	}

	private void printViolationsByLine(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart) throws IOException
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

	private void printSourcesAndSinks(Collection<IFCAnnotation> sources, Collection<IFCAnnotation> sinks) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "Sources: "+sources.size());
		for(IFCAnnotation source : sources)
		{
			FileUtils.write(reportFilePath,"	SOURCE: "+ source.toString());
			FileUtils.write(reportFilePath,"	- PROGRAM PART: "+source.getProgramPart());
			FileUtils.write(reportFilePath," - CONTEXT: "+source.getContext());
			FileUtils.writeNewLine(reportFilePath," - TYPE: "+source.getType());
		}
		FileUtils.writeNewLine(reportFilePath,"Sinks: "+sinks.size());
		for(IFCAnnotation sink : sinks)
		{
			FileUtils.write(reportFilePath,"	SINK: "+sink.toString());
			FileUtils.write(reportFilePath,"	- PROGRAM PART: "+sink.getProgramPart());
			FileUtils.write(reportFilePath," - CONTEXT: "+sink.getContext());
			FileUtils.writeNewLine(reportFilePath," - TYPE: "+sink.getType());			
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
							FileUtils.writeNewLine(reportFilePath, "LINE "+line_number+": "+instruction);
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
		run(true);
	}
	
	public void run(boolean methodLevelAnalysis) throws ClassNotFoundException, IOException, ClassHierarchyException, UnsoundGraphException, CancelException
	{
		createFile(reportFilePath);
		List<String> paths = new EntryPoint(srcPath, modMethods).createEntryPoint();
		if(compilePaths(paths, "entryPointBuild_report.txt") == 0)
		{
			String parent = new File(reportFilePath).getParent();
			File entryPointBuild= new File(parent+File.separator+"entryPointBuild_report.txt");
			if(entryPointBuild.length() == 0)
			{
				entryPointBuild.delete();
			}
			SDGConfig config = setConfig();

			/** build the PDG */
			program = SDGProgram.createSDGProgram(config, System.out, new NullProgressMonitor());

			/** optional: save PDG to disk */
			SDGSerializer.toPDGFormat(program.getSDG(), new FileOutputStream("yourSDGFile.pdg"));

			ana = new IFCAnalysis(program);
			/** annotate sources and sinks */
			// for example: fields
			//ana.addSourceAnnotation(program.getPart("foo.bar.MyClass.secretField"), BuiltinLattices.STD_SECLEVEL_HIGH);
			//ana.addSinkAnnotation(program.getPart("foo.bar.MyClass.publicField"), BuiltinLattices.STD_SECLEVEL_LOW);
			if(methodLevelAnalysis)
			{
				Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results = runAnalysisPerMethod();
				if(results.size() > 0)
				{
					printAllMethodsViolations(results);
					printAllMethodsViolationsByLine(results);
				}else{
					FileUtils.writeNewLine(reportFilePath, "NO VIOLATION FOUND!");
				}	
			}else{
				List<TObjectIntMap<IViolation<SDGProgramPart>>> results = runAnalysisForAllMethods();
				if(results.size() > 0)
				{
					FileUtils.writeNewLine(reportFilePath, "VIOLATIONS");
					FileUtils.writeNewLine(reportFilePath, "TOTAL VIOLATIONS: " + printAllViolations(results));
					FileUtils.writeNewLine(reportFilePath, "LINE violations");
					printAllViolationsByLine(results);
				}else{
					FileUtils.writeNewLine(reportFilePath, "NO VIOLATION FOUND!");
				}	
			}
			
		}else{
			FileUtils.writeNewLine(reportFilePath, "FAILED TO BUILD ENTRY POINT!");
			new File(reportFilePath).delete();
		}

	}
	
	private void printAllViolationsByLine(List<TObjectIntMap<IViolation<SDGProgramPart>>> results) throws IOException
	{
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		for(int i = 0; i < results.size(); i++)
		{
			resultsByPart = results.get(i);
			if(!resultsByPart.isEmpty()){
				printViolationsByLine(resultsByPart);
			}				
		}
	}
	
	private void printAllMethodsViolationsByLine(Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results) throws IOException {
		FileUtils.writeNewLine(reportFilePath, "LINE violations");
		for(String method : results.keySet())
		{
			printAllViolationsByLine(results.get(method));
		}
	}
	
	private int printAllViolations(List<TObjectIntMap<IViolation<SDGProgramPart>>> results)
			throws IOException {
		int violations = 0;
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		for(int i = 0; i < results.size(); i++)
		{
			resultsByPart = results.get(i);	
			if(!resultsByPart.isEmpty()){
				printViolations(resultsByPart);
				violations += resultsByPart.size();
			}
		}
		return violations;
	}

	private void printAllMethodsViolations(
			Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results) throws IOException {		

		int violations = 0;
		FileUtils.writeNewLine(reportFilePath, "VIOLATIONS");
		for(String method : results.keySet())
		{
			violations += printAllViolations(results.get(method));
		}
		FileUtils.writeNewLine(reportFilePath, "TOTAL VIOLATIONS: "+violations);

	}
	
	private List<TObjectIntMap<IViolation<SDGProgramPart>>> runAnalysisForAllMethods()
			throws IOException {
		for(String method : modMethods.keySet())
		{
			ModifiedMethod modMethod = modMethods.get(method);
			FileUtils.writeNewLine(reportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0){
				addSourcesAndSinks(method);
				
			}else{
				FileUtils.writeNewLine(reportFilePath, "LEFT AND RIGHT CONTRIBUTIONS ARE EMPTY");
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
			FileUtils.writeNewLine(reportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
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
				FileUtils.writeNewLine(reportFilePath, "LEFT AND/OR RIGHT CONTRIBUTION IS EMPTY");
			}
			FileUtils.writeNewLine(reportFilePath, "");
		}
		return results;
	}

	private List<TObjectIntMap<IViolation<SDGProgramPart>>> runAnalysis(
			Collection<IFCAnnotation> sinks,Collection<IFCAnnotation> sources) throws IOException {
		List<TObjectIntMap<IViolation<SDGProgramPart>>> results = new ArrayList<TObjectIntMap<IViolation<SDGProgramPart>>>();
		if(sources.size() > 0 || sinks.size() > 0)
		{
			FileUtils.writeNewLine(reportFilePath,"FIRST ANALYSIS: ");
			/** run the analysis */
			Collection<? extends IViolation<SecurityNode>> result = ana.doIFC();		
			
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);			

			/** do something with result */

			invertSourceAndSinks(sinks, sources);
			printSourcesAndSinks(ana.getSources(), ana.getSinks());
			FileUtils.writeNewLine(reportFilePath, "SECOND ANALYSIS: ");

			result = ana.doIFC();
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart2 = ana.groupByPPPart(result);	
			if(!resultByProgramPart.isEmpty() || !resultByProgramPart2.isEmpty()){
				results.add(resultByProgramPart);
				results.add(resultByProgramPart2);
			}
		}else{
			FileUtils.writeNewLine(reportFilePath,"0 SOURCES AND SINKS");
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

	

	

	private int compilePaths(List<String> compilePaths, String reportFileName)
			throws IOException, FileNotFoundException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		String parent = new File(reportFilePath).getParent();
		File entryPointBuild_report = new File(parent+File.separator+reportFileName);
		entryPointBuild_report.createNewFile();
		OutputStream err = new FileOutputStream(entryPointBuild_report);
		List<String> compArgs = new ArrayList<String>(Arrays.asList(new String[] {"-sourcepath", srcPath, "-d", classPath}));
		if(libPaths != null)
		{
			compArgs.add("-cp");
			compArgs.add(FileUtils.getAllJarFiles(libPaths));
		}
		compArgs.addAll(compilePaths);
		/*
		for(String compArg : compArgs)
		{
			System.out.println(compArg);
		}
		*/
		return compiler.run(null, null, err, compArgs.toArray(new String[compArgs.size()]));
	}

	public static void createFile(String newClassPath) throws IOException {
		File file = new File(newClassPath);
		if(file.exists())
		{
			file.delete();
		}
		File parent = file.getParentFile();
		if(!parent.exists()){
			parent.mkdirs();
		}
		if (!file.exists()) {
			file.createNewFile();
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

		/** precision of the used points-to analysis - INSTANCE_BASED is a good value for simple examples */
		config.setPointsToPrecision(PointsToPrecision.TYPE_BASED);

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

		//String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava/revs/rev_fd9b6-4350f";
		//String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava/revs/rev_29060-15e64";
		
		String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/voldemort/revs/rev_df73c_dc509/rev_df73c-dc509";
		//String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/voldemort/revs/rev_24c82_64c90/rev_24c82-64c90";
		String projectPath = rev + "/git"; 
		String src = "/src/java";//"/src/main/java";
		String fullSrc = projectPath + src;
		String reportsPath = rev + "/reports";
		String bin = "/dist/classes";//"/build/classes/main";
		JoanaInvocation joana = new JoanaInvocation(projectPath, methods, bin, src, /*null*/"/lib/*:/dist/*", reportsPath);
		
		
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
				
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(820);
		left.add(827);
		right.add(731);
		methods.put("voldemort.server.VoldemortConfig.VoldemortConfig(Props)", new ModifiedMethod("voldemort.server.VoldemortConfig.VoldemortConfig(Props)",new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"})),left, right, new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"}))));	
		/*
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(145);
		left.add(146);
		left.add(147);
		right.add(141);
		methods.put("voldemort.VoldemortClientShell.VoldemortClientShell(ClientConfig, String, BufferedReader, PrintStream, PrintStream)", new ModifiedMethod("voldemort.VoldemortClientShell.VoldemortClientShell(ClientConfig, String, BufferedReader, PrintStream, PrintStream)",new ArrayList<String>(Arrays.asList(new String[] {})),left, right, new ArrayList<String>(Arrays.asList(new String[] {"voldemort.client.ClientConfig", "java.io.BufferedReader", "java.io.PrintStream"}))));
		*/
		
		joana.run();
		//joana.run(false);
	}
	
}
