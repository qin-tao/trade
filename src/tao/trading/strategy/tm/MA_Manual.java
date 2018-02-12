package tao.trading.strategy.tm;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.Instrument;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.entry.AfterEntryAddMonitor;
import tao.trading.entry.PullbackConfluenceEntry;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.NameValue;
import tao.trading.strategy.util.TimeUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

import com.ib.client.EClientSocket;

public class MA_Manual extends TradeManager2
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
	boolean average_up = false;
	boolean auto_target = false;
	
	boolean market_order = true;
	String checkPullbackSetup_start_date = null;
	boolean trade_detect_email_notifiy = true;
	AfterEntryAddMonitor afterEntryAddMonitorEntry;


	
	public MA_Manual()
	{
		super();
	}
	
	public MA_Manual(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
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
	
	/*****************************************************************************************************************************
	 * 
	 * 
	 * Static Methods
	 * 
	 * 
	 *****************************************************************************************************************************/
	public void process( int req_id, QuoteData data )
	{
		//if ( req_id != 509 )  //509
		  //return;
		//System.out.println("DD " + data.time);
			//if (data.time.equals("20130920  08:22:00"))
					//System.out.println(data.time);
		//System.out.println(STRATEGY_NAME + ".");

		if (req_id == getInstrument().getId_realtime())
		{
			if ((MODE == Constants.TEST_MODE) &&  (trade != null) && Constants.STATUS_PLACED.equals(trade.status))
				checkStopTarget(data);

			if ((trade != null ) && ( MODE != Constants.SIGNAL_MODE )) 
			{
				/*String hr = data.time.substring(9,12).trim();
				String min = data.time.substring(13,15);
				int hour = new Integer(hr);
				int minute = new Integer(min);
				*/
					manage(data );
				
				
				//if ( MODE == Constants.TEST_MODE )
				//	tradeManager[i].report2(data.close);

				//tradeManager[i].lastQuoteData = data;
				return;
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

	public Trade detect( QuoteData data )
	{
		return null;
	}

	public void entry( QuoteData data )
	{
	}
		
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		/*
		if (Constants.STATUS_FILLED.equals(trade.status) || Constants.STATUS_PARTIAL_FILLED.equals(trade.status)){
			if ( afterEntryAddMonitorEntry == null )
				afterEntryAddMonitorEntry = new AfterEntryAddMonitor(this);
			afterEntryAddMonitorEntry.entry_manage(data, this); 
		}
		/*
		if (( trade != null ) && (Constants.STATUS_FILLED.equals(trade.status) ))//|| Constants.STATUS_STOPPEDOUT.equals(trade.status))) 
		{
			monitor_exit(data);
			//processTradEvents();
		}*/
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		//QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		//int lastbarL = quotesL.length - 1;

		warning("order filled:" + orderId + " " + filled);
		
		if (orderId == trade.stopId)
		{
			warning("order " + orderId + " stopped out ");
			trade.stopId = 0;
			trade.status = Constants.STATUS_STOPPEDOUT;
			trade.closeTime = quotes[lastbar].time;

			TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes[lastbar].time);
			tv.addNameValue("Stopped Price", new Double(trade.stop).toString());
			trade.addTradeEvent(tv);

			cancelTargets();
			cancelStopMarkets();
			
			//processAfterHitStopLogic_c();
			removeTrade();
			return;

		}
		else if (orderId == trade.stopMarketId)   
		{
			warning("stop market order: " + orderId + " " + filled + " filled");
			trade.stopMarketId = 0;  // avoid sometime same message get sent twoice

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.stopMarketSize, trade.stopMarketStopPrice);

			trade.stopMarketPosFilled += filled; 
			trade.remainingPositionSize += trade.stopMarketSize; //+= filled;
			//trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			if ( trade.stop != 0 ){
				if ( trade.stopId != 0 ){
					info("cancel stop " );
					cancelStop();
				}
				
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			trade.openOrderDurationInHour = 0;
			//trade.status = Constants.STATUS_FILLED;
		
		}

		// check limit entries
		for ( int i = 0; i < Trade.TOTAL_LIMITS; i++ )
		{
			TradePosition limit = trade.limits[i];
			if (( limit != null ) && ( limit.orderId == orderId ))
			{
				info(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
				limit.orderId = 0;
				limit.filled = true;

				if (trade.recordOpened == false){
					CreateTradeRecord(trade.type, Constants.ACTION_SELL.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, limit.position_size, limit.price);
					
				if ( trade.getEntryTime() == null )
					trade.setEntryTime(quotes[lastbar].time);
				trade.addFilledPosition(limit.price, limit.position_size, quotes[lastbar].time);
				trade.remainingPositionSize += limit.position_size;
				trade.status = Constants.STATUS_PARTIAL_FILLED;
					
				// calculate stop here
				cancelStop();
				String oca = new Long(new Date().getTime()).toString();
				if (Constants.ACTION_SELL.equals(trade.action))
				{
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);
					if ( auto_target == true ){
						trade.targetPrice = adjustPrice(limit.price - DEFAULT_PROFIT_TARGET  * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos = trade.remainingPositionSize;
						trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
					}
					
					if ( average_up == true ){
						double averageDownPrice =  adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.stopMarketSize = limit.position_size;
						info("place average down order " + averageDownPrice + " " + trade.stopMarketSize);
						trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageDownPrice, trade.stopMarketSize, null);
					}
				}
				else if (Constants.ACTION_BUY.equals(trade.action)){
					if ( trade.stop == 0 )
						trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);

					if ( auto_target == true ){
						trade.targetPrice = adjustPrice(limit.price + DEFAULT_PROFIT_TARGET  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos = trade.remainingPositionSize;
						trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPos, oca);
					}
					
					if ( average_up == true ){
						double averageUpPrice =  adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.stopMarketSize = limit.position_size;
						info("place average up order " + averageUpPrice + " " + trade.stopMarketSize);
						trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, trade.stopMarketSize, null);
					}
				}				
				
				if ( trade.entryPrice == 0 ) 
					trade.entryPrice = limit.price;
				trade.openOrderDurationInHour = 0;
				trade.status = Constants.STATUS_FILLED;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
				tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
				trade.addTradeEvent(tv);
				return; // limit order found, no need to continue;
			}
		}

		//  check targets
		for ( int i = 0; i < trade.TOTAL_TARGETS; i++ )
		{
			TradePosition target = trade.targets[i];
			if (( target != null ) && ((( orderId != 0 ) && (orderId == target.orderId)) ))
			{
				warning("target hit, close " + target.position_size + " @ " + target.price);
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
						trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
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


	
	
	

	
	//////////////////////////////////////////////////////////////////////////
	//
	//  Custom Methods
	//
	///////////////////////////////////////////////////////////////////////////
	
	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		//QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		//int lastbarL = quotesL.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && ( trade.limits[i].filled == false ) && (data.high >= limit.price ) )
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
					}
					AddOpenRecord(data.time, Constants.ACTION_SELL, limit.position_size, limit.price);

					trade.entryPrice = limit.price;
					trade.remainingPositionSize += limit.position_size;
					trade.setEntryTime(quotes[lastbar].time);

					// calculate stop here
					if ( !trade.scaleIn )
					{	
					    if ( trade.stop == 0 )
						    trade.stop = adjustPrice(limit.price + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					    trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}

					trade.status = Constants.STATUS_FILLED;
					trade.openOrderDurationInHour = 0;

					if (( i == 0 ) && ( trade.averageUp == false )) // only first limit order will create a stop market order
					{
						double averageUpPrice = limit.price - FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_SELL, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
					trade.limits[i].filled = true;
				}
			}

			
			
			/*
			
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

				trade.status = Constants.STATUS_FILLED;
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

				if (trade.remainingPositionSize == trade.positionSize)
					trade.status = Constants.STATUS_PLACED;
			}*/

			
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

			
			// check stop;
			if ((trade.stopId != 0) && (data.high > trade.stop))
			{
				info("stopped out @ " + trade.stop + " " + trade.remainingPositionSize + " " + data.time);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				trade.stoppedOutPos = lastbar;
				trade.stopId = 0;
				trade.status = Constants.STATUS_STOPPEDOUT;
				trade.closeTime = quotes[lastbar].time;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes[lastbar].time);
				tv.addNameValue("Stopped Price", new Double(trade.stop).toString());
				trade.addTradeEvent(tv);

				cancelTargets();
				cancelStopMarkets();
				removeTrade();
				return;
			}

			// check target;
/*			if ((trade.targetId1 != 0) && (data.low < trade.targetPrice1))
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

			}*/
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
					else if ( trade.stop != 0 )
					{
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}
				}
			}
			
			

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check entry limit;
			for ( int i = 0; i < trade.TOTAL_LIMITS; i++ )
			{
				TradePosition limit = trade.limits[i];
				if (( limit != null ) && (limit.filled = false ) && (data.low >= limit.price ))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
					}
					AddOpenRecord(data.time, Constants.ACTION_BUY, limit.position_size, limit.price);

					trade.entryPrice = limit.price;
					trade.remainingPositionSize += limit.position_size;
					trade.setEntryTime(quotes[lastbar].time);

					// calculate stop here
					if ( !trade.scaleIn )
					{	
					    if ( trade.stop == 0 )
						    trade.stop = adjustPrice(limit.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
					    trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}

					trade.status = Constants.STATUS_FILLED;
					trade.openOrderDurationInHour = 0;

					if (( i == 0 ) && ( trade.averageUp == false )) // only first limit order will create a stop market order
					{
						double averageUpPrice = limit.price + FIXED_STOP * PIP_SIZE;
						trade.stopMarketSize = POSITION_SIZE;
						trade.stopMarketStopPrice = averageUpPrice;
						trade.stopMarketId = placeStopOrder(Constants.ACTION_BUY, averageUpPrice, POSITION_SIZE, null);
						trade.averageUp = true;
					}

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
					trade.limits[i].filled = true;
				}
			}

			
			// check entry limit;
			/*
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

				trade.status = Constants.STATUS_FILLED;
				trade.openOrderDurationInHour = 0;

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

				if (trade.remainingPositionSize == trade.positionSize)
					trade.status = Constants.STATUS_PLACED;
			}*/

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
			if ((trade.stopId != 0) && (data.low < trade.stop))
			{
				info("stopped out @ " + trade.stop + " " + + trade.remainingPositionSize + " " + data.time);
				//trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;
				trade.status = Constants.STATUS_STOPPEDOUT;
				trade.stoppedOutPos = lastbar;
				trade.closeTime = quotes[lastbar].time;

				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes[lastbar].time);
				tv.addNameValue("Stopped Price", new Double(trade.stop).toString());
				trade.addTradeEvent(tv);

				cancelTargets();
				cancelStopMarkets();
				removeTrade();
				return;
			}

			// check target;
			/*if ((trade.targetId1 != 0) && (data.high > trade.targetPrice1))
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
			}*/

			
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
					else if ( trade.stop != 0 )
					{
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}
					return;
				}
			}

		}
	}



	
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
	

	public void checkTradeExpiring_ByTime(QuoteData data)
	{
		/*
		Calendar cal = Calendar.getInstance();
	    cal.setTime(now);
	    int hour = cal.get(Calendar.HOUR_OF_DAY);
	    int min = cal.get(Calendar.MINUTE);
	    
	    int minOfDay = hour * 60 + min;
	    */
		
		if (!trade.status.equals(Constants.STATUS_FILLED))
		{	
		    try{
			    Date detectTime = IBDataFormatter.parse(trade.detectTime);
				Date now = new Date();
		    	
			    if (now.getTime() - detectTime.getTime() > 8 * 60 * 60000L)
				{
					warning( "trade " + trade.action + " expired after 8 hours: "+ now.toString());
					removeTrade();
				}
		    }
		    catch ( Exception e)
		    {
		    	e.printStackTrace();
		    }
		}
	}

	
/*
	public void processTradEvents()
	{
		if ( trade == null )
			return;
		
		ArrayList<TradeEvent> tes = trade.getTradeEvents();

		//TradeEvent [] te = new TradeEvent[tes.size()];
		//TradeEvent[] tradeEvents = (TradeEvent[])tes.toArray(te); 

		Iterator<TradeEvent> it = tes.iterator();
		while( it.hasNext())
		{
			TradeEvent te = it.next();
			
			if ( te.processed == false )
			{
				if (TradeEvent.TRADE_STOPPEDOUT.equals(te.getEventName()))
				{
					cancelTargets();

					//boolean reversequalified = true;
					//if (mainProgram.isNoReverse(symbol ))
					//	reversequalified = false;
						
					//processAfterHitStopLogic_c();
					if (Constants.TRADE_CNT.equals(trade.type))
						removeTrade();
				}
				
				if (MODE == Constants.REAL_MODE)
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail(symbol + te.getEventName(), "sent from automated trading system");
				}
				te.processed = true;
			}
		}
	}

	*/

	
	
	
	public void monitor_exit( QuoteData data )
	{
		
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
		int lastbar5 = quotes5.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		int lastbar240 = quotes240.length - 1;
		int hr = new Integer(data.time.substring(10,12).trim());
		MATouch[] maTouches = null;
		
		int tradeEntryPos15 = Utility.findPositionByHour(quotes15, trade.entryTime, 2 );
		int tradeEntryPos60 = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos240 = TimeUtil.get240Pos(trade.entryTime, quotes240);
		int timePassed = lastbar60 - tradeEntryPos60; 
		
		trade.monitorHrAfterEntry = TimeUtil.timeDiffInHr(data.time, trade.entryTime);
		trade.monitorCurrentHour = new Integer(data.time.substring(10,12).trim());
		if (Constants.ACTION_SELL.equals(trade.action))
			trade.monitorProfitInPips = (int)((trade.entryPrice - data.low)/PIP_SIZE);
		else if (Constants.ACTION_BUY.equals(trade.action))
			trade.monitorProfitInPips = (int)((data.high - trade.entryPrice )/PIP_SIZE);

		
		//if (Constants.SETUP_SCALE_IN.equals(trade.entrySetup))
		if ( trade.scaleIn )
		{
			//if (inTradingTime( hour, minute ))
			//trackPullBackScaleInEntry(data, quotes60, ema20_60);
			//return;
		}

		
		/*********************************************************************
		 *  status: closed
		 *********************************************************************/
		if  (Constants.STATUS_CLOSED.equals(trade.status) || Constants.STATUS_STOPPEDOUT.equals(trade.status))
		{
			try{
			
				Date closeTime = IBDataFormatter.parse(trade.closeTime);
				Date currTime = IBDataFormatter.parse(data.time);
				
				if ((currTime.getTime() - closeTime.getTime()) > (120 * 60000L))
				{
					removeTrade(); //temporily not remove the file
					return;
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return;
			}
		}
		
		///////////////////////////////////////////////////////////////////////////

		/*********************************************************************
		 *  status: stopped out, check to reverse
		 *********************************************************************/
		if ( monitor_reverse(data) == Constants.STOP)
			return;

		monitor_quick_profit_move(data);
			
		/*********************************************************************
		 *  detect first momentum move
		 *********************************************************************/
		if ( monitor_contra_spike(data) == Constants.STOP)
			return;
			
			
		
		
		/*********************************************************************
		 *  status: detect profit and move stop
		 *********************************************************************/
		monitor_profit(data);
	
	}
	

	
	
	
	
	
	
	
	
	
	
	
	  void reverseTrade( double reversePrice )
	   {
			QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
			int lastbar15 = quotes15.length - 1;

			//processAfterHitStopLogic_c();
			String prevAction = trade.action;
			//String prevType = trade.type;

			removeTrade();

			createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL.equals(prevAction)?Constants.ACTION_BUY:Constants.ACTION_SELL);
			trade.detectTime = quotes15[lastbar15].time;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.entryPrice = reversePrice;
			trade.setEntryTime(((QuoteData) quotes15[lastbar15]).time);

			enterMarketPosition(reversePrice);
			TradeEvent tv = new TradeEvent(TradeEvent.TRADE_REVERSE, quotes15[lastbar15].time);
			tv.addNameValue(new NameValue(TradeEvent.NAME_ENTRY_PRICE, (new Double(reversePrice)).toString()));
			trade.addTradeEvent(tv);
			return;

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
		
		

		
		
		
	  
	  
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Target
	//
	//
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
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
	
	
	
	
	
	
	
	
	
	
	
	
	

	
	
	
	
	
	


	
	

	
	// this is the first half profit taking
/*	protected void takeProfit2( double price, int size )
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
	}*/

	// this is to take all profit
	protected boolean exitTradePlaced()
	{
		if ( trade == null )
			return true;
		
		return trade.targetPlaced1;
	}
	
/*
	protected void takeProfit( double price, int size )
	{
		if (trade.targetPlaced1 == true)  // order already placed earlier
			return;
		
		if ( trade.targetPlaced2 == true )
		{	
			if ( size + trade.targetPos2 > trade.positionSize )
				size = trade.positionSize - trade.targetPos2;
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
	}*/


	protected int takePartialProfit( double price, int size )
	{
		for ( int i = 0; i < trade.TOTAL_TARGETS; i++)
		{
			TradePosition tp = trade.targets[i];
			if ((tp != null ) && ( tp.price == price) && ( tp.position_size == size))  // already placed
				return 0;
		}
		
		for ( int i = 0; i < trade.TOTAL_TARGETS; i++)
		{
			if ( trade.targets[i] == null )
			{
				TradePosition partial = new TradePosition(price, size, null );

				String action = Constants.ACTION_BUY;
				if (trade.action.equals(Constants.ACTION_BUY))
					action = Constants.ACTION_SELL;
					
				partial.orderId = placeLmtOrder(action, price, size, null);
				warning("take partcal profitprofit remainning profit " + action + " target order placed@ " + price + " " + size );

				trade.targets[i] = partial;
				return partial.orderId;
			}
		}
		
		return 0;
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

	



	private int monitor_reverse(QuoteData data ){
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		int lastbar240 = quotes240.length - 1;
		
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
		if (  Constants.STATUS_STOPPEDOUT.equals(trade.status)){
			if (( reverse_trade == true ) && (trade.type != Constants.TRADE_CNT ))
			{	
				double prevStop = trade.stop;
				String prevAction = trade.action;
				double prevEntryPrice = trade.entryPrice;
				double avgSize15 = BarUtil.averageBarSizeOpenClose( quotes15 );
	
				if (Constants.ACTION_SELL.equals(prevAction))
				{
					//  look to reverse if it goes against me soon after entry
					/*
					double lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPosL, lastbar60).low;
					warning("low point after entry is " + lowestPointAfterEntry + " entry price:" + prevEntryPrice); 
					
					if ((( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 ) || 
					(( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) && (( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE ))) 
					{
						System.out.println(bt.toString(quotes60));
						//bt = determineBigTrend( quotesL);
						warning(" close trade with small tip");
						reverseTrade( prevStop );
						return;
					}*/
					if ( lastbar15 == trade.stoppedOutPos ) 
					{
						double lastbarSize = quotes15[lastbar15].close - quotes15[lastbar15].open;
						if ( lastbarSize > 2 * avgSize15 )
						{
							TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
							tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(lastbarSize/PIP_SIZE))));
							trade.addTradeEvent(tv);
							reverseTrade( data.close );
							return 0;
						}
					}
					else if ( lastbar15 == trade.stoppedOutPos + 1 )
					{
						double lastbarSize = quotes15[trade.stoppedOutPos].close - quotes15[trade.stoppedOutPos].open;
						if ( lastbarSize > 2 * avgSize15 )
						{
							TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
							tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(lastbarSize/PIP_SIZE))));
							trade.addTradeEvent(tv);
							reverseTrade( data.close );
							return 0;
						}
					}
					else if ( lastbar15 > trade.stoppedOutPos + 1 )
					{
						trade.closeTime = data.time;
						trade.status = Constants.STATUS_CLOSED;

						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes1[lastbar1].time);
						trade.addTradeEvent(tv);
						removeTrade();
						return 0;
					}

				}
				else if (Constants.ACTION_BUY.equals(prevAction))
				{
					//  look to reverse if it goes against me soon after entry
					/*
					double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPosL, lastbar60).high;
					info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 
	 
					if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
					     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
					{
						//bt = determineBigTrend( quotesL);
						warning(" close trade with small tip");
						reverseTrade( prevStop );
						return;
					}*/
					if ( lastbar15 == trade.stoppedOutPos ) 
					{
						double lastbarSize = quotes15[lastbar15].open - quotes15[lastbar15].close;
						if ( lastbarSize > 2 * avgSize15 )
						{
							TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
							tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(lastbarSize/PIP_SIZE))));
							trade.addTradeEvent(tv);
							reverseTrade( data.close );
							return 0;
						}
					}
					else if ( lastbar15 == trade.stoppedOutPos + 1 )
					{
						double lastbarSize = quotes15[trade.stoppedOutPos].open - quotes15[trade.stoppedOutPos].close;
						if ( lastbarSize > 2 * avgSize15 )
						{
							TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
							tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(lastbarSize/PIP_SIZE))));
							trade.addTradeEvent(tv);
							reverseTrade( data.close );
							return 0;
						}
					}
					else if ( lastbar15 > trade.stoppedOutPos + 1 )
					{
						trade.closeTime = data.time;
						trade.status = Constants.STATUS_CLOSED;

						TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes1[lastbar1].time);
						trade.addTradeEvent(tv);
						removeTrade();
						
						return 0;
					}
				}
				
				return 0;
			}
			
			// stay to see if there is further opportunity
		}

		return 1;
	}
	
	
	
	private int monitor_quick_profit_move(QuoteData data){

		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		int lastbar240 = quotes240.length - 1;

		int profit = 0;
		if (Constants.ACTION_SELL.equals(trade.action))
			profit = (int)((trade.entryPrice - data.close)/ PIP_SIZE);
		else if (Constants.ACTION_BUY.equals(trade.action))
			profit = (int)((data.close - trade.entryPrice )/ PIP_SIZE);;

		/*********************************************************************
		 *  detect first momentum move
		 *********************************************************************/
		//System.out.println("EntryTime " + trade.entryTime + " " + TimeUtil.atNight( trade.entryTime ) + " " + hrAfterEntry + " " + hr);
		if (TimeUtil.atNight( trade.entryTime ) && (trade.monitorHrAfterEntry < 6) && (trade.monitorHrAfterEntry >= 0 ) && ( trade.monitorCurrentHour >1 ) && ( trade.monitorCurrentHour < 5 ))
		{
			// contra bar counting the last 8 bars
			double breakOutBarSize = Math.abs(quotes60[lastbar60-1].close - quotes60[lastbar60-1].open);
			//double avgBarSize60 = BarUtil.averageBarSize(quotes60);
			//if ( BarUtil.barSize(quotes60[lastbar60-1]) > avgBarSize60 )
			if ( breakOutBarSize > (FIXED_STOP * PIP_SIZE/2) )
			{
				if (Constants.ACTION_SELL.equals(trade.action) && BarUtil.isUpBar(quotes60[lastbar60-1]))
				{	
					// if a green bar > 3 previous red bars
					//System.out.println("Is Upper Bar " + data.time);
					//double breakOutBarSize = quotes60[lastbar60-1].close - quotes60[lastbar60-1].open;
					int downBars = 0;
					boolean breakOut = true;
					for ( int i = lastbar60-2; i > lastbar60-8; i--)
					{
						if (BarUtil.isDownBar(quotes60[i]))
						{
							downBars++;
							if ( BarUtil.barSize(quotes60[i]) > breakOutBarSize )
								breakOut = false;
						}
						if ( downBars > 4 )
							break;
					}
					if ( breakOut )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONTR_BREAK_OUT_BAR_60, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, (int)(breakOutBarSize/PIP_SIZE)));
						trade.addTradeEvent(tv);
					}
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && BarUtil.isDownBar(quotes60[lastbar60-1]))
				{
					//double breakOutBarSize = quotes60[lastbar60-1].open - quotes60[lastbar60-1].close;
					int upBars = 0;
					boolean breakOut = true;
					for ( int i = lastbar60-2; i > lastbar60-8; i--)
					{
						if (BarUtil.isUpBar(quotes60[i]))
						{
							upBars++;
							if ( BarUtil.barSize(quotes60[i]) > breakOutBarSize )
								breakOut = false;
						}
						if ( upBars > 4 )
							break;
					}
					if ( breakOut )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONTR_BREAK_OUT_BAR_60, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, (int)(breakOutBarSize/PIP_SIZE)));
						trade.addTradeEvent(tv);
					}
				}
			}

		
		}

		return Constants.CONTINUE;
	}
	
	
    /*********************************************************************
	 *  status: detect an counter spike move
	 *********************************************************************/
	private int monitor_contra_spike( QuoteData data ){
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		int lastbar240 = quotes240.length - 1;

		//if ( lastbar1 > 10 )
		if (TimeUtil.atNight( trade.entryTime ) && ( trade.monitorHrAfterEntry < 6) && (trade.monitorHrAfterEntry >= 0 ) && ( trade.monitorCurrentHour >1 ) && ( trade.monitorCurrentHour < 5 ))
		{	
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				// check if there is a big move against the trade
				/*
				double spike5S = data.close - data.open;
				if (spike5S > 8 * PIP_SIZE) 
				{
					//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_5S, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike5S/PIP_SIZE))));
					trade.addTradeEvent(tv);
					if (trade.type != Constants.TRADE_CNT )
					{
						reverseTrade( data.close );
						break;
					}
					else
						closeTrade();
				}
				
				// check 1 minute
				double spike1M = Math.abs( quotes1[lastbar1-1].close - quotes1[lastbar1-1].open);
				if (Utility.isUpBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 4 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
					trade.addTradeEvent(tv);
				}
				
				// check multiple minutes
				ConsectiveBars consec1 = Utility.findLastConsectiveUpBars(quotes1);
				if ( consec1 != null )
				{
					double upSize = quotes1[consec1.getEnd()].close - quotes1[consec1.getBegin()].open;
					if ( upSize > 12 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_1M, quotes1[lastbar1].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}*/

				ConsectiveBars consec15 = Utility.findLastConsectiveUpBars(quotes15);
				if ( consec15 != null )
				{
					int upBarNum = consec15.getSize();
					double upSize = quotes15[consec15.getEnd()].close - quotes15[consec15.getBegin()].open;
					if (((upBarNum == 1 ) && ( upSize >= (15 * PIP_SIZE)/*(PIP_SIZE * FIXED_STOP/2))*/)) || (( upBarNum ==2 ) && ( upSize > (0.8 * FIXED_STOP * PIP_SIZE))))
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}

				/*
				ConsectiveBars consec60 = Utility.findLastConsectiveUpBars(quotes60);
				if ( consec60 != null )
				{
					double upSize = quotes60[consec60.getEnd()].close - quotes60[consec60.getBegin()].open;
					if ( upSize > FIXED_STOP * 1.5 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_60M, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}*/
				
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				/*
				double spike5S = data.open - data.close;
				if (spike5S > 8 * PIP_SIZE) 
				{
					//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_5S, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike5S/PIP_SIZE))));
					trade.addTradeEvent(tv);
					if (trade.type != Constants.TRADE_CNT )
						reverseTrade( data.close );
					else
						closeTrade();
				}

				
				// check 1 minute
				double spike1M = Math.abs( quotes1[lastbar1-1].close - quotes1[lastbar1-1].open);
				if (Utility.isDownBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 4 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
					trade.addTradeEvent(tv);
				}

				if (Utility.isUpBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 4 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.PROFIT_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
					trade.addTradeEvent(tv);
				}

				
				// check multiple minutes
				ConsectiveBars consec1 = Utility.findLastConsectiveDownBars(quotes1);
				if ( consec1 != null )
				{
					double downSize = quotes1[consec1.getBegin()].open - quotes1[consec1.getEnd()].close;
					if ( downSize > 12 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_1M, quotes1[lastbar1].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}*/

				
				ConsectiveBars consec15 = Utility.findLastConsectiveDownBars(quotes15);
				if ( consec15 != null )
				{
					int downBarNum = consec15.getSize();
					double downSize = quotes15[consec15.getBegin()].open - quotes15[consec15.getEnd()].close;
					if (((downBarNum == 1 ) && ( downSize >= (PIP_SIZE * FIXED_STOP/2))) || (( downBarNum ==2 ) && ( downSize > (0.8 * FIXED_STOP * PIP_SIZE))))
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}

				/*
				ConsectiveBars consec60 = Utility.findLastConsectiveDownBars(quotes60);
				if ( consec60 != null )
				{
					double downSize = quotes60[consec60.getBegin()].open - quotes60[consec60.getEnd()].close;
					if ( downSize > FIXED_STOP * 1.5 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_60M, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}*/
			}
		}

		
		
		/*********************************************************************
		 *  EXIT if there is a contra move???
		 *********************************************************************/
		/*
		if ((Constants.ACTION_SELL.equals(trade.action)) && ( data.close < trade.entryPrice ))
		{
			TradeEvent te = trade.findLastEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M);
			if ( te != null )
			{
				takeProfit( adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
				return;
			}
		}
		else if ((Constants.ACTION_BUY.equals(trade.action)) && ( data.close > trade.entryPrice ))
		{
			TradeEvent te = trade.findLastEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M);
			if ( te != null )
			{
				takeProfit( adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
				return;
			}
		}*/
		
		return Constants.CONTINUE;

	}
	
	
	
	private int monitor_profit(QuoteData data)
	{
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);
		int lastbar5 = quotes5.length - 1;
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		int lastbar240 = quotes240.length - 1;

		int tradeEntryPos15 = Utility.findPositionByHour(quotes15, trade.entryTime, 2 );
		int tradeEntryPos60 = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos240 = TimeUtil.get240Pos(trade.entryTime, quotes240);

		if (( trade.reach1FixedStop == false ) && (trade.monitorProfitInPips > FIXED_STOP ))
			trade.reach1FixedStop = true;
		
		int profitTimesStop = trade.monitorProfitInPips/FIXED_STOP;
		if ((trade.scaleIn == false) && (trade.reach2FixedStop == false) && (profitTimesStop >= 2 ))
		{
			placeBreakEvenStop();
			trade.reach2FixedStop = true;
			TradeEvent tv = new TradeEvent(TradeEvent.STOP_SIZE_PROFIT_REACHED, quotes1[lastbar1].time);
			tv.addNameValue(new NameValue(TradeEvent.NAME_STOP_SIZE_PROFIT, (new Integer(profitTimesStop)).toString()));
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
		
		/*********************************************************************
		 *  status: detect peaks 60 min
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getHigh(quotes60, tradeEntryPos60-12, pushStart).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes60, pushStart, lastbar60);
			if ((pushList != null ) && (pushList.end == lastbar60) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				double price = quotes60[lastPush.prePos].low;
				int pullback = (int)((lastPush.pullBack.high - quotes60[lastPush.prePos].low)/PIP_SIZE);

				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
//					System.out.println(pushList.toString(quotes60, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_LOW_60, quotes60[lastbar60].time);
					tv.setHeader("Exit:");
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes60[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
					//warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
					//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
				}
				
				
				/******************************************************************************
				// move stops
				 * ****************************************************************************/
				/* temporilary disable this
				if (trade.reach2FixedStop == true)
				{	
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = lastPush.prePos+1; j < lastPush.curPos; j++)
						if ( quotes60[j+1].high > quotes15[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(lastPush.pullBack.high, Constants.ADJUST_TYPE_UP);
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
			int pushStart = (lastbar60 - tradeEntryPos60 > 4)? tradeEntryPos60+4: lastbar60;
			pushStart = Utility.getLow(quotes60, tradeEntryPos60-12, pushStart).pos;
			
			PushList pushList = PushSetup.getUp2PushList(quotes60, pushStart, lastbar60);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && (pushList.end == lastbar60) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1];
				double price = quotes60[lastPush.prePos].high;
				int pullback = (int)((quotes60[lastPush.prePos].high - lastPush.pullBack.low)/PIP_SIZE);


				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					//System.out.println(pushList.toString(quotes60, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_HIGH_60, quotes60[lastbar60].time);
					tv.setHeader("Exit:");
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes60[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
					//warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
					//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), POSITION_SIZE/2 );
				}

				/******************************************************************************
				// move stop
				 * ****************************************************************************/
				/* tempoarly disable this
				if (trade.reach2FixedStop == true)
				{	
					double stop = adjustPrice(lastPush.pullBack.low, Constants.ADJUST_TYPE_DOWN);
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


		
		
		/*********************************************************************
		 *  status: detect peaks 15 min
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int pushStart = (lastbar15 - tradeEntryPos15 > 4)? tradeEntryPos15+4: lastbar15;
			pushStart = Utility.getHigh(quotes15, tradeEntryPos15-12, pushStart).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes15, pushStart, lastbar15);
			if ((pushList != null ) && (pushList.end == lastbar15) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				double price = quotes15[lastPush.prePos].low;
				int pullback = (int)((lastPush.pullBack.high - quotes15[lastPush.prePos].low)/PIP_SIZE);

				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					//System.out.println(pushList.toString(quotes15, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_LOW_15, quotes15[lastbar15].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes15[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int pushStart = (lastbar15 - tradeEntryPos15 > 4)? tradeEntryPos15+4: lastbar15;
			pushStart = Utility.getLow(quotes15, tradeEntryPos15-12, pushStart).pos;
			PushList pushList = PushSetup.getUp2PushList(quotes15, pushStart, lastbar15);

			if ((pushList != null ) && (pushList.end == lastbar15) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				double price = quotes15[lastPush.prePos].high;
				int pullback = (int)((quotes15[lastPush.prePos].high - lastPush.pullBack.low)/PIP_SIZE);
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					//System.out.println(pushList.toString(quotes15, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_HIGH_15, quotes15[lastbar15].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes15[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
				}
			}
		}

		
		/*********************************************************************
		 *  status: detect peaks 240 min
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int pushStart = (lastbar240 - tradeEntryPos240 > 4)? tradeEntryPos240+4: lastbar240;
			pushStart = Utility.getHigh(quotes240, (tradeEntryPos240-12>0)?tradeEntryPos240-12:0, pushStart).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes240, pushStart, lastbar240);
			if ((pushList != null ) && (pushList.end == lastbar240) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				double price = quotes240[lastPush.prePos].low;
				int pullback = (int)((lastPush.pullBack.high - quotes240[lastPush.prePos].low)/PIP_SIZE);

				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					//System.out.println(pushList.toString(quotes240, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_LOW_240, quotes240[lastbar240].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes240[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int pushStart = (lastbar240 - tradeEntryPos240 > 4)? tradeEntryPos240+4: lastbar240;
			pushStart = Utility.getLow(quotes240, (tradeEntryPos240-12>0)?tradeEntryPos240-12:0, pushStart).pos;
			PushList pushList = PushSetup.getUp2PushList(quotes240, pushStart, lastbar240);

			if ((pushList != null ) && (pushList.end == lastbar240) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				double price = quotes240[lastPush.prePos].high;
				int pullback = (int)((quotes240[lastPush.prePos].high - lastPush.pullBack.low)/PIP_SIZE);
				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					//System.out.println(pushList.toString(quotes240, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_HIGH_240, quotes240[lastbar240].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes240[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
				}
			}
		}
		
		
		
		
		
		
		
		
		
		/******************************************************************************
		// smaller timefram for detecting sharp pullbacks
		 * ****************************************************************************/
		if (!exitTradePlaced())
		{	
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getHigh( quotes5, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNLow(quotes5, tradeEntryPosS, lastbar5, 1);
				if (phlS != null)
				{
					double pullBackDist =  phlS.pullBack.high - quotes5[phlS.prePos].low;
		
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							//takePartialProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( trade.monitorProfitInPips > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								//takePartialProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
					}
				}
				
				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotes5, 20);
				double[] ema10S = Indicator.calculateEMA(quotes5, 10);
		
				if (( ema10S[lastbar5] > ema20S[lastbar5]) && ( ema10S[lastbar5-1] < ema20S[lastbar5-1]))
				{
					//System.out.println(data.time + " cross over detected " + quotesS[lastbarS].time);
					// just cross over;
					int lastCrossDown = Pattern.findLastMACrossDown(ema10S, ema20S, lastbar5-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbar5-1]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large rundown detected " + quotes5[lastbar5].time);
						//takePartialProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotes5, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getLow( quotes5, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNHigh(quotes5, tradeEntryPosS, lastbar5, 1);
				if (phlS != null)
				{
					double pullBackDist =  quotes5[phlS.prePos].high - phlS.pullBack.low;
					
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							//takePartialProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( trade.monitorProfitInPips > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								//takePartialProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
					}
				}

				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotes5, 20);
				double[] ema10S = Indicator.calculateEMA(quotes5, 10);

				if (( ema10S[lastbar5] < ema20S[lastbar5]) && ( ema10S[lastbar5-1] > ema20S[lastbar5-1]))
				{
					int lastCrossUp = Pattern.findLastMACrossUp(ema10S, ema20S, lastbar5-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbar5-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large runup detected " + quotes5[lastbar5].time);
						//takePartialProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}

		}
		return Constants.CONTINUE;
		
	}
	
	
	private int monitor_profit_detect_peak(QuoteData data, QuoteData[] quotes, int CHART)
	{
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int tradeEntryPos = Utility.findPosition(quotes, CHART, trade.entryTime, Constants.BACK_TO_FRONT);
			if (tradeEntryPos == Constants.NOT_FOUND)
				return Constants.CONTINUE;
			int pushStart = Utility.getHigh(quotes, tradeEntryPos-12, tradeEntryPos).pos;
			
			PushList pushList = PushSetup.getDown2PushList(quotes, pushStart, lastbar);
			if ((pushList != null ) && (pushList.end == lastbar) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				double price = quotes[lastPush.prePos].low;
				int pullback = (int)((lastPush.pullBack.high - quotes[lastPush.prePos].low)/PIP_SIZE);

				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
//					System.out.println(pushList.toString(quotes60, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_LOW_60, quotes[lastbar].time);
					tv.setHeader("Exit:");
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
					//warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
					//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
				}
				
				
				/******************************************************************************
				// move stops
				 * ****************************************************************************/
				/* temporilary disable this
				if (trade.reach2FixedStop == true)
				{	
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = lastPush.prePos+1; j < lastPush.curPos; j++)
						if ( quotes60[j+1].high > quotes15[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(lastPush.pullBack.high, Constants.ADJUST_TYPE_UP);
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
			int tradeEntryPos = Utility.findPosition(quotes, CHART, trade.entryTime, Constants.BACK_TO_FRONT);
			if (tradeEntryPos == Constants.NOT_FOUND)
				return Constants.CONTINUE;
			int pushStart = Utility.getLow(quotes, tradeEntryPos-12, tradeEntryPos).pos;

			PushList pushList = PushSetup.getUp2PushList(quotes, pushStart, lastbar);
			if ((pushList != null ) && (pushList.end == lastbar) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1];
				double price = quotes[lastPush.prePos].high;
				int pullback = (int)((quotes[lastPush.prePos].high - lastPush.pullBack.low)/PIP_SIZE);


				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					//System.out.println(pushList.toString(quotes60, PIP_SIZE));
					TradeEvent tv = new TradeEvent(TradeEvent.PEAK_HIGH_60, quotes[lastbar].time);
					tv.setHeader("Exit:");
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes[pushStart].time));
					tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
					trade.addTradeEvent(tv);
					//warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
					//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), POSITION_SIZE/2 );
				}

				/******************************************************************************
				// move stop
				 * ****************************************************************************/
				/* tempoarly disable this
				if (trade.reach2FixedStop == true)
				{	
					double stop = adjustPrice(lastPush.pullBack.low, Constants.ADJUST_TYPE_DOWN);
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
		
		return Constants.CONTINUE;

	}
}



