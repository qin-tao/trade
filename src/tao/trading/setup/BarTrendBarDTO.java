package tao.trading.setup;

import tao.trading.QuoteData;

public class BarTrendBarDTO {
	public QuoteData trendBar;
	public int direction;
	public boolean newTrend;
	public double triggerPrice;
	public int triggerPos;
	public String action;
	public int begin;
	public int end;
	
	public double triggerBarSize;
	public double avg4BarSize;
	
	public BarTrendBarDTO( int trend, QuoteData trendBar) {
		this.trendBar = trendBar;
		this.direction = trend;
	}

	public BarTrendBarDTO( String action, QuoteData trendBar) {
		super();
		this.trendBar = trendBar;
		this.action = action;
	}

	public BarTrendBarDTO(int direction) {
		super();
		this.direction = direction;
	}

	public String toString(QuoteData[] quotes) {
		return "BarTrendDetectorDTO [direction=" + direction + ", newTrend="
				+ newTrend + ", begin=" + quotes[begin].time + ", end=" + quotes[end].time + "]" 
				+ "triggerPrice=]" + triggerPrice;
	}
	
	
	
}
