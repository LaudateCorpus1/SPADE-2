package spade.storage.neo4j;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import spade.core.AbstractEdge;
import spade.core.AbstractVertex;
import spade.query.quickgrail.core.QueryEnvironment;
import spade.query.quickgrail.core.QueryInstructionExecutor;
import spade.query.quickgrail.core.Resolver.PredicateOperator;
import spade.query.quickgrail.instruction.CollapseEdge;
import spade.query.quickgrail.instruction.CreateEmptyGraph;
import spade.query.quickgrail.instruction.CreateEmptyGraphMetadata;
import spade.query.quickgrail.instruction.DistinctifyGraph;
import spade.query.quickgrail.instruction.EraseSymbols;
import spade.query.quickgrail.instruction.EvaluateQuery;
import spade.query.quickgrail.instruction.ExportGraph;
import spade.query.quickgrail.instruction.GetAdjacentVertex;
import spade.query.quickgrail.instruction.GetEdge;
import spade.query.quickgrail.instruction.GetEdgeEndpoint;
import spade.query.quickgrail.instruction.GetLineage;
import spade.query.quickgrail.instruction.GetLink;
import spade.query.quickgrail.instruction.GetShortestPath;
import spade.query.quickgrail.instruction.GetSubgraph;
import spade.query.quickgrail.instruction.GetVertex;
import spade.query.quickgrail.instruction.InsertLiteralEdge;
import spade.query.quickgrail.instruction.InsertLiteralVertex;
import spade.query.quickgrail.instruction.IntersectGraph;
import spade.query.quickgrail.instruction.LimitGraph;
import spade.query.quickgrail.instruction.ListGraphs;
import spade.query.quickgrail.instruction.OverwriteGraphMetadata;
import spade.query.quickgrail.instruction.SetGraphMetadata;
import spade.query.quickgrail.instruction.StatGraph;
import spade.query.quickgrail.instruction.SubtractGraph;
import spade.query.quickgrail.instruction.UnionGraph;
import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.storage.Neo4j;

public class Neo4jInstructionExecutor extends QueryInstructionExecutor{

	private final Neo4j storage;
	private final Neo4jQueryEnvironment neo4jQueryEnvironment;
	
	public Neo4jInstructionExecutor(Neo4j storage, Neo4jQueryEnvironment neo4jQueryEnvironment){
		this.storage = storage;
		this.neo4jQueryEnvironment = neo4jQueryEnvironment;
		if(this.neo4jQueryEnvironment == null){
			throw new IllegalArgumentException("NULL Query Environment");
		}
		if(this.storage == null){
			throw new IllegalArgumentException("NULL storage");
		}
	}
	
	public final QueryEnvironment getQueryEnvironment(){
		return neo4jQueryEnvironment;
	}
	
	@Override
	public void insertLiteralEdge(InsertLiteralEdge instruction){
		// TODO Auto-generated method stub
	}

	@Override
	public void insertLiteralVertex(InsertLiteralVertex instruction){
		// TODO Auto-generated method stub
	}

	@Override
	public void createEmptyGraphMetadata(CreateEmptyGraphMetadata instruction){
		// TODO Auto-generated method stub
	}

	@Override
	public void overwriteGraphMetadata(OverwriteGraphMetadata instruction){
		// TODO Auto-generated method stub
	}

	@Override
	public void setGraphMetadata(SetGraphMetadata instruction){
		// TODO Auto-generated method stub
	}
	
	

	@Override
	public void createEmptyGraph(CreateEmptyGraph instruction){
		neo4jQueryEnvironment.dropVertexLabels(instruction.graph.name);
		neo4jQueryEnvironment.dropEdgeSymbol(instruction.graph.name);
	}

	@Override
	public void distinctifyGraph(DistinctifyGraph instruction){
		unionGraph(new UnionGraph(instruction.targetGraph, instruction.sourceGraph));
	}

	@Override
	public void eraseSymbols(EraseSymbols instruction){
		for(String symbol : instruction.getSymbols()){
			neo4jQueryEnvironment.eraseGraphSymbol(symbol);
			neo4jQueryEnvironment.eraseGraphMetadataSymbol(symbol);
		}
	}

