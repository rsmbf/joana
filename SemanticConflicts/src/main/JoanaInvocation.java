package main;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
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
			write(reportFilePath,"Key: "+key);
			writeNewLine(reportFilePath,", Value: "+resultByProgramPart.get(key));
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
			write(reportFilePath, "Key: "+msg);			
			writeNewLine(reportFilePath, ", Value: "+msgs.get(msg));
		}

	}

	private void printSourcesAndSinks(Collection<IFCAnnotation> sources, Collection<IFCAnnotation> sinks) throws IOException {
		writeNewLine(reportFilePath, "Sources: "+sources.size());
		for(IFCAnnotation source : sources)
		{
			write(reportFilePath,"	SOURCE: "+ source.toString());
			write(reportFilePath,"	- PROGRAM PART: "+source.getProgramPart());
			write(reportFilePath," - CONTEXT: "+source.getContext());
			writeNewLine(reportFilePath," - TYPE: "+source.getType());
		}
		writeNewLine(reportFilePath,"Sinks: "+sinks.size());
		for(IFCAnnotation sink : sinks)
		{
			write(reportFilePath,"	SINK: "+sink.toString());
			write(reportFilePath,"	- PROGRAM PART: "+sink.getProgramPart());
			write(reportFilePath," - CONTEXT: "+sink.getContext());
			writeNewLine(reportFilePath," - TYPE: "+sink.getType());			
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
							writeNewLine(reportFilePath, "LINE "+line_number+": "+instruction);
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
		List<String> paths = createEntryPoint();
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
					writeNewLine(reportFilePath, "NO VIOLATION FOUND!");
				}	
			}else{
				List<TObjectIntMap<IViolation<SDGProgramPart>>> results = runAnalysisForAllMethods();
				if(results.size() > 0)
				{
					writeNewLine(reportFilePath, "VIOLATIONS");
					writeNewLine(reportFilePath, "TOTAL VIOLATIONS: " + printAllViolations(results));
					writeNewLine(reportFilePath, "LINE violations");
					printAllViolationsByLine(results);
				}else{
					writeNewLine(reportFilePath, "NO VIOLATION FOUND!");
				}	
			}
			
		}else{
			writeNewLine(reportFilePath, "FAILED TO BUILD ENTRY POINT!");
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
		writeNewLine(reportFilePath, "LINE violations");
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
		writeNewLine(reportFilePath, "VIOLATIONS");
		for(String method : results.keySet())
		{
			violations += printAllViolations(results.get(method));
		}
		writeNewLine(reportFilePath, "TOTAL VIOLATIONS: "+violations);

	}
	
	private List<TObjectIntMap<IViolation<SDGProgramPart>>> runAnalysisForAllMethods()
			throws IOException {
		for(String method : modMethods.keySet())
		{
			ModifiedMethod modMethod = modMethods.get(method);
			writeNewLine(reportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
			if(modMethod.getLeftContribs().size() > 0 || modMethod.getRightContribs().size() > 0){
				addSourcesAndSinks(method);
				
			}else{
				writeNewLine(reportFilePath, "LEFT AND RIGHT CONTRIBUTIONS ARE EMPTY");
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
			writeNewLine(reportFilePath, "Method: "+modMethod.getMethodSignature().toHRString());
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
				writeNewLine(reportFilePath, "LEFT AND/OR RIGHT CONTRIBUTION IS EMPTY");
			}
			writeNewLine(reportFilePath, "");
		}
		return results;
	}

	private List<TObjectIntMap<IViolation<SDGProgramPart>>> runAnalysis(
			Collection<IFCAnnotation> sinks,Collection<IFCAnnotation> sources) throws IOException {
		List<TObjectIntMap<IViolation<SDGProgramPart>>> results = new ArrayList<TObjectIntMap<IViolation<SDGProgramPart>>>();
		if(sources.size() > 0 || sinks.size() > 0)
		{
			writeNewLine(reportFilePath,"FIRST ANALYSIS: ");
			/** run the analysis */
			Collection<? extends IViolation<SecurityNode>> result = ana.doIFC();		
			
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);			

			/** do something with result */

			invertSourceAndSinks(sinks, sources);
			printSourcesAndSinks(ana.getSources(), ana.getSinks());
			writeNewLine(reportFilePath, "SECOND ANALYSIS: ");

			result = ana.doIFC();
			TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart2 = ana.groupByPPPart(result);	
			if(!resultByProgramPart.isEmpty() || !resultByProgramPart2.isEmpty()){
				results.add(resultByProgramPart);
				results.add(resultByProgramPart2);
			}
		}else{
			writeNewLine(reportFilePath,"0 SOURCES AND SINKS");
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

	private void writeNewLine(String path, String line) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter(path, true));
		bw.write(line + "\n");
		bw.close();
		System.out.println(line);
	}

	private void write(String path, String line) throws IOException
	{
		BufferedWriter bw = new BufferedWriter(new FileWriter(path, true));
		bw.write(line);
		bw.close();
		System.out.print(line);
	}

	public Map<JavaPackage, List<String>> groupMethodsByPackage()
	{		
		Map<JavaPackage, List<String>> groupedMethods = new HashMap<JavaPackage, List<String>>();
		for(String method : modMethods.keySet())
		{
			JavaMethodSignature signature = modMethods.get(method).getMethodSignature();
			JavaPackage type_package = signature.getDeclaringType().getPackage();
			List<String> pack_methods = groupedMethods.get(type_package);
			if(pack_methods == null)
			{
				pack_methods = new ArrayList<String>();
			}
			pack_methods.add(method);
			groupedMethods.put(type_package, pack_methods);
		}
		return groupedMethods;
	}

	private List<String> createEntryPoint() throws IOException, ClassNotFoundException
	{		
		String newClassPath = srcPath + "/JoanaEntryPoint.java";

		createFile(newClassPath);


		Map<JavaPackage, List<String>> groupedMethods = groupMethodsByPackage();
		List<String[]> entryPointsResults = createPackagesEntryPoints(groupedMethods);
		List<String> imports = new ArrayList<String>();
		List<String> methods = new ArrayList<String>();
		List<String> compilePaths = new ArrayList<String>();
		for(String[] entryPointResult : entryPointsResults)
		{
			String packageName = entryPointResult[0];
			String className = entryPointResult[1].replace(".java", "");
			imports.add(packageName + "." + className.replace(".java", ""));
			methods.add(className + ".main(null);");
			compilePaths.add(entryPointResult[2]);
		}
		compilePaths.add(newClassPath);
		List<String> methodsDefaultPack = groupedMethods.get(new JavaPackage("(default package)"));
		if(methodsDefaultPack != null)
		{
			//methods.addAll(methodsDefaultPack);
			for(String method : methodsDefaultPack)
			{
				methods.add(callMethod(method));
			}
		}


		createClass(null, newClassPath, imports, methods);

		return compilePaths;
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

	private void createClass(String packageName, String newClassPath, List<String> imports, List<String> methodCalls) throws IOException { 
		if(packageName != null)
		{
			writeNewLine(newClassPath, "package "+packageName+";");
		}
		for(String import_str : imports)
		{
			writeNewLine(newClassPath, "import "+import_str+";");
		}
		String[] splittedClassPath = newClassPath.split("/");
		String className = splittedClassPath[splittedClassPath.length - 1].replace(".java", "");
		writeNewLine(newClassPath, "public class " +className+ " {");
		writeNewLine(newClassPath, "	public static void main(String[] args) {");
		writeNewLine(newClassPath, "		try {");

		for(String call : methodCalls)
		{
			writeNewLine(newClassPath, "			"+call);
		}

		writeNewLine(newClassPath, "		}");
		writeNewLine(newClassPath, "		catch(Exception e) {");
		writeNewLine(newClassPath, "			e.printStackTrace();");
		writeNewLine(newClassPath, "		}");	
		writeNewLine(newClassPath, "	}");
		writeNewLine(newClassPath, "}");
	}

	private List<String[]> createPackagesEntryPoints(
			Map<JavaPackage, List<String>> groupedMethods) throws IOException {
		List<String[]> entryPointsPath = new ArrayList<String[]>();
		for(JavaPackage java_package : groupedMethods.keySet())
		{
			String packageName = java_package.getName();
			if(!packageName.equals("(default package)")){
				String className = packageName.substring(0,1).toUpperCase() + packageName.substring(1).replace(".", "_") + "_EntryPoint.java";
				String classPath = srcPath + File.separator + packageName.replace(".", File.separator) + File.separator + className;
				createFile(classPath);
				Set<String> imports = new HashSet<String>();
				List<String> packageMethods = groupedMethods.get(java_package);
				List<String> methodsCalls = new ArrayList<String>();
				for(String method : packageMethods)
				{
					for(String import_str : modMethods.get(method).getImportsList())
					{
						imports.add(import_str);
					}
					methodsCalls.add(callMethod(method));
				}
				List<String> importsList = new ArrayList<String>();
				importsList.addAll(imports);
				createClass(packageName, classPath, importsList, methodsCalls);
				entryPointsPath.add(new String[] {packageName, className, classPath});
			}			
		}
		return entryPointsPath;
	}


	private void createFile(String newClassPath) throws IOException {
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

	private String callMethod(String method) {
		ModifiedMethod modMethod = modMethods.get(method);
		String call = "";
		JavaMethodSignature methodSign = modMethod.getMethodSignature();
		String declaringType = methodSign.getDeclaringType().toHRStringShort();
		call += "new "+ declaringType +"(";
		if(!methodSign.getMethodName().equals(declaringType))
		{			
			List<String> constArgs = modMethod.getDefaultConstructorArgs();
			if(constArgs.size() > 0)
			{
				String argsStr = "";
				for(String constructorArg : constArgs )
				{
					argsStr += getTypeDefaultValue(constructorArg) + " , ";
				}
				argsStr = argsStr.substring(0,argsStr.length() - 3);
				call += argsStr;//write(path, argsStr);
			}
			call += ")."+methodSign.getMethodName() +"(";//write(path, ")."+methodSign.getMethodName() +"(");

		}
		String argsStr = "";				
		if(methodSign.getArgumentTypes().size() > 1 || 
				(methodSign.getArgumentTypes().size() == 1 && !methodSign.getArgumentTypes().get(0).toHRString().equals("")))
		{
			for(JavaType argType : methodSign.getArgumentTypes())
			{
				argsStr += getTypeDefaultValue(argType.toHRStringShort().split(" ")[0]) +" , ";
			}
			argsStr = argsStr.substring(0,argsStr.length() - 3);
			call += argsStr;//write(path, argsStr);
		}
		call += ");";//writeNewLine(path,");");
		return call;
	}

	private String getTypeDefaultValue(String type)
	{
		String value = "("+ type + ") null";
		if(type.equals("byte") || type.equals("short") || type.equals("int") 
				|| type.equals("long") || type.equals("flot") || type.equals("double")){
			value = "0";
		}else if(type.equals("char"))
		{
			value = "'a'";
		}else if(type.equals("boolean"))
		{
			value = "false";
		}
		return value;
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
		String projectPath = rev + "/git"; 
		String src = "/src/java";//"/src/main/java";
		String fullSrc = projectPath + src;
		String reportsPath = rev + "/reports";
		String bin = "/dist/classes";//"/build/classes/main";
		JoanaInvocation joana = new JoanaInvocation(projectPath, methods, bin, src, "/lib/*:/dist/*", reportsPath);
		
		
		/*
		joana.compilePaths(new ArrayList<String>(
				Arrays.asList(new String[]{fullSrc + "/rx/internal/operators/Anon_Subscriber.java",
						fullSrc + "/rx/internal/operators/Anon_Producer.java",
						fullSrc + "/rx/internal/operators/OperatorOnBackPressureDrop.java"
				})), "anon_comp_report.txt");

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		right.add(13);
		methods.put("rx.internal.operators.Anon_Producer.request(long)", new ModifiedMethod("rx.internal.operators.Anon_Producer.request(long)", new ArrayList<String>(Arrays.asList(new String[]{"AtomicLong"})), left, right));
		
		
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(37);
		left.add(38);
		left.add(39);
		left.add(40);
		left.add(41);
		methods.put("rx.internal.operators.Anon_Subscriber.onNext(Object)", new ModifiedMethod("rx.internal.operators.Anon_Subscriber.onNext(Object)", new ArrayList<String>(Arrays.asList(new String[]{"Subscriber","AtomicLong", "Action1"})), left, right));
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
		methods.put("rx.internal.operators.OperatorMulticast.connect(rx.functions.Action1)", new ModifiedMethod("rx.internal.operators.OperatorMulticast.connect(rx.functions.Action1)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Observable","rx.functions.Func0"})), left, right));
		
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(12);
		left.add(13);
		left.add(14);
		left.add(15);
		methods.put("rx.Anon_Subscriber.onNext(java.lang.Object)", new ModifiedMethod("rx.Anon_Subscriber.onNext(java.lang.Object)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right));
		
		left = new ArrayList<Integer>();
		left.add(17);
		left.add(18);
		left.add(19);
		left.add(20);
		methods.put("rx.Anon_Subscriber.onError(Throwable)", new ModifiedMethod("rx.Anon_Subscriber.onError(Throwable)", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right));
		
		left = new ArrayList<Integer>();
		left.add(22);
		left.add(23);
		left.add(24);
		left.add(25);
		methods.put("rx.Anon_Subscriber.onCompleted()", new ModifiedMethod("rx.Anon_Subscriber.onCompleted()", new ArrayList<String>(Arrays.asList(new String[]{"rx.Subscriber"})), left, right));
		*/
		
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(820);
		left.add(827);
		right.add(731);
		methods.put("voldemort.server.VoldemortConfig.VoldemortConfig(Props)", new ModifiedMethod("voldemort.server.VoldemortConfig.VoldemortConfig(Props)",new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"})),left, right, new ArrayList<String>(Arrays.asList(new String[] {"voldemort.utils.Props"}))));
		
		joana.run();
		//joana.run(false);
	}
	
}
