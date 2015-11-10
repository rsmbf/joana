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

public class JoanaInvocation {
	private Map<SDGProgramPart, Integer> parts_map;
	private SDGProgram program;
	private IFCAnalysis ana;
	private Map<String, ModifiedMethod> modMethods;
	private String classPath;
	private String srcPath;
	private String reportFilePath;

	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods)
	{	
		this(projectPath, modMethods, "/bin", "/src", System.getProperty("user.dir")+File.separator+"reports");
	}
	
	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods, String binPath, String srcPath, String reportFilePath)
	{
		this.classPath = projectPath + binPath;
		this.srcPath = projectPath + srcPath;
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
					String meth_signature = method.getSignature().toHRString();
					String mod_sign = meth_signature.split(" ",2)[1];
					methodFound = methodEvaluated.equals(mod_sign);
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
							System.out.println("LINE "+line_number+": "+instruction);
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

	public void run() throws ClassNotFoundException, IOException, ClassHierarchyException, UnsoundGraphException, CancelException
	{
		createFile(reportFilePath);
		List<String> paths = createEntryPoint();
		if(compileEntryPoints(paths) == 0)
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
			Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results = runAnalysisPerMethod();
			if(results.size() > 0)
			{
				printAllMethodsViolations(results);
				printAllMethodsViolationsByLine(results);
			}else{
				writeNewLine(reportFilePath, "NO VIOLATION FOUND!");
			}	
		}else{
			writeNewLine(reportFilePath, "FAILED TO BUILD ENTRY POINT!");
			new File(reportFilePath).delete();
		}
			
	}

	private void printAllMethodsViolationsByLine(Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results) throws IOException {
		//System.out.println("LINE violations");
		writeNewLine(reportFilePath, "LINE violations");
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		List<TObjectIntMap<IViolation<SDGProgramPart>>> methodResults;
		for(String method : results.keySet())
		{
			methodResults = results.get(method);

			for(int i = 0; i < 2; i++)
			{
				resultsByPart = methodResults.get(i);
				if(!resultsByPart.isEmpty()){
					printViolationsByLine(resultsByPart);
				}				
			}
		}
	}

	private void printAllMethodsViolations(
			Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results) throws IOException {		
		
		int violations = 0;
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		List<TObjectIntMap<IViolation<SDGProgramPart>>> methodResults;
		writeNewLine(reportFilePath, "VIOLATIONS");
		for(String method : results.keySet())
		{
			methodResults = results.get(method);
			for(int i = 0; i < 2; i++)
			{
				resultsByPart = methodResults.get(i);	
				if(!resultsByPart.isEmpty()){
					printViolations(resultsByPart);
					violations += resultsByPart.size();
				}
			}
		}
		writeNewLine(reportFilePath, "TOTAL VIOLATIONS: "+violations);

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
				if(sources.size() > 0 && sinks.size() > 0)
				{
					writeNewLine(reportFilePath,"FIRST ANALYSIS: "+method);
					/** run the analysis */
					Collection<? extends IViolation<SecurityNode>> result = ana.doIFC();		
					List<TObjectIntMap<IViolation<SDGProgramPart>>> methodResults = new ArrayList<TObjectIntMap<IViolation<SDGProgramPart>>>();
					TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);			

					/** do something with result */
					
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
					printSourcesAndSinks(ana.getSources(), ana.getSinks());
					writeNewLine(reportFilePath, "SECOND ANALYSIS: "+method);
					
					result = ana.doIFC();
					TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart2 = ana.groupByPPPart(result);	
					if(!resultByProgramPart.isEmpty() || !resultByProgramPart2.isEmpty()){
						methodResults.add(resultByProgramPart);
						methodResults.add(resultByProgramPart2);
						results.put(method, methodResults);
					}
				}else{
					writeNewLine(reportFilePath,"0 SOURCES AND/OR SINKS");
				}
				ana.clearAllAnnotations();
			}else{
				writeNewLine(reportFilePath, "LEFT AND/OR RIGHT CONTRIBUTION IS EMPTY");
			}
			writeNewLine(reportFilePath, "");
		}
		return results;
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

	public Map<JavaPackage, List<String>> groupMethodCallsByPackage()
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
			pack_methods.add(callMethod(method));
			groupedMethods.put(type_package, pack_methods);
		}
		return groupedMethods;
	}

	private List<String> createEntryPoint() throws IOException, ClassNotFoundException
	{		
		String newClassPath = srcPath + "/JoanaEntryPoint.java";

		createFile(newClassPath);

		
		Map<JavaPackage, List<String>> groupedMethods = groupMethodCallsByPackage();
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
			methods.addAll(methodsDefaultPack);
		}
		

		createClass(null, newClassPath, imports, methods);

		return compilePaths;
	}

	private int compileEntryPoints(List<String> compilePaths)
			throws IOException, FileNotFoundException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		String parent = new File(reportFilePath).getParent();
		File entryPointBuild_report = new File(parent+File.separator+"entryPointBuild_report.txt");
		entryPointBuild_report.createNewFile();
		OutputStream err = new FileOutputStream(entryPointBuild_report);
		List<String> compArgs = new ArrayList<String>(Arrays.asList(new String[] {"-sourcepath", srcPath, "-d", classPath}));
		compArgs.addAll(compilePaths);
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
				createClass(packageName, classPath, new ArrayList<String>(), groupedMethods.get(java_package));
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
		call += "new "+methodSign.getDeclaringType().toHRStringShort() + "(";
		if(!methodSign.getMethodName().equals("<init>"))
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
		String value = "null";
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
		
		left.add(51);
		right.add(53);
		right.add(55);		
		methods.put("cin.ufpe.br.Teste3.main(java.lang.String[])",new ModifiedMethod("cin.ufpe.br.Teste3.main(java.lang.String[])", new ArrayList<String>(), left, right));

		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		left.add(10);
		left.add(12);
		right.add(14);		
		methods.put("cin.ufpe.br.Teste3.<init>()", new ModifiedMethod("cin.ufpe.br.Teste3.<init>()", new ArrayList<String>(), left, right));
				
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

		methods.put("cin.ufpe.br.Teste4.<init>(int, char, cin.ufpe.br.Teste2)", new ModifiedMethod("cin.ufpe.br.Teste4.<init>(int, char, cin.ufpe.br.Teste2)", new ArrayList<String>(), left, right));
		
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
		methods.put("Test2.main(java.lang.String[])", new ModifiedMethod("Test2.main(java.lang.String[])", new ArrayList<String>(),left, right));

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
		
		String projectPath = "/Users/Roberto/Documents/UFPE/Msc/Projeto/joana/joana/example/joana.example.tiny-special-tests";	
		JoanaInvocation joana = new JoanaInvocation(projectPath, methods);

		/*
		left.add(186);
		right.add(193);
		methods.put("rx.plugins.RxJavaPlugins.getSchedulersHook()", new ModifiedMethod("rx.plugins.RxJavaPlugins.getSchedulersHook()", new ArrayList<String>(), left, right ));
		JoanaInvocation joana = new JoanaInvocation("/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava", methods, "/build/classes/main", "/src/main/java");
		*/
		joana.run();
	}

}
