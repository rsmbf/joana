/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.ui.ifc.wala.console.console;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.PatternSyntaxException;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.IFCType;
import edu.kit.joana.api.annotations.AnnotationType;
import edu.kit.joana.api.annotations.IFCAnnotation;
import edu.kit.joana.api.lattice.BuiltinLattices;
import edu.kit.joana.api.sdg.SDGClass;
import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGFormalParameter;
import edu.kit.joana.api.sdg.SDGMethod;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.api.sdg.SDGProgramPartWriter;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.SecurityNode.SecurityNodeFactory;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.graph.SDG;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.lattice.IEditableLattice;
import edu.kit.joana.ifc.sdg.lattice.IStaticLattice;
import edu.kit.joana.ifc.sdg.lattice.InvalidLatticeException;
import edu.kit.joana.ifc.sdg.lattice.LatticeUtil;
import edu.kit.joana.ifc.sdg.lattice.WrongLatticeDefinitionException;
import edu.kit.joana.ifc.sdg.mhpoptimization.MHPType;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.ui.ifc.wala.console.io.IFCAnnotationDumper;
import edu.kit.joana.ui.ifc.wala.console.io.IFCAnnotationReader;
import edu.kit.joana.ui.ifc.wala.console.io.IFCConsoleOutput;
import edu.kit.joana.ui.ifc.wala.console.io.IFCConsoleOutput.Answer;
import edu.kit.joana.ui.ifc.wala.console.io.InvalidAnnotationFormatException;
import edu.kit.joana.ui.ifc.wala.console.io.MethodNotFoundException;
import edu.kit.joana.ui.ifc.wala.console.io.NumberedIFCAnnotationDumper;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.util.io.IOFactory;
import edu.kit.joana.wala.core.NullProgressMonitor;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.FieldPropagation;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

public class IFCConsole {

/** @formatter:off */
	public enum CMD {
		// format is
		// 		(String name, int arity, String format, String description)
		// or 	(String name, int minArity, int maxArity, String format, String description)
		HELP(			"help", 				0, 		"",
							"Display this help."),
		SEARCH_ENTRIES(	"searchEntries", 		0, 		"",
							"Searches for possible entry methods."),
		SELECT_ENTRY(	"selectEntry", 			1, 		"",
							"Selects an entry method for sdg generation."),
		SET_CLASSPATH(	"setClasspath", 		1, 		"<path>",
							"Sets the class path for sdg generation. Can be for example a bin directory or a jar file."),
		SET_EXCEPTIONS( "setExceptionAnalysis", 1, "<exception analysis type>", "Sets the type of exception analysis to perform during SDG construction. Possible values are: " + Arrays.toString(ExceptionAnalysis.values())),
		SET_POINTSTO(	"setPointsTo", 			1, 		"<points-to precision>",
							"Sets the points-to precision for sdg generation."),
		SET_COMPUTE_INTERFERENCES("setComputeInterferences", 1, "true|false", "Sets whether interference edges shall be computed or not."),
		SET_MHP_TYPE(
						"setMHPType", 		1, 	"<mhp type>", 			"Sets the type of MHP analysis to use if interference edges are activated. Possible values are: " + Arrays.toString(MHPType.values())),
		INFO(			"info", 				0, 		"",
							"Display the current configuration for sdg generation and ifc analysis"),
		BUILD_SDG(		"buildSDG", 			0, 	3, 	"<compute interference?> <mhptype> [<exception analysis type>]",
							"Build sdg with respect to selected entry method. It is possible to use this command parameterless, then the current values of the respective options are taken. Otherwise, provide <compute interference> , <mhptype> and optionally <exception analysis type>. If, in this latter form, <exception analysis type> is not provided, INTERPROC is used."),
		LOAD_SDG(		"loadSDG", 				1, 		"<filename>",
							"Load sdg stored in <filename>."),
		SOURCE(			"source", 				2, 		"<index> <level>",
							"Annotate specified part of method with provided security level <level>. <index> is either of the form p<number> for parameters of i<number> for instructions."),
		SAVE_SDG(		"saveSDG", 				1, 		"<filename>",
							"Store current SDG in file specified by <filename>."),
		SINK(			"sink", 				2, 		"<index> <level>",
							"Annotate specified node with required security level <level>. <index> refers to the indices shown in the currently active method."),
		CLEAR(			"clear", 				1, 		"<index>",
							"Clear annotation of specified node. <index> refers to the indices shown in the currently active method."),
		CLEARALL(		"clearAll", 			0, 		"",
							"Clear all annotations."),
		DECLASS(		"declass", 				3, 		"<index> <level1> <level2>",
							"Declassify specified node from <level1> to <level2>. <index> refers to the indices shown in the currently active method."),
		RUN(			"run", 					0, 	2, 	" [type] ",
							"Run IFC analysis with specified data. The optional parameter type denotes the type of ifc analysis. It can be " + IFCTYPE_CLASSICAL_NI + ", " + IFCTYPE_LSOD + " or " + IFCTYPE_RLSOD + ". If it is omitted, classical non-interference is used."),
		RESET(			"reset", 				0, 		"",
							"Reset node data."),
		SAVE_ANNOT(		"saveAnnotations", 		1, 		"<filename>",
							"Save annotations done so far in specified file."),
		LOAD_ANNOT(		"loadAnnotations", 		1, 		"<filename>",
							"Load annotations from specified file."),
		SHOW_ANNOT(		"showAnnotations", 		0, 		"",
							"Show the annotations done so far."),
		LOAD_LATTICE(	"loadLattice", 			1, 		"<filename>",
							"Load security lattice definition from file specified by <filename>."),
		SET_LATTICE(	"setLattice", 			1, 		"<latticeSpec>",
							"Set lattice to the one given by <latticeSpec>. <latticeSpec> is a comma-separated list (without any spaces) of inequalities of the form lhs<=rhs specifying the lattice."),
		SEARCH(			"search", 				1, 		"<pattern>",
							"Searches for method names containing the given pattern and displays result. <pattern> is a java-style regular expression."),
		SET_STUBSPATH(	"setStubsPath", 		1, 		"<path>",
							"Sets the path to the stubs file. If <path> is 'none', then no stubs will be used."),
		LIST(			"list", 				0, 		"",
							"Displays last method search results."),
		SELECT(			"select", 				1, 		"<index>",
							"Selects method with index <index> from last search result list."),
		ACTIVE(			"active", 				0, 		"",
							"Shows the active method."),
		SAVESCRIPT(		"saveScript", 			1, 		"<filename>",
							"Saves the instructions up to now to given file."),
		LOADSCRIPT(		"loadScript", 			1, 		"<filename>",
							"Loads instructions from given file and executes them."),
		QUIT(			"quit", 				0, 		"",
							"Exit the IFC console."),
		SHOW_CLASSES(	"showClasses", 			0, 		"",
							"Shows all classes contained in the current sdg"),
		SHOWBCI(		"showBCI", 				0, 		"",
							"Shows all bc indices seen so far."),
		VERIFY_ANNOT(	"verifyAnnotations", 	0, 		"",
							"Verifies that the recorded annotations are mapped consistently to the sdg and vice versa."),
        CHOP(			"chop", 				2, 		"<source> <sink>",
        					"Generates a chop between two program points");

