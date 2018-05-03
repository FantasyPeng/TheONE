package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import core.Connection;
import core.Coord;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.World;
import util.Graph;
import util.Tuple;

public class DTNCloseAllRouter extends ActiveRouter {
	private Map<DTNHost,Double> mpreal;         //拓扑表，存储到每个节点的权值以及路径
	private Map<String,Double> mpvirtual; 
	private Map<String,List<String>> paths; 
	
	private Double frame = 8.0;  //预测时间段
	
	
	public DTNCloseAllRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected DTNCloseAllRouter(DTNCloseAllRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	
	/**
	 * 生成节点的拓扑表,获取路径
	 */
	private void generateTopologyTable() {
		mpreal =  new HashMap<DTNHost, Double>();
		mpvirtual =  new HashMap<String, Double>();
		paths = new HashMap<String, List<String>>();
		Graph g = World.graph;
		List<DTNHost> hosts = World.getHosts();
		for (int i = 0 ,n = hosts.size();i < n; i++) {
			if (this.getHost().equals(hosts.get(i))) {
				continue;
			}
			String toid = hosts.get(i).toString();
			Map<Double,List<String>> m = g.getShortestPath(this.getHost().toString(),
															hosts.get(i).toString());
			if (m.isEmpty() || m == null) { //若空，则为虚链接，计算虚链接的权值
				double vw = getVirtualWeight(this.getHost(),hosts.get(i));
				mpvirtual.put(toid,vw);
			} else {
				for (Double key : m.keySet()) {  
					  
					mpreal.put(hosts.get(i), key);
					//System.out.println("Key = " + key);  
					paths.put(toid, m.get(key));
				} 
				
			}
			
		}
	}
	
	/**
	 * 计算虚链接权值
	 */
	private double getVirtualWeight(DTNHost from,DTNHost to) {
		double dis = from.getLocation().distance(to.getLocation());
		double transrange = to.getInterface(1).getTransmitRange();
		double tc = (dis - transrange) / from.getSpeed();
		//double tc = (dis - transrange) / 4.5;
		double ttx = 0.1;
		return tc + ttx;
	}
	/**
	 * 计算F时间后两节点的虚链接权值
	 */
	private double getVirtualWeight_F(DTNHost from,DTNHost to) {
		
		Coord newfromloc = from.getLocation_F(frame);
		Coord newtoloc = to.getLocation_F(frame);
		double dis = newfromloc.distance(newtoloc);
		
		double transrange = to.getInterface(1).getTransmitRange();
		double tc = (dis - transrange) / from.getSpeed();
		double ttx = 0.1;
		
		return tc + ttx;
	}

	/**
	 * 找到与当前节点实际链接，且F时间后离终点距离最近的的点
	 */
	public Connection getMinWeight_F(DTNHost to) {
		List<Connection> connections = getConnections();
		Connection cmin = null;
		double weight = getVirtualWeight_F(getHost(),to); 
		double minweight = Double.MAX_VALUE;
		DTNHost dmin = null;
		for (DTNHost key : mpreal.keySet()) {  		  
			double kweight = getVirtualWeight_F(key,to);
			if (kweight < minweight) {
				minweight = kweight;
				dmin = key;
			}
		} 
		if (minweight < weight) {
			String id = dmin.toString();
			List<String> p = paths.get(id);
			for (Connection con:connections) {
				DTNHost other = con.getOtherNode(getHost());
				if (p.contains(other.toString())) {
					cmin = con;
				}
			}
		}
		return cmin;
	}
	/**
	 * 选择发送节点并发送
	 */
	private Tuple<Message, Connection> tryMotionDriven() {
		List<Connection> connections = getConnections();
 		if (connections.size() == 0 || this.getNrofMessages() == 0) {
			return null;
		}
 		List<Tuple<Message, Connection>> messages =
 				new ArrayList<Tuple<Message, Connection>>();

 		Collection<Message> msgCollection = getMessageCollection();
 		
 		for (Connection con : connections) { 
 			DTNHost other = con.getOtherNode(getHost());
			DTNCloseAllRouter othRouter = (DTNCloseAllRouter)other.getRouter();
			
			if (othRouter.isTransferring()) {
				continue; // skip hosts that are transferring
			}
			for (Message m : msgCollection) {
				DTNHost tohost = m.getTo();
	 			String toid = tohost.toString();		
	 			List<String> p = paths.get(toid);
	 			
	 			if (p != null) {
	 				if (p.contains(other.toString()))
	 					messages.add(new Tuple<Message,Connection>(m,con));
	 			} else {//p为空，或者p无路径
	 				Connection cmin = getMinWeight_F(tohost);
	 				if (cmin != null && con.equals(cmin)) {
	 					messages.add(new Tuple<Message,Connection>(m,con));
	 				}
	 			}
			}
 		}
		return tryMessagesForConnected(messages);
	}
	
	@Override
	public void update() {
		super.update();
		
		if (isTransferring() || !canStartTransfer()) {		
			return; // transferring, don't try other connections yet
		}

		// Try first the messages that can be delivered to final recipient
		if (exchangeDeliverableMessages() != null) {
			return; // started a transfer, don't try others (yet)
		}
		generateTopologyTable();
		tryMotionDriven();
	}
	/**
	 * 传输完成之后drop掉刚发送的消息，保证single-copy
	 * @param con The connection whose transfer was finalized
	 */
	@Override
	protected void transferDone(Connection con) {
		Message m = con.getMessage();
		String id = m.getId();
		DTNHost from = con.getMsgFrom();
		from.deleteMessage(id,true);
	}
	@Override
	public DTNCloseAllRouter replicate() {
		// TODO Auto-generated method stub
		return new DTNCloseAllRouter(this);
	}
}
