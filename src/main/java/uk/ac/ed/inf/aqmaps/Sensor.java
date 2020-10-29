package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

public class Sensor {
	
	private Point point;
	private String location;
	private double battery;
	private String reading;
	
	public static Sensor dummySensor(Point point) {
		var dummy = new Sensor();
		dummy.setPoint(point);
		dummy.setLocation("dummy");
		return dummy;
	}
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
	public String getReading() {
		return reading;
	}
	public void setReading(String reading) {
		this.reading = reading;
	}
}
