package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class Sensor extends Waypoint {
	
	private final String w3wAddress;
	private final double battery;
	private final double reading;
	
	public Sensor(Point point, String w3wAddress, double battery, double reading) {
		super(point);
		this.w3wAddress = w3wAddress;
		this.battery = battery;
		this.reading = reading;
	}
	
	public String getW3wAddress() {
		return w3wAddress;
	}
	
	public double getBattery() {
		return battery;
	}
	
	public double getReading() {
		return reading;
	}
}
