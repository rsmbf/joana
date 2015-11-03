package main;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
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

	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods)
	{	
		this(projectPath, modMethods, "/bin", "/src");
	}
	
	public JoanaInvocation(String projectPath, Map<String, ModifiedMethod> modMethods, String binPath, String srcPath)
	{
		this.classPath = projectPath + binPath;
		this.srcPath = projectPath + srcPath;
		this.modMethods = modMethods;
		parts_map = new HashMap<SDGProgramPart, Integer>();	
	}

	private static void printViolations(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart)
	{
		for(Object key : resultByProgramPart.keys())
		{
			System.out.print("Key: "+key);
			System.out.println(", Value: "+resultByProgramPart.get(key));
		}
	}

	private void printViolationsByLine(TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart)
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
			System.out.print("Key: "+msg);
			System.out.println(", Value: "+msgs.get(msg));
		}

	}

	private static void printSourcesAndSinks(Collection<IFCAnnotation> sources, Collection<IFCAnnotation> sinks) {
		System.out.println("Sources: "+sources.size());
		for(IFCAnnotation source : sources)
		{
			System.out.print("	SOURCE: "+ source.toString());
			System.out.print("	- PROGRAM PART: "+source.getProgramPart());
			System.out.print(" - CONTEXT: "+source.getContext());
			System.out.println(" - TYPE: "+source.getType());
		}
		System.out.println("Sinks: "+sinks.size());
		for(IFCAnnotation sink : sinks)
		{
			System.out.print("	SINK: "+sink.toString());
			System.out.print("	- PROGRAM PART: "+sink.getProgramPart());
			System.out.print(" - CONTEXT: "+sink.getContext());
			System.out.println(" - TYPE: "+sink.getType());			
		}
	}

	private void addSourcesAndSinks(String methodEvaluated) throws IOException {		

		Collection<SDGClass> classes = program.getClasses();
		Iterator<SDGClass> classesIt = classes.iterator();
		boolean methodFound = false;
		JavaMethodSignature methodSignature = modMethods.get(methodEvaluated).getMethodSignature();
		JavaType declaringClassType = methodSignature.getDeclaringType();
		//System.out.println("Searched method: "+methodEvaluated);
		while(!methodFound && classesIt.hasNext())
		{
			SDGClass SdgClass = classesIt.next();
			//System.out.println(SdgClass.getTypeName().toHRString());
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
			System.out.println("NO VIOLATION FOUND!");
		}		
	}

	private void printAllMethodsViolationsByLine(Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results) {
		System.out.println("LINE violations");
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
			Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results) {		
		int violations = 0;
		TObjectIntMap<IViolation<SDGProgramPart>> resultsByPart;
		List<TObjectIntMap<IViolation<SDGProgramPart>>> methodResults;
		System.out.println("VIOLATIONS");
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
		System.out.println("TOTAL VIOLATIONS: "+violations);

	}

	private Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> runAnalysisPerMethod()
			throws IOException {
		Map<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>> results = new HashMap<String, List<TObjectIntMap<IViolation<SDGProgramPart>>>>();
		for(String method : modMethods.keySet())
		{
			ModifiedMethod modMethod = modMethods.get(method);
			if(modMethod.getLeftContribs().size() > 0 && modMethod.getRightContribs().size() > 0)
			{
				addSourcesAndSinks(method);

				printSourcesAndSinks(ana.getSources(), ana.getSinks());
				System.out.println("FIRST ANALYSIS: "+method);
				/** run the analysis */
				Collection<? extends IViolation<SecurityNode>> result = ana.doIFC();		
				List<TObjectIntMap<IViolation<SDGProgramPart>>> methodResults = new ArrayList<TObjectIntMap<IViolation<SDGProgramPart>>>();
				TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);			

				/** do something with result */
				Collection<IFCAnnotation> sinks = ana.getSinks();
				Collection<IFCAnnotation> sources = ana.getSources();
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
				System.out.println("SECOND ANALYSIS: "+method);
				result = ana.doIFC();
				TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart2 = ana.groupByPPPart(result);	
				if(!resultByProgramPart.isEmpty() || !resultByProgramPart2.isEmpty()){
					methodResults.add(resultByProgramPart);
					methodResults.add(resultByProgramPart2);
					results.put(method, methodResults);
				}
				ana.clearAllAnnotations();
			}

		}
		return results;
	}

	private void writeNewLine(BufferedWriter bw, String line) throws IOException
	{
		bw.write(line + "\n");
		System.out.println(line);
	}

	private void write(BufferedWriter bw, String line) throws IOException
	{
		bw.write(line);
		System.out.print(line);
	}

	public Set<String> getImports()
	{		
		Set<String> imports = new HashSet<String>();
		for(String method : modMethods.keySet())
		{
			JavaMethodSignature signature = JavaMethodSignature.fromString("void "+method);
			//System.out.println(signature.getDeclaringType());
			imports.add(signature.getDeclaringType().toHRString());	
		}
		return imports;
	}

	private void createEntryPoint() throws IOException, ClassNotFoundException
	{		
		String newClassPath = srcPath + "/JoanaEntryPoint.java";

		BufferedWriter bw = createFile(newClassPath);

		Set<String> imports = getImports();


		for(String import_str : imports)
		{
			writeNewLine(bw, "import "+import_str+";");
		}
		
		writeNewLine(bw, "public class JoanaEntryPoint {");
		writeNewLine(bw, "	public static void main(String[] args) {");
		writeNewLine(bw, "		try {");
		for(String method : modMethods.keySet())
		{
			callMethod(bw, method);
		}
		
		writeNewLine(bw, "		}");
		writeNewLine(bw, "		catch(Exception e) {");
		writeNewLine(bw, "			e.printStackTrace();");
		writeNewLine(bw, "		}");	
		writeNewLine(bw, "	}");
		writeNewLine(bw, "}");
		bw.close();	

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		int result = compiler.run(null, null, null, new String[] {"-sourcepath", srcPath, "-d", classPath, newClassPath});
	}

	private BufferedWriter createFile(String newClassPath) throws IOException {
		File file = new File(newClassPath);
		if(file.exists())
		{
			file.delete();
		}
		if (!file.exists()) {
			file.createNewFile();
		}
		FileWriter fw = new FileWriter(newClassPath);
		BufferedWriter bw = new BufferedWriter(fw);
		return bw;
	}

	private void callMethod(BufferedWriter bw, String method)
			throws IOException {
		ModifiedMethod modMethod = modMethods.get(method);
		JavaMethodSignature methodSign = modMethod.getMethodSignature();
		write(bw, "			new "+methodSign.getDeclaringType().toHRStringShort() + "(");
		List<String> constArgs = modMethod.getDefaultConstructorArgs();
		if(constArgs.size() > 0)
		{
			String argsStr = "";
			for(String constructorArg : constArgs )
			{
				argsStr += getTypeDefaultValue(constructorArg) + " , ";
			}
			argsStr = argsStr.substring(0,argsStr.length() - 3);
			write(bw, argsStr);
		}
		write(bw, ")");
		if(!methodSign.getMethodName().equals("<init>"))
		{
			write(bw, "."+methodSign.getMethodName() +"(");
			String argsStr = "";				
			if(methodSign.getArgumentTypes().size() > 1 || 
					(methodSign.getArgumentTypes().size() == 1 && !methodSign.getArgumentTypes().get(0).toHRString().equals("")))
			{
				for(JavaType argType : methodSign.getArgumentTypes())
				{
					argsStr += getTypeDefaultValue(argType.toHRStringShort().split(" ")[0]) +" , ";
				}
				argsStr = argsStr.substring(0,argsStr.length() - 3);
				write(bw, argsStr);
			}
			writeNewLine(bw,");");
		}else{
			writeNewLine(bw, ";");
		}
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

	private SDGConfig setConfig() throws IOException, ClassNotFoundException {
		createEntryPoint();
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
		 
		
		/*
		contribs = new ArrayList<List<Integer>>();
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		contribs.add(left);
		contribs.add(right);
		left.add(6);
		right.add(9);
		methods.put("cin.ufpe.br2.Teste5.m()", new ModifiedMethod("cin.ufpe.br2.Teste5.m()", new ArrayList<String>(),contribs));
		
		contribs = new ArrayList<List<Integer>>();
		right = new ArrayList<Integer>();
		left = new ArrayList<Integer>();
		contribs.add(left);
		contribs.add(right);
		left.add(60);
		right.add(62);
		right.add(64);
		methods.put("Test2.main(java.lang.String[])", new ModifiedMethod("Test2.main(java.lang.String[])", new ArrayList<String>(),contribs));
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