		private final String name;
		private final int minArity;
		private final int maxArity;
		private final String format;
		private final String description;

		private CMD(String name, int arity, String format, String descr) {
			this(name, arity, arity, format, descr);
		}

		private CMD(String name, int minArity, int maxArity, String format, String descr) {
			this.name = name;
			this.minArity = minArity;
			this.maxArity = maxArity;
			this.format = format;
			this.description = descr;
		}

		public String getName() {
			return this.name;
		}

		public String getExpectedFormat() {
			return this.format;
		}

		public int getMinArity() {
			return this.minArity;
		}

		public int getMaxArity() {
			return this.maxArity;
		}

		public String getDescription() {
			return this.description;
		}
	}
/** @formatter:on */
	
	private static abstract class Command {

		private final CMD cmd;
		private String stringRepr = null;

		public Command(CMD cmd) {
			this.cmd = cmd;
		}

		public int getMinArity() {
			return cmd.getMinArity();
		}

		abstract boolean execute(String[] args);

		public String getName() {
			return cmd.getName();
		}

		String getExpectedFormat() {
			return cmd.getExpectedFormat();
		}

		public String getDescription() {
			return cmd.getDescription();
		}

		public String toString() {
			if (stringRepr == null) {
				StringBuffer sb = new StringBuffer();
				sb.append(getName());

				if (getMinArity() > 0) {
					sb.append(" ");
					sb.append(getExpectedFormat());
				}

				sb.append("\n\t" + getDescription());
				stringRepr = sb.toString();
			}

			return stringRepr;
		}

		public CMD getCMD() {
			return cmd;
		}
	}

	private static class CommandRepository {
		private SortedMap<String, Command> availCommands = new TreeMap<String, Command>();

		public void addCommand(Command cmd) {
			availCommands.put(cmd.getName(), cmd);
		}

		public boolean knowsCommand(String cmd) {
			return availCommands.containsKey(cmd);
		}

		// public int getArity(String cmd) {
		// return availCommands.get(cmd).getArity();
		// }

		public boolean executeCommand(CMD cmd, String[] args) {
			final Command command = availCommands.get(cmd.getName());
            return command.execute(args);
		}

		public CMD getCommand(String cmdstr) {
			Command cmd = availCommands.get(cmdstr);

			return (cmd == null ? null : cmd.getCMD());
		}

		public Collection<String> getCommands() {
			return availCommands.keySet();
		}

		public String getHelpMessage(String cmdName) {
			return availCommands.get(cmdName).toString();
		}
	}

	private static final String IFCTYPE_CLASSICAL_NI = "classical-ni";
	private static final String IFCTYPE_LSOD = "lsod";
	private static final String IFCTYPE_RLSOD = "rlsod";

	public static final String LATTICE_BINARY = "BINARY";
	public static final String LATTICE_TERNARY = "TERNARY";
	public static final String LATTICE_DIAMOND = "DIAMOND";

	public static final String DONT_USE_STUBS = "<none>";
	public static final String AVOID_TIME_TRAVEL = "TIMESENS";

	private BufferedReader in;
	// private PrintStream out;
	// private PrintStream errOut;
	// private PrintStream infoOut;
	//
	private IFCConsoleOutput out;

	private boolean showPrompt = true;

	// private SDG sdg;
	private IFCAnalysis ifcAnalysis = null;
	private String classPath = "bin";
	private PointsToPrecision pointsTo = PointsToPrecision.INSTANCE_BASED;
	private ExceptionAnalysis excAnalysis = ExceptionAnalysis.INTRAPROC;
	private boolean computeInterference = false;
	private MHPType mhpType = MHPType.NONE;
	// private IStaticLattice<String> securityLattice;
	private Collection<IViolation<SecurityNode>> lastAnalysisResult = new LinkedList<IViolation<SecurityNode>>();
	private TObjectIntMap<IViolation<SDGProgramPart>> groupedIFlows = new TObjectIntHashMap<IViolation<SDGProgramPart>>();
	private Set<edu.kit.joana.api.sdg.SDGInstruction> lastComputedChop = null;
    private final EntryLocator loc = new EntryLocator();
	private List<IFCConsoleListener> consoleListeners = new LinkedList<IFCConsoleListener>();
	private IProgressMonitor monitor = NullProgressMonitor.INSTANCE;
	private final SDGMethodSelector methodSelector = new SDGMethodSelector(this);
	private IStaticLattice<String> secLattice = IFCAnalysis.stdLattice;
	private CommandRepository repo = new CommandRepository();
	private String outputDirectory = "./";
	private String latticeFile;
	private Stubs stubsPath = Stubs.JRE_14;

	private List<String> script = new LinkedList<String>();

	public IFCConsole(BufferedReader in, IFCConsoleOutput out) {
		this.in = in;
		this.out = out;
		// this.infoOut = infoOut;
		// this.errOut = errOut;
		initialize();
	}

	private Command makeCommandHelp() {
		return new Command(CMD.HELP) {
			@Override
			boolean execute(String[] args) {
				showUsageOutline();
				return true;
			}
		};
	}

	private Command makeCommandSearchEntries() {
		return new Command(CMD.SEARCH_ENTRIES) {

			@Override
			boolean execute(String[] args) {
				return searchEntries();
			}
		};
	}

	private Command makeCommandSelectEntry() {
		return new Command(CMD.SELECT_ENTRY) {

			@Override
			boolean execute(String[] args) {
				Integer i = parseInteger(args[1]);
				if (i != null) {
					return selectEntry(i);
				} else {
					JavaMethodSignature sig = JavaMethodSignature.fromString(args[1]);
					if (sig != null) {
						return selectEntry(sig);
					} else {
						return false;
					}
				}
			}
		};
	}

	private Command makeCommandSetClasspath() {
		return new Command(CMD.SET_CLASSPATH) {

			@Override
			boolean execute(String[] args) {
				setClasspath(args[1]);
				out.logln("classPath = " + classPath);
				return true;
			}

		};
	}

	private Command makeCommandSetPointsTo() {
		return new Command(CMD.SET_POINTSTO) {

			@Override
			boolean execute(String[] args) {
				setPointsTo(args[1]);
				out.logln("points-to = " + pointsTo.desc);
				return true;
			}

		};
	}

	private Command makeCommandSetExceptionAnalysis() {
		return new Command(CMD.SET_EXCEPTIONS) {

			@Override
			boolean execute(String[] args) {
				setExceptionAnalysis(args[1]);
				out.logln("exceptionAnalysis = " + excAnalysis.desc);
				return true;
			}

		};
	}

