package uk.ac.ed.inf.aqmaps;

public class Sensor {

	// watch out with float
	
	private String location;
	private float battery;
	private String reading;
	
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	
	public float getBattery() {
		return battery;
	}
	public void setBattery(float battery) {
		this.battery = battery;
	}
	
	public String getReading() {
		return reading;
	}
	public void setReading(String reading) {
		this.reading = reading;
	}

	
}
