/**
 * This file is part of the Joana IFC project. It is developed at the
 * Programming Paradigms Group of the Karlsruhe Institute of Technology.
 *
 * For further details on licensing please read the information at
 * http://joana.ipd.kit.edu or contact the authors.
 */
/*
 * Created on 29.03.2005
 *
 */
package edu.kit.joana.util.maps;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 
 * This class represents a map in which single keys are mapped to sets of values instead
 * of single values.<br>
 * 
 * @author Martin Mohr 
 *
 **/
public class MultiMap<K, T> {
	
	
	private Map<K, Set<T>> map = new HashMap<K, Set<T>>();
	
	
	/***
	 * Adds a value to the value set of the given key.
	 * @param key key to add value for
	 * @param value value to add to the value set of the given key
	 */
	public void addValue(K key, T value) {
		Set<T> vals;
		if (!map.containsKey(key)) {
			vals = new HashSet<T>();
			map.put(key, vals);
		} else {
			vals = map.get(key);
		}
		vals.add(value);
	}

	/**
	 * Returns all values associated to the given key. In particular, an empty set is returned, if
	 * the given key is not contained in this map.
	 * @param key key to retrieve value set for
	 * @return set of all values associated with key
	 */
	public Set<T> getAllValues(K key) {
		Set<T> vals = map.get(key);
		return vals == null ? new HashSet<T>() : vals;
	}

	public void clear() {
		map.clear();
	}

	@Override
	public String toString() {
		return map.toString();
	}
}
