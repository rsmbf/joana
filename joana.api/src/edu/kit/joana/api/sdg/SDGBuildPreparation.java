/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.api.sdg;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

import com.ibm.wala.cfg.exc.intra.MethodState;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.JarStreamModule;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.pruned.ApplicationLoaderPolicy;
import com.ibm.wala.ipa.callgraph.pruned.PruningPolicy;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.DefaultIRFactory;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.config.FileOfClasses;
import com.ibm.wala.util.config.SetOfClasses;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.StringStuff;

import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.util.JoanaConstants;
import edu.kit.joana.util.LogUtil;
import edu.kit.joana.util.io.IOFactory;
import edu.kit.joana.wala.core.CGConsumer;
import edu.kit.joana.wala.core.ExternalCallCheck;
import edu.kit.joana.wala.core.NullProgressMonitor;
import edu.kit.joana.wala.core.SDGBuilder;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.FieldPropagation;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import edu.kit.joana.wala.core.SDGBuilder.StaticInitializationTreatment;
import edu.kit.joana.wala.core.params.objgraph.SideEffectDetectorConfig;
import edu.kit.joana.wala.flowless.pointsto.AliasGraph.MayAliasGraph;
import edu.kit.joana.wala.flowless.spec.java.ast.MethodInfo;
import edu.kit.joana.wala.util.WriteGraphToDot;
import edu.kit.joana.wala.util.pointsto.ObjSensZeroXCFABuilder;

public final class SDGBuildPreparation {

	private SDGBuildPreparation() {
		throw new UnsupportedOperationException();
	}

	public final static String STD_EXCLUSION_REG_EXP =
			"sun\\/awt\\/.*\n"
					+ "sun\\/swing\\/.*\n"
					+ "com\\/sun\\/.*\n"
					+ "sun\\/.*\n"
					+ "apple\\/awt\\/.*\n"
					+ "com\\/apple\\/.*\n"
					+ "org\\/omg\\/.*\n";

	// these classes are modeled without specific fields
	public final static String[] IMMUTABLE_STUBS = {
		"Ljava/lang/String",
		"Ljava/lang/Integer",
		"Ljava/lang/Long",
		"Ljava/lang/Character",
		"Ljava/lang/Object",
		"Ljava/lang/Throwable",
		"Ljava/lang/Exception",
	};

	public final static String[] IMMUTABLE_NO_OUT = {
		"Ljava/lang/String",
		"Ljava/lang/Integer",
		"Ljava/lang/Long",
		"Ljava/lang/Character",
	};

	public final static String[] IGNORE_STATIC_FIELDS = {
		"Ljava/lang/Integer",
		"Ljava/lang/Object",
		"Ljava/lang/Long",
		"Ljava/lang/Character",
		"Ljava/lang/Throwable",
		"Ljava/lang/Exception",
	};

	public final static int DEFAULT_PRUNE_CG = 2;
	public final static int DO_NOT_PRUNE_CG = SDGBuilder.DO_NOT_PRUNE;

	public final static String STD_CLASS_PATH = "bin/";

	public final static ExceptionAnalysis DEFAULT_EXCEPTION_ANALYSIS = ExceptionAnalysis.INTRAPROC;

	public final static boolean DEFAULT_ACCESS_PATH = false;

	public static ClassHierarchy computeClassHierarchy(PrintStream out, Config cfg) throws IOException, ClassHierarchyException {
		AnalysisScope scope = setUpAnalysisScope(out, cfg);
		// Klassenhierarchie berechnen
		return ClassHierarchy.make(scope);
	}


	public static List<String> searchMainMethods(PrintStream out, Config cfg) throws IOException, ClassHierarchyException {
		final List<String> result = new LinkedList<String>();
		out.println("Searching for main methods in '" + cfg.classpath + "'...");
		ClassHierarchy cha = computeClassHierarchy(out, cfg);
		for (final IClass cls : cha) {
			if (!cls.isInterface() && !cls.isAbstract() && cls.getClassLoader().getName().equals(AnalysisScope.APPLICATION)) {
				for (final IMethod m : cls.getDeclaredMethods()) {
					if (m.isStatic() && "main([Ljava/lang/String;)V".equals(m.getSelector().toString())) {
						out.println("\tfound '" + m.getSignature() + "'");
						result.add(m.getSignature());
					}
				}
			}
		}

		out.println("done.");

		return result;
	}

