package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

// Another "struct" type class for representing sensors
// Extends Waypoint, as sensors are just waypoints that store extra data
public class Sensor extends Waypoint{
	
	private String w3wAddress;
	private double battery;
	private double reading;
	
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
