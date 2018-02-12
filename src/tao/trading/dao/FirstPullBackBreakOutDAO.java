package tao.trading.dao;

public class FirstPullBackBreakOutDAO
{
	int direction;
	double triggerPrice;
	int firstBreakOutStart;

	public FirstPullBackBreakOutDAO(int direction, double triggerPrice, int firstBreakOutStart)
	{
		super();
		this.direction = direction;
		this.triggerPrice = triggerPrice;
		this.firstBreakOutStart = firstBreakOutStart;
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

	public int getFirstBreakOutStart()
	{
		return firstBreakOutStart;
	}

	public void setFirstBreakOutStart(int firstBreakOutStart)
	{
		this.firstBreakOutStart = firstBreakOutStart;
	}

	
}
