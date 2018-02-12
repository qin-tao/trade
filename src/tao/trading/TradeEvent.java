package tao.trading;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Iterator;

import tao.trading.strategy.util.NameValue;

public class TradeEvent
{
	static SimpleDateFormat InternalDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm");

	// EVENTS
	public static String TRADE_CREATED = "Trade created";
	public static String TRADE_PlACED = "Trade placed";
	public static String TRADE_DETECTED = "Trade detected";
	public static String TRADE_FILLED = "Trade filled";
	public static String TRADE_STOPPEDOUT = "Stopped Out";
	public static String TRADE_REVERSE = "Reverse Trade";
	public static String NEW_LOW = "NewLow";
	public static String NEW_HIGH = "NewHigh";
	
	public static String ENTRY_NEW_LOW = "ENTRY_NEW_LOW";
	public static String ENTRY_NEW_HIGH = "ENTRY_NEW_HIGH";
	public static String ENTRY_BREAK_HIGHER_HIGH = "ENTRY_BREAK_HIGHER_HIGH";
	public static String ENTRY_BREAK_LOWER_LOW = "ENTRY_BREAK_LOWER_LOW";
	
	public static String CONTR_BREAK_OUT_BAR_60 = "Contra BreakOut Bar 60";
	public static String CONTR_BREAK_OUT_BAR_15 = "Contra BreakOut Bar 15";

	public static String SPIKE_CONTRA_MOVE_5S = "Spike contraMove 5S";
	public static String SPIKE_CONTRA_MOVE_1M = "Spike ContraMove 1M";

	public static String CONSEC_CONTRA_MOVE_1M = "Consecutive ContraMove 1M";
	public static String CONSEC_CONTRA_MOVE_5M = "Consectuive ContraMove 5M";
	public static String CONSEC_CONTRA_MOVE_15M = "Consecutive ContraMove 15M";
	public static String CONSEC_CONTRA_MOVE_60M = "Consecutive ContraMove 60M";

	public static String PROFIT_MOVE_1M = "ProfitMove 1M";
	public static String PROFIT_MOVE_5M = "ProfitMove 5M";
	public static String PROFIT_MOVE_15M = "ProfitMove 15M";
	public static String PROFIT_MOVE_60M = "ProfitMove 60M";

	public static String PEAK_LOW_15 = "Peak Low 15";
	public static String PEAK_LOW_60 = "Peak Low 60";
	public static String PEAK_LOW_240 = "Peak Low 240";
	public static String PEAK_HIGH_15 = "Peak High 15";
	public static String PEAK_HIGH_60 = "Peak High 60";
	public static String PEAK_HIGH_240 = "Peak High 240";
	public static String STOP_SIZE_PROFIT_REACHED = "Stop Size Profit Reached";
	public static String QUICK_PROFIT = "Quick Profit";
	
	
	// NAME VALUE PAIR ATTRIBUTES
	public static String NAME_PULLBACK_SIZE = "PULLBACK_SIZE";
	public static String NAME_MOVE_SIZE = "MOVE_SIZE";
	public static String NAME_STOP_SIZE_PROFIT = "Profit(StopSize)";
	public static String NAME_ENTRY_PRICE = "Entry Price";
	public static String NAME_START_TIME = "Push StartTime";
	public static String NAME_PRICE = "Price";
	public static String PREVIOUS_PUSH_SIZE = "Prev PushSize";
	
	String eventName;
	String time;
	String header;
	public boolean processed;
	//String description;
	
	public ArrayList<NameValue> nameValues = new ArrayList<NameValue>();
	
	public TradeEvent(String event, String time )
	{
		super();
		this.eventName = event;
		this.time = time;
	}

	
	public String getHeader()
	{
		return header;
	}


	public void setHeader(String header)
	{
		this.header = header;
	}


	public String getEventName()
	{
		return eventName;
	}

	public String getTime()
	{
		return time;
	}

	public void addNameValue(String name, String value)
	{
		nameValues.add(new NameValue(name, value));
	}
	
	public void addNameValue(NameValue nv)
	{
		nameValues.add(nv);
	}

	@Override
	public String toString()
	{
		String s = "";
		if (( header != null ) && ( header.length() > 0 ))
			s = header;
		
		StringBuffer sb = new StringBuffer(s + time + " - " + eventName);
		
		Iterator<NameValue> it = nameValues.iterator();
		while ( it.hasNext())
		{
			NameValue nv = it.next();
			sb.append( " " + nv.getName() + "=" + nv.getValue().toString());
		}
		
		if ( processed == true )
			sb.append(" *");
		
		return sb.toString();
	}
	

	
	
}