	private Command makeCommandSetMHPType() {
		return new Command(CMD.SET_MHP_TYPE) {

			@Override
			boolean execute(String[] args) {
				setMHPType(args[1]);
				out.logln("mhpAnalysis = " + mhpType);
				return true;
			}

		};
	}

	private Command makeCommandSetComputeInterferences() {
		return new Command(CMD.SET_COMPUTE_INTERFERENCES) {

			@Override
			boolean execute(String[] args) {
				if (!("true".equals(args[1]) || "false".equals(args[1]))) {
					out.logln("invalid setting: " + args[1]);
					return false;
				} else {
					boolean b = "true".equals(args[1]);
					setComputeInterferences(b);
					out.logln("computeInterferences = " + args[1]);
					return true;
				}
			}

		};
	}

	private Command makeCommandSetStubsPath() {
		return new Command(CMD.SET_STUBSPATH) {

			@Override
			boolean execute(String[] args) {
				Stubs stubs = Stubs.fromString(args[1]);
				if (stubs != null) {
					setStubsPath(stubs);
					out.logln("stubs = " + stubs);
					return true;
				} else {
					out.error("Specified stubs not available!");
					return false;
				}

			}

		};
	}

	private Command makeCommandInfo() {
		return new Command(CMD.INFO) {

			@Override
			boolean execute(String[] args) {
				displayCurrentConfig();
				return true;
			}
		};
	}

	private Command makeCommandBuildSDG() {
		return new Command(CMD.BUILD_SDG) {
			@Override
			boolean execute(String[] args) {
				if (args.length == 1) {
					return buildSDG(IFCConsole.this.computeInterference, IFCConsole.this.mhpType, IFCConsole.this.excAnalysis);
				} else if (args.length == 3) {
					if ("true".equals(args[1])) {
						return buildSDG(true, MHPType.valueOf(MHPType.class, args[2]), ExceptionAnalysis.INTERPROC);
					} else {
						assert "false".equals(args[1]);
						return buildSDG(false, MHPType.NONE,
								ExceptionAnalysis.valueOf(ExceptionAnalysis.class, args[2]));
					}
				} else {
					assert args.length == 4;
					if ("true".equals(args[1])) {
						return buildSDG(true, MHPType.valueOf(MHPType.class, args[2]),
								ExceptionAnalysis.valueOf(ExceptionAnalysis.class, args[3]));
					} else {
						assert "false".equals(args[1]);
						return buildSDG(false, MHPType.valueOf(MHPType.class, args[2]),
								ExceptionAnalysis.valueOf(ExceptionAnalysis.class, args[3]));
					}
				}
			}
		};
	}

	private Command makeCommandSaveSDG() {
		return new Command(CMD.SAVE_SDG) {

			@Override
			boolean execute(String[] args) {
				return saveSDG(args[1]);
			}

		};
	}

	// this command is redundant!
	// private Command makeCommandBuildCSDG() {
	// return new Command(CMD.BUILD_CSDG) {
	// @Override
	// boolean execute(String[] args) {
	// if (args.length == 2) {
	// return buildCSDG(args[1]);
	// } else {
	// assert args.length == 3;
	// return buildCSDG(args[1],
	// ExceptionAnalysis.valueOf(ExceptionAnalysis.class, args[2]));
	// }
	// }
	// };
	// }

	private Command makeCommandLoadSDG() {
		return new Command(CMD.LOAD_SDG) {
			@Override
			boolean execute(String[] args) {
				return loadSDG(args[1]);
			}
		};
	}

	private Command makeCommandSource() {
		return new Command(CMD.SOURCE) {

			@Override
			boolean execute(String[] args) {
				return annotateProgramPartAsSrcOrSnk(args[1], args[2], AnnotationType.SOURCE);
			}
		};
	}

	private Command makeCommandSink() {
		return new Command(CMD.SINK) {
			@Override
			boolean execute(String[] args) {
				return annotateProgramPartAsSrcOrSnk(args[1], args[2], AnnotationType.SINK);
			}
		};
	}

	private Command makeCommandClear() {
		return new Command(CMD.CLEAR) {
			@Override
			boolean execute(String[] args) {
				return clearAnnotation(args[1]);
			}
		};
	}

	private Command makeCommandClearAll() {
		return new Command(CMD.CLEARALL) {
			@Override
			boolean execute(String[] args) {
				if (ifcAnalysis == null || ifcAnalysis.getProgram() == null) {
					out.info("No program loaded. Build or load SDG first!");
					return true;
				}
				ifcAnalysis.clearAllAnnotations();
				return true;
			}
		};
	}

	private Command makeCommandDeclass() {
		return new Command(CMD.DECLASS) {
			@Override
			boolean execute(String[] args) {
				return declassifyProgramPart(args[1], args[2], args[3]);
			}
		};
	}

	private Command makeCommandRun() {
		return new Command(CMD.RUN) {
			@Override
			boolean execute(String[] args) {
				if (args.length == 1) {
					return doIFC(IFCType.CLASSICAL_NI, false);
				} else {
					IFCType ifcType = parseIFCType(args[1]);
					// standard value for time-sensitivity is false; only set to true if mentioned explicitly
					boolean timeSens = args.length > 2 && AVOID_TIME_TRAVEL.equals(args[2]);

					if (ifcType == null) {
						out.error("unknown ifc type: " + args[1]);
						return false;
					} else {
						return doIFC(ifcType, timeSens);
					}
				}
			}

			private IFCType parseIFCType(String s) {
				if (IFCTYPE_CLASSICAL_NI.equals(s)) {
					return IFCType.CLASSICAL_NI;
				} else if (IFCTYPE_LSOD.equals(s)) {
					return IFCType.LSOD;
				} else if (IFCTYPE_RLSOD.equals(s)) {
					return IFCType.RLSOD;
				} else {
					return null;
				}
			}
		};
	}

	private Command makeCommandReset() {
		return new Command(CMD.RESET) {
			@Override
			boolean execute(String[] args) {
				reset();
				return true;
			}
		};
	}

	private Command makeCommandSaveMarkings() {
		return new Command(CMD.SAVE_ANNOT) {
			@Override
			boolean execute(String[] args) {
				return saveAnnotations(args[1]);
			}
		};
	}

	private Command makeCommandLoadMarkings() {
		return new Command(CMD.LOAD_ANNOT) {

			@Override
			boolean execute(String[] args) {

				return loadAnnotations(args[1]);

			}

		};
	}

	private Command makeCommandShowMarkings() {
		return new Command(CMD.SHOW_ANNOT) {
			@Override
			boolean execute(String[] args) {
				showAnnotations();
				return true;
			}
		};
	}

	private Command makeCommandLoadLattice() {
		return new Command(CMD.LOAD_LATTICE) {

			@Override
			boolean execute(String[] args) {
				return loadLattice(args[1]);
			}
		};
	}

