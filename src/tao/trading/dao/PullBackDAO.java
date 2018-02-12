package tao.trading.dao;

import tao.trading.QuoteData;

public class PullBackDAO
{
	int direction;
	int startPos;
	int endPos;
	float startPrice;
	float endPrice;
	int ma;
	
	public PullBackDAO(int direction, int startPos, int endPos) {
		super();
		this.direction = direction;
		this.startPos = startPos;
		this.endPos = endPos;
	}
	
	
	
	
}
