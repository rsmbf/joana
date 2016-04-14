package main;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.kit.joana.api.sdg.SDGProgramPart;

public class LineInterferencesPoints {
	private Map<Integer,Set<SDGProgramPart>> sourcesLeftMap;
	private Map<Integer,Set<SDGProgramPart>> sourcesRightMap;
	private Set<SDGProgramPart> sinks;
	
	public LineInterferencesPoints()
	{
		sourcesLeftMap = new HashMap<Integer,Set<SDGProgramPart>>();
		sourcesRightMap = new HashMap<Integer, Set<SDGProgramPart>>();
		sinks = new HashSet<SDGProgramPart>();
	}

	public void addNewInterference(SDGProgramPart newSink,
			Map<Integer, Set<SDGProgramPart>> newSourcesLeftMap, Map<Integer, Set<SDGProgramPart>> newSourcesRightMap) {
		sinks.add(newSink);
		addSourcesMap(sourcesLeftMap, newSourcesLeftMap);
		addSourcesMap(sourcesRightMap, newSourcesRightMap);
	}

	private void addSourcesMap(Map<Integer, Set<SDGProgramPart>> sourcesMap,Map<Integer, Set<SDGProgramPart>> newSourcesMap) {
		for(Integer line : newSourcesMap.keySet())
		{			
			if(sourcesMap.containsKey(line))
			{
				sourcesMap.get(line).addAll(newSourcesMap.get(line));
			}else{
				sourcesMap.put(line, newSourcesMap.get(line));
			}
		}
	}

	public Map<Integer, Set<SDGProgramPart>> getSourcesLeftMap() {
		return sourcesLeftMap;
	}

	public Map<Integer, Set<SDGProgramPart>> getSourcesRightMap() {
		return sourcesRightMap;
	}

	public Set<SDGProgramPart> getSinks() {
		return sinks;
	}
}
