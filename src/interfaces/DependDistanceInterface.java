package interfaces;

import java.util.Collection;

import core.Connection;
import core.NetworkInterface;
import core.Settings;
import core.VBRConnection;

public class DependDistanceInterface extends NetworkInterface {


	
	public DependDistanceInterface(Settings s)	{
		super(s);
	}
	
	public DependDistanceInterface(DependDistanceInterface ni) {
		super(ni);
	}
	
	@Override
	public NetworkInterface replicate() {
		// TODO Auto-generated method stub
		return new DependDistanceInterface(this);
	}

	@Override
	public void connect(NetworkInterface anotherInterface) {
		// TODO Auto-generated method stub
		if (isScanning()
				&& anotherInterface.getHost().isRadioActive()
				&& isWithinRange(anotherInterface)
				&& !isConnected(anotherInterface)
				&& (this != anotherInterface)) {

			Connection con = new VBRConnection(this.host, this,
					anotherInterface.getHost(), anotherInterface);
			connect(con,anotherInterface);
		}
	}

	@Override
	public void update() {
		// TODO Auto-generated method stub
		if (optimizer == null) {
			return; /* nothing to do */
		}

		// First break the old ones
		optimizer.updateLocation(this);
		for (int i=0; i<this.connections.size(); ) {
			Connection con = this.connections.get(i);
			NetworkInterface anotherInterface = con.getOtherInterface(this);

			// all connections should be up at this stage
			assert con.isUp() : "Connection " + con + " was down!";

			if (!isWithinRange(anotherInterface)) {
				disconnect(con,anotherInterface);
				connections.remove(i);
			}
			else {
				i++;
			}
		}
		// Then find new possible connections
		Collection<NetworkInterface> interfaces =
			optimizer.getNearInterfaces(this);
		for (NetworkInterface i : interfaces) {
			connect(i);
		}

		/* update all connections */
		for (Connection con : getConnections()) {
			con.update();
		}
	}

	@Override
	public int getTransmitSpeed(NetworkInterface ni) {
		double distance;
		double speed;

		/* distance to the other interface */
		distance = ni.getLocation().distance(this.getLocation());

		if (distance >= this.transmitRange) {
			return 0;
		}

		speed = 1000000 / 8 * (-9.09 * (Math.log(distance) / Math.log(2)) + 72.58);
	

		return (int)speed;
	}
	
	@Override
	public void createConnection(NetworkInterface anotherInterface) {
		// TODO Auto-generated method stub
		if (!isConnected(anotherInterface) && (this != anotherInterface)) {
			Connection con = new VBRConnection(this.host, this,
					anotherInterface.getHost(), anotherInterface);
			connect(con,anotherInterface);
		}
	}

	/**
	 * Returns a string representation of the object.
	 * @return a string representation of the object.
	 */
	public String toString() {
		return "DependDistanceInterface " + super.toString();
	}
}
