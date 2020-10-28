package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class Sensor {
	
	private Point point;
	private String location;
	private double battery;
	private double reading;
	
	public Point getPoint() {
		return point;
	}
	public void setPoint(Point point) {
		this.point = point;
	}
	public String getLocation() {
		return location;
	}
	public void setLocation(String location) {
		this.location = location;
	}
	public double getBattery() {
		return battery;
	}
	public void setBattery(double battery) {
		this.battery = battery;
	}
	public double getReading() {
		return reading;
	}
	public void setReading(String reading) {
		this.reading = Double.parseDouble(reading);
	}
}
