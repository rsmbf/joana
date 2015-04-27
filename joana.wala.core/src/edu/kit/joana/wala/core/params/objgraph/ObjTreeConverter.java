/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.wala.core.params.objgraph;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;

import edu.kit.joana.wala.core.PDG;
import edu.kit.joana.wala.core.PDGEdge;
import edu.kit.joana.wala.core.PDGNode;
import edu.kit.joana.wala.core.SDGBuilder;

/**
 * Converts an object graph sdg into an object tree sdg, by copying shared parameter fields to separate nodes.
 *
 * @author Juergen Graf <juergen.graf@gmail.com>
 *
 */
public final class ObjTreeConverter {

	private final SDGBuilder sdg;

	private ObjTreeConverter(final SDGBuilder sdg) {
		this.sdg = sdg;
	}

	/**
	 * Converts an object graph sdg to an object tree sdg. This is expected to be run after object graph
	 * computation and before summary edge computation. However it should also work after summary edge computation.
	 */
	public static void convert(final SDGBuilder sdg, final IProgressMonitor progress) throws CancelException {
		final ObjTreeConverter objtree = new ObjTreeConverter(sdg);
		objtree.run(progress);
	}

	private void run(final IProgressMonitor progress) throws CancelException {
		final Map<PDG, TreeRoots> pdgRoots = computeFormalTrees(progress);

		for (final PDG caller : sdg.getAllPDGs()) {
			deleteOrigNodes(caller, pdgRoots.get(caller));

			for (final PDGNode call : caller.getCalls()) {
				final TreeRoots callRoots = computeActualTrees(caller, call);
				deleteOrigNodes(caller, callRoots);

				for (final PDG callee : sdg.getPossibleTargets(call)) {
					final TreeRoots calleeRoots = pdgRoots.get(callee);
					final Map<PDGNode, PDGNode> formToAct = findRootFormToAct(caller, call, callee);

					for (final PDGNode formRoot : calleeRoots.getRoots()) {
						final PDGNode actRoot = formToAct.get(formRoot);
						final TreeElem actTreeRoot = callRoots.getRootTree(actRoot);
						final TreeElem formTreeRoot = calleeRoots.getRootTree(formRoot);

						// connect nodes by matching structure
						connectChildren(caller, actTreeRoot, callee, formTreeRoot);
					}
				}
			}
		}
	}

	private void deleteOrigNodes(final PDG pdg, final TreeRoots treeRoots) {
		for (final PDGNode root : treeRoots.getRoots()) {
			final TreeElem rootElem = treeRoots.getRootTree(root);
			deleteChildOrigNodes(pdg, rootElem);
		}
	}

	private static void deleteChildOrigNodes(final PDG pdg, final TreeElem elem) {
		if (elem.hasChildren()) {
			for (final TreeElem child : elem.getChildren()) {
				final PDGNode toDelete = child.orig;
				if (pdg.containsVertex(toDelete)) {
					if (isFormalParam(toDelete) && isOutput(toDelete)) {
						final List<PDGNode> toDeleteChildren = new LinkedList<PDGNode>();

						for (final PDGEdge out : pdg.outgoingEdgesOf(toDelete)) {
							if (out.kind == PDGEdge.Kind.PARAMETER_OUT && pdg.getId() != out.to.getPdgId()) {
								toDeleteChildren.add(out.to);
							}
						}

						pdg.removeAllVertices(toDeleteChildren);
					} else if (isActualParam(toDelete) && isInput(toDelete)) {
						final List<PDGNode> toDeleteChildren = new LinkedList<PDGNode>();

						for (final PDGEdge in : pdg.outgoingEdgesOf(toDelete)) {
							if (in.kind == PDGEdge.Kind.PARAMETER_IN && pdg.getId() != in.to.getPdgId()) {
								toDeleteChildren.add(in.to);
							}
						}

						pdg.removeAllVertices(toDeleteChildren);
					}

					pdg.removeVertex(toDelete);
				}

				deleteChildOrigNodes(pdg, child);
			}
		}
	}