	public static void run(PrintStream out, Config cfg) throws IOException, ClassHierarchyException, UnsoundGraphException, CancelException {
		final SDG sdg = compute(out, cfg);

		if (sdg != null) {
			out.print("Writing SDG to disk... ");
			final String fileName = cfg.outputDir + WriteGraphToDot.sanitizeFileName(sdg.getName()) + ".pdg";
			final File file = new File(fileName);
			out.print("(" + file.getAbsolutePath() + ") ");
			PrintWriter pw = new PrintWriter(IOFactory.createUTF8PrintStream(new FileOutputStream(file)));
			SDGSerializer.toPDGFormat(sdg, pw);
			out.println("done.");
		}
	}

	/**
	 * Search file in filesystem. If not found, try to load from classloader (e.g. from inside the jarfile).
	 */
	private static Module findJarModule(final PrintStream out, final String path) throws IOException {
		final File f = new File(path);
		if (f.exists()) {
			out.print("(from file " + path + ") ");
			return new JarFileModule(new JarFile(f));
		} else {
			final URL url = SDGBuildPreparation.class.getClassLoader().getResource(path);
			final URLConnection con = url.openConnection();
			final InputStream in = con.getInputStream();
			out.print("(from jar stream " + path + ") ");
			return new JarStreamModule(new JarInputStream(in));
		}
	}

	public static AnalysisScope setUpAnalysisScope(final PrintStream out, final Config cfg) throws IOException {
		// Fuegt die normale Java Bibliothek zum Scope hinzu

		// deactivates WALA synthetic methods if cfg.nativesXML != null
		com.ibm.wala.ipa.callgraph.impl.Util.setNativeSpec(cfg.nativesXML);

		AnalysisScope scope;
		// if use stubs
		if (cfg.stubs != null) {
			scope = AnalysisScope.createJavaAnalysisScope();
			final Module stubs = findJarModule(out, cfg.stubs);
			scope.addToScope(ClassLoaderReference.Primordial, stubs);

		} else {
			scope = AnalysisScopeReader.makePrimordialScope(null);
		}

		// Nimmt unnoetige Klassen raus

		SetOfClasses exclusions =
				new FileOfClasses(new ByteArrayInputStream(IOFactory.createUTF8Bytes(cfg.exclusions)));
		scope.setExclusions(exclusions);

		ClassLoaderReference loader = scope.getLoader(AnalysisScope.APPLICATION);
		AnalysisScopeReader.addClassPathToScope(cfg.classpath, scope, loader);
		if (cfg.thirdPartyLibPath != null) {
			ClassLoaderReference extLoader = scope.getLoader(AnalysisScope.EXTENSION);
			AnalysisScopeReader.addClassPathToScope(cfg.thirdPartyLibPath, scope, extLoader);
		}
		return scope;
	}

	public static SDG compute(PrintStream out, Config cfg) throws ClassHierarchyException, IOException, UnsoundGraphException, CancelException {
		return compute(out, cfg, NullProgressMonitor.INSTANCE);
	}


	public static SDG compute(PrintStream out, Config cfg, IProgressMonitor progress) throws IOException, ClassHierarchyException, UnsoundGraphException, CancelException {
		return compute(out, cfg, false, progress);
	}

