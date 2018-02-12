package tao.trading.strategy;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.EmailSender;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.PositionToMA;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.dao.MABreakOut;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

public class SF210TradeManager extends TradeManager2
{
	SF210 mainProgram;
	boolean CNT = false;
    String TIME_ZONE;
	boolean oneMinutChart = false;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	int DEFAULT_PROFIT_TARGET = 400;
	QuoteData currQuoteData;
//	int firstRealTimeDataPosL = 0;
	double lastTick_bid, lastTick_ask, lastTick_last;
	int lastTick;
	long firstRealTime = 0;
	int bigChartState;
	String INPUTED_ACTION = null;
	double INPUTED_ENTRY_PRICE = 0;
	
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
	boolean average_up = false;

	public SF210TradeManager()
	{
		super();
	}

	public SF210TradeManager(String account, EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super(account, client, contract, symbolIndex, logger);
	}


	// /////////////////////////////////////////////////////////
	//
	// Check Stop
	//
	// /////////////////////////////////////////////////////////
	public void checkOrderFilled(int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		//QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if (orderId == trade.stopId)
		{
			warning("order " + orderId + " stopped out ");
			trade.stopId = 0;

			cancelTargets();
			
			//processAfterHitStopLogic_c();  no reverse for 140
			removeTrade();

		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning(" limit order: " + orderId + " " + filled + " filled");
			trade.limitId1 = 0;

			//CreateTradeRecord(trade.type, trade.action);
			//AddOpenRecord(quotes[lastbar].time, trade.action, trade.limitPos1, trade.limitPrice1);

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize += trade.limitPos1; //+= filled;
			trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			trade.stop = trade.limit1Stop;
			//String oca = new Long(new Date().getTime()).toString();
			String oca = null; // do not use oca so we can keep the target order
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);
				warning(" stop order BUY " + trade.stopId + " placed " + trade.stop + " " + trade.remainingPositionSize);

			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);
				warning(" stop order SELL " + trade.stopId + " placed " + trade.stop + " " + trade.remainingPositionSize);
			}
			
			warning("place target order: targetPrice " + trade.targetPrice);
			if ( trade.targetPrice != 0 )
			{	
				trade.targetPos = trade.POSITION_SIZE;
				trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);
				warning(" target order " + (Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY) + " " + trade.targetId + " " +  trade.targetPrice + " " + trade.targetPos + " placed ");
			}
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_FILLED;
		
		}
		else if ((orderId == trade.limitId2) && ( trade.limitPos2Filled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("limit order: " + orderId + " " + filled + " filled");
			trade.limitId2 = 0;

			//CreateTradeRecord(trade.type, trade.action);
			//AddOpenRecord(quotes[lastbar].time, trade.action, trade.limitPos1, trade.limitPrice1);

			trade.limitPos2Filled = trade.limitPos2;
			trade.entryPrice = trade.limitPrice2;
			trade.remainingPositionSize += trade.limitPos2; //+= filled;
			trade.setEntryTime(quotes[lastbar].time);

			cancelStop();
			cancelTargets();
			// calculate stop here
			trade.stop = trade.limit2Stop;
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
			trade.status = Constants.STATUS_FILLED;
		
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

				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);

				if (trade.targetId != 0)
				{
					cancelOrder(trade.targetId);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize, null);
				}
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
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				
				if (trade.targetId != 0)
				{
					cancelOrder(trade.targetId);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY.equals(trade.action)?Constants.ACTION_SELL:Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize, null);
				}
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


	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.high >= trade.limitPrice1) && (trade.limitPos1Filled == 0 ))
			{
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
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
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
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
				logger.warning(symbol + " " + CHART + " stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
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

				boolean reversequalified = true;
				if (mainProgram.isNoReverse(symbol ))
					reversequalified = false;
					
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
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
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
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice1 + " filled " + ((QuoteData) quotes[lastbar]).time);
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
				logger.warning(symbol + " " + CHART + " stop market order of " + trade.stopMarketStopPrice + " filled " + ((QuoteData) quotes[lastbar]).time);
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);

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
		if (mainProgram.isNoReverse(symbol ))
		{
			warning("no reverse");
			return;
		}
		
		
		BigTrend bt = determineBigTrend( quotesL);
	//	BigTrend bt = determineBigTrend2( quotes240);
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
				logger.warning(symbol + " " + CHART + " close trade with small tip");
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
				logger.warning(symbol + " " + CHART + " close trade with small tip");
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
	public Trade checkPullBacktoBigMoves(QuoteData data)
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;

		Push lastBigPush = null;
		if (( lastBigPush = Utility.findLargestConsectiveUpBars(quotes240, 0, lastbar240) ) != null )
		{
			
		}
		
		if (( lastBigPush = Utility.findLargePushDownBars(quotes240, 0, lastbar240) ) != null )
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
			int pushStart = Utility.findPositionByHour(quotes60, quotes240[lastBigPush.pushStart].time, 1 );
			pushStart = Utility.getHigh( quotes60, pushStart-4, pushStart+4).pos;
			
			logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " push start is at " + " " +  quotes60[pushStart].time);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.setFirstBreakOutStartTime(quotes60[pushStart].time);
			return trade;
			
		}
		
		return null;
	}


	
	

	public Trade checkPrice_daily(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);

		if (Constants.ACTION_SELL.equals(INPUTED_ACTION))
		{
			if ( data.low < INPUTED_ENTRY_PRICE )
			{	
				return enterSellTrade( INPUTED_ENTRY_PRICE, quotesL[lastbarL].time, 0);
			}
		}	
		else if (Constants.ACTION_BUY.equals(INPUTED_ACTION))
		{
			if ( data.high > INPUTED_ENTRY_PRICE )
			{	
				return enterSellTrade( INPUTED_ENTRY_PRICE, quotesL[lastbarL].time, 0);
			}
		}
		
		return null;
	}

	
	
	
	// assumign this will be called after 1am
	public void checkPullBackEntry(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;
		
		//int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
		//int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.triggerPrice > 0 )
			{
				/*
				if ( data.high > trade.triggerPrice ) 
				{
					info( "data high >" +  trade.triggerPrice );
					int downPos15 = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 1);
					PushHighLow phl = Pattern.findLastNHigh(quotes, downPos15, lastbar,1);
					if ( phl != null ) 
					{
						warning("Past1High hit at " + data.time + " start " + quotes[downPos15].time);
						if (MODE == Constants.REAL_MODE)
							enterLimitPosition1(quotes[phl.prePos].high, trade.positionSize); 
						else
						    enterMarketPosition(quotes[phl.prePos].high);
						return;
					}
				}*/
				if ( data.high + 2*PIP_SIZE > trade.triggerPrice ) 
				{
					enterLimitPosition1(trade.triggerPrice, trade.POSITION_SIZE);
					return;
				}

			}
			else
			{	
				//int startL = Utility.findPositionByHour( quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT);
				int startL = Utility.findPositionByHour( quotesL, trade.pullBackStartTime, Constants.BACK_TO_FRONT);
				startL = Utility.getLow( quotesL, startL-4, (startL+4>lastbarL)?lastbarL:startL+4).pos;
				//System.out.println("start =" + )
				
				PushList pushList = PushSetup.getUp2PushList(quotesL, startL, lastbarL);
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
				{	
					//System.out.println(pushList.toString(quotesL, PIP_SIZE));
					int pushSize = pushList.phls.length;
					PushHighLow[] phls = pushList.phls;
					lastPush = pushList.phls[pushSize - 1];
					if ( pushSize > 1 )
						prevPush = pushList.phls[pushSize - 2];
					lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;
					double triggerPrice = quotesL[lastPush.prePos].high;
	
					if ( lastbarL == pushList.end )
					{	
						if ( pushSize >= 2 )
						{
							if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
							{
								warning("2 sell triggered " + triggerPrice + quotesL[lastbarL].time);
								enterMarketPosition(triggerPrice);
								return;
								//enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
							}
	
							if ( prevPush.breakOutSize <= 8 * PIP_SIZE )
							{
								warning("2.1 sell triggered " + triggerPrice + quotesL[lastbarL].time);
								enterMarketPosition(triggerPrice);
								return;
								//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
							}
						}
						
						if ( pushSize >=3 )
						{
							double prevBreakOutsize = phls[pushSize-2].breakOutSize;
							
							if ( prevBreakOutsize < 5 * PIP_SIZE )
							{
								warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
								enterMarketPosition(triggerPrice);
								return;
								//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
							}
						}
						
						
						if ( pushSize >=4 )
						{
							warning("4 sell triggered " + triggerPrice + quotesL[lastbarL].time);
							enterMarketPosition(triggerPrice);
							return;
							//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
						}
						
					}
					else if ( lastbarL == ( pushList.end - 1))
					{
						
					}
					
					
					if ( data.low < lastPush.pullBack.low )
					{
						warning("Pullback trade sell triggered at below pullback " + data.time + " @ " + lastPush.pullBack.low + " pullbackStart:" + quotesL[startL].time );
						triggerPrice = lastPush.pullBack.low;
						enterMarketPosition(triggerPrice);
						return;
						//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
				}
	
				
				if ( data.low < quotesL[startL].low )
				{
					QuoteData afterHigh = Utility.getHigh( quotesL, startL+1, lastbarL);
					if (( afterHigh != null ) && (( afterHigh.high - quotesL[startL].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
					{
						removeTrade();
						return;
					}
	
					double triggerPrice = quotesL[startL].low;
					warning("Breakout base sell triggered at " + data.time + " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].low );
					enterMarketPosition(triggerPrice);
					return;
					//return enterSellTrade( triggerPrice, quotesL[lastbarL].time, startL);
				}
				
				return;
			}
			
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.triggerPrice > 0 )
			{
				/*
				if ( data.low < trade.triggerPrice )
				{
					info( "data low <" +  trade.triggerPrice );
					int upPos15 = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 1);
					PushHighLow phl = Pattern.findLastNLow(quotes, upPos15, lastbar,1);
					if ( phl != null ) 
					{
						warning("Past1Low hit at " + data.time + " start " + quotes[upPos15].time);
						if (MODE == Constants.REAL_MODE)
							enterLimitPosition1(quotes[phl.prePos].low, trade.positionSize); 
						else
						    enterMarketPosition(quotes[phl.prePos].low);

						return;
					}
					return;
				}*/
				if ( data.low - 2*PIP_SIZE < trade.triggerPrice ) 
				{
					enterLimitPosition1(trade.triggerPrice, trade.POSITION_SIZE);
					return;
				}

			}
			else
			{	
				//int startL = Utility.findPositionByHour( quotesL, trade.getFirstBreakOutStartTime(), Constants.BACK_TO_FRONT);
				int startL = Utility.findPositionByHour( quotesL, trade.pullBackStartTime, Constants.BACK_TO_FRONT);
				startL = Utility.getHigh( quotesL, startL-4, (startL+4>lastbarL)?lastbarL:startL+4).pos;
				
				PushList pushList = PushSetup.getDown2PushList(quotesL, startL, lastbarL);
				if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
				{	
					//System.out.println(pushList.toString(quotesL, PIP_SIZE));
	
					int pushSize = pushList.phls.length;
					PushHighLow[] phls = pushList.phls;
					lastPush = pushList.phls[pushList.phls.length - 1];
					if ( pushSize > 1 )
						prevPush = pushList.phls[pushSize - 2];
					double triggerPrice = quotesL[lastPush.prePos].low;
	
					if ( lastbarL == pushList.end )
					{	
						
						if ( pushSize >= 2 ) 
						{
							if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
							{
								warning("2 buy triggered " + triggerPrice + quotesL[lastbarL].time);
								enterMarketPosition(triggerPrice);
								return;
								//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
							}
	
							if ( prevPush.breakOutSize <= 8 * PIP_SIZE )
							{
								warning("2.1 buy triggered " + triggerPrice + quotesL[lastbarL].time);
								enterMarketPosition(triggerPrice);
								return;
								//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
							}
						}
						
						if ( pushSize >= 3 )
						{
							double prevBreakOutsize = phls[pushSize-2].breakOutSize;
							
							if ( prevBreakOutsize < 5 * PIP_SIZE )
							{
								warning("3 buy triggered " + triggerPrice + quotesL[lastbarL].time);
								enterMarketPosition(triggerPrice);
								return;
								//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
							}
		
						}
						
						if ( pushSize >= 4 )
						{
							warning("4 buy triggered " + triggerPrice + quotesL[lastbarL].time);
							enterMarketPosition(triggerPrice);
							return;
							//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
						}
					}
					else if ( lastbarL == ( pushList.end - 1))
					{
						
					}
	
					
					if ( data.high > lastPush.pullBack.high )
					{
						triggerPrice = lastPush.pullBack.high;
						warning("Pullback trade buy triggered at above pullback " + data.time + " @ " + lastPush.pullBack.high + " pullbackStart:" + quotesL[startL].time);
						enterMarketPosition(triggerPrice);
						return;
						//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
					}
				}
					
				
				if ( data.high > quotesL[startL].high )
				{
					QuoteData afterLow = Utility.getLow( quotesL, startL+1, lastbarL);
					if (( afterLow != null ) && (( quotesL[startL].high - afterLow.low) > 2.5 * FIXED_STOP * PIP_SIZE ))
					{
						removeTrade();
						return;
					}
	
					double triggerPrice = quotesL[startL].high;
					System.out.println("Breakout base buy triggered at " + data.time +  " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].high );
					enterMarketPosition(triggerPrice);
					return;
					//return enterBuyTrade( triggerPrice, quotesL[lastbarL].time, startL);
				}
			}
		}

		return;
	}

	
	
	public Trade checkPullBack20_simple(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);

		if ((( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			if (!Constants.ACTION_SELL.equals(INPUTED_ACTION))
				return null;
			
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			int startL = downPos;
			while (( quotesL[startL].high < ema20L[startL]) && (startL > 0 ))
				startL--;

			int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 2);
			if (prevConsectiveUpPos == Constants.NOT_FOUND)
				prevConsectiveUpPos = 0;

			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 2);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
	//		int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
	//		int pullBackStart = Utility.getLow( quotesL, pushStartL, downPos).pos;
	//		if ( pullBackStart == lastbarL)
	//			pullBackStart = Utility.getLow( quotesL, pushStartL, lastbarL-1).pos;
			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			
			
			if  (findTradeHistory( Constants.ACTION_SELL, quotesL[prevConsectiveUpPos].time) != null) 
				return null;

			logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " pullback start is at " + " " +  quotesL[lastbarL].time + " pullbackStart " + quotesL[pullBackStart].time);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			
			trade.setFirstBreakOutTime(quotesL[pullBackStart].time);
			trade.setFirstBreakOutStartTime(quotesL[prevConsectiveUpPos].time);
			//trade.setTouch20MATime(quotesL[touch20MA].time);
			//trade.prevDownStart = prevDownPos;

			trade.detectTime = quotesL[lastbarL].time;
			//trade.detectTime = quotesL[lastbarL].time;
			//trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			//trade.pullBackPos = pullBackStart;
	        return trade;

		}
		else if (((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			if (!Constants.ACTION_BUY.equals(INPUTED_ACTION))
				return null;

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
			//	return null;

			//if ( upPos < (lastbarL - 3 ))
				//return null;
			int startL = upPos;
			while (( quotesL[startL].low > ema20L[startL]) && ( startL> 0 ))
				startL--;

			int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 2);
			if (prevConsectiveDownPos == Constants.NOT_FOUND)
				prevConsectiveDownPos = 0;
			
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 2);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			//int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			//int pullBackStart = Utility.getHigh( quotesL, startL-24, upPos).pos;

			if (findTradeHistory( Constants.ACTION_BUY, quotesL[prevConsectiveDownPos].time) != null )
				return null;

			logger.warning(symbol + " " + CHART + " " + " buy detected " + quotesL[lastbarL].time + " pullbackStart " + quotesL[pullBackStart].time);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;

			trade.setFirstBreakOutTime(quotesL[pullBackStart].time);
			trade.setFirstBreakOutStartTime(quotesL[prevConsectiveDownPos].time);
			trade.detectTime = quotesL[lastbarL].time;
			//trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			//trade.pullBackPos = pullBackStart;
			return trade;
		}

		return null;
	}

	
	
	
	
	public Trade checkPullBack20(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			if (!Constants.ACTION_SELL.equals(INPUTED_ACTION))
				return null;
			
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			
			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) )
			//	return null;
			
			
			//if ( downPos < (lastbarL - 3 ))
				//return null;
			
			int startL = downPos;
			while (( quotesL[startL].high < ema20L[startL]) && (startL > 0 ))
				startL--;

			int prevConsectiveUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 2);
			if (prevConsectiveUpPos == Constants.NOT_FOUND)
				prevConsectiveUpPos = 0;

			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 2);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
	//		int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
	//		int pullBackStart = Utility.getLow( quotesL, pushStartL, downPos).pos;
	//		if ( pullBackStart == lastbarL)
	//			pullBackStart = Utility.getLow( quotesL, pushStartL, lastbarL-1).pos;
			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;
			//int pullBackStart = Utility.getLow( quotesL, startL-24, downPos).pos;
			
			
			if  (findTradeHistory( Constants.ACTION_SELL, quotesL[prevConsectiveUpPos].time) != null) 
				return null;

			logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " pullback start is at " + " " +  quotesL[pullBackStart].time);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			
			trade.setFirstBreakOutTime(quotesL[pullBackStart].time);
			trade.setFirstBreakOutStartTime(quotesL[prevConsectiveUpPos].time);
			//trade.setTouch20MATime(quotesL[touch20MA].time);
			//trade.prevDownStart = prevDownPos;

			trade.detectTime = quotesL[lastbarL].time;
			//trade.detectTime = quotesL[lastbarL].time;
			//trade.pushStartL = pushStartL;
			//trade.pullBackStartL = pullBackStart; 
			//trade.pullBackPos = pullBackStart;
	        return trade;

		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			if (!Constants.ACTION_BUY.equals(INPUTED_ACTION))
				return null;

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
			//	return null;

			//if ( upPos < (lastbarL - 3 ))
				//return null;
			int startL = upPos;
			while (( quotesL[startL].low > ema20L[startL]) && ( startL> 0 ))
				startL--;

			int prevConsectiveDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 2);
			if (prevConsectiveDownPos == Constants.NOT_FOUND)
				prevConsectiveDownPos = 0;
			
			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 2);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			//int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;
			//int pullBackStart = Utility.getHigh( quotesL, startL-24, upPos).pos;

			if (findTradeHistory( Constants.ACTION_BUY, quotesL[prevConsectiveDownPos].time) != null )
				return null;

			//logger.warning(symbol + " " + CHART + " " + " buy detected " + data.time + " last up open is at " + ((QuoteData)quotesL[upPos]).time);
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;

			trade.setFirstBreakOutTime(quotesL[pullBackStart].time);
			trade.setFirstBreakOutStartTime(quotesL[prevConsectiveDownPos].time);
			trade.detectTime = quotesL[lastbarL].time;
			//trade.pushStartL = pushStartL;
			//trade.pullBackStartL = pullBackStart; 
			//trade.pullBackPos = pullBackStart;
			return trade;
		}

		return null;
	}

	
	
	
	public Trade checkPullBack2050(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 1);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 1);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotesL[prevDownPos-1].low < quotesL[prevDownPos].low )
				prevDownPos--;

			
			
					
			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;
			
			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].low <= ema20L[i])
				{
					touch20MA = i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
		
			QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED))
				{
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
					if ( quotesL[i].high > firstBreakOut.high )
					{
						fine("first breakout high missed at " + quotesL[i].time);
						return null;
					}
				
				
				// run trend analyser
				BigTrend bt = determineBigTrend_v3( quotesL);
				if (!bt.direction.equals(Constants.DIRT_DOWN))
					return null;
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevDownStart = prevDownPos;
//				trade.lastLargeMoveDirection = lastLargeMoveDirection;
//				trade.lastLargeMoveEnd = lastLargeMoveEnd;
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;

				warning("break UP detected at " + data.time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			//debug("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			//prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			//int prevUpPos1 = prevUpPos;  //
			//int prevDownPosBeforeUp = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevUpPos1, 2); //
			//if ( prevDownPosBeforeUp > prevUpPos1-48 )//
			//	return null;//
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
			{
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
					break;
			}
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].high >= ema20L[i])
				{
					touch20MA=i;
					fine( "touch20MA is" + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;


			QuoteData firstBreakOut = Utility.getLow( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED))
				{
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
					if ( quotesL[i].low < firstBreakOut.low )
					{
						fine("first breakout low missed at " + quotesL[i].time);
						return null;
					}


				BigTrend bt = determineBigTrend_v3( quotesL);
				
				
				if (!bt.direction.equals(Constants.DIRT_UP))
					return null;
				
				
				
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;

				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevUpStart = prevUpPos;
			//	trade.lastLargeMoveDirection = lastLargeMoveDirection;
			//	trade.lastLargeMoveEnd = lastLargeMoveEnd;

				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}

	
	
	
	public Trade checkBreakOut4a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			while ( quotesL[prevDownPos-1].low < quotesL[prevDownPos].low )
				prevDownPos--;

			
			
					
			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;
			
			// now it is the first up
			
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].low <= ema20L[i])
				{
					touch20MA = i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;

			QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED))
				{
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
					if ( quotesL[i].high > firstBreakOut.high )
					{
						fine("first breakout high missed at " + quotesL[i].time);
						return null;
					}
				
				
				// run trend analyser
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;

				if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) )
				    return null;
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevDownStart = prevDownPos;
//				trade.lastLargeMoveDirection = lastLargeMoveDirection;
//				trade.lastLargeMoveEnd = lastLargeMoveEnd;
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;

				warning("break UP detected at " + data.time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			//debug("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			//prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			//int prevUpPos1 = prevUpPos;  //
			//int prevDownPosBeforeUp = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevUpPos1, 2); //
			//if ( prevDownPosBeforeUp > prevUpPos1-48 )//
			//	return null;//
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
			{
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
					break;
			}
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].high >= ema20L[i])
				{
					touch20MA=i;
					fine( "touch20MA is" + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;


			QuoteData firstBreakOut = Utility.getLow( quotesL, start, touch20MA-1);
			if ( firstBreakOut != null )
			{
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED))
				{
					removeTrade();
				}

				for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
					if ( quotesL[i].low < firstBreakOut.low )
					{
						fine("first breakout low missed at " + quotesL[i].time);
						return null;
					}


				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				
				if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				    return null;
				
				
				
				
				
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;

				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevUpStart = prevUpPos;
			//	trade.lastLargeMoveDirection = lastLargeMoveDirection;
			//	trade.lastLargeMoveEnd = lastLargeMoveEnd;

				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}

	

	public Trade checkLargeTimeFrameMomuntumEvent(QuoteData data, double price )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_240);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
	
		if (( quotesL[lastbarL-1].high < quotesL[lastbarL-3].high ) && ( quotesL[lastbarL-2].high < quotesL[lastbarL-3].high ))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			// lastbarL-3 should be the highest in the last 10 bars
			int highestInPast10 = Utility.getHigh(quotesL, lastbarL-10, lastbarL).pos;
			if ( highestInPast10 != lastbarL-3 )
				return null;