	private Command makeCommandSetLattice() {
		return new Command(CMD.SET_LATTICE) {

			@Override
			boolean execute(String[] args) {
				return setLattice(args[1]);
			}

		};
	}

	private Command makeCommandSearch() {
		return new Command(CMD.SEARCH) {

			@Override
			boolean execute(String[] args) {
				return searchMethodsByName(args[1]);
			}
		};
	}

	private Command makeCommandList() {
		return new Command(CMD.LIST) {

			@Override
			boolean execute(String[] args) {
				displayLastSearchResults();
				return true;
			}
		};
	}

	private Command makeCommandSelect() {
		return new Command(CMD.SELECT) {

			@Override
			boolean execute(String[] args) {
				Integer i = parseInteger(args[1]);
				if (i != null) {
					return selectMethod(i);
				} else {
					return false;
				}
			}
		};
	}

	private Command makeCommandActive() {
		return new Command(CMD.ACTIVE) {

			@Override
			boolean execute(String[] args) {
				return displayActiveMethod();
			}
		};
	}

	private Command makeCommandLoadScript() {
		return new Command(CMD.LOADSCRIPT) {

			@Override
			boolean execute(String[] args) {
				return loadInstructions(args[1]);
			}

		};
	}

	private Command makeCommandSaveScript() {
		return new Command(CMD.SAVESCRIPT) {

			@Override
			boolean execute(String[] args) {
				return saveInstructions(args[1]);
			}
		};
	}

	private Command makeCommandShowClasses() {
		return new Command(CMD.SHOW_CLASSES) {

			@Override
			boolean execute(String[] args) {
				return showClasses();
			}

		};
	}

	private Command makeCommandVerifyAnnotations() {
		return new Command(CMD.VERIFY_ANNOT) {

			@Override
			boolean execute(String[] args) {
				return verifyAnnotations();
			}
		};
	}

    private Command makeCommandChop() {
		return new Command(CMD.CHOP) {
			@Override
			boolean execute(String[] args) {
                return createChop(args[1], args[2]);
			}
		};
	}


	private boolean verifyAnnotations() {
		if (getSDG() == null) {
			return ifcAnalysis.getSources().isEmpty() && ifcAnalysis.getSinks().isEmpty()
					&& ifcAnalysis.getDeclassifications().isEmpty();
		} else {
			Collection<IFCAnnotation> sources = ifcAnalysis.getSources();
			Collection<IFCAnnotation> sinks = ifcAnalysis.getSinks();
			Collection<IFCAnnotation> declass = ifcAnalysis.getDeclassifications();
			for (SDGNode node : getSDG().vertexSet()) {
				SecurityNode sNode = (SecurityNode) node;
				boolean found = false;
				if (sNode.isInformationSource()) {
					for (IFCAnnotation source : sources) {
						if (ifcAnalysis.getProgram().covers(source.getProgramPart(),sNode)) {
							found = true;
							break;
						}
					}
					if (!found) {
						out.error("Node " + sNode + " in sdg is annotated but has no corresponding annotation object!");
						return false;
					}
				} else if (sNode.isInformationSink()) {
					for (IFCAnnotation sink : sinks) {
						if (ifcAnalysis.getProgram().covers(sink.getProgramPart(), sNode)) {
							found = true;
							break;
						}
					}
					if (!found) {
						out.error("Node " + sNode + " in sdg is annotated but has no corresponding annotation object!");
						return false;
					}
				} else if (sNode.isDeclassification()) {
					for (IFCAnnotation dec : declass) {
						if (ifcAnalysis.getProgram().covers(dec.getProgramPart(), sNode)) {
							found = true;
							break;
						}
					}
					if (!found) {
						out.error("Node " + sNode + " in sdg is annotated but has no corresponding annotation object!");
						return false;
					}
				} else {
					// assert sNode.getProvided() == null && sNode.getRequired()
					// == null;
					// for (IFCAnnotation source : sources) {
					// if (source.getProgramPart().covers(sNode)) {
					// out.error("Node " + node +
					// " in sdg which is not annotated but has a corresponding annotation object: "
					// + source.getProgramPart() + "!");
					// return false;
					// }
					// }
					//
					// for (IFCAnnotation sink : sinks) {
					// if (sink.getProgramPart().covers(sNode)) {
					// out.error("Node " + node +
					// " in sdg which is not annotated but has a corresponding annotation object: "
					// + sink.getProgramPart() + "!");
					// return false;
					// }
					// }
					//
					// for (IFCAnnotation dec : declass) {
					// if (dec.getProgramPart().covers(sNode)) {
					// out.error("Node " + node +
					// " in sdg which is not annotated but has a corresponding annotation object: "
					// + dec.getProgramPart() + "!");
					// return false;
					// }
					// }
				}
			}
			out.info("Annotations are completely verified.");
			return true;
		}
	}

	private void initialize() {

		// setLattice("public<=secret");

		repo.addCommand(makeCommandHelp());
		repo.addCommand(makeCommandSearchEntries());
		repo.addCommand(makeCommandSelectEntry());
		repo.addCommand(makeCommandSetClasspath());
		repo.addCommand(makeCommandSetExceptionAnalysis());
		repo.addCommand(makeCommandSetPointsTo());
		repo.addCommand(makeCommandSetComputeInterferences());
		repo.addCommand(makeCommandSetMHPType());
		repo.addCommand(makeCommandSetStubsPath());
		repo.addCommand(makeCommandInfo());
		repo.addCommand(makeCommandBuildSDG());
		// repo.addCommand(makeCommandBuildCSDG()); <-- this command is
		// redundant!
		repo.addCommand(makeCommandLoadSDG());
		repo.addCommand(makeCommandSaveSDG());

		// ifc commands

		repo.addCommand(makeCommandSource());
		repo.addCommand(makeCommandSink());
		repo.addCommand(makeCommandClear());
		repo.addCommand(makeCommandClearAll());
		repo.addCommand(makeCommandDeclass());
		repo.addCommand(makeCommandRun());
		repo.addCommand(makeCommandReset());
		repo.addCommand(makeCommandSaveMarkings());
		repo.addCommand(makeCommandLoadMarkings());
		repo.addCommand(makeCommandShowMarkings());

		// lattice commands

		repo.addCommand(makeCommandLoadLattice());
		repo.addCommand(makeCommandSetLattice());

		// method search and selection

		repo.addCommand(makeCommandSearch());
		repo.addCommand(makeCommandList());
		repo.addCommand(makeCommandSelect());
		repo.addCommand(makeCommandActive());

		repo.addCommand(makeCommandLoadScript());
		repo.addCommand(makeCommandSaveScript());
		repo.addCommand(makeCommandShowClasses());
		repo.addCommand(makeCommandVerifyAnnotations());
        repo.addCommand(makeCommandChop());

		setLattice(LATTICE_BINARY);
	}

