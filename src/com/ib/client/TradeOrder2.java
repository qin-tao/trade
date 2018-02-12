package com.ib.client;

import java.io.Serializable;
import java.util.Vector;

public class TradeOrder2 implements Serializable{
	
	public String tradeType;
	public String tradeAction;
	public Vector<TradeRecord> open = new Vector<TradeRecord>();
	public Vector<TradeRecord> close = new Vector<TradeRecord>();

	public TradeOrder2( String type, String action )
	{
		tradeType = type;
		tradeAction = action;
	}
}
