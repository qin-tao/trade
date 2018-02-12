package tao.trading.dao;

import tao.trading.QuoteData;

public class MABreakOut {

	public int begin;
	public int end;
	public QuoteData low;
	public QuoteData high;
	public boolean complete;  // end return to MA
	
	public MABreakOut(int begin)
	{
		super();
		this.begin = begin;
	}
	
	
	
}
