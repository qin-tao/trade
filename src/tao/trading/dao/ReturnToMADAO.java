package tao.trading.dao;

import tao.trading.QuoteData;

public class ReturnToMADAO
{
	int direction;
	double triggerPrice;
	int startPos;
	int endPos;
	int pullBackStart;
	int ma;
	public double pushStart, pushEnd, pullBackPoint;
	
	public ReturnToMADAO(int direction, double triggerPrice, int pullBackStart)
	{
		super();
		this.direction = direction;
		this.triggerPrice = triggerPrice;
		this.pullBackStart = pullBackStart;
	}

	public ReturnToMADAO(int direction, int startPos, int endPos, int lhPos, int ma)
	{
		super();
		this.direction = direction;
		this.startPos = startPos;
		this.endPos = endPos;
		this.pullBackStart = lhPos;
		this.ma = ma;
	}

	public int getDirection()
	{
		return direction;
	}

	public void setDirection(int direction)
	{
		this.direction = direction;
	}

	public double getTriggerPrice()
	{
		return triggerPrice;
	}

	public void setTriggerPrice(double triggerPrice)
	{
		this.triggerPrice = triggerPrice;
	}

	public int getStartPos()
	{
		return startPos;
	}

	public void setStartPos(int startPos)
	{
		this.startPos = startPos;
	}

	public int getEndPos()
	{
		return endPos;
	}

	public void setEndPos(int endPos)
	{
		this.endPos = endPos;
	}

	public int getLowestHighestPos()
	{
		return pullBackStart;
	}

	public void setLowestHighestPos(int lowestHighestPos)
	{
		this.pullBackStart = lowestHighestPos;
	}

	public int getMa()
	{
		return ma;
	}

	public String toString(QuoteData[] quotes)
	{
		return "direction=" + direction + ", startPos=" + quotes[startPos].time + ", endPos=" + quotes[endPos].time + ", lowestHighestPos=" + quotes[pullBackStart].time
				+ ", ma=" + ma + "]";
	}


	
	
	
	
}