//			warning(" highestBar is " + quotesL[highestInPast10].time);
			
			// highestInPast10 is breaking HH/LL
			int lastConsectiveAbove = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, lastbarL, 10);			
			int lastConsectiveBelow = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 10);			
			if (lastConsectiveAbove > lastConsectiveBelow )
				return null;
			int prevAbove = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastConsectiveBelow,  1);
			if ( prevAbove == Constants.NOT_FOUND )
				return null;
			int prevHighPoint = Utility.getHigh( quotesL, prevAbove, lastConsectiveBelow).pos;
			if ( prevHighPoint == Constants.NOT_FOUND )
				return null;
			PushList pushList = PushSetup.getDown2PushList(quotesL, prevHighPoint, highestInPast10);
			if ((pushList == null ) || ( pushList.phls == null ) || ( pushList.phls.length <= 0))
				return null;
			else
			{
				PushHighLow lastPush = pushList.phls[pushList.phls.length - 1];
				if ( quotesL[highestInPast10].high < lastPush.pullBack.high )
					return null;
			}
			
			// Look for moentume
			int pushStart = Utility.getLow( quotesL, lastConsectiveBelow -10, highestInPast10).pos;
			
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[pushStart].time) != null )
				return null;
	
			System.out.println(symbol + " pullback buy detected at " + quotesL[lastbarL].time + " start:" + quotesL[pushStart].time);

			
			trade = new Trade(symbol);
			trade.type = Constants.TRADE_PBK;
			trade.action = Constants.ACTION_BUY;
			trade.POSITION_SIZE = POSITION_SIZE;
			//t.status = Constants.STATUS_OPEN;

			trade.status = Constants.STATUS_DETECTED;
			trade.setFirstBreakOutStartTime(quotesL[pushStart].time);
			trade.setFirstBreakOutTime(quotesL[pushStart].time);
			trade.pullBackStartTime = quotesL[highestInPast10].time;
			return trade;
		}
		else if (( quotesL[lastbarL-1].low > quotesL[lastbarL-3].low ) && ( quotesL[lastbarL-2].low > quotesL[lastbarL-3].low ))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			// lastbarL-3 should be the highest in the last 10 bars
			int lowestestInPast10 = Utility.getLow(quotesL, lastbarL-10, lastbarL).pos;
			if ( lowestestInPast10 != lastbarL-3 )
				return null;

