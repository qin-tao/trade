package tao.trading.dao;

import tao.trading.Constants;
import tao.trading.QuoteData;

public class PushTrendDAO {
	int direction;
	PushList currentTrend;
	PushList lastTrend;
	
	public PushTrendDAO(int direction, PushList currTrend, PushList lastTrend) {
		super();
		this.direction = direction;
		this.currentTrend = currTrend;
		this.lastTrend = lastTrend;
	}

	public String toString(QuoteData[] quotes, double PIP_SIZE) {
		StringBuffer sb = new StringBuffer();
		
		sb.append("PushTrendDAO direction=" + ((direction==Constants.DIRECTION_UP)?"UP":"DOWN") + "\n" );
		sb.append("Current Trend:\n" );
		sb.append(currentTrend.toString(quotes,PIP_SIZE));
		sb.append("Last Trend:\n" );
		if ( lastTrend != null )
			sb.append(lastTrend.toString(quotes,PIP_SIZE));
		
		return sb.toString();
	}

	public PushList getCurrentTrend() {
		return currentTrend;
	}

	public int getDirection() {
		return direction;
	}

	public PushList getLastTrend() {
		return lastTrend;
	}

	public void setLastTrend(PushList lastTrend) {
		this.lastTrend = lastTrend;
	}

	
	
	
}
