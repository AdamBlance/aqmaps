package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.inRange;

public class Drone {
	
	private Point position;
	private int timesMoved = 0;
	
	// These are only public so that they can be easily accessed
	public static final int MAX_MOVES = 150;
	public static final double MOVE_DISTANCE = 0.0003;
	public static final double SENSOR_READ_DISTANCE = 0.0002;
	public static final double END_POINT_DISTANCE = 0.0003;
	
	public Drone(Point startPosition) {
		position = startPosition;
	}

	public boolean move(int bearing) {
		if (timesMoved >= MAX_MOVES) {
			return false;
		}
		position = moveDestination(position, bearing);
		timesMoved += 1;
		return true;
	}

	public double readSensor(Sensor sensor) {
		if (inRange(position, sensor)) {
			return sensor.getReading();
		}
		System.out.println("Fatal error: Drone tried to read sensor that was out of range. Exiting...");
		System.exit(1);
		return 0;  // Method always needs to return something
	}
	
	public Point getPosition() {
		return position;
	}

	public int getTimesMoved() {
		return timesMoved;
	}
	
}
