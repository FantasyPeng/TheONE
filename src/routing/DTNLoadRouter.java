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

public class DTNLoadRouter extends ActiveRouter {
	private Map<String,Double> mpreal;         //���˱��洢��ÿ���ڵ��Ȩֵ�Լ�·��
	private Map<String,Double> mpvirtual; 
	private Map<String,List<String>> paths; 
	
	private Double frame = 1.0;  //Ԥ��ʱ���
	private Double interv = 0.2;  //��t��t+F����ɢ���
	
	
	public DTNLoadRouter(Settings s) {
		super(s);
		//TODO: read&use epidemic router specific settings (if any)
	}
	/**
	 * Copy constructor.
	 * @param r The router prototype where setting values are copied from
	 */
	protected DTNLoadRouter(DTNLoadRouter r) {
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
	 * ����Fʱ�����Χ�ھ�������Ȩֵ
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
	 * ѡ��ʱ��F֮����������С���ھӻ�������
	 */
	public Connection getMinVirtualWeight_F(DTNHost to) {
		List<Connection> connections = getConnections();
		Connection cmin = null;
		double minweight = Double.MAX_VALUE;
		for (Connection con : connections) {
			double weight = getVirtualWeight_F(con.getOtherNode(getHost()),to);
			if (weight < minweight) {	
				cmin = con;
				minweight = weight;
			}
		}
		if (minweight < getVirtualWeight_F(getHost(),to)) 
			return cmin;
		else 
			return null;
	}
	/**
	 * ����ʱ��n֮��ýڵ��Ƿ���ͨ�ŷ�Χ��
	 */
	private boolean getIfWithinRange(DTNHost from,DTNHost to,double n) {
		Coord newfromloc = from.getLocation_F(n);
		Coord newtoloc = to.getLocation_F(n);
		double dis = newfromloc.distance(newtoloc);
		double transrange = from.getInterface(1).getTransmitRange();
		if (dis < transrange) 
			return true;
		else 
			return false;
	}
	/**
	 * ����ʱ��n֮��ڵ�֮���sֵ*interv��������ֵ��ʱ����֮��
	 */
	private double getSdnij(DTNHost from,DTNHost to) {

		Coord newfromloc = from.getLocation_F(interv);
		Coord newtoloc = to.getLocation_F(interv);
		double dis = newfromloc.distance(newtoloc);
		double sij = 1000000 * (-9.09 * (Math.log(dis) / Math.log(2)) + 72.58);   //���������й�ʽ����λΪbit/s
		
		return sij * interv;
	}
	/**
	 * ���ؽڵ��Bnjֵ
	 */
	private double getBnj(DTNHost to) {
		double Mdata = 7000.0;
		double timeend = frame;
		double time = 0.0;
		double sum = 0.0;
		boolean ifin = true;
		while(time < timeend) {
			ifin = getIfWithinRange(this.getHost(),to,time);
			if (!ifin) {
				return Double.MAX_VALUE;
			}
			double Snij = getSdnij(this.getHost(),to);
			sum += Snij;
			time += interv;
		}
		return sum / Mdata;
	}
	/**
	 * ��ȡ���ת���ڵ�
	 */
	private Connection getBestLoad(DTNHost to) {
		List<Connection> connections = getConnections();
		List<Connection> conless =  new ArrayList<Connection>();
		Connection cmin = null;
		double thisweight =  getVirtualWeight_F(getHost(),to);
		for (Connection con : connections) {
			double weight = getVirtualWeight_F(con.getOtherNode(getHost()),to);
			if (weight < thisweight) {	
				conless.add(con);
			}
		}
		double minBn = Double.MAX_VALUE;
		
		for (Connection con : conless) {
			double Bn = getBnj(con.getOtherNode(getHost()));
			if (Bn < minBn) {
				minBn = Bn;
				cmin = con;
			} /*else if (Bn == minBn) {
					System.out.println("ERROR IN DTNLOAD182");
			}*/	
		}
		return cmin;
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
			DTNLoadRouter othRouter = (DTNLoadRouter)other.getRouter();
			
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
	 				Connection cmin = getBestLoad(tohost);
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
	public DTNLoadRouter replicate() {
		// TODO Auto-generated method stub
		return new DTNLoadRouter(this);
	}
}