	private static Pair<Long, SDGBuilder.SDGBuilderConfig> prepareBuild(PrintStream out, Config cfg, boolean computeInterference, IProgressMonitor progress) throws IOException, ClassHierarchyException {
		if (!checkOrCreateOutputDir(cfg.outputDir)) {
			out.println("Could not access/create diretory '" + cfg.outputDir +"'");
			System.out.println("Could not access/create diretory '" + cfg.outputDir +"'");
			return null;
		}
		final long startTime = System.currentTimeMillis();

		out.print("Setting up analysis scope... ");
		System.out.print("Setting up analysis scope... ");

		AnalysisScope scope = setUpAnalysisScope(out, cfg);

		out.println("done.");
		System.out.println("done.");

		out.print("Creating class hierarchy... ");
		System.out.print("Creating class hierarchy...");

		// Klassenhierarchie berechnen
		ClassHierarchy cha = ClassHierarchy.make(scope);

		out.println("(" + cha.getNumberOfClasses() + " classes) done.");
		System.out.println("(" + cha.getNumberOfClasses() + " classes) done.");
		/*Iterator it2 = cha.iterator();
		while(it2.hasNext())
		{
			System.out.println(it2.next());
		}*/
		if (cfg.extern != null) {
			cfg.extern.setClassHierarchy(cha);
		}
		IMethod m = null;
		List<IMethod> ms = null;
		if(cfg.entryMethods != null && cfg.entryMethods.size() > 0)
		{
			out.print("Setting up entrypoints... ");
			System.out.print("Setting up entrypoints... ");
			ms = new ArrayList<IMethod>();
			for(String entryMethod : cfg.entryMethods)
			{
				out.print("Setting up entrypoint " + entryMethod + "... ");
				System.out.print("Setting up entrypoint " + entryMethod + "... ");

				// Methode in der Klassenhierarchie suchen
				final MethodReference mr = StringStuff.makeMethodReference(Language.JAVA, entryMethod);
				
				IMethod meth = cha.resolveMethod(mr);
				if (meth == null) {
					System.out.println("MR: "+mr.getDeclaringClass());
					IClass iClass = cha.lookupClass(mr.getDeclaringClass());				
					if(iClass == null)
					{
						
						/*Iterator it = cha.iterator();
						while(it.hasNext())
						{
							System.out.println(it.next());
						}*/
						fail("could no resolve "+mr);
					}

					boolean methodFound = false;
					Iterator<IMethod> it = iClass.getDeclaredMethods().iterator();
					while(it.hasNext() && !methodFound)
					{
						IMethod currentMethod = it.next(); 
						System.out.println(currentMethod);
						int thisCorrectionIndex = 0;
						if(!currentMethod.isStatic())
						{
							thisCorrectionIndex++;
						}
						int noParamsCorrectionIndex = 0;
						boolean noParams = (currentMethod.getNumberOfParameters() == 0 + thisCorrectionIndex) && mr.getNumberOfParameters() == 1;
						if(noParams)
						{
							noParamsCorrectionIndex++;
						}
						if(currentMethod.getName().equals(mr.getName()) && 
								(currentMethod.getNumberOfParameters() - thisCorrectionIndex == mr.getNumberOfParameters() - noParamsCorrectionIndex)){
							int i = 0;
							boolean parametersMightMatch = true;
							if(!noParams)
							{
								while(i < mr.getNumberOfParameters() && parametersMightMatch)
								{
									TypeName mrType = mr.getParameterType(i).getName();
									TypeName currType = currentMethod.getParameterType(i + thisCorrectionIndex).getName();
									Atom mrTypePack = mrType.getPackage();
									Atom mrTypeClassName = mrType.getClassName();
									Atom currTypeClassName = currType.getClassName();
									Atom currTypePack = currType.getPackage();
									//String currTypePackStr = currType.getPackage().toString();
									
									boolean parPackagesMightMatch = mrTypePack == null || mrTypePack.equals(currTypePack) 
									    || mrTypePack.toString().contains(currTypePack.toString()) 
									    || Character.isUpperCase(mrTypePack.toString().charAt(0));
									parametersMightMatch = mrType.equals(currType) || 
											(parPackagesMightMatch && currTypeClassName.equals(mrTypeClassName))
											/*((mrTypePack == null && (currTypeClassName.equals(mrTypeClassName))) 
													/*|| (mrTypePack != null && currTypeClassName.equals(mrTypeClassName) )* /)*/;
									if(!parametersMightMatch && parPackagesMightMatch/*&& mrType.getPackage() != null*/)
									{
										String currTypeClassNameStr = currTypeClassName.toString();
										if(currTypeClassNameStr.contains("$"))
										{
											int lastIndex = currTypeClassNameStr.lastIndexOf("$");	
											String outterClass = currTypeClassNameStr.substring(0, lastIndex);
											String fullOutterClass = currTypePack.toString() + "/" + outterClass;
											parametersMightMatch = currTypeClassNameStr.substring(lastIndex + 1).equals(mrTypeClassName.toString()) && 
											    (mrTypePack == null || outterClass.equals(mrTypePack.toString()) || fullOutterClass.equals(mrTypePack.toString()));
										}
									}
									i++;
								}
							}

							TypeName mrReturnType = mr.getReturnType().getName();
							TypeName currReturnType = currentMethod.getReturnType().getName();
							Atom mrReturnTypePack = mrReturnType.getPackage();
							boolean retPacksMightMatch = mrReturnTypePack == null || mrReturnTypePack.equals(currReturnType.getPackage())
									 || mrReturnTypePack.toString().contains(currReturnType.toString()) 
				                      || Character.isUpperCase(mrReturnTypePack.toString().charAt(0));
							methodFound = parametersMightMatch && (mrReturnType.equals(currReturnType) || 
									(retPacksMightMatch && mrReturnType.getClassName().equals(currReturnType.getClassName())));
							if(!methodFound && parametersMightMatch && retPacksMightMatch/*mrReturnType.getPackage() != null*/)
							{
								String currRetTypeClassNameStr = currReturnType.getClassName().toString();
								if(currRetTypeClassNameStr.contains("$"))
								{
									int lastIndex = currRetTypeClassNameStr.lastIndexOf("$");			
									String outterReturn = currRetTypeClassNameStr.substring(0, lastIndex);
									String fullOutterReturn = currReturnType.getPackage().toString() + "/" + outterReturn;
									methodFound = currRetTypeClassNameStr.substring(lastIndex + 1).equals(mrReturnType.getClassName().toString().trim()) && 
											(mrReturnTypePack == null || outterReturn.equals(mrReturnTypePack.toString()) || fullOutterReturn.equals(mrReturnTypePack.toString()));
								}
							}
						}
						
						if(methodFound)
						{
							meth = currentMethod;
						}
					}

					if(!methodFound){
						fail("could not resolve " + mr);
					}
				}
				ms.add(meth);
			}
		}else if(cfg.entryMethod != null){
			out.print("Setting up entrypoint " + cfg.entryMethod + "... ");
			System.out.print("Setting up entrypoint " + cfg.entryMethod + "... ");

			// Methode in der Klassenhierarchie suchen
			final MethodReference mr = StringStuff.makeMethodReference(Language.JAVA, cfg.entryMethod);
			m = cha.resolveMethod(mr);
			if (m == null) {
				fail("could not resolve " + mr);
			}

		}
		out.println("done.");
		System.out.println("done.");

		AnalysisCache cache = new AnalysisCache(new DefaultIRFactory());

		out.print("Building system dependence graph... ");
		System.out.print("Building system dependence graph... ");

		ExternalCallCheck chk;
		if (cfg.extern == null) {
			chk = new ExternalCallCheck() {
				@Override
				public boolean isCallToModule(SSAInvokeInstruction invk) {
					return false;
				}

				@Override
				public void registerAliasContext(SSAInvokeInstruction invk, int callNodeId, MayAliasGraph context) {
				}

				@Override
				public void setClassHierarchy(IClassHierarchy cha) {
				}

				@Override
				public MethodInfo checkForModuleMethod(IMethod im) {
					return null;
				}

				@Override
				public boolean resolveReflection() {
					return false;
				}
			};
		} else {
			chk = cfg.extern;
		}

		final SDGBuilder.SDGBuilderConfig scfg = new SDGBuilder.SDGBuilderConfig();
		scfg.out = out;
		scfg.scope = scope;
		scfg.cache = cache;
		scfg.cha = cha;
		scfg.entry = m;
		scfg.entries = ms;
		scfg.ext = chk;
		scfg.immutableNoOut = IMMUTABLE_NO_OUT;
		scfg.immutableStubs = IMMUTABLE_STUBS;
		scfg.ignoreStaticFields = IGNORE_STATIC_FIELDS;
		scfg.exceptions = cfg.exceptions;
		scfg.defaultExceptionMethodState = cfg.defaultExceptionMethodState;
		scfg.accessPath = cfg.accessPath;
		scfg.sideEffects = cfg.sideEffects;
		scfg.prunecg = DEFAULT_PRUNE_CG;
		scfg.pruningPolicy = cfg.pruningPolicy;
		scfg.pts = cfg.pts;
		if (cfg.objSensFilter != null) {
			scfg.objSensFilter = cfg.objSensFilter;
		}
		scfg.staticInitializers = StaticInitializationTreatment.SIMPLE;
		scfg.fieldPropagation = cfg.fieldPropagation;
		scfg.debugManyGraphsDotOutput = cfg.debugManyGraphsDotOutput;
		scfg.computeInterference = computeInterference;
		scfg.computeAllocationSites = cfg.computeAllocationSites;
		scfg.cgConsumer = cfg.cgConsumer;
		scfg.additionalContextSelector = cfg.ctxSelector;
		return Pair.make(startTime, scfg);
	}