	private static void connectChildren(final PDG caller, final TreeElem act, final PDG callee, final TreeElem form) {
		if (act.hasChildren()) {
			for (final TreeElem actChild : act.getChildren()) {
				final TreeElem formChild = form.findChild(actChild);

				if (isInput(actChild.node)) {
					caller.addVertex(formChild.node);
					caller.addEdge(actChild.node, formChild.node, PDGEdge.Kind.PARAMETER_IN);
				} else {
					callee.addVertex(actChild.node);
					callee.addEdge(formChild.node, actChild.node, PDGEdge.Kind.PARAMETER_OUT);
				}

				connectChildren(caller, actChild, callee, formChild);
			}
		}
	}

	private TreeRoots computeActualTrees(final PDG pdg, final PDGNode call) {
		final TreeRoots callRoots = TreeRoots.createCallRoots(pdg, call);

		for (final PDGNode root : callRoots.getRoots()) {
			final TreeElem rootTree = callRoots.getRootTree(root);
			addChildrenToTree(pdg, rootTree);
		}

		return callRoots;
	}

	private Map<PDG, TreeRoots> computeFormalTrees(final IProgressMonitor progress) throws CancelException {
		final Map<PDG, TreeRoots> trees = new HashMap<PDG, TreeRoots>();

		for (final PDG pdg : sdg.getAllPDGs()) {
			MonitorUtil.throwExceptionIfCanceled(progress);

			final TreeRoots pdgRoots = TreeRoots.createFormalRoots(pdg);

			for (final PDGNode root : pdgRoots.getRoots()) {
				final TreeElem rootTree = pdgRoots.getRootTree(root);
				addChildrenToTree(pdg, rootTree);
			}

			trees.put(pdg, pdgRoots);
		}

		return trees;
	}

	private static void addChildrenToTree(final PDG pdg, final TreeElem tree) {
		for (final PDGNode child : findDirectChildren(pdg, tree.orig)) {
			final TreeElem treeChild = tree.findOnPathToRoot(child);

			if (treeChild == null) {
				final PDGNode newChild = copyNode(pdg, child);
				copyDepsFromTo(pdg, child, newChild);
				pdg.addEdge(tree.node, newChild, PDGEdge.Kind.PARAM_STRUCT);
				final TreeElem newTreeChild = new TreeElem(tree, child, newChild);
				addChildrenToTree(pdg, newTreeChild);
			} else {
				pdg.addEdge(tree.node, treeChild.node, PDGEdge.Kind.PARAM_STRUCT);
			}
		}
	}

	private static final class TreeRoots {
		private final Map<PDGNode, TreeElem> roots = new HashMap<PDGNode, TreeElem>();;

		public static TreeRoots createFormalRoots(final PDG pdg) {
			final TreeRoots tr = new TreeRoots();

			final List<PDGNode> rootNodes = findFormRoots(pdg);
			for (final PDGNode r : rootNodes) {
				final TreeElem rootTree = new TreeElem(null, r, r);
				tr.roots.put(r, rootTree);
			}

			return tr;
		}

		public static TreeRoots createCallRoots(final PDG pdg, final PDGNode call) {
			final TreeRoots tr = new TreeRoots();

			final List<PDGNode> rootNodes = findCallRoots(pdg, call);
			for (final PDGNode r : rootNodes) {
				final TreeElem rootTree = new TreeElem(null, r, r);
				tr.roots.put(r, rootTree);
			}

			return tr;
		}

		private TreeRoots() {}

		public TreeElem getRootTree(final PDGNode n) {
			if (roots.containsKey(n)) {
				return roots.get(n);
			}

			return null;
		}

		public Set<PDGNode> getRoots() {
			return roots.keySet();
		}

	}

	private static final class TreeElem {
		public final TreeElem parent;
		public final PDGNode orig;
		public final PDGNode node;
		private List<TreeElem> children;

		private TreeElem(final TreeElem parent, final PDGNode orig, final PDGNode node) {
			this.parent = parent;
			this.orig = orig;
			this.node = node;

			if (parent != null) {
				parent.addChild(this);
			}
		}

