package tao.trading.dao;

import tao.trading.QuoteData;


public class MABreakOutList
{
	public int direction;
	public String overAllDirection;
	public MABreakOut[] bos;
	public int begin,end;
	
	public MABreakOutList(int direction, MABreakOut[] bos)
	{
		super();
		this.direction = direction;
		this.bos = bos;
	}

	public String toString(QuoteData[] quotes)
	{
		StringBuffer sb = new StringBuffer();
		sb.append("MABreakOutList direction=" + direction  + " " + quotes[begin].time + " break Out Times:" + getNumOfBreakOuts() + "\n");
		
		
		return sb.toString();
	}
	
	public int getNumOfBreakOuts()
	{
		return ((bos== null)?0:bos.length);
	}

	public MABreakOut getLastMBBreakOut()
	{
		return bos[bos.length-1];
	}

	public int getDirection()
	{
		return direction;
	}

	
}
