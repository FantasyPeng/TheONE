/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package core;

import input.EventQueue;
import input.ExternalEvent;
import input.ScheduledUpdatesQueue;
import routing.MessageRouter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import util.Vertex;
import util.Graph;

/**
 * World contains all the nodes and is responsible for updating their
 * location and connections.
 */
public class World {
	/** name space of optimization settings ({@value})*/
	public static final String OPTIMIZATION_SETTINGS_NS = "Optimization";

	/**
	 * Should the order of node updates be different (random) within every
	 * update step -setting id ({@value}). Boolean (true/false) variable.
	 * Default is @link {@link #DEF_RANDOMIZE_UPDATES}.
	 */
	public static final String RANDOMIZE_UPDATES_S = "randomizeUpdateOrder";
	/** should the update order of nodes be randomized -setting's default value
	 * ({@value}) */
	public static final boolean DEF_RANDOMIZE_UPDATES = true;

	/**
	 * Should the connectivity simulation be stopped after one round
	 * -setting id ({@value}). Boolean (true/false) variable.
	 */
	public static final String SIMULATE_CON_ONCE_S = "simulateConnectionsOnce";

	private int sizeX;
	private int sizeY;
	private List<EventQueue> eventQueues;
	private double updateInterval;
	private SimClock simClock;
	private double nextQueueEventTime;
	private EventQueue nextEventQueue;
	/** list of nodes; nodes are indexed by their network address */
	public static List<DTNHost> hosts;
	private boolean simulateConnections;
	/** nodes in the order they should be updated (if the order should be
	 * randomized; null value means that the order should not be randomized) */
	private ArrayList<DTNHost> updateOrder;
	/** is cancellation of simulation requested from UI */
	private boolean isCancelled;
	private List<UpdateListener> updateListeners;
	/** Queue of scheduled update requests */
	private ScheduledUpdatesQueue scheduledUpdates;
	private boolean simulateConOnce;

	public static Graph graph;
	/**
	 * Constructor.
	 */
	public World(List<DTNHost> hosts, int sizeX, int sizeY,
			double updateInterval, List<UpdateListener> updateListeners,
			boolean simulateConnections, List<EventQueue> eventQueues) {
		World.hosts = hosts;
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.updateInterval = updateInterval;
		this.updateListeners = updateListeners;
		this.simulateConnections = simulateConnections;
		this.eventQueues = eventQueues;

		this.simClock = SimClock.getInstance();
		this.scheduledUpdates = new ScheduledUpdatesQueue();
		this.isCancelled = false;

		setNextEventQueue();
		initSettings();
	}

	/**
	 * Initializes settings fields that can be configured using Settings class
	 */
	private void initSettings() {
		Settings s = new Settings(OPTIMIZATION_SETTINGS_NS);
		boolean randomizeUpdates = DEF_RANDOMIZE_UPDATES;

		if (s.contains(RANDOMIZE_UPDATES_S)) {
			randomizeUpdates = s.getBoolean(RANDOMIZE_UPDATES_S);
		}
		simulateConOnce = s.getBoolean(SIMULATE_CON_ONCE_S, false);

		if(randomizeUpdates) {
			// creates the update order array that can be shuffled
			this.updateOrder = new ArrayList<DTNHost>(World.hosts);
		}
		else { // null pointer means "don't randomize"
			this.updateOrder = null;
		}
	}

	/**
	 * Moves hosts in the world for the time given time initialize host
	 * positions properly. SimClock must be set to <CODE>-time</CODE> before
	 * calling this method.
	 * @param time The total time (seconds) to move
	 */
	public void warmupMovementModel(double time) {
		if (time <= 0) {
			return;
		}

		while(SimClock.getTime() < -updateInterval) {
			moveHosts(updateInterval);
			simClock.advance(updateInterval);
		}

		double finalStep = -SimClock.getTime();

		moveHosts(finalStep);
		simClock.setTime(0);
	}

	/**
	 * Goes through all event Queues and sets the
	 * event queue that has the next event.
	 */
	public void setNextEventQueue() {
		EventQueue nextQueue = scheduledUpdates;
		double earliest = nextQueue.nextEventsTime();

		/* find the queue that has the next event */
		for (EventQueue eq : eventQueues) {
			if (eq.nextEventsTime() < earliest){
				nextQueue = eq;
				earliest = eq.nextEventsTime();
			}
		}

		this.nextEventQueue = nextQueue;
		this.nextQueueEventTime = earliest;
	}

