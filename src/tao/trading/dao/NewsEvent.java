package tao.trading.dao;

import java.util.Date;

public class NewsEvent
{
	Date time;
	String currency;
	String importance;
	String eventName;
	
	public NewsEvent(Date time, String currency, String importance, String eventName)
	{
		super();
		this.time = time;
		this.currency = currency;
		this.importance = importance;
		this.eventName = eventName;
	}

	
	
	public Date getTime()
	{
		return time;
	}



	public String getCurrency()
	{
		return currency;
	}



	public String getImportance()
	{
		return importance;
	}



	public String getEventName()
	{
		return eventName;
	}



	@Override
	public String toString()
	{
		return "NewsEvent [" + time + ", " + currency + ", " + importance + ", " + eventName + "]";
	}
	
	
}