	private static long[] postpareBuild(long startTime, PrintStream out) {
		out.println("\ndone.");
		System.out.println("\ndone.");
		final long endTime = System.currentTimeMillis();
		final long time = (endTime - startTime);
		long memory = ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
		String info = "Time needed: " + time + "ms - Memory: "
				+ memory
				+ "M used.";
		out.println(info);
		System.out.println(info);
		return new long[]{time, memory};
	}

	public static SDG compute(PrintStream out, Config cfg, boolean computeInterference, IProgressMonitor progress) throws IOException, ClassHierarchyException, UnsoundGraphException, CancelException {
		Pair<Long, SDGBuilder.SDGBuilderConfig> p = prepareBuild(out, cfg, computeInterference, progress);
		long startTime = p.fst;
		SDGBuilder.SDGBuilderConfig scfg = p.snd;
		final SDG sdg = SDGBuilder.build(scfg, progress);
		postpareBuild(startTime, out);
		//		SDGVerifier.verify(sdg, false, true);

		return sdg;
	}
	private static long[] timeAndMemory;
	public static long[] getTimeAndMemory()
	{
	  return timeAndMemory;
	}
	public static Pair<SDG, SDGBuilder> computeAndKeepBuilder(PrintStream out, Config cfg, boolean computeInterference, IProgressMonitor progress) throws UnsoundGraphException, CancelException, IOException, ClassHierarchyException {
		Pair<Long, SDGBuilder.SDGBuilderConfig> p = prepareBuild(out, cfg, computeInterference, progress);
		long startTime = p.fst;
		SDGBuilder.SDGBuilderConfig scfg = p.snd;
		final Pair<SDG, SDGBuilder> ret = SDGBuilder.buildAndKeepBuilder(scfg, progress);
		timeAndMemory = postpareBuild(startTime, out);
		//		SDGVerifier.verify(sdg, false, true);

		return ret;
	}

