package tao.trading.dao;

public class DiverageDAO {
	public String action;
	public int pushStart;
	public int prePos;
	public int curPos;
	public double pullBackSize;
	public int pullBackWidth;
	
	public DiverageDAO(){
	}
	
	public DiverageDAO(int prePos, int curPos) {
		super();
		this.prePos = prePos;
		this.curPos = curPos;
	}
	
	
}
