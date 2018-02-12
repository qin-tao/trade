package tao.trading;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import com.ib.client.Contract;


public class Instrument
{
	static SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm:ss");
	static SimpleDateFormat InternalDataFormatter = new SimpleDateFormat("yyyyMMdd  HH:mm");

	int inst_id;
	Contract contract;
	int id_history;
	int id_realtime;
	int id_ticker;
	String symbol;
	int priceType;
	int region1,region2;
	
	QuoteData lastQuote;
	double lastTick_bid, lastTick_ask, lastTick_last;
	int lastTick;

	double PIP_SIZE;
	
	//HashMap<String, QuoteData>[] qts;
	ArrayList<QuoteData>[] qts;
	QuoteData[][] qts_data;
	boolean qts_cached[];

	public Instrument(int inst_id, Contract contract, int id_history, int id_realtime, double pIP_SIZE, int priceType, int region1, int region2)
	{
		super();
		this.inst_id = inst_id;
		this.contract = contract;
		this.id_history = id_history;
		this.id_realtime = id_realtime;
		this.id_ticker = id_realtime + 1000;
		this.symbol = contract.m_symbol + "." + contract.m_currency;
		PIP_SIZE = pIP_SIZE;
		this.priceType = priceType;
		this.region1 = region1;
		this.region2 = region2;

		/*this.qts = new HashMap[Constants.TOTAL_CHARTS];
		this.qts_data = new QuoteData[Constants.TOTAL_CHARTS][];
		this.qts_cached = new boolean[Constants.TOTAL_CHARTS];
   		qts[Constants.CHART_5_SEC] = new HashMap<String, QuoteData>(1000);
   		qts[Constants.CHART_1_MIN] = new HashMap<String, QuoteData>(1000);
   		qts[Constants.CHART_5_MIN] = new HashMap<String, QuoteData>(1000);
   		qts[Constants.CHART_15_MIN] = new HashMap<String, QuoteData>(500);
   		qts[Constants.CHART_60_MIN] = new HashMap<String, QuoteData>(200);
   		qts[Constants.CHART_240_MIN] = new HashMap<String, QuoteData>(100);
   		qts[Constants.CHART_DAILY] = new HashMap<String, QuoteData>(50);*/
		this.qts = new ArrayList[Constants.TOTAL_CHARTS];
		this.qts_data = new QuoteData[Constants.TOTAL_CHARTS][];
		this.qts_cached = new boolean[Constants.TOTAL_CHARTS];
   		qts[Constants.CHART_5_SEC] = new ArrayList<QuoteData>();
   		qts[Constants.CHART_1_MIN] = new ArrayList<QuoteData>();
   		qts[Constants.CHART_5_MIN] = new ArrayList<QuoteData>();
   		qts[Constants.CHART_15_MIN] = new ArrayList<QuoteData>();
   		qts[Constants.CHART_60_MIN] = new ArrayList<QuoteData>();
   		qts[Constants.CHART_240_MIN] = new ArrayList<QuoteData>();
   		qts[Constants.CHART_DAILY] = new ArrayList<QuoteData>();

	}

	
	public String getSymbol()
	{
		return symbol;
	}

	public int getId_history()
	{
		return id_history;
	}

	public void setId_history(int id_history)
	{
		this.id_history = id_history;
	}

	public int getId_realtime()
	{
		return id_realtime;
	}

	public void setId_realtime(int id_realtime)
	{
		this.id_realtime = id_realtime;
	}

	public int getId_ticker() {
		return id_ticker;
	}


	public void setId_ticker(int id_ticker) {
		this.id_ticker = id_ticker;
	}


	public double getPIP_SIZE()
	{
		return PIP_SIZE;
	}

	public Contract getContract()
	{
		return contract;
	}

	public int getPriceType()
	{
		return priceType;
	}


	public void setPriceType(int priceType)
	{
		this.priceType = priceType;
	}


