package tao.trading.dao;

public class ConsectiveBars
{
	int begin;
	int end;
	
	public ConsectiveBars(int begin, int end)
	{
		super();
		this.begin = begin;
		this.end = end;
	}
	
	public int getSize()
	{
		return end-begin+1;
	}

	public int getBegin()
	{
		return begin;
	}

	public int getEnd()
	{
		return end;
	}
	
}
