package main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import util.FileUtils;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.ifc.sdg.util.JavaPackage;
import edu.kit.joana.ifc.sdg.util.JavaType;

public class EntryPoint {
	private String srcPath;
	private Map<String, ModifiedMethod> modMethods;
	public EntryPoint(String srcPath, Map<String, ModifiedMethod> modMethods)
	{
		this.srcPath = srcPath;
		this.modMethods = modMethods;
	}
	public List<String> createEntryPoint() throws IOException, ClassNotFoundException
	{		
		String newClassPath = srcPath + "/JoanaEntryPoint.java";

		FileUtils.createFile(newClassPath);


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

	private List<String[]> createPackagesEntryPoints(
			Map<JavaPackage, List<String>> groupedMethods) throws IOException {
		List<String[]> entryPointsPath = new ArrayList<String[]>();
		for(JavaPackage java_package : groupedMethods.keySet())
		{
			String packageName = java_package.getName();
			if(!packageName.equals("(default package)")){
				String className = packageName.substring(0,1).toUpperCase() + packageName.substring(1).replace(".", "_") + "_EntryPoint.java";
				String classPath = srcPath + File.separator + packageName.replace(".", File.separator) + File.separator + className;
				FileUtils.createFile(classPath);
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
	
	private void createClass(String packageName, String newClassPath, List<String> imports, List<String> methodCalls) throws IOException { 
		if(packageName != null)
		{
			FileUtils.writeNewLine(newClassPath, "package "+packageName+";");
		}
		for(String import_str : imports)
		{
			FileUtils.writeNewLine(newClassPath, "import "+import_str+";");
		}
		String[] splittedClassPath = newClassPath.split("/");
		String className = splittedClassPath[splittedClassPath.length - 1].replace(".java", "");
		FileUtils.writeNewLine(newClassPath, "public class " +className+ " {");
		FileUtils.writeNewLine(newClassPath, "	public static void main(String[] args) {");
		FileUtils.writeNewLine(newClassPath, "		try {");

		for(String call : methodCalls)
		{
			FileUtils.writeNewLine(newClassPath, "			"+call);
		}

		FileUtils.writeNewLine(newClassPath, "		}");
		FileUtils.writeNewLine(newClassPath, "		catch(Exception e) {");
		FileUtils.writeNewLine(newClassPath, "			e.printStackTrace();");
		FileUtils.writeNewLine(newClassPath, "		}");	
		FileUtils.writeNewLine(newClassPath, "	}");
		FileUtils.writeNewLine(newClassPath, "}");
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
	
	public int compilePaths(List<String> compilePaths, String reportFileName, String classPath, String[] libPaths)
			throws IOException, FileNotFoundException {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		File entryPointBuild_report = new File(reportFileName);
		FileUtils.createFile(reportFileName);
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
	
	public static void main(String[] args) throws FileNotFoundException, IOException {
		String rev = "/Users/Roberto/Documents/UFPE/Msc/Projeto/projects/RxJava/revs/" + "rev_fd9b6-4350f";//"rev_29060-15e64";
		String projectPath = rev + "/git"; 
		String src = projectPath + "/src/main/java";
		EntryPoint entryPoint = new EntryPoint(src, null);
		System.out.println(entryPoint.compilePaths(new ArrayList<String>(
				Arrays.asList(new String[]{
						src + "/rx/functions/Func0Impl.java",
						src + "/JoanaEntryPoint.java",
				})), rev + "/reports/entryPointBuild_report.txt", projectPath + "/build/classes/main", null));
	}
	
}
