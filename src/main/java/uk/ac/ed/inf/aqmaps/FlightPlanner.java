package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;


public class FlightPlanner {
	
	public static List<Sensor> greedyPath(Point start, List<Sensor> sensors) {		
		var dronePath = new ArrayList<Sensor>();
		var unvisited = new ArrayList<Sensor>(sensors);
		var currentPoint = start;
		
		for (int i = 0; i < sensors.size(); i++) {
			var closest = closestSensor(currentPoint, unvisited);
			currentPoint = closest.getPoint();
			dronePath.add(closest);
			unvisited.remove(closest);
		}
		return dronePath;
	}
	
	public static List<Sensor> twoOptPath(Point start, List<Sensor> sensors) {
		var startWaypoint = new StartEndPoint(start);
		
		List<Waypoint> path = new ArrayList<>();
		path.add(startWaypoint);
		path.addAll(greedyPath(start, sensors));
		path.add(startWaypoint);
				
		boolean improved = true;
		while (improved) {
			improved = false;
			outerloop:
			for (int i = 1; i <= path.size() - 3; i++) {
				for (int j = i+1; j <= path.size() - 2; j++) {
					boolean better = pathDifference(path, i, j);
					if (better) {
						path = modifiedPath(path, i, j);
						improved = true;					
						break outerloop;
					}
				}
			}
		}
		var sensorPath = path.subList(1, path.size() - 1)  // Remove the start and end of the path that are both the start/end point
				.stream()
				.map(waypoint -> (Sensor) waypoint)  // Cast from List<Waypoint> to List<Sensor>
				.collect(Collectors.toList());

		return sensorPath;
	}
	
	private static boolean pathDifference(List<Waypoint> path, int start, int end) {
		
		var beforeStartP = path.get(start - 1).getPoint();
		var startP = path.get(start).getPoint();	
		var endP = path.get(end).getPoint();
		var afterEndP = path.get(end + 1).getPoint();
		
		double lengthBefore = distanceBetween(beforeStartP, startP) + distanceBetween(endP, afterEndP);
		double lengthAfter = distanceBetween(beforeStartP, endP) + distanceBetween(startP, afterEndP);
				
		return lengthAfter < lengthBefore;
	}
	
	private static List<Waypoint> modifiedPath(List<Waypoint> path, int s, int e) {
		var output = new ArrayList<>(path);
		for (int i = s; i <= e; i++) {
			output.set(i, path.get(e-(i-s)));
		}
		return output;
	}
	
	private static Sensor closestSensor(Point point, List<Sensor> sensors) {
		return sensors.stream()
				.min((Sensor a, Sensor b) -> Double.compare(distanceBetween(point, a.getPoint()), distanceBetween(point, b.getPoint())))
				.get();
	}
	
}
