package tao.trading.strategy.tm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.EmailSender;
import tao.trading.Instrument;
import tao.trading.MATouch;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.NewsEvent;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

public class NE_1_NewsBreakOut extends TradeManager2
{
	boolean CNT = false;;
	static boolean newsCalendarLoaded = false;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	int DEFAULT_PROFIT_TARGET = 400;
	QuoteData currQuoteData, lastQuoteData;
//	int firstRealTimeDataPosL = 0;
	double lastTick_bid, lastTick_ask, lastTick_last;
	int lastTick;
	long firstRealTime = 0;
	int bigChartState;
	
	// important switch
	boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean reverse_trade = false; // reverse after stopped out
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;
	
	boolean market_order = true;
	String checkPullbackSetup_start_date = null;
	boolean trade_detect_email_notifiy = true;
	
	/********************************
	 *  reverse trade setting
	 *******************************/
	boolean average_up = false;
	boolean tip_reverse = true;
	boolean stop_reverse = true;
	static ArrayList<NewsEvent> news_events;

	
	public NE_1_NewsBreakOut()
	{
		super();
	}


	public NE_1_NewsBreakOut(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
	{
		super(ib_account, m_client, instrument.getContract(), symbol_id);
		this.IB_ACCOUNT = ib_account;
		this.instrument = instrument;
		this.symbol = instrument.getSymbol();
	   	this.PIP_SIZE = instrument.getPIP_SIZE();
	   	this.exchangeRate = exchangeRate.get(instrument.getContract().m_currency);
	   	this.currency = instrument.getContract().m_currency;
	   	this.strategy = stragety;
	   	this.MODE = strategy.mode;
	   	this.logger = strategy.logger;
	}
	
	
	
	public void loadNewsCalendar()
	{
		System.out.println("load news event " + InternalDataFormatter.format(new Date()));
		
		String EVENT_FILE = "Calendar.csv";
		SimpleDateFormat csvDataFormatter = new SimpleDateFormat("EEE MMM dd yyyy HH:mm zzz");

		news_events = new ArrayList<NewsEvent>();
		
		try 
        { 
			File f = new File(EVENT_FILE);
		   	FileReader fr = new FileReader(f);
	
			//logger.warning("Read CSV files......");
				
			BufferedReader br = new BufferedReader( fr ); 
	        String line = ""; 
	        StringTokenizer token = null; 
	        int lineNum = 0; 
	         
	        while((line = br.readLine()) != null)
	        { 
	        	lineNum++;
	        	if ( lineNum == 1 )
	        		continue;
	               
	            // break comma separated file line by line 
	            token = new StringTokenizer(line, ","); 
	               
	            int tokenNum = 0;
	            String date=null, time=null, timezone=null, currency=null, event=null, importance=null;
	            
	            while(token.hasMoreTokens()) 
	            {
	                tokenNum++; 
	            	
	            	if ( tokenNum == 1 )
	            		date = token.nextToken();
	            	else if ( tokenNum == 2 )
	            		time = token.nextToken();
	            	else if ( tokenNum == 3 )
	            		timezone = token.nextToken();
	            	else if ( tokenNum == 4 )
	            		currency = token.nextToken();
	            	else if ( tokenNum == 5 )
	            		event = token.nextToken();
	            	else if ( tokenNum == 6 )
	            		importance = token.nextToken();
	            	else if ( tokenNum >= 7 )
	            		break;
	            }
	            
	            if (!"Medium".equals(importance) && !"High".equals(importance))
	            	continue;
	            
	            if ( date.length() == 9 )
	            	date = date.substring(0, 8) + "0" + date.charAt(8);
	            
	            if (time.length() == 4)
	            	time = "0" + time;
	            
	            String eventDateTime = date + " 2013 " + time + " " + timezone;
	            
	            Date eventDate = null;
	            
	            try{
	            	eventDate = csvDataFormatter.parse(eventDateTime);
	            }
	            catch( java.text.ParseException e)
	            {
	            	continue;
	            }
				//warning("Input CSV:" + importance + " " + eventDateTime + " " + currency + " " + event + " " + importance);
				NewsEvent ne = new NewsEvent( eventDate, currency.toUpperCase(), importance, event ); 
				System.out.println( ne.toString());
				
				news_events.add(ne);
			}
	
			
	        fr.close();	
	        
	        // back up this file,
	     //   String currentDay = DateFormatter2.format(new Date());
	    //	String BACKUP_OPPORTUNITY_FILE = "./opp/" + currentDay + "_Opportunity.csv";
	    //	Path source = Paths.get(OPPORTUNITY_FILE);
	    //	Path target = Paths.get(BACKUP_OPPORTUNITY_FILE);
	    //	Files.copy(source, target,REPLACE_EXISTING, COPY_ATTRIBUTES);
	        
	    //    f.delete();  // we no longer need this file
	        //f.delete();//
	        
        }
        catch ( FileNotFoundException ex )
        {
	        	//System.out.println("External Command file not found");
	        	//e.printStackTrace();  ignore excetion as file does not exist is normal
		} 
	  	catch(Exception e) 
	 	{
	    	  e.printStackTrace();
	    }
	    finally
        {
				System.out.println();
	   	}

	}

	
	/*****************************************************************************************************************************
	 * 
	 * 
	 * Static Methods
	 * 
	 * 
	 *****************************************************************************************************************************/
	public void process( int req_id, QuoteData data )
	{
			if ( newsCalendarLoaded == false )
			{
				loadNewsCalendar();
				newsCalendarLoaded = true;
			}
			
			//System.out.print("NewsEvent..");
			//System.out.print(".NE.");
			if (( news_events== null ) || ( news_events.size() == 0 ))
				return;
	
			NewsEvent ne = null;
			
			Iterator<NewsEvent> it = news_events.iterator();
			while (it.hasNext())
			{
				ne = it.next();
				if (((ne.getTime().getTime() - data.timeInMillSec )/60000L) < -15)
				{
					warning( "remove news " + ne.toString() + " currentTime:" + data.time );
					it.remove();
					if (MODE != Constants.TEST_MODE) 
						saveCurrentEvents();
				}
				else
				{
				// allow multiple events to be triggered at the same time
					if ("USD".equals(ne.getCurrency()))
					{
						if ((req_id == 501 ) || ( req_id == 514 ))   // EUR.USD, USD.JPY
							detect(data, ne);
					}
					else if ("EUR".equals(ne.getCurrency()))
					{
						if ((req_id == 500 ) || ( req_id == 501 ))   // EUR.USD, EUR.JPY
							detect(data, ne);
					}
					else if ("JPY".equals(ne.getCurrency()))
					{
						if ((req_id == 501 ) || ( req_id == 514 ))   // EUR.JPY, USD.JPY
							detect(data, ne);
					}
					else if ("GBP".equals(ne.getCurrency()))
					{
						if ((req_id == 509 ) || ( req_id == 512 ))   // EUR.JPY, USD.JPY
							detect(data, ne);
					}
					else if ("AUD".equals(ne.getCurrency()))
					{
						if ((req_id == 518 ) || ( req_id == 519 ))   // EUR.JPY, USD.JPY
							detect(data, ne);
					}
					else if ("CAD".equals(ne.getCurrency()))
					{
						if ((req_id == 502 ) || ( req_id == 517 ))   // EUR.JPY, USD.JPY
							detect(data, ne);
					}
				}
			// allow multiple events to be triggered at the same time
				/*
				if ("USD".equals(ne.getCurrency()))
				{
					if (req_id == tradeManager[0].getInstrument().getId_realtime())  // EUR.USD
						((NE_1_NewsBreakOut)tradeManager[0]).detect(data, ne);
		
					if (req_id == tradeManager[14].getInstrument().getId_realtime())  // USD.JPY
						((NE_1_NewsBreakOut)tradeManager[14]).detect(data, ne);
				}
				else if ("EUR".equals(ne.getCurrency()))
				{
					if (req_id == tradeManager[0].getInstrument().getId_realtime())  // EUR.USD
						((NE_1_NewsBreakOut)tradeManager[0]).detect(data, ne);
		
					if (req_id == tradeManager[1].getInstrument().getId_realtime())  // EUR.JPY
						((NE_1_NewsBreakOut)tradeManager[1]).detect(data, ne);
				}
				else if ("JPY".equals(ne.getCurrency()))
				{
					if (req_id == tradeManager[1].getInstrument().getId_realtime())  // EUR.JPY
						((NE_1_NewsBreakOut)tradeManager[1]).detect(data, ne);
		
					if (req_id == tradeManager[14].getInstrument().getId_realtime())  // USD.JPY
						((NE_1_NewsBreakOut)tradeManager[14]).detect(data, ne);
				}
				else if ("GBP".equals(ne.getCurrency()))
				{
					if (req_id == tradeManager[9].getInstrument().getId_realtime())  // EUR.JPY
						((NE_1_NewsBreakOut)tradeManager[9]).detect(data, ne);
		
					if (req_id == tradeManager[12].getInstrument().getId_realtime())  // USD.JPY
						((NE_1_NewsBreakOut)tradeManager[12]).detect(data, ne);
				}
				else if ("AUD".equals(ne.getCurrency()))
				{
					if (req_id == tradeManager[18].getInstrument().getId_realtime())  // EUR.JPY
						((NE_1_NewsBreakOut)tradeManager[18]).detect(data, ne);
		
					if (req_id == tradeManager[19].getInstrument().getId_realtime())  // USD.JPY
						((NE_1_NewsBreakOut)tradeManager[19]).detect(data, ne);
				}
				else if ("CAD".equals(ne.getCurrency()))
				{
					if (req_id == tradeManager[2].getInstrument().getId_realtime())  // EUR.JPY
						((NE_1_NewsBreakOut)tradeManager[2]).detect(data, ne);
		
					if (req_id == tradeManager[17].getInstrument().getId_realtime())  // USD.JPY
						((NE_1_NewsBreakOut)tradeManager[17]).detect(data, ne);
				}*/
			}
		
		
		/*
		
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			if (req_id == tradeManager[i].getInstrument().getId_realtime())
			{
				Trade trade = tradeManager[i].getTrade();
				
				if (trade != null )    
				{
					if (trade.status.equals(Constants.STATUS_FILLED))
					{
						tradeManager[i].manage(data );
					}
				}
			}
		}*/
		
		

	}
	
	public Trade detect( QuoteData data, NewsEvent ne )
	{
		if ( trade != null )
			return trade;

		if ( findTradeHistory( InternalDataFormatter.format(ne.getTime())) != null )
			return null;

		int minuteToEvent = (int)((ne.getTime().getTime() - data.timeInMillSec)/60000L);
		//System.out.println("\n" + symbol+ " minute to Event" + minuteToEvent + " Event:" + ne.toString());
		if ((minuteToEvent < 5 ) && (minuteToEvent > -10))
		{
			//System.out.println(data.toString() + symbol + " approaching " + ne.toString() );
			//warning("triggered: " + ne.toString() + data.toString());
			if (MODE != Constants.TEST_MODE) 
				saveEventQuoteData(ne, data);
			
			QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
			int lastbar5 = quotes5.length - 1;
			
			QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
			int lastbar1 = quotes1.length - 1;
			if ( lastbar1 < 1 )
				return null;  // sometime this kicks off before getting first quote
			
			double avgSize1 = BarUtil.averageBarSizeOpenClose( quotes1 );

			int spike5S = (int)((data.close - data.open)/PIP_SIZE);
			if ( spike5S > 10 )
			{
				warning ( ne.toString());
				warning ( "spike UP " + spike5S + " detect on 5S enter trade by at " + data.close + " " + data.time);
				createOpenTrade(ne.getImportance().substring(0, 3), Constants.ACTION_BUY);
				trade.entryPrice = trade.triggerPrice = data.close;
				trade.POSITION_SIZE = POSITION_SIZE;
				//enterMarketPosition(trade.entryPrice);
				trade.detectTime = trade.entryTime = quotes1[lastbar1].time;
				trade.eventTime = InternalDataFormatter.format(ne.getTime());
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes1[lastbar1].time);
				tv.addNameValue("triggerPrice", new Double(trade.entryPrice).toString() ); 
				tv.addNameValue("triggerMove", new Integer(spike5S).toString() ); 
				trade.addTradeEvent(tv);
				return trade;
			}
			else if ( spike5S < -10 )
			{
				warning ( ne.toString());
				warning ( "spike DOWN " + spike5S + " detect on 5S enter trade by at " + data.close + " " + data.time);
				createOpenTrade(ne.getImportance().substring(0, 3), Constants.ACTION_SELL);
				trade.entryPrice = trade.triggerPrice = data.close;
				trade.POSITION_SIZE = POSITION_SIZE;
				//enterMarketPosition(trade.entryPrice);
				trade.detectTime = trade.entryTime = quotes1[lastbar1].time;
				trade.eventTime = InternalDataFormatter.format(ne.getTime());
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes1[lastbar1].time);
				tv.addNameValue("triggerPrice", new Double(trade.entryPrice).toString() ); 
				tv.addNameValue("triggerMove", new Integer(spike5S).toString() ); 
				trade.addTradeEvent(tv);
				return trade;
			}
			
			int spike1M =  (int)((quotes1[lastbar1-1].close - quotes1[lastbar1-1].open)/PIP_SIZE);
			if ( spike1M > 15 )
			{
				warning ( ne.toString());
				warning ( "spike UP " + spike1M + " detect on 1M enter trade by at " + data.close + " " + quotes1[lastbar1-1].time);
				createOpenTrade(ne.getImportance().substring(0, 3), Constants.ACTION_BUY);
				trade.entryPrice = trade.triggerPrice = data.close;
				trade.POSITION_SIZE = POSITION_SIZE;
				//enterMarketPosition(trade.entryPrice);
				trade.detectTime = trade.entryTime = quotes1[lastbar1-1].time;
				trade.eventTime = InternalDataFormatter.format(ne.getTime());
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes1[lastbar1].time);
				tv.addNameValue("triggerPrice", new Double(trade.entryPrice).toString() ); 
				tv.addNameValue("triggerMove", new Integer(spike1M).toString() ); 
				trade.addTradeEvent(tv);
				return trade;
			}
			else if ( spike1M < -15 )
			{
				warning ( ne.toString());
				warning ( "spike DOWN " + spike1M + " detect on 1M enter trade by at " + data.close + " " + quotes1[lastbar1-1].time);
				createOpenTrade(ne.getImportance().substring(0, 3), Constants.ACTION_SELL);
				trade.entryPrice = trade.triggerPrice = data.close;
				trade.POSITION_SIZE = POSITION_SIZE;
				//enterMarketPosition(trade.entryPrice);
				trade.detectTime = trade.entryTime = quotes1[lastbar1-1].time;
				trade.eventTime = InternalDataFormatter.format(ne.getTime());
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes1[lastbar1].time);
				tv.addNameValue("triggerPrice", new Double(trade.entryPrice).toString() ); 
				tv.addNameValue("triggerMove", new Integer(spike1M).toString() ); 
				trade.addTradeEvent(tv);
				return trade;
			}
			
		}
		
		return null;
	}

	
	public static String getCurrencyNews( String symbol)
	{
		int count = 0;
		StringBuffer newsInStr = new StringBuffer();
		if ( news_events != null )
		{	
			Iterator<NewsEvent> it = news_events.iterator();
			while (it.hasNext() && (count < 3))
			{
				NewsEvent ne = it.next();
				if (symbol.indexOf(ne.getCurrency()) != -1)
				{
					newsInStr.append(ne.toString() + "\n");
					count++;
				}
			}
		}
		
		return newsInStr.toString();
	}
	
	public static String getNext3News()
	{
		int count = 0;
		StringBuffer newsInStr = new StringBuffer();
		if ( news_events != null )
		{	
			Iterator<NewsEvent> it = news_events.iterator();
			while (it.hasNext() && (count < 3))
			{
				NewsEvent ne = it.next();
				if ("high".equalsIgnoreCase(ne.getImportance()))
				{
					newsInStr.append(ne.toString() + "\n");
					count++;
				}
			}
		}
		
		return newsInStr.toString();
	}
	
	
	private void saveEventQuoteData(NewsEvent ne, QuoteData data)
	{
		SimpleDateFormat fileDataFormatter = new SimpleDateFormat("yyyyMMdd_HHmm");

		try {
			FileWriter fw = new FileWriter(OUTPUT_DIR + "/" + strategy + "/" + symbol + "_" +  fileDataFormatter.format(ne.getTime())+".txt", true );
			fw.append(data.toString() + "\n");
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}

	private void saveCurrentEvents()
	{
		if (( news_events == null ) || ( news_events.size() == 0 ))
			return;
		
		try {

			FileWriter fw = new FileWriter(OUTPUT_DIR + "/" + strategy + "/currentEvents.txt");
			Iterator<NewsEvent> it = news_events.iterator();
			while (it.hasNext())
			{
				NewsEvent ne = it.next();
				fw.append( ne.toString() + "\n");
			}
			
			fw.close();
		} 
		catch (IOException e){
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	
	public Trade detect( QuoteData data )
	{
		return null;
	}
	
	
	public void entry( QuoteData data )
	{
		if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			checkStopTarget(data);

		if (trade != null)
		{
			if (Constants.ACTION_SELL.equals(trade.action))
			{
		//		trackPullBackTradeSell( data, 0);
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
		//		trackPullBackTradeBuy( data, 0 );
			}
		}
	}
		
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if ( trade != null )
		{	
			//if ( trade.status.equals(Constants.STATUS_OPEN))
			//	checkTradeExpiring_ByTime(data);
			
			//if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED)))
			{
				//exit123_1M(data);
			}		
		}
	}

	
	
	
	
	
	
	/***********************************************************************************************************************
	 * 
	 * 
	 *	Standard Interface 
	 * 
	 * 
	 ************************************************************************************************************************/
	
    public void run() {
    }

	
	public QuoteData[] getQuoteData(int CHART)
	{
		return instrument.getQuoteData(CHART);
	}


	public void checkOrderFilled(int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if (orderId == trade.stopId)
		{
			warning("order " + orderId + " stopped out ");
			trade.stopId = 0;

			cancelTargets();
			
			processAfterHitStopLogic_c();
			//removeTrade();

		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("limit order: " + orderId + " " + filled + " filled");
			trade.limitId1 = 0;

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.limitPos1, trade.limitPrice1);

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize += trade.limitPos1; //+= filled;
			trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			trade.stop = trade.limit1Stop;
			String oca = new Long(new Date().getTime()).toString();
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);
				if ( trade.targetPrice != 0 )
				{	
					trade.targetPos = trade.POSITION_SIZE;
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
				}

			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);
				if ( trade.targetPrice != 0 )
				{	
					trade.targetPos = trade.POSITION_SIZE;
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
				}
			}
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_PLACED;
		
		}
		else if ((orderId == trade.stopMarketId) && ( trade.stopMarketPosFilled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("stop market order: " + orderId + " " + filled + " filled");
			trade.stopMarketId = 0;

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.POSITION_SIZE, trade.stopMarketStopPrice);

			trade.stopMarketPosFilled = trade.POSITION_SIZE;
			trade.entryPrice = trade.stopMarketStopPrice;
			trade.remainingPositionSize = trade.POSITION_SIZE; //+= filled;
			trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			if ( trade.stop != 0 )
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_PLACED;
		
		}
		else if ((orderId == trade.limitId2)&& ( trade.limitPos2Filled == 0 ))
		{
			// not being used
		}
		else if ((orderId == trade.targetId1) && (trade.target1Reached == false))
		{
			warning( "target1 filled, " + " price: " + trade.targetPrice1);
			trade.targetId1 = 0;
			trade.target1Reached = true;
			trade.remainingPositionSize -= trade.targetPos1;
			cancelStop();
			
			warning(" remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				cancelTargets();
				removeTrade();
			}
			return;
		}
		else if ((orderId == trade.targetId2)&& (trade.target2Reached == false))
		{
			warning( "target2 filled, " + " price: " + trade.targetPrice2);
			trade.targetId2 = 0;
			trade.target2Reached = true;
			trade.remainingPositionSize -= trade.targetPos2;
			cancelStop();
			
			info(" remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				cancelTargets();
				removeTrade();
			}
			return;
		}
		else if (orderId == trade.targetId)
		{
			warning( "target filled, " + " price: " + trade.targetPrice);
			trade.targetId = 0;
			trade.targetReached = true;
			trade.remainingPositionSize -= filled;
			cancelStop();
			
			warning("remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				String oca = new Long(new Date().getTime()).toString();

				if (Constants.ACTION_SELL.equals(trade.action))
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
		
					trade.targetPrice = adjustPrice(trade.targetPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize, oca);
				}
				else if (Constants.ACTION_BUY.equals(trade.action))
				{
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

					trade.targetPrice = adjustPrice(trade.targetPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.remainingPositionSize, oca);
				}
			}
			else
			{
				cancelTargets();
				removeTrade();
			}
			return;
		}
	}


	

	
	//////////////////////////////////////////////////////////////////////////
	//
	//  Custom Methods
	//
	///////////////////////////////////////////////////////////////////////////
	
	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.high >= trade.limitPrice1) && (trade.limitPos1Filled == 0 ))
			{
				warning(" limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos1, trade.limitPrice1);
				trade.limitPos1Filled = trade.limitPos1;

				trade.entryPrice = trade.limitPrice1;
				trade.remainingPositionSize += trade.limitPos1Filled;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				trade.stop = trade.limit1Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId1 = 0;

				trade.status = Constants.STATUS_PLACED;
				trade.openOrderDurationInHour = 0;
				
			}

			if ((trade.limitId2 != 0) && (data.high >= trade.limitPrice2))
			{
				// this is for partial entry
				warning(" limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos2, trade.limitPrice2);
				trade.limitPos2Filled = trade.limitPos2;

				trade.remainingPositionSize += trade.limitPos2Filled;

				// calculate stop here
				trade.stop = trade.limit2Stop;
				if ( trade.stop == 0 )  
					trade.stop = adjustPrice(trade.limitPrice2 + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			
			if ((trade.stopMarketId != 0) && (data.low <= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.POSITION_SIZE;

				trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.POSITION_SIZE;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
				trade.status = Constants.STATUS_PLACED;
			}

			
			// check stop;
			if ((trade.stopId != 0) && (data.high > trade.stop))
			{
				info("stopped out @ " + trade.stop + " " + data.time);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				//trade.stoppedOutPos = lastbar;
				trade.stopId = 0;

				cancelTargets();

				//boolean reversequalified = true;
				//if (mainProgram.isNoReverse(symbol ))
				//	reversequalified = false;
				trade.status = Constants.STATUS_STOPPEDOUT;	
				trade.closeTime = quotes[lastbar].time;
				//processAfterHitStopLogic_c();
				removeTrade();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.low < trade.targetPrice1))
			{
				info("target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos1;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

				return;

			}
			else if ((trade.targetId2 != 0) && (data.low < trade.targetPrice2))
			{
				info("target2 hit, close " + trade.targetPos2 + " @ " + trade.targetPrice2);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos2, trade.targetPrice2);
				trade.targetId2 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos2;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				return;

			}
			else if ((trade.targetId != 0) && (data.low < trade.targetPrice))
			{
				warning("target hit, close " + trade.targetPos + " @ " + trade.targetPrice);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.targetPrice);
				trade.targetId = 0;

				cancelStop();
				trade.remainingPositionSize -= trade.targetPos;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				}
				return;

			}

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.low <= trade.limitPrice1) && (trade.limitPos1Filled == 0 ))
			{
				warning(" limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos1, trade.limitPrice1);
				trade.limitPos1Filled = trade.limitPos1;

				trade.entryPrice = trade.limitPrice1;
				trade.remainingPositionSize += trade.limitPos1Filled;
				trade.setEntryTime(quotes[lastbar].time);
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				// calculate stop here
				trade.stop = trade.limit1Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
						trade.remainingPositionSize, null);
				trade.limitId1 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;

			}

			// this is for partial entry
			if ((trade.limitId2 != 0) && (data.high <= trade.limitPrice2))
			{
				warning(" limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos2, trade.limitPrice2);
				trade.limitPos1Filled = trade.limitPos2;

				trade.remainingPositionSize += trade.limitPos2Filled;
				trade.setEntryTime(quotes[lastbar].time);
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				// calculate stop here
				trade.stop = trade.limit2Stop;
				if ( trade.stop == 0 )  
					trade.stop = adjustPrice(trade.limitPrice2 - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
						trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			if ((trade.stopMarketId != 0) && (data.high >= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.POSITION_SIZE;

				trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.POSITION_SIZE;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
				trade.status = Constants.STATUS_PLACED;
			}

			// check stop;
			if ((trade.stopId != 0) && (data.low < trade.stop))
			{
				info("stopped out @ " + trade.stop + " " + data.time);
				//trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;
				//trade.stoppedOutPos = lastbar;

				cancelTargets();
				trade.status = Constants.STATUS_STOPPEDOUT;	
				trade.closeTime = quotes[lastbar].time;
				//processAfterHitStopLogic_c();
				removeTrade();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.high > trade.targetPrice1))
			{
				info("target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos1;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				return;
			}
			else if ((trade.targetId2 != 0) && (data.high > trade.targetPrice2))
			{
				info("target2 hit, close " + trade.targetPos2 + " @ " + trade.targetPrice2);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos2, trade.targetPrice2);
				trade.targetId2 = 0;

				cancelStop();
				
				trade.remainingPositionSize -= trade.targetPos2;
				if (trade.remainingPositionSize <= 0)
					removeTrade();
				else
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				return;
			}
			else if ((trade.targetId != 0) && (data.high > trade.targetPrice))
			{
				warning("target hit, close " + trade.targetPos + " @ " + trade.targetPrice);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.targetPrice);
				trade.targetId = 0;

				cancelStop();
				removeTrade();
				return;
			}

		}
	}



	
	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
		int lastbar5 = quotes5.length - 1;

		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);

		double prevStop = trade.stop;
		String prevAction = trade.action;
		String prevType = trade.type;
		String prevEntryTime = trade.getEntryTime();
		double prevEntryPrice = trade.entryPrice;
		
		warning("check reverse logic");

		if (prevType == Constants.TRADE_CNT )
		{
			warning("prev trade was CNT, reverse does not apply");
			removeTrade();
			return;
		}

		int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
		int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, prevEntryTime, 2 );

		removeTrade();
		
		// check no_reverse list
		/*
		if (mainProgram.isNoReverse(symbol ))
		{
			warning("no reverse");
			return;
		}*/
		
		
		//BigTrend bt = TrendAnalysis.determineBigTrend( quotesL);
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotesL);
		warning(" trend is " + bt.direction);

		
		double reversePrice = prevStop;
		boolean reversequalified = false;

		if (Constants.ACTION_SELL.equals(prevAction))
		{
			// check if there is a big move against the trade
			/*
			double avgSize5 = Utility.averageBarSizeOpenClose( quotes5 );
			if ((( quotes5[lastbar5].close - quotes5[lastbar5].open ) > 3 * avgSize5 ) && (Math.abs(quotes5[lastbar5].close - quotes5[lastbar5].high) < 5 * PIP_SIZE))
				reversequalified = true;
			*/
			
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			warning("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
			
			if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 ) || 
			(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))) 
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				reversequalified = true;
				
				if ( (touch20MAPosL - firstBreakOutStartL) > 5)
				{
					double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
					double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
					if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
						reversequalified = false;
				}

			}
		}
		else if (Constants.ACTION_BUY.equals(prevAction))
		{
			/*
			double avgSize5 = Utility.averageBarSizeOpenClose( quotes5 );
			if ((( quotes5[lastbar5].close - quotes5[lastbar5].open ) > 3 * avgSize5 ) && (Math.abs(quotes5[lastbar5].close - quotes5[lastbar5].low) < 5 * PIP_SIZE))
				reversequalified = true;
			*/
			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 

			if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
			     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				reversequalified = true;

				if ( (touch20MAPosL - firstBreakOutStartL) > 5)
				{
					double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
					double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
					if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
						reversequalified = false;
				}

				// reverse;
				if ( reversequalified )
				{
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

					enterMarketPosition(reversePrice);
					return;
				}
			}
		}
		
		// reverse;
		if ( reversequalified )
		{
			createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.entryPrice = reversePrice;
			trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

			enterMarketPosition(reversePrice);
			return;
		}

	}

	
	
	public void trackTradeTickerEntry(int field, double price)
	{
		if (trade.filled == true)
			return;

		// ticker type: 1 = bid, 2 = ask, 4 = last, 6 = high, 7 = low, 9 = close

		/*
		if (field == 1) // bid
		{
			checkTriggerMarketSell(price);
		}
		else if (field == 2) // ask
		{
			checkTriggerMarketBuy(price);
		}*/
		
		if (( field == 4 ) && (trade != null ))
		{
			if (Constants.ACTION_SELL.equals( trade.action))
			{
				//checkTriggerMarketSell(price);
			}
			else if (Constants.ACTION_BUY.equals( trade.action))
			{
				//checkTriggerMarketBuy(price);
			}
		}
	}

	
	
	public void checkTradeExpiring_ByTime(QuoteData data)
	{
		if ( trade.status.equals(Constants.STATUS_OPEN) && ( trade.openOrderExpireInMill != 0 )) 
		{
			Date now = new Date();
			if (now.getTime() > trade.openOrderExpireInMill )
			{
				warning( "trade " + trade.action + " expired: detectTime "+ trade.detectTime);
				
				if ( trade.limitId1 != 0 )
				{
					cancelOrder(trade.limitId1);
					trade.limitId1 = 0;
				}
				if ( trade.stopMarketId != 0 )
				{
					cancelOrder(trade.stopMarketId);
					trade.stopMarketId = 0;
				}
				if ( trade.stopId != 0 )
				{
					cancelOrder(trade.stopId);
					trade.stopId = 0;
				}
				
				trade.status = Constants.STATUS_DETECTED;
			}
		}
	}

	
	
	public void checkTradeExpiring(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		
		int tradeDetectPos = Utility.findPositionByMinute( quotes, trade.detectTime, Constants.BACK_TO_FRONT);
		if ( lastbar == tradeDetectPos )
			return;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			double lowAfterDetect = Utility.getLow(quotes, tradeDetectPos+1,lastbar).low;
			
			if ( trade.status.equals(Constants.STATUS_OPEN)) 
			{
				if((trade.limitPrice1 - lowAfterDetect ) > 1.5 * FIXED_STOP * PIP_SIZE )
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as price move away");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
				else if (( data.timeInMillSec - trade.detectTimeInMill) > 5 * 3600000L)
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as time passed");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			double highAfterDetect = Utility.getHigh(quotes, tradeDetectPos+1,lastbar).high;

			if ( trade.status.equals(Constants.STATUS_OPEN))
			{
				if (( highAfterDetect - trade.limitPrice1) > 1.5 * FIXED_STOP * PIP_SIZE )
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as price moved away");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
				else if (( data.timeInMillSec - trade.detectTimeInMill) > 5 * 3600000L)
				{
					logger.warning(symbol + " trade " + trade.detectTime + " missed as time passed ");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
			}
		}
	}
	
	
	
	



	protected Trade findPastTradeHistory(String symbol, String action, String tradeType)
	{
		Iterator it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = (Trade) it.next();
			if (t.symbol.equals(symbol) && t.action.equals(action) && t.type.equals(tradeType))
				return t;
		}

		return null;

	}


	
	protected Trade findTradeHistory( String eventTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( eventTime.equals(t.eventTime))
				return t;
		}

		return null;

	}

	
	
	protected void moveStop( double stopPrice )
	{
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			double stop = adjustPrice(stopPrice, Constants.ADJUST_TYPE_UP);
			cancelOrder(trade.stopId);
			trade.stop = stopPrice;
			trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
			warning(" stop moved to " + stop + " orderId:" + trade.stopId );
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			double stop = adjustPrice(stopPrice, Constants.ADJUST_TYPE_DOWN);
			cancelOrder(trade.stopId);
			trade.stop = stopPrice;
			trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
			warning(" stop moved to " + stop + " orderId:" + trade.stopId );
		}
	}

	
	
	public void createTradeTargetOrder( double quantity, double price )
	{
		if (( trade != null ) && trade.status.equals(Constants.STATUS_PLACED))
		{
			if ( trade.targetId != 0 )
				cancelTargets();

			trade.targetPrice = price;
			trade.targetPos = (int)(POSITION_SIZE*quantity);
			trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
		}
		else
		{
			System.out.println(symbol + " Set Target, trade does not exist");
			System.out.println(symbol + " Set Target, trade does not exist");
			System.out.println(symbol + " Set Target, trade does not exist");
		}
	}
	
	

	public void emailNotifyEntry ( String entry_type, String time )
	{
		if ( MODE == Constants.SIGNAL_MODE )
		{
			if (!findEmailNotificationHistory( entry_type + time ))
			{ 
				EmailSender es = EmailSender.getInstance();
				es.sendYahooMail("302 Entry Detection", symbol +  " " + trade.action + " " + time + " " + entry_type );
				addEmailNotificationHistory(entry_type + time);
			}
		}
	}


	public boolean inTradingTime( int hour, int min )
	{
		//return true;
		int minute = hour * 60 + min;
		if (( minute >= 0) && (minute <= 420))//600
									return true;
								else
									return false;		
		/*
		switch ( TIME_ZONE)
		{
			case 23:  	if (( minute >= 90) && (minute <= 420))//600
							return true;
						else
							return false;
			
			case 12:  	if ((minute >= 1080) || (minute <= 420))//600
							return true;
						else
							return false;
			
			default:	return false;

		}*/
	}

	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public void exit123_1M( QuoteData data )
	{
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		double avgSize1 = BarUtil.averageBarSizeOpenClose( quotes1 );

		QuoteData[] quote15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quote15.length - 1;
		
		//QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		//int lastbar60 = quotes60.length - 1;
		
		//QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
		//int lastbar5 = quotes5.length - 1;
		//double[] ema20_5 = Indicator.calculateEMA(quotes60, 20);
		

		MATouch[] maTouches = null;

		
		int LOT_SIZE = POSITION_SIZE/2;

		
		/*********************************************************************
		 *  status: closed
		 *********************************************************************/
		if  (Constants.STATUS_CLOSED.equals(trade.status))
		{
			try{
			
				Date closeTime = IBDataFormatter.parse(trade.closeTime);
				Date currTime = IBDataFormatter.parse(data.time);
				
				if ((currTime.getTime() - closeTime.getTime()) > (60 * 60000L))
				{
					removeTrade();
					return;
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return;
			}


			/******************
			 * to do something
			 */
			// not to do anything at the moment
			return;

		}
		
		///////////////////////////////////////////////////////////////////////////

		
		/*********************************************************************
		 *  status: stopped out, check to reverse
		 *********************************************************************/

		
		
		/*********************************************************************
		 *  status: detect an counter spike move
		 *********************************************************************/

		
		
		
		/*********************************************************************
		 *  status: detect profit and move stop
		 *********************************************************************/
		//double profit = Math.abs( quotes60[lastbar60].close - trade.entryPrice)/ PIP_SIZE;
		double profitInPip = 0;
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInPip = (trade.entryPrice - data.low)/PIP_SIZE;
			if (profitInPip > FIXED_STOP )
				trade.reach1FixedStop = true;

			if (profitInPip > 2 * FIXED_STOP )
			{
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
//				addTradeEvent( "2 times stop size profit reached");
			}
					
			//profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			//if (( trade.getProgramTrailingStop() != 0 ) && ((trade.getProgramTrailingStop() - data.close)/PIP_SIZE < profitInRisk ))
			//	profitInRisk = (trade.getProgramTrailingStop() - data.close)/PIP_SIZE;
			/*
			trade.adjustProgramTrailingStop(data);
			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.high > trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}*/
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			profitInPip = ( data.high - trade.entryPrice )/PIP_SIZE;
			if (profitInPip > FIXED_STOP )
				trade.reach1FixedStop = true;

			if ( profitInPip > 2 * FIXED_STOP )
			{
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
	//			addTradeEvent( "2 times stop size profit reached");
			}

			//profitInRisk =  ( data.close - trade.stop )/PIP_SIZE;
			//if (( trade.getProgramTrailingStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
			//	profitInRisk = ( data.close - trade.getProgramTrailingStop() )/PIP_SIZE;

			/*
			trade.adjustProgramTrailingStop(data);
			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.low < trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}*/
		}
		

		
		
		/*********************************************************************
		 *  status: detect peaks
		 *********************************************************************/
		int tradeEntryPos = Utility.findPositionByMinute( quotes1, trade.entryTime, Constants.BACK_TO_FRONT);
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int wave2PtL = 0;
			
			int pushStart = Utility.getHigh(quotes1, tradeEntryPos-5, (tradeEntryPos+5>lastbar1)?lastbar1: tradeEntryPos+5).pos;
			
			PushList pushList = PushSetup.getDown2PushList(quotes1, pushStart, lastbar1);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes1[lastPush.prePos].low;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotes1[phl.prePos].low;
				int positive = phls[lastPushIndex].pullBackMACDPositive;

		//		addTradeEvent( "peack low reached: " + quotes1[phl.prePos].low);
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if (!exitTradePlaced())
				{
					
					if ( numOfPush == 1 )
					{
						lastBreakOut1 = pushList.phls[0].breakOutSize;
						lastPullBack1 = pushList.phls[0].pullBackSize;
						lastBreakOut2 = 0;
						lastPullBack2 = 0;
					}
					else  
					{
						lastBreakOut1 = pushList.phls[lastPushIndex].breakOutSize;
						lastBreakOut2 = pushList.phls[lastPushIndex-1].breakOutSize;
						lastPullBack1 = pushList.phls[lastPushIndex].pullBackSize;
						lastPullBack2 = pushList.phls[lastPushIndex-1].pullBackSize;
					}
							
					for ( int i = 1; i < numOfPush; i++ )
					{
						if ( phls[i].breakOutSize > 0.3 * FIXED_STOP * PIP_SIZE)
						{
							largePushIndex = i;
							break;
						}
					}
					
					
					/******************************************************************************
					// look for pullbacks
					 * ****************************************************************************/

					if ( pullback  > 10 * PIP_SIZE)
					{
						warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
						takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						//takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
						takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
					}
				}
				
				
				/******************************************************************************
				// move stops
				 * ****************************************************************************/
				/*
				if (trade.reach2FixedStop == true)
				{	
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = phl.prePos+1; j < phl.curPos; j++)
						if ( quotes60[j+1].high > quote15[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{
							//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							//trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
				}*/
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// calculate touch 20MA point
			int wave2PtL = 0;
			int pushStart = Utility.getLow(quotes1, tradeEntryPos-5, (tradeEntryPos+5>lastbar1)?lastbar1: tradeEntryPos+5).pos;
			PushList pushList = PushSetup.getUp2PushList(quotes1, pushStart, lastbar1);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes1[lastPush.prePos].high;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = quotes1[phl.prePos].high - phl.pullBack.low;
				int negatives = phls[lastPushIndex].pullBackMACDNegative;

			//	addTradeEvent( "peack high reached: " + quotes1[phl.prePos].high);
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if (!exitTradePlaced())
				{
					if ( numOfPush == 1 )
					{
						lastBreakOut1 = pushList.phls[0].breakOutSize;
						lastPullBack1 = pushList.phls[0].pullBackSize;
						lastBreakOut2 = 0;
						lastPullBack2 = 0;
					}
					else  
					{
						lastBreakOut1 = pushList.phls[lastPushIndex].breakOutSize;
						lastBreakOut2 = pushList.phls[lastPushIndex-1].breakOutSize;
						lastPullBack1 = pushList.phls[lastPushIndex].pullBackSize;
						lastPullBack2 = pushList.phls[lastPushIndex-1].pullBackSize;
					}
					
					for ( int i = 1; i < numOfPush; i++ )
					{
						if ( phls[i].breakOutSize > 0.5 * FIXED_STOP * PIP_SIZE)
						{
							largePushIndex = i;
							break;
						}
					}
	
	
					if ( pullback  > 10 * FIXED_STOP * PIP_SIZE)
					{
						warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
						takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						warning(data.time + " take prift buy on MACD with pullback > 1.5");
						//takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
						takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
						//trade.createProgramTrailingRange( triggerPrice, triggerPrice + 3 * FIXED_STOP * PIP_SIZE, 1.5*FIXED_STOP * PIP_SIZE );
					}
					
				}

				
				/******************************************************************************
				// move stop
				 * ****************************************************************************/
				/*
				if (trade.reach2FixedStop == true)
				{	
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}*/
				
			}
		}


		/******************************************************************************
		// smaller timefram for detecting sharp pullbacks
		 * ****************************************************************************/
	
	}
	
	
	
	public void exit123_new9_checkReversalsOrEalyExit( QuoteData data/*, MABreakOutList mol*/ )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes);
		//BigTrend bt = determineBigTrend2( quotes240);
				//BigTrend bt = determineBigTrend_v3( quotesL);
		/*
		if ( mol != null )
		{	
		if (mol.getDirection() == Constants.DIRECTION_DOWN)
			bt.direction =  Constants.DIRT_DOWN;
		else
			bt.direction =  Constants.DIRT_UP;
		}*/	

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			/*
			int pushStart = trade.prevUpStart;
			if ( pushStart > 24 )
			    pushStart = Utility.getHigh( quotesL, pushStart-24, pushStart).pos;

			PushList pushList = SmoothSetup.getDown2PushList(quotesL, pushStart, lastbarL);
			if ( pushList != null )
			{
				//System.out.println(bt.direction);
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				double lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;
				double triggerPrice = quotesL[lastPush.prePos].low;

				
				if ( pushSize >= 2 )
				{
				}
				
				if ( pushSize >=3 )
				{
				}
				
				
				if ( pushSize >=4 )
				{
				}
			} */
			
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( tip_reverse && !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + FIXED_STOP * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					//System.out.println(bt.toString(quotesL));
					//logger.warning(symbol + " " + CHART + " close trade with small tip at " + data.high);
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;

					boolean reversequalified = true;
				/*	if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}*/

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					closeReverseTrade( reversequalified, reversePrice, POSITION_SIZE );
					// reverse;
					/*
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);
						cancelTargets();

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
						trade.detectTime = quotes[lastbar].time;
						trade.positionSize = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}*/
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			/*
			int pushStart = trade.prevDownStart;
			if ( pushStart > 24 )
				pushStart = Utility.getLow( quotesL, pushStart-24, pushStart).pos;
			
			PushList pushList = SmoothSetup.getUp2PushList(quotesL, pushStart, lastbarL);
			if ( pushList != null )
			{	
				//System.out.println(bt.direction);
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));

				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				double triggerPrice = quotesL[lastPush.prePos].high;

				
				if ( pushSize >= 2 )
				{
				}
				
				if ( pushSize >=3 )
				{
				}
				
				
				if ( pushSize >=4 )
				{
					/*
					if ( pushList.rules_2_n_allSmallBreakOuts( 12 * PIP_SIZE))
					{
						closeReverseTrade( true, triggerPrice, POSITION_SIZE );
						return;
					}*/
			//	}
			/*	
				double avgBarSize = Utility.averageBarSize(quotesL);
				//System.out.println("Average Bar Size:" + avgBarSize);
				
				int bigBreakOutInd = -99;
				if (pushList.phls[0].pushSize > 3 * avgBarSize )
				   bigBreakOutInd = -1;

				for ( int i = 1; i < pushList.phls.length; i++)
				{
					if ( pushList.phls[i].breakOutSize > 2 * avgBarSize )
					{
						if ( bigBreakOutInd <= 0 )
							bigBreakOutInd = i;
						else
						{
							if ( pushList.phls[i].breakOutSize > pushList.phls[bigBreakOutInd].breakOutSize )
								bigBreakOutInd = i;
						}
					}
				}
				
				//System.out.println("break out size " + pushList.phls.length);
				//System.out.println("big break is " + bigBreakOutInd);
				
				int NoOfBreak = pushList.phls.length;
				double initBreakOut = pushList.phls[0].pushSize;
				double break1 = (NoOfBreak >= 1)? pushList.phls[0].breakOutSize:0;
				double break2 = (NoOfBreak >= 2)? pushList.phls[1].breakOutSize:0;
				double break3 = (NoOfBreak >= 3)? pushList.phls[2].breakOutSize:0;
				double break4 = (NoOfBreak >= 4)? pushList.phls[3].breakOutSize:0;
				double break5 = (NoOfBreak >= 5)? pushList.phls[4].breakOutSize:0;
				double break6 = (NoOfBreak >= 6)? pushList.phls[5].breakOutSize:0;
				
				double currentTakeProfitPrice = adjustPrice(quotesL[pushList.phls[NoOfBreak-1].prePos].high,  Constants.ADJUST_TYPE_UP);
						
				if ( NoOfBreak == 1 ) 
				{
					// do nothing
				}
				else if ( NoOfBreak == 2 ) 
				{
					/*
					if ( bigBreakOutInd >= -1 )
					{
						takePartialProfit(currentTakeProfitPrice, TAKE_PROFIT_LOT_SIZE );
					}*/
				}
		/*		else if ( NoOfBreak == 3 ) 
				{
					if ( break2 < 5 * PIP_SIZE )
					{
						//System.out.println("<<<<<<<<<<<<<<<<<");
					}
				}
				else if ( NoOfBreak >= 4 ) 
				{
					
				}
			}
			
			
			*/
			
			
			
			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (tip_reverse && !trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE *0.3 ))           
			{
				if (( data.low <  (highestPointAfterEntry - FIXED_STOP * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					System.out.println(bt.toString(quotesL));
					//logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					
					boolean reversequalified = true;
					/*if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}*/

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					closeReverseTrade( reversequalified, reversePrice, POSITION_SIZE );

					/*
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);
						cancelTargets();

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
						trade.detectTime = quotes[lastbar].time;
						trade.positionSize = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}*/
					//return;
				//}
			}
		}
	}


	
	
	void placeBreakEvenStop()
	{
		if ( trade.breakEvenStopPlaced == false )
		{	
			cancelOrder(trade.stopId);
			
			if (trade.action.equals(Constants.ACTION_BUY))
			{
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else if (trade.action.equals(Constants.ACTION_SELL))
			{
				trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			}
			
			trade.breakEvenStopPlaced = true;
		}
	}
	
	
	


	protected void takePartialProfit( double price, int size )
	{
		TradePosition tp = new TradePosition( price, size);
		
		boolean found = false;
		Iterator<TradePosition> it = trade.takeProfitQueue.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}
		
		it = trade.takeProfitHistory.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}

		
		if (!found)
			trade.takeProfitQueue.add(tp);
		
		if ( trade.takeProfitPartialId == 0 )
		{
			TradePosition tr = trade.takeProfitQueue.poll();
			if ( tr!= null )
			{	
				trade.takeProfitHistory.add(tr);
				
				trade.takeProfitPartialPrice = tr.price;
				trade.takeProfitPartialPosSize = tr.position_size;
	
				String action = Constants.ACTION_BUY;
				if (trade.action.equals(Constants.ACTION_BUY))
					action = Constants.ACTION_SELL;
				
				trade.takeProfitPartialId = placeLmtOrder(action, trade.takeProfitPartialPrice, trade.takeProfitPartialPosSize, null);
				warning("take partial profit " + action + " order placed@ " + trade.takeProfitPartialPrice + " " + trade.takeProfitPartialPosSize );
			}
		}
	}
	


	
	protected void enterPartialPosition( double price, int size )
	{
		TradePosition tp = new TradePosition( price, size);
		
		boolean found = false;
		Iterator<TradePosition> it = trade.enterPositionQueue.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}
		
		it = trade.entryPositionHistory.iterator();
		while ( it.hasNext())
		{
			TradePosition tpq = it.next();
			if ( tp.equals(tpq))
			{
				found = true;
				break;
			}
		}

		
		if (!found)
			trade.enterPositionQueue.add(tp);
		
		if ( trade.limitId2 == 0 )
		{
			TradePosition tr = trade.enterPositionQueue.poll();
			if ( tr!= null )
			{	
				trade.entryPositionHistory.add(tr);
				
				trade.limitPrice2 = tr.price;
				trade.limitPos2 = tr.position_size;
	
				trade.limitId2 = placeLmtOrder(trade.action, trade.limitPrice2, trade.limitPos2, null);
				warning("place partial position entry " + trade.action + " order placed@ " + trade.limitPrice2 + " " + trade.limitPos2 );
				trade.limit2Placed = true;
			}
		}
		
	}

	
	
	public void closeReverseTrade( boolean reverse, double reversePrice, int reverse_size )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// reverse;
			AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
			if ( reverse == false )
			{
				closePositionByMarket(trade.remainingPositionSize, reversePrice);
			}
			else
			{	
				cancelOrder(trade.stopId);
				cancelTargets();

				//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				int prevPosionSize = trade.remainingPositionSize;
				removeTrade();
				
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = reversePrice;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				enterMarketPosition(reversePrice, prevPosionSize);
			}
			return;
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
			if ( reverse == false )
			{
				closePositionByMarket(trade.remainingPositionSize, reversePrice);
			}
			else
			{	
				cancelOrder(trade.stopId);
				cancelTargets();

				//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				int prevPosionSize = trade.remainingPositionSize;
				removeTrade();
				
				//logger.warning(symbol + " " + CHART + " reverse opportunity detected");
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = reversePrice;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				enterMarketPosition(reversePrice, prevPosionSize);
			}
		}
	}
	

	
	
	// this is the first half profit taking
	protected void takeProfit2( double price, int size )
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return;

		if ( !trade.targetPlaced2)
		{	
			trade.targetPrice2 = price;
			trade.targetPos2 = size;
			String action = Constants.ACTION_BUY;
			if (trade.action.equals(Constants.ACTION_BUY))
				action = Constants.ACTION_SELL;
			
			trade.targetId2 = placeLmtOrder(action, trade.targetPrice2, trade.targetPos2, null);
			warning("take profit 2 " + action + " target order placed@ " + trade.targetPrice2 + " " + trade.targetPos2 );
	
			trade.targetPlaced2 = true;
			return;
		}
	}
	
	protected boolean takeProfit2_set()
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return true;

		return trade.targetPlaced2;
	}

	// this is to take all profit
	protected boolean exitTradePlaced()
	{
		if ( trade == null )
			return true;
		
		return trade.targetPlaced1;
	}
	
	protected void takeProfit( double price, int size )
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return;
		
		if ( trade.targetPlaced2 == true )
		{	
			if ( size + trade.targetPos2 > trade.POSITION_SIZE )
				size = trade.POSITION_SIZE - trade.targetPos2;
		}
		
		String action = Constants.ACTION_BUY;
		if (trade.action.equals(Constants.ACTION_BUY))
			action = Constants.ACTION_SELL;
		
		trade.targetPrice1 = price;
		trade.targetPos1 = size;//trade.remainingPositionSize;
		trade.targetId1 = placeLmtOrder(action, trade.targetPrice1, trade.targetPos1, null);
		warning("take profit remainning profit " + action + " target order placed@ " + trade.targetPrice1 + " " + trade.targetPos1 );

		trade.targetPlaced1 = true;
		return;
	}


	
	
	
	
	
	
	
}


