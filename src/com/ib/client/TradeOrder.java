package com.ib.client;

import java.util.Vector;

public class TradeOrder {
	
	public TradeRecord open;
	public Vector<TradeRecord> close;

	public TradeOrder( TradeRecord o)
	{
		open = o;
		close = new Vector<TradeRecord>();
	}
}
