package uk.ac.bris.cs.scotlandyard.ui.ai;

//Instead of returning an array that contains the two values or using a generic Pair class, consider creating a class that
// represents the result that you want to return, and return an instance of that class.
final class Distance {

	private final int shortestDistance;
	private final int sumOfDistances;

	public Distance(int shortestDistance, int sumOfDistances) {
		this.shortestDistance = shortestDistance;
		this.sumOfDistances = sumOfDistances;
	}

	public int getShortestDistance() {
		return shortestDistance;
	}

	public int getSumOfDistances() {
		return sumOfDistances;
	}
}