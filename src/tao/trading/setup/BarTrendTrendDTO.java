package tao.trading.setup;

import java.util.ArrayList;

import tao.trading.QuoteData;

public class BarTrendTrendDTO {
	public int direction;
	public double triggerPrice;
	public int triggerPos;
	public String action;
	public int begin;
	public int end;
	
	public BarTrendTrendDTO(int direction) {
		super();
		this.direction = direction;
	}

	
	
	
}