	private String buildComparison(
			String annotationKey, PredicateOperator operator,
			String annotationValue){
		String query = "`" + annotationKey + "` ";
		switch(operator){
			case EQUAL: query += "="; break;
			case GREATER: query += ">"; break;
			case GREATER_EQUAL: query += ">="; break;
			case LESSER: query += "<"; break;
			case LESSER_EQUAL: query += "<="; break;
			case NOT_EQUAL: query += "<>"; break;
			case REGEX: query += "=~"; break;
			case LIKE:{
				query += "=~";
				annotationValue = annotationValue.replace("%", ".*");
			}
			break;
			default: throw new RuntimeException("Unexpected comparison operator");
		}
		query += " '" + annotationValue + "'";
		return query;
	}
	
	@Override
	public void getVertex(GetVertex instruction){
		String query = "";
		query += "match (v:" + instruction.subjectGraph.name + ")";
		if(instruction.hasArguments()){
			query += " where v." + buildComparison(instruction.annotationKey, instruction.operator, instruction.annotationValue);
		}
		query += " set v:" + instruction.targetGraph.name + ";";
 		storage.executeQuery(query);
	}

	@Override
	public ResultTable evaluateQuery(EvaluateQuery instruction){
		List<Map<String, Object>> result = storage.executeQueryForSmallResult(instruction.nativeQuery);
		
		int cellCount = 0;
		
		ResultTable table = new ResultTable();
	
		Map<String, Object> treeMapForKeys = new TreeMap<String, Object>();
		for(Map<String, Object> map : result){
			Map<String, Object> treeMap = new TreeMap<String, Object>(map);
			ResultTable.Row row = new ResultTable.Row();
			for(String key : treeMap.keySet()){
				treeMapForKeys.put(key, null);
				row.add(treeMap.get(key));
				cellCount++;
			}
			table.addRow(row);
		}
		
		Schema schema = new Schema();
		if(cellCount == 0){
			schema.addColumn("NO RESULT!", StringType.GetInstance());
		}else{
			for(String key : treeMapForKeys.keySet()){
				schema.addColumn(key, StringType.GetInstance());
			}
		}
		
		table.setSchema(schema);
		return table;
	}

	private String buildSubqueryForUpdatingEdgeSymbols(String edgeAlias, String targetGraphName){
		final String edgeProperty = edgeAlias + ".`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		targetGraphName = "'," + targetGraphName + ",'";
		String query = "set " + edgeProperty + " = "
				+ "case "
				+ "when not exists(" + edgeProperty + ") then " + targetGraphName + " " // set
				+ "when " + edgeProperty + " contains " + targetGraphName + " then " + edgeProperty + " " // leave as is
				+ "else " + edgeProperty + " + " + targetGraphName + " end"; // append
		return query;
	}
	
	@Override
	public void collapseEdge(CollapseEdge instruction){
		String fieldsString = "";
		int xxx = 0;
		for(String field : instruction.getFields()){
			fieldsString += "e.`" + field + "` as x" + (xxx++) + " , ";
		}
		if(!fieldsString.isEmpty()){
			fieldsString = " " + fieldsString.substring(0, fieldsString.length() - 2);
		}
		
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		String query = "match (a:" + instruction.sourceGraph.name + ")-[e]->(b:" + instruction.sourceGraph.name + ")"
				+ " where " + edgeProperty + " contains ',"+instruction.sourceGraph.name+",'"
				+ " with distinct a,b," + fieldsString 
				+ " set a:"+instruction.targetGraph.name
				+ " set b:"+instruction.targetGraph.name
				+ " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
		storage.executeQuery(query);
//		'match (v:graph_3)-[e]-(u:graph_6) with distinct v,u,e.operation as iii set v:graph_1';
	}
	
	@Override
	public void getEdge(GetEdge instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		String query = "";
		query += "match ()-[e]->() ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += "where " + edgeProperty + " contains ',"+instruction.subjectGraph.name+",' ";
			if(instruction.hasArguments()){
				query += "and e." + buildComparison(instruction.annotationKey, instruction.operator, instruction.annotationValue);
			}
		}else{
			if(instruction.hasArguments()){
				query += "where e." + buildComparison(instruction.annotationKey, instruction.operator, instruction.annotationValue);
			}
		}
		query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
		