	public boolean selectMethod(int i) {
		if (methodSelector.lastSearchResultEmpty()) {
			out.info("Last search result is empty. Cannot select anything from empty list!");
			return false;
		} else if (!methodSelector.indexValid(i)) {
			out.error("Invalid method index!");
			return false;
		} else {
			methodSelector.selectMethod(i);
			displayActiveMethod();
			return true;
		}
	}

	public boolean searchMethodsByName(String name) {
		boolean found;
		try {
			found = methodSelector.searchMethodsContainingName(name);
			if (!found) {
				out.info("No search results. Last search results remain active.");
				return false;
			} else {
				displayLastSearchResults();
				return true;
			}
		} catch (PatternSyntaxException e) {
			out.error("Invalid search pattern: " + e.getMessage());
			return false;
		}
	}

	public boolean annotateProgramPartAsSrcOrSnk(String programPart, String level, AnnotationType type) {
		if (inSecurityLattice(level)) {
			SDGProgramPart toMark = getProgramPartFromSelectorString(programPart, false);
			if (toMark != null) {
				IFCAnnotation ann = new IFCAnnotation(type, level, toMark);
				if (ifcAnalysis.isAnnotationLegal(ann)) {
					ifcAnalysis.addAnnotation(ann);
					out.logln(String.format("Annotating '%s' as %s of security level '%s'...", toMark.toString(), type.toString(), level));
					return true;
				} else {
					out.error("Illegal Annotation!");
					return false;
				}

			} else {
				return false;
			}
		} else {
			out.error("Level " + level + " not in security lattice! Try one of " + getSecurityLevels());
			return false;
		}
	}

	public boolean declassifyProgramPart(String programPartDesc, String level1, String level2) {
		if (inSecurityLattice(level1) && inSecurityLattice(level2) && greaterOrEqual(level1, level2)) {
			SDGProgramPart toMark = getProgramPartFromSelectorString(programPartDesc, false);
			if (toMark != null) {
				IFCAnnotation ann = new IFCAnnotation(level1, level2, toMark);
				if (ifcAnalysis.isAnnotationLegal(ann)) {
					ifcAnalysis.addDeclassification(toMark, level1, level2);
					return true;
				} else {
					out.error("Illegal Annotation!");
					return false;
				}
			} else {
				out.error("Program part with name " + programPartDesc + " not found!");
				return false;
			}
		} else {
			if (!inSecurityLattice(level1)) {
				out.error("Level " + level1 + " is not an element of security lattice! Try one of "
						+ getSecurityLevels());
				return false;
			} else {
				out.error("Level " + level2 + " is not an element of security lattice! Try one of "
						+ getSecurityLevels());
				return false;
			}
		}
	}

	public boolean clearAnnotation(String programPartDesc) {
		SDGProgramPart toClear = getProgramPartFromSelectorString(programPartDesc, true);
		if (toClear != null) {
			ifcAnalysis.clearAllAnnotationsOfMethodPart(toClear);
			return true;
		} else {
			out.error("Program part with name " + programPartDesc + " not found!");
			return false;
		}
		// Integer clearIndex;
		// clearIndex = parseInteger(programPartDesc);//
		// getMethodPartFromAnnotationIndex(args[1]);
		// if (clearIndex != null) {
		// if (annotationManager.annotationIndexValid(clearIndex)) {
		// annotationManager.clearAnnotation(clearIndex);
		// return true;
		// } else {
		// out.error("Invalid annotation index! Must be between 0 and "
		// + (annotationManager.getNumberOfAnnotations() - 1) + "!");
		// return false;
		// }
		// } else {
		// return false;
		// }
	}

	public boolean selectEntry(int i) {
		if (loc.foundPossibleEntries()) {
			if (loc.entryIndexValid(i)) {
				loc.selectEntry(i);
				out.logln("entry = " + loc.getActiveEntry().toHRString());
				return true;
			} else {
				out.error("Invalid index! Must be in range 0-" + (loc.getNumberOfFoundEntries() - 1));
				return false;
			}
		} else {
			out.info("Cannot select any entry method from empty list! Invoke search first!");
			return false;
		}
	}

	public boolean selectEntry(JavaMethodSignature sig) {
		if (loc.foundPossibleEntries()) {
			if (loc.getIndex(sig) >= 0) {
				loc.selectEntry(sig);
				out.logln("entry = " + loc.getActiveEntry().toHRString());
				return true;
			} else {
				out.error("Method " + sig + " not found!");
				return false;
			}
		} else {
			out.info("Cannot select any entry method from empty list! Invoke search first!");
			return false;
		}
	}

	public boolean searchEntries() {
		JavaMethodSignature oldSelected = loc.getActiveEntry();
		boolean found = loc.doSearch(classPath, out);
		if (!found) {
			out.info("No entry methods found.");
			return false;
		} else {
			loc.displayLastEntrySearchResults(out);
			if (loc.getNumberOfFoundEntries() == 1) {
				selectEntry(0);
			} else if (loc.getLastSearchResults().contains(oldSelected)) {
				selectEntry(oldSelected);
			}
			return true;
		}
	}

	public void setClasspath(String newClasspath) {
		this.classPath = newClasspath;
	}

	public void setPointsTo(final String newPts) {
		for (final PointsToPrecision pts : PointsToPrecision.values()) {
			if (pts.name().equals(newPts)) {
				this.pointsTo = pts;
				break;
			}
		}
	}

	public void setExceptionAnalysis(final String newExc) {
		for (final ExceptionAnalysis exc : ExceptionAnalysis.values()) {
			if (exc.name().equals(newExc)) {
				this.excAnalysis = exc;
				break;
			}
		}
	}

	public void setMHPType(final String newMHPType) {
		for (final MHPType mhp : MHPType.values()) {
			if (mhp.name().equals(newMHPType)) {
				this.mhpType = mhp;
				break;
			}
		}
	}

	public void setComputeInterferences(boolean cmpInt) {
		this.computeInterference = cmpInt;
	}

	public Stubs getStubsPath() {
		return stubsPath;
	}

	public void setStubsPath(Stubs newStubsPath) {
		this.stubsPath = newStubsPath;
	}

	public Collection<String> getSecurityLevels() {
		return ifcAnalysis.getLattice().getElements();
	}

	private boolean inSecurityLattice(String level) {
		return ifcAnalysis.getLattice().getElements().contains(level);
	}

	private boolean greaterOrEqual(String level1, String level2) {
		return ifcAnalysis.getLattice().leastUpperBound(level1, level2).equals(level1);
	}

	public void displayLastSearchResults() {
		if (methodSelector.noSearchResults()) {
			out.info("No search results.");
		} else {
			for (int i = 0; i < methodSelector.numberOfSearchResults(); i++) {
				out.logln("[" + i + "] " + methodSelector.getMethod(i));
			}
		}
	}