	public static SDGBuilder createBuilder(PrintStream out, Config cfg, boolean computeInterference, IProgressMonitor progress) throws UnsoundGraphException, CancelException, ClassHierarchyException, IOException {
		Pair<Long, SDGBuilder.SDGBuilderConfig> p = prepareBuild(out, cfg, computeInterference, progress);
		return SDGBuilder.onlyCreate(p.snd);
	}

	public static boolean checkOrCreateOutputDir(String dir) {
		if (dir.endsWith(File.separator)) {
			dir = dir.substring(0, dir.length() - File.separator.length());
		}

		final File f = new File(dir);

		if (!f.exists()) {
			if (!f.mkdirs()) {
				return false;
			}
		}

		return f.canRead() && f.canWrite();
	}

	private static void fail(String msg) {
		throw new IllegalStateException(msg);
	}

	public static class Config {
		public String name;
		public String entryMethod;
		public List<String> entryMethods;
		public String classpath;
		public String thirdPartyLibPath;
		public String exclusions;
		public String nativesXML;
		public String stubs;
		public String outputDir;
		public ExternalCallCheck extern;
		public PointsToPrecision pts;
		// only used iff pts is set to object sensitive. If null defaults to
		// "do object sensitive analysis for all methods"
		public ObjSensZeroXCFABuilder.MethodFilter objSensFilter = null;
		public ExceptionAnalysis exceptions;
		public MethodState defaultExceptionMethodState = null;
		public boolean accessPath;
		public boolean debugManyGraphsDotOutput = false;
		public FieldPropagation fieldPropagation;
		public SideEffectDetectorConfig sideEffects = null;
		public PruningPolicy pruningPolicy = ApplicationLoaderPolicy.INSTANCE;
		public boolean computeAllocationSites = false;
		public CGConsumer cgConsumer = null;
		public ContextSelector ctxSelector = null;
		public Config(String name) {
			this(name, "<no entry defined>", FieldPropagation.OBJ_GRAPH);
		}