	public QuoteData[] getQuoteData( int chart )
	{
		//if ( qts_cached[chart] == true )
		//{
		//return qts_data[chart];
		//}
		//else  // do not optimize for now
		{
			//Object[] quotes = this.qts[chart].toArray();
			//Arrays.sort(quotes);
			//qts_data[chart] = Arrays.copyOf(quotes, quotes.length, QuoteData[].class);
			//qts_cached[chart] = true;
			//return qts_data[chart] ;
		}

		QuoteData quotes[] = new QuoteData[qts[chart].size()];
		 return this.qts[chart].toArray(quotes);

		/*int default_chart_size = 120;
		if ( chart == Constants.CHART_15_MIN)
			default_chart_size = 480;
		else if ( chart == Constants.CHART_5_MIN)
			default_chart_size = 1460;  // 480 * 3		
		
		int lastElementPos = qts[chart].size()-1;
		int firstElementPos = (lastElementPos - default_chart_size < 0 )?0:lastElementPos - default_chart_size; 
		List<QuoteData> list = qts[chart].subList(firstElementPos, lastElementPos);
	    QuoteData quotes[] = new QuoteData[list.size()];
		return this.qts[chart].toArray(quotes);*/
	}
	
	public void createOrUpdateQuotes(QuoteData data, int chart1, int chart2, int chart3, int chart4, int chart5, int chart6, int chart7, int chart8, int chart9 )
	{
		if ( chart1 >= 0 )
			createOrUpdateQuotes(chart1, data );
		if ( chart2 >= 0 )
			createOrUpdateQuotes(chart2, data );
		if ( chart3 >= 0 )
			createOrUpdateQuotes(chart3, data );
		if ( chart4 >= 0 )
			createOrUpdateQuotes(chart4, data );
		if ( chart5 >= 0 )
			createOrUpdateQuotes(chart5, data );
		if ( chart6 >= 0 )
			createOrUpdateQuotes(chart6, data );
		if ( chart7 >= 0 )
			createOrUpdateQuotes(chart7, data );
		if ( chart8 >= 0 )
			createOrUpdateQuotes(chart8, data );
		if ( chart9 >= 0 )
			createOrUpdateQuotes(chart9, data );
		/*
		createOrUpdateQuotes(Constants.CHART_5_MIN, data );
		createOrUpdateQuotes(Constants.CHART_15_MIN, data );
		createOrUpdateQuotes(Constants.CHART_60_MIN, data );
		createOrUpdateQuotes(Constants.CHART_240_MIN, data );*/
		lastQuote = data;
	}

