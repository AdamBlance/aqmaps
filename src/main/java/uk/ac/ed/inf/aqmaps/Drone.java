package uk.ac.ed.inf.aqmaps;

import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.mapbox.geojson.Point;

public class Drone {
	
	// maybe use Point idk
	
	private Point position;
	private HashMap<Point, Sensor> sensors;	
	private NoFlyZoneChecker nfzc;
	
	public int timesMoved = 0;
	
	private List<Point> path;
	
	private static final double MOVE_DISTANCE = 0.0003;
	private static final int MAX_MOVES = 150;
	
	// should maybe standardise lat(itude) etc
	
	public Drone(Point initialPos, HashMap<Point, Sensor> sensors, NoFlyZoneChecker nfzc) {
		position = initialPos;
		path = new ArrayList<>(Arrays.asList(position));
		this.nfzc = nfzc;
	}
	
	public DroneStatus move(int bearing) {
		if (timesMoved == MAX_MOVES) {
			return DroneStatus.OUT_OF_MOVES;
		}
		if (bearing % 10 == 0 
				&& bearing >= 0 
				&& bearing <= 350) {
			
			// we need to check if its legal to move first
			
			double rad = Math.toRadians(bearing);
			
			Point newPos = Point.fromLngLat(
					position.longitude() + MOVE_DISTANCE*Math.sin(rad),
					position.latitude() + MOVE_DISTANCE*Math.cos(rad));
			
			if (nfzc.isMoveLegal(position, newPos)) {
				position = newPos;
				
				timesMoved += 1;
				return DroneStatus.OK;
			} else {
				return DroneStatus.ILLEGAL;
			}
			
			
		} else {
			throw new IllegalArgumentException("Invalid bearing");
		}
	}
	
	public Point getPosition() {
		return position;
	}
	
	// be careful of type
	
	// idk here Optionals?
	
	// For the now
	// not sure about this one
//	public Optional<Float> getReading(Point sensor) {
//		Sensor sensorData = sensors.get(sensor);
//		if ((distance(position, sensor) < 0.0002) && (sensorData.getBattery() >= 10)) {
//			return Float.parseFloat(sensorData.getReading());
//		} else {
//			return null;
//		}
//	}
	
	public boolean isLegal(double latitude, double longitude) {
		return true;
		// Honestly we need the webserver stuff first before we do other things cause we need the data
	}
	
	
	// think about moving this
	private static double distance(Point a, Point b) {
		return Math.sqrt( Math.pow(a.longitude()-b.longitude(), 2) + Math.pow(a.latitude()-b.latitude(), 2));
	}
	
	
}