//			warning(" highestBar is " + quotesL[highestInPast10].time);
			
			// highestInPast10 is breaking HH/LL
			int lastConsectiveAbove = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, lastbarL, 10);			
			int lastConsectiveBelow = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 10);			
			if (lastConsectiveBelow > lastConsectiveAbove )
				return null;
			int prevBelow = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastConsectiveAbove,  1);
			if ( prevBelow == Constants.NOT_FOUND )
				return null;
			int prevLowPoint = Utility.getLow( quotesL, prevBelow, lastConsectiveAbove).pos;
			if ( prevLowPoint == Constants.NOT_FOUND )
				return null;
			PushList pushList = PushSetup.getUp2PushList(quotesL, prevLowPoint, lowestestInPast10);
			if ((pushList == null ) || ( pushList.phls == null ) || ( pushList.phls.length <= 0))
				return null;
			else
			{
				PushHighLow lastPush = pushList.phls[pushList.phls.length - 1];
				if ( quotesL[lowestestInPast10].low < lastPush.pullBack.low )
					return null;
			}
			
			// Look for moentume
			int pushStart = Utility.getHigh( quotesL, lastConsectiveAbove -10, lowestestInPast10).pos;
			
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[pushStart].time) != null )
				return null;
	
			System.out.println(symbol + " pullback sell detected at " + quotesL[lastbarL].time + " start:" + quotesL[pushStart].time);

			
			trade = new Trade(symbol);
			trade.type = Constants.TRADE_PBK;
			trade.action = Constants.ACTION_SELL;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.status = Constants.STATUS_OPEN;

			trade.status = Constants.STATUS_DETECTED;
			trade.setFirstBreakOutStartTime(quotesL[pushStart].time);
			trade.setFirstBreakOutTime(quotesL[pushStart].time);
			trade.pullBackStartTime = quotesL[lowestestInPast10].time;
			return trade;
		}

		return null;
	}

	
	
	public Trade checkLargeTimeFrameEvent3(QuoteData data, double price )
	{
		/*QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
		double[] ema50_60 = Indicator.calculateEMA(quotes60, 50);
*/
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes240, ema20_240, lastbar240, 4);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes240, ema20_240, lastbar240, 4 );
	
		if ( lastUpPos > lastDownPos)
		{
			
			if ((quotes240[lastbar240].low < ema20_240[lastbar240]) && ( quotes240[lastbar240 - 1].low > ema20_240[lastbar240 - 1]))
			{
				//BigTrend btt = calculateTrend( quotes240);
				//if (!Constants.DIRT_UP.equals(btt.direction))
				//	return null;;
				
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
					return null;
				
				
				MABreakOutList mol = MABreakOutAndTouch.findMABreakOutsUp(quotes240, ema20_240,-1);
				if ( mol != null ) 
				{
					//System.out.print(mol.toString(quotes240));
					//if ( mol.getBreakOutTimes() > 2 )
						//return null;
					
					// Run a list of analisys
					MABreakOut lastBreakOut = mol.getLastMBBreakOut();
					Push pullBack = Utility.findLargestPullBackFromHigh(quotes240, lastBreakOut.begin, lastBreakOut.end );
					System.out.println("pullback size is " + pullBack.pullback/PIP_SIZE);
					if ( pullBack.pullback > 2 * FIXED_STOP * PIP_SIZE )
						return null;
				}
				

				int prevTouchPos = lastUpPos;
				while (( quotes240[prevTouchPos].low > ema20_240[prevTouchPos]) && ( prevTouchPos > 0 ))
					prevTouchPos--;
				
				QuoteData prevHigh = Utility.getHigh( quotes240, prevTouchPos, lastUpPos);
				if ( findTradeHistory( Constants.ACTION_BUY, quotes240[lastbar240].time) != null )
					return null;

				System.out.println(symbol + " pullback buy detected at " + quotes240[lastbar240].time + " start:" + prevHigh.time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.setFirstBreakOutStartTime(quotes240[lastbar240].time);
				trade.setFirstBreakOutTime(prevHigh.time);
				return trade;

			}
		}
		else if ( lastDownPos > lastUpPos)
		{
			if ((quotes240[lastbar240].high > ema20_240[lastbar240]) && ( quotes240[lastbar240 - 1].high < ema20_240[lastbar240 - 1]))
			{
				//BigTrend btt = calculateTrend( quotes240);
				//if (!Constants.DIRT_DOWN.equals(btt.direction))
			//		return null;;
				
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
					return null;

				MABreakOutList mol = MABreakOutAndTouch.findMABreakOutsDown(quotes240, ema20_240, -1);
				if ( mol != null )
				{	
					//System.out.print(mol.toString(quotes240));
					//if ( mol.getBreakOutTimes() > 2 )
						//return null;
					MABreakOut lastBreakOut = mol.getLastMBBreakOut();
					Push pullBack = Utility.findLargestPullBackFromLow(quotes240, lastBreakOut.begin, lastBreakOut.end );
					System.out.println("pullback size is " + pullBack.pullback/PIP_SIZE);
					if ( pullBack.pullback > 2 * FIXED_STOP * PIP_SIZE )
						return null;
				}

				int prevTouchPos = lastDownPos;
				while (( quotes240[prevTouchPos].high < ema20_240[prevTouchPos]) && ( prevTouchPos > 0 ))
					prevTouchPos--;
				
				QuoteData prevLow = Utility.getLow( quotes240, prevTouchPos, lastDownPos);
				if ( findTradeHistory( Constants.ACTION_SELL, quotes240[lastbar240].time) != null )
					return null;

				System.out.println(symbol + " pullback sell detected at " + quotes240[lastbar240].time  + " start:" + prevLow.time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.setFirstBreakOutStartTime(quotes240[lastbar240].time);
				trade.setFirstBreakOutTime(prevLow.time);

				return trade;

			}
		}

		return null;
	}

	
	
	public Trade checkLargeTimeFramePullBack(QuoteData data, double price )
	{
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
	//	int lastbar240 = quotes240.length - 1;
	//	double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);

		
		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes240, ema20_240, lastbar240, 5);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes240, ema20_240, lastbar240, 5);
	
		if ( lastUpPos > lastDownPos)
		{
			if ((lastUpPos == (lastbar240 - 1)) &&  (quotes240[lastbar240].low < ema20_240[lastbar240]) && ( quotes240[lastbar240 - 1].low > ema20_240[lastbar240 - 1]))
			{
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_UP.equals(btt.direction))
					return null;;
				
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
					return null;

				int prevTouchPos = lastUpPos - 5;
				while ( quotes240[prevTouchPos].low > ema20_240[prevTouchPos])
					prevTouchPos--;
				
				QuoteData prevHigh = Utility.getHigh( quotes240, prevTouchPos, lastUpPos);
				
				int pushStart = Utility.findPositionByHour(quotes60, prevHigh.time, 1 );
				QuoteData prevHigh60 = Utility.getHigh( quotes60, pushStart-4, pushStart+4);

				if ( findTradeHistory( Constants.ACTION_BUY, prevHigh60.time) != null )
					return null;

				System.out.println(symbol + " pullback buy detected at " + quotes240[lastbar240].time + " start:" + prevHigh.time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.setFirstBreakOutStartTime(prevHigh60.time);

				return trade;

			}
		}
		else if ( lastDownPos > lastUpPos)
		{
			if ((lastDownPos == (lastbar240 - 1)) &&(quotes240[lastbar240].high > ema20_240[lastbar240]) && ( quotes240[lastbar240 - 1].high < ema20_240[lastbar240 - 1]))
			{
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_DOWN.equals(btt.direction))
					return null;;
				
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
					return null;

				int prevTouchPos = lastDownPos - 5;
				while ( quotes240[prevTouchPos].high < ema20_240[prevTouchPos])
					prevTouchPos--;
				
				QuoteData prevLow = Utility.getLow( quotes240, prevTouchPos, lastDownPos);

				int pushStart = Utility.findPositionByHour(quotes60, prevLow.time, 1 );
				QuoteData prevLow60 = Utility.getLow( quotes60, pushStart-4, pushStart+4);

				
				if ( findTradeHistory( Constants.ACTION_SELL, prevLow60.time) != null )
					return null;

				System.out.println(symbol + " pullback sell detected at " + quotes240[lastbar240].time  + " start:" + prevLow.time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.setFirstBreakOutStartTime(prevLow60.time);

				return trade;

			}
		}

		return null;
	}

	
	
	
	
	public Trade checkLargeTimeFrameEvent2(QuoteData data, double price )
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		double[] ema20_60 = Indicator.calculateEMA(quotes60, 20);
		double[] ema50_60 = Indicator.calculateEMA(quotes60, 50);
		//double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		int lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotes60, ema50_60, lastbar60, 5);
		int lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotes60, ema50_60, lastbar60, 5);
	
		if ( lastUpPos > lastDownPos)
		{
			if ((quotes60[lastbar60].low < ema20_60[lastbar60]) && ( quotes60[lastbar60 - 1].low > ema20_60[lastbar60 - 1]))
			{
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_UP.equals(btt.direction))
					return null;;
				
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
					return null;

				int prevTouchPos = lastUpPos - 5;
				while ( quotes60[prevTouchPos].low > ema20_60[prevTouchPos])
					prevTouchPos--;
				
				QuoteData prevHigh = Utility.getHigh( quotes60, prevTouchPos, lastUpPos);
				if ( findTradeHistory( Constants.ACTION_BUY, prevHigh.time) != null )
					return null;

				System.out.println(symbol + " pullback buy detected at " + quotes60[lastbar60].time + " start:" + prevHigh.time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				trade.setFirstBreakOutStartTime(prevHigh.time);

				return trade;

			}
		}
		else if ( lastDownPos > lastUpPos)
		{
			if ((quotes60[lastbar60].high > ema20_60[lastbar60]) && ( quotes60[lastbar60 - 1].high < ema20_60[lastbar60 - 1]))
			{
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_DOWN.equals(btt.direction))
					return null;;
				
				if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
					return null;

				int prevTouchPos = lastDownPos - 5;
				while ( quotes60[prevTouchPos].high < ema20_60[prevTouchPos])
					prevTouchPos--;
				
				QuoteData prevLow = Utility.getLow( quotes60, prevTouchPos, lastDownPos);
				if ( findTradeHistory( Constants.ACTION_SELL, prevLow.time) != null )
					return null;

				System.out.println(symbol + " pullback sell detected at " + quotes60[lastbar60].time  + " start:" + prevLow.time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;
				trade.setFirstBreakOutStartTime(prevLow.time);

				return trade;

			}
		}

		return null;
	}

	
	
	
	
	
	
	public Trade checkLargeTimeFrameEvent(QuoteData data, double price )
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		//double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		if (data.time.equals("20120618  00:00:00"))
			System.out.println("here");

		
		
		BigTrend btt = calculateTrend( quotes240);
		//if ( btt.pushList != null )
			//System.out.println("Big Trend Analysis: " + btt.pushList.toString(quotes240, PIP_SIZE));
		
		if (Constants.DIRT_UP.equals(btt.direction))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			// look for first breakout
			if 	( btt.pushList != null )
			{
				if ((btt.pushList.phls != null) && (btt.pushList.phls.length > 0 ))
				{
					PushHighLow lastPush = btt.pushList.phls[btt.pushList.phls.length-1];
					if ( lastbar240 == lastPush.curPos + 2 )
					{
						System.out.println(symbol + " UP pullback detected at " + quotes240[lastbar240]);
						System.out.println("Trend Start " + quotes240[btt.start].time);
						System.out.println("Big Trend Analysis: " + btt.pushList.toString(quotes240, PIP_SIZE));

						QuoteData prevTop = Utility.getHigh( quotes60, lastbar60-8, lastbar60);
						System.out.println("PrevTop = " + prevTop.time);
						if ( findTradeHistory( Constants.ACTION_BUY, prevTop.time) != null )
							return null;

						createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
						trade.status = Constants.STATUS_DETECTED;
						trade.setFirstBreakOutTime(prevTop.time);

						return trade;

					}
				}
			}
			
		}
		else if (Constants.DIRT_DOWN.equals(btt.direction))
		{
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;

			// look for first breakout
			if 	( btt.pushList != null )
			{
				if ((btt.pushList.phls != null) && (btt.pushList.phls.length > 0 ))
				{
					PushHighLow lastPush = btt.pushList.phls[btt.pushList.phls.length-1];
					if ( lastbar240 == lastPush.curPos + 2 )
					{
						System.out.println(symbol + " DOWN pullback detected at " + quotes240[lastbar240]);
						System.out.println("Trend Start " + quotes240[btt.start].time);
						System.out.println("Big Trend Analysis: " + btt.pushList.toString(quotes240, PIP_SIZE));

						QuoteData prevDown = Utility.getLow( quotes60, lastbar60-8, lastbar60);
						System.out.println("PrevDown = " + prevDown.time);
						if ( findTradeHistory( Constants.ACTION_SELL, prevDown.time) != null )
							return null;

						createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
						trade.status = Constants.STATUS_DETECTED;
						trade.setFirstBreakOutTime(prevDown.time);

					
					}
				}
			}
			
		}
			
		return null;
		
	}

	
	
	
	
	
	private void calculateBigChartState()
	{
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		
		bigChartState = Constants.STATE_UNKNOWN;
		BigTrend bt = determineBigTrend2( quotes240);

		if ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) 
		{
			bigChartState = Constants.STATE_BREAKOUT_UP;
		}
		else if ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
		{
			bigChartState = Constants.STATE_BREAKOUT_DOWN;
		}

	}
	
	
	public Trade check240PullbackSetup(QuoteData data, double price )
	{
		calculateBigChartState();
		
		if (( bigChartState != Constants.STATE_BREAKOUT_UP ) && ( bigChartState != Constants.STATE_BREAKOUT_DOWN ))
			return null;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotes240, ema20_240, lastbar240-1, 5);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotes240, ema20_240, lastbar240-1, 5);

		
		if (( bigChartState == Constants.STATE_BREAKOUT_UP ) &&  ( upPos > downPos )
				&& (( quotes240[lastbar240].low < ema20_240[lastbar240]) || (quotes240[lastbar240-1].low < ema20_240[lastbar240-1]))) 
		{
			int startL = upPos;
			while ( quotes240[startL].low > ema20_240[startL])
				startL--;

			int pushStartL = Utility.getHigh( quotes240, startL, lastbar240).pos;

			if ( findTradeHistory( Constants.ACTION_BUY, quotes240[pushStartL].time) != null )
				return null;
			
			logger.warning(symbol + " " + CHART + " " + " pull back BUY detected " + data.time );
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotes60[lastbar60].time;

			int pushStart60 = Utility.findPositionByHour( quotes60, quotes240[pushStartL].time, Constants.FRONT_TO_BACK);
			int pushStart60_2 = Utility.findPositionByHour( quotes60, quotes240[pushStartL].time, Constants.BACK_TO_FRONT);
			trade.pushStartL = Utility.getHigh(quotes60, pushStart60, pushStart60_2).pos;

			trade.setFirstBreakOutStartTime(quotes240[pushStartL].time);
	        return trade;

		}
		else if (( bigChartState == Constants.STATE_BREAKOUT_DOWN ) &&  ( downPos > upPos )
				&& (( quotes240[lastbar240].high > ema20_240[lastbar240]) || (quotes240[lastbar240-1].high > ema20_240[lastbar240-1]))) 
		{
			
			int startL = downPos;
			while ( quotes240[startL].high < ema20_240[startL])
				startL--;

			int pushStartL = Utility.getLow( quotes240, startL, lastbar240).pos;

			if ( findTradeHistory( Constants.ACTION_SELL, quotes240[pushStartL].time) != null )
				return null;
			
			logger.warning(symbol + " " + CHART + " " + " pull back SELL detected " + data.time );
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.detectTime = quotes60[lastbar60].time;

			int pushStart60 = Utility.findPositionByHour( quotes60, quotes240[pushStartL].time, Constants.FRONT_TO_BACK);
			int pushStart60_2 = Utility.findPositionByHour( quotes60, quotes240[pushStartL].time, Constants.BACK_TO_FRONT);
			trade.pushStartL = Utility.getLow(quotes60, pushStart60, pushStart60_2).pos;

			trade.setFirstBreakOutStartTime(quotes240[pushStartL].time);
	        return trade;

		}

		return null;
	}
	

	
	public Trade check240PullbackFromBreakHighLow(QuoteData data, double price )
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		int lastbar240 = quotes240.length - 1;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);
		//double[] ema5_240 = Indicator.calculateEMA(quotes240, 5);

		
		if (( quotes240[lastbar240-1].high < quotes240[lastbar240-3].high ) && ( quotes240[lastbar240-2].high < quotes240[lastbar240-3].high ))     // lower high, up bar 
		{
			// check the lastbar240-3 is a breakout
			int breakOutPos = lastbar240-3;
			if (quotes240[breakOutPos].low <= ema20_240[breakOutPos])
				return null;
			
			// look for the push size;
			int breakOutBegin = breakOutPos;
			
			while(! ((quotes240[breakOutBegin-1].low > quotes240[breakOutBegin].low) && (quotes240[breakOutBegin-2].low > quotes240[breakOutBegin].low)))
				breakOutBegin--;
			
			if  (( quotes240[breakOutPos].high - quotes240[breakOutBegin].low ) > 2 * FIXED_STOP * PIP_SIZE )
			{
				//logger.warning(symbol + " " + CHART + " " + " pull back BUY detected " + data.time );
				System.out.println(symbol + " " + CHART + " " + " pull back BUY detected " + quotes240[lastbar240-3].time + " pushBegin:" + quotes240[breakOutBegin].time + " pushEnd:" + quotes240[breakOutPos].time + "pushbars:" + (breakOutPos-breakOutBegin + 1 ) + " avg PushSize" + (( quotes240[breakOutPos].high - quotes240[breakOutBegin].low ))/(breakOutPos-breakOutBegin + 1 ));
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				
				trade.status = Constants.STATUS_DETECTED;
				trade.detectTime = quotes240[lastbar240].time;
	
				int pushStart60 = Utility.findPositionByHour( quotes60, quotes240[breakOutPos].time, Constants.FRONT_TO_BACK);
				int pushStart60_2 = Utility.findPositionByHour( quotes60, quotes240[breakOutPos].time, Constants.BACK_TO_FRONT);
				trade.pushStartL = Utility.getHigh(quotes60, pushStart60, pushStart60_2).pos;
	
				trade.setFirstBreakOutStartTime(quotes240[breakOutPos].time);
		        return trade;
			}

		}

		return null;
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public Trade checkPullbackSetup2(QuoteData data, double price )
	{
		if (( bigChartState != Constants.STATE_BREAKOUT_UP ) && ( bigChartState != Constants.STATE_BREAKOUT_DOWN ))
			return null;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
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

		    if (previousTradeExist(Constants.TRADE_PBK, Constants.ACTION_BUY, start60))
				return null;
			
		    for ( int i = lastbar60 -1; i > start60; i--)
		    {
			    PushList pushList = Pattern.findPast1Lows1(quotes60, start60, i );
					
				if (( pushList != null )&& (pushList.phls != null) && (pushList.phls.length > 0 ) )
				{
					PushHighLow phl = pushList.phls[pushList.phls.length-1];
				    System.out.println("Start60=" + quotes60[start60].time);
				    System.out.println("Push Detect at" + quotes60[phl.curPos].time);
					
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
						logger.warning(symbol + " " + CHART + " " + " buy detected " + data.time + " pushStart:" + quotes60[start60].time  );
						createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);

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
	

	
	
	
	

	

	
	
	
	
	
	
	
	
	
	
	
	

	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public boolean checkBigTimeFramePullBackFromGoingDown(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) || (((QuoteData) quotes[lastbar]).high < ema20[lastbar]))
			return false;

		int begin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 3);
		Object[] matouches = Pattern.findMATouchUpsFromGoingDown(quotes, ema20, begin, lastbar);

		if ((matouches != null) && (matouches.length <= 1))
			return true;
		else
			return false;

	}

	public boolean checkBigTimeFramePullBackFromGoingUp(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) || (((QuoteData) quotes[lastbar]).high < ema20[lastbar]))
			return false;

		int begin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 3);
		Object[] matouches = Pattern.findMATouchDownsFromGoingUp(quotes, ema20, begin, lastbar);

		if ((matouches != null) && (matouches.length <= 1))
			return true;
		else
			return false;

	}


	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Entry
	//
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackTradeEntry(QuoteData data, double price )
	{
		if ((MODE == Constants.TEST_MODE) /*&& Constants.STATUS_PLACED.equals(trade.status)*/)
			checkStopTarget(data);

		if (trade != null)
		{
			if (trade.type.equals(Constants.TRADE_PBK))
			{
				checkPullBackEntry( data);
				//trackPullBackEntry( data, price);
				//reverseEntryBreakHigherHighLowerLow( data, price);
				//reverseEntryBreakTrendLine15(data, price);
				//reverseEntrySlimJim(data, price);
				//reverseEntryBreakOut(data, price);
			}
		}
	}

	void enterSellPosition(QuoteData data, int lastbar, int lastbarL)
	{
		logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
		trade.entryPrice = data.close;
		// trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.setEntryTime(data.time);
		// trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
		if (MODE == Constants.TEST_MODE)
			AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

		trade.stop = data.close + 2 * FIXED_STOP * PIP_SIZE;
		trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																									// Long(oca).toString());

		return;

	}

	void enterBuyPosition(QuoteData data, int lastbar, int lastbarL)
	{
		logger.warning(symbol + " " + CHART + " place market buy order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
		trade.entryPrice = data.close;
		// trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.setEntryTime(data.time);
		// trade.entryTimeL = ((QuoteData)quotesL[lastbarL]).time;
		if (MODE == Constants.TEST_MODE)
			AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

		// calculate and place stop order
		// long oca = new Date().getTime();
		// trade.oca = oca;
		trade.stop = data.close - 2 * FIXED_STOP * PIP_SIZE;
		trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																									// Long(oca).toString());
	}

	public void trackPullBackTradeEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if ((((QuoteData) quotes[lastbar]).low < ((QuoteData) quotes[lastbar - 1]).low)
					&& (((QuoteData) quotes[lastbar]).high < ((QuoteData) quotes[lastbar - 1]).high))
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData) quotes[lastbar - 1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

				if (MODE == Constants.REAL_MODE)
				{
					// trade.price = adjustPrice( trade.price,
					// Constants.ADJUST_TYPE_UP);
					// trade.orderId = placeLmtOrder(Constants.ACTION_SELL,
					// trade.price, trade.positionSize, null);
					// trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				}
				else if (MODE == Constants.TEST_MODE)
				{
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				}

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getHigh(quotes, trade.detectPos, lastbar).high;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;

			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if ((((QuoteData) quotes[lastbar]).high > ((QuoteData) quotes[lastbar - 1]).high)
					&& (((QuoteData) quotes[lastbar]).low > ((QuoteData) quotes[lastbar - 1]).low))
			{
				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = ((QuoteData) quotes[lastbar - 1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.setEntryTime(((QuoteData) quotes[lastbar]).time);

				if (MODE == Constants.REAL_MODE)
				{
					// trade.price = adjustPrice( trade.price,
					// Constants.ADJUST_TYPE_DOWN);
					// trade.orderId = placeLmtOrder(Constants.ACTION_BUY,
					// trade.price, trade.positionSize, null);
					// trade.orderPlaced = true;
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				}
				else if (MODE == Constants.TEST_MODE)
				{
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
				}

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				trade.stop = Utility.getLow(quotes, trade.detectPos, lastbar).low;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;

			}
		}
	}


	
	
	public void trackPullBackTradeSell(QuoteData data, double price)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		double entryPrice = trade.entryPrice;

		//System.out.println(data.low + " " + data.time);
		/*
		// detect large pullback after the first breakout
		int pushStartL = start-1;
		while (!(( quotesL[pushStartL].high > quotesL[pushStartL-1].high ) && ( quotesL[pushStartL].high > quotesL[pushStartL-2].high )))
			pushStartL--;
		double pullBackMark = firstBreakOut.low + 0.618 * (quotesL[pushStartL].high - firstBreakOut.low );
		double pullBackHigh = Utility.getHigh(quotesL, firstBreakOut.pos, lastbarL).high;
		//info("pullbackCalculating Startpoint is " + quotesL[pushStartL].time + " pullback 0.618Mark:" + pullBackMark + " pullbackHigh:" + pullBackHigh );
		
		if (((pullBackHigh - firstBreakOut.low) > FIXED_STOP * PIP_SIZE ) && ( pullBackHigh > pullBackMark)) 
		{
			info("highest from below 20MA > 0.618 stop size, trade removed");
			removeTrade();
			return;
		}*/

		
		// Use 15 minute chart to verify pull back is within range
		/*
		int start15 = Utility.findPositionByHour(quotes, quotesL[start].time, Constants.FRONT_TO_BACK );
		int firstTouch = Utility.findFirstPriceLow(quotes, start15, entryPrice);
		
		if ((firstTouch != Constants.NOT_FOUND))
		{
			QuoteData highAfterFirstBreakOut15 = Utility.getHigh( quotes, firstTouch+1, lastbar );
			if (( highAfterFirstBreakOut15 != null ) && ((highAfterFirstBreakOut15.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
			{
				double diverage = (highAfterFirstBreakOut15.high - entryPrice)/PIP_SIZE;
				if ( diverage > 1.5 * FIXED_STOP )
				{
					double push = (quotesL[trade.prevUpStart].high - entryPrice)/PIP_SIZE;
					//if (diverage/push > 0.39 )
					{
						info("entry sell diverage low is" + highAfterFirstBreakOut15.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
						removeTrade();   
						return;
					}
				}
			}
		}*/


		int firstBreakOutL = trade.getFirstBreakOutPos(quotesL);
		QuoteData highAfterFirstBreakOut = Utility.getHigh( quotesL, firstBreakOutL+1, lastbarL );
		if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
		{
			double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
			if ( diverage > 1.5 * FIXED_STOP )
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
		
		if ( market_order == true ) 
		{
			if (triggerPrice < entryPrice ) 
			{
				//BigTrend bt = determineBigTrend( quotesL);
				//int pushLen = bt.pushes.size();
				//int lastPushIndex = bt.pushes.size() - 1;
				
				/*
				if ((bt.pushes.elementAt(pushLen-1).duration < 48) && (bt.pushes.elementAt(pushLen-2).duration < 48))
				{
					trade = null;
					return;
				}*/

				// to see if there is a large pull back from the current push down or last push down
				/*
				if (( lastPushIndex == bt.lastTrendPushIndex ) && ( bt.pushes.elementAt(bt.lastTrendPushIndex).direction == Constants.DIRECTION_DOWN ))
				{
					// check if there is a pull back from current push
					int lastUpPushIndex = lastPushIndex - 1;
					if ( lastUpPushIndex >= 0 )
					{	
						QuoteData highStart = bt.pushes.elementAt(lastUpPushIndex).getPushHigh(quotesL);
						for ( int j = lastbarL; j > highStart.pos + 5; j--)
						{	
							PushHighLow[] phls = Pattern.findPast2Lows(quotesL, bt.pushes.elementAt(lastUpPushIndex).getPushHigh(quotesL).pos, lastbarL).phls;
							if ( phls != null )
							{
								int lastPhlsIndex = phls.length -1;
								PushHighLow lastPhls = phls[0];//phls[lastPhlsIndex];
								QuoteData lowest = Utility.getLow( quotesL, lastPhls.curPos, lastbarL);
								
								if ((( quotesL[lastPhls.prePos].low - lowest.low ) < ( 0.2 * FIXED_STOP * PIP_SIZE )) && ( entryPrice > lowest.low))
								{
									info("exhausting sell pattern, trade removed");
									//removeTrade();
									trade = null;
									return;
								}
							}
						}
					}
				}*/
				
				
				String tradeType = Constants.TRADE_EUR;
				QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
				BigTrend bt = determineBigTrend2( quotes240);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotes240));
				
				
				if (( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
				{
					tradeType = Constants.TRADE_RVS;
				}
				else if (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					tradeType = Constants.TRADE_PBK;
					
					if ( bt.support != null )
						System.out.println("Support is at " + bt.support.low);
				}

				
				trade.type = tradeType;
				trade.setEntryTime(quotes[lastbar].time);
				trade.entryPrice = entryPrice;
	
				warning("break DOWN trade entered at " + data.time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
				if ( data != null )
					warning(data.time + " " + data.high + " " + data.low );
				else
					warning("last tick:" + price);
					
	
				trade.stop = trade.triggerPrice + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
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
				
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);
	
				if (MODE == Constants.REAL_MODE)
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail(symbol + " SELL order placed", "sent from automated trading system");
				}

			}
		}
		else
		{
			if ((triggerPrice < entryPrice + 0.3 * FIXED_STOP * PIP_SIZE) && ( trade.stopMarketPlaced == false ))
			{
				trade.stopMarketStopPrice = adjustPrice(entryPrice, Constants.ADJUST_TYPE_UP);
				trade.stop = entryPrice + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
				Date time = new Date();
				trade.openOrderPlacedTimeInMill = time.getTime();
				trade.openOrderDurationInHour = 3;
				trade.openOrderExpireInMill = trade.openOrderPlacedTimeInMill + 3*60*60000L;
				time.setTime(trade.openOrderExpireInMill);
				String goodTill = OrderDateFormatter.format(time);
				enterStopMarketPosition(goodTill);

				trade.stopMarketPlaced = true;
				warning(data.time + " place stop market " + trade.action + " order, orderId:" + trade.stopMarketId + " stop triggerPrice:" + trade.stopMarketStopPrice + " good till:" + goodTill);

			}
		}
		
	}



	
	
	public void trackPullBackEntry3(QuoteData data, double price)
	{
		//QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		//int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		//double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);
		//double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);
		PushHighLow lastPush=null;
		PushHighLow prevPush=null;
		double lastPullbackSize, prevPullbackSize, lastBreakOutSize;

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);
		//String startTime = trade.getFirstBreakOutStartTime();
		int startL = trade.pullBackStartL;
		//String startTime = trade.getFirstBreakOutTime();
		
		//int start = Utility.findPositionByHour(quotesL, startTime, Constants.FRONT_TO_BACK );
		//int start2= Utility.findPositionByHour(quotesL, startTime,  Constants.BACK_TO_FRONT );
		
		//int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime,  Constants.BACK_TO_FRONT );
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			PushList pushList = PushSetup.getUp2PushList(quotesL, startL, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				System.out.println(pushList.toString(quotesL, PIP_SIZE));
				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushSize - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				lastPullbackSize = quotesL[lastPush.prePos].high - lastPush.pullBack.low;
				double triggerPrice = quotesL[lastPush.prePos].high;

				if ( lastbarL == pushList.end )
				{	
					if ( pushSize >= 2 )
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							warning("2 sell triggered " + triggerPrice + quotesL[lastbarL].time);
							enterTrade( triggerPrice, quotesL[lastbarL].time);
							return;
						}

						if ( prevPush.breakOutSize <= 8 * PIP_SIZE )
						{
							warning("2.1 sell triggered " + triggerPrice + quotesL[lastbarL].time);
							enterTrade( triggerPrice, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >=3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							warning("3 sell triggered " + triggerPrice + quotesL[lastbarL].time);
							enterTrade( triggerPrice, quotesL[lastbarL].time);
							return;
						}
					}
					
					
					if ( pushSize >=4 )
					{
						warning("4 sell triggered " + triggerPrice + quotesL[lastbarL].time);
						enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
						return;
					}
					
				}
				else if ( lastbarL == ( pushList.end - 1))
				{
					
				}
				
				
				if ( data.low < lastPush.pullBack.low )
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade sell triggered at " + data.time + " @ " + lastPush.pullBack.low );
					double entryPrice = lastPush.pullBack.low;
					trade.setEntryTime(quotesL[lastbarL].time);
					trade.entryPrice = entryPrice;
					enterMarketPosition(lastPush.pullBack.low);
					return;
				}
			}

			
			if ( data.low < quotesL[startL].low )
			{
				QuoteData afterHigh = Utility.getHigh( quotesL, startL+1, lastbarL);
				if (( afterHigh != null ) && (( afterHigh.high - quotesL[startL].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					removeTrade();
					return;
				}

				warning("Pullback trade sell triggered at " + data.time + " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].low );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].low;
				trade.entryTime = ((QuoteData) quotesL[lastbarL]).time;
				enterMarketPosition(quotesL[startL].low);
				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			PushList pushList = PushSetup.getDown2PushList(quotesL, startL, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));

				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];
				if ( pushSize > 1 )
					prevPush = pushList.phls[pushSize - 2];
				double triggerPrice = quotesL[lastPush.prePos].low;

				if ( lastbarL == pushList.end )
				{	
					
					if ( pushSize >= 2 ) 
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							warning("2 buy triggered " + triggerPrice + quotesL[lastbarL].time);
							enterTrade( triggerPrice, quotesL[lastbarL].time);
							return;
						}

						if ( prevPush.breakOutSize <= 8 * PIP_SIZE )
						{
							warning("2.1 buy triggered " + triggerPrice + quotesL[lastbarL].time);
							enterTrade( triggerPrice, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >= 3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							warning("3 buy triggered " + triggerPrice + quotesL[lastbarL].time);
							enterTrade( triggerPrice, quotesL[lastbarL].time);
							return;
						}
	
					}
					
					if ( pushSize >= 4 )
					{
						warning("4 buy triggered " + triggerPrice + quotesL[lastbarL].time);
						enterTrade( triggerPrice, quotesL[lastbarL].time);
						return;
					}
				}
				else if ( lastbarL == ( pushList.end - 1))
				{
					
				}

				
				if ( data.high > lastPush.pullBack.high )
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade buy triggered at " + data.time + " @ " + lastPush.pullBack.high );
					double entryPrice = lastPush.pullBack.high;
					trade.setEntryTime(quotesL[lastbarL].time);
					trade.entryPrice = entryPrice;
					enterMarketPosition(lastPush.pullBack.high);
					return;
				}
			}
				
			
			if ( data.high > quotesL[startL].high )
			{
				QuoteData afterLow = Utility.getLow( quotesL, startL+1, lastbarL);
				if (( afterLow != null ) && (( quotesL[startL].high - afterLow.low) > 2.5 * FIXED_STOP * PIP_SIZE ))
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

				System.out.println("Pullback trade buy triggered at " + data.time +  " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].high );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].high;
				trade.entryTime = ((QuoteData) quotesL[lastbarL]).time;
				enterMarketPosition(quotesL[startL].high);
				return;
			}
		}
	}


	
	
	
	
	
	
	
	
	public void trackPullBackEntry4(QuoteData data, double price)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		PushHighLow lastPush, prevPush;

		int startL = trade.getFirstBreakOutStartPosL(quotesL);

		//int startL = trade.getFirstBreakOutStartPosL(quotesL);
		//String startTime = trade.getFirstBreakOutStartTime();
		int startL = 0;
		String startTime = trade.getFirstBreakOutTime();
		
		int start = Utility.findPositionByHour(quotesL, startTime, Constants.FRONT_TO_BACK );
		int start2= Utility.findPositionByHour(quotesL, startTime,  Constants.BACK_TO_FRONT );
		
		//int detectPos = Utility.findPositionByHour(quotesL, trade.detectTime,  Constants.BACK_TO_FRONT );
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			// find first downbar after detection
			/*
			int firstDownbar = 0;
			for ( int i = detectPos+1; i < lastbarL; i++ )
				if ( quotesL[i].close < quotesL[lastbarL-1].open )
				{
					firstDownbar = i;
					break;
				}
			
			// if no above 20MA bar between the detection and downbar, enter the trade;
			boolean above = false;
			for ( int i = detectPos + 1; i <= lastbarL-1; i++)
			{
				if (quotesL[i].low > ema20L[i])
					above = true;
			}
				
			if (( firstDownbar != 0 ) && !above )
			{
				if ( data.low < quotesL[firstDownbar].low )
				{
					enterTrade( quotesL[firstDownbar].low, quotesL[lastbarL].time);
					return;
				}
			}
			*/
			
			MABreakOutList mol = MABreakOutAndTouch.findMABreakOutsUp(quotes240, ema20_240,-1);
			if (( mol != null ) && (mol.getNumOfBreakOuts() > 0))
			{
				System.out.print(mol.toString(quotes240));
				//if ( mol.getBreakOutTimes() > 2 )
					//return null;
				
				// Run a list of analisys
				MABreakOut lastBreakOut = mol.getLastMBBreakOut();
				Push pullBack = Utility.findLargestPullBackFromHigh(quotes240, lastBreakOut.begin, lastBreakOut.end );
				System.out.println("pullback size is " + pullBack.pullback/PIP_SIZE);
				if ( pullBack.pullback > 2 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}
			}

			
			startL = Utility.getLow( quotesL, start, start2).pos;

			PushList pushList = PushSetup.getUp2PushList(quotesL, startL, lastbarL);
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
					if ( pushSize >= 2 )
					{
						if ( lastPush.pullBackSize > 1.5 * FIXED_STOP * PIP_SIZE)
						{
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
					}
					
					if ( pushSize >=3 )
					{
						double prevBreakOutsize = phls[pushSize-2].breakOutSize;
						
						if ( prevBreakOutsize < 5 * PIP_SIZE )
						{
							enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
							return;
						}
					}
					
					
					if ( pushSize >=4 )
					{
						enterTrade( quotesL[lastPush.prePos].high, quotesL[lastbarL].time);
						return;
					}
				}
				else if ( lastbarL == ( pushList.end - 1))
				{
					
				}
				
				
				if ( data.low < lastPush.pullBack.low )
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade sell triggered at " + data.time + " @ " + lastPush.pullBack.low );
					double entryPrice = lastPush.pullBack.low;
					trade.setEntryTime(quotesL[lastbarL].time);
					trade.entryPrice = entryPrice;
					enterMarketPosition(lastPush.pullBack.low);
					return;
				}
			}

			
			if ( data.low < quotesL[startL].low )
			{
				QuoteData afterHigh = Utility.getHigh( quotesL, startL+1, lastbarL);
				if (( afterHigh != null ) && (( afterHigh.high - quotesL[startL].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					removeTrade();
					return;
				}

				warning("Pullback trade sell triggered at " + data.time + " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].low );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].low;
				trade.entryTime = ((QuoteData) quotesL[lastbarL]).time;
				enterMarketPosition(quotesL[startL].low);
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
			MABreakOutList mol = MABreakOutAndTouch.findMABreakOutsDown(quotes240, ema20_240,-1);
			if (( mol != null ) && (mol.getNumOfBreakOuts() > 0 ))
			{	
				System.out.print(mol.toString(quotes240));
				//if ( mol.getBreakOutTimes() > 2 )
					//return null;
				MABreakOut lastBreakOut = mol.getLastMBBreakOut();
				Push pullBack = Utility.findLargestPullBackFromLow(quotes240, lastBreakOut.begin, lastBreakOut.end );
				System.out.println("pullback size is " + pullBack.pullback/PIP_SIZE);
				if ( pullBack.pullback > 2 * FIXED_STOP * PIP_SIZE )
				{
					removeTrade();
					return;
				}
			}

			startL = Utility.getHigh( quotesL, start, start2).pos;
			PushList pushList = PushSetup.getDown2PushList(quotesL, startL, lastbarL);
			if ( pushList != null )
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));

				int pushSize = pushList.phls.length;
				PushHighLow[] phls = pushList.phls;
				lastPush = pushList.phls[pushList.phls.length - 1];

				if ( lastbarL == pushList.end )
				{	
					
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
				else if ( lastbarL == ( pushList.end - 1))
				{
					
				}

				
				if ( data.high > lastPush.pullBack.high )
				{
					/*
					BigTrend btt = calculateTrend( quotes240);
					if (!Constants.DIRT_DOWN.equals(btt.direction))
					{
						removeTrade();
						return;
					}*/
					warning("Pullback trade buy triggered at " + data.time + " @ " + lastPush.pullBack.high );
					double entryPrice = lastPush.pullBack.high;
					trade.setEntryTime(quotesL[lastbarL].time);
					trade.entryPrice = entryPrice;
					enterMarketPosition(lastPush.pullBack.high);
					return;
				}
			}
				
			
			if ( data.high > quotesL[startL].high )
			{
				QuoteData afterLow = Utility.getLow( quotesL, startL+1, lastbarL);
				if (( afterLow != null ) && (( quotesL[startL].high - afterLow.low) > 2.5 * FIXED_STOP * PIP_SIZE ))
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

				System.out.println("Pullback trade buy triggered at " + data.time +  " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].high );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].high;
				trade.entryTime = ((QuoteData) quotesL[lastbarL]).time;
				enterMarketPosition(quotesL[startL].high);
				return;
			}
		}
	}

	
	
	
	
	

	private void enterTrade( double entryPrice, String entryTime)
	{
		
		trade.setEntryTime(entryTime);
		trade.entryPrice = entryPrice;
		enterMarketPosition(entryPrice);
		return;
	}
	
	private Trade enterSellTrade( double entryPrice, String entryTime, int startL)
	{
		createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);

		trade.pullBackStartL = startL; 
		if (findTradeHistory(Constants.ACTION_SELL, startL) != null )
		{
			trade = null;
			return null;
		}

		trade.setEntryTime(entryTime);
		trade.entryPrice = entryPrice;
		enterMarketPosition(entryPrice);
		return trade;
	}
	
	private Trade enterBuyTrade( double entryPrice, String entryTime, int startL)
	{
		createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);

		trade.pullBackStartL = startL; 
		if (findTradeHistory(Constants.ACTION_SELL, startL) != null )
		{
			trade = null;
			return null;
		}

		trade.setEntryTime(entryTime);
		trade.entryPrice = entryPrice;
		enterMarketPosition(entryPrice);
		return trade;
	}
	
	
	
	public void trackPullBackEntry2(QuoteData data, double price)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);

		int startL = trade.getFirstBreakOutPos(quotesL);
		//int start = Utility.findPositionByHour(quotes, quotesL[startL].time, Constants.FRONT_TO_BACK );
		//int start2= Utility.findPositionByHour(quotes, quotesL[startL].time,  Constants.BACK_TO_FRONT );
		
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			for ( int i = lastbarL; i > startL; i--)
			{	
				PushList pushList = Pattern.findPast1Highs1(quotesL, startL, i);
				if (( pushList.phls != null ) && ( pushList.phls.length > 0 )) // latest
				{	
					PushHighLow[] phls = pushList.phls;
					PushHighLow lastPush = pushList.phls[pushList.phls.length - 1];

					//System.out.println(data.time + " length:" + phls.length);
					//for ( int j = 0; j < phls.length; j++)
					//{
					//	System.out.println("PushStart:" + quotesL[phls[j].pushStart].time + " PreHigh:" + quotesL[phls[j].prePos].time + " PullBack:" + phls[j].pullBack.time +
					//			" " + " CurHigh:" + quotesL[phls[j].curPos].time + " " + phls[j].pushLen + " " + phls[j].pullBackLen + " Ratio:" + phls[j].pullBackRatio );
					//}
					
					if ( data.low < lastPush.pullBack.low )
					{
						BigTrend btt = calculateTrend( quotes240);
						if (!Constants.DIRT_DOWN.equals(btt.direction))
						{
							removeTrade();
							return;
						}
						warning("Pullback trade sell triggered at " + data.time + " @ " + lastPush.pullBack.low );
						double entryPrice = lastPush.pullBack.low;
						trade.setEntryTime(quotesL[lastbarL].time);
						trade.entryPrice = entryPrice;
						enterMarketPosition(lastPush.pullBack.low);
					}
					
					return;
				}
				
			}

			if ( data.low < quotesL[startL].low )
			{
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_DOWN.equals(btt.direction))
				{
					removeTrade();
					return;
				}

				warning("Pullback trade sell triggered at " + data.time + " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].low );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].low;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				enterMarketPosition(quotesL[startL].low);
				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			for ( int i = lastbarL; i > startL; i--)
			{
				PushList pushList = Pattern.findPast1Lows1(quotesL, startL, i);
				if (( pushList.phls != null ) && ( pushList.phls.length > 0 ))
				{	
					PushHighLow[] phls = pushList.phls;
					PushHighLow lastPush = pushList.phls[pushList.phls.length - 1];

					if ( data.high > lastPush.pullBack.high )
					{
						System.out.println(data.time + " length:" + phls.length);
						for ( int j = 0; j < phls.length; j++)
						{
							//System.out.println(" PreLow:" + quotes[phls[j].prePos].time + " PullBack:" + phls[j].pullBack.time +
								//	" " + " CurLow:" + quotes[phls[j].curPos].time + " " + phls[j].pushSize + " " + phls[j].pullBackHeight + " Ratio:" + phls[j].pullBackRatio );
						}
						
						BigTrend btt = calculateTrend( quotes240);
						if (!Constants.DIRT_UP.equals(btt.direction))
						{
							removeTrade();
							return;
						}

						warning("Pullback trade buy triggered at " + data.time + " @ " + lastPush.pullBack.low );
						double entryPrice = lastPush.pullBack.high;
						trade.setEntryTime(quotesL[lastbarL].time);
						trade.entryPrice = entryPrice;
						enterMarketPosition(lastPush.pullBack.high);
					}
					
					return;
				}
			}
				
			if ( data.high > quotesL[startL].high )
			{
				BigTrend btt = calculateTrend( quotes240);
				if (!Constants.DIRT_UP.equals(btt.direction))
				{
					removeTrade();
					return;
				}

				System.out.println("Pullback trade buy triggered at " + data.time +  " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].high );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].high;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				enterMarketPosition(quotesL[startL].high);
				return;
			}
		}
		
	}

	
	
	
	
	
	
	
	
	
	public void trackPullBackEntry(QuoteData data, double price)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_15);
		int lastbarL = quotesL.length - 1;

		int startL = trade.getFirstBreakOutPos(quotesL);
		//int startL = trade.pushStartL;
		
		int start = Utility.findPositionByHour(quotes, quotesL[startL].time, Constants.FRONT_TO_BACK );
		int start2= Utility.findPositionByHour(quotes, quotesL[startL].time,  Constants.BACK_TO_FRONT );
		
		
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			start = Utility.getLow(quotes, start, start2).pos;

			for ( int i = lastbar; i > start; i--)
			{	
				PushList pushList = Pattern.findPast2Highs2(quotes, start, i);
				if (( pushList.phls != null ) && ( pushList.phls.length > 0 )) // latest
				{	
					
					PushHighLow[] phls = pushList.phls;
					PushHighLow lastPush = pushList.phls[pushList.phls.length - 1];
					//System.out.println(data.time + " length:" + phls.length);
					//for ( int j = 0; j < phls.length; j++)
					//{
					//	System.out.println("PushStart:" + quotesL[phls[j].pushStart].time + " PreHigh:" + quotesL[phls[j].prePos].time + " PullBack:" + phls[j].pullBack.time +
					//			" " + " CurHigh:" + quotesL[phls[j].curPos].time + " " + phls[j].pushLen + " " + phls[j].pullBackLen + " Ratio:" + phls[j].pullBackRatio );
					//}
					
					if ( data.low < lastPush.pullBack.low )
					{
						warning("Pullback trade sell triggered at " + data.time + " @ " + lastPush.pullBack.low );
						enterMarketPosition(lastPush.pullBack.low);
					}
					
					return;
				}
				
			}

			if ( data.low < quotesL[startL].low )
			{
				warning("Pullback trade sell triggered at " + data.time + " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].low );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].low;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				enterMarketPosition(quotesL[startL].low);
				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			//if ((startL != lastbarL) && ( quotesL[startL+1].low > quotesL[startL].low ))
			//	startL++;

			start = Utility.getHigh(quotes, start, start2).pos;

			for ( int i = lastbar; i > start; i--)
			{
				PushList pushList = Pattern.findPast2Lows2(quotes, start, i);
				if (( pushList.phls != null ) && ( pushList.phls.length > 0 ))
				{	
					PushHighLow[] phls = pushList.phls;
					PushHighLow lastPush = pushList.phls[pushList.phls.length - 1];

					if ( data.high > lastPush.pullBack.high )
					{
						System.out.println(data.time + " length:" + phls.length);
						for ( int j = 0; j < phls.length; j++)
						{
							//System.out.println(" PreLow:" + quotes[phls[j].prePos].time + " PullBack:" + phls[j].pullBack.time +
								//	" " + " CurLow:" + quotes[phls[j].curPos].time + " " + phls[j].pushSize + " " + phls[j].pullBackHeight + " Ratio:" + phls[j].pullBackRatio );
						}
						
						warning("Pullback trade buy triggered at " + data.time + " @ " + lastPush.pullBack.low );
						enterMarketPosition(lastPush.pullBack.high);
					}
					
					return;
				}
			}
				
			if ( data.high > quotesL[startL].high )
			{
				System.out.println("Pullback trade buy triggered at " + data.time +  " pullback: " + quotesL[startL].time + " @ " + quotesL[startL].high );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotesL[startL].high;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				enterMarketPosition(quotesL[startL].high);
				return;
			}
		}
		
	}

	
	
	public void reverseEntryBreakHigherHighLowerLow(QuoteData data, double price )
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;

		//int start60 = trade.pushStartL;
		int start60 = trade.getFirstBreakOutPos(quotes60);

		if (trade.action.equals(Constants.ACTION_BUY))
		{	
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
							if ( quotes60[j].high > phl.pullBack.high )
							{
								warning(data.time + " trade missed at " + quotes60[j].time + " pushStart" + quotes60[phl.prePos].time + " pushEnd" + quotes60[phl.curPos].time + " pullback" + phl.pullBack.time);
								removeTrade();
								return;
							}
						}
						
						double entryPrice = phl.pullBack.high;
						trade.setEntryTime(quotes60[lastbar60].time);
						trade.entryPrice = entryPrice;
						
						trade.targetPrice = adjustPrice(entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);
	
						warning("break UP trade entered at " + data.time + " start:" + quotes60[start60].time +  " breakout tip:" + entryPrice );
			
						enterMarketPosition(entryPrice);
						//enterLimitPosition1(entryPrice);
						return;
			
					}
				}
		    }
		    
			if ( data.high > quotes60[start60].high )
			{
				warning("Pullback trade sell triggered at " + data.time + " pullback: " + quotes60[start60].time + " @ " + quotes60[start60].high );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotes60[start60].high;
				trade.entryTime = quotes60[lastbar60].time;
				trade.targetPrice = adjustPrice(trade.entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
				enterMarketPosition(trade.entryPrice);
				return;
			}
		    
		}
	    else if (trade.action.equals(Constants.ACTION_SELL))
		{	
		    for ( int i = lastbar60 -1; i > start60; i--)
		    {
			    PushList pushList = Pattern.findPast1Highs1(quotes60, start60, i );
					
				if (( pushList != null )&& (pushList.phls != null) && (pushList.phls.length > 0 ) )
				{
					int pushListSize = pushList.phls.length;
					PushHighLow phl = pushList.phls[pushListSize-1];
				    //System.out.println("Start60=" + quotes60[start60].time);
				    //System.out.println("Push Detect at" + quotes60[phl.curPos].time);
					
					int pushDuration = phl.pushWidth;
					double pushSize = phl.pushSize;
	
					if ( data.low < phl.pullBack.low)
					{
						for ( int j = phl.pullBack.pos + 1; j < lastbar60; j++)
						{
							if ( quotes60[j].low < phl.pullBack.high )
							{
								warning(data.time + " trade missed at " + quotes60[j].time + " pushStart" + quotes60[phl.prePos].time + " pushEnd" + quotes60[phl.curPos].time + " pullback" + phl.pullBack.time);
								for ( int k = 0; k < pushListSize; k++)
								{
									PushHighLow phll = pushList.phls[k];
									System.out.println("pushlist " + k + " " + quotes60[phll.prePos].time + " " + quotes60[phll.curPos].time + " " + phll.pullBack.time);
								}
								
								removeTrade();
								return;
							}
						}
						
						double entryPrice = phl.pullBack.low;
						trade.setEntryTime(quotes60[lastbar60].time);
						trade.entryPrice = entryPrice;
						
						trade.targetPrice = adjustPrice(entryPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);
	
						warning("break DOWN trade entered at " + data.time + " start:" + quotes60[start60].time +  " breakout tip:" + entryPrice );
							
						enterMarketPosition(entryPrice);
						//enterLimitPosition1(entryPrice);
						return;
			
					}
				}
		    }
		    
			if ( data.low < quotes60[start60].low )
			{
				warning("Pullback trade sell triggered at " + data.time + " pullback: " + quotes60[start60].time + " @ " + quotes60[start60].low );
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = quotes60[start60].low;
				trade.entryTime = quotes60[lastbar60].time;
				trade.targetPrice = adjustPrice(trade.entryPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);
				enterMarketPosition(trade.entryPrice);
				return;
			}

		    
		}
	}
	

	
	

	
	public void reverseEntryBreakTrendLine15(QuoteData data, double price)
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5);
		int lastbarS = quotesS.length - 1;
		PushHighLow lastPush;
		
		int start = trade.pushStart;  // 15 minut
		
		//int start = Utility.findPositionByHour(quotes,trade.detectTime, Constants.FRONT_TO_BACK );
		//int start2= Utility.findPositionByHour(quotes,trade.detectTime,  Constants.BACK_TO_FRONT );
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
						trade.entryTime = quotesS[lastbarS].time;
					}
					
					return;
				}
				
			}
		}
	}


	
	
	
	

	
	
	
	
	public void reverseEntrySlimJim(QuoteData data, double limitPrice)
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		
		// assuming some limit order has already been placed

		int detectPos15 = Utility.findPositionByMinute( quotes15, trade.detectTime, Constants.BACK_TO_FRONT);

		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.limit1Placed == false )
			{
				if (( trade.limitPrice1 != 0 ) && ( trade.limitPos1 != 0 ))
				{
					double limitPrice1 = adjustPrice( limitPrice, Constants.ADJUST_TYPE_UP);
					enterLimitPosition1(trade.limitPrice1, trade.limitPos1, 0); 
					trade.limit1Placed = true;
				}
			}
			
			if ( trade.limit2Placed == false )
			{
				if (( trade.limitPrice2 != 0 ) && ( trade.limitPos2 != 0 ))
				{
					double limitPrice1 = adjustPrice( limitPrice, Constants.ADJUST_TYPE_UP);
					enterLimitPosition1(trade.limitPrice2, trade.limitPos2, 0); 
					trade.limit2Placed = true;
				}
			}

			// in case we miss the entry, we need to follow up with a reverse entry
			reversalEntry(data);
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.limit1Placed == false )
			{
				if (( trade.limitPrice1 != 0 ) && ( trade.limitPos1 != 0 ) && ( trade.limit1Placed == false ))
				{
					double limitPrice1 = adjustPrice( limitPrice, Constants.ADJUST_TYPE_DOWN);
					enterLimitPosition1(limitPrice1, trade.limitPos1, 0); 
					trade.limit1Placed = true;
				}
			}

			if ( trade.limit2Placed == false )
			{
				if (( trade.limitPrice2 != 0 ) && ( trade.limitPos2 != 0 ))
				{
					double limitPrice1 = adjustPrice( limitPrice, Constants.ADJUST_TYPE_DOWN);
					enterLimitPosition1(trade.limitPrice2, trade.limitPos2, 0); 
					trade.limit2Placed = true;
				}
			}

			// in case we miss the entry, we need to follow up with a reverse entry
			reversalEntry(data);
		}

	}

	
	
	
	public void reverseEntryBreakOut(QuoteData data, double price )
	{
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;
		
		int start60 = trade.pushStartL;
//		int detectPos60 = Utility.findPositionByMinute( quotes60, trade.detectTime, Constants.BACK_TO_FRONT);
//		if (lastbar60 <= detectPos60)
//			return;
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			QuoteData highestPoint = Utility.getHigh( quotes60, start60, lastbar60);
			
		    PushList pushList = Pattern.findPast1Lows1(quotes60, highestPoint.pos, lastbar60 );
			
			if ((pushList.phls == null) || (pushList.phls.length == 0 ))
				return;

			double avgBarSize60 = Utility.averageBarSize(quotes60);
			
			PushHighLow phl = pushList.phls[pushList.phls.length-1];
			
			int pushDuration = phl.pushWidth;
			double pushSize = phl.pushSize;

			if ((highestPoint.high - quotes60[phl.prePos].low ) > 2 * avgBarSize60)
			{
				trade.entryPrice = quotes60[phl.prePos].low;;
				trade.setEntryTime(quotes60[lastbar60].time);
				
				trade.targetPrice = adjustPrice(trade.entryPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);

				warning("break DOWN trade entered at " + data.time );
				if ( data != null )
					warning(data.time +	" " + data.high + " " + data.low );
				else
					warning("last tick:" + price);
					
				trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
				enterMarketPosition(trade.entryPrice);
				return;
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			
			QuoteData lowestPoint = Utility.getLow( quotes60, start60, lastbar60);
			System.out.println("lowes point is " + lowestPoint.time);
			
		    PushList pushList = Pattern.findPast1Highs1(quotes60, lowestPoint.pos, lastbar60 );
			
			if ((pushList.phls == null) || (pushList.phls.length == 0 ))
				return;

			double avgBarSize60 = Utility.averageBarSize(quotes60);
			
			PushHighLow phl = pushList.phls[pushList.phls.length-1];
			
			int pushDuration = phl.pushWidth;
			double pushSize = phl.pushSize;

			if (( quotes60[phl.prePos].high - lowestPoint.low ) > 2 * avgBarSize60)
			{
				
				
				
				
				trade.entryPrice = quotes60[phl.prePos].high;
				trade.setEntryTime(quotes60[lastbar60].time);
				
				trade.targetPrice = adjustPrice(trade.entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);

				warning("break UP trade entered at " + data.time );
				if ( data != null )
					warning(data.time +	" " + data.high + " " + data.low );
				else
					warning("last tick:" + price);
					
				trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
	
				enterMarketPosition(trade.entryPrice);
				return;
			}
		}
		
		//return null;
	}
	

	
	
	
	public void reversalEntry(QuoteData data)
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60);
		int lastbar60 = quotes60.length - 1;

		int detectPos15 = Utility.findPositionByMinute( quotes15, trade.detectTime, Constants.BACK_TO_FRONT);
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int highest15 = Utility.getHigh( quotes15, detectPos15-5, lastbar15).pos;

		    PushList pushList = Pattern.findPast1Lows1(quotes15, highest15, lastbar15 );
			
			if ((pushList.phls == null) || (pushList.phls.length == 0 ) || (pushList.phls.length > 2 ))
				return;
			
			PushHighLow phl = pushList.phls[pushList.phls.length-1];
			
			int pushDuration = phl.pushWidth;
			double pushSize = phl.pushSize;

			double entryPrice = quotes15[phl.prePos].low;
			if ( entryPrice < highest15 - FIXED_STOP * PIP_SIZE)
			{
				trade.setEntryTime(quotes15[lastbar15].time);
				trade.entryPrice = entryPrice;
	
				trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
				enterMarketPosition(entryPrice);
			} 
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			int lowest15 = Utility.getLow( quotes15, detectPos15-5, lastbar15).pos;

		    PushList pushList = Pattern.findPast1Highs1(quotes15, lowest15, lastbar15 );
		    System.out.println("lowest15=" + quotes15[lowest15].time + " " + data.time);
		    
			if ((pushList != null ) && (pushList.phls != null) && (pushList.phls.length > 0 ))
				System.out.println("find high @" + data.time);
			
			if ((pushList.phls == null) || (pushList.phls.length == 0 ) || (pushList.phls.length > 2 ))
				return;
			
			PushHighLow phl = pushList.phls[pushList.phls.length-1];
			
			//int pushDuration = phl.pushWidth;
			//double pushSize = phl.pushSize;

			double entryPrice = quotes15[phl.prePos].low;
			if ( entryPrice > lowest15 + FIXED_STOP * PIP_SIZE)
			{
				trade.setEntryTime(quotes15[lastbar15].time);
				trade.entryPrice = entryPrice;
	
				trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
	
				enterMarketPosition(entryPrice);
			} 
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
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_FILLED)))
			{
				exit123_close_monitor(data);
				//exit123_new9c4_123( data ); default  
				//exit123_new9c4( data );  
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
		QuoteData[] quotes = getQuoteData();
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
					logger.warning(symbol + " " + CHART + " trade " + trade.detectTime + " missed as price move away");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
				else if (( data.timeInMillSec - trade.detectTimeInMill) > 5 * 3600000L)
				{
					logger.warning(symbol + " " + CHART + " trade " + trade.detectTime + " missed as time passed");
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
					logger.warning(symbol + " " + CHART + " trade " + trade.detectTime + " missed as price moved away");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
				else if (( data.timeInMillSec - trade.detectTimeInMill) > 5 * 3600000L)
				{
					logger.warning(symbol + " " + CHART + " trade " + trade.detectTime + " missed as time passed ");
					cancelOrder(trade.limitId1);
					cancelOrder(trade.stopId);
					trade = null;
				}
			}
		}
	}
	
	public void checkTradeExpiring_folow_by_market(QuoteData data)
	{
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.status.equals(Constants.STATUS_OPEN) &&
					(/*(trade.entryPrice - data.low ) > 2 * FIXED_STOP * PIP_SIZE ) ||*/ ( data.timeInMillSec - trade.detectTimeInMill) > 1800000L))
			{
				logger.warning(symbol + " " + CHART + " trade " + trade.detectTime + " sell missed, follow by a market order ");
				cancelOrder(trade.limitId1);
				cancelOrder(trade.stopId);
				trade.limitId1 = trade.stopId = 0;

				double prevEntryPrice = trade.entryPrice;
				String prevEntryTime = trade.detectTime;
				enterSellPosition( data.close - PIP_SIZE, true);
				// reset back so the exit stragety can work correctly
				trade.entryPrice = prevEntryPrice;
				trade.setEntryTime(prevEntryTime);
				
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.status.equals(Constants.STATUS_OPEN) &&
					(( data.timeInMillSec - trade.detectTimeInMill) > 1800000L))
			{
				logger.warning(symbol + " " + CHART + " trade " + trade.detectTime + " buy missed, follow by a market order ");
				cancelOrder(trade.limitId1);
				cancelOrder(trade.stopId);
				trade.limitId1 = trade.stopId = 0;

				double prevEntryPrice = trade.entryPrice;
				String prevEntryTime = trade.detectTime;
				enterBuyPosition( data.close + PIP_SIZE, true);
				// reset back so the exit stragety can work correctly
				trade.entryPrice = prevEntryPrice;
				trade.setEntryTime(prevEntryTime);
			}
		}
		
	}

	
	
	
	
	
	
	

	
	
	public void exit123_close_monitor( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		
		int LOT_SIZE = POSITION_SIZE/2;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		
		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Iteger(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		//BigTrend bt = determineBigTrend2( quotes240);
		
		//MABreakOutList mol240 = MABreakOutTouchSetup.getMABreakOuts(quotes240, ema20_240 );
		//if ( mol240 != null )
		//{
			//System.out.print(mol240.toString(quotes240));
		//}
		
		
		//if (trade.reach1FixedStop == false)
		//	exit123_new9_checkReversalsOrEalyExit( data );

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE )
				trade.reach1FixedStop = true;

			if (( trade.entryPrice - data.low ) > 2 * FIXED_STOP * PIP_SIZE )
			{
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
			}
					
			
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			if (( trade.getProgramTrailingStop() != 0 ) && ((trade.getProgramTrailingStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramTrailingStop() - data.close)/PIP_SIZE;

			trade.adjustProgramTrailingStop(data);
			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.high > trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}

			
			// average up
			if ((average_up == true ) && (trade.averageUp == false ) && (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].low < trade.entryPrice - FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice - FIXED_STOP * PIP_SIZE, LOT_SIZE );
				
				trade.averageUp = true;
			}
			
			

			if (lastbarL < tradeEntryPosL + 2)
			return;

			// calculate touch 20MA point
			int wave2PtL = 0;
			/*MABreakOutList molL = MABreakOutTouchSetup.findMABreakOutsDown(quotesL, ema20L, tradeEntryPosL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				wave2PtL = 1;
			}
			/*
			public static MABreakOutList findMABreakOutsDown(QuoteData[] quotes, double[] ema, int start )*/

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
			
			
			//int pushStart = tradeEntryPosL-1;
			//int pushStart = trade.pushStart;
			//int pushStart = (trade.prevUpStart > 12)? trade.prevUpStart-12:0;
			//pushStart = Utility.getHigh(quotesL, pushStart, lastbarL).pos;
			int pushStart = Utility.getHigh(quotesL, lastbarL-48, lastbarL).pos;
			trade.pushStartL = pushStart;
			PushList pushList = PushSetup.getDown2PushList(quotesL, pushStart, lastbarL);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
		        
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotesL[lastPush.prePos].low;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotesL[phl.prePos].low;
				pushList.calculatePushMACD( quotesL);
				int positive = phls[lastPushIndex].pullBackMACDPositive;

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
					
					// calculate touch20MA
					//pushList.calculatePushTouchMA( quotesL, ema20L);
					//wave2PtL = pushList.getTouch20MANum(); 
	
					
					// take profit rule 1:
					/*
					if ( numOfPush >= 3 )
					{
						
						if (( phls[lastPushIndex-2].breakOutSize > 0.3 * FIXED_STOP * PIP_SIZE ) && ( phls[lastPushIndex-1].breakOutSize < 0.2 * FIXED_STOP * PIP_SIZE ))
						{
							//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
							takeProfit2( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
						}
					}*/
					
					// take profit rule 2:
	
					
					
					/******************************************************************************
					// look for pullbacks
					 * ****************************************************************************/
	//				if ( pushList.end == lastbarL)
					{
						if ( numOfPush >= 2 )
						{
							/*
							if (LESS_VALUE( lastBreakOut1, 6 * PIP_SIZE ) &&  LESS_VALUE( lastBreakOut2, 6 * PIP_SIZE ))
							{
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								return;
							}*/
								
						}

						if (( numOfPush >= 4 ) && ( largePushIndex == 0 ))
						{
							//takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							/*if (!trade.type.equals(Constants.TRADE_CNT))
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							else
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );*/
							//return;
						}
						

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
									//trade.createProgramTrailingRange( triggerPrice, triggerPrice - 3 * FIXED_STOP * PIP_SIZE, 1.5*FIXED_STOP * PIP_SIZE );
	
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
	
								
							if ( (trade.reach2FixedStop == false) && ( timePassed >= 24 ))
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
									//if ( numOfPush >= 4 )
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
//				else if ( pushList.end == (lastbarL-2))
				{
					/*
					if (!exitTradePlaced())
					{
						if (( lastPush.pullBackSize  > 2 * FIXED_STOP * PIP_SIZE) && ( lastPush.breakOutSize < 10 * PIP_SIZE ))
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(quotesL[lastbarL-1].close, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}*/
				}
/*				else  // look up for reversals
				{
					if ((mol != null ) && ( mol.getDirection() == Constants.DIRECTION_UP ))
					{
						if (!trade.type.equals(Constants.TRADE_CNT) && ( trade.reach1FixedStop == false ) && ( data.high > lastPush.pullBack.high )) 
						{
							closeReverseTrade( true, lastPush.pullBack.high, POSITION_SIZE );
							return;
						}
					}
				}*/
				
				
				/******************************************************************************
				// move stops
				 * ****************************************************************************/
				if (trade.reach2FixedStop == true)
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
							System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							//trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
				}
			}

			
			
			
			/******************************************************************************
			// check on smaller time frame as well
			 * ****************************************************************************/
			if (!exitTradePlaced())
			{	
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
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE )
				trade.reach1FixedStop = true;

			if (( data.high - trade.entryPrice ) > 2 * FIXED_STOP * PIP_SIZE )
			{
				placeBreakEvenStop();
				trade.reach2FixedStop = true;
			}


			profitInRisk =  ( data.close - trade.stop )/PIP_SIZE;
			if (( trade.getProgramTrailingStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramTrailingStop() )/PIP_SIZE;

			trade.adjustProgramTrailingStop(data);
			if  (( trade.getProgramTrailingStop() != 0 ) && ( data.low < trade.getProgramTrailingStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramTrailingStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramTrailingStop());
				return;
			}

			
			if ((average_up == true ) && (trade.averageUp == false ) && (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].high > trade.entryPrice + FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice + FIXED_STOP * PIP_SIZE, LOT_SIZE );

				trade.averageUp = true;
			}

			

			if (lastbarL < tradeEntryPosL + 2)
				return;
			

			// calculate touch 20MA point
			int wave2PtL = 0;
			/*MABreakOutList molL = MABreakOutTouchSetup.findMABreakOutsUp(quotesL, ema20L, tradeEntryPosL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				wave2PtL = 1;
			}*/

			
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

			
			//int pushStart = tradeEntryPosL-1;
			//int pushStart = trade.pushStart;
			//int pushStart = (trade.prevDownStart > 12)? trade.prevDownStart-12:0;
			//pushStart = Utility.getLow(quotesL, pushStart, lastbarL).pos;
			int pushStart = Utility.getLow(quotesL, lastbarL-48, lastbarL).pos;
			trade.pushStartL = pushStart;
			PushList pushList = PushSetup.getUp2PushList(quotesL, pushStart, lastbarL);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotesL[lastPush.prePos].high;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				double pullback = quotesL[phl.prePos].high - phl.pullBack.low;
				pushList.calculatePushMACD( quotesL);
				int negatives = phls[lastPushIndex].pullBackMACDNegative;

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
	
					//pushList.calculatePushTouchMA( quotesL, ema20L);
					//wave2PtL = pushList.getTouch20MANum(); 
					
					// take profit rule 1:
					/*
					if ( numOfPush >= 3 )
					{
						if (( phls[lastPushIndex-2].breakOutSize > 0.3 * FIXED_STOP * PIP_SIZE ) && ( phls[lastPushIndex-1].breakOutSize < 0.2 * FIXED_STOP * PIP_SIZE ))
						{
							//takePartialProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
							takeProfit2( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), POSITION_SIZE/2 );
						}
					}*/
					
					// take profit rule 2:
	
	
					//System.out.println("number of push is " + numOfPush);
		//			if ( pushList.end == lastbarL)
					{
						/*
						if ( numOfPush >= 2 )
						{
							if (LESS_VALUE( lastBreakOut1, 6 * PIP_SIZE ) &&  LESS_VALUE( lastBreakOut2, 6 * PIP_SIZE ))
							{
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							//takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								return;
							}
								
						}*/
	
						if (( numOfPush >= 4 ) && ( largePushIndex == 0 ))
						{
						//	takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							/*if (!trade.type.equals(Constants.TRADE_CNT))
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							else
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );*/
							//return;
						}
						
	
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
									//trade.createProgramTrailingRange( triggerPrice, triggerPrice + 3 * FIXED_STOP * PIP_SIZE, 1.5*FIXED_STOP * PIP_SIZE );
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
									//if ( numOfPush >= 4 )
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
	//			else if ( pushList.end == (lastbarL-2))
				{
					/*
					if (!exitTradePlaced())
					{
						if (( lastPush.pullBackSize  > 2 * FIXED_STOP * PIP_SIZE) && ( lastPush.breakOutSize < 10 * PIP_SIZE ))
						{
							warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
							takeProfit( adjustPrice(quotesL[lastbarL-1].close, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}*/
				}
			/*	else  // look up for reversals
				{
					if ((mol != null ) && ( mol.getDirection() == Constants.DIRECTION_DOWN ))
					{
						if (!trade.type.equals(Constants.TRADE_CNT) && ( trade.reach1FixedStop == false ) && ( data.low < lastPush.pullBack.low )) 
						{
							closeReverseTrade( true, lastPush.pullBack.low, POSITION_SIZE );
							return;
						}
					}
				}*/

				
				/******************************************************************************
				// move stop
				 * ****************************************************************************/
				if (trade.reach2FixedStop == true)
				{	
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}
				
			}	


			/******************************************************************************
			// smaller timefram for detecting sharp pullbacks
			 * ****************************************************************************/
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
		}
	}
	
	

	public void exit123_new9c4_123( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);;
		
		int LOT_SIZE = POSITION_SIZE/2;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		labelPositions( quotesL );

		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

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
			/*
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + FIXED_STOP * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					//System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;

					boolean reversequalified = true;
					if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
						trade.detectTime = quotes[lastbar].time;
						trade.positionSize = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}
					return;
				}
			}*/			
			
			
			
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
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
				}
			}


			int wave2PtL = 1;
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
							System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
			/*
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && ((( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE *0.3 ))            )/*      || 
				(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE ))*/
		/*	{
				if (( data.low <  (highestPointAfterEntry - FIXED_STOP * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					
					boolean reversequalified = true;
					if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

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
					}
					return;
				}
			}*/


			// check break_123
			/*
			boolean break123 = false;
			Reversal123 r = null;
			if ((!trade.type.equals(Constants.TRADE_CNT))
				&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
			{	
				int startL = Utility.findPositionByHour(quotesL, trade.getFirstBreakOutStartTime(), 2 );
				int touch20MAL = Utility.findPositionByHour(quotesL, trade.getTouch20MATime(), 2 );
	
				int prevDownStart = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 2);
				int prevDownStart1 = prevDownStart;
				while ( quotesL[prevDownStart1].high < ema20L[prevDownStart1])
					prevDownStart1--;
				prevDownStart = Utility.getLow( quotesL, prevDownStart1, prevDownStart).pos;	
				int fractual1 = prevDownStart;
				
			   // QuoteData[] frs = Pattern.getLastFractualLows2(quotesL, touch20MAL, lastbarL );
				QuoteData[] frs = Pattern.getLastFractualLows2(quotesL, touch20MAL-3, lastbarL );
			   // QuoteData[] frs = Pattern.getLastFractualLows2(quotesL, fractual1+2, lastbarL );
			   // QuoteData[] frs = Pattern.getLastFractualLows2(quotesL, startL, lastbarL );
			    if (( frs != null ) && ( frs.length > 0 ))
			    {
					int fractual2 = frs[0].pos;
					//System.out.println(symbol + " fractuals:" + quotesL[fractual1].time + " " + quotesL[fractual2].time );
					
					double last_a = 0;
					last_a = TrendLine.cal_a(quotesL[fractual1].pos, quotesL[fractual2].pos,quotesL[fractual1].low, quotesL[fractual2].low );
					
					r = new Reversal123(last_a, fractual1, fractual2);
			    }
			    
			    if ( r != null )
			    {
			    	for ( int j = r.fractual2+2; j < lastbarL; j++)
			    	{	
				    	double expected_price_last = r.calculateProjectedPrice(quotesL, j, Reversal123.UP_BY_LOW);
						double expected_price_last_1 = r.calculateProjectedPrice(quotesL, j-1, Reversal123.UP_BY_LOW);
						
						if (( quotesL[j].high <= expected_price_last ) && ( quotesL[j-1].high <= expected_price_last_1 ))
						{
							break123 = true;
							System.out.println(symbol + " detect touching 123 at " + expected_price_last + "@" +  quotesL[j].time + " fractuals:" + quotesL[r.fractual1].time + " " + quotesL[r.fractual2].time );
							break;
						}
			    	}
			    }
			}*/
			
			
			

			
			
			
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
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
					/*
					if ( break123 == true)
					{
						warning(data.time + " take profit at " + triggerPrice + " after breaking 123");
						takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
					}
					else*/ if ( pullback  > 2 * FIXED_STOP * PIP_SIZE)
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
						System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
	

	
	
	
	
	
	
	
	public void exit123_new9c5( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);;

		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

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
					logger.warning(symbol + " " + CHART + " close trade with small tip");

					boolean reversequalified = true;
					String no_reverse_symbol = mainProgram.checkNoReverse();
					if (( no_reverse_symbol != null ) && ( no_reverse_symbol.indexOf(symbol)!= -1))
					{
						reversequalified = false;
					}
					
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
			
			
			// add a second reversal
			if ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) )
			{	
				for ( int i = lastbarL; i > tradeEntryPosL; i--)
				{	
					PushHighLow phl = findLastNLow( quotesL, tradeEntryPosL, i, 2 );
					if ( phl != null )
					{
						if (data.high > phl.pullBack.high)       
						{	
							logger.warning(symbol + " " + CHART + " reverse to the trend");
							double reversePrice = phl.pullBack.high;
							boolean reversequalified = true;

							// reverse;
							AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
							if ( reversequalified == false )
							{
								closePositionByMarket(trade.remainingPositionSize, reversePrice);
							}
							else
							{	
								cancelOrder(trade.stopId);

								logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
				maTouches = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
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
							MATouch[] maTouches2 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);

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
			if (trade.reach2FixedStop == true )
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
							System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
					logger.warning(symbol + " " + CHART + " close trade with small tip");

					boolean reversequalified = true;
					String no_reverse_symbol = mainProgram.checkNoReverse();
					if (( no_reverse_symbol != null ) && ( no_reverse_symbol.indexOf(symbol)!= -1))
					{
						reversequalified = false;
					}

					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
				maTouches = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
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
							MATouch[] maTouches2 = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 1);
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
						System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
	

	
	
	
	

	
	
	
	public void exit123_new9c4( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240);;
		
		int LOT_SIZE = POSITION_SIZE/2;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

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
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;

					boolean reversequalified = true;
					if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
				maTouches = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
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
							MATouch[] maTouches2 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);

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
							System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					
					boolean reversequalified = true;
					if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						cancelOrder(trade.stopId);

						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						removeTrade();
						
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
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
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
				maTouches = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
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
							MATouch[] maTouches2 = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 1);
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
						System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
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


	public static PushHighLow findLastNHigh20(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastHigh = Utility.getHigh(quotes, lastbar - 20, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).high < lastHigh.high)
			return null; // not the highest

		if ((lastbar - lastHigh.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos, lastbar);
			phl.pullBack = Utility.getLow(quotes, lastHigh.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	
	public static PushHighLow findLastNLow20(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastLow = Utility.getLow(quotes, lastbar - 20, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).low > lastLow.low)
			return null; // not the lowest

		if ((lastbar - lastLow.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos, lastbar);
			phl.pullBack = Utility.getHigh(quotes, lastLow.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	
	public static PushHighLow findLastNHigh(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).high < lastHigh.high)
			return null; // not the highest

		if ((lastbar - lastHigh.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos, lastbar);
			phl.pullBack = Utility.getLow(quotes, lastHigh.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNLow(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).low > lastLow.low)
			return null; // not the lowest

		if ((lastbar - lastLow.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos, lastbar);
			phl.pullBack = Utility.getHigh(quotes, lastLow.pos, lastbar);
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNHigh2(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastHigh = Utility.getHigh(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).high < lastHigh.high)
			return null; // not the highest

		if ((lastbar - lastHigh.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastHigh.pos, lastbar);
			phl.pullBack = Utility.getLow(quotes, lastHigh.pos + 1, lastbar); // we
																				// do
																				// not
																				// want
																				// to
																				// the
																				// first
																				// bar
																				// to
																				// be
																				// the
																				// pull
																				// back
			return phl;
		}
		else
			return null;

	}

	public static PushHighLow findLastNLow2(Object[] quotes, int start, int lastbar, int n)
	{
		QuoteData lastLow = Utility.getLow(quotes, start, lastbar - 1);

		if (((QuoteData) quotes[lastbar]).low > lastLow.low)
			return null; // not the lowest

		if ((lastbar - lastLow.pos) > n)
		{
			PushHighLow phl = new PushHighLow(lastLow.pos, lastbar);
			phl.pullBack = Utility.getHigh(quotes, lastLow.pos + 1, lastbar); // we
																				// do
																				// not
																				// want
																				// to
																				// the
																				// first
																				// bar
																				// to
																				// be
																				// the
																				// pull
																				// back
			return phl;
		}
		else
			return null;

	}


	int findNumLows20(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNLow20(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	int findNumHighs20(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNHigh20(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	int findNumLows(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNLow(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	int findNumHighs(Object[] quotes, int begin, int end, int pullbackgap)
	{
		int count = 0;
		while (end > begin)
		{
			PushHighLow phl = findLastNHigh(quotes, begin, end, pullbackgap);
			if (phl != null)
			{
				count++;
				end = phl.prePos;
			}
			else
				end--;
		}

		return count;
	}

	private int checkTradeHistoy(String action)
	{
		int count = 0;
		Iterator it = tradeHistory.iterator();
		while (it.hasNext())
		{
			Trade t = (Trade) it.next();
			if (t.action.equals(action))
				count++;
		}

		return count;
	}

	public QuoteData findLastPullBackLow(Object[] quotes, int begin, int end)
	{
		int pos = end;

		for (int i = end; i > begin; i--)
		{
			if (((QuoteData) quotes[pos - 1]).low > ((QuoteData) quotes[pos]).low)
			{
				return Utility.getLow(quotes, pos, end);
			}
		}

		return null;
	}

	public QuoteData findLastPullBackHigh(Object[] quotes, int begin, int end)
	{
		int pos = end;

		for (int i = end; i > begin; i--)
		{
			if (((QuoteData) quotes[pos - 1]).high < ((QuoteData) quotes[pos]).low)
			{
				return Utility.getHigh(quotes, pos, end);
			}
		}

		return null;
	}

	private boolean findTrendlineBreakToUp(QuoteData data, Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		// if there is a ma cross over, then the cross direction is the new
		// direction
		int lastConsectiveUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 72);
		int lastConsectiveDown = Pattern.findLastMAConsectiveDown(ema20, ema50, 72);

		// make sure I dont get caught up on a major reversal
		if (lastConsectiveDown > lastConsectiveUp)
		{
			// the last trend is down
			if (data.close > ema20[lastbar])
			{
				for (int i = lastbar; i > 0; i--)
				{
					if (((QuoteData) quotes[i]).low > ema20[i])
					{
						int pos = i;
						while (((QuoteData) quotes[pos]).low < ema20[pos])
							pos--;

						int pos2 = pos;
						while (((QuoteData) quotes[pos2]).low > ema20[pos])
							pos2--;

						// find the last highest point
						QuoteData lastHigh = Utility.getHigh(quotes, pos2, pos);
						if (data.close > lastHigh.high)
							return true;
					}
				}
			}
		}

		return false;

	}

	private boolean findTrendlineUpBreakDown(QuoteData data, Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		// if there is a ma cross over, then the cross direction is the new
		// direction
		int lastConsectiveUp = Pattern.findLastMAConsectiveUp(ema20, ema50, 72);
		int lastConsectiveDown = Pattern.findLastMAConsectiveDown(ema20, ema50, 72);

		// make sure I dont get caught up on a major reversal
		if (lastConsectiveUp > lastConsectiveDown)
		{
			logger.warning(symbol + " " + CHART + " larger timeframe check - up trend, check for break down");
			// the last trend is up
			if (data.close < ema20[lastbar])
			{
				logger.warning(symbol + " " + CHART + " larger timeframe check - close below 20MA");
				// the last bar is down, so to verify not to caught up a
				// downside 1-2-3 break
				for (int i = lastbar; i > 0; i--)
				{
					if (((QuoteData) quotes[i]).low < ema20[i])
					{
						int pos = i;
						while (((QuoteData) quotes[pos]).low < ema20[pos])
							pos--;

						int pos2 = pos;
						while (((QuoteData) quotes[pos2]).low > ema20[pos])
							pos2--;

						logger.warning(symbol + " " + CHART + " larger timeframe check - last low is between close below 20MA");

						// find the last highest point
						QuoteData lastHigh = Utility.getHigh(quotes, pos2, pos);
						if (data.close > lastHigh.high)
							return true;
					}
				}
			}
		}

		return false;

	}

	// private Vector<QuoteData> findTrendlineDownBreakingToUpSide( Object[]
	// quotes, int begin, int end)
	private void findTrendlineDownBreakingToUpSide(Object[] quotes, int begin, int end)
	{
		/*
		 * Reversal_123 reversal123 = new Reversal_123( symbol, logger );
		 *
		 * int lastbar = quotes.length -1; double[] ema20 =
		 * Indicator.calculateEMA(quotes, 20);
		 *
		 * Reversal123 r = reversal123.calculateUp123_20MA( quotes, ema20); if (
		 * r != null ) { logger.info(symbol + " reversal 123 UP start at " +
		 * ((QuoteData) quotes[r.startpos]).time); logger.info(symbol +
		 * " reversal 123 UP support at " + ((QuoteData)
		 * quotes[r.supportpos]).time); logger.info(symbol +
		 * " reversal 123 UP break at " + ((QuoteData)
		 * quotes[r.breakpos]).time);
		 *
		 * }
		 *
		 * return true;
		 */

		/*
		 * Vector peaks = new Vector(); double[] ema20 =
		 * Indicator.calculateEMA(quotes, 20);
		 *
		 * int pos = end; while ( pos > begin ) { int peakbegin = pos; int
		 * peakend = pos;
		 *
		 * while (peakbegin > begin + 1) { if ((((QuoteData)
		 * quotes[peakbegin]).high > ema20[peakbegin]) && (((QuoteData)
		 * quotes[peakbegin-1]).high < ema20[peakbegin-1])) { // turning point
		 * to down peakbegin = peakbegin -1; QuoteData p =
		 * Utility.getHigh(quotes, peakbegin, peakend); peaks.add(p); break; }
		 * peakbegin--; }
		 *
		 * pos = peakbegin -1;
		 *
		 * }
		 *
		 * return peaks;
		 */
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		TrendLine reversal_123 = new TrendLine(symbol, logger);

		/*
		 * for ( int i = lastbar-20; i < lastbar - 2; i++) { Reversal123 r1 =
		 * reversal_123.calculateDown123_20MA_2(quotes, i, ema20, ema50);
		 * Reversal123 r2 = reversal_123.calculateDown123_20MA_2(quotes, i+1,
		 * ema20, ema50); Reversal123 r3 =
		 * reversal_123.calculateDown123_20MA_2(quotes, i+2, ema20, ema50);
		 *
		 * if (( r1.a == r2.a ) && ( r2.a != r3.a )) { // logger.info( symbol +
		 * " " + CHART + " down trend line - start point:" +
		 * ((QuoteData)quotes[r.startpos]).time + " a:" + r.a); logger.info(
		 * symbol + " " + CHART + " down trend line break detect at " +
		 * ((QuoteData)quotes[i+2]).time ); } }
		 */
		Reversal123 r1 = reversal_123.calculateDown123_20MA_2(quotes, lastbar, ema20, ema50);
		if (r1 != null)
		{
			for (int i = lastbar - 1; i > lastbar - 50; i--)
			{
				Reversal123 r0 = reversal_123.calculateDown123_20MA_2(quotes, i, ema20, ema50);
				if (r0 != null)
					// if ( r1 != null )
					// logger.info( symbol + " " + CHART + " a" + "=" + r1.a +
					// " " + ((QuoteData)quotes[i]).time);
					if (r1.a != r0.a)
					{
						logger.info(symbol + " " + CHART + " trend change detected at " + ((QuoteData) quotes[i]).time);
						return;
					}
			}
		}

	}

	Object[] findLastNPushesHigh(Object[] quotes, int start, int width)
	{
		int lastbar = quotes.length - 1;

		PushHighLow phl_cur = findLastNHigh(quotes, start, lastbar, width);
		if (phl_cur == null)
			return null;

		Vector<PushHighLow> highlows = new Vector<PushHighLow>();
		highlows.add(phl_cur);

		int pos = phl_cur.prePos;
		while (pos > start)
		{
			for (int i = pos; i > start; i--)
			{
				PushHighLow phl = findLastNHigh(quotes, start, i, width);
				if (phl != null)
				{
					highlows.add(phl);
					pos = phl.prePos;
					continue;
				}
			}
		}

		return highlows.toArray();
	}

	Object[] findLastNPushesLow(Object[] quotes, int start, int width)
	{
		int lastbar = quotes.length - 1;

		PushHighLow phl_cur = findLastNLow(quotes, start, lastbar, width);
		if (phl_cur == null)
			return null;

		Vector<PushHighLow> highlows = new Vector<PushHighLow>();
		highlows.add(phl_cur);

		int pos = phl_cur.prePos;
		while (pos > start)
		{
			for (int i = pos; i > start; i--)
			{
				PushHighLow phl = findLastNLow(quotes, start, i, width);
				if (phl != null)
				{
					highlows.add(phl);
					pos = phl.prePos;
					continue;
				}
			}
		}

		return highlows.toArray();
	}

	int determineTrendByHigherHigh(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		if (((QuoteData) quotes[lastbar]).close > ema50[lastbar])
		{
			for (int i = lastbar; i > lastbar - 25; i--)
			{
				PushHighLow phl_cur = findLastNHigh(quotes, i - 25, i, 2);

				if (phl_cur != null)
				{
					double currentLow = Utility.getLow(quotes, phl_cur.curPos + 1, lastbar).low;

					if (currentLow > phl_cur.pullBack.low)
						return Constants.DIRECTION_UP;

					break;
				}
			}
		}
		else if (((QuoteData) quotes[lastbar]).close < ema50[lastbar])
		{
			for (int i = lastbar; i > lastbar - 25; i--)
			{
				PushHighLow phl_cur = findLastNLow(quotes, i - 25, i, 2);

				if (phl_cur != null)
				{
					double currentHigh = Utility.getHigh(quotes, phl_cur.curPos + 1, lastbar).high;

					if (currentHigh < phl_cur.pullBack.high)
						return Constants.DIRECTION_DOWN;

					break;
				}
			}
		}

		return Constants.DIRECTION_UNKNOWN;
	}

	int determineTrendByHigherHigh2(Object[] quotes)
	{
		int lastbar = quotes.length - 1;
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastNHigh = -1;
		int lastNLow = -1;
		PushHighLow phl_cur = null;
		for (int i = lastbar; i > lastbar - 25; i--)
		{
			phl_cur = findLastNHigh(quotes, i - 25, i, 2);
			if (phl_cur != null)
			{
				lastNHigh = i;
				break;
			}
		}

		for (int i = lastbar; i > lastbar - 25; i--)
		{
			phl_cur = findLastNLow(quotes, i - 25, i, 2);

			if (phl_cur != null)
			{
				lastNLow = i;
				break;
			}
		}

		if (lastNHigh > lastNLow)
		{
			double currentLow = Utility.getLow(quotes, phl_cur.curPos + 1, lastbar).low;

			if (currentLow > phl_cur.pullBack.low)
				return Constants.DIRECTION_UP;
		}
		else if (lastNHigh < lastNLow)
		{
			double currentHigh = Utility.getHigh(quotes, phl_cur.curPos + 1, lastbar).high;

			if (currentHigh < phl_cur.pullBack.high)
				return Constants.DIRECTION_DOWN;

		}

		return Constants.DIRECTION_UNKNOWN;
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

	public int countingCrossOvers(double[] ema1, double[] ema2, int begin, int end)
	{
		int count = 0;

		for (int i = begin + 1; i <= end; i++)
		{
			if (((ema1[i] > ema2[i]) && (ema1[i - 1] < ema2[i - 1])) || ((ema1[i] < ema2[i]) && (ema1[i - 1] > ema2[i - 1])))
				count++;
		}

		return count;
	}


	
	public static boolean EF_ifFirstSecondPullBackUp(QuoteData[] quotes, double[] ema20, int numOfBarsCrossOver, int numOfBarsBreakout )
	{
		int pos = Pattern.findPriceCross20MAUp(quotes, ema20, numOfBarsCrossOver);
		
		//if ( pos == Constants.NOT_FOUND )
			//return true;
		
		Vector<BreakOut> breakouts = Pattern.find20MABreakoutUps(quotes, ema20, pos);
		
		int sum = 0;
		int biggestWidth = 0;
		Iterator<BreakOut> it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			if ( bo.highWidth >= numOfBarsBreakout)
				sum++;
			if ( bo.highWidth > biggestWidth)
				biggestWidth = bo.highWidth;
		}

		if (( biggestWidth > 30 ) || ( sum > 4 ))
			return false;
		else
			return true;
			
	}
	
	
	
	public static boolean EF_ifFirstSecondPullBackDown(QuoteData[] quotes, double[] ema20, int numOfBarsCrossOver, int numOfBarsBreakout )
	{
		int pos = Pattern.findPriceCross20MADown(quotes, ema20, numOfBarsCrossOver);

		//if ( pos == Constants.NOT_FOUND )
		//	return true;
		
		Vector<BreakOut> breakouts = Pattern.find20MABreakoutDowns(quotes, ema20, pos);
		
		int sum = 0;
		int biggestWidth = 0;
		Iterator<BreakOut> it = breakouts.iterator();
		while( it.hasNext())
		{
			BreakOut bo = (BreakOut)it.next();
			//System.out.println("breakouts: " + bo.width);
			if ( bo.highWidth >= numOfBarsBreakout)
				sum++;
			if ( bo.highWidth > biggestWidth)
				biggestWidth = bo.highWidth;
		}
		
		if (( biggestWidth > 30 ) || ( sum > 4 ))
			return false;
		else
			return true;
			
	}

	PushHighLow findLastPullBack( QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		PushHighLow phl_up = null;
		PushHighLow phl_down = null;
		
		for ( int i = lastbar-1; i > 0; i--)
		{
			// looking for the first bar that has higher/lower on both side and the right side is higher/lower than the left
			// look for ups
			int left =  Constants.NOT_FOUND;
			for ( int j = i -1; j> 0; j--)
			{
				if (quotes[j].high > quotes[i].high)
				{
					left = j;
					while( quotes[left-1].high > quotes[left].high)
						left--;
					break;
				}
			}
			
			int right = Constants.NOT_FOUND;
			QuoteData q = Utility.getHigh(quotes, i+1, lastbar);
			if ( q != null )
				right = q.pos;
			
			if ((left != Constants.NOT_FOUND) && (right != Constants.NOT_FOUND))
			{
				if ( quotes[right].high > quotes[left].high)
				{
					phl_up = new PushHighLow(left, right);
					QuoteData pullback = Utility.getLow(quotes, left+1, right-1);
					phl_up.pullBack = pullback;
					phl_up.direction = Constants.DIRECTION_UP;
				}
			}
			
			
			// look for downs
			left =  Constants.NOT_FOUND;
			for ( int j = i -1; j> 0; j--)
			{
				if (quotes[j].low < quotes[i].low)
				{
					left = j;
					while( quotes[left-1].low < quotes[left].low)
						left--;
					break;
				}
			}
			
			right = Constants.NOT_FOUND;
			q = Utility.getLow(quotes, i+1, lastbar);
			if ( q != null )
				right = q.pos;
			
			if ((left != Constants.NOT_FOUND) && (right != Constants.NOT_FOUND))
			{
				if ( quotes[right].low < quotes[left].low)
				{
					phl_down = new PushHighLow(left, right);
					QuoteData pullback = Utility.getHigh(quotes, left+1, right-1);
					phl_down.pullBack = pullback;
					phl_down.direction = Constants.DIRECTION_DOWN;
				}
			}

			if (( phl_up != null ) && ( phl_down != null))
				return null;
			
			if ( phl_up != null )
				return phl_up;
			else if ( phl_down != null )
				return phl_down;
		}

		return null;

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

	protected Trade findTradeHistory(String action, int startL)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (startL == t.pullBackStartL))
				return t;
		}

		return null;

	}

	protected Trade findTradeHistory(String action, double triggerPrice)
	{
		Iterator<Trade> it = tradeHistory.iterator();

		while (it.hasNext())
		{
			Trade t = it.next();
			if ( t.action.equals(action) && (triggerPrice == t.triggerPrice))
				return t;
		}

		return null;

	}

	
	
	public int getLast2CrossOver(QuoteData[] quotes )
	{
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastCross = Constants.NOT_FOUND;
		int prevCross = Constants.NOT_FOUND;
		int thirdEarly = Constants.NOT_FOUND;

		if ( ema20[lastbar] > ema50[lastbar])
		{
			lastCross = Pattern.findLastMACrossUp(ema20, ema50, lastbar, 1);
			if ( lastCross != Constants.NOT_FOUND )
			{
				prevCross = Pattern.findLastMACrossDown(ema20, ema50, lastCross-1, 1);
				if ( prevCross != Constants.NOT_FOUND )
				{
					thirdEarly = Pattern.findLastMACrossUp(ema20, ema50, prevCross-1, 1);
				}
			}
		}
		else if ( ema20[lastbar] < ema50[lastbar])
		{
			lastCross = Pattern.findLastMACrossDown(ema20, ema50, lastbar, 1);
			if ( lastCross != Constants.NOT_FOUND )
			{
				prevCross = Pattern.findLastMACrossUp(ema20, ema50, lastCross-1, 1);
				if ( prevCross != Constants.NOT_FOUND )
				{
					thirdEarly = Pattern.findLastMACrossDown(ema20, ema50, prevCross-1, 1);
				}
			}
		}
		
		return thirdEarly;
	}


	
	QuoteData findLastSignificentHigh( QuoteData[] quotesL, int pos)
	{
		for ( int i = pos - 12; i > pos - 72; i--)
		{
			boolean highestForAfter = true;
			double lowestAfter = 999;
			for ( int j = i+1; j <=pos; j++)
			{
				if ( quotesL[j].high > quotesL[i].high)
				{
					highestForAfter = false;
					break;
				}
				else
				{
					if (quotesL[j].low < lowestAfter)
						lowestAfter = quotesL[j].low;
				}
			}
			
			if (!highestForAfter)
				continue;
			if (( quotesL[i].high - lowestAfter ) < 3 * FIXED_STOP * PIP_SIZE)
				continue;
			
			int prevMark = (i - 48)>0?(i - 48):0;
			
			boolean highestForBefore = true;
			boolean lowestBefore = false;
			for ( int j = i-1; j > prevMark; j--)
			{
				if ( quotesL[j].high > quotesL[i].high)
				{
					highestForBefore = false;
					break;
				}
				else
				{
					if (quotesL[j].low < quotesL[i].high - 2 * FIXED_STOP * PIP_SIZE)
					{
						lowestBefore = true;
						break;
					}
				}
			}
			
			if (!highestForBefore)
				continue;
			
			if ( lowestBefore)
				return quotesL[i];
		}
		
		return null;
		/*
		QuoteData lastHigh = Utility.getHigh(quotesL, pos - 72, pos);
	
		double lowAfter = Utility.getLow( quotesL, lastHigh.pos + 1, pos-1).low;
		if ((lastHigh.high - lowAfter) < 2 * FIXED_STOP * PIP_SIZE)
			return null;
		
		int prevMark = (lastHigh.pos - 72)>0?(lastHigh.pos - 72):0;
		double lowBefore = Utility.getLow( quotesL, prevMark, lastHigh.pos-1).low;
		if ((lastHigh.high - lowBefore) < 2 * FIXED_STOP * PIP_SIZE)
			return null;
		
		return lastHigh;*/
		
		
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
	
	
	public void createTrade( String action, double quantity, double price1 )
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15);
		int lastbar15 = quotes15.length -1;
		
		if ( trade == null )
		{
			createOpenTrade("MANUAL", action);
			trade.status = Constants.STATUS_DETECTED;
			trade.POSITION_SIZE = (int)(POSITION_SIZE*quantity);
			
			enterLimitPosition1(price1, trade.POSITION_SIZE, 0); 
		}
		else
		{
			System.out.println(symbol + " Place Trade, trade already exist");
			System.out.println(symbol + " Place Trade, trade already exist");
			System.out.println(symbol + " Place Trade, trade already exist");
		}
	}
	
	
	public boolean inTradingTime( int hour, int min )
	{
		return true;
		/*
		int minute = hour * 60 + min;
		if (( minute >= 0) && (minute <= 660))//600
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


	public boolean inExpiringTime( int hour, int min )
	{
		if (( hour >= 12) && (hour < 13 ))//600
				return true;
		else
				return false;		
	}

	
	Trade createTradeFromInput()
	{
		if (Constants.ACTION_SELL.equals(INPUTED_ACTION))
		{
			if ( findTradeHistory( Constants.ACTION_SELL, INPUTED_ENTRY_PRICE) != null )
				return null;
			
			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
			trade.status = Constants.STATUS_DETECTED;
			trade.triggerPrice = INPUTED_ENTRY_PRICE;

			return trade;
			
		}
		else if (Constants.ACTION_BUY.equals(INPUTED_ACTION))
		{
			if ( findTradeHistory( Constants.ACTION_BUY, INPUTED_ENTRY_PRICE) != null )
				return null;

			createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
			trade.status = Constants.STATUS_DETECTED;
			trade.triggerPrice = INPUTED_ENTRY_PRICE;
			
		}
		
		return null;

	}
}


