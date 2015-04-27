/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
package edu.kit.joana.wala.util.pointsto;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.impl.DelegatingContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import edu.kit.joana.wala.util.NotImplementedException;

/**
 * CallGraph builder for the object sensitive points-to analysis
 *
 * @author Juergen Graf <graf@kit.edu>
 *
 */
public class ObjSensZeroXCFABuilder extends ZeroXCFABuilder {

	public interface MethodFilter {
		/**
		 * Decides for which methods an object sensitive points-to analysis should be engaged. A one level call string
		 * sensitivity is used as a fallback. Note that static methods have no receiver, so they cannot be
		 * analyzed with object sensitivity. Therefore no static method will be passed to this interface.
		 * The decision whether to engage object sensitivity can be made dependent on the calling context, i.e. the cg node of
		 * the caller.
		 * @param caller the context in which the method is called
		 * @param m The method that may be analyzed in an object sensitive context.
		 * @return <tt>true</tt> if calls to the method should be analyzed with object sensitivity, <tt>false</tt>
		 * if a one level call string sensitivity should be used.
		 */
		public boolean engageObjectSensitivity(CGNode caller, IMethod m);

		/**
		 * Decides for a method where {@link #engageObjectSensitivity(IMethod)} returns {@code true}, whether
		 * the object creation history context should be restricted to one level.<br>
		 * This has the effect that all methods which may be called from m are analyzed in a more coarse-grained
		 * context.
		 * @param m a method for which {@link #engageObjectSensitivity(IMethod)} returns {@code true}.
		 * @return {@code true}, if the object creation history context should be restricted to one level and
		 * in effect all methods which may be called from the given method are explored in a more coarse-grained
		 * context.
		 */
		public boolean restrictToOneLevelObjectSensitivity(IMethod m);
		
		public int getFallbackCallsiteSensitivity();
	}
	
	public static class DefaultMethodFilter implements MethodFilter {

		@Override
		public boolean engageObjectSensitivity(final CGNode caller, final IMethod m) {
			// use context sensitivity for all methods as default
			return true;
		}

		@Override
		public boolean restrictToOneLevelObjectSensitivity(final IMethod m) {
			// use unlimited object-sensitivity only for application code.
			return m.getDeclaringClass().getClassLoader().getReference().getName() != ClassLoaderReference.Application.getName();
		}

		@Override
		public int getFallbackCallsiteSensitivity() {
			return 1;
		}
		
	}

	public ObjSensZeroXCFABuilder(final IClassHierarchy cha, final ExtendedAnalysisOptions options, final AnalysisCache cache,
			final ContextSelector objSensSelector, final SSAContextInterpreter appContextInterpreter,
			final int instancePolicy) {
		super(cha, options, cache, objSensSelector, appContextInterpreter, instancePolicy);
	}

	@Override
	protected ZeroXInstanceKeys makeInstanceKeys(final IClassHierarchy cha,	final AnalysisOptions options,
			final SSAContextInterpreter contextInterpreter,	final int instancePolicy) {
		return new ObjSensInstanceKeys((ExtendedAnalysisOptions) options, cha, contextInterpreter, instancePolicy);
	}

    public static ZeroXCFABuilder make(final IClassHierarchy cha, final ExtendedAnalysisOptions options,
			final AnalysisCache cache, final ContextSelector appContextSelector,
			final SSAContextInterpreter appCtxInterp, final int instancePolicy) throws IllegalArgumentException {
        return make(cha, options, cache, appContextSelector, null, appCtxInterp, instancePolicy);
    }

	public static ZeroXCFABuilder make(final IClassHierarchy cha, final ExtendedAnalysisOptions options,
			final AnalysisCache cache, final ContextSelector appContextSelector, final ContextSelector additionalContextSelector,
			final SSAContextInterpreter appCtxInterp, final int instancePolicy) throws IllegalArgumentException {
		if (options == null) {
			throw new IllegalArgumentException("options == null");
		}
	   
		final ObjSensContextSelector objSensSelector = new ObjSensContextSelector(appContextSelector, options.filter);
        final ContextSelector contextSelector;
        
        if (additionalContextSelector != null) {
            contextSelector = new DelegatingContextSelector(objSensSelector, additionalContextSelector);
        } else {
            contextSelector = objSensSelector;
        }

		return new ObjSensZeroXCFABuilder(cha, options, cache, contextSelector, appCtxInterp, instancePolicy);
	}


	public static SSAPropagationCallGraphBuilder make(AnalysisOptions options, AnalysisCache cache, IClassHierarchy cha,
			ClassLoader cl, AnalysisScope scope, String[] xmlFiles, byte instancePolicy) throws IllegalArgumentException {
		throw new NotImplementedException("This cannot be used to create an object sensitive analysis.");
	}

	public static ZeroXCFABuilder make(IClassHierarchy cha, AnalysisOptions options, AnalysisCache cache,
			ContextSelector appContextSelector, SSAContextInterpreter appContextInterpreter, int instancePolicy)
			throws IllegalArgumentException {
		throw new NotImplementedException("This cannot be used to create an object sensitive analysis.");
	}

}
