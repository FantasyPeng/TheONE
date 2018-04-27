package util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;




public class Graph {
	
private final Map<String, List<Vertex>> vertices;
	
	public Graph() {
		this.vertices = new HashMap<String, List<Vertex>>();
	}
	
	public void addVertex(String String, List<Vertex> vertex) {
		this.vertices.put(String, vertex);
	}
	
	public Map<Double,List<String>> getShortestPath(String start, String finish) {
		final Map<String, Double> distances = new HashMap<String, Double>();
		final Map<String, Vertex> previous = new HashMap<String, Vertex>();
		PriorityQueue<Vertex> nodes = new PriorityQueue<Vertex>();
		/*
		 * 初始化距离数组，前继点数组
		 */
		for(String vertex : vertices.keySet()) {
			if (vertex == start) {
				distances.put(vertex, 0.0);
				nodes.add(new Vertex(vertex, 0.0));
			} else {
				distances.put(vertex, Double.MAX_VALUE);
				nodes.add(new Vertex(vertex, Double.MAX_VALUE));
			}
			previous.put(vertex, null);
		}
		
		while (!nodes.isEmpty()) {
			Vertex smallest = nodes.poll();   // 

			if (distances.get(smallest.getId()) == Double.MAX_VALUE) {
				break;
			}
			if (smallest.getId() == finish) {
				final Map<Double,List<String>> res = new HashMap<Double, List<String>>();
				final List<String> path = new ArrayList<String>();
				while (previous.get(smallest.getId()) != null) {
					path.add(smallest.getId());
					smallest = previous.get(smallest.getId());
				}
			//	System.out.println(distances.get(finish));
				res.put(distances.get(finish),path);
				return res;
			}

						
			for (Vertex neighbor : vertices.get(smallest.getId())) {  //获取smallest节点的邻居，neighbor是Vertex类型,smallest为当前最近节点
				Double alt = distances.get(smallest.getId()) + neighbor.getDistance();
				if (alt < distances.get(neighbor.getId())) {
					distances.put(neighbor.getId(), alt);
					previous.put(neighbor.getId(), smallest);
					
					forloop:
					for(Vertex n : nodes) {
						if (n.getId() == neighbor.getId()) {
							nodes.remove(n);
							n.setDistance(alt);
							nodes.add(n);
							break forloop;
						}
					}
				}
			}
		}
		
		return new HashMap<Double, List<String>>();
	}
	
	
}

