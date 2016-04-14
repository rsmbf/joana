package main;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.kit.joana.api.sdg.SDGCallPart;
import edu.kit.joana.api.sdg.SDGProgramPart;

public class InterferencePoint {
	private SDGProgramPart sink;//sink inst other
	private Map<Integer, Set<SDGProgramPart>> sourcesLeftMap;//[source inst left]
	private Map<Integer, Set<SDGProgramPart>> sourcesRightMap;//[source inst right]
	private Map<SDGProgramPart, Integer> parts_map;
	
	public InterferencePoint(SDGProgramPart sdgProgramPart, Map<SDGProgramPart, Integer> parts_map)
	{
		this.sink = sdgProgramPart;
		this.parts_map = parts_map;
		sourcesLeftMap = new HashMap<Integer, Set<SDGProgramPart>>();
		sourcesRightMap = new HashMap<Integer, Set<SDGProgramPart>>();
	}
	
	public void addSourceLeft(SDGProgramPart source)
	{
		addSource(sourcesLeftMap, source);
	}
	
	public void addSourceRight(SDGProgramPart source)
	{
		addSource(sourcesRightMap, source);
	}
	
	public void addSource(Map<Integer, Set<SDGProgramPart>> sourcesMap,SDGProgramPart source)
	{
		Set<SDGProgramPart> sources;
		int sourceLine = parts_map.get(getSource(source));
		if(sourcesMap.containsKey(sourceLine)){
			sources = sourcesMap.get(sourceLine);
		}else{
			sources = new HashSet<SDGProgramPart>();
			sourcesMap.put(sourceLine, sources);
		}
		sources.add(source);
	}
	
	public Map<Integer, Set<SDGProgramPart>> getSourcesLeftMap()
	{
		return sourcesLeftMap;
	}
	
	public Map<Integer,Set<SDGProgramPart>> getSourcesRightMap()
	{
		return sourcesRightMap;
	}
	
	private static SDGProgramPart getSource(SDGProgramPart source)
	{		
		SDGProgramPart filteredSource;
		if(source instanceof SDGCallPart)
		{
			filteredSource = ((SDGCallPart) source).getOwningCall();
		}else{
			filteredSource = source;
		}
		return filteredSource;
	}
}