		public boolean hasChildren() {
			return children != null && !children.isEmpty();
		}

		public TreeElem findChild(final TreeElem elem) {
			return getChild(elem.node.getBytecodeIndex(), elem.node.getBytecodeName(), isInput(elem.node));
		}

		public List<TreeElem> getChildren() {
			return children;
		}

		private void addChild(final TreeElem child) {
			if (children == null) {
				children = new LinkedList<TreeElem>();
			}

			children.add(child);
		}

		public TreeElem getChild(final int bcIndex, final String bcName, boolean isIn) {
			if (children != null) {
				for (final TreeElem ch : children) {
					final PDGNode chn = ch.node;
					if (chn.getBytecodeIndex() == bcIndex && bcName.equals(chn.getBytecodeName())
							&& ((isIn && isInput(chn)) || (!isIn && isOutput(chn)))) {
						return ch;
					}
				}
			}

			return null;
		}

		public TreeElem findOnPathToRoot(final PDGNode orig) {
			if (this.orig == orig) {
				return this;
			} else if (parent != null) {
				return parent.findOnPathToRoot(orig);
			}

			return null;
		}

//		public TreeElem findOnPathToRoot(final int bcIndex, final String bcName, boolean isIn) {
//			if (matches(bcIndex, bcName, isIn)) {
//				return this;
//			} else if (parent != null) {
//				return parent.findOnPathToRoot(bcIndex, bcName, isIn);
//			}
//
//			return null;
//		}
//
//		public boolean matches(final int bcIndex, final String bcName, boolean isIn) {
//			return node.getBytecodeIndex() == bcIndex && bcName.equals(node.getBytecodeName())
//					&& ((isIn && isInput(node)) || (!isIn && isOutput(node)));
//		}

	}

	private static boolean isInput(final PDGNode n) {
		switch (n.getKind()) {
		case ACTUAL_IN:
		case FORMAL_IN:
			return true;
		default: // nothing to do here
		}

		return false;
	}

	private static boolean isOutput(final PDGNode n) {
		switch (n.getKind()) {
		case ACTUAL_OUT:
		case FORMAL_OUT:
			return true;
		case EXIT:
			return n.getTypeRef() != TypeReference.Void;
		default: // nothing to do here
		}

		return false;
	}

	private static Map<PDGNode, PDGNode> findRootFormToAct(final PDG caller, final PDGNode call, final PDG callee) {
		final Map<PDGNode, PDGNode> form2act = new HashMap<PDGNode, PDGNode>();

		final List<PDGNode> acts = findAllParameterNodes(caller, call);
		final List<PDGNode> forms = findAllParameterNodes(callee, callee.entry);

		for (final PDGNode a : acts) {
			if (a.getKind() == PDGNode.Kind.ACTUAL_IN) {
				for (final PDGEdge e : caller.outgoingEdgesOf(a)) {
					if (e.kind == PDGEdge.Kind.PARAMETER_IN && e.to.getPdgId() == callee.getId()) {
						assert !form2act.containsKey(e.to);
						form2act.put(e.to, a);
					}
				}
			}
		}

		for (final PDGNode f : forms) {
			if (f.getKind() != PDGNode.Kind.FORMAL_IN) {
				for (final PDGEdge e : callee.outgoingEdgesOf(f)) {
					if (e.kind == PDGEdge.Kind.PARAMETER_OUT
							&& (e.to.getPdgId() == caller.getId() && acts.contains(e.to))) {
						assert !form2act.containsKey(f);
						form2act.put(f, e.to);
					}
				}
			}
		}


		return form2act;
	}

	private static List<PDGNode> findFormRoots(final PDG pdg) {
		final List<PDGNode> roots = new LinkedList<PDGNode>();

		for (final PDGEdge e : pdg.outgoingEdgesOf(pdg.entry)) {
			if (e.kind == PDGEdge.Kind.PARAM_STRUCT	&& isFormalParam(e.to)) {
				roots.add(e.to);
			}
		}

		return roots;
	}

