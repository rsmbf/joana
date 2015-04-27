/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.ifc.sdg.mhpoptimization;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.kit.joana.ifc.sdg.graph.SDGEdge;
import edu.kit.joana.ifc.sdg.graph.SDGNode;
import edu.kit.joana.ifc.sdg.graph.slicer.graph.CFG;
import edu.kit.joana.ifc.sdg.graph.slicer.graph.DynamicContextManager;
import edu.kit.joana.ifc.sdg.graph.slicer.graph.DynamicContextManager.DynamicContext;
import edu.kit.joana.ifc.sdg.graph.slicer.graph.building.GraphFolder;
import edu.kit.joana.ifc.sdg.mhpoptimization.loopdet.LoopDetermination;
import edu.kit.joana.ifc.sdg.mhpoptimization.loopdet.PreciseLoopDetermination;
import edu.kit.joana.ifc.sdg.mhpoptimization.loopdet.SimpleLoopDetermination;
import edu.kit.joana.util.Log;
import edu.kit.joana.util.Logger;

/**
 * Implements a thread allocation analysis. It is based on the thread allocation
 * analysis of Eric Ruf.
 * 
 * -- Created on October 18, 2006
 * 
 * @author Dennis Giffhorn
 */
public class ThreadAllocationAnalysis {

	public static enum LoopDetPrec {
		SIMPLE, PRECISE;
	}
	
	public static enum SpawnNumber {
		ONLY_ONCE, INDEFINITE;
	}

	private final LoopDetPrec loopDetPrec;

	/** A CFG and its contexts. */
	private CFG cfg;
	private DynamicContextManager conMan;

	/** used to determine whether a spawn happens in a loop */
	private LoopDetermination loopDet;

	/** maps thread entries to their possible contexts */
	private Map<SDGNode, Collection<DynamicContext>> run_thread;
	
	/** flattened version of the value set of the map run_thread (i.e. the collection all possible contexts of thread entries) */
	private Set<DynamicContext> threads;
	
	/** maps each thread entry context to the number of times it is possibly executed */
	private Map<DynamicContext, SpawnNumber> thread_amount; 

	/**
	 * Creates a new instance of ThreadAllocation
	 * 
	 * @param g
	 *            A SDG.
	 */
	public ThreadAllocationAnalysis(CFG cfg, LoopDetPrec prec) {
		this.cfg = cfg;
		
		// create the context manager
		conMan = new DynamicContextManager(cfg);
		this.loopDetPrec = prec;
		if (loopDetPrec == LoopDetPrec.SIMPLE) {
			this.loopDet = new SimpleLoopDetermination(GraphFolder.foldIntraproceduralSCC(cfg), conMan);
		} else {
			this.loopDet = new PreciseLoopDetermination(cfg);
		}
		
	}

	public Set<DynamicContext> getThreads() {
		return Collections.unmodifiableSet(threads);
	}

	public Map<DynamicContext, SpawnNumber> getThreadAmount() {
		return thread_amount;
	}

	/**
	 * Executes the thread allocation analysis.
	 */
	public void compute() {
		final Logger debug = Log.getLogger(Log.L_MHP_DEBUG);
		// determine all existing thread run methods
		// the List will contain their entry nodes
		List<SDGNode> runEntries = allRunMethods();
		debug.outln("run-method entries                : " + runEntries);

		// determine thread contexts
		run_thread = threadContexts(runEntries);
		threads = new HashSet<DynamicContext>();
		for (Collection<DynamicContext> l : run_thread.values()) {
			threads.addAll(l);
		}

		debug.outln("run-entries -> thread contexts : " + run_thread);
		debug.outln("threads : " + threads);

		// compute the number of threads
		thread_amount = computeNumberOfThreads();
		debug.outln("thread -> instances   : " + thread_amount);

		// compute the thread invocation structure
		HashMap<DynamicContext, List<DynamicContext>> str = invocationStructure();
		debug.outln("thread invocation structure:\n " + str);
	}

	private HashMap<DynamicContext, List<DynamicContext>> invocationStructure() {
		HashMap<DynamicContext, List<DynamicContext>> result = new HashMap<DynamicContext, List<DynamicContext>>();

		for (DynamicContext c : threads) {
			DynamicContext invokedBy = null;
			int diff = c.size();

			for (DynamicContext d : threads) {
				if (c == d)
					continue;
				if (d.isSuffixOf(c) && diff > (c.size() - d.size())) {
					invokedBy = d;
				}
			}

			if (invokedBy != null) {
				List<DynamicContext> invoked = result.get(invokedBy);
				if (invoked == null) {
					invoked = new LinkedList<DynamicContext>();
					result.put(invokedBy, invoked);
				}
				invoked.add(c);
			}
		}

		return result;
	}

	/**
	 * Computes the entries of all run methods in the program.
	 * 
	 * @return A list with the found entry nodes.
	 */
	private List<SDGNode> allRunMethods() {
		List<SDGNode> result = new LinkedList<SDGNode>();
		// traverse all fork edges to find the entries
		for (SDGEdge fork : cfg.edgeSet()) {
			if (fork.getKind() != SDGEdge.Kind.FORK)
				continue;

			if (!result.contains(fork.getTarget())) {
				result.add(fork.getTarget());
			}
		}

		return result;
	}

	private HashMap<SDGNode, Collection<DynamicContext>> threadContexts(List<SDGNode> runEntries) {
		HashMap<SDGNode, Collection<DynamicContext>> tc = new HashMap<SDGNode, Collection<DynamicContext>>();

		for (SDGNode run : runEntries) {
			Collection<DynamicContext> cons = conMan.getExtendedContextsOf(run);
			tc.put(run, cons);
		}

		return tc;
	}

	private Map<DynamicContext, SpawnNumber> computeNumberOfThreads() {
		HashMap<DynamicContext, SpawnNumber> result = new HashMap<DynamicContext, SpawnNumber>();
		List<DynamicContext> remainingThreads = new LinkedList<DynamicContext>();

		// search for recursive calls in the contexts
		for (DynamicContext thread : threads) {
			boolean recursive = false;

			for (SDGNode n : thread.getCallStack()) {
				if (n.getId() < 0) {
					result.put(thread, SpawnNumber.INDEFINITE);
					recursive = true;
				}
			}

			if (!recursive) {
				remainingThreads.add(thread);
			}
		}

		// search for loops in the TCFG
		for (DynamicContext thread : remainingThreads) {
			if (isInALoop(thread)) {
				result.put(thread, SpawnNumber.INDEFINITE);
			} else {
				result.put(thread, SpawnNumber.ONLY_ONCE);
			}
		}

		// handle recursive thread generation
		LinkedList<DynamicContext> recursiveThreads = new LinkedList<DynamicContext>();
		LinkedList<DynamicContext> refinedThreads = new LinkedList<DynamicContext>();
		for (DynamicContext thread : threads) {
			if (thread.top() != null && thread.top().getId() < 0) {
				// this thread recursively invokes itself (directly or
				// indirectly)
				recursiveThreads.add(thread);

				DynamicContext rootOfTheRecursion = thread.copy();
				rootOfTheRecursion.pop();
				result.put(rootOfTheRecursion, SpawnNumber.INDEFINITE);
				result.remove(thread);
				refinedThreads.add(rootOfTheRecursion);
			}
		}
		threads.removeAll(recursiveThreads);
		threads.addAll(refinedThreads);

		return result;
	}

	private boolean isInALoop(DynamicContext thread) {
		return loopDet.isInALoop(thread);
	}
}
