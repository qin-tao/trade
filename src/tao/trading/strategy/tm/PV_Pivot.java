package tao.trading.strategy.tm;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.EmailSender;
import tao.trading.Indicator;
import tao.trading.Instrument;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.PositionToMA;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.dao.ReturnToMADAO;
import tao.trading.entry.IncrementalEntryFixed;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.EClientSocket;

public class PV_Pivot extends TradeManager2 
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
	int realtime_count = 0;
	
	HashSet <String>pivots_set = new HashSet <String>();
	 
	public PV_Pivot()
	{
		super();
	}
	
	public PV_Pivot(String ib_account, EClientSocket m_client, int symbol_id, Instrument instrument, Strategy stragety, HashMap<String, Double> exchangeRate )
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


	/*****************************************************************************************************************************
	 * 
	 * 
	 * Static Methods
	 * 
	 * 
	 *****************************************************************************************************************************/
	public void process( int req_id, QuoteData data )
	{
		//if ( req_id != 500 )  //500
		  //return;
		
		/*if (req_id == getInstrument().getId_realtime())
		{
				findPivotPoints(data, Constants.DIRECTION_UP);
				findPivotPoints(data, Constants.DIRECTION_DOWN);
		}*/
		
		
		
		if (req_id == getInstrument().getId_realtime()){
			reverseEntryBreakTrendLine15(data);
		}
		//	else if ((trade != null) && !(trade.status.equals(Constants.STATUS_DETECTED)|| trade.status.equals(Constants.STATUS_OPEN)|| Constants.TRADE_MA.equals(trade.type))){
		//			manage(data );
		//	}


	}

	


	

	

	
	
	
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


    
	public Trade findPivotPoints(QuoteData data, int direction)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;
		
		int GO_BACK = (lastbar > 72)?72:lastbar;
		int PREVIOUS_BAR = 2;
		double minimum_pullback = FIXED_STOP * 1.8 * PIP_SIZE;
		
		if ( direction == Constants.DIRECTION_UP )
		{
			outerloop:for ( int i = lastbar-3; i > lastbar - GO_BACK; i--)
			{	
				// last bar should be the highest
				if (quotes[lastbar].high <= quotes[i].high)
					continue;
				
				// the last bar has to be the first one higher than this bar
				double low = 999;
				for ( int j = i + 1; j < lastbar; j++)
				{
					if ( quotes[j].high > quotes[i].high )
						continue outerloop;
					if ( quotes[j].high > quotes[lastbar].high )
						continue outerloop;
					if ( quotes[j].low < low )
						low = quotes[j].low;
				}
				
				if (( minimum_pullback > 0) && ((quotes[i].high - low ) < minimum_pullback ))
					continue outerloop;
				
				// should be plenty of bars before this i bar that is lowerer than this bar
				//if ( lastbar - i > 5)
				//	PREVIOUS_BAR = (int)((lastbar - i ) * 1.5);
				
				for ( int j = i - 1; j > i - PREVIOUS_BAR; j--)
					if ( quotes[j].high > quotes[i].high )
						continue outerloop;
				
				String pivot_found = symbol + " Pivot UP found: " + quotes[lastbar].time + " " + quotes[i].time;

				if (!pivots_set.contains(pivot_found))
				{
					System.out.println(pivot_found);
					pivots_set.add(pivot_found);

					createOpenTrade(Constants.TRADE_PV, Constants.ACTION_SELL);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = data.time;

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_HIGH,  quotes[lastbar].time);
					tv.addNameValue("LastHigh", quotes[i].time + " " + quotes[i].high); 
					trade.addTradeEvent(tv);

					return null;
				}
			}
		}
		else if ( direction == Constants.DIRECTION_DOWN )
		{
			outerloop: for ( int i = lastbar-3; i > lastbar - GO_BACK; i--)
			{	
				// last bar should be the lowest
				if (quotes[lastbar].low >= quotes[i].low)
					continue;
				
				// the last bar has to be the first one higher than this bar
				double high = 0;
				for ( int j = i + 1; j < lastbar; j++)
				{
					if ( quotes[j].low < quotes[i].low )
						continue outerloop;
					if ( quotes[j].low < quotes[lastbar].low )
						continue outerloop;
					if ( quotes[j].high > high )
						high = quotes[j].high;
				}

				if (( minimum_pullback > 0) && ((high - quotes[i].low ) < minimum_pullback ))
					continue outerloop;
				
				
				// should be plenty of bars before this i bar that is lowerer than this bar
				//if ( lastbar - i > 5)
				//	PREVIOUS_BAR = (int)((lastbar - i ) * 1.5);
				
				for ( int j = i - 1; j > i - PREVIOUS_BAR; j--)
					if ( quotes[j].low < quotes[i].low )
						continue outerloop;
				
				String pivot_found = symbol + " Pivot DOWN found: " + quotes[lastbar].time + " " + quotes[i].time;

				if (!pivots_set.contains(pivot_found))
				{
					System.out.println(pivot_found);
					pivots_set.add(pivot_found);

					createOpenTrade(Constants.TRADE_PV, Constants.ACTION_BUY);
					trade.status = Constants.STATUS_DETECTED;
					trade.detectTime = data.time;

					TradeEvent tv = new TradeEvent(TradeEvent.ENTRY_NEW_LOW,  quotes[lastbar].time);
					tv.addNameValue("LastLow", quotes[i].time + " " + quotes[i].low); 
					trade.addTradeEvent(tv);
					return null;
				}
			}
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
			checkStopTarget(data);

		if ((trade != null) && (trade.type.equals(Constants.TRADE_RM)))
		{
			//trackPullBackEntry3( data );
			if ( trade.entryMode == 0 )
				trackEntryUsingConfluenceIndicator( data);  
			else if (trade.entryMode == Constants.ENTRY_MODE_INCREMENTAL )
				trade_entry_mode(data);
			//trackPullBackEntry_new(data);
		}
	}
		
		
	public void manage( QuoteData data )
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if ( trade != null )
		{	
			if ( trade.status.equals(Constants.STATUS_OPEN))
				checkTradeExpiring_ByTime(data);
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED)))
			{
				//exit123_new9c4_123( data );  
				exit123_new9c4( data );  
			}		
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

				}
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
				
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
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
				if (( limit != null ) && (limit.orderId != 0 ) && (data.high >= limit.price ))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
					}
					AddOpenRecord(data.time, Constants.ACTION_SELL, limit.position_size, limit.price);

					limit.orderId = 0;
					limit.filled = true;
					//trade.entryPrice = limit.price;
					trade.remainingPositionSize += limit.position_size;
					if ( trade.remainingPositionSize == trade.POSITION_SIZE )
						trade.status = Constants.STATUS_FILLED;
					else
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						
					trade.setEntryTime(quotes[lastbar].time);

					// calculate stop here
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
					}

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
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
				
				cancelAllOrders();
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
					else
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
				if (( limit != null ) && ( limit.orderId != 0 ) && (data.low >= limit.price ))
				{
					warning(" limit order of " + limit.price + " filled " + ((QuoteData) quotes[lastbar]).time);
					if (trade.recordOpened == false)
					{
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
					}
					AddOpenRecord(data.time, Constants.ACTION_BUY, limit.position_size, limit.price);

					limit.orderId = 0;
					//trade.entryPrice = limit.price;
					trade.remainingPositionSize += limit.position_size;
					if ( trade.remainingPositionSize == trade.POSITION_SIZE )
						trade.status = Constants.STATUS_FILLED;
					else
						trade.status = Constants.STATUS_PARTIAL_FILLED;
					trade.setEntryTime(quotes[lastbar].time);

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
					}

					TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
					tv.addNameValue("FillPrice", new Double(trade.entryPrice).toString());
					trade.addTradeEvent(tv);
					
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

				cancelAllOrders();
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
					else
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


	
	
	
	
	
	
	
	
	
	
	public void trade_entry_mode(QuoteData data)
	{
		if ( trade.entryMode == Constants.ENTRY_MODE_INCREMENTAL )
		{	
			//IncrementalEntry ie = (IncrementalEntry)trade.entryDAO;
			
			QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
			int lastbar60 = quotes60.length - 1;
			int startPos60 = 0;
			double[] ema = Indicator.calculateEMA(quotes60, 20);
			
			IncrementalEntryFixed ie;
			if ( trade.entryDAO == null )
			{
				ie = new IncrementalEntryFixed(POSITION_SIZE/2); // DEFAULT
				trade.entryDAO = ie;
			}
			else
				ie = (IncrementalEntryFixed)trade.entryDAO;

			// entry
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
				startPos60 = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

				PushList pushList = PushSetup.getUp2PushList( quotes60, startPos60, lastbar60 );
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0) && (lastbar60 == pushList.end ))
				{	
					int pushSize = pushList.phls.length;
					PushHighLow lastPush = pushList.phls[pushSize - 1];
					
					double triggerPrice = quotes60[lastPush.prePos].high;
					if ( ie.findEntry(triggerPrice) == false )
					{
						ie.addPosition(triggerPrice);
						triggerPrice = adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP);
						System.out.println(pushList.toString(quotes60, PIP_SIZE));
						warning("incremental limit " + ie.action + " order " + ie.positionSize + " placed at " + triggerPrice + " " + quotes60[lastbar60].time );
						enterLimitPositionMulti( ie.positionSize, triggerPrice );
						trade.newLimitEntered = true;
					}
				}
				
				// unload position if it is over 
				if ( trade.remainingPositionSize > trade.POSITION_SIZE )
				{
					if (data.low < ema[lastbar60])
					{
						if ( trade.newLimitEntered == true )
						{
							createTradeTargetOrder( (trade.remainingPositionSize - trade.POSITION_SIZE), data.low );
							trade.newLimitEntered = false;
						}
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(ie.action))
			{
				int startL = Utility.findPositionByHour(quotes60, trade.getPullBackStartTime(), Constants.BACK_TO_FRONT );
				startPos60 = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;

				PushList pushList = PushSetup.getDown2PushList( quotes60, startPos60, lastbar60 );
				if ((pushList != null ) &&( pushList.phls != null ) && ( pushList.phls.length > 0) && (lastbar60 == pushList.end ))
				{	
					int pushSize = pushList.phls.length;
					PushHighLow lastPush = pushList.phls[pushSize - 1];
					
					double triggerPrice = quotes60[lastPush.prePos].low;
					if ( ie.findEntry(triggerPrice) == false )
					{
						ie.addPosition(triggerPrice);
						triggerPrice = adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN);
						warning("incremental limit " + ie.action + " order " + ie.positionSize + " placed at " + triggerPrice );
						enterLimitPositionMulti( ie.positionSize, triggerPrice );
						trade.newLimitEntered = true;
					}
				}
	
				// unload position if it is over 
				if ( trade.remainingPositionSize > trade.POSITION_SIZE )
				{
					if (data.high > ema[lastbar60])
					{
						if ( trade.newLimitEntered == true )
						{
							createTradeTargetOrder( (trade.remainingPositionSize - trade.POSITION_SIZE), data.high );
							trade.newLimitEntered = false;
						}
					}
				}
	
			
			}
		}
		
		// exit if over limit
		
		
	}
	
	
	
	public void trackEntryUsingConfluenceIndicator(QuoteData data)
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
			//warning( "PullbackSTartTime :" + trade.getPullBackStartTime());
			startL = Utility.getLow( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;
			//warning( "Pullback start position is :" + quotes60[startL].time);

			if ( lastbar240 == returnToMA.getEndPos() + 2 )
			{
				if ((quotes240[returnToMA.getEndPos() + 1].open > ema[returnToMA.getEndPos() + 1]) && BarUtil.isUpBar(quotes240[returnToMA.getEndPos() + 1]))
				{
					removeTrade();
					return;
				}
			}
			/*
			else if ( lastbar240 > returnToMA.getEndPos() + 3 )
			{
				for ( int i = returnToMA.getEndPos() +3; i < lastbar240 - 3; i++)
				{
					if ((quotes240[i].low > ema[i]) && ( quotes240[i+1].low > ema[i+1]) && ( quotes240[i+2].low > ema[i+2] ))
					{
						info(quotes240[lastbar240].time + " Two bars above, trade removed" );
						trade = null;
						return;
					}
				}
			}*/
			
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
					double triggerPrice = data.close;//lastPush.pullBack.low;

					if ( realtime_count < 3 )
					{
						removeTrade();
						return;
					}
					
					warning("Breakout base sell triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					enterMarketPosition(triggerPrice);
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
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].high, Constants.ADJUST_TYPE_UP);
						//enterMarketPosition(triggerPrice);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice );
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
				double triggerPrice = data.close;//quotes60[startL].low;

				if ( realtime_count < 3 )
				{
					removeTrade();
					return;
				}
				/*
				QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
				int lastbar = quotes.length - 1;
				if (quotes[lastbar-1].low < triggerPrice) 
				{
					warning( "Remove trade 11:" + quotes[lastbar].time);
					removeTrade();
					return;
				}*/
				
				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				enterMarketPosition(triggerPrice);
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
			//warning( "PullbackSTartTime :" + trade.getPullBackStartTime());
			startL = Utility.getHigh( quotes60, startL-4<0?0:startL-4, startL+4>lastbar60?lastbar60:startL+4).pos;
			//warning( "Pullback start position is :" + quotes60[startL].time);
			// entry at tips

			if ( lastbar240 == returnToMA.getEndPos() + 2 )
			{
				if ((quotes240[returnToMA.getEndPos() + 1].open < ema[returnToMA.getEndPos() + 1]) && BarUtil.isDownBar(quotes240[returnToMA.getEndPos() + 1]))
				{	
					removeTrade();
					return;
				}
			}
			/*
			else if ( lastbar240 > returnToMA.getEndPos() + 3 )
			{
				for ( int i = returnToMA.getEndPos() +3; i < lastbar240 - 3; i++)
				{
					if ((quotes240[i].high < ema[i]) && ( quotes240[i+1].high < ema[i+1]) && ( quotes240[i+2].high < ema[i+2] ))
					{
						info(quotes240[lastbar240].time + " Two bars below, trade removed" );
						trade = null;
						return;
					}
				}
			}*/

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
					double triggerPrice = data.close;//lastPush.pullBack.high;
					if ( realtime_count < 3 )
					{
						removeTrade();
						return;
					}
					
					warning("Breakout base buy triggered at " + triggerPrice + " " + data.time + " pullback: " + quotes60[startL].time + " @ " + quotes60[startL].low );
					enterMarketPosition(triggerPrice);
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
						double triggerPrice = adjustPrice(quotes60[lastPush.prePos].low, Constants.ADJUST_TYPE_DOWN);
						//warning("Pullback trade sell triggered on large pull back  @ " + quotes60[lastbar60].time );
						//enterMarketPosition(triggerPrice);
						//enterPartialPosition( triggerPrice, POSITION_SIZE/2);
						//warning("limit order " + POSITION_SIZE/2 + " placed at " + triggerPrice );
						//createTradeLimitOrder( POSITION_SIZE/2, triggerPrice );
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
				double triggerPrice = data.close;//quotes60[startL].high;
				if ( realtime_count < 3 )
				{
					removeTrade();
					return;
				}/*
				QuoteData[] quotes = getQuoteData(Constants.CHART_1_MIN);
				int lastbar = quotes.length - 1;
				if (quotes[lastbar-1].high > triggerPrice) 
				{
					warning( "Remove trade 22:" + quotes[lastbar].time);
					removeTrade();
					return;
				}*/

				warning("Breakout base sell triggered at " + triggerPrice + " " + data.time );
				enterMarketPosition(triggerPrice);
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
	public void trackTradeTarget(QuoteData data, int field, double currPrice)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if ( trade != null )
		{	
			if ( trade.status.equals(Constants.STATUS_OPEN))
				checkTradeExpiring_ByTime(data);
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED)))
			{
				//exit123_new9c4_123( data );  
				exit123_new9c4( data );  
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
	
	
	
	
	
	
	
	
	
	
	
	
	public void exit123_new9c4( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		
		int LOT_SIZE = POSITION_SIZE/2;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes240);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			if (( trade.getProgramTrailingStop() != 0 ) && ((trade.getProgramTrailingStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramTrailingStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.high > trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}

			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + FIXED_STOP * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					//System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;

					boolean reversequalified = true;
					//if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					/*
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}*/

					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						warning(" reverse opportunity detected");
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
			}			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			// gathering parameters
			if (trade.reach2FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					warning(" move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
				}
			}


			int wave2PtL = 0;
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
			if (( first2above < first2below ) && ( first2above > 0 ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
				if ( !trade.type.equals(Constants.TRADE_CNT))
				{
					if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
					{
						wave2PtL =  maTouches[0].touchBegin;
					}
				}
				else 
				{
					if ( maTouches.length > 1 )
					{
						if ( maTouches[1].touchBegin != 0 )
						{
							wave2PtL =  maTouches[1].touchBegin;
						}
					}
					else if ( maTouches.length > 0 ) 
					{
						if (( maTouches[0].lowEnd != 0 ) && ((  maTouches[0].lowEnd -  maTouches[0].lowBegin) >= 12))
						{
							wave2PtL =  maTouches[0].lowEnd + 1;
						}
					}
				}
			}
			
			
			int pushStart = tradeEntryPosL-1;
			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, pushStart, lastbarL).phls;
			PushHighLow phl = null;
			if ((phls != null) && (phls.length >= 1 ))
			{
				//System.out.println("on tip:" + data.time);
				phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int positive = 0;
				int above20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram > 0 )
						positive ++;
					
					if (quotesL[j].low > ema20L[j])
						above20MA++;
				}
				
				double pullback = phl.pullBack.high - quotesL[phl.prePos].low;
				double triggerPrice = quotesL[phl.prePos].low;
				int phl_width = phl.curPos - phl.prePos;
				int numOfPush = phls.length;
				
				if (!exitTradePlaced())
				{
					if ( pullback  > 2 * FIXED_STOP * PIP_SIZE)
					{
						if ( wave2PtL != 0 )
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						if ( positive > 0 )
						{	
							if ( wave2PtL != 0 )
							{	
								for ( int j = 0; j < phls.length; j++ )
								{
									System.out.println(quotesL[phls[j].prePos].time + " " + quotesL[phls[j].breakOutPos].time + " " + quotesL[phls[j].curPos].time); 									
								}
								warning(data.time + " take prift buy on MACD with pullback > 1.5");
								takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
								takeProfit( adjustPrice(triggerPrice - 30 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
							}
						}					
					}
					else
					{
						if ( positive > 0 ) 
						{
							if (( wave2PtL != 0 ) && (!takeProfit2_set()))
							{	
								double targetPrice = adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift buy on MACD with pullback < 1.5");
							}
						}

							
						if ( ( wave2PtL != 0 ) && (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
						{
							MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);

							double prevProfit = trade.entryPrice - quotesL[phl.prePos].low;
							double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
							if ( avgProfit > 0 )
							{	
								//double avgPullBack = pullback / ( lastbarL - phl.prePos);
								
								//if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
								if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back");
									takeProfit( adjustPrice(quotesL[phl.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								}
							}
							
							
							PositionToMA ptm = Pattern.countAboveMA(quotesL, ema20L, tradeEntryPosL, lastbarL);
							float numberOfbars = lastbarL-tradeEntryPosL;
							if (ptm.below < ptm.above ) 
							{
								System.out.println(data.time + " take profit on disporportional pull back2");
								takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}

							if ( lastbarL >= tradeEntryPos + 8 )
							{	
								float numAbove = 0;
								for ( int j = tradeEntryPosL+1; j <=lastbarL; j++)
								{	
									if ( quotesL[j].low > trade.entryPrice )
										numAbove += 1;
								}
							
								if ( numAbove/numberOfbars > 0.6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back 3");
									takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
							}
						}
					}
				}
			}

				
			if (!exitTradePlaced())
			{	
				// check on small time frame
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getHigh( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNLow(quotesS, tradeEntryPosS, lastbarS, 1);
				if (phlS != null)
				{
					double pullBackDist =  phlS.pullBack.high - quotesS[phlS.prePos].low;
	
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profit > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
					}
				}
				
				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotesS, 20);
				double[] ema10S = Indicator.calculateEMA(quotesS, 10);

				if (( ema10S[lastbarS] > ema20S[lastbarS]) && ( ema10S[lastbarS-1] < ema20S[lastbarS-1]))
				{
					//System.out.println(data.time + " cross over detected " + quotesS[lastbarS].time);
					// just cross over;
					int lastCrossDown = Pattern.findLastMACrossDown(ema10S, ema20S, lastbarS-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossDown != Constants.NOT_FOUND )&& (( ema10S[lastCrossDown] - ema10S[lastbarS-1]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large rundown detected " + quotesS[lastbarS].time);
						takeProfit( quotesS[lastbarS].close, trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach2FixedStop == true)
			{	
				if (phl != null)
				{
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = phl.prePos+1; j < phl.curPos; j++)
						if ( quotesL[j+1].high > quotes[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{
							System.out.println(symbol + " " + quotes[lastbar].time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							//trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			profitInRisk =  ( data.close - trade.stop )/PIP_SIZE;
			if (( trade.getProgramTrailingStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramTrailingStop() )/PIP_SIZE;

			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.low < trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}

			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && ((( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE *0.3 ))            )/*      || 
				(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE ))*/
			{
				if (( data.low <  (highestPointAfterEntry - FIXED_STOP * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					
					boolean reversequalified = true;
					//if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					/*
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}*/

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						logger.warning(symbol + " " + " reverse opportunity detected");
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
						trade.detectTime = quotes[lastbar].time;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}
					return;
				}
			}

			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.reach2FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " "  + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
				}
			}
			

			int wave2PtL = 0;
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
			if (( first2below > 0 ) && ( first2below < first2above ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
				if ( !trade.type.equals(Constants.TRADE_CNT))
				{
					if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
					{
						wave2PtL =  maTouches[0].touchBegin;
					}
				}
				else
				{
					if (( maTouches.length > 1 ) )
					{
						if ( maTouches[1].touchBegin != 0 )
						{
							wave2PtL =  maTouches[1].touchBegin;
						}
					}
					else if ( maTouches.length > 0 )
					{
						if ( ( maTouches[0].highEnd != 0 ) && ((  maTouches[0].highEnd -  maTouches[0].highBegin) >= 12))
						{
							wave2PtL =  maTouches[0].highEnd + 1;
						}
					}
				}
			}

			if ( wave2PtL != 0 )
			{
				//System.out.println("first touch 20MA point:" + quotesL[wave2PtL].time);
			}
			
			int pushStart = tradeEntryPosL-1;
			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, pushStart, lastbarL).phls;
			PushHighLow phl = null;
			if ((phls != null) && (phls.length >=1))
			{
				phl = phls[0];
				MACD[] macd = Indicator.calculateMACD( quotesL );
				int negatives = 0;
				int below20MA = 0;
				for ( int j = phl.prePos; j <= phl.curPos; j++)
				{
					if ( macd[j].histogram < 0 )
						negatives ++;

					if (quotesL[j].high < ema20L[j])
						below20MA++;
				}
				
				double pullback = quotesL[phl.prePos].high - phl.pullBack.low;
				double triggerPrice = quotesL[phl.prePos].high;
				int phl_width = phl.curPos - phl.prePos;
				int numOfPush = phls.length;
				
				//System.out.println("number of push is " + numOfPush);
				if (!exitTradePlaced())
				{
					if ( pullback  > 2 * FIXED_STOP * PIP_SIZE)
					{
						if ( wave2PtL != 0 )
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						if ( negatives > 0 )
						{	
							if ( wave2PtL != 0 )
							{	
								warning(data.time + " take prift buy on MACD with pullback > 1.5");
								takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
								takeProfit( adjustPrice(triggerPrice + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
							}
						}					
					}
					else
					{
						if ( negatives > 0 ) 
						{
							if (( wave2PtL != 0 ) && (!takeProfit2_set()))
							{	
								double targetPrice = adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift sell on MACD with pullback < 1.5");
							}
						}

							
						if ( ( wave2PtL != 0 ) && (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
						{
							MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 1);
							// Exit Scenario 2:  disporportional pullback
							double prevProfit = quotesL[phl.prePos].high - trade.entryPrice;
							double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
							if ( avgProfit > 0 )
							{	
								//double avgPullBack = pullback / ( lastbarL - phl.prePos);
								//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
		
								//if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
								if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back");
									takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
							}
							
							
							PositionToMA ptm = Pattern.countAboveMA(quotesL, ema20L, tradeEntryPosL, lastbarL);
							float numberOfbars = lastbarL-tradeEntryPosL;
							if (ptm.below > ptm.above ) 
							{
								System.out.println(data.time + " take profit on disporportional pull back 2");
								takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}

						
							if ( lastbarL >= tradeEntryPos + 8 )
							{	
								float numBelow = 0;
								for ( int j = tradeEntryPosL+1; j <=lastbarL; j++)
								{	
									if ( quotesL[j].high < trade.entryPrice )
										numBelow += 1;
								}
							
								if ( numBelow/numberOfbars > 0.6 )
								{
									System.out.println(data.time + " take profit on disporportional pull back 3");
									takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
							}

						}
					}
				}
			}	


			// smaller timefram for detecting sharp pullbacks
			if (!exitTradePlaced())
			{	
				int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
				int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
				int tradeEntryPosS = Utility.getLow( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
				
				PushHighLow phlS = Pattern.findLastNHigh(quotesS, tradeEntryPosS, lastbarS, 1);
				if (phlS != null)
				{
					double pullBackDist =  quotesS[phlS.prePos].high - phlS.pullBack.low;
					
					// exit scenario1, large parfit
					if ( ( phlS.curPos - phlS.prePos) <= 48 )
					{
						if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
						{
							warning(data.time + " take profit > 200 on 2.0");
							takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profit > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
					}
				}

				// check if there has been a big run
				double[] ema20S = Indicator.calculateEMA(quotesS, 20);
				double[] ema10S = Indicator.calculateEMA(quotesS, 10);

				if (( ema10S[lastbarS] < ema20S[lastbarS]) && ( ema10S[lastbarS-1] > ema20S[lastbarS-1]))
				{
					int lastCrossUp = Pattern.findLastMACrossUp(ema10S, ema20S, lastbarS-1, 8);
					//if (lastCrossUp != Constants.NOT_FOUND )
					//System.out.println(data.time + " last cross up " + quotesS[lastCrossUp].time);
					
					if ((lastCrossUp != Constants.NOT_FOUND )&& ((ema10S[lastbarS-1] - ema10S[lastCrossUp]) > 5 * PIP_SIZE * FIXED_STOP ))
					{
						warning(data.time + " cross over after large runup detected " + quotesS[lastbarS].time);
						takeProfit( quotesS[lastbarS].close, trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach2FixedStop == true)
			{	
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						System.out.println(symbol + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}
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

	
	
	public void reverseEntryBreakTrendLine15(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_60_MIN);
		int lastbar = quotes.length - 1;

		labelPositions( quotes);
		
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			for ( int i = lastbar; i > start; i--)
			{	
				//PushList pushList = Pattern.findPast2Highs2(quotes, start, i);
				TrendLine reverse123 = new TrendLine(symbol, logger);
				Reversal123 last_a = reverse123.calculateUp123_2(quotes, start, i);
				
				if ( last_a != null )
				{
					double expected_price_last = last_a.calculateProjectedPrice(lastbar);
					double expected_price_last_1 = last_a.calculateProjectedPrice(lastbar-1);
					
					if (( quotes[lastbar].high < expected_price_last ) && ( quotes[lastbar-1].high < expected_price_last_1 ))
					{
						System.out.println("break trendline detected at " + quotes[lastbar].time);
						QuoteData[] fractuals = last_a.fractuals;
						for ( int j = 0; j < fractuals.length; j++)
							System.out.println("Fractual:" + fractuals[j].time + " " + fractuals[j].low + " pos " + fractuals[j].pos);

						warning("Pullback trade sell triggered at " + quotes[lastbar].close + " " + data.time);
						enterMarketPosition(quotes[lastbar].close);
						trade.entryTime = quotes[lastbar].time;
					}
					
					return;
				}
				
			}
		}
	}


	void labelPositions(QuoteData[] quotes) {
		for (int i = 0; i < quotes.length; i++)
			quotes[i].pos = i;
	}

}


