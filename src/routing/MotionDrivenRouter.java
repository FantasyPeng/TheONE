package routing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.Connection;
import core.DTNHost;
import core.Message;
import core.Settings;
import core.World;
import util.Graph;
import util.Tuple;

public class MotionDrivenRouter extends ActiveRouter {
	
	private Map<String,Double> mpreal;         //���˱��洢��ÿ���ڵ��Ȩֵ�Լ�·��
	private Map<String,Double> mpvirtual; 
	private Map<String,List<String>> paths; 
	private List<String> shortpath;        //�洢��Ŀ��ڵ�����·��
	
	
	public MotionDrivenRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected MotionDrivenRouter(MotionDrivenRouter r) {
		super(r);
		//TODO: copy epidemic settings here (if any)
	}
	
	/**
	 * ���ɽڵ�����˱�,��ȡ·��
	 */
	private void generateTopologyTable() {
		mpreal =  new HashMap<String, Double>();
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
			if (m.isEmpty() || m == null) { //���գ���Ϊ�����ӣ����������ӵ�Ȩֵ
				double vw = getVirtualWeight(this.getHost(),hosts.get(i));
				mpvirtual.put(toid,vw);
			} else {
				for (Double key : m.keySet()) {  
					  
					mpreal.put(toid, key);
					//System.out.println("Key = " + key);  
					paths.put(toid, m.get(key));
				} 
				
			}
			
		}
	}
	
	/**
	 * ����������Ȩֵ
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
	 * ѡ�������ھӽڵ��е�Ŀ��������Ȩֵ��С��
	 */
	private Connection getMinVirtualWeight(DTNHost to) {
		List<Connection> connections = getConnections();
		Connection cmin = null;
		double minweight = Double.MAX_VALUE;
		for (Connection con : connections) {
			double weight = getVirtualWeight(con.getOtherNode(getHost()),to);
			if (weight < minweight) {	
				cmin = con;
				minweight = weight;
			}
		}
		if (minweight < getVirtualWeight(getHost(),to)) 
			return cmin;
		else 
			return null;
	}
	
	/**
	 * ѡ���ͽڵ㲢����
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
			MotionDrivenRouter othRouter = (MotionDrivenRouter)other.getRouter();
			
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
	 			} else {//pΪ�գ�����p��·��
	 				Connection cmin = getMinVirtualWeight(tohost);
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
	//	this.tryAllMessagesToAllConnections();
	}
	/**
	 * �������֮��drop���շ��͵���Ϣ����֤single-copy
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
	public MotionDrivenRouter replicate() {
		// TODO Auto-generated method stub
		return new MotionDrivenRouter(this);
	}
	
}
