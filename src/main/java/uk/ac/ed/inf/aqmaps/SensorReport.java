package uk.ac.ed.inf.aqmaps;

public class SensorReport {
	
	private boolean visited;
	private boolean valid;
	
	public SensorReport(boolean visited, boolean valid) {
		this.visited = visited;
		this.valid = valid;
	}
	
	public boolean isVisited() {
		return visited;
	}
	public void setVisited(boolean visited) {
		this.visited = visited;
	}
	public boolean isValid() {
		return valid;
	}
	public void setValid(boolean valid) {
		this.valid = valid;
	}
}