	public void createOrUpdateQuotesFromRealTime(QuoteData data )
	{
		createOrUpdateQuotes(Constants.CHART_1_MIN, data );
		createOrUpdateQuotes(Constants.CHART_5_MIN, data );
		createOrUpdateQuotes(Constants.CHART_15_MIN, data );
		createOrUpdateQuotes(Constants.CHART_60_MIN, data );
		createOrUpdateQuotes(Constants.CHART_240_MIN, data );
		createOrUpdateQuotes(Constants.CHART_DAILY, data );
	}

	
	private void createOrUpdateQuotes( int CHART, final QuoteData data ){
		String time = data.time;
		int hr = new Integer(time.substring(10,12).trim());
		int minute = new Integer(time.substring(13,15));

		if ( CHART == Constants.CHART_3_MIN )
			minute = minute - minute%3;
		else if ( CHART == Constants.CHART_5_MIN )
			minute = minute - minute%5;
		else if ( CHART == Constants.CHART_10_MIN )
			minute = minute - minute%10;
		else if ( CHART == Constants.CHART_15_MIN )
			minute = minute - minute%15;
		else if ( CHART == Constants.CHART_30_MIN )
			minute = minute - minute%30;
		else if ( CHART == Constants.CHART_60_MIN ){
			minute = 0;
		}
		else if ( CHART == Constants.CHART_240_MIN ){
			hr = hr- hr%4;
			minute = 0;
			try
			{
				Date date = IBDataFormatter.parse(time);
				Calendar calendar = Calendar.getInstance();
				calendar.setTime( date );
				int weekday = calendar.get(Calendar.DAY_OF_WEEK);
				int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
				
				if (( weekday ==  Calendar.SUNDAY ) && ( hour_of_day < 20 ))
				{
					// this date will be conslidate to last friday's 16:00 bar
					long datel = date.getTime(); // milliseconds
					datel -= 48*60*60*1000L;
					
					date.setTime(datel);
					time = InternalDataFormatter.format(date);
				}
			}
			catch ( Exception e){
				e.printStackTrace();
			}
		}
		else if ( CHART == Constants.CHART_DAILY ){
			hr = minute = 0;
		}
		
		String hrStr = new Integer(hr).toString();
		if ( hr < 10 )
			hrStr = "0" + new Integer(hr).toString();
		else
			hrStr = new Integer(hr).toString();

		String minStr = new Integer(minute).toString();
		if ( minute < 10 )
			minStr = "0" + new Integer(minute).toString();
		else
			minStr = new Integer(minute).toString();
		
		String indexStr = time.substring(0,10) + hrStr + ":" + minStr + ":00";
		
		/*
		QuoteData qdata = qts[CHART].get(indexStr);
		if ( qdata == null ){
			qdata = new QuoteData();
			qdata.open = data.open;
			qdata.high = data.high;
			qdata.low = data.low;
			qdata.close = data.close;
			qdata.time = indexStr;
			qts[CHART].put( indexStr, qdata);
		}
		else{
			qdata.close = data.close;
			if (data.high > qdata.high)
				qdata.high = data.high;
			if (data.low < qdata.low )
				qdata.low = data.low;
			qdata.numOfUpdates++;
		}*/

		ArrayList<QuoteData> qds = qts[CHART];

		if ( qds.size() == 0 ){
			QuoteData newQuoteData = new QuoteData();
			newQuoteData.open = data.open;
			newQuoteData.high = data.high;
			newQuoteData.low = data.low;
			newQuoteData.close = data.close;
			newQuoteData.time = indexStr;
			qds.add(0,newQuoteData); 
		}
		else{
			QuoteData last_data = qds.get(qds.size()-1);
			if (last_data.time.equals(indexStr)){ 
				last_data.close = data.close;
				if (data.high > last_data.high)
					last_data.high = data.high;
				if (data.low < last_data.low )
					last_data.low = data.low;
				last_data.numOfUpdates++;
			}
			else if ( indexStr.compareTo(last_data.time) > 0 ){  // later than any element in the array
				QuoteData newQuoteData = new QuoteData();
				newQuoteData.open = data.open;
				newQuoteData.high = data.high;
				newQuoteData.low = data.low;
				newQuoteData.close = data.close;
				newQuoteData.time = indexStr;
				qds.add(qds.size(),newQuoteData); // this goes to the end of the list
				checkQtsSize(CHART);
				
			}
			else{  // this is to insert in the middle
				for ( int i = 0; i < qds.size(); i++){
					QuoteData qdata = qds.get(i);
					if (qdata.time.equals(indexStr)){
						qdata.close = data.close;
						if (data.high > qdata.high)
							qdata.high = data.high;
						if (data.low < qdata.low )
							qdata.low = data.low;
						break;
					}
					else if ( indexStr.compareTo(qdata.time) < 0 ){  // find the next element that is bigger, but there is still no match
						QuoteData newQuoteData = new QuoteData();
						newQuoteData.open = data.open;
						newQuoteData.high = data.high;
						newQuoteData.low = data.low;
						newQuoteData.close = data.close;
						newQuoteData.time = indexStr;
						qds.add(i,newQuoteData);

						checkQtsSize(CHART);

						/*
						// check size
						int default_chart_size = 240;
						if ( CHART == Constants.CHART_15_MIN)
							default_chart_size = 960;
						else if ( CHART == Constants.CHART_5_MIN)
							default_chart_size = 3000;  // 480 * 3		
						if (qds.size() > default_chart_size){
							//qds.remove(0);
						}*/
						break;
					}
				}
			}
		}
		//qts_cached[CHART] = false;
		
		// check size
		// check size
	}

	void checkQtsSize( int CHART ){
		// check size
		int default_chart_size = 480;
		if ( CHART == Constants.CHART_15_MIN)
			default_chart_size = 960;
		else if ( CHART == Constants.CHART_5_MIN)
			default_chart_size = 3000;  // 480 * 3		
		else if ( CHART == Constants.CHART_1_MIN)
			default_chart_size = 7200;  // 480 * 3		
		if (qts[CHART].size() > default_chart_size){
			qts[CHART].remove(0);
		}
	}
	
	
	
