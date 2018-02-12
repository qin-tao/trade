package tao.trading;

import tao.trading.strategy.util.Utility;

public class Push
{
	public int direction;
	public String dirt;
	public int pushStart;
	public int pushEnd;
	public int duration;
	public int peakPos;

	public double push;
	public double pullback;
	
	public Push()
	{
		super();
	}
	
	public Push( int start, int end )
	{
		pushStart = start;
		pushEnd = end;
		duration = end - start + 1;
	}
	
	public Push ( int direction, int start, int end)
	{
		this.direction = direction;
		this.pushStart = start;
		this.pushEnd = end;
		duration = end - start + 1;
	}

	public Push ( String dirt, int start, int end)
	{
		this.dirt = dirt;
		this.pushStart = start;
		this.pushEnd = end;
		duration = end - start + 1;
	}

	
	public QuoteData getPushHigh( QuoteData[] quotes )
	{
		return Utility.getHigh( quotes, pushStart, pushEnd);
	}
	
	public QuoteData getPushLow( QuoteData[] quotes )
	{
		return Utility.getLow( quotes, pushStart, pushEnd);
	}

}
