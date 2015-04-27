/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.api.example;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.NullProgressMonitor;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;

import edu.kit.joana.api.IFCAnalysis;
import edu.kit.joana.api.lattice.BuiltinLattices;
import edu.kit.joana.api.sdg.SDGConfig;
import edu.kit.joana.api.sdg.SDGProgram;
import edu.kit.joana.api.sdg.SDGProgramPart;
import edu.kit.joana.ifc.sdg.core.SecurityNode;
import edu.kit.joana.ifc.sdg.core.violations.IViolation;
import edu.kit.joana.ifc.sdg.graph.SDGSerializer;
import edu.kit.joana.ifc.sdg.util.JavaMethodSignature;
import edu.kit.joana.util.Stubs;
import edu.kit.joana.wala.core.SDGBuilder.ExceptionAnalysis;
import edu.kit.joana.wala.core.SDGBuilder.PointsToPrecision;
import gnu.trove.map.TObjectIntMap;

/**
 * TODO: @author Add your name here.
 */
public class FileReadExample {

	public static void main(String[] args) throws ClassHierarchyException, IOException, UnsoundGraphException,
			CancelException {

		/**
		 * the class path is either a directory or a jar containing all the
		 * classes of the program which you want to analyze
		 */
		String classPath = "/Users/rodrigoandrade/Documents/workspaces/Doutorado/wala/SimpleExamples/bin/";
		
		/**
		 * the entry method is the main method which starts the program you want
		 * to analyze
		 */
		JavaMethodSignature entryMethod = JavaMethodSignature.mainMethodOfClass("file.FileExample");

		/**
		 * For multi-threaded programs, it is currently neccessary to use the
		 * jdk 1.4 stubs
		 */
		SDGConfig config = new SDGConfig(classPath, entryMethod.toBCString(), Stubs.JRE_14);

		/**
		 * compute interference edges to model dependencies between threads (set
		 * to false if your program does not use threads)
		 */
		config.setComputeInterferences(false);

		/**
		 * additional MHP analysis to prune interference edges (does not matter
		 * for programs without multiple threads)
		 */
		// config.setMhpType(MHPType.PRECISE);

		/**
		 * precision of the used points-to analysis - INSTANCE_BASED is a good
		 * value for simple examples
		 */
		config.setPointsToPrecision(PointsToPrecision.UNLIMITED_OBJECT_SENSITIVE);

		/**
		 * exception analysis is used to detect exceptional control-flow which
		 * cannot happen
		 */
		config.setExceptionAnalysis(ExceptionAnalysis.INTERPROC);

		/** build the PDG */
		SDGProgram program = SDGProgram.createSDGProgram(config, System.out, new NullProgressMonitor());

		/** optional: save PDG to disk */
		SDGSerializer.toPDGFormat(program.getSDG(), new FileOutputStream("/Users/rodrigoandrade/Dropbox/Temp/SDG.pdg"));

		IFCAnalysis ana = new IFCAnalysis(program);
		/** annotate sources and sinks */
		// for example: fields
		ana.addSourceAnnotation(program.getPart("file.FileExample.password"), BuiltinLattices.STD_SECLEVEL_HIGH);
		ana.addSinkAnnotation(program.getMethod("file.FileExample.readFile()V"),
				BuiltinLattices.STD_SECLEVEL_LOW);

		/** run the analysis */
		Collection<? extends IViolation<SecurityNode>> result = ana.doIFC();
		TObjectIntMap<IViolation<SDGProgramPart>> resultByProgramPart = ana.groupByPPPart(result);
		System.out.println(resultByProgramPart);
		/** do something with result */
	}
}