		storage.executeQuery(query);
	}

	@Override
	public void getEdgeEndpoint(GetEdgeEndpoint instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		String query = "";
		query += "match (a:" + instruction.subjectGraph.name + ")-[e]->(b:" + instruction.subjectGraph.name + ") ";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
			query += "where " + edgeProperty + " contains ',"+instruction.subjectGraph.name+",' ";
		}
		if(instruction.component.equals(GetEdgeEndpoint.Component.kSource)
				|| instruction.component.equals(GetEdgeEndpoint.Component.kBoth)){
			query += "set a:" + instruction.targetGraph.name + " ";
		}
		if(instruction.component.equals(GetEdgeEndpoint.Component.kDestination)
				|| instruction.component.equals(GetEdgeEndpoint.Component.kBoth)){
			query += "set b:" + instruction.targetGraph.name + " ";
		}
		
		query += ";";
		
		storage.executeQuery(query);
	}

	@Override
	public void intersectGraph(IntersectGraph instruction){
		String vertexQuery = "";
		vertexQuery += "match (x:" + instruction.lhsGraph.name + ":" + instruction.rhsGraph.name + ") ";
		vertexQuery += "set x:" + instruction.outputGraph.name + ";";
		
		storage.executeQuery(vertexQuery);
		
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		String edgeQuery = "";
		edgeQuery = "match ()-[e]->()";
		if(neo4jQueryEnvironment.isBaseGraph(instruction.lhsGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.rhsGraph)){
			// no where needed
		}else if(!neo4jQueryEnvironment.isBaseGraph(instruction.lhsGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.rhsGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.lhsGraph.name + ",'";
		}else if(neo4jQueryEnvironment.isBaseGraph(instruction.lhsGraph) && !neo4jQueryEnvironment.isBaseGraph(instruction.rhsGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.rhsGraph.name + ",'";
		}else{
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.lhsGraph.name + ",'";
			edgeQuery += " and " + edgeProperty + " contains '," + instruction.rhsGraph.name + ",'";
		}
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.outputGraph.name);
		
		storage.executeQuery(edgeQuery);
	}

	@Override
	public void limitGraph(LimitGraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		String vertexQuery = 
				"match (v:" + instruction.sourceGraph.name + ") with v order by id(v) asc limit " 
				+ instruction.limit + " set v:" + instruction.targetGraph.name + ";";
		String edgeQuery = "match ()-[e]->()";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.sourceGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.sourceGraph.name + ",'";
		}
		edgeQuery += " with e order by id(e) asc limit " + instruction.limit;
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
		storage.executeQuery(vertexQuery);
		storage.executeQuery(edgeQuery);
	}

	@Override
	public ResultTable listGraphs(ListGraphs instruction){
		return neo4jQueryEnvironment.listGraphs(instruction.style);
	}
	
	@Override
	public GraphStats statGraph(StatGraph instruction){
		return neo4jQueryEnvironment.getGraphStats(instruction.targetGraph);
	}

	@Override
	public void subtractGraph(SubtractGraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		if(neo4jQueryEnvironment.isBaseGraph(instruction.minuendGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.subtrahendGraph)){
			// no resulting vertices and edge since both are base
		}else if(!neo4jQueryEnvironment.isBaseGraph(instruction.minuendGraph) && neo4jQueryEnvironment.isBaseGraph(instruction.subtrahendGraph)){
			// no resulting vertices and edges since the subtrahend is base
		}else if(neo4jQueryEnvironment.isBaseGraph(instruction.minuendGraph) && !neo4jQueryEnvironment.isBaseGraph(instruction.subtrahendGraph)){
			String vertexQuery = "match (n:" + instruction.minuendGraph.name + ") where not '" + instruction.subtrahendGraph.name + 
					"' in labels(n) set n:" + instruction.outputGraph.name + ";";
			String edgeQuery = "match ()-[e]->()";
			edgeQuery += " where not " + edgeProperty + " contains '," + instruction.subtrahendGraph.name + ",'";
			edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.outputGraph.name);
			storage.executeQuery(vertexQuery);
			storage.executeQuery(edgeQuery);
		}else{
			String vertexQuery = "match (n:" + instruction.minuendGraph.name + ") where not '" + instruction.subtrahendGraph.name + 
					"' in labels(n) set n:" + instruction.outputGraph.name + ";";
			
			String edgeQuery = "match ()-[e]->()";
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.minuendGraph.name + ",'";
			edgeQuery += " and not " + edgeProperty + " contains '," + instruction.subtrahendGraph.name + ",'";
			edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.outputGraph.name);
			storage.executeQuery(vertexQuery);
			storage.executeQuery(edgeQuery);
		}
	}

	@Override
	public void unionGraph(UnionGraph instruction){
		String edgeQuery = "match ()-[e]->()";
		String vertexQuery = "match (v:" + instruction.sourceGraph.name + ") set v:" + instruction.targetGraph.name + ";";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.sourceGraph)){
			final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.sourceGraph.name + ",'";
		}
		edgeQuery += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name);
		
		storage.executeQuery(edgeQuery);
		storage.executeQuery(vertexQuery);
	}

	@Override
	public void getAdjacentVertex(GetAdjacentVertex instruction){ // TODO rename to get adjacent graph
		// $x = $y.getadj($z, '?')
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		if(instruction.direction.equals(GetLineage.Direction.kAncestor) || instruction.direction.equals(GetLineage.Direction.kBoth)){
			String query = "match (a:"+instruction.sourceGraph.name+":"+instruction.subjectGraph.name+")-[e]->"
					+ "(b:"+instruction.subjectGraph.name+")";
			if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
				query += " where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",'";
			}
			query += " set a:" + instruction.targetGraph.name;
			query += " set b:" + instruction.targetGraph.name;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
			storage.executeQuery(query);
		}
		
		if(instruction.direction.equals(GetLineage.Direction.kDescendant) || instruction.direction.equals(GetLineage.Direction.kBoth)){
			String query = "match (a:"+instruction.subjectGraph.name+")-[e]->"
					+ "(b:"+instruction.sourceGraph.name+":"+instruction.subjectGraph.name+")";
			if(!neo4jQueryEnvironment.isBaseGraph(instruction.subjectGraph)){
				query += " where " + edgeProperty + " contains '," + instruction.subjectGraph.name + ",'";
			}
			query += " set a:" + instruction.targetGraph.name;
			query += " set b:" + instruction.targetGraph.name;
			query += " " + buildSubqueryForUpdatingEdgeSymbols("e", instruction.targetGraph.name) + ";";
			storage.executeQuery(query);
		}
	}

	@Override
	public spade.core.Graph exportGraph(ExportGraph instruction){
		final String edgeProperty = "e.`"+neo4jQueryEnvironment.edgeSymbolsPropertyKey+"`";
		String edgeQuery = "match ()-[e]->()";
		String nodesQuery = "match (v:" + instruction.targetGraph.name + ") return v;";
		if(!neo4jQueryEnvironment.isBaseGraph(instruction.targetGraph)){
			edgeQuery += " where " + edgeProperty + " contains '," + instruction.targetGraph.name + ",'";
		}
		edgeQuery += " return e;";
		Map<String, AbstractVertex> hashToVertex = storage.readHashToVertexMap("v", nodesQuery);
		Set<AbstractEdge> edgeSet = storage.readEdgeSet("e", edgeQuery, hashToVertex);
		spade.core.Graph spadeCoreGraph = new spade.core.Graph();
		spadeCoreGraph.vertexSet().addAll(hashToVertex.values());
		spadeCoreGraph.edgeSet().addAll(edgeSet);
		return spadeCoreGraph;
	}

//	@Override
//	public void getLineage(GetLineage instruction){
//		// TODO Auto-generated method stub
//
//	}

	@Override
	public void getLink(GetLink instruction){
		
	}

//	@Override
//	public void getPath(GetPath instruction){
//		// TODO Auto-generated method stub
//
//	}

	@Override
	public void getShortestPath(GetShortestPath instruction){
		// TODO Auto-generated method stub

	}

	@Override
	public void getSubgraph(GetSubgraph instruction){
		// TODO Auto-generated method stub
	}

}