package tao.trading.strategy.tm;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import tao.trading.AlertOption;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Instrument;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.Position;
import tao.trading.PositionToMA;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.MomentumDAO;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.entry.IncrementalEntryFixed;
import tao.trading.entry.PullbackConfluenceEntry;
import tao.trading.setup.BarTrendDetector;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.MomentumDetector;
import tao.trading.setup.PushSetup;
import tao.trading.setup.ReturnToMASetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

import com.ib.client.EClientSocket;

public class RM_ReturnToMA extends TradeManager2 
{
	boolean CNT = false;;
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
	TradeMonitor monitor;
	PullbackConfluenceEntry pullbackConfluenceEntry;
	int PULLBACK_SIZE = 0;
	
	public RM_ReturnToMA()
	{
		super();
	}
	
	public RM_ReturnToMA(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
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

	public void setStrategy(Strategy s){
		super.strategy = s;
	}

	public void setPositionSize(int tradeSize){
	   	this.POSITION_SIZE = tradeSize;
	}

	public void setStopSize(int stopSize){
	   	this.FIXED_STOP = stopSize;
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
 	/*	createInstrument(  0, "EUR", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  1, "EUR", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument(  2, "EUR", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument(  3, "EUR", "CASH", "IDEALPRO", "GBP", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  4, "EUR", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  5, "EUR", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument(  6, "EUR", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  7, "GBP", "CASH", "IDEALPRO", "AUD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument(  8, "GBP", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument(  9, "GBP", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 10, "GBP", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument( 11, "GBP", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 12, "GBP", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 13, "CHF", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 14, "USD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 15, "USD", "CASH", "IDEALPRO", "CAD", 0.0001, Constants.PRICE_TYPE_3);
 		createInstrument( 16, "USD", "CASH", "IDEALPRO", "CHF", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 17, "CAD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 18, "AUD", "CASH", "IDEALPRO", "USD", 0.0001, Constants.PRICE_TYPE_4);
 		createInstrument( 19, "AUD", "CASH", "IDEALPRO", "JPY",   0.01, Constants.PRICE_TYPE_2);
 		createInstrument( 20, "AUD", "CASH", "IDEALPRO", "NZD", 0.0001, Constants.PRICE_TYPE_4);*/

		
		
		//if ( req_id != 501 )  //500
		  //return;
		
		realtime_count++;
		
		if (req_id == getInstrument().getId_realtime())
		{
			//System.out.print("RealTime ID " + req_id);
			if (!((trade != null) && ((trade.status.equals(Constants.STATUS_FILLED)||trade.status.equals(Constants.STATUS_PARTIAL_FILLED)))))
			{
				// moon light 
				//detect_pullback_moonlight(data);	
				//detect_pullback_moonlight2(data);	

				// breakout pullback
				//detect_push_pullback(data);
				//detect_push_pullback_60(data);

				
				// pullback to 20MA
				//checkPullBack20_60min(data);  // this one works
				checkPullBack20_60min_2(data); 
				//detect_pullback_onMA20_240(data);
				//detect_pullback_onMA20_2(data); // latest
			}

			
			if ( trade != null )  
			{
				trade.lastClosePrice = data.close;

				if (trade.status.equals(Constants.STATUS_DETECTED) || trade.status.equals(Constants.STATUS_PARTIAL_FILLED))
				{
					//if (inTradingTime( hour, minute ))
					entry(data );
				}
				else if (trade.status.equals(Constants.STATUS_FILLED)) 
				{
					manage(data );
				}
				
				
				//if ( MODE == Constants.TEST_MODE )
				//	tradeManager[i].report2(data.close);

				//tradeManager[i].lastQuoteData = data;
				return;
			}
		}
	}

	


/*
	public void listAllTradeEvent()
	{
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			Iterator it = tradeManager[i].getTradeHistory().iterator();
			while ( it.hasNext())
			{
				Trade t = (Trade)it.next();
				
				Iterator it2 = t.events.iterator();
				while ( it2.hasNext())
				{
					String eventStr = (String)it2.next();
					System.out.println(eventStr);
					
				}
			}
		}
		
	}*/
	
/*
	public void removeTradeNotUpdated()
	{
		for ( int i = 0; i < TOTAL_SYMBOLS; i++)
		{
			Trade trade = tradeManager[i].getTrade();
			String symbol = tradeManager[i].getSymbol();
			if ((trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED )) && (trade.positionUpdated == false))
			{
				LOGGER.warning(symbol + " " +  trade.action + " position did not updated, trade removed");
				tradeManager[i].setTrade(null);
			}
		}
	}*/
	
	

	
	
	
	/***********************************************************************************************************************
	 * 
	 * 
	 *	Standard Interface 
	 * 
	 * 
	 ************************************************************************************************************************/
	public void initEntryMode(int mode, double init_price, double init_quantity)
	{
		System.out.println(symbol + " initial Entry Mode " + mode + " " + init_price + " " + init_quantity);
		if (( trade!= null ) && ( Constants.STATUS_DETECTED.equals(trade.status)))
		{
			System.out.println(symbol + " set entry mode " + mode );
			trade.entryMode = mode;
			if (( init_price != 0 ) && ( init_quantity != 0 ))
			{
				if (Constants.ACTION_SELL.equals(trade.action))
				{	
					enterLimitPositionMulti( (int)(init_quantity * POSITION_SIZE), init_price );
					trade.entryDAO = new IncrementalEntryFixed((int)(init_quantity*POSITION_SIZE));
				//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice + 0.5 * FIXED_STOP * PIP_SIZE );
					trade.status = Constants.STATUS_PLACED;
				}
				
			}
		}
		
		
	}

	
    public void run() {
    }



	public Trade detect_push_pullback_60( QuoteData data )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int start, end;
			
		if (BarUtil.isUpBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close > ema20L[lastbarL-2]) && BarUtil.isDownBar(quotesL[lastbarL-1]))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			start = end = lastbarL-2;
			while (BarUtil.isUpBar(quotesL[start-1]) || (quotesL[start-1].low < quotesL[start].low))
				start--;
			
			double breakOutSize = quotesL[end].high - quotesL[start].low;
			if ( breakOutSize < 60 * PIP_SIZE )
				return null;
			
			if 	( tradeHistory.size() >= 3 )
				return null;
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			warning("pullback BUY detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].low + " end:" + quotesL[end].time + " " + quotesL[end].high);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[end].time);
			
			return trade;
		}	
		else if (BarUtil.isDownBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close < ema20L[lastbarL-2]) && BarUtil.isUpBar(quotesL[lastbarL-1]))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			start = end = lastbarL-2;
			while (BarUtil.isDownBar(quotesL[start-1]) || (quotesL[start-1].high > quotesL[start].high))
				start--;
			
			double breakOutSize = quotesL[start].high - quotesL[end].low;
			if ( breakOutSize < 60 * PIP_SIZE )
				return null;
			
			if 	( tradeHistory.size() >= 3 )
				return null;
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;
			
			warning("pullback SELL detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].high + " end:" + quotesL[end].time + " " + quotesL[end].low);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[end].time);
			
			return trade;
		}	
		
		return null;
	}

    
    public Trade detect_push_pullback( QuoteData data )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int start = lastbarL;
			
		if (BarUtil.isUpBar(quotesL[lastbarL-2]) && (quotesL[lastbarL-2].close > ema20L[lastbarL-2]) && BarUtil.isDownBar(quotesL[lastbarL-1]))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			start = lastbarL-2;
			while (BarUtil.isUpBar(quotesL[start-1]) || (quotesL[start-1].low < quotesL[start].low))
				start--;
			
			double breakOutSize = ( quotesL[lastbarL-2].high - quotesL[start].low );
			if ( breakOutSize < 60 * PIP_SIZE )
				return null;
			
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			if 	( tradeHistory.size() >= 3 )
				return null;

			warning("pullback BUY break DOWN detected at " + data.time + " start:" + quotesL[start].time + " " + quotesL[start].low);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotesL[lastbarL].time;
			trade.setFirstBreakOutStartTime(quotesL[start].time);
			trade.setFirstBreakOutTime(quotesL[lastbarL-2].time);
			
			return trade;
		}	
		
		return null;
	}

    
    
    
    
	public Trade detect_pullback_moonlight2( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;
			
		if (( lastUpPos > lastDownPos) && BarUtil.isDownBar(quotes[lastbar-1]))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			// check this is the first pullback downbar
			for ( int i = lastUpPosStart + 1; i < lastbar-1; i++)
				if (BarUtil.isDownBar(quotes[i]))
					return null;
				
			start = lastUpPosStart;
			while (! ((quotes[start-1].low > quotes[start].low) && (quotes[start-2].low > quotes[start].low)))
					start--;
			
			start = Utility.getLow( quotes, start, lastUpPosStart).pos;

			if ( findTradeHistory( Constants.ACTION_BUY, quotes[start].time) != null )
				return null;
			
			if 	( tradeHistory.size() >= 3 )
				return null;

			
			// check if there are previous up and downs
			/*
			int upSide = 0;
			int downSide = 0;
			for ( int i = lastUpPosStart -1; i < lastUpPosStart - 49; i--){
				if ( quotes[i].high < ema20[i] )
					downSide++;
				if (quotes[i].low > ema20[i])
					upSide++;
			}
			
			if (( upSide >= 2) && (downSide >= 2))
				return null;
			*/
			
			//
			QuoteData firstBreakOut = Utility.getHigh( quotes, start, lastbar-1);
			if ( firstBreakOut == null )
				return null;


			double entryPrice = firstBreakOut.high;

			//if ( data.high > entryPrice )
			{
			/*	int firstBreakOutL = firstBreakOut.pos;
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						//info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}*/

				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				warning("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			
			}

		}	
		else if (( lastDownPos > lastUpPos ) && BarUtil.isUpBar(quotes[lastbar-1]))
		{	
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;

			// check this is the first pullback downbar
			for ( int i = lastDownPosStart + 1; i < lastbar-1; i++)
				if (BarUtil.isUpBar(quotes[i]))
					return null;

			start = lastDownPosStart;
			while (! ((quotes[start-1].high < quotes[start].high) && (quotes[start-2].high < quotes[start].high)))
				start--;
		
			start = Utility.getHigh( quotes, start, lastDownPosStart).pos;
	
			if ( findTradeHistory( Constants.ACTION_SELL, quotes[start].time) != null )
				return null;
				
			if 	( tradeHistory.size() >= 3 )
				return null;

			// check if there are previous up and downs
			/*
			int upSide = 0;
			int downSide = 0;
			for ( int i = lastDownPosStart -1; i < lastDownPosStart - 49; i--){
				if ( quotes[i].high < ema20[i] )
					downSide++;
				if (quotes[i].low > ema20[i])
					upSide++;
			}
			
			if (( upSide >= 2) && (downSide >= 2))
				return null;*/

			//
			QuoteData firstBreakOut = Utility.getLow( quotes, start, lastbar-1);
			if ( firstBreakOut == null )
				return null;

			double entryPrice = firstBreakOut.low;
				
			//if (data.low < entryPrice ) 
			{
				// filter 1:  deep pullbacks
			/*	int firstBreakOutL = firstBreakOut.pos;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						//info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}*/
						
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				warning("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			}

		}
		
		return null;
	}


    
    
    
	public Trade detect_pullback_moonlight( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);

		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int prevUpPos, prevDownPos;

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		int start = lastbar;
			
		if ( lastUpPos > lastDownPos)
		{
			int lastUpPosStart = lastUpPos;
			while ( quotes[lastUpPosStart].low > ema20[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotes[prevDownPos-1].low < quotes[prevDownPos].low )
				prevDownPos--;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbar; start++)
				if (( quotes[start].low > ema20[start]) && ( quotes[start+1].low > ema20[start+1]))
					break;
				
			if ( start == lastbar )
				return null;


			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++)
			{
				if ( quotes[i].low <= ema20[i] )
				{
					touch20MA = i;
					break;
				}
			}
				
			if ( touch20MA == 0 )
				return null;

				
			QuoteData firstBreakOut = Utility.getHigh( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;

			for ( int i = touch20MA+1; i < lastbar; i++)
			{
				if ( quotes[i].high > firstBreakOut.high )
				{
					//debug("first breakout high missed at " + quotes[i].time);
					return null;
				}
			}
					

			double entryPrice = firstBreakOut.high;

			//if ( data.high > entryPrice )
			{
				int firstBreakOutL = firstBreakOut.pos;
			/*	QuoteData lowAfterFirstBreakOut = Utility.getLow( quotes, firstBreakOutL+1, lastbar );
				if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						//info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}*/

				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				warning("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			
			}

		}	
		else if ( lastDownPos > lastUpPos )
		{	
			int lastDownPosStart = lastDownPos;
			while ( quotes[lastDownPosStart].high < ema20[lastDownPosStart])
				lastDownPosStart--;
				
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
				
			while ( quotes[prevUpPos-1].high > quotes[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbar; start++)
				if (( quotes[start].high < ema20[start]) && ( quotes[start+1].high < ema20[start+1]))
					break;
				
			if ( start == lastbar )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbar; i++)
			{
				if ( quotes[i].high >= ema20[i])
				{
					touch20MA=i;
					break;
				}
			}

			if ( touch20MA == 0 )
				return null;

			QuoteData firstBreakOut = Utility.getLow( quotes, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;
				

			//for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
			for ( int i = touch20MA+1; i < lastbar; i++)
			{
				if ( quotes[i].low < firstBreakOut.low )
				{
					//debug("first breakout low missed at " + quotes[i].time);
					return null;
				}
			}


			double entryPrice = firstBreakOut.low;
				
			//if (data.low < entryPrice ) 
			{
				// filter 1:  deep pullbacks
			/*	int firstBreakOutL = firstBreakOut.pos;
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotes, firstBreakOutL+1, lastbar );
				if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						//info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}
				}*/
						
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
				trade.action = Constants.ACTION_SELL;
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes[lastbar].time;
				trade.setFirstBreakOutStartTime(quotes[start].time);
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				warning("break DOWN detected at " + data.time + " start:" + quotes[start].time );
				
				return trade;
			}

		}
		
		return null;
	}

    
    
    
    
    
    
    
	public Trade detect_pullback_onMA20_240(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_240_MIN);
		int lastbar = quotes.length - 1;

		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		ReturnToMADAO returnTo20 = null;
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		if (downPos > upPos) 
		{
			// remove buy trade if price already drop below 50MA
			if (( trade != null ) && (quotes[lastbar].high < ema50[lastbar]) && Constants.ACTION_BUY.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;
			
			if (( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema20[lastbar]))
			{
				int startL = downPos;
				while (( quotes[startL].high < ema20[startL]) && (startL > 0 ))
					startL--;
	
				int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
				if (prevConsectiveUpPos == Constants.NOT_FOUND)
					prevConsectiveUpPos = 0;
	
				int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
				if (downPos2 == Constants.NOT_FOUND)
					downPos2 = 3;
				int pullBackStart = Utility.getLow( quotes, startL, downPos).pos;
				
				returnTo20 = new ReturnToMADAO( Constants.DIRECTION_DOWN, startL, downPos, pullBackStart, 20);
			}
		}
		else if (upPos > downPos)
		{
			// remove sell trade if price already above 50MA
			if (( trade != null ) && (quotes[lastbar].low > ema50[lastbar]) && Constants.ACTION_SELL.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;

			if (( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema20[lastbar]))
			{
				int startL = upPos;
				while (( quotes[startL].low > ema20[startL]) && ( startL> 0 ))
					startL--;
	
				int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
				if (prevConsectiveDownPos == Constants.NOT_FOUND)
					prevConsectiveDownPos = 0;
				
				int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
				if (upPos2 == Constants.NOT_FOUND)
					upPos2 = 3;
				int pullBackStart = Utility.getHigh( quotes, startL, upPos).pos;
				
				returnTo20 = new ReturnToMADAO( Constants.DIRECTION_UP, startL, upPos, pullBackStart, 20);
			}
		
		}	
		
		
		if ( returnTo20 != null )
		{
			//BigTrend bt = TrendAnalysis.determineBigTrend_3( quotes240 );
			if (( returnTo20.getDirection() == Constants.DIRECTION_UP ))
			{
				if (( trade != null ) && ( Constants.ACTION_BUY.equals(trade.action)))
					return trade;
		
				if ( findTradeHistory( Constants.ACTION_BUY, quotes[returnTo20.getLowestHighestPos()].time) != null )
						return null;
					
				createOpenTrade(Constants.TRADE_RM, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.pullBackStartL = returnTo20.getLowestHighestPos(); 
				trade.detectPos = lastbar;
				trade.setPullBackStartTime(quotes[returnTo20.getLowestHighestPos()].time);
				trade.setFirstBreakOutStartTime(quotes[returnTo20.getLowestHighestPos()].time);
				trade.detectTime = quotes[lastbar].time;

				trade.setupDAO = returnTo20;
				
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes[lastbar].time);
				tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
				trade.addTradeEvent(tv);

				warning("BUY detected " + quotes[lastbar].time + " " + returnTo20.toString(quotes));

		        return trade;
			}
			else if (( returnTo20.getDirection() == Constants.DIRECTION_DOWN ))
			{
				if (( trade != null ) && ( Constants.ACTION_SELL.equals(trade.action)))
					return trade;
		
				if ( findTradeHistory( Constants.ACTION_SELL, quotes[returnTo20.getLowestHighestPos()].time) != null )
						return null;
					
				createOpenTrade(Constants.TRADE_RM, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.pullBackStartL = returnTo20.getLowestHighestPos(); 
				trade.detectPos = lastbar;
				trade.setPullBackStartTime(quotes[returnTo20.getLowestHighestPos()].time);
				trade.setFirstBreakOutStartTime(quotes[returnTo20.getLowestHighestPos()].time);
				trade.detectTime = quotes[lastbar].time;

				trade.setupDAO = returnTo20;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes[lastbar].time);
				tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
				trade.addTradeEvent(tv);

				warning("SELL detected " + quotes[lastbar].time + " " + returnTo20.toString(quotes));

		        return trade;
			}
		}
		
		return null;
	
	}



	
	
	public Trade detect_pullback_onMA20_2(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		ReturnToMADAO returnTo20 = null;
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		if (downPos > upPos){ 
			if (( trade != null ) && ( Constants.ACTION_SELL.equals(trade.action)))
				return trade;

			// remove buy trade if price already drop below 50MA
			if (( trade != null ) && (quotes[lastbar].high < ema50[lastbar]) && Constants.ACTION_BUY.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;
			
			if (( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema20[lastbar])){
				int endL = downPos;
				int startL = downPos;
				while (( quotes[startL].high < ema20[startL]) && (startL > 0 ))
					startL--;
	
				// TBD check this is a large move
				if ((quotes[startL].close - quotes[endL].close) > 2 * PIP_SIZE * FIXED_STOP ){
				
					int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
					if (prevConsectiveUpPos == Constants.NOT_FOUND)
						prevConsectiveUpPos = 0;
		
					int pullBackStart = Utility.getLow( quotes, prevConsectiveUpPos, downPos).pos;
					if ( findTradeHistory( Constants.ACTION_SELL, quotes[pullBackStart].time) != null )
							return null;
						
					createOpenTrade(Constants.TRADE_RM, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					
					trade.pullBackStartL = pullBackStart; 
					trade.detectPos = lastbar;
					trade.setPullBackStartTime(quotes[pullBackStart].time);
					trade.pullBackStartPrice = quotes[pullBackStart].low;
					trade.setFirstBreakOutStartTime(quotes[pullBackStart].time);
					trade.detectTime = quotes[lastbar].time;
	
					trade.setupDAO = returnTo20;
	
					//TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes[lastbar].time);
					//tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
					//rade.addTradeEvent(tv);
	
					warning("SELL detected " + quotes[lastbar].time + " pullback start " + trade.getPullBackStartTime());
					//super.strategy.sentDetectEmail(strategy.getStrategyName() + " DETECT " + trade.symbol + " " + trade.action + " " + data.close);
	
			        return trade;
	
				}
			}
		}
		else if (upPos > downPos){
			if (( trade != null ) && ( Constants.ACTION_BUY.equals(trade.action)))
				return trade;
	
			// remove sell trade if price already above 50MA
			if (( trade != null ) && (quotes[lastbar].low > ema50[lastbar]) && Constants.ACTION_SELL.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;

			if (( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema20[lastbar])){
				int endL = upPos;
				int startL = upPos;
				while (( quotes[startL].low > ema20[startL]) && ( startL> 0 ))
					startL--;

				// TBD check this is a large move
				if ((quotes[endL].close - quotes[startL].close) > 2 * PIP_SIZE * FIXED_STOP ){
	
					int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
					if (prevConsectiveDownPos == Constants.NOT_FOUND)
						prevConsectiveDownPos = 0;
					
					int pullBackStart = Utility.getHigh( quotes, prevConsectiveDownPos, upPos).pos;
					
					if ( findTradeHistory( Constants.ACTION_BUY, quotes[pullBackStart].time) != null )
							return null;
						
					createOpenTrade(Constants.TRADE_RM, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					
					trade.pullBackStartL = pullBackStart; 
					trade.detectPos = lastbar;
					trade.setPullBackStartTime(quotes[pullBackStart].time);
					trade.pullBackStartPrice = quotes[pullBackStart].high;
					trade.setFirstBreakOutStartTime(quotes[pullBackStart].time);
					trade.detectTime = quotes[lastbar].time;
	
					trade.setupDAO = returnTo20;
					
					//TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes[lastbar].time);
					//tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
					//trade.addTradeEvent(tv);
	
					warning("BUY detected " + quotes[lastbar].time + " pullback start " + trade.getPullBackStartTime());
					//super.strategy.sentDetectEmail(strategy.getStrategyName() + " DETECT " + trade.symbol + " " + trade.action + " " + data.close);
	
			        return trade;
				}
			}
		
		}	
		
		return null;
	
	}

    
    
    
    
    
	public Trade checkPullBack20_signal2(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;

		ReturnToMADAO returnTo20 = ReturnToMASetup.detect(quotes240, 20);

		if ( returnTo20 != null )
		{
			//BigTrend bt = TrendAnalysis.determineBigTrend_3( quotes240 );
			if (( returnTo20.getDirection() == Constants.DIRECTION_UP ))
			{
				if (( trade != null ) && ( Constants.ACTION_BUY.equals(trade.action)))
					return trade;
		
				if ( findTradeHistory( Constants.ACTION_BUY, quotes240[returnTo20.getLowestHighestPos()].time) != null )
						return null;
					
				createOpenTrade(Constants.TRADE_RM, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.pullBackStartL = returnTo20.getLowestHighestPos(); 
				trade.detectPos = lastbar240;
				trade.setPullBackStartTime(quotes240[returnTo20.getLowestHighestPos()].time);
				trade.setFirstBreakOutStartTime(quotes240[returnTo20.getLowestHighestPos()].time);
				trade.detectTime = quotes240[lastbar240].time;

				trade.setupDAO = returnTo20;
				
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes240[lastbar240].time);
				tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
				trade.addTradeEvent(tv);

				warning("BUY detected " + quotes240[lastbar240].time + " " + returnTo20.toString(quotes240));

		        return trade;
			}
			else if (( returnTo20.getDirection() == Constants.DIRECTION_DOWN ))
			{
				if (( trade != null ) && ( Constants.ACTION_SELL.equals(trade.action)))
					return trade;
		
				if ( findTradeHistory( Constants.ACTION_SELL, quotes240[returnTo20.getLowestHighestPos()].time) != null )
						return null;
					
				createOpenTrade(Constants.TRADE_RM, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.pullBackStartL = returnTo20.getLowestHighestPos(); 
				trade.detectPos = lastbar240;
				trade.setPullBackStartTime(quotes240[returnTo20.getLowestHighestPos()].time);
				trade.setFirstBreakOutStartTime(quotes240[returnTo20.getLowestHighestPos()].time);
				trade.detectTime = quotes240[lastbar240].time;

				trade.setupDAO = returnTo20;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes240[lastbar240].time);
				tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
				trade.addTradeEvent(tv);

				warning("SELL detected " + quotes240[lastbar240].time + " " + returnTo20.toString(quotes240));

		        return trade;
			}
		}
		
		return null;
	
	}
	

	public Trade checkPullBack20_60min(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		int lastbar = quotes.length - 1;

		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		ReturnToMADAO returnTo20 = null;
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		//BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);
		
		if (downPos > upPos){ 
			// detected a return to MA
			if (( trade != null ) && ( Constants.ACTION_SELL.equals(trade.action)))
				return trade;

			// remove a buy trade if it close below 50MA and still not entered
			if (( trade != null ) && (quotes[lastbar].high < ema50[lastbar]) && Constants.ACTION_BUY.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;
	
			if (( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema20[lastbar])){
				int startL = downPos;
				
				while (( quotes[startL].high < ema20[startL]) && (startL > 0 ))
					startL--;
				
				int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
				if (prevConsectiveUpPos == Constants.NOT_FOUND)
					prevConsectiveUpPos = 0;
	
				int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, startL, 2);
				if (downPos2 == Constants.NOT_FOUND)
					downPos2 = 3;
				int pullBackStart = Utility.getLow( quotes, downPos2, downPos).pos;
			
				////////////////////////////////////////////////////
				// filter 1:  the price has to move quite a bit on the first move
				///////////////////////////////////////////////////
				/*double pushStart = Utility.getHigh(quotes, downPos2-4, downPos2+4).high;//ema20[startL];//quotes[startL].high;//; //; 
				double pushEnd = ema20[lastbar];
				double lowestPoint = quotes[pullBackStart].low;
				double totalPushSize = pushStart - lowestPoint;
				double pullBackSize = pushEnd - lowestPoint;
				if ((pullBackSize < (0.768 * totalPushSize)) && (totalPushSize > 1.5 * FIXED_STOP * PIP_SIZE)){*/
				if (new BarTrendDetector(quotesL, PIP_SIZE).getLastTrend().direction == Constants.DIRECTION_DOWN){
					if ( findTradeHistory( Constants.ACTION_SELL, quotes[startL].time) != null )
						return null;
					
					createOpenTrade(Constants.TRADE_RM, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPrice = data.close;
					trade.setFirstBreakOutStartTime(quotes[startL].time);// for history lookup only
					trade.pullBackStartTime = quotes[pullBackStart].time;
	
					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
					tv.addNameValue("Detected", Double.toString(trade.detectPrice));
					trade.addTradeEvent(tv);
					super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.detectPrice, trade);
	
					//enter right away
					/*
					double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					return trade;
					*/
					returnTo20 = new ReturnToMADAO( Constants.DIRECTION_DOWN, startL, downPos, pullBackStart, 20);
					trade.setupDAO = returnTo20;
				}
			}
		}
		else if (upPos > downPos){
			if (( trade != null ) && (quotes[lastbar].low > ema50[lastbar]) && Constants.ACTION_SELL.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;

			if (( trade != null ) && ( Constants.ACTION_BUY.equals(trade.action)))
				return trade;

			if (( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema20[lastbar])){
				int startL = upPos;
				while (( quotes[startL].low < ema20[startL]) && ( startL > 0 ))
					startL--;
	
				int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
				if (prevConsectiveDownPos == Constants.NOT_FOUND)
					prevConsectiveDownPos = 0;
				
				int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, startL, 2);
				if (upPos2 == Constants.NOT_FOUND)
					upPos2 = 3;
				int pullBackStart = Utility.getHigh( quotes, upPos2, upPos).pos;
				
				////////////////////////////////////////////////////
				// filter 1:  the price has to move quite a bit on the first move
				///////////////////////////////////////////////////
				/*double pushStart = ema20[startL];//quotes[startL].low; 
				double pushEnd = ema20[lastbar];
				double highestPoint = quotes[pullBackStart].high;
				double totalPushSize = highestPoint - pushStart;
				double pullBackSize = highestPoint - pushEnd;
				if ((pullBackSize < (0.768 * totalPushSize)) && (totalPushSize > 1.5 * FIXED_STOP * PIP_SIZE)){*/
				if (new BarTrendDetector(quotesL, PIP_SIZE).getLastTrend().direction == Constants.DIRECTION_UP){
				
					if ( findTradeHistory( Constants.ACTION_BUY, quotes[startL].time) != null )
						return null;
						
					createOpenTrade(Constants.TRADE_RM, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPrice = data.close;
					trade.pullBackStartTime = quotes[pullBackStart].time;
					trade.setFirstBreakOutStartTime(quotes[startL].time);// for history lookup only

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
					tv.addNameValue("Detected", Double.toString(trade.detectPrice));
					trade.addTradeEvent(tv);
					super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.detectPrice, trade);

					// enter right away
					/*double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					return trade;
					*/
					
					returnTo20 = new ReturnToMADAO( Constants.DIRECTION_UP, startL, upPos, pullBackStart, 20);
					trade.setupDAO = returnTo20;
				}
			}
		}	
		

		if ( returnTo20 != null ){
			//TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes[lastbar].time);
			//tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
			//trade.addTradeEvent(tv);

			warning(trade.action + " detected " + quotes[lastbar].time + " " + returnTo20.toString(quotes));
			super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.detectPrice, trade);
			if ( MODE == Constants.SIGNAL_MODE )
				return null;
			else
				return trade;
		}
		
		return null;
	
	}
	


	public Trade checkPullBack20_60min_2(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		QuoteData[] quotesL = getQuoteData(Constants.CHART_DAILY);
		int lastbar = quotes.length - 1;

		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);
		ReturnToMADAO returnTo20 = null;
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 2);

		//BarTrendDetector tbddL = new BarTrendDetector(quotesL, PIP_SIZE);

		if ((downPos > upPos) /*&& (tbddL.detectTrend() == Constants.DIRECTION_DOWN)*/){ 
			// detected a return to MA
			if (( trade != null ) && ( Constants.ACTION_SELL.equals(trade.action)))
				return trade;

			// remove a buy trade if it close below 50MA and still not entered
			if (( trade != null ) && (quotes[lastbar].high < ema50[lastbar]) && Constants.ACTION_BUY.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;
	
			if (( downPos == lastbar-1 ) && ( quotes[lastbar].high > ema20[lastbar])){
				int startL = downPos;
				while (( quotes[startL].high < ema20[startL]) && (startL > 0 ))
					startL--;
				if ( startL == 0 )
					return null;
				
				int pullBackStart = Utility.getLow( quotes, startL, downPos).pos;
				//if ( pullBackStart < startL )
				//	return null;*/

				////////////////////////////////////////////////////
				// filter 1:  the price has to move quite a bit on the first move
				///////////////////////////////////////////////////
				/*double pushStart = Utility.getHigh(quotes, downPos2-4, downPos2+4).high;//ema20[startL];//quotes[startL].high;//; //; 
				double pushEnd = ema20[lastbar];
				double lowestPoint = quotes[pullBackStart].low;
				double totalPushSize = pushStart - lowestPoint;
				double pullBackSize = pushEnd - lowestPoint;
				if ((pullBackSize < (0.768 * totalPushSize)) && (totalPushSize > 1.5 * FIXED_STOP * PIP_SIZE)){*/
				//if (new BarTrendDetector(quotesL, PIP_SIZE).getLastTrend().direction == Constants.DIRECTION_DOWN){
				{
					if ( findTradeHistory( Constants.ACTION_SELL, quotes[startL].time) != null )
						return null;
					
					createOpenTrade(Constants.TRADE_RM, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPrice = data.close;
					trade.setFirstBreakOutStartTime(quotes[startL].time);// for history lookup only
					trade.pullBackStartTime = quotes[pullBackStart].time;
	
					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
					tv.addNameValue("Detected", Double.toString(trade.detectPrice));
					trade.addTradeEvent(tv);
					
					if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
						super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action );
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.detectPrice, trade);
					}
	
					//enter right away
					/*
					double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					return trade;
					*/
					returnTo20 = new ReturnToMADAO( Constants.DIRECTION_DOWN, startL, downPos, pullBackStart, 20);
					trade.setupDAO = returnTo20;
				}
			}
		}
		else if ((upPos > downPos)/* && (tbddL.detectTrend() == Constants.DIRECTION_UP)*/){
			if (( trade != null ) && (quotes[lastbar].low > ema50[lastbar]) && Constants.ACTION_SELL.equals(trade.action) && Constants.STATUS_DETECTED.equals(trade.status))
				trade = null;

			if (( trade != null ) && ( Constants.ACTION_BUY.equals(trade.action)))
				return trade;

			if (( upPos == lastbar-1 ) && ( quotes[lastbar].low < ema20[lastbar])){
				int startL = upPos;
				while (( quotes[startL].low < ema20[startL]) && ( startL > 0 ))
					startL--;
				if ( startL == 0 )
					return null;
				
				int pullBackStart = Utility.getHigh( quotes, startL, upPos).pos;
				
				////////////////////////////////////////////////////
				// filter 1:  the price has to move quite a bit on the first move
				///////////////////////////////////////////////////
				/*double pushStart = ema20[startL];//quotes[startL].low; 
				double pushEnd = ema20[lastbar];
				double highestPoint = quotes[pullBackStart].high;
				double totalPushSize = highestPoint - pushStart;
				double pullBackSize = highestPoint - pushEnd;
				if ((pullBackSize < (0.768 * totalPushSize)) && (totalPushSize > 1.5 * FIXED_STOP * PIP_SIZE)){*/
				//if (new BarTrendDetector(quotesL, PIP_SIZE).getLastTrend().direction == Constants.DIRECTION_UP){
				{
					if ( findTradeHistory( Constants.ACTION_BUY, quotes[startL].time) != null )
						return null;
						
					createOpenTrade(Constants.TRADE_RM, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = quotes[lastbar].time;
					trade.detectPrice = data.close;
					trade.pullBackStartTime = quotes[pullBackStart].time;
					trade.setFirstBreakOutStartTime(quotes[startL].time);// for history lookup only

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED, quotes[lastbar].time);
					tv.addNameValue("Detected", Double.toString(trade.detectPrice));
					trade.addTradeEvent(tv);
					if ((MODE == Constants.SIGNAL_MODE) || (MODE == Constants.REAL_MODE)) {
						super.strategy.addStrategyTrigger(trade.symbol + " " + trade.detectTime + " " + trade.action );
						super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.detectPrice, trade);
					}

					// enter right away
					/*double triggerPrice = data.close;
					enterMarketPosition(triggerPrice);
					return trade;
					*/
					
					returnTo20 = new ReturnToMADAO( Constants.DIRECTION_UP, startL, upPos, pullBackStart, 20);
					trade.setupDAO = returnTo20;
				}
			}
		}	
		

		if ( returnTo20 != null ){
			//TradeEvent tv = new TradeEvent(TradeEvent.TRADE_DETECTED,  quotes[lastbar].time);
			//tv.addNameValue("detail", "pullback start is at " + " " + trade.getPullBackStartTime()); 
			//trade.addTradeEvent(tv);

			warning(trade.action + " detected " + quotes[lastbar].time + " " + returnTo20.toString(quotes));
			super.strategy.sentAlertEmail(AlertOption.detect, trade.symbol + " ATD60 " + trade.action + " " + trade.detectPrice, trade);
			if ( MODE == Constants.SIGNAL_MODE )
				return null;
			else
				return trade;
		}
		
		return null;
	
	}

	
	
	
	

	
	
	

  
	public Trade detect( QuoteData data )
	{
		return null;
	}

	
	
	
	public void entry( QuoteData data )
	{
		if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			checkStopTarget(data,0,0);

		if ((trade != null) && trade.type.equals(Constants.TRADE_RM)){

			// method 1:
			/*
			if (Constants.ACTION_SELL.equals(trade.action))
				trackPullBackTradeSell( data, 0);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trackPullBackTradeBuy( data, 0 );
			*/
			
			checkPullBack20_60min_entry(data);
			//trackEntryUsingChangedMomentum( data);  //last time it works 1
			//trackPullbackEntryUsingWeakBreakOut(data);  // latest
			//trackPullbackEntryUsingConfluenceIndicator60(data);   //2
			//trackPullbackEntryUsingMoonLight(data);   

			/*
			if (!Constants.STATUS_FILLED.equals(trade.status))
				trackEntryUsingConfluenceIndicator( data); 
			else{
				if ( pullbackConfluenceEntry == null )
					pullbackConfluenceEntry = new PullbackConfluenceEntry(this);
				pullbackConfluenceEntry.entry_manage(data, this);

			}*/
		}
		
		/*
		if ((trade != null) && (trade.type.equals(Constants.TRADE_PBK)))
			//track_PBK_entry_base( data);  
			track_PBK_entry_base60( data);	// method 1
			//track_PBK_entry_using_momentum(data);  // method 2
		*/
	}
		
	private void checkPullBack20_60min_entry(QuoteData data){
		//checkPullBack20_60min_trackEntryUsingConfirmation(data);
		track_PBK_entry_using_everything(data);
	}
	
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data,0,0);

		if ( trade != null )
		{	
			if ( trade.status.equals(Constants.STATUS_OPEN))
				checkTradeExpiring_ByTime(data);
			
			
			if ((trade != null) && !(trade.status.equals(Constants.STATUS_DETECTED)|| trade.status.equals(Constants.STATUS_OPEN))){
			//if (( trade != null ) && ( trade.status.equals(Constants.STATUS_FILLED))){
				//exit123_new9c4_123( data );  
				//exit123_new9c4( data ); // latest working
				exit123_latest(data);
				//monitor.monitor_exit(data);
			}		
			
			
			// temp to comment out
			/*if (( trade != null ) && ( trade.status.equals(Constants.STATUS_FILLED))){
				//smonitor.monitor_exit(data);
			}*/		
		}
	}

	
	public QuoteData[] getQuoteData(int CHART)
	{
		return instrument.getQuoteData(CHART);
	}

	public Instrument getInstrument()
	{
		return instrument;
	}

	public void checkOrderFilled(int orderId, int filled)
	{
		checkStopTarget(null, orderId, filled);
		/*
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		//QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		//int lastbarL = quotesL.length - 1;

		if (orderId == trade.stopId)
		{
			warning("order " + orderId + " stopped out ");
			trade.stopId = 0;
			trade.closeTime = quotes[lastbar].time;

			cancelTargets();
			cancelStopMarkets();
			
			//processAfterHitStopLogic_c();
			removeTrade();
			return;

		}
		/*
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
				trade.targetPrice = adjustPrice(trade.limitPrice1 - DEFAULT_PROFIT_TARGET  * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
				trade.targetPos = trade.positionSize;
				trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
				
				/*
				if ( average_up == true )
				{
					double averageUpPrice = trade.entryPrice - FIXED_STOP * PIP_SIZE;
					trade.stopMarketSize = POSITION_SIZE;
					trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageUpPrice, POSITION_SIZE, null);
				}*/

/*			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);
				trade.targetPrice = adjustPrice(trade.limitPrice1 + DEFAULT_PROFIT_TARGET  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
				trade.targetPos = trade.positionSize;
				trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPos, oca);

				/*
				if ( average_up == true )
				{
					double averageUpPrice = trade.entryPrice + FIXED_STOP * PIP_SIZE;
					trade.stopMarketSize = POSITION_SIZE;
					trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, POSITION_SIZE, null);
				}*/
		/*	}
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_FILLED;

			TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotesL[lastbarL].time);
			tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
			trade.addTradeEvent(tv);
		
		}*/
		/*
		else if ((orderId == trade.stopMarketId) && ( trade.stopMarketPosFilled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("stop market order: " + orderId + " " + filled + " filled");
			trade.stopMarketId = 0;

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.POSITION_SIZE, trade.stopMarketStopPrice);

			trade.stopMarketPosFilled = trade.POSITION_SIZE;
			//trade.entryPrice = trade.stopMarketStopPrice;
			trade.remainingPositionSize += trade.stopMarketSize; //+= filled;
			//trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			if ( trade.stopId != 0 )
				cancelStop();
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			
			trade.openOrderDurationInHour = 0;
			//trade.status = Constants.STATUS_FILLED;
		
		}
		/*
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
		}*/
		
		/*
		// check limit entries
		for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
		{
			TradePosition limit = trade.limits[i];
			if (( limit != null ) && ( limit.orderId == orderId ))
			{
				warning( "limit filled, " + " price: " + limit.price);
				limit.orderId = 0;  // do not remove
				limit.filled = true;

				if (i == 0 )
					CreateTradeRecord(trade.type, trade.action);
				AddOpenRecord(quotes[lastbar].time, trade.action, trade.limitPos1, trade.limitPrice1);

				//trade.limitPos1Filled = trade.limitPos1;
				//trade.entryPrice = trade.limitPrice1;
				if ( trade.entryPrice == 0 ) 
					trade.entryPrice = limit.price;
				trade.openOrderDurationInHour = 0;
				trade.remainingPositionSize += limit.position_size; //+= filled;
				if ( trade.remainingPositionSize == trade.POSITION_SIZE )
					trade.status = Constants.STATUS_FILLED;
				else
					trade.status = Constants.STATUS_PARTIAL_FILLED;
				trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				cancelStop();
				String oca = new Long(new Date().getTime()).toString();
				if (Constants.ACTION_SELL.equals(trade.action))
				{
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					if (trade.stopId != 0 )
						cancelOrder(trade.stopId);
					warning("place stop order buy " + trade.stop + " " + trade.remainingPositionSize + ((QuoteData) quotes[lastbar]).time);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);
					warning("stop order placed");

					//double defualtTargetPrice = adjustPrice(limit.price - DEFAULT_PROFIT_TARGET  * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					//createTradeTargetOrder( trade.remainingPositionSize, defualtTargetPrice );

					/*
					if ( trade.averageUp == false )
					{
						double averageUpPrice = limit.price - FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

		/*		}
				else if (Constants.ACTION_BUY.equals(trade.action))
				{
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					if (trade.stopId != 0 )
						cancelOrder(trade.stopId);
					warning("place stop order sell " + trade.stop + " " + trade.remainingPositionSize + ((QuoteData) quotes[lastbar]).time);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);
					warning("stop order placed");
					
					//double defualtTargetPrice = adjustPrice(limit.price + DEFAULT_PROFIT_TARGET  * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					//createTradeTargetOrder( trade.remainingPositionSize, defualtTargetPrice );

					/*
					if ( trade.averageUp == false )
					{
						double averageUpPrice = limit.price + FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/
				}
				
			/*	TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
				tv.addNameValue("FillPrice", new Double(limit.price).toString());
				tv.addNameValue("FillSize", new Double(limit.position_size).toString());
				trade.addTradeEvent(tv);
			
				trade.limits[i] = null;

			}
		}
		//  check targets
		for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
		{
			TradePosition target = trade.targets[i];
			if (( target != null ) && ( target.orderId == orderId ))
			{
				warning( "target filled, " + " price: " + trade.targetPrice);
				target.orderId = 0;  // do not remove
				trade.remainingPositionSize -= target.position_size;
				cancelStop();

				warning("target hit, close " + target.position_size + " @ " + target.price + " remainning position is " + trade.remainingPositionSize);
				if (trade.remainingPositionSize > 0)
				{
					String oca = new Long(new Date().getTime()).toString();

					if (Constants.ACTION_SELL.equals(trade.action))
					{
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}
					else if (Constants.ACTION_BUY.equals(trade.action))
					{
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}
				}
				else
				{
					cancelAllOrders();
					removeTrade();
				}
				return;
			}
		}

	}*/


	
	//////////////////////////////////////////////////////////////////////////
	//
	//  Custom Methods
	//
	///////////////////////////////////////////////////////////////////////////
	public void checkStopTarget(QuoteData data, int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		if (lastbar == -1 )
			lastbar = 0;	// this could be triggered before all the quotes been completed
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check stop;
			if ((( orderId != 0 ) && (orderId == trade.stopId)) || (( data != null ) && (trade.stopId != 0) && (data.high > trade.stop))){ 
				OrderFilled_stopId(quotes[lastbar].time);
				return;
			}

			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ((( orderId != 0 ) && (orderId == limit.orderId)) || (( data != null ) && (limit.orderId != 0 ) && (data.high >= limit.price ))))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					limit.orderId = 0;
					limit.filled = true;

					if (trade.recordOpened == false){
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
					}
					
					if ( trade.getEntryTime() == null )
						trade.setEntryTime(quotes[lastbar].time);
					if ( trade.entryPrice == 0 )
						trade.entryPrice = limit.price;
					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.remainingPositionSize += limit.position_size;
					trade.status = Constants.STATUS_FILLED;
					
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, limit.position_size, limit.price);
					
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					if ( trade.stopId != 0 )
						cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					/*if ( trade.averageUp ){
						double averageUpPrice = limit.price - FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
				}
			}


			/*
			if ((trade.stopMarketId != 0) && (data.low <= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.stopMarketSize, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.stopMarketSize;

				if ( trade.entryPrice == 0 )
					trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.stopMarketSize;
				//trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
			}*/

			

			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ((( orderId != 0 ) && (orderId == target.orderId)) || (( data != null ) && (target.orderId != 0 ) && (data.low <= target.price))))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					if ( data != null )
						AddCloseRecord(data.time, Constants.ACTION_BUY, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						if ( trade.stop != 0 ){
							info("replace stop order");
							cancelStop();
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}
						if ( trade.targetId != 0 ){
							info("replace target order");
							cancelOrder(trade.targetId);
							trade.targetPos = trade.remainingPositionSize;
							logger.warning(symbol + " place target order " + trade.targetPrice + " " + trade.targetPos );
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ((( orderId != 0 ) && (orderId == trade.stopId)) || (( data != null ) && (trade.stopId != 0) && (data.low < trade.stop))){ 
				OrderFilled_stopId(quotes[lastbar].time);
				return;
			}
			
			
			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ((( orderId != 0 ) && (orderId == limit.orderId)) || (( data != null ) && (limit.orderId != 0 ) && (data.low <= limit.price ))))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					limit.orderId = 0;
					limit.filled = true;
					if (trade.recordOpened == false){
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
					}
					
					if ( trade.getEntryTime() == null )
						trade.setEntryTime(quotes[lastbar].time);
					if ( trade.entryPrice == 0 )
						trade.entryPrice = limit.price;
					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.remainingPositionSize += limit.position_size;
					trade.status = Constants.STATUS_FILLED;
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, limit.position_size, limit.price);

					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					if ( trade.stopId != 0 )
						cancelOrder( trade.stopId);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					/*if ( trade.averageUp ){
						double averageUpPrice = limit.price + FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
				}
			}

			/*
			if ((trade.stopMarketId != 0) && (data.high >= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.stopMarketSize, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.stopMarketSize;

				if ( trade.entryPrice == 0 )
					trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.stopMarketSize;
				//trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
			}*/


			
			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ((( orderId != 0 ) && (orderId == target.orderId)) || (( data != null ) && (target.orderId != 0 ) && (data.high >= target.price))))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					if ( data != null )
						AddCloseRecord(data.time, Constants.ACTION_SELL, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						if ( trade.stop != 0 ){
							info("replace stop order");
							cancelStop();
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						}
						if ( trade.targetId != 0 ){
							info("replace target order");
							cancelOrder(trade.targetId);
							trade.targetPos = trade.remainingPositionSize;
							logger.warning(symbol + " place target order " + trade.targetPrice + " " + trade.targetPos );
							trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
						}
					}
				}
			}
		}
	}

	private void OrderFilled_stopId(String time){
		
		info("RM Order stopped out @ " + trade.stop + " " + time);
		AddCloseRecord(time, Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);

		trade.stopId = 0;

		cancelTargets();
		
		//boolean reversequalified = true;
		//if (mainProgram.isNoReverse(symbol ))
		//	reversequalified = false;
		trade.status = Constants.STATUS_STOPPEDOUT;	
		//processAfterHitStopLogic_c();
		//removeTrade();
		return;
	}

	/*
	public void checkStopTarget(QuoteData data, int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		//QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//int lastbarL = quotesL.length - 1;
		if (lastbar == -1 )
			lastbar = 0;	// this could be triggered before all the quotes been completed
		
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check stop;
			if ((( orderId != 0 ) && (orderId == trade.stopId)) || (( data != null ) && (trade.stopId != 0) && (data.high > trade.stop))) 
			{
				info("stopped out @ " + trade.stop + " " + trade.getCurrentPositionSize() + " " + data.time);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.getCurrentPositionSize(), trade.stop);
				trade.stoppedOutPos = lastbar;
				trade.stopId = 0;
				trade.status = Constants.STATUS_STOPPEDOUT;
				trade.closeTime = quotes[lastbar].time;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes[lastbar].time);
				tv.addNameValue("Stopped Price", new Double(trade.stop).toString());
				trade.addTradeEvent(tv);
				
				cancelAllOrders();
				removeTrade();
				return;
			}

			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ((( orderId != 0 ) && (orderId == limit.orderId)) || (( data != null ) && (limit.orderId != 0 ) && (data.high >= limit.price ))))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
					}
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, limit.position_size, limit.price);

					limit.orderId = 0;
					limit.filled = true;
					//trade.entryPrice = limit.price;
					//trade.remainingPositionSize += limit.position_size;
					//if ( trade.remainingPositionSize == trade.positionSize )
					//	trade.status = Constants.STATUS_FILLED;
					//else
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						
					trade.setEntryTime(quotes[lastbar].time);

					trade.addFilledPosition(limit.price, limit.position_size,quotes[lastbar].time);
					trade.stop = trade.getStopPrice();
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.getCurrentPositionSize(), null);
					// calculate stop here
					/*
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

					trade.status = Constants.STATUS_PARTIAL_FILLED;
					trade.openOrderDurationInHour = 0;

					if ( trade.averageUp )
					{
						double averageUpPrice = limit.price - FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

/*					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
				}
			}


			
			if ((trade.stopMarketId != 0) && (data.low <= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.stopMarketSize, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.stopMarketSize;

				if ( trade.entryPrice == 0 )
					trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.stopMarketSize;
				//trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
			}

			

			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ( target.orderId != 0 ) && (data.low < target.price))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					AddCloseRecord(data.time, Constants.ACTION_BUY, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					cancelStop();
					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}
				}
			}
			
			

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ((( orderId != 0 ) && (orderId == trade.stopId)) || (( data != null ) && (trade.stopId != 0) && (data.low < trade.stop))) 
			{
				info("stopped out @ " + trade.stop + " " + + trade.getCurrentPositionSize() + " " + data.time);
				//trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.getCurrentPositionSize(), trade.stop);
				trade.stopId = 0;
				trade.status = Constants.STATUS_STOPPEDOUT;
				trade.stoppedOutPos = lastbar;
				trade.closeTime = quotes[lastbar].time;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes[lastbar].time);
				tv.addNameValue("Stopped Price", new Double(trade.stop).toString());
				trade.addTradeEvent(tv);

				cancelAllOrders();
				removeTrade();
				return;
			}

			
			
			
			
			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ((( orderId != 0 ) && (orderId == limit.orderId)) || (( data != null ) && (limit.orderId != 0 ) && (data.low <= limit.price ))))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
					}
					
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, limit.position_size, limit.price);

					limit.orderId = 0;
					//trade.entryPrice = limit.price;
					//trade.remainingPositionSize += limit.position_size;
					//if ( trade.remainingPositionSize == trade.positionSize )
					//	trade.status = Constants.STATUS_FILLED;
					//else
						trade.status = Constants.STATUS_PARTIAL_FILLED;
					trade.setEntryTime(quotes[lastbar].time);

					trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
					trade.stop = trade.getStopPrice();
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.getCurrentPositionSize(), null);

					/*
					// calculate stop here
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

					trade.openOrderDurationInHour = 0;

					if ( trade.averageUp )
					{
						double averageUpPrice = limit.price + FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}*/

/*					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
				}
			}

			
			if ((trade.stopMarketId != 0) && (data.high >= trade.stopMarketStopPrice) && (trade.stopMarketPosFilled == 0 ))
			{
				warning(" stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.stopMarketSize, trade.stopMarketStopPrice);
				trade.stopMarketPosFilled = trade.stopMarketSize;

				if ( trade.entryPrice == 0 )
					trade.entryPrice = trade.stopMarketStopPrice;
				trade.remainingPositionSize += trade.stopMarketSize;
				//trade.setEntryTime(quotes[lastbar].time);

				// calculate stop here
				if (trade.stop != 0 )
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				
				trade.stopMarketId = 0;
				trade.openOrderDurationInHour = 0;
			}

			// check stop;


			
			for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
			{
				TradePosition target = trade.targets[i];
				if (( target != null ) && ( target.orderId != 0 ) && (data.high > target.price))
				{
					warning("target hit, close " + target.position_size + " @ " + target.price);
					AddCloseRecord(data.time, Constants.ACTION_SELL, target.position_size, target.price);
					trade.targets[i].orderId = 0;

					cancelStop();
					trade.remainingPositionSize -= target.position_size;
					if (trade.remainingPositionSize <= 0)
					{
						cancelAllOrders();
						removeTrade();
						return;
					}
					else
					{
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}
					return;
				}
			}

		}
	}
*/


	
	void processAfterHitStopLogic_c()
	{
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

		
		if (Constants.ACTION_SELL.equals(prevAction))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			warning("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
			
			if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 ) || 
			(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))) 
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				double reversePrice = prevStop;
				boolean reversequalified = true;
				
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
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

					enterMarketPosition(reversePrice);
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(prevAction))
		{
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 

			if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
			     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
			{
				System.out.println(bt.toString(quotesL));
				//bt = determineBigTrend( quotesL);
				warning(" close trade with small tip");
				double reversePrice = prevStop;
				boolean reversequalified = true;
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

	
	
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Entry Setups
	//
	//
	// //////////////////////////////////////////////////////////////////////////////////////////////////////////


	
	
	
	
	public Trade checkPullbackSetup2(QuoteData data, double price )
	{
		if (( bigChartState != Constants.STATE_BREAKOUT_UP ) && ( bigChartState != Constants.STATE_BREAKOUT_DOWN ))
			return null;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);

		if  ( bigChartState == Constants.STATE_BREAKOUT_UP )
		{
			// find last 20MA touch point on 60 minute chart
			 //System.out.println("Now=" + data.time);
			
			int lastTouchMA = lastbar60;
			while (!( quotes60[lastTouchMA].low < ema20_60[lastTouchMA]) && ( quotes60[lastTouchMA].high > ema20_60[lastTouchMA])) 
				lastTouchMA--;
			//System.out.println("Last Touch 20MA = " + quotes60[lastTouchMA].time);
			
			 
			int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes60, ema20_60, lastTouchMA, 2);
			int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes60, ema20_60, lastTouchMA, 2);
			
			if ( lastDownPos > lastUpPos )
				return null;
			
			// find where the push starts
			int lastUpPosStart = lastUpPos-2;
			while ( quotes60[lastUpPosStart].low > ema20_60[lastUpPosStart])
				lastUpPosStart--;
			
			int start60 = Utility.getHigh( quotes60, lastUpPosStart, lastUpPos).pos;
		    //System.out.println("Start60=" + quotes60[start60].time);

		    if (previousTradeExist(Constants.TRADE_RM, Constants.ACTION_BUY, start60))
				return null;
			
		    for ( int i = lastbar60 -1; i > start60; i--)
		    {
			    PushList pushList = Pattern.findPast1Lows1(quotes60, start60, i );
					
				if (( pushList != null )&& (pushList.phls != null) && (pushList.phls.length > 0 ) )
				{
					PushHighLow phl = pushList.phls[pushList.phls.length-1];
				    //System.out.println("Start60=" + quotes60[start60].time);
				    //System.out.println("Push Detect at" + quotes60[phl.curPos].time);
					
					int pushDuration = phl.pushWidth;
					double pushSize = phl.pushSize;

					if ( data.high > phl.pullBack.high)
					{
						for ( int j = phl.pullBack.pos + 1; j < lastbar60; j++)
						{
							if ( data.high > phl.pullBack.high )
								return null;
						}
						
						System.out.println("Now=" + data.time);
						System.out.println("Last Touch 20MA = " + quotes60[lastTouchMA].time);
						System.out.println("Start60=" + quotes60[start60].time);
						System.out.println("push Top found" + quotes60[i].time + " " + data.time);
						logger.warning(symbol + " " + " buy detected " + data.time + " pushStart:" + quotes60[start60].time  );
						createOpenTrade(Constants.TRADE_RM, Constants.ACTION_BUY);

						trade.setEntryTime(quotes60[lastbar60].time);
						trade.entryPrice = phl.pullBack.high;
						trade.pushStartL = start60; 
						trade.pullBackPos = start60;  // this is used to track history
			
						trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
			
						enterMarketPosition(trade.entryPrice);
						return trade;
					}
					break;
				}
		    }
		}
		
		return null;
	
	}
	

	
	
	
	

	

	
	
	
	
	
	
	
	
	
	
	
	

	
	
	
	
	
	
	

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Entry
	//
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackPullBackEntry_new(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;

		int startL = Utility.findPositionByHour(quotesL, trade.pullBackStartTime, 2 );
		//System.out.println("startL:" + quotesL[startL].time + " " + startL);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// find last upbar;
	    	int lastUpbar = lastbarL - 1;
	    	while (!BarUtil.isUpBar(quotesL[lastUpbar]))
	    		lastUpbar--;
	    	int lastUpbarBegin = lastUpbar-1;
	    	while(BarUtil.isUpBar(quotesL[lastUpbarBegin]))
	    		lastUpbarBegin--;
	    	lastUpbarBegin = lastUpbarBegin + 1;
	    	
	    	double lowestUpPoint = Utility.getLow( quotesL, lastUpbarBegin, lastUpbar).low;
	    	
			if ( quotesL[lastbarL].low < lowestUpPoint )
			{
				//warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
				enterMarketPosition(lowestUpPoint);
				return;
				//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// entry at tips
			// find last upbar;
	    	int lastDownbar = lastbarL - 1;
	    	while (!BarUtil.isDownBar(quotesL[lastDownbar]))
	    		lastDownbar--;
	    	int lastDownbarBegin = lastDownbar-1;
	    	while(BarUtil.isDownBar(quotesL[lastDownbarBegin]))
	    		lastDownbarBegin--;
	    	lastDownbarBegin = lastDownbarBegin + 1;
	    	
	    	double highestDownPoint = Utility.getHigh( quotesL, lastDownbarBegin, lastDownbar).high;
	    	
			if ( quotesL[lastbarL].high > highestDownPoint )
			{
				//warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
				enterMarketPosition(highestDownPoint);
				return;
				//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
			}
		}

		return;
	}


	
	

	public void entry_incremental_fixed(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		int startPos60 = 0;
		double[] ema = Indicator.calculateEMA(quotes60, 20);
		
		IncrementalEntryFixed ie = (IncrementalEntryFixed)trade.entryDAO;

		// entry
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos60 = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getUp2PushList( quotes60, startPos60, lastbar60 );
			
			// adding positions
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) && (lastbar60 == pushList.end ))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				double triggerPrice = quotes60[lastPush.prePos].high;
				
				if ( trade.entryDAO == null) {
					if ( lastPush.pullBackSize > FIXED_STOP * PIP_SIZE ){
						warning("Initial trade entered at " + triggerPrice + " " + data.time);
						enterMarketPositionMulti(triggerPrice, POSITION_SIZE);
						
						// override the default
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						trade.stopId = 0;

						ie = new IncrementalEntryFixed(POSITION_SIZE); // DEFAULT
						trade.entryDAO = ie;
						ie.addPosition(triggerPrice, POSITION_SIZE);
					
					}
				}
				else{
					if ( ie.findEntry(triggerPrice) == false ){
						ie.addPosition(triggerPrice, POSITION_SIZE);
						triggerPrice = adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP);
						System.out.println(pushList.toString(quotes60, PIP_SIZE));
						warning("incremental limit " + ie.action + " order " + ie.positionSize + " placed at " + triggerPrice + " " + quotes60[lastbar60].time );
	
						enterMarketPositionMulti(triggerPrice, POSITION_SIZE);
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						trade.stopId = 0;
					}
				}
			}

			// unload positions
			if ( trade.entryDAO != null ){
				if ( quotes60[lastbar60].low < ema[lastbar60]){
	
					double triggerPrice = ema[lastbar60];
					for (int i = 1; i < ie.getPositions().size(); i++) {
						Position position = ie.getPositions().get(i);
						if (Constants.STATUS_OPEN.equals(position.status) && ( position.price > triggerPrice )){
	
							warning(data.time + " program stop tiggered, exit trade");
							AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
							closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
							return;
						}
					}
				}
			}

			if ( Constants.STATUS_PARTIAL_FILLED.equals( trade.status )){
				if (( quotes60[lastbar60-1].high < ema[lastbar60-1] ) && ( quotes60[lastbar60-2].high < ema[lastbar60-2] )){
					trade.stop = Utility.getHigh( quotes60, startPos60, lastbar60).high;
					warning(data.time + " price below 20MA, stop adding new trades, place stop at " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
					trade.status = Constants.STATUS_FILLED;
					return;
				}
			}
		}
	}

	
	
	
	
	
	
	
	
	public void entry_incremental_on_20MA(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		int startPos60 = 0;
		double[] ema = Indicator.calculateEMA(quotes60, 20);
		
		IncrementalEntryFixed ie = (IncrementalEntryFixed)trade.entryDAO;

		// entry
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startPos60 = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getUp2PushList( quotes60, startPos60, lastbar60 );
			
			// adding positions
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) && (lastbar60 == pushList.end ))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow lastPush = pushList.phls[pushSize - 1];
				double triggerPrice = quotes60[lastPush.prePos].high;
				
				if ( trade.entryDAO == null) {
					if ( lastPush.pullBackSize > FIXED_STOP * PIP_SIZE ){
						warning("Initial trade entered at " + triggerPrice + " " + data.time);
						enterMarketPositionMulti(triggerPrice, POSITION_SIZE);
						
						// override the default
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						trade.stopId = 0;

						ie = new IncrementalEntryFixed(POSITION_SIZE); // DEFAULT
						trade.entryDAO = ie;
						ie.addPosition(triggerPrice, POSITION_SIZE);
					
					}
				}
				/*
				else{
					if ( ie.findEntry(triggerPrice) == false ){
						ie.addPosition(triggerPrice, POSITION_SIZE);
						triggerPrice = adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP);
						System.out.println(pushList.toString(quotes60, PIP_SIZE));
						warning("incremental limit " + ie.action + " order " + ie.positionSize + " placed at " + triggerPrice + " " + quotes60[lastbar60].time );
	
						enterMarketPosition2(triggerPrice, POSITION_SIZE);
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						trade.stopId = 0;
					}
				}*/
			}

			// unload positions
			/*
			if ( trade.entryDAO != null ){
				if ( quotes60[lastbar60].low < ema[lastbar60]){
	
					double triggerPrice = ema[lastbar60];
					for (int i = 1; i < ie.getPositions().size(); i++) {
						Position position = ie.getPositions().get(i);
						if (Constants.STATUS_OPEN.equals(position.status) && ( position.price > triggerPrice )){
	
							warning(data.time + " program stop tiggered, exit trade");
							AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
							closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
							return;
						}
					}
				}
			}

			if ( Constants.STATUS_PARTIAL_FILLED.equals( trade.status )){
				if (( quotes60[lastbar60-1].high < ema[lastbar60-1] ) && ( quotes60[lastbar60-2].high < ema[lastbar60-2] )){
					trade.stop = Utility.getHigh( quotes60, startPos60, lastbar60).high;
					warning(data.time + " price below 20MA, stop adding new trades, place stop at " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
					trade.status = Constants.STATUS_FILLED;
					return;
				}
			}*/
		}
	}
	

	


	public void track_PBK_entry_using_momentum(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
	
		int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime, Constants.BACK_TO_FRONT );
	
		if (Constants.ACTION_BUY.equals(trade.action)){
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pushEnd = Utility.getHigh( quotesL, pushStart, pushEnd).pos;
			double pushSizeL = quotesL[pushEnd].high - quotesL[pushStart].low;

			if ( lastbarL - pushEnd >= 3 ){
				int lastDownBarEnd = lastbar60;
				while (!BarUtil.isDownBar(quotes60[lastDownBarEnd]))
					lastDownBarEnd--;
				int lastDownBarBegin = lastDownBarEnd;
				while (BarUtil.isDownBar(quotes60[lastDownBarBegin]))
					lastDownBarBegin--;
				lastDownBarBegin++;
				
				if ( data.high > quotes60[lastDownBarBegin].high ){
					double entryPrice = quotes60[lastDownBarBegin].high;
					enterMarketPositionMulti(entryPrice, POSITION_SIZE);
					return;
				}
			}
			
			if ( data.high > quotesL[pushEnd].high ){
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}

				double triggerPrice = quotesL[pushEnd].high;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
		}
		else if (Constants.ACTION_SELL.equals(trade.action)){
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pushEnd = Utility.getLow( quotesL, pushStart, pushEnd).pos;
			double pushSizeL = quotesL[pushStart].high - quotesL[pushEnd].low ;

			if ( lastbarL - pushEnd >= 3 ){
				int lastUpBarEnd = lastbar60;
				while (!BarUtil.isUpBar(quotes60[lastUpBarEnd]))
					lastUpBarEnd--;
				int lastUpBarBegin = lastUpBarEnd;
				while (BarUtil.isUpBar(quotes60[lastUpBarBegin]))
					lastUpBarBegin--;
				lastUpBarBegin++;
				
				if ( data.low < quotes60[lastUpBarBegin].low ){
					double entryPrice = quotes60[lastUpBarBegin].low;
					enterMarketPositionMulti(entryPrice, POSITION_SIZE);
					return;
				}
			}

			if ( data.low < quotesL[pushEnd].low ){
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}
	
				double triggerPrice = quotesL[pushEnd].low ;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
		}
	}
	
	
	
	
	public void track_PBK_entry_base60(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pushEnd = Utility.getHigh( quotesL, pushStart, pushEnd).pos;
			double pushSizeL = quotesL[pushEnd].high - quotesL[pushStart].low;
			/*
			int pullbackStart = Utility.findPositionByHour(quotes, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pullbackStart = Utility.getHigh( quotes, pullbackStart-4<0?0:pullbackStart-4, pullbackStart+4>lastbar?lastbar:pullbackStart+4).pos;
			
			QuoteData pullbackLow = Utility.getLow(quotes, pullbackStart, lastbar);
			if  (( quotes[pullbackStart].high - pullbackLow.low ) > 0.7 * pushSizeL ){
				warning("pullback low is" + pullbackLow.time + " " + pullbackLow.low + " pull back too deep, trade removed");
				removeTrade();
				return;
			}
			/*	
			PushList pushList = PushSetup.getDown2PushList(quotes, pullbackStart, lastbar);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
				{
					if ( realtime_count < 10 ){
						removeTrade();
						return;
					}
					
					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes[lastbar].time);
					//trade.addTradeEvent(tv);

					double triggerPrice = lastPush.pullBack.high;  
					//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					//enterMarketPosition(triggerPrice);
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					
					warning("Break LL triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.high );
					enterMarketPosition2(triggerPrice,remainingToBeFilledPos);
					cancelLimits();

					return;
				}
			}*/
			
			if ( data.high > quotesL[pushEnd].high )
			{
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}

				double triggerPrice = quotesL[pushEnd].high;

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].high );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		else if (Constants.ACTION_SELL.equals(trade.action))
		{
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pushEnd = Utility.getLow( quotesL, pushStart, pushEnd).pos;
			double pushSizeL = quotesL[pushStart].high - quotesL[pushEnd].low ;
			
			/*
			int pullbackStart = Utility.findPositionByHour(quotes, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pullbackStart = Utility.getLow( quotes, pullbackStart-4<0?0:pullbackStart-4, pullbackStart+4>lastbar?lastbar:pullbackStart+4).pos;
			
			QuoteData pullbackHigh = Utility.getHigh(quotes, pullbackStart, lastbar);
			if  (( pullbackHigh.high - quotes[pullbackStart].low ) > 0.7 * pushSizeL ){
				warning("pullback high is" + pullbackHigh.time + " " + pullbackHigh.high + " pull back too deep, trade removed");
				removeTrade();
				return;
			}
				
			PushList pushList = PushSetup.getUp2PushList(quotes, pullbackStart, lastbar);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.low < lastPush.pullBack.low ))
				{
					if ( realtime_count < 10 ){
						removeTrade();
						return;
					}
					
					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes[lastbar].time);
					//trade.addTradeEvent(tv);

					double triggerPrice = lastPush.pullBack.low; 

					//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					//enterMarketPosition(triggerPrice);
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					
					warning("Breakout HH base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					enterMarketPosition2(triggerPrice,remainingToBeFilledPos);
					cancelLimits();

					return;
				}
			}*/
			
			if ( data.low < quotesL[pushEnd].low )
			{
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}

				double triggerPrice = quotesL[pushEnd].low ;

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " base: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		

		return;
	}


	
	public void track_PBK_entry_base(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		if (Constants.ACTION_BUY.equals(trade.action))
		{
			int pushStart = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT );
			int pushEnd = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			double pushSizeL = quotesL[pushEnd].high - quotesL[pushStart].low;
			
			
			int pullbackStart = Utility.findPositionByHour(quotes, trade.getFirstBreakOutTime(), Constants.BACK_TO_FRONT );
			pullbackStart = Utility.getHigh( quotes, pullbackStart-4<0?0:pullbackStart-4, pullbackStart+4>lastbar?lastbar:pullbackStart+4).pos;
			
			QuoteData pullbackLow = Utility.getLow(quotes, pullbackStart, lastbar);
			if  (( quotes[pullbackStart].high - pullbackLow.low ) > pushSizeL ){
				warning("pullback low is" + pullbackLow.time + " " + pullbackLow.low + " pull back too deep, trade removed");
				removeTrade();
				return;
			}
				
			PushList pushList = PushSetup.getDown2PushList(quotes, pullbackStart, lastbar);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
				{
					if ( realtime_count < 10 ){
						removeTrade();
						return;
					}
					
					warning("Break lower low detected");
					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes[lastbar].time);
					trade.addTradeEvent(tv);

					double triggerPrice = lastPush.pullBack.high;  // data.close

					//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					//enterMarketPosition(triggerPrice);
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					
					warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.high );
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();

					return;
				}
			}
			
			if ( data.high > quotes[pullbackStart].high )
			{
				if ( realtime_count < 10 ){
					removeTrade();
					return;
				}

				double triggerPrice = quotes[pullbackStart].high;

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[pullbackStart].time + " @ " + quotes[pullbackStart].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		

		return;
	}

	
	
	
	


	
	public void trackEntryUsingChangedMomentum(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;

		int startL = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			startL = Utility.getLow( quotes, startL-4<0?0:startL-4, startL+4>lastbar?lastbar:startL+4).pos;

			// detect failed momentum
			int lastUpEnd = lastbar -1;
			while (!BarUtil.isUpBar(quotes[lastUpEnd]))
				lastUpEnd--;
			int lastUpBegin = lastUpEnd;
			while (!BarUtil.isUpBar(quotes[lastUpBegin]))
				lastUpBegin--;
	
			if ( data.low < quotes[lastUpBegin].low ){
				double triggerPrice = quotes[lastUpBegin].low;
			
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
			if ( data.low < quotes[startL].low )
			{
				double triggerPrice = quotes[startL].low;

				double highestPoint = Utility.getHigh( quotes, startL, lastbar).high;
				if (( highestPoint -  quotes[startL].low ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				if ( isFakeLowQuote( triggerPrice )){
					removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			startL = Utility.getHigh( quotes, startL-4<0?0:startL-4, startL+4>lastbar?lastbar:startL+4).pos;

			// detect failed momentum
			int lastDownEnd = lastbar -1;
			while (!BarUtil.isDownBar(quotes[lastDownEnd]))
				lastDownEnd--;
			int lastDownBegin = lastDownEnd;
			while (!BarUtil.isDownBar(quotes[lastDownBegin]))
				lastDownBegin--;
	
			if ( data.high > quotes[lastDownBegin].high ){
				double triggerPrice = quotes[lastDownBegin].high;
			
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
			if ( data.high > quotes[startL].high )
			{
				double triggerPrice = quotes[startL].high;

				double lowestPoint = Utility.getLow( quotes, startL, lastbar).low;
				if (( triggerPrice - lowestPoint ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				if ( isFakeHighQuote( triggerPrice )){
					removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes[startL].time + " @ " + quotes[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
		}
		return;
	}

	

	
	public void checkPullBack20_60min_trackEntryUsingConfirmation(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		double[] ema240_20 = Indicator.calculateEMA(quotes240, 20);
		//QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		//int lastbar60 = quotes60.length - 1;
		//double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		//PushHighLow lastPush=null;
		//PushHighLow prevPush=null;
		
		ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;
		//double[] ema = Indicator.calculateEMA(quotes240, returnToMA.getMa());

		int touchPoint = returnToMA.getEndPos() + 1;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (((lastbar240-1) >= touchPoint) && (quotes240[lastbar240-1].low > ema240_20[lastbar240-1])){
				removeTrade();
				return;
			}
			
			if (BarUtil.isDownBar(quotes240[lastbar240-1]) && ( data.low < quotes240[lastbar240-1].low )){
				enterMarketPositionMulti(quotes240[lastbar240-1].low,POSITION_SIZE);
				cancelLimits();
				return;
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (((lastbar240-1) >= touchPoint) && (quotes240[lastbar240-1].high < ema240_20[lastbar240-1])){
				removeTrade();
				return;
			}
			
			if (BarUtil.isUpBar(quotes240[lastbar240-1]) && ( data.high > quotes240[lastbar240-1].high )){
				enterMarketPositionMulti(quotes240[lastbar240-1].high,POSITION_SIZE);
				cancelLimits();
				return;
			}
		}
		
	}
	
	public void track_PBK_entry_using_everything(QuoteData data)
	{
			QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
			int lastbar = quotes.length - 1;
			QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
			int lastbar15 = quotes15.length - 1;
			double triggerPrice;
			
			int FIXED_STOP = getFIXED_STOP();
			double PIP_SIZE = getPIP_SIZE();
			int POSITION_SIZE = getPOSITION_SIZE();
			
			ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;
			
			// start point 
			int pullBackStart = 0;
			if (Constants.ACTION_BUY.equals(trade.action)){
				pullBackStart = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
				pullBackStart = Utility.getHigh( quotes, pullBackStart-4<0?0:pullBackStart-4, lastbar-1).pos;
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				pullBackStart = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
				pullBackStart = Utility.getLow( quotes, pullBackStart-4<0?0:pullBackStart-4, lastbar-1).pos;
			}

			int detectPos = Utility.findPositionByHour(quotes, trade.detectTime, Constants.BACK_TO_FRONT );

			// remove trade that pulls back too much
			/*
			if (Constants.ACTION_BUY.equals(trade.action)){
				double totalPushSize = returnToMA.pushEnd - returnToMA.pushStart;
				double pullBackSize = returnToMA.pushEnd - data.low;
				if 	(pullBackSize > (0.8 * totalPushSize)){
				//if 	((pullBackSize > (2.5 * PIP_SIZE * FIXED_STOP)) && (pullBackSize > (0.618 * totalPushSize))){
				//if 	(pullBackSize > (2.5 * PIP_SIZE * FIXED_STOP) ){
					removeTrade();
					return;
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				double totalPushSize = returnToMA.pushStart - returnToMA.pushEnd;
				double pullBackSize = data.high - returnToMA.pushEnd;
				if 	(pullBackSize > (0.8 * totalPushSize)){
				//if 	((pullBackSize > (2.5 * PIP_SIZE * FIXED_STOP)) && (pullBackSize > (0.618 * totalPushSize))){
				//if 	(pullBackSize > (2.5 * PIP_SIZE * FIXED_STOP)){
					removeTrade();
					return;
				}
			}*/
			

			
			
			
			// check peak high entries && HH_LL on 15min entries
			if (Constants.ACTION_BUY.equals(trade.action)){

				for ( int i = lastbar; i > pullBackStart; i-- ){
					PushList pushList = Pattern.findPast1Lows1(quotes, pullBackStart, i);
					if ((pushList != null ) &&( pushList.phls != null ) && ( pushList.phls.length > 0)){
						PushHighLow[] phls = pushList.phls;
						int pushSize = pushList.phls.length;
						PushHighLow lastPush = phls[pushSize - 1];
	
						if ( lastPush.curPos == lastbar ){
							super.strategy.sentAlertEmail(AlertOption.entry_point, trade.symbol + " " + trade.action + quotes[lastPush.prePos].low, trade);
							break;
						}

						if ( data.high > lastPush.pullBack.high ){
							triggerPrice = lastPush.pullBack.high;
							if ( quotes15[lastbar15-1].high > triggerPrice ){
								removeTrade();
								return;
							}
	
							warning("Trade entry_HH_LL: Trade buy entered at " + data.time + " " + trade.entryPrice);
							int positionSize = POSITION_SIZE - trade.remainingPositionSize;

							TradeEvent tv = new TradeEvent(TradeEvent.NEW_LOW, quotes[lastbar].time);
							tv.addNameValue("Entry breaking HH_LL", Double.toString(triggerPrice));
							trade.addTradeEvent(tv);
							super.strategy.sentAlertEmail(AlertOption.new_low, trade.symbol + " " + trade.action + quotes[lastPush.prePos].low, trade);
							
							//enterMarketPosition(trade.entryPrice);
							enterMarketPositionMulti(triggerPrice,positionSize);
							return;
						}
					}
				}
			}
			else if (Constants.ACTION_SELL.equals(trade.action)){
				
				for ( int i = lastbar; i > pullBackStart; i-- ){
					PushList pushList = Pattern.findPast1Highs1(quotes, pullBackStart, i);
					if ((pushList != null ) &&( pushList.phls != null ) && ( pushList.phls.length > 0)){
						PushHighLow[] phls = pushList.phls;
						int pushSize = pushList.phls.length;
						PushHighLow lastPush = phls[pushSize - 1];
	
						if ( lastPush.curPos == lastbar ){
							super.strategy.sentAlertEmail(AlertOption.entry_point, trade.symbol + " " + trade.action + quotes[lastPush.prePos].high, trade);
							break;
						}

						if ( data.low < lastPush.pullBack.low ){
							triggerPrice = lastPush.pullBack.low;
							if ( quotes15[lastbar15-1].low < triggerPrice ){
								removeTrade();
								return;
							}
	
							warning("Trade entry_HH_LL: Trade sell entered at " + data.time + " " + trade.entryPrice);
							int positionSize = POSITION_SIZE - trade.remainingPositionSize;

							TradeEvent tv = new TradeEvent(TradeEvent.NEW_LOW, quotes[lastbar].time);
							tv.addNameValue("Entry breaking HH_LL", Double.toString(triggerPrice));
							trade.addTradeEvent(tv);
							super.strategy.sentAlertEmail(AlertOption.new_low, trade.symbol + " " + trade.action + quotes[lastPush.prePos].low, trade);
							
							//enterMarketPosition(trade.entryPrice);
							enterMarketPositionMulti(triggerPrice,positionSize);
							return;
						}
					}
					break;
				}
			}
			
			// check fall out of bottom entry
			if (Constants.ACTION_BUY.equals(trade.action) && (data.high > quotes[pullBackStart].high)){
				triggerPrice = quotes[pullBackStart].high;
				if ( quotes15[lastbar15-1].high > triggerPrice ){
					removeTrade();
					return;
				}
				int positionSize = POSITION_SIZE - trade.remainingPositionSize;
				warning("fall out bottom buy entered at " + data.time + " " + triggerPrice);
				enterMarketPositionMulti(triggerPrice,positionSize);
				return;
			}
			else if (Constants.ACTION_SELL.equals(trade.action) && (data.low < quotes[pullBackStart].low)){
				triggerPrice = quotes[pullBackStart].low;
				if ( quotes15[lastbar15-1].low < triggerPrice ){
					removeTrade();
					return;
				}
				int positionSize = POSITION_SIZE - trade.remainingPositionSize;
				warning("fall out bottom sell entered at " + data.time + " " + triggerPrice);
				enterMarketPositionMulti(triggerPrice,positionSize);
				return;
			}	
	
			// MOON light
			/*
			if (Constants.ACTION_SELL.equals(trade.action)){
				if (( quotes[lastbar-2].high < ema20[lastbar-2]) && ( quotes[lastbar-1].high < ema20[lastbar-1]) && ((lastbar-1) > detectPos)){
					// moon light
					triggerPrice = data.close;
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					warning("moon light triggered at " + triggerPrice + " " + data.time );
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();
					return;
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if (( quotes[lastbar-2].low > ema20[lastbar-2]) && ( quotes[lastbar-1].low > ema20[lastbar-1]) && ((lastbar-1) > detectPos)){
					// moon light
					triggerPrice = data.close;
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					warning("moon light triggered at " + triggerPrice + " " + data.time );
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();
					return;
				}
			}*/


			
			/************************************
			 *  check momentum change and additional position after momentum
			 ************************************/
			/*
			if ( trade.pullBackSetup.momentumChangePoint == 0 ){
				if (Constants.ACTION_BUY.equals(trade.action)){
					MomentumDAO momentumChange = MomentumSetup.detect_momentum_change_by_close(quotes, Constants.DIRECTION_DOWN,  startPos, lastbar);
					if ( momentumChange != null ){
						//trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change BUY triggered " + momentumChange.triggerPrice + " " + data.time + " lastDownBegin:" + quotes[momentumChange.lastDownBarBegin].time + " " + quotes[momentumChange.lastDownBarBegin].high + " pullbackStart:"+ quotes[startPos].time);
						triggerPrice = momentumChange.triggerPrice;
						int positionSize = POSITION_SIZE - trade.remainingPositionSize;
						warning("momentume change  entered at " + data.time + " " + triggerPrice);
						enterMarketPositionMulti(triggerPrice,positionSize);
						return;
					}
				}
				else if (Constants.ACTION_SELL.equals(trade.action)){
					MomentumDAO momentumChange = MomentumSetup.detect_momentum_change_by_close(quotes, Constants.DIRECTION_UP,  startPos, lastbar);
					if ( momentumChange != null ){
						//trade.pullBackSetup.momentumChangePoint = momentumChange.momentumChangePoint;
						System.out.println("Momentum Change SELL triggered " + momentumChange.triggerPrice + " " + data.time + " lastUpBegin:" + quotes[momentumChange.lastUpBarBegin].time + " " + quotes[momentumChange.lastUpBarBegin].low + " pullbackStart:"+ quotes[startPos].time);
						triggerPrice = momentumChange.triggerPrice;
						int positionSize = POSITION_SIZE - trade.remainingPositionSize;
						warning("momentume change  entered at " + data.time + " " + triggerPrice);
						enterMarketPositionMulti(triggerPrice,positionSize);
						return;
					}
				}
			}
				/*
				else{
					if (Constants.ACTION_BUY.equals(trade.action)){
						//PushList push1List = PushSetup.findPast1Lows1(quotes, startPos, lastbar60);
						PushList push1List = PushSetup.findPastLowsByX(quotes,  startPos,  lastbar,  1 );
						if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
							PushHighLow lastPush = push1List.getLastPush();
							triggerPrice = quotes[lastPush.prePos].low;
							if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
								System.out.println("Add additonal positon after Momentum Change BUY triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes[lastPush.prePos].time + " " + quotes[lastPush.prePos].low);
								//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
								tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
								trade.pullBackSetup.numOfAdditionalPosition++;
								
								if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
									trade.stop = triggerPrice- FIXED_STOP*PIP_SIZE;
									System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
									tradeManager.createStopOrder(trade.stop);
								}
									//trade.status = Constants.STATUS_FILLED;  // can still add final positions
								return;
							}

						}
					}else if (Constants.ACTION_SELL.equals(trade.action)){
						//PushList push1List = PushSetup.findPast1Highs1(quotes, startPos, lastbar);
						PushList push1List = PushSetup.findPastHighsByX(quotes,  startPos,  lastbar,  1 );
						if (( push1List != null ) && ( push1List.getNumOfPushes() > 0 )){
							PushHighLow lastPush = push1List.getLastPush();
							triggerPrice = quotes[lastPush.prePos].high;
							if (!trade.findFilledPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
							//if (!trade.findLimitPosition(triggerPrice) && (trade.pullBackSetup.numOfAdditionalPosition < 2 )){
								System.out.println("Add additonal positon after Momentum Change SELL triggered " + triggerPrice + " " + data.time + " lastpeak:" + quotes[lastPush.prePos].time + " " + quotes[lastPush.prePos].low);
								//tradeManager.enterLimitPositionMulti(INCREMENT_POSITION_SIZE, triggerPrice);
								tradeManager.enterIncrementalMarketPosition(triggerPrice, INCREMENT_POSITION_SIZE, data.time);
								trade.pullBackSetup.numOfAdditionalPosition++;

								if ((trade.stop == 0 )&&( trade.pullBackSetup.numOfAdditionalPosition == 2 )){
									trade.stop = triggerPrice + FIXED_STOP*PIP_SIZE;
									System.out.println("set stop after 2 addtional positions: Stop set at " + trade.stop);
									tradeManager.createStopOrder(trade.stop);
								}
							}
						}
					}
				}*/
			

			//}

			
			
				/*************************************************
				// set stop and take profits
				// set stop by trend resume
				 * */
				/*
				if ((trade.pullBackSetup.momentumChangeDetected) && ( trade.pullBackSetup.trendResumePos == 0 )){
					MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes, Constants.DIRECTION_DOWN, lastbar-1);
					if ( mom != null ){
						System.out.println("Trend resume detected at " + quotes[lastbar].time);
						trade.pullBackSetup.trendResumePos = mom.startPos;
						// calculate stop;
					}
				}*/
				
			/*************************************************
			// detect trend resume
			 *************************************************/
			/*
			if (( trade.pullBackSetup.trendResumePos == 0 ) && ((trade.pullBackSetup.momentumChangePoint > 0 ) || Constants.STATUS_FILLED.equals(trade.status))){
				if (Constants.ACTION_BUY.equals(trade.action)){
					if ( BarUtil.isUpBar(quotes[lastbar-1])){
						int upBarEnd = lastbar-1;
						MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes, Constants.DIRECTION_UP, upBarEnd);//upBarBegin-1);
						if ( mom != null ){
							int downBarEnd = mom.startPos-1;
							int downBarBegin = downBarEnd;
							while (BarUtil.isDownBar(quotes[downBarBegin-1]))
								downBarBegin--;
							
							//double startPrice = quotes240[pushStart240].high;gg

							if (( quotes[mom.endPos].close > quotes[downBarBegin].high ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
								trade.pullBackSetup.trendResumePos = mom.startPos;
								System.out.println("Trend resume detected at " + quotes[trade.pullBackSetup.trendResumePos].time);
							}
						}
					}
				}
				else if (Constants.ACTION_SELL.equals(trade.action)){
					if ( BarUtil.isDownBar(quotes[lastbar-1])){
						int downBarEnd = lastbar-1;
						MomentumDAO mom = MomentumSetup.detect_large_momentum(quotes, Constants.DIRECTION_DOWN, downBarEnd);//upBarBegin-1);
						if ( mom != null ){
							int upBarEnd = mom.startPos-1;
							int upBarBegin = upBarEnd;
							while (BarUtil.isUpBar(quotes[upBarBegin-1]))
								upBarBegin--;
							if (( quotes[mom.endPos].close < quotes[upBarBegin].low ) && ( mom.startPos > (trade.pullBackSetup.momentumChangePoint+3))){
								trade.pullBackSetup.trendResumePos = mom.startPos;
								System.out.println("Trend resume detected at " + quotes[trade.pullBackSetup.trendResumePos].time);
							}
						}
					}
				}
			}*/
	}

	
	
	
	
	public void entry_diverage(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double triggerPrice = 0;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;

				if ( lastPullbackSize > 45 * PIP_SIZE ){
					triggerPrice = quotes60[lastPush.prePos].high;
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					enterMarketPositionMulti(triggerPrice,trade.POSITION_SIZE);
					cancelLimits();
					return;					
				}
			}
				
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = lastPush.pullBack.high - quotes60[lastPush.prePos].low;

				if ( lastPullbackSize > 45 * PIP_SIZE ){
					triggerPrice = quotes60[lastPush.prePos].low;
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					enterMarketPositionMulti(triggerPrice,trade.POSITION_SIZE);
					cancelLimits();
					return;					
				}
			}
		}
		
	}


	
	public void entry_small_breaks(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double triggerPrice = 0;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;

				if  ( pushSize > 3 ){
					triggerPrice = quotes60[lastPush.prePos].high;
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					enterMarketPositionMulti(triggerPrice,trade.POSITION_SIZE);
					cancelLimits();
					return;					
				}
			}
				
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = lastPush.pullBack.high - quotes60[lastPush.prePos].low;

				if  ( pushSize > 3 ){
					triggerPrice = quotes60[lastPush.prePos].low;
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					enterMarketPositionMulti(triggerPrice,trade.POSITION_SIZE);
					cancelLimits();
					return;					
				}
			}
		}
		
	}

	
	
	
	
	
	
	
	
	
	
	
	public void trackEntryUsingEllieteWave(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		ReturnToMADAO returnToMA = (ReturnToMADAO)trade.setupDAO;
		double[] ema = Indicator.calculateEMA(quotes240, returnToMA.getMa());
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			// run push setup
			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			//if ( pushList != null )
				//warning( trade.action + " EntryPushes:" + pushList.toString());
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;

				if (( lastPush != null ) && ( data.low < lastPush.pullBack.low ))
				{
					/*
					if ( realtime_count < 3 )  // do not hit if open lower
					{
						removeTrade();
						return;
					}*/

					double triggerPrice = lastPush.pullBack.low;// data.close
					if ( isFakeLowQuote( triggerPrice ))
					{
						removeTrade();
						return;
					}

					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();
					return;
				}

				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH, quotes60[lastbar60].time);
					tv.addNameValue("High Price", new Double(quotes60[lastPush.prePos].high).toString());
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					trade.addTradeEvent(tv);
					//double avgBarSize60 = BarUtil.averageBarSize2(quotes60);
					
					if (lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
					{
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						
						if ( trade.limit1Placed == false )
						{
							warning("Peak trade sell triggered on large pull back  @ " + quotes60[lastbar60].time + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.curPos].time );
							enterLimitPositionMulti( POSITION_SIZE/2, triggerPrice );
							trade.limit1Placed = true;
						}
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice + 0.5 * FIXED_STOP * PIP_SIZE );
						
						/* tempoilary disable this
						IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_SELL, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
						trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
						trade.entryDAO = ie;		
						return;*/
					}
					else if (lastPush.pullBackSize > FIXED_STOP * PIP_SIZE)
					{
						double totalPullBack = quotes60[lastPush.prePos].high - quotes60[startL].low;
						if ( lastPush.pullBackSize > 0.618 * totalPullBack )
						{
							/* tempoilary disable this
							IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_SELL, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
							trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
							trade.entryDAO = ie;		
							return;*/
						}
					}
				}
			}
			
			if ( data.low < quotes60[startL].low )
			{
				double triggerPrice = quotes60[startL].low;

				double highestPoint = Utility.getHigh( quotes60, startL, lastbar60).high;
				if (( highestPoint -  quotes60[startL].low ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				/*
				if ( realtime_count < 3 ){ 
					removeTrade();
					return;
				}

				QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
				int lastbar = quotes.length - 1;
				if (quotes[lastbar-1].low < triggerPrice)
					triggerPrice = data.close;
				*/
				
				if ( isFakeLowQuote( triggerPrice ))
				{
					removeTrade();
					return;
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
			}
			
			
			// run MA breakout and return setup
			//public static MATouch[] findMATouchUpsFromGoingDown(Object[] quotes, double[] ema, int begin, int end )
			double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsDown(quotes60, ema20_60, startL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				//wave2PtL = 1;
			}
			return;

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			//if ( pushList != null )
				//warning( trade.action + " EntryPushes:" + pushList.toString());
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
				{
					/*
					if ( realtime_count < 3 )  // do not hit if open lower
					{
						removeTrade();
						return;
					}*/

					double triggerPrice = lastPush.pullBack.high;  // data.close
					if ( isFakeHighQuote( triggerPrice ))
					{
						removeTrade();
						return;
					}

					//warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					//enterMarketPosition(triggerPrice);
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					
					warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.high );
					enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					cancelLimits();

					return;
				}

				if ( lastbar60 == pushList.end )
				{	
					int pullBackSize = (int)(lastPush.pullBackSize/PIP_SIZE);

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_LOW, quotes60[lastbar60].time);
					tv.addNameValue("Low Price", new Double(quotes60[lastPush.prePos].low).toString());
					tv.addNameValue("NumPush", new Integer(pushSize).toString());
					tv.addNameValue("PullBackSize", new Integer(pullBackSize).toString());
					trade.addTradeEvent(tv);
					//double avgBarSize60 = BarUtil.averageBarSize2(quotes60);
					
					if ( lastPush.pullBackSize > 2 * FIXED_STOP * PIP_SIZE)
					{
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						//enterMarketPosition(triggerPrice);
						//enterPartialPosition( triggerPrice, POSITION_SIZE/2);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						
						if ( trade.limit1Placed == false )
						{
							warning("Peak trade buy triggered on large pull back  @ " + quotes60[lastbar60].time + quotes60[lastPush.prePos].time + " " + quotes60[lastPush.curPos].time );
							enterLimitPositionMulti( POSITION_SIZE/2, triggerPrice );
							trade.limit1Placed = true;
						}
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice - 0.5 * FIXED_STOP * PIP_SIZE );

						/* tempoilary disable this
						IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_BUY, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
						trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
						trade.entryDAO = ie;		
						return;*/
					}
					else if (lastPush.pullBackSize > FIXED_STOP * PIP_SIZE)
					{
						double totalPullBack = quotes60[startL].high - quotes60[lastPush.prePos].low ;
						if ( lastPush.pullBackSize > 0.618 * totalPullBack )
						{
							/* tempoilary disable this
							IncrementalEntry ie = new IncrementalEntry(Constants.ACTION_BUY, Constants.CHART_60_MIN, quotes60[startL].time, POSITION_SIZE/2);
							trade.entryMode = Constants.ENTRY_MODE_INCREMENTAL;
							trade.entryDAO = ie;		
							return;*/
						}
					}

				}
			}
			
			if ( data.high > quotes60[startL].high )
			{
				double triggerPrice = quotes60[startL].high;

				double lowestPoint = Utility.getLow( quotes60, startL, lastbar60).low;
				if (( triggerPrice - lowestPoint ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				/*
				if ( realtime_count < 3 ){ 
					removeTrade();
					return;
				}

				QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
				int lastbar = quotes.length - 1;
				if (quotes[lastbar-1].high > triggerPrice)
					triggerPrice = data.close;
				*/
				if ( isFakeHighQuote( triggerPrice ))
				{
					removeTrade();
					return;
				}

				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				//enterMarketPosition(triggerPrice);
				//cancelLimits();
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}

			
		}
		

		return;
	}

	
	
	
	
	
	
	public void trackPullBackEntry3(QuoteData data)
	{
		//QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		//int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		//double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);
		PushHighLow lastPush, prevPush;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);
		//String startTime = trade.getFirstBreakOutStartTime();
		//int pullBackStartL = trade.pullBackStartL;
		String startTime = trade.getFirstBreakOutTime();
		int pullBackStartL = Utility.findPositionByHour(quotesL, startTime, Constants.FRONT_TO_BACK );
		
		//int start = Utility.findPositionByHour(quotesL, startTime, Constants.FRONT_TO_BACK );
		//int start2= Utility.findPositionByHour(quotesL, startTime,  Constants.BACK_TO_FRONT );
		
		//int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime,  Constants.BACK_TO_FRONT );
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			//startL = Utility.getLow( quotesL, start, start2).pos;
			//int pullBackStart = Utility.getLow(quotesL, trade.pullBackStartL, trade.detectPos).pos;
			//int pullBackStart = trade.pullBackStartL;
			
			//pullBackStartL = Utility.getLow(quotesL, pullBackStartL-4, pullBackStartL+4).pos;
			pullBackStartL = Utility.getLow(quotesL, pullBackStartL-4, lastbarL-1).pos;
			PushList pushList = PushSetup.getUp1PushList(quotesL, pullBackStartL, lastbarL);
			if ( pushList != null )
			{	
				//System.out.println(bt.direction);
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;

				if ( lastbarL == pushList.end )
				{
					// send notification email
					
					if ( pushSize >= 2 )
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							warning("Pullback trade sell triggered push size 2 at " + data.time + " @ " + quotesL[lastPush.prePos].high );
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >=3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							warning("Pullback trade sell triggered push size 3 at " + data.time + " @ " + quotesL[lastPush.prePos].high );
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
					}
					
					
					if ( pushSize >=4 )
					{
						warning("Pullback trade sell triggered push size 4 at " + data.time + " @ " + quotesL[lastPush.prePos].high );
						enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
						return;
					}
				}
				else if ( lastbarL == ( pushList.end - 1))
				{
					
				}
				
				
				if (( data.low < lastPush.pullBack.low ) &&  (lastQuoteData.low > lastPush.pullBack.low)) 
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade sell triggered < pullback low at " + data.time + " @ " + lastPush.pullBack.low );
					enterTrade( lastPush.pullBack.low, quotesL[lastbarL].time);
					//enterTrade( data.close, quotesL[lastbarL].time);
					return;
				}
			}

			
			if ( data.high > ema20L[lastbarL])
			{
				if ( lastbarL - pullBackStartL > 8 )
				{
					warning("Pullback trade sell triggered 8 bars at " + data.time + " @ " + data.close + " pullback start is " + quotesL[pullBackStartL].time + " trigged: " + quotesL[lastbarL].time + " " + quotesL[lastbarL].close);
					System.out.println(data.toString());
					//trade.entryPrice = data.close;
					//trade.setEntryTime(quotesL[lastbarL].time);
					//enterMarketPosition(trade.entryPrice);
					enterTrade( data.close, quotesL[lastbarL].time);
					return;
				}
			}
			
			if (( data.low < quotesL[pullBackStartL].low ) && (lastQuoteData.low > quotesL[pullBackStartL].low)) 
			{
				QuoteData afterHigh = Utility.getHigh( quotesL, pullBackStartL+1, lastbarL);
				if (( afterHigh != null ) && (( afterHigh.high - quotesL[pullBackStartL].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					removeTrade();
					return;
				}

				warning("Pullback trade sell triggered < start at " + data.time + " pullback: " + quotesL[pullBackStartL].time + " @ " + quotesL[pullBackStartL].low );
				//trade.positionSize = POSITION_SIZE;
				//trade.entryPrice = quotesL[pullBackStartL].low;
				//trade.entryTime = ((QuoteData) quotesL[lastbarL]).time;
				//enterMarketPosition(quotesL[pullBackStartL].low);
				enterTrade( quotesL[pullBackStartL].low, quotesL[lastbarL].time);
				//enterTrade( data.close, quotesL[lastbarL].time);
				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			// find first downbar after detection
			/*
			int firstUpbar = 0;
			for ( int i = detectPos+1; i < lastbarL; i++ )
				if ( quotesL[i].close > quotesL[lastbarL-1].open )
				{
					firstUpbar = i;
					break;
				}
			
			// if no above 20MA bar between the detection and downbar, enter the trade;
			boolean below = false;
			for ( int i = detectPos + 1; i <= lastbarL-1; i++)
			{
				if (quotesL[i].high < ema20L[i])
					below = true;
			}
				
			if (( firstUpbar != 0 ) && !below )
			{
				if ( data.high > quotesL[firstUpbar].high )
				{
					enterTrade( quotesL[firstUpbar].high, quotesL[lastbarL].time);
					return;
				}
			}*/
			/*
			MABreakOutList mol = MABreakOutTouchSetup.findMABreakOutsDown(quotes240, ema20_240,-1);
			if (( mol != null ) && (mol.getNumOfBreakOuts() > 0 ))
			{	
				System.out.print(mol.toString(quotes240));
				//if ( mol.getBreakOutTimes() > 2 )
					//return null;
				MABreakOut lastBreakOut = mol.getLastMBBreakOut();
				Push pullBack = Utility.findLargestPullBackFromLow(quotes240, lastBreakOut.begin, lastBreakOut.end );
				//System.out.println("pullback size is " + pullBack.pullback/PIP_SIZE);
				if ( pullBack.pullback > 2 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}
			}*/

			//startL = Utility.getHigh( quotesL, start, start2).pos;
			//int pullBackStart = Utility.getHigh(quotesL, trade.pullBackStartL, trade.detectPos).pos;
			//int pullBackStart = trade.pullBackStartL;
			pullBackStartL = Utility.getHigh(quotesL, pullBackStartL-4, pullBackStartL+4).pos;
			PushList pushList = PushSetup.getDown1PushList(quotesL, pullBackStartL, lastbarL);

			if ( pushList != null )
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];

				// enter based on push
				if ( lastbarL == pushList.end )
				{	
					// send notification email
					if ( pushSize >= 2 ) 
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							enterTrade( quotesL[lastPush.prePos].low, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >= 3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
	
					}
					
					if ( pushSize >= 4 )
					{
						enterTrade( quotesL[lastPush.prePos].low, quotesL[lastbarL].time);
						return;
					}
				}

				// enter based on reversal
				if ( data.high > lastPush.pullBack.high )
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade buy triggered at " + data.time + " @ " + lastPush.pullBack.high + " pullback: " + quotesL[lastPush.prePos].time + " " + quotesL[lastPush.curPos].time + " " + lastPush.pullBack.time );
					//double entryPrice = lastPush.pullBack.high;
					//trade.setEntryTime(quotesL[lastbarL].time);
					//trade.entryPrice = entryPrice;
					//enterMarketPosition(lastPush.pullBack.high);
					enterTrade( lastPush.pullBack.high, quotesL[lastbarL].time);
					return;
				}
			}
				
			
			if ( data.low < ema20L[lastbarL])
			{
				if ( lastbarL - pullBackStartL > 8 )
				{
					warning("Pullback trade buy triggered at long pullback" + data.time + " @ " + data.close);
					//trade.entryPrice = data.close;
					//trade.setEntryTime(quotesL[lastbarL].time);
					//enterMarketPosition(trade.entryPrice);
					enterTrade( data.close, quotesL[lastbarL].time);
					return;
				}
			}
			
			if ( data.high > quotesL[pullBackStartL].high )
			{
				QuoteData afterLow = Utility.getLow( quotesL, pullBackStartL+1, lastbarL);
				if (( afterLow != null ) && (( quotesL[pullBackStartL].high - afterLow.low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					removeTrade();
					return;
				}

				/*
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_UP.equals(btt.direction))
				{
					removeTrade();
					return;
				}*/

				System.out.println("Pullback trade buy triggered at " + data.time +  " pullback: " + quotesL[pullBackStartL].time + " @ " + quotesL[pullBackStartL].high );
				enterTrade( quotesL[pullBackStartL].high, quotesL[lastbarL].time);
				return;
			}
		}
		
	}

	

	private void enterTrade( double entryPrice, String entryTime)
	{
		if ( MODE != Constants.SIGNAL_MODE )
		{
			trade.setEntryTime(entryTime);
			trade.entryPrice = entryPrice;
			enterMarketPosition(entryPrice);
			return;
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	

	public int counting123(Object[] quotes, int begin, int end, int direction)
	{
		int pos = begin + 1;
		int count = 0;

		if (Constants.DIRECTION_UP == direction)
		{
			while (true)
			{
				// while (( pos <= end ) && (((QuoteData) quotes[pos]).high >=
				// ((QuoteData) quotes[pos-1]).high))
				// pos++;
				while ((pos <= end)
						&& !((((QuoteData) quotes[pos]).high < ((QuoteData) quotes[pos - 1]).high) && (((QuoteData) quotes[pos]).low < ((QuoteData) quotes[pos - 1]).low)))
					pos++;

				logger.info(symbol + " count high: " + ((QuoteData) quotes[pos - 1]).high + " at " + ((QuoteData) quotes[pos - 1]).time);
				count++;

				if (pos > end)
					return count;

				// now pos is smaller than pos-1
				double lastHigh = ((QuoteData) quotes[pos - 1]).high;
				while ((pos <= end) && (((QuoteData) quotes[pos]).high <= lastHigh))
					pos++;

				if (pos > end)
					return count;

				pos++;
				// now pos is > lastHigh
			}
		}
		else if (Constants.DIRECTION_DOWN == direction)
		{
			while (true)
			{
				// while (( pos <= end ) && (((QuoteData) quotes[pos]).low <=
				// ((QuoteData) quotes[pos-1]).low))
				// pos++;
				while ((pos <= end)
						&& !((((QuoteData) quotes[pos]).high > ((QuoteData) quotes[pos - 1]).high) && (((QuoteData) quotes[pos]).low > ((QuoteData) quotes[pos - 1]).low)))
					pos++;

				logger.info(symbol + " count low: " + ((QuoteData) quotes[pos - 1]).low + " at " + ((QuoteData) quotes[pos - 1]).time);
				count++;

				if (pos > end)
					return count;

				// now pos is smaller than pos-1
				double lastLow = ((QuoteData) quotes[pos - 1]).low;
				while ((pos <= end) && (((QuoteData) quotes[pos]).low >= lastLow))
					pos++;

				if (pos > end)
					return count;

				pos++;

				// now pos is < lastLow
			}
		}

		return Constants.NOT_FOUND;

	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Target
	//
	//
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
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
	
	
	
	
	
	
	
	
	
	
	
	
	


	
	public void exit123_latest( QuoteData data )
	{
		QuoteData[] quote15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quote15.length - 1;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
		int lastbar5 = quotes5.length - 1;
		
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		double avgSize1 = BarUtil.averageBarSizeOpenClose( quotes1 );

		MATouch[] maTouches = null;

		
		int LOT_SIZE = POSITION_SIZE/2;
		int tradeEntryPos60 = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quote15, trade.entryTime, Constants.BACK_TO_FRONT);
		int timePassed = lastbar60 - tradeEntryPos60; 

		
		/*********************************************************************
		 *  status: closed
		 *********************************************************************/
		if  (Constants.STATUS_CLOSED.equals(trade.status)){

			try{
				Date closeTime = IBDataFormatter.parse(trade.closeTime);
				Date currTime = IBDataFormatter.parse(data.time);
				
				if ((currTime.getTime() - closeTime.getTime()) > (0 * 60000L)){
					warning("trade closed, remove trade");
					removeTrade();
					return;
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}

			// not to do anything at the moment
			return;
		}
		

		/*********************************************************************
		 *  status: stopped out, check to reverse
		 *********************************************************************/
		if (Constants.STATUS_STOPPEDOUT.equals(trade.status)){ 	

			//processAfterHitStopLogic_c();
			double prevStop = trade.stop;
			String prevAction = trade.action;
			String prevType = trade.type;
			String prevEntryTime = trade.getEntryTime();
			double prevEntryPrice = trade.entryPrice;

			System.out.println("1111111111reverse trade is set to trade, try to reverse the trade");
			if (prevType == Constants.TRADE_CNT ){
				info("prev trade was CNT, reverse does not apply");
				removeTrade();
				return;
			}

			if ( reverse_trade != true ){
				info("reverse not turned on, remove trade");
				removeTrade();
				return;
			}

			System.out.println("reverse trade is set to trade, try to reverse the trade");
			double reversePrice = prevStop;
			boolean reversequalified = false;

			//  look to reverse if it goes against me soon after entry
			if (Constants.ACTION_SELL.equals(prevAction)){
				double lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPos60+1, lastbar60).low;
				info("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
				
				//if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 ) || 
					//(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))) 
				if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))/* && 
					( Constants.DIRECTION_UP == bt2.dir )) */
				{
					//System.out.println(bt.toString(quotes60));
					info("close trade with small tip, reverse trade to buy qualified");
					reversequalified = true;
				}
			}
			else if (Constants.ACTION_BUY.equals(prevAction)){
				double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPos60+1, lastbar60).high;
				info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 
 
				//if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
				    //(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
				if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE ))/* &&
					( Constants.DIRECTION_DOWN == bt2.dir )) */
				{
					info("close trade with small tip, reverse trade to sell qualified");
					reversequalified = true;
				}
			}
			
			// reverse;
			if ( reversequalified ){
				removeTrade();
				info("close trade with small tip, reverse trade qualified");
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
				enterMarketPosition(reversePrice, 0);
				return;
			}else{
				// stay to see if there is further opportunity
				trade.closeTime = data.time;
				trade.status = Constants.STATUS_CLOSED;
				return;
			}
		}

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// move stop
			if (trade.reach2FixedStop != true){
				if (( trade.entryPrice - data.low) > 2 * FIXED_STOP * PIP_SIZE){
					
				// take half profit
				//createTradeTargetOrder(POSITION_SIZE/2, adjustPrice(trade.pullBackStartPrice, Constants.ADJUST_TYPE_DOWN));

				double stop = trade.entryPrice;
				cancelOrder(trade.stopId);
				trade.stop = stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
				//trade.lastStopAdjustTime = data.time;
				warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
				trade.reach2FixedStop = true;
				}
			}
			
			/*
			if ((lastbarL > tradeEntryPosL + 2) && (trade.adjustStop == 0 )){
				if ( trade.peakPrice < trade.stop ){ 
					cancelOrder(trade.stopId);
					trade.stop = trade.peakPrice;
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				}
				trade.adjustStop = 1;
			}*/

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// move stop
			if (trade.reach2FixedStop != true){
				if (( data.high - trade.entryPrice) > 2 * FIXED_STOP * PIP_SIZE){

					// take half profit
					//createTradeTargetOrder(POSITION_SIZE/2, adjustPrice(trade.pullBackStartPrice, Constants.ADJUST_TYPE_UP));
				
					double stop = trade.entryPrice;
					cancelOrder(trade.stopId);
					trade.stop = stop;
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
					//trade.lastStopAdjustTime = data.time;
					warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					trade.reach2FixedStop = true;
				}
			}

			/*
			if ((lastbarL > tradeEntryPosL + 2) && (trade.adjustStop == 0 )){
				if ( trade.peakPrice > trade.stop ){ 
					cancelOrder(trade.stopId);
					trade.stop = trade.peakPrice;
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				trade.adjustStop = 1;
			}*/

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


	

	
	protected Trade findTradeHistory(String action, String firstBreakOutStartTime)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (firstBreakOutStartTime.equals(t.getFirstBreakOutStartTime())))
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
	
	/*
	public void createTrade( String action, double quantity, double price1 )
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length -1;
		
		if ( trade == null )
		{
			createOpenTrade("MANUAL", action);
			trade.status = Constants.STATUS_DETECTED;
			trade.positionSize = (int)(POSITION_SIZE*quantity);
			
			enterLimitPosition1(price1, trade.positionSize, 0); 
		}
		else
		{
			System.out.println(symbol + " Place Trade, trade already exist");
			System.out.println(symbol + " Place Trade, trade already exist");
			System.out.println(symbol + " Place Trade, trade already exist");
		}
	}*/
	
	


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

	
	
	public void trackPullBackTradeSell(QuoteData data, double price)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		double entryPrice = trade.entryPrice;


		int firstBreakOutL = trade.getFirstBreakOutPos(quotesL);
		QuoteData highAfterFirstBreakOut = Utility.getHigh( quotesL, firstBreakOutL+1, lastbarL );
		if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
		{
			double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
			if ( diverage > PULLBACK_SIZE )
			{
				double push = (quotesL[trade.prevUpStart].high - entryPrice)/PIP_SIZE;
				{
					info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
					removeTrade();   
					return;
				}
			}
		}

		
		
		double triggerPrice = 99999;
		if ( data != null )
			triggerPrice = data.low;
		else if ( price != 0 )
			triggerPrice = price;

		int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime, 2);

		int numOfBarCloseAboveMA = 0;
		for (  int i = lastbarL -1; i > detectPos; i-- ){
			if (quotesL[i].low > ema20L[i])
				numOfBarCloseAboveMA++;
		}	
		if ( numOfBarCloseAboveMA >= 2 ){
			removeTrade();
			return;
		}

		for (  int i = lastbarL -1; i > detectPos; i-- ){
			if ( BarUtil.isUpBar(quotesL[i])){
				int upBarBegin = i;
				while ( BarUtil.isUpBar(quotesL[upBarBegin-1]) )
					upBarBegin--;
				if ( triggerPrice < quotesL[upBarBegin].open ){
					warning("break DOWN trade entered at " + triggerPrice + " " + data.time + " low than last Up Bar of " + quotesL[upBarBegin].time );
					enterMarketPositionMulti(quotesL[upBarBegin].open,trade.POSITION_SIZE);
					return;
				}
			}
		}
		
		
		if (triggerPrice < entryPrice ) 
		{
			// check first 
			Calendar calendar = Calendar.getInstance();
			calendar.setTime( new Date(data.timeInMillSec));
			int weekday = calendar.get(Calendar.DAY_OF_WEEK);
			int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
			int minute=calendar.get(Calendar.MINUTE);

			if ((weekday == Calendar.SUNDAY) && (hour_of_day == 17) && (minute <= 16)){
				removeTrade();
				return;
			}
				
			// check trade history
			try{
				int lastStoppedOutTradeCount = 0;
				Iterator<Trade> it = tradeHistory.iterator();
				while (it.hasNext()){
					Trade t = it.next();
					Date now = 	IBDataFormatter.parse(data.time);
					if ( t.closeTime != null ){
						Date lastTradeCloseTime = IBDataFormatter.parse(t.closeTime);
						if ( Constants.STATUS_STOPPEDOUT.equals(trade.status) && ((now.getTime() - lastTradeCloseTime.getTime()) < 24 * 60 * 60000))
							lastStoppedOutTradeCount ++;
					}
				}
				if ( lastStoppedOutTradeCount >= 2 ){
					removeTrade();
					return;
				}
			}catch (ParseException e){
			}
			
			
			warning("break DOWN trade entered at " + data.time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
			if ( data != null )
				warning(data.time + " " + data.high + " " + data.low );
			else
				warning("last tick:" + price);
				
			if ( MODE == Constants.REAL_MODE )
			{	
				if (( ( data != null ) && (( data.timeInMillSec - firstRealTime ) < 60*60000L ) && (Math.abs( triggerPrice - entryPrice) > 5  * PIP_SIZE ))
					||  (Math.abs( triggerPrice - entryPrice) > 7  * PIP_SIZE ))
				{
					warning("Entry missed, set limit order of " + trade.entryPrice);
					entryPrice = adjustPrice( entryPrice, Constants.ADJUST_TYPE_UP);
					trade.openOrderPlacedTimeInMill = currQuoteData.timeInMillSec;
					trade.openOrderDurationInHour = 3;
					trade.limit1Stop = adjustPrice(entryPrice + FIXED_STOP  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					enterLimitPosition1(entryPrice, trade.POSITION_SIZE, trade.limit1Stop); 
					return;
				}
			}
			
			enterMarketPositionMulti(entryPrice,trade.POSITION_SIZE);

			//enterMarketPosition(entryPrice);
			//enterLimitPosition1(entryPrice);
		}
		
	}


	
	public void trackPullBackTradeBuy(QuoteData data, double price )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		double entryPrice = trade.entryPrice;

		
		int firstBreakOutL = trade.getFirstBreakOutPos(quotesL);
		QuoteData lowAfterFirstBreakOut = Utility.getLow( quotesL, firstBreakOutL+1, lastbarL );
		if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * FIXED_STOP * PIP_SIZE))
		{
			double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
			if ( diverage > PULLBACK_SIZE )
			{
				double push = (quotesL[trade.prevUpStart].high - entryPrice)/PIP_SIZE;
				{
					info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
					removeTrade();   
					return;
				}
			}
		}

		
		
		double triggerPrice = 0;
		if ( data != null )
			triggerPrice = data.high;
		else if ( price != 0 )
			triggerPrice = price;

		int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime, 2);
		
		int numOfBarCloseBelowMA = 0;
		for (  int i = lastbarL -1; i > detectPos; i-- ){
			if (quotesL[i].high < ema20L[i])
				numOfBarCloseBelowMA++;
		}	
		if ( numOfBarCloseBelowMA >= 2 ){
			removeTrade();
			return;
		}
		
		for (  int i = lastbarL -1; i > detectPos; i-- ){
			if ( BarUtil.isDownBar(quotesL[i])){
				int downBarBegin = i;
				while ( BarUtil.isDownBar(quotesL[downBarBegin-1]) )
					downBarBegin--;
				if ( triggerPrice > quotesL[downBarBegin].open ){
					warning("break UP trade entered at " + triggerPrice + " " + data.time + " higher than recent downbar of " + quotesL[downBarBegin].time );
					enterMarketPositionMulti(quotesL[downBarBegin].open,trade.POSITION_SIZE);
					return;
				}
			}
		}

		if (triggerPrice > entryPrice) 
		{
			Calendar calendar = Calendar.getInstance();
			calendar.setTime( new Date(data.timeInMillSec));
			int weekday = calendar.get(Calendar.DAY_OF_WEEK);
			int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
			int minute=calendar.get(Calendar.MINUTE);

			if ((weekday == Calendar.SUNDAY) && (hour_of_day == 17) && (minute <= 16)){
				removeTrade();
				return;
			}

			// check trade history
			try{
				int lastStoppedOutTradeCount = 0;
				Iterator<Trade> it = tradeHistory.iterator();
				while (it.hasNext()){
					Trade t = it.next();
					Date now = 	IBDataFormatter.parse(data.time);
					if ( t.closeTime != null ){
						Date lastTradeCloseTime = IBDataFormatter.parse(t.closeTime);
						if ( Constants.STATUS_STOPPEDOUT.equals(trade.status) && ((now.getTime() - lastTradeCloseTime.getTime()) < 24 * 60 * 60000))
							lastStoppedOutTradeCount ++;
					}
				}
				if ( lastStoppedOutTradeCount >= 2 ){
					removeTrade();
					return;
				}
			}catch (ParseException e){
			}

			
			trade.setEntryTime(quotes[lastbar].time);
			trade.entryPrice = entryPrice;
			
			trade.targetPrice = adjustPrice(entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);

			warning("break UP trade entered at " + data.time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
			if ( data != null )
				warning(data.time +	" " + data.high + " " + data.low );
			else
				warning("last tick:" + price);
				
			trade.stop = trade.triggerPrice - FIXED_STOP * PIP_SIZE;
			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);

			if ( MODE == Constants.REAL_MODE )
			{	
				if ((( data != null ) && (( data.timeInMillSec - firstRealTime ) < 60*60000L ) && (Math.abs( triggerPrice - entryPrice) > 5  * PIP_SIZE ))
					||  (Math.abs( triggerPrice - entryPrice) > 7  * PIP_SIZE ))
				{
					warning("Entry missed, set limit order of " + trade.entryPrice);
					entryPrice = adjustPrice( entryPrice, Constants.ADJUST_TYPE_DOWN);
					trade.openOrderPlacedTimeInMill = currQuoteData.timeInMillSec;
					trade.openOrderDurationInHour = 3;
					trade.limit1Stop = adjustPrice(entryPrice - FIXED_STOP  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					enterLimitPosition1(entryPrice, trade.POSITION_SIZE, trade.limit1Stop); 
					return;
				}
			}

			enterMarketPositionMulti(entryPrice,trade.POSITION_SIZE);
			//enterMarketPosition(entryPrice);
			
			//enterLimitPosition1(entryPrice);
		}
	
	}	
	

	
	
	
	public void trackPullbackEntryUsingWeakBreakOut(QuoteData data)
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		PushList pushListHHLL = null;
		double triggerPrice;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			// run push setup
			pushListHHLL = PushSetup.findPastHighsByX(quotes60, startL, lastbar60-2, 1);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				lastPush = pushListHHLL.phls[pushSize - 1];
				double prevHigh = quotes60[lastPush.prePos].high;
				double breakout = quotes60[lastbar60-2].high - prevHigh;
				
				if (( breakout < (PIP_SIZE * FIXED_STOP / 3)) &&  
				   ( quotes60[lastbar60-1].high < quotes60[lastbar60-2].high)){
					triggerPrice = data.close;
					logger.info(data.time + " " + data.close + " weak breakout entry: prevHigh" + quotes60[lastPush.prePos].time + " " + prevHigh + " breakout dist:" + breakout + " trend start:" + quotes60[startL].time);
					trade.peakPrice = quotes60[lastbar60-2].high + PIP_SIZE;
			    	enterMarketPosition(triggerPrice);
					super.strategy.sentAlertEmail(AlertOption.trigger, trade.symbol + " " + trade.action + " " + triggerPrice + " weak breakout triggered", trade);
					return;
				}
			}

			/*
			PushList pushList = PushSetup.getUp2PushList( quotes60, startL, lastbar60 );
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotes60[lastPush.prePos].high - lastPush.pullBack.low;

				if (( lastPush != null ) && ( data.low < lastPush.pullBack.low ))
				{
					triggerPrice = lastPush.pullBack.low;
					warning("Break higher high detected " + data.time + " " + triggerPrice);
					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_HIGHER_HIGH, quotes60[lastbar60].time);
					//trade.addTradeEvent(tv);

					for ( int j = lastPush.pullBack.pos +1; j < lastbar60; j++ ){
						if (quotes60[j].low < triggerPrice){
							removeTrade();
							return;
						}
					}

					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					warning("Breakout higher high triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + lastPush.pullBack.low );
			    	enterMarketPosition(triggerPrice);
					return;
				}

			}*/
			
			/*
			if ( data.low < quotes60[startL].low )
			{
				triggerPrice = quotes60[startL].low;
				double highestPoint = Utility.getHigh( quotes60, startL, lastbar60).high;
				if (( highestPoint -  quotes60[startL].low ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				for ( int j = startL +1; j < lastbar60; j++ ){
					if (quotes60[j].low < triggerPrice){
						removeTrade();
						return;
					}
				}

				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
		    	enterMarketPosition(triggerPrice);
				//enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				//cancelLimits();
				return;
			}*/
			
			
			// run MA breakout and return setup
			//public static MATouch[] findMATouchUpsFromGoingDown(Object[] quotes, double[] ema, int begin, int end )
			/*
			double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
			MABreakOutList molL = MABreakOutAndTouch.findMABreakOutsDown(quotes60, ema20_60, startL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				//wave2PtL = 1;
			}
			return;*/
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

			pushListHHLL = PushSetup.findPastLowsByX(quotes60, startL, lastbar60-2, 1);
			if ((pushListHHLL != null ) &&( pushListHHLL.phls != null ) && ( pushListHHLL.phls.length > 0)){
				int pushSize = pushListHHLL.phls.length;
				lastPush = pushListHHLL.phls[pushSize - 1];
				double prevLow = quotes60[lastPush.prePos].low;
				double breakout = prevLow - quotes60[lastbar60-2].low;
				
				if (( breakout < (PIP_SIZE * FIXED_STOP / 3)) &&   
				   ( quotes60[lastbar60-1].low > quotes60[lastbar60-2].low)){
					triggerPrice = data.close;
					logger.info(data.time + " " + data.close + " weak breakout entry: prevLow" + quotes60[lastPush.prePos].time + " " + prevLow + " breakout dist:" + breakout + " trend start:" + quotes60[startL].time);
					trade.peakPrice = quotes60[lastbar60-2].low - PIP_SIZE;
			    	enterMarketPosition(triggerPrice);
					super.strategy.sentAlertEmail(AlertOption.trigger, strategy.getStrategyName() + " " + strategy.IB_PORT + " " + trade.symbol + " " + trade.action + " " + triggerPrice + " weak breakout triggered", trade);
					return;
				}
			}

			
			/*
			PushList pushList = PushSetup.getDown2PushList(quotes60, startL, lastbar60);
			//if ( pushList != null )
				//warning( trade.action + " EntryPushes:" + pushList.toString());
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];

				if (( lastPush != null ) && ( data.high > lastPush.pullBack.high ))
				{
					triggerPrice = lastPush.pullBack.high;  // data.close
					warning("Break lower low detected " + data.time + " " + triggerPrice);
					//TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_BREAK_LOWER_LOW, quotes60[lastbar60].time);
					//trade.addTradeEvent(tv);

					for ( int j = lastPush.pullBack.pos +1; j < lastbar60; j++ ){
						if (quotes60[j].high > triggerPrice){
							removeTrade();
							return;
						}
					}

					
					int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
					warning("Breakout lower low buy triggered at " + triggerPrice + " " + data.time + " pullback: " + lastPush.pullBack.time + " @ " + triggerPrice );
			    	enterMarketPosition(triggerPrice);
			    	//enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
					//cancelLimits();

					return;
				}
			}*/
			
			/*
			if ( data.high > quotes60[startL].high )
			{
				triggerPrice = quotes60[startL].high;

				double lowestPoint = Utility.getLow( quotes60, startL, lastbar60).low;
				if (( triggerPrice - lowestPoint ) > 2 * FIXED_STOP * PIP_SIZE){
					removeTrade();
					return;
				}

				for ( int j = startL + 1; j < lastbar60; j++ ){
					if (quotes60[j].low < triggerPrice){
						removeTrade();
						return;
					}
				}

				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				//enterMarketPosition(triggerPrice);
				//cancelLimits();
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				
				warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
		    	enterMarketPosition(triggerPrice);

				//enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				//cancelLimits();

				return;
			}*/
		}
		

		return;
	}

	
	private boolean isFakeLowQuote( double triggerPrice )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
		int lastbar = quotes.length - 1;
		if (/*( realtime_count < 3 ) ||*/ (quotes[lastbar-1].low < triggerPrice))
			return true;
		return false;
	}

	private boolean isFakeHighQuote( double triggerPrice )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
		int lastbar = quotes.length - 1;
		if (/*( realtime_count < 3 ) ||*/ (quotes[lastbar-1].high > triggerPrice))
			return true;
		return false;
	}


	public void trackPullbackEntryUsingMoonLight(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getLow( quotes, startL-4<0?0:startL-4, startL+4>lastbar?lastbar:startL+4).pos;

			// run push setup
			if (( quotes[lastbar].high < ema20[lastbar]) && ( quotes[lastbar-1].high < ema20[lastbar-1])){
				// moon light
				
				// do some checking
				
				double triggerPrice = data.close;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("moon light triggered at " + triggerPrice + " " + data.time );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();
				return;
				
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int startL = Utility.findPositionByHour(quotes, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
			startL = Utility.getHigh( quotes, startL-4<0?0:startL-4, startL+4>lastbar?lastbar:startL+4).pos;

			// run push setup
			if (( quotes[lastbar].low > ema20[lastbar]) && ( quotes[lastbar-1].low > ema20[lastbar-1])){
				// moon light
				
				// do some checking

				//warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				//enterMarketPosition(triggerPrice);
				//cancelLimits();
				double triggerPrice = data.close;
				int remainingToBeFilledPos = trade.POSITION_SIZE - trade.getCurrentPositionSize();
				warning("moon light triggered at " + triggerPrice + " " + data.time );
				enterMarketPositionMulti(triggerPrice,remainingToBeFilledPos);
				cancelLimits();

				return;
			}
		}
		

		return;
	}



	
}


