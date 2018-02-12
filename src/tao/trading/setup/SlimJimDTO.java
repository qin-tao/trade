package tao.trading.setup;

public class SlimJimDTO {
	public double range;
	public int highestPoint;
	public int lowestPoint;
	public SlimJimDTO(int highestPoint, int lowestPoint, double range ) {
		super();
		this.range = range;
		this.highestPoint = highestPoint;
		this.lowestPoint = lowestPoint;
	}
	
}