	public Collection<SDGClass> getClasses() {
		return ifcAnalysis.getProgram().getClasses();
	}

	public SDGProgramPart getProgramPartFromSelectorString(String desc, boolean silent) {
		return ifcAnalysis.getProgramPart(desc);
	}

	public String getSelectorStringFromMethodPart(SDGProgramPart part) {
		return SDGProgramPartWriter.getStandardVersion().writeSDGProgramPart(part);
	}

	public boolean displayActiveMethod() {
		if (methodSelector.lastSearchResultEmpty()) {
			out.info("No method to select");
			return false;
		} else {
			if (!methodSelector.methodSelected()) {
				out.info("No method selected.");
				return false;
			} else {
				displayMethod(methodSelector.getActiveMethod());
				return true;
			}
		}
	}

	public void displayMethod(SDGMethod m) {
		int mIndex = methodSelector.getIndex(m);
		out.logln("Displaying method " + m.getSignature().toHRString());
		out.logln("Parameters: ");
		for (SDGFormalParameter p : m.getParameters()) {
			out.logln("[p" + p.getIndex() + "] " + p);
		}
		out.logln("Instructions: ");
		for (int i = 0; i < m.getNumberOfInstructions(); i++) {
			out.logln("[m" + mIndex + "->i" + i + "] " + m.getInstruction(i));
		}
	}

	private void setSDGProgram(SDGProgram newSDGProgram) {
		if (ifcAnalysis == null) {
			ifcAnalysis = new IFCAnalysis(newSDGProgram, this.secLattice);
		} else {
			ifcAnalysis.setProgram(newSDGProgram);
			ifcAnalysis.setLattice(this.secLattice);
		}
	}

	private void setSDG(SDG newSDG) {
		setSDGProgram(new SDGProgram(newSDG));
	}

	public void displayCurrentConfig() {
		out.logln("classpath = " + classPath);
		out.logln("entry = " + (loc.getActiveEntry() == null ? "<none>" : loc.getActiveEntry()));
		out.logln("output directory = " + outputDirectory);
		out.logln("points-to = " + pointsTo.desc);
		out.logln("lattice = " + latticeFile);
		// out.logln("sdg = " + sdgFile);

	}

	private Integer parseInteger(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	public boolean loadLattice(String latFile) {

		FileInputStream in;

		try {
			in = new FileInputStream(latFile);
		} catch (FileNotFoundException fnfe) {
			out.error("File " + latFile + " not found!");
			return false;
		}

		IEditableLattice<String> lattice;

		try {
			lattice = LatticeUtil.loadLattice(in);
		} catch (WrongLatticeDefinitionException wlde) {
			out.error("Lattice specified in " + latFile + " is invalid! Old lattice is left untouched!");
			return false;
		} catch (IOException ioe) {
			out.error("I/O error while reading lattice from file " + latFile + "!");
			return false;
		}

		if (checkAndSetLattice(lattice)) {
			latticeFile = latFile;
			return true;
		} else {
			return false;
		}
	}

	private boolean checkAndSetLattice(IStaticLattice<String> l0) {
		try {
			l0.getBottom();
		} catch (InvalidLatticeException e) {
			out.error("Wrong lattice definition! Specified partial order has no bottom element and is thus no lattice! Old lattice is left untouched!");
			return false;
		}
		for (String s : l0.getElements()) {
			for (String t : l0.getElements()) {
				try {
					l0.leastUpperBound(s, t);
				} catch (InvalidLatticeException e) {
					out.error("Specified partial order is no lattice! Elements " + s + " and " + t
							+ " have no least upper bound! Old lattice is left untouched!");
					return false;
				}
			}
		}
		this.secLattice = l0;
		if (this.ifcAnalysis != null) {
			this.ifcAnalysis.setLattice(l0);
		}
		return true;
	}

	/**
	 * Sets the lattice used from now on.
	 *
	 * @param latticeSpec
	 *            either one of the constants for the built-in lattices (
	 *            {@link #LATTICE_BINARY}, {@link #LATTICE_TERNARY},
	 *            {@link #LATTICE_DIAMOND}, or a comma-separated inequalities
	 *            specifying a user-defined lattice.
	 * @return {@code true} if latticeSpec specifies one of the predefined
	 *         lattices (see above) or a valid user-defined lattice,
	 *         {@code false} otherwise.
	 */
	public boolean setLattice(String latticeSpec) {
		IStaticLattice<String> newLattice;
		latticeFile = "[preset: " + latticeSpec + "]";
		if (LATTICE_BINARY.equals(latticeSpec)) {
			newLattice = BuiltinLattices.getBinaryLattice();
		} else if (LATTICE_TERNARY.equals(latticeSpec)) {
			newLattice = BuiltinLattices.getTernaryLattice();
		} else if (LATTICE_DIAMOND.equals(latticeSpec)) {
			newLattice = BuiltinLattices.getDiamondLattice();
		} else {
			latticeSpec = latticeSpec.replaceAll("\\s*,\\s*", "\n");
			try {
				newLattice = LatticeUtil.loadLattice(latticeSpec);
			} catch (WrongLatticeDefinitionException e) {
				out.error("Error while parsing lattice: " + e.getMessage() + " Old lattice is left untouched!");
				return false;
			}
			latticeFile = "[user-defined: " + latticeSpec + "]";
		}
		if (checkAndSetLattice(newLattice)) {
			out.logln("current lattice: " + latticeFile);
		}
		return checkAndSetLattice(newLattice);
	}

	public CMD searchCommand(final String cmdstr) {
		CMD cmd = null;
		String[] parts = cmdstr.split("\\s+");
		if (repo.knowsCommand(parts[0])) {
			cmd = repo.getCommand(parts[0]);
		}

		return cmd;
	}

	public synchronized boolean processCommand(final CMD cmd, final String[] parts) {
		beforeCommand(cmd, parts);
		boolean success;

		try {
			success = executeAndLogCommand(cmd, parts);
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));

			out.error("Error: " + sw.toString());
			e.printStackTrace();
			success = false;
		}

		afterCommand(cmd, parts);

		return success;

	}

	private void beforeCommand(final CMD cmd, final String[] parts) {

		for (IFCConsoleListener l : consoleListeners) {
			l.cmdIssued(cmd, parts);
		}
	}

	private void afterCommand(final CMD cmd, final String[] parts) {
		for (IFCConsoleListener l : consoleListeners) {
			l.cmdDone(cmd, parts);
		}
	}

	private synchronized boolean executeAndLogCommand(final CMD cmd, final String[] parts) {
		boolean success;
		if (parts.length - 1 < cmd.getMinArity() || parts.length - 1 > cmd.getMaxArity()) {
			if (cmd.getMinArity() < cmd.getMaxArity()) {
				out.error("Invalid number of parameters. Command " + parts[0] + " expects between " + cmd.getMinArity()
						+ " and " + cmd.getMaxArity() + " parameters.");
			} else {
				out.error("Invalid number of parameters. Command " + parts[0] + " expects " + cmd.getMinArity()
						+ " parameters.");
			}
			success = false;
		} else {
			success = repo.executeCommand(cmd, parts);

			if (success) {
				switch (cmd) {
				case LOADSCRIPT:
				case SAVESCRIPT:
				case QUIT:
					break;
				default:
					script.add(glueTogether(parts));
					break;
				}
			}
		}
		notify();
		return success;
	}

	private String glueTogether(String[] parts) {
		StringBuffer sb = new StringBuffer();
		for (String part : parts) {
			sb.append(part + " ");
		}
		sb.replace(sb.length() - 1, sb.length(), "");
		return sb.toString();
	}

	public synchronized boolean processCommand(final String cmd) {

		String[] parts = cmd.split("\\s+");
		if (!repo.knowsCommand(parts[0])) {
			out.error("Command not found: " + cmd);
			notify();
			return false;
		} else {
			CMD c = searchCommand(cmd);
			return processCommand(c, parts);
		}

	}

	public boolean saveInstructions(String filename) {
		PrintStream fileOut;

		try {
			fileOut = IOFactory.createUTF8PrintStream(new FileOutputStream(filename));
		} catch (FileNotFoundException fnfe) {
			out.error("File " + filename + " not found!");
			return false;
		}

		for (String instruction : script) {
			fileOut.println(instruction);
			out.logln("Instruction " + instruction + " written into file " + filename);
		}
		fileOut.close();
		return true;
	}

	public boolean loadInstructions(String filename) {
		BufferedReader fileIn;

		try {
			fileIn = new BufferedReader(IOFactory.createUTF8ISReader(new FileInputStream(filename)));
		} catch (FileNotFoundException fnfe) {
			out.error("File " + filename + " not found!");
			return false;
		}

		try {
			String nextLine = fileIn.readLine();
			while (nextLine != null) {
				if (!nextLine.startsWith("#") && !nextLine.replaceAll("\\s", "").equals("")) {
					if (!processCommand(nextLine)) {
						return false;
					}
				}
				nextLine = fileIn.readLine();
			}
		} catch (IOException ioe) {
			out.error("I/O error while reading from file " + filename + "!");
			return false;
		} finally {
			try {
				fileIn.close();
			} catch (IOException ioe) {
				out.error("I/O error while closing file " + filename + "!");
				return false;
			}
		}



		return true;
	}

	public void showAnnotations() {
		new NumberedIFCAnnotationDumper(out.getPrintStream()).dumpAnnotations(ifcAnalysis
				.getAnnotations());
	}

	public void showUsageOutline() {
		out.logln("Available commands:");
		for (String cmdName : repo.getCommands()) {
			out.logln(repo.getHelpMessage(cmdName));
		}
		out.logln("Or invoke 'quit' to exit.");
	}

	public boolean loadAnnotations(String fileName) {
		ifcAnalysis.clearAllAnnotations();
		Set<IFCAnnotation> annotations;
		try {
			annotations = new IFCAnnotationReader(methodSelector, new FileInputStream(fileName)).readAnnotations();
		} catch (FileNotFoundException fnfe) {
			out.error("File " + fileName + " not found!");
			return false;
		} catch (IOException ioe) {
			out.error("I/O error while reading annotations from file " + fileName + "!");
			return false;
		} catch (InvalidAnnotationFormatException iafe) {
			out.error("Annotation " + iafe.getInvalidAnnotation() + " in file " + fileName + " has illegal format!");
			return false;
		} catch (MethodNotFoundException mnfe) {
			out.error("Annotation " + mnfe.getAnnotation() + " in file " + fileName + " refers to missing method "
					+ mnfe.getMethodName() + "!");
			return false;
		}

		for (IFCAnnotation annotation : annotations) {
			ifcAnalysis.addAnnotation(annotation);
		}
		showAnnotations();
		return true;
	}

	public boolean saveAnnotations(String fileName) {
		IFCAnnotationDumper dumper;

		try {
			dumper = new IFCAnnotationDumper(IOFactory.createUTF8PrintStream(new FileOutputStream(fileName)));
		} catch (FileNotFoundException e) {
			out.error("File " + fileName + " not found!");
			return false;
		}

		dumper.dumpAnnotations(ifcAnalysis.getAnnotations());
		return true;
	}

	public void setProgressMonitor(IProgressMonitor progress) {
		this.monitor = progress;
	}

	public synchronized boolean buildSDG() {
		return buildSDG(false, MHPType.NONE, ExceptionAnalysis.INTERPROC);
	}

	private boolean buildSDG(boolean computeInterference, MHPType mhpType, ExceptionAnalysis exA) {
		if (!loc.entrySelected()) {
			out.info("No entry method selected. Select entry method first!");
			return false;
		} else {

			SDGProgram program;

			try {
				SDGConfig config = new SDGConfig(classPath, loc.getActiveEntry().toBCString(), stubsPath);
				config.setComputeInterferences(computeInterference);
				config.setMhpType(mhpType);
				config.setExceptionAnalysis(exA);
				config.setPointsToPrecision(pointsTo);
				config.setFieldPropagation(FieldPropagation.OBJ_GRAPH_SIMPLE_PROPAGATION);
				program = SDGProgram.createSDGProgram(config, out.getPrintStream(), monitor);
			} catch (ClassHierarchyException e) {
				out.error(e.getMessage());
				return false;
			} catch (IOException e) {
				out.error("\nI/O problem during sdg creation: " + e.getMessage());
				return false;
			} catch (CancelException e) {
				out.error("\nSDG creation cancelled.");
				return false;
			} catch (UnsoundGraphException e) {
				out.error("\nResulting SDG is not sound: " + e.getMessage());
				return false;
			}

			setSDGProgram(program);
			return true;
		}
	}

	public synchronized boolean saveSDG(String path) {
		if (ifcAnalysis == null || ifcAnalysis.getProgram() == null) {
			out.info("No active program.");
		} else {
			BufferedOutputStream bOut;

			try {
				bOut = new BufferedOutputStream(new FileOutputStream(path));
			} catch (FileNotFoundException e) {
				out.error("I/O problem while writing sdg into file " + path + "!");
				return false;
			}
			SDGSerializer.toPDGFormat(ifcAnalysis.getProgram().getSDG(), bOut);
		}

		return true;
	}

	public synchronized boolean loadSDG(String path) {

		SDG sdg;

		try {
			sdg = SDG.readFrom(path, new SecurityNodeFactory());
		} catch (IOException e) {
			out.error("I/O error while reading sdg from file " + path);
			return false;
		}

		setSDG(sdg);
		// sdgFile = path;
		return true;
	}

	public void reset() {
		ifcAnalysis.clearAllAnnotations();
		methodSelector.reset();
	}

	public Collection<? extends IViolation<SecurityNode>> getLastAnalysisResult() {
		return lastAnalysisResult;
	}

	public TObjectIntMap<IViolation<SDGProgramPart>> getLastAnalysisResultGrouped() {
		return groupedIFlows;
	}

	public boolean doIFC(IFCType ifcType, boolean timeSens) {
		if (ifcAnalysis == null || ifcAnalysis.getProgram() == null) {
			out.info("No program to analyze.");
			return false;
		} else {
			ifcAnalysis.setTimesensitivity(timeSens);
			out.logln("Performing IFC - Analysis type: " + ifcType);
			Collection<? extends IViolation<SecurityNode>> vios = ifcAnalysis.doIFC(ifcType);

			lastAnalysisResult.clear();
			lastAnalysisResult.addAll(vios);

			groupedIFlows.clear();

			if (lastAnalysisResult.size() > 0) {

				groupedIFlows = ifcAnalysis.groupByPPPart(vios);
				out.logln("done, found " + groupedIFlows.size() + " security violation(s):");
				Set<String> output = new TreeSet<String>();
				for (IViolation<SDGProgramPart> vio : groupedIFlows.keySet()) {
					output.add(String
							.format("Security violation: %s (internal: %d security violations on the SDG node level)",
									vio.toString(), groupedIFlows.get(vio)));
				}
				for (String s : output) {
					out.logln(s);
				}
			} else {
				out.logln("No violations found.");
			}
			return true;
		}
	}


    public Set<edu.kit.joana.api.sdg.SDGInstruction> getLastComputedChop() {
        return this.lastComputedChop;
    }

    public boolean createChop(final String source, final String sink) {
        final SDGProgramPart sourceP = getProgramPartFromSelectorString(source, false);
        final SDGProgramPart sinkP = getProgramPartFromSelectorString(sink, false);
        return createChop(sourceP, sinkP);
    }

    public boolean createChop(final SDGProgramPart source, final SDGProgramPart sink) {
        final SDGProgram program = getProgram();
        
        if (source == null) {
            out.info("Chop: Source is null - aborted");
            return false;
        }
        if (sink == null) {
            out.info("Chop: Sink is null - aborted");
            return false;
        }
        if (program == null) {
            out.info("No program loaded");
            return false;
        }

        this.out.logln("Calculating Chop from " + source + " to " + sink + "...");

        final Set<edu.kit.joana.api.sdg.SDGInstruction> chop = program.computeInstructionChop(source, sink);
        this.lastComputedChop = chop;

        out.logln("Chop from " + source + " to " + sink + " is:");
        for (final edu.kit.joana.api.sdg.SDGInstruction inst : chop) {
            out.logln("  " + inst);
        }
        return true;
    }

	public static String convertIFCType(IFCType ifcType) {
		switch (ifcType) {
		case CLASSICAL_NI:
			return IFCTYPE_CLASSICAL_NI;
		case LSOD:
			return IFCTYPE_LSOD;
		case RLSOD:
			return IFCTYPE_RLSOD;
		default:
			throw new IllegalStateException("not all cases handled by this method!");
		}
	}

	public boolean showClasses() {
		if (ifcAnalysis == null) {
			out.error("Load or build SDG first!");
			return false;
		}
		Collection<SDGClass> classes = ifcAnalysis.getProgram().getClasses();

		for (SDGClass cl : classes) {
			out.logln(cl.getDescription());
		}

		return true;
	}

	public void interactive() throws IOException {
		String nextCommand = null;
		boolean quit = false;
		while (!quit) {
			if (showPrompt) {
				out.log("> ");
			}
			nextCommand = in.readLine();
			if (nextCommand == null) {
				quit = true;
			} else if (isQuit(nextCommand)) {
				quit = true;
			} else {
				processCommand(nextCommand);
			}
		}
	}

	public void setShowPrompt(boolean showPrompt) {
		this.showPrompt = showPrompt;
	}

	public boolean isQuit(String cmd) {
		return CMD.QUIT.getName().equals(cmd);
	}

	public synchronized EntryLocator getEntryLocator() {
		return loc;
	}

	public String getClassPath() {
		return classPath;
	}

	public PointsToPrecision getPointsTo() {
		return pointsTo;
	}

	public ExceptionAnalysis getExceptionAnalysis() {
		return excAnalysis;
	}

	public boolean getComputeInterferences() {
		return computeInterference;
	}

	public MHPType getMHPType() {
		return mhpType;
	}

	public Collection<IFCAnnotation> getSources() {
		if (ifcAnalysis == null) {
			return new LinkedList<IFCAnnotation>();
		} else {
			return ifcAnalysis.getSources();
		}
	}

	public Collection<IFCAnnotation> getSinks() {
		if (ifcAnalysis == null) {
			return new LinkedList<IFCAnnotation>();
		} else {
			return ifcAnalysis.getSinks();
		}
	}

	public Collection<IFCAnnotation> getDeclassifications() {
		if (ifcAnalysis == null) {
			return new LinkedList<IFCAnnotation>();
		} else {
			return ifcAnalysis.getDeclassifications();
		}
	}

	public String getLatticeFile() {
		return latticeFile;
	}

	public SDG getSDG() {
		if (ifcAnalysis == null) {
			return null;
		} else {
			return ifcAnalysis.getProgram().getSDG();
		}
	}

	public void addListener(IFCConsoleListener l) {
		this.consoleListeners.add(l);
	}

	public IStaticLattice<String> getLattice() {
		return ifcAnalysis.getLattice();
	}

	public boolean canAnnotate(Collection<SDGProgramPart> selectedParts, AnnotationType type) {
		boolean ret = true;

		for (SDGProgramPart part : selectedParts) {
			if (!canAnnotate(part)) {
				ret = false;
			}
		}

		if (!ret) {
			Answer ans = out
					.question("At least one of the selected program parts is already annotated. Do you want to overwrite these annotations?");
			if (ans == Answer.YES) {
				return true;
			} else {
				return false;
			}
		} else {
			return true;
		}
	}

	public boolean canAnnotate(SDGProgramPart part) {
		return !ifcAnalysis.isAnnotated(part);
	}

	public void executionAborted(CMD cmd, String[] args, Throwable error) {
		List<String> argList;
		if (args.length == 1) {
			argList = Collections.<String> emptyList();
		} else {
			argList = Arrays.asList(args);
			argList = argList.subList(1, argList.size() - 1);
		}
		out.error("Execution of command " + cmd + " applied to arguments " + argList
				+ " aborted due to the following error: " + error);

		afterCommand(cmd, args);
	}

	/**
	 * Returns the program currently under analysis, if there is any. Returns
	 * {@code null} if no program was loaded.
	 *
	 * @return the program currently under analysis, {@code null} if there is
	 *         none
	 */
	public SDGProgram getProgram() {
		if (ifcAnalysis == null) {
			return null;
		} else {
			return ifcAnalysis.getProgram();
		}
	}
}
