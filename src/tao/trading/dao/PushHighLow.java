package tao.trading.dao;

import tao.trading.QuoteData;

public class PushHighLow {
	public int prePos;
	public int curPos;
	public int direction;
	public int pushStart;
	public int pushExt;
	public int breakOutPos;
	public double breakOutSize;
	public double totalBreakOutSize;
	public QuoteData pullBack;
	public double pushSize, pullBackSize, pullBackRatio;
	public int pushWidth, pullBackWidth;

	public int pullBackTouch20MA;
	public int pullBackMACDPositive, pullBackMACDNegative;
	
	
	public PushHighLow(int direction) {
		super();
		this.direction = direction;
	}

	public PushHighLow(int prePos, int curPos) {
		super();
		this.prePos = prePos;
		this.curPos = curPos;
	}
	
	public PushHighLow(int pushStart, int prePos, int curPos) {
		super();
		this.pushStart = pushStart;
		this.prePos = prePos;
		this.curPos = curPos;
	}
	
	
}