		public Config(String name, String entryMethod, FieldPropagation fieldPropagation) {
			this(name, entryMethod, STD_CLASS_PATH, PointsToPrecision.INSTANCE_BASED, DEFAULT_EXCEPTION_ANALYSIS,
					DEFAULT_ACCESS_PATH, STD_EXCLUSION_REG_EXP, JoanaConstants.DEFAULT_NATIVES_XML, /* stubs */null,
					/*ext-call*/null, "./", fieldPropagation);
		}

		public Config(String name, String entryMethod, String classpath, FieldPropagation fieldPropagation) {
			this(name, entryMethod, classpath, PointsToPrecision.INSTANCE_BASED, DEFAULT_EXCEPTION_ANALYSIS,
					DEFAULT_ACCESS_PATH, STD_EXCLUSION_REG_EXP, JoanaConstants.DEFAULT_NATIVES_XML, /* stubs */null,
					/*ext-call*/null, "./", fieldPropagation);
		}

		public Config(String name, String entryMethod, String classpath, PointsToPrecision pts,
				FieldPropagation fieldPropagation) {
			this(name, entryMethod, classpath, pts, DEFAULT_EXCEPTION_ANALYSIS, DEFAULT_ACCESS_PATH,
					STD_EXCLUSION_REG_EXP, JoanaConstants.DEFAULT_NATIVES_XML, /* stubs */null, /*ext-call*/null,
					"./", fieldPropagation);
		}

		public Config(String name, String entryMethod, String classpath, String exclusions,
				FieldPropagation fieldPropagation) {
			this(name, entryMethod, classpath, PointsToPrecision.INSTANCE_BASED, DEFAULT_EXCEPTION_ANALYSIS,
					DEFAULT_ACCESS_PATH, exclusions, JoanaConstants.DEFAULT_NATIVES_XML, /* stubs */null,
					/*ext-call*/null, "./", fieldPropagation);
		}

		public Config(String name, String entryMethod, String classpath, PointsToPrecision pts, String exclusions,
				FieldPropagation fieldPropagation) {
			this(name, entryMethod, classpath, pts, DEFAULT_EXCEPTION_ANALYSIS, DEFAULT_ACCESS_PATH, exclusions,
					JoanaConstants.DEFAULT_NATIVES_XML, /* stubs */null, /*ext-call*/null, "./", fieldPropagation);
		}

		public Config(String name, String entryMethod, String classpath, PointsToPrecision pts,
				ExceptionAnalysis exceptions, boolean accessPath, String exclusions, String nativesXML, String stubs,
				ExternalCallCheck extern, String outputDir,	FieldPropagation fieldPropagation) {
			this.name = name;
			this.pts = pts;
			this.exceptions = exceptions;
			this.accessPath = accessPath;
			this.classpath = classpath;
			this.entryMethod = entryMethod;
			this.exclusions = exclusions;
			this.nativesXML = nativesXML;
			this.stubs = stubs;
			this.extern = extern;

			if (!outputDir.endsWith(File.separator)) {
				this.outputDir = outputDir + File.separator;
			} else {
				this.outputDir = outputDir;
			}

			this.fieldPropagation = fieldPropagation;
		}

		@Override
		public String toString() {
			return LogUtil.attributesToString(this);
		}
	}


}