	private static List<PDGNode> findCallRoots(final PDG pdg, final PDGNode call) {
		final List<PDGNode> roots = new LinkedList<PDGNode>();

		for (final PDGEdge e : pdg.outgoingEdgesOf(call)) {
			if (e.kind == PDGEdge.Kind.PARAM_STRUCT	&& isActualParam(e.to)) {
				roots.add(e.to);
			}
		}

		return roots;
	}

	// Copy all except parameter-in/out and parameter structure edges.
	// And add to node to control flow
	private static void copyDepsFromTo(final PDG pdg, final PDGNode copyFrom, final PDGNode copyTo) {
		final List<PDGEdge> copyIncoming = new LinkedList<PDGEdge>();
		for (final PDGEdge in : pdg.incomingEdgesOf(copyFrom)) {
			switch (in.kind) {
			case CONTROL_FLOW:
			case CONTROL_FLOW_EXC:
			case PARAMETER_IN:
			case PARAMETER_OUT:
			case PARAM_STRUCT:
				break;
			default:
				copyIncoming.add(in);
				break;
			}
		}

		for (final PDGEdge inCopy : copyIncoming) {
			pdg.addEdge(inCopy.from, copyTo, inCopy.kind);
		}

		final List<PDGEdge> copyOutgoing = new LinkedList<PDGEdge>();
		final List<PDGEdge> outControlFlow = new LinkedList<PDGEdge>();
		for (final PDGEdge out : pdg.outgoingEdgesOf(copyFrom)) {
			switch (out.kind) {
			case CONTROL_FLOW:
			case CONTROL_FLOW_EXC:
				outControlFlow.add(out);
				break;
			case PARAMETER_IN:
			case PARAMETER_OUT:
			case PARAM_STRUCT:
				break;
			default:
				copyOutgoing.add(out);
				break;
			}
		}

		for (final PDGEdge outCopy : copyOutgoing) {
			pdg.addEdge(copyTo, outCopy.to, outCopy.kind);
		}

		pdg.removeAllEdges(outControlFlow);
		pdg.addEdge(copyFrom, copyTo, PDGEdge.Kind.CONTROL_FLOW);
		for (final PDGEdge cf : outControlFlow) {
			pdg.addEdge(copyTo, cf.to, cf.kind);
		}
	}

	private static List<PDGNode> findDirectChildren(final PDG pdg, final PDGNode n) {
		final List<PDGNode> children = new LinkedList<PDGNode>();

		for (final PDGEdge e : pdg.outgoingEdgesOf(n)) {
			if (e.kind == PDGEdge.Kind.PARAM_STRUCT) {
				children.add(e.to);
			}
		}

		return children;
	}

	private static List<PDGNode> findAllParameterNodes(final PDG pdg, final PDGNode n) {
		final List<PDGNode> params = new LinkedList<PDGNode>();

		for (final PDGEdge e : pdg.outgoingEdgesOf(n)) {
			if (e.kind == PDGEdge.Kind.CONTROL_DEP_EXPR && (isActualParam(e.to) || isFormalParam(e.to))) {
				params.add(e.to);
			}
		}

		return params;
	}

	private static boolean isActualParam(final PDGNode n) {
		switch (n.getKind()) {
		case ACTUAL_IN:
		case ACTUAL_OUT:
			return true;
		default: // nothing to do here
		}

		return false;
	}

	private static boolean isFormalParam(final PDGNode n) {
		switch (n.getKind()) {
		case FORMAL_IN:
		case FORMAL_OUT:
			return true;
		case EXIT:
			return n.getTypeRef() != TypeReference.Void;
		default: // nothing to do here
		}

		return false;
	}

	private static PDGNode copyNode(final PDG pdg, final PDGNode toCopy) {
		final PDGNode copy = pdg.createNode(toCopy.getLabel(), toCopy.getKind(), toCopy.getTypeRef());
		copy.setBytecodeIndex(toCopy.getBytecodeIndex());
		copy.setBytecodeName(toCopy.getBytecodeName());
		copy.setDebug(toCopy.getDebug());
		copy.setSourceLocation(toCopy.getSourceLocation());
		copy.setParameterField(toCopy.getParameterField());

		return copy;
	}

}
