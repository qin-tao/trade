package tao.trading.dao;

public class MomentumDAO {

	public int direction;
	public int startPos;
	public int endPos;
	public String startTime;
	public String endTime;
	public double size;
	
	public int momentumChangePoint;
	public int lastUpBarBegin;
	public int lastDownBarBegin;
	public double triggerPrice;
	
	public MomentumDAO() {
	}

	public MomentumDAO(int direction) {
		super();
		this.direction = direction;
	}
	
	
}