	/**
	 * Update (move, connect, disconnect etc.) all hosts in the world.
	 * Runs all external events that are due between the time when
	 * this method is called and after one update interval.
	 */
	public void update () {
		double runUntil = SimClock.getTime() + this.updateInterval;

		setNextEventQueue();
		
		/* process all events that are due until next interval update */
		while (this.nextQueueEventTime <= runUntil) {
			simClock.setTime(this.nextQueueEventTime);
			ExternalEvent ee = this.nextEventQueue.nextEvent();
			ee.processEvent(this);
			updateHosts(); // update all hosts after every event
			setNextEventQueue();
		}

		moveHosts(this.updateInterval);
		simClock.setTime(runUntil);
		updateGraph();
		updateHosts();

		/* inform all update listeners */
		for (UpdateListener ul : this.updateListeners) {
			ul.updated(World.hosts);
		}
	}
	/**
	 * 更新图的邻接表，为后续最短路径算法铺垫
	 */
	private void updateGraph() {
		graph = new Graph();
		for (int i=0, n = hosts.size();i < n; i++) {
			List<Vertex> vertex = new ArrayList<Vertex>(); 	               
			String id = hosts.get(i).toString();	
			for (int j = 0; j < n; j++) {
				if (hosts.get(i).canConnect(hosts.get(j))) {
					String otherid = hosts.get(j).toString();
					double weight = getReallinksWeight(hosts.get(i),hosts.get(j));
					vertex.add(new Vertex(otherid,weight));
				}
			}
			graph.addVertex(id, vertex);
		}			
	}
	/**
	 * 获取真实存在的链接，即实链接边的权值
	 * @param from
	 * @param to
	 * @return
	 */
	private double getReallinksWeight(DTNHost from,DTNHost to) {
		
		double dis = from.getLocation().distance(to.getLocation());
		double sij = 1000000 * (-9.09 * (Math.log(dis) / Math.log(2)) + 72.58);   //根据论文中公式，单位为bit/s
		double weight = 7000 * 8 / sij;                                           //7KB = 7000 * 8Kbit
				
		return weight;
	}
	
	/**
	 * Updates all hosts (calls update for every one of them). If update
	 * order randomizing is on (updateOrder array is defined), the calls
	 * are made in random order.
	 */
	private void updateHosts() {
		if (this.updateOrder == null) { // randomizing is off
			for (int i=0, n = hosts.size();i < n; i++) {
				if (this.isCancelled) {
					break;
				}
				hosts.get(i).update(simulateConnections);
			}
/*			for (int i=0, n = hosts.size();i < n; i++) {
				if (this.isCancelled) {
					break;
				}
				hosts.get(i).updateRouter();
			}*/
		}
		else { // update order randomizing is on
			assert this.updateOrder.size() == World.hosts.size() :
				"Nrof hosts has changed unexpectedly";
			Random rng = new Random(SimClock.getIntTime());
			Collections.shuffle(this.updateOrder, rng);
			/*
			 * 将更新网络层与路由协议拆分开，先更新所有节点的网络层，包括建立连接，断开连接等，再更新全图拓扑
			 * 最后更新路由协议
			 */
			for (int i=0, n = hosts.size();i < n; i++) {
				if (this.isCancelled) {
					break;
				}
				this.updateOrder.get(i).update(simulateConnections);
			}
			
			/*for (int i=0, n = hosts.size();i < n; i++) {
				if (this.isCancelled) {
					break;
				}
				this.updateOrder.get(i).updateRouter();
			}*/
			
		}

		if (simulateConOnce && simulateConnections) {
			simulateConnections = false;
		}
	}

	/**
	 * Moves all hosts in the world for a given amount of time
	 * @param timeIncrement The time how long all nodes should move
	 */
	private void moveHosts(double timeIncrement) {
		for (int i=0,n = hosts.size(); i<n; i++) {
			DTNHost host = hosts.get(i);
			host.move(timeIncrement);
		}
	}

	/**
	 * Asynchronously cancels the currently running simulation
	 */
	public void cancelSim() {
		this.isCancelled = true;
	}

	/**
	 * Returns the hosts in a list
	 * @return the hosts in a list
	 */
	public static List<DTNHost> getHosts() {
		return World.hosts;
	}

	/**
	 * Returns the x-size (width) of the world
	 * @return the x-size (width) of the world
	 */
	public int getSizeX() {
		return this.sizeX;
	}

	/**
	 * Returns the y-size (height) of the world
	 * @return the y-size (height) of the world
	 */
	public int getSizeY() {
		return this.sizeY;
	}

	/**
	 * Returns a node from the world by its address
	 * @param address The address of the node
	 * @return The requested node or null if it wasn't found
	 */
	public DTNHost getNodeByAddress(int address) {
		if (address < 0 || address >= hosts.size()) {
			throw new SimError("No host for address " + address + ". Address " +
					"range of 0-" + (hosts.size()-1) + " is valid");
		}

		DTNHost node = World.hosts.get(address);
		assert node.getAddress() == address : "Node indexing failed. " +
			"Node " + node + " in index " + address;

		return node;
	}
	/**
	 * 删除全网络的消息副本
	 */
	public static void deleteMessageFromAll(String id,DTNHost to) {
		for (int i=0,n = hosts.size(); i<n; i++) {

			DTNHost host = hosts.get(i);
			if (host.equals(to)) {
				continue;
			}
			MessageRouter mr = host.getRouter();
			Message removed = mr.removeFromMessages(id);
			Message rem = mr.removeFromIncomingBufferById(id);
		/*	if (removed == null) throw new SimError("no message for id " +
					id + " to remove at " + host);*/
			if (removed == null) 
				continue;
			
			for (MessageListener ml : mr.mListeners) {
				ml.messageDeleted(removed,host, false);
			}
		}
	}
	/**
	 * Schedules an update request to all nodes to happen at the specified
	 * simulation time.
	 * @param simTime The time of the update
	 */
	public void scheduleUpdate(double simTime) {
		scheduledUpdates.addUpdate(simTime);
	}
}