	public void createOrUpdateQuotesByTicker( int CHART, double bid, double ask )
	{
		//System.out.println("update chart " + CHART + " " + bid + " " + ask);
		/*
		Entry<String, QuoteData> maxEntry = null;
		for(Entry<String, QuoteData> entry : qts[CHART].entrySet()) {
		    if (maxEntry == null || (entry.getValue().time.compareTo( maxEntry.getValue().time) > 0)) {
		        maxEntry = entry;
		    }
		}

		
		if ( bid > 0 ){
			//if ( bid > maxEntry.getValue().close )
			//	maxEntry.getValue().close = bid;
			if ( bid > maxEntry.getValue().high )
				maxEntry.getValue().high = bid;
		}
	
		if ( ask > 0 ){
			//if ( ask < maxEntry.getValue().close)
			//	maxEntry.getValue().close = ask;
			if ( ask < maxEntry.getValue().low)
				maxEntry.getValue().low = ask;
		}*/
		
		ArrayList<QuoteData> qds = qts[CHART];
		if ( qds.size() > 0 ){
			QuoteData last_data = qds.get(qds.size()-1);	// only update the last one
			if ( bid > 0 ){
				if ( bid > last_data.high )
					last_data.high = bid;
			}
			if ( ask > 0 ){
				if ( ask < last_data.low)
					last_data.low = ask;
			}
		}
	}

	
/*	
	public void replaceQuotes( int chart, QuoteData data )
	{
		String indexStr = data.time.substring(0,15);
		data.time = indexStr;
		data.updated = true;
		
		qts[chart].put( indexStr, data);
		qts_cached[chart] = false;
	}*/


	public HashMap<String, QuoteData> rebuildQuote( int targetChart, QuoteData[] baseQuotes )
	{
		HashMap<String, QuoteData> quotes = new HashMap<String, QuoteData>(500);
		
		int len = baseQuotes.length;
		
		for ( int i = 0; i < len; i++)
		{
			String time = baseQuotes[i].time;
			int hr = new Integer(time.substring(10,12).trim());
			int minute = new Integer(time.substring(13,15));

			if ( targetChart == Constants.CHART_3_MIN )
				minute = minute - minute%3;
			else if ( targetChart == Constants.CHART_5_MIN )
				minute = minute - minute%5;
			else if ( targetChart == Constants.CHART_10_MIN )
				minute = minute - minute%10;
			else if ( targetChart == Constants.CHART_15_MIN )
				minute = minute - minute%15;
			else if ( targetChart == Constants.CHART_30_MIN )
				minute = minute - minute%30;
			else if ( targetChart == Constants.CHART_60_MIN )
				minute = 0;
			else if ( targetChart == Constants.CHART_240_MIN )
			{
				hr = hr- hr%4;
				minute = 0;
				
				try
				{
					Date date = InternalDataFormatter.parse(time);
					Calendar calendar = Calendar.getInstance();
					calendar.setTime( date );
					int weekday = calendar.get(Calendar.DAY_OF_WEEK);
					int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
					
					if (( weekday ==  Calendar.SUNDAY ) && ( hour_of_day < 20 ))
					{
						// this date will be conslidate to last friday's 16:00 bar
						long datel = date.getTime(); // milliseconds
						datel -= 48*60*60*1000;
						
						date.setTime(datel);
						time = InternalDataFormatter.format(date);
					}
				}
				catch ( Exception e)
				{
					System.out.println("can not parse date during 240 conversion " + baseQuotes[i].time);
				}
			}
			
			String hrStr = new Integer(hr).toString();
			if ( hr < 10 )
				hrStr = "0" + new Integer(hr).toString();
			else
				hrStr = new Integer(hr).toString();

			String minStr = new Integer(minute).toString();
			if ( minute < 10 )
				minStr = "0" + new Integer(minute).toString();
			else
				minStr = new Integer(minute).toString();
			
			//String indexStr = baseQuotes[i].time.substring(0,13) + minStr;
			String indexStr = time.substring(0,10) + hrStr + ":" + minStr + ":00";
			
			QuoteData qdata = quotes.get(indexStr);
			if ( qdata == null )
			{
				qdata = new QuoteData();
				qdata.open = baseQuotes[i].open;
				qdata.high = baseQuotes[i].high;
				qdata.low = baseQuotes[i].low;
				qdata.close = baseQuotes[i].close;
				qdata.time = indexStr;
			}
			else
			{
				if (baseQuotes[i].high > qdata.high)
					qdata.high = baseQuotes[i].high;
				if (baseQuotes[i].low < qdata.low )
					qdata.low = baseQuotes[i].low;

				qdata.close = baseQuotes[i].close;

			}
			
			quotes.put( indexStr, qdata);

		
		}

		qts_cached[targetChart] = false;
		
		return quotes;
	}


	public QuoteData getLastQuote()
	{
		return lastQuote;
	}


	public int getRegion1() {
		return region1;
	}


	public int getRegion2() {
		return region2;
	}



	
	
}
