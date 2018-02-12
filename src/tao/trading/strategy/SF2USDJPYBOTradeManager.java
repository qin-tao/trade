package tao.trading.strategy;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.BreakOut;
import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MACD;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.Push;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

public class SF2USDJPYBOTradeManager extends TradeManager2
{
	HashMap<String, QuoteData> qts = new HashMap<String, QuoteData>(1000);
	boolean CNT = false;
	int TARGET_PROFIT_SIZE;
    String TIME_ZONE;
	boolean oneMinutChart = false;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	HashMap<Integer, Integer> profitTake = new HashMap<Integer, Integer>();
	String currentTime;
	int DEFAULT_RETRY = 0;
	boolean test = false;
	boolean larger_timeframe_restriction = false;
	int QUICK_PROFIT = 25;
	int LOCKIN_PROFIT = 40;
	int SHALLOW_PROFIT_REVERSAL = 40;
	int CONN_ID;
	protected SF2USDJPYBOTradeManager largerTimeFrameTraderManager, smallTimeFrameTraderManager;
	boolean breakOutCalculated = false;
	boolean toBreakOutUp = false;
	boolean toBreakOutDown = false;
	public Vector<Integer> detectHistory = new Vector<Integer>();
	QuoteData currQuoteData;
//	int firstRealTimeDataPosL = 0;
	double lastTick_bid, lastTick_ask, lastTick_last;
	int lastTick;
	long firstRealTime = 0;
	
	// important switch
	boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean reverse_trade = false; // reverse after stopped out
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;
	
	boolean market_order = true;

	public SF2USDJPYBOTradeManager()
	{
		super();
	}

	public SF2USDJPYBOTradeManager(String account, EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super(account, client, contract, symbolIndex, logger);
	}

	public QuoteData[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
		Arrays.sort(quotes);
		return Arrays.copyOf(quotes, quotes.length, QuoteData[].class);
	}
	

	// /////////////////////////////////////////////////////////
	//
	// Check Stop
	//
	// /////////////////////////////////////////////////////////
	public void checkOrderFilled(int orderId, int filled)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if (orderId == trade.stopId)
		{
			warning("order " + orderId + " stopped out ");
			trade.stopId = 0;

			cancelTargets();
			
			removeTrade();
			//processAfterHitStopLogic_c();

		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("limit order: " + orderId + " " + filled + " filled");

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.limitPos1, trade.limitPrice1);

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize += trade.limitPos1; //+= filled;
			trade.entryTime = quotes[lastbar].time;
			trade.entryPos = lastbar;
			trade.entryPosL = lastbarL;

			// calculate stop here
			trade.stop = trade.limit1Stop;
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			
			trade.openOrderDurationInHour = 0;
			trade.status = Constants.STATUS_PLACED;
		
		}
		else if ((orderId == trade.stopMarketId) && ( trade.stopMarketPosFilled == 0 ))  // avoid sometime same message get sent twoice
		{
			warning("stop market order: " + orderId + " " + filled + " filled");

			CreateTradeRecord(trade.type, trade.action);
			AddOpenRecord(quotes[lastbar].time, trade.action, trade.POSITION_SIZE, trade.stopMarketStopPrice);

			trade.stopMarketPosFilled = trade.POSITION_SIZE;
			trade.entryPrice = trade.stopMarketStopPrice;
			trade.remainingPositionSize = trade.POSITION_SIZE; //+= filled;
			trade.entryTime = quotes[lastbar].time;
			trade.entryPos = lastbar;
			trade.entryPosL = lastbarL;

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
		else if (orderId == trade.takeProfit1Id)
		{
		}
		else if (orderId == trade.takeProfit2Id)
		{
		}
	}


	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
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
				trade.entryTime =  quotes[lastbar].time;
				trade.entryPos = lastbar;
				trade.entryPosL = lastbarL;

				// calculate stop here
				trade.stop = trade.limit1Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId1 = 0;

				trade.status = Constants.STATUS_PLACED;
				trade.openOrderDurationInHour = 0;
				
			}

			if ((trade.limitId2 != 0) && (data.high >= trade.limitPrice2))
			{
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
				trade.entryTime =  quotes[lastbar].time;
				trade.entryPos = lastbar;
				trade.entryPosL = lastbarL;

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
				trade.stoppedOutPos = lastbar;
				trade.stopId = 0;

				cancelTargets();
				processAfterHitStopLogic_c();
				//removeTrade();
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

			if ((trade.targetId2 != 0) && (data.low < trade.targetPrice2))
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
				trade.entryTime = quotes[lastbar].time;
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

			if ((trade.limitId2 != 0) && (data.high <= trade.limitPrice2))
			{
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
				trade.entryTime =  quotes[lastbar].time;
				trade.entryPos = lastbar;
				trade.entryPosL = lastbarL;

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
				trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;
				trade.stoppedOutPos = lastbar;

				cancelTargets();
				processAfterHitStopLogic_c();
				//removeTrade();
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

			if ((trade.targetId2 != 0) && (data.high > trade.targetPrice2))
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
		}
	}



	
	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		
		double prevStop = trade.stop;
		String prevAction = trade.action;
		String prevType = trade.type;
		String prevEntryTime = trade.entryTime;
		double prevEntryPrice = trade.entryPrice;
		
		if (prevType == Constants.TRADE_CNT )
		{
			removeTrade();
			return;
		}

		int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
		int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, prevEntryTime, 2 );

		removeTrade();

		if (Constants.ACTION_SELL.equals(prevAction))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( ( prevEntryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 )
			{
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
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					enterMarketPosition(reversePrice);
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(prevAction))
		{
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if ( ( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 )
			{
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
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

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

	public Trade checkBreakOut5a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//QuoteData[] quotesS = smallTimeFrameTraderManager.getQuoteData();
		//int lastbarS = quotesS.length - 1;
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			fine("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
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
			{
				QuoteData highest = Utility.getHigh( quotesL, start, lastbarL-1);
				if ((highest.pos < lastbarL-2 )&& (quotesL[highest.pos+1].high < highest.high )  && ( quotesL[highest.pos+2].high < highest.high))
					touch20MA = Utility.getLow( quotesL, highest.pos+1, highest.pos+2 ).pos;
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
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.setFirstBreakOutPos( firstBreakOut.pos, firstBreakOut.time);
				trade.setFirstBreakOutStartPos(start, quotesL[start].time);
				trade.setTouch20MAPos(touch20MA, quotesL[touch20MA].time);
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.FIXED_STOP = FIXED_STOP;

				warning("break UP detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			fine("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			

			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
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
				if ( quotesL[i].high >= ema20L[i])
				{
					touch20MA=i;
					fine( "touch20MA is" + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
			{
				QuoteData lowest = Utility.getLow( quotesL, start, lastbarL-1);
				if ((lowest.pos < lastbarL-2 )&& (quotesL[lowest.pos+1].low > lowest.low )  && ( quotesL[lowest.pos+2].low > lowest.low))
					touch20MA = Utility.getHigh( quotesL, lowest.pos+1, lowest.pos+2 ).pos;
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

				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;

				trade.setFirstBreakOutPos( firstBreakOut.pos, firstBreakOut.time);
				trade.setFirstBreakOutStartPos(start, quotesL[start].time);
				trade.setTouch20MAPos(touch20MA, quotesL[touch20MA].time);
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.FIXED_STOP = FIXED_STOP;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}


	

	
	
	public Trade checkBreakOut4a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//QuoteData[] quotesS = smallTimeFrameTraderManager.getQuoteData();
		//int lastbarS = quotesS.length - 1;
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		//if (( quotesL[start].low > ema20L[start]) || ( quotesL[start].high < ema20L[start]))
		//	start--;
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			fine("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			/*
			int checkRunUp = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, lastUpPosStart, 12);
			int checkRunDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastUpPosStart, 12);
			if ( checkRunUp > checkRunDown )
			{
				for ( int i = checkRunUp; i < lastbarL -2; i++)
					if ((quotesL[i].high < ema20L[i]) && (quotesL[i+1].high < ema20L[i+1]))
						return null;
			}*/

			//prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastUpPosStart, 2);
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 2);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
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
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.setFirstBreakOutPos( firstBreakOut.pos, firstBreakOut.time);
				trade.setFirstBreakOutStartPos(start, quotesL[start].time);
				trade.setTouch20MAPos(touch20MA, quotesL[touch20MA].time);
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.FIXED_STOP = FIXED_STOP;

				warning("break UP detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				
				return trade;
			}
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			fine("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			/*
			int checkRunUp = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 12);
			int checkRunDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 12);
			if ( checkRunUp < checkRunDown )
			{
				for ( int i = checkRunDown; i < lastbarL -2; i++)
					if ((quotesL[i].low > ema20L[i]) && (quotesL[i+1].low > ema20L[i+1]))
						return null;
			}*/

			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			//prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
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

				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
				trade.status = Constants.STATUS_DETECTED;

				trade.setFirstBreakOutPos( firstBreakOut.pos, firstBreakOut.time);
				trade.setFirstBreakOutStartPos(start, quotesL[start].time);
				trade.setTouch20MAPos(touch20MA, quotesL[touch20MA].time);
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.FIXED_STOP = FIXED_STOP;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}


	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public Trade checkBreakOut2(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		if (( quotesL[start].low > ema20L[start]) || ( quotesL[start].high < ema20L[start]))
			start--;
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 1);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 1);
	
		if ( lastUpPos > lastDownPos)
		{	
			start = lastUpPos;
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPos, 2);
			if ( prevDownPos == Constants.NOT_FOUND )
				return null;
			
			start = prevDownPos;
			while ( quotesL[start].low <= ema20L[start] )
				start++;

			//while ( quotesL[start].high > ema20L[start] )  // make sure all highs counted in , not just the break out ones
			//	start--;
			//start = start+1;

			// now it is the first up
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL; i++)
			{
				if ( quotesL[i].high > quotesL[lastbarL].high)
					return null;
				if ( quotesL[i].low < ema20L[i])
					touch20MA++;
			}
			if ( touch20MA == 0 )
				return null;

			double high = Utility.getHigh( quotesL, prevDownPos, lastbarL-1).high;//
			if ( high > quotesL[lastbarL].high)  //
				return null;  //
			
			
			QuoteData pullback = Utility.getLow( quotesL, start+1, lastbarL-1);
			
			//if (( quotesL[lastbarL].high - pullback.low) > FIXED_STOP * 1.5 * PIP_SIZE )
			//	return null;
			if (( high - pullback.low) > FIXED_STOP * 1.5 * PIP_SIZE )
				return null;
			
			
			logger.info(symbol + " " + CHART + " " + " break up detected at " + quotesL[lastbarL].time + " firstPushUp: " + quotesL[start].time + " pullback: " + pullback.time );
			
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
			trade.detectTime = quotesL[lastbarL].time;
			trade.pushStartL = start;
			trade.pullBackPos = pullback.pos;
			trade.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(trade.type, trade.pullBackPos))
				return null;
			
			trade.triggerPos = lastbarL;
			trade.stop = adjustPrice( pullback.low, Constants.ADJUST_TYPE_DOWN);
			enterBuyPosition( Utility.getHigh( quotesL, start+1, lastbarL-1).high, false);
			return trade;
		}	
		else if ( lastDownPos > lastUpPos )
		{	
			start = lastDownPos;
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPos, 2);
			if ( prevUpPos == Constants.NOT_FOUND )
				return null;
			
			start = prevUpPos;
			while ( quotesL[start].high >= ema20L[start] )
				start++;

			//while ( quotesL[start].low < ema20L[start] )  // make sure all lows counted in , not just the break out ones
			//	start--;
			//start = start+1;
			
			// now it is the first up
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL; i++)
			{
				if ( quotesL[i].low < quotesL[lastbarL].low)
					return null;
				if ( quotesL[i].high > ema20L[i])
					touch20MA++;
			}
			if ( touch20MA == 0 )
				return null;

			double low = Utility.getLow( quotesL, prevUpPos, lastbarL-1).low;//
			if ( low < quotesL[lastbarL].low)  //
				return null;  //

			QuoteData pullback = Utility.getHigh( quotesL, start+1, lastbarL-1);
			
			//if (( pullback.high - quotesL[lastbarL].low ) > FIXED_STOP * 1.5 * PIP_SIZE )
			//	return null;
			if (( pullback.high - low ) > FIXED_STOP * 1.5 * PIP_SIZE )
				return null;
			
			
			logger.info(symbol + " " + CHART + " " + " break up detected at " + quotesL[lastbarL].time + " firstPushDown: " + quotesL[start].time + " pullback: " + pullback.time );
			
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
			trade.detectTime = quotesL[lastbarL].time;
			trade.pushStartL = start;
			trade.pullBackPos = pullback.pos;
			trade.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(trade.type, trade.pullBackPos))
				return null;
			
			trade.triggerPos = lastbarL;
			trade.stop = adjustPrice( pullback.high, Constants.ADJUST_TYPE_UP);
			enterSellPosition( Utility.getLow( quotesL, start+1, lastbarL-1).low, false);
			return trade;
		}	
		
		return null;
	}


	public Trade checkUSDJPYBreakOutBreakOut(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		//int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		double[] ema100L = Indicator.calculateEMA(quotesL, 100);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		
		int start = lastbarL;
		
		//if ("20100318  08:59:00".equals(data.time))
		//	System.out.println("here");
		
		//if ("20100318  09:00:00".equals(data.time))
			//System.out.println("here");
		while ( true )
		{
			lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 1);
			lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 1);
	
			if ( lastUpPos > lastDownPos)
			{	
				start = lastUpPos;
				while (( quotesL[start].low > ema20L[start]) && ( start > 0))
					start--;
				prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 2);
				prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 2);
				if ( prevDownPos > prevUpPos )
				{
					direction = Constants.DIRECTION_UP;
					break;
				}
			}	
			else if ( lastDownPos > lastUpPos)
			{	
				start = lastDownPos;
				while (( quotesL[start].high < ema20L[start]) && ( start > 0 ))
					start--;
				prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, start, 2);
				prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 2);
				if ( prevUpPos > prevDownPos)
				{
					direction = Constants.DIRECTION_DOWN;
					break;
				}
			}	
			break;
		}
			
			
		//if (lastUpPos > lastDownPos)
		//	direction = Constants.DIRECTION_UP;
		//else if (lastDownPos > lastUpPos)
		//	direction = Constants.DIRECTION_DOWN;
		
		//if ( direction == Constants.DIRECTION_UP )
		//	System.out.println("last directin up detected at " + quotesL[lastbarL].time);
		//if ( direction == Constants.DIRECTION_DOWN )
			//System.out.println("last directin down detected at " + quotesL[lastDownPos].time);
		
		if (direction == Constants.DIRECTION_UP) 
		{
			int startL = lastUpPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			QuoteData firstPushUp = Utility.getHigh(quotesL, startL, lastUpPos);
			QuoteData firstPullBack = Utility.getLow( quotesL, firstPushUp.pos, lastbarL-1);
			if ( firstPullBack.low > ema20L[firstPullBack.pos])
				return  null;
			
			logger.info(symbol + " " + CHART + " " + " first push up is " + firstPushUp.time + " first pull back is " + firstPullBack.time );
			
			// 3. check if the first push is not too far
			if ( firstPushUp.high - firstPullBack.low > FIXED_STOP * 1.5 * PIP_SIZE )
				return  null;
			logger.info(symbol + " " + CHART + " " + data.time + " push < 1.5 pip size" );
			
			// 4. check if the first breakout
			if ( quotesL[lastbarL].high < firstPushUp.high )
				return null; 
			logger.info(symbol + " " + CHART + " " + " first higher than push is " + quotesL[lastbarL].time );
			for ( int i = firstPushUp.pos+1; i < lastbarL; i++)
			{
				if ( quotesL[i].high > firstPushUp.high )
					return null;
			}
			
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
			trade.detectTime = quotesL[lastbarL].time;
			trade.pushStartL = firstPushUp.pos;
			trade.pullBackPos = firstPullBack.pos;
			trade.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(trade.type, firstPullBack.pos))
				return null;
			
			logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + firstPushUp.time );
			trade.triggerPos = lastbarL;
			trade.stop = adjustPrice( firstPullBack.low, Constants.ADJUST_TYPE_DOWN);
			enterBuyPosition(firstPushUp.high, false);
			//enterLimitPosition1(Constants.ACTION_BUY, POSITION_SIZE, quotes[phl.prePos].high - 10 * PIP_SIZE);
			return trade;
		}
		else if (direction == Constants.DIRECTION_DOWN)
		{
			// 1. Find first touch 20MA
			//boolean touched20MA = false;
			//for ( int i = lastDownPos+1; i <= lastbarL; i++)
			//	if (quotesL[i].high > ema20L[i])
			//		touched20MA = true;
			
			//if (touched20MA == false)
			//	return null;
			/*
			boolean allowed = false;
			if (ema50L[lastbarL] < ema100L[lastbarL])
			{
				start = Pattern.findLastMACrossDown(ema50L, ema100L);
				if ( start != Constants.NOT_FOUND )
				for ( int i = lastbarL; i > start; i--)
				{	
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, start - 10, i );
					if ( phls != null )
					{
						if ( phls.length >= 2 )
						{
							int touch20MAcount = 0;
							for ( int j = 0; j < phls.length; j++)
							{
								if (phls[j].pullBack.high > ema50L[phls[j].pullBack.pos])
									touch20MAcount++;
							}
						
							if ( touch20MAcount >= 2 )
							{	
								allowed = true;
								break;
							}
						}
						break;
					}
				}

				if ( allowed == false )
					return null;
			}*/
				
			int startL = lastDownPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			QuoteData firstPushDown = Utility.getLow(quotesL, startL, lastDownPos);
			QuoteData firstPullBack = Utility.getHigh( quotesL, firstPushDown.pos, lastbarL-1);
			if ( firstPullBack.high < ema20L[firstPullBack.pos])
				return  null;
			
			// 2. check if prevPos is down
			//int prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL-1, 2);
			//int prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL-1, 2);
			//if ( prevDownPos > prevUpPos )
			//	return null;
			logger.info(symbol + " " + CHART + " " + " first push down is " + firstPushDown.time + " first pull back is " + firstPullBack.time );
			
			// 3. check if the first push is not too far
			if ( firstPullBack.high - firstPushDown.low > FIXED_STOP * 1.5 * PIP_SIZE )
				return  null;
			logger.info(symbol + " " + CHART + " " + data.time + " push < 1.5 pip size" );
			
			// 4. check if the first breakout
			if ( quotesL[lastbarL].low > firstPushDown.low )
				return null; 
			logger.info(symbol + " " + CHART + " " + " first lower than push is " + quotesL[lastbarL].time );
			for ( int i = firstPushDown.pos+1; i < lastbarL; i++)
			{
				if ( quotesL[i].low < firstPushDown.low )
					return null;
			}
			
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
			trade.detectTime = quotesL[lastbarL].time;
			trade.pushStartL = firstPushDown.pos;
			trade.pullBackPos = firstPullBack.pos;
			trade.POSITION_SIZE = POSITION_SIZE;
			if (prevTradeExist(trade.type, firstPullBack.pos))
				return null;
			
			logger.warning(symbol + " " + CHART + " " + " buy detected 1 " + data.time + " push start:" + firstPushDown.time );
			trade.triggerPos = lastbarL;
			trade.stop = adjustPrice( firstPullBack.high, Constants.ADJUST_TYPE_UP);
			enterSellPosition(firstPushDown.low, false);
			//enterLimitPosition1(Constants.ACTION_BUY, POSITION_SIZE, quotes[phl.prePos].high - 10 * PIP_SIZE);
			return trade;
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
			//if (Constants.TRADE_EUR.equals(trade.type))
			//trackHigherHighLowerLowEntry(data, quotesL);
			
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				trackPullBackTradeSell( data, price);
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				trackPullBackTradeBuy( data, price );
			}
			
		}
	}

	void enterSellPosition(QuoteData data, int lastbar, int lastbarL)
	{
		logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
		trade.price = data.close;
		trade.entryPrice = data.close;
		// trade.triggerHighLow = Utility.getHigh( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.entryTime = data.time;
		trade.entryPos = lastbar;
		trade.entryPosL = lastbarL;
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
		trade.price = data.close;
		trade.entryPrice = data.close;
		// trade.triggerHighLow = Utility.getLow( quotes, detectPos, lastbar);
		trade.status = Constants.STATUS_PLACED;
		trade.position = Constants.POSITION_LONG;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.entryTime = data.time;
		trade.entryPos = lastbar;
		trade.entryPosL = lastbarL;
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
		Object[] quotes = getQuoteData();
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
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

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
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

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

	public void trackRSCTradeEntry2(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			// if (((QuoteData)quotes[lastbar]).low < trade.rscEntryPrice )
			if (((QuoteData) quotes[lastbar]).high > ((QuoteData) quotes[lastbar - 1]).high)
			{
				// place order
				logger.warning(symbol + " " + CHART + " place market sell order at " + data.time);
				trade.price = ((QuoteData) quotes[lastbar - 1]).low;
				trade.entryPrice = trade.price;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				// trade.stop = Utility.getHigh(quotes, trade.detectPos,
				// lastbar).high;
				trade.stop = trade.price + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				trade.profitStartingPointing = Utility.getHigh(quotes, trade.detectPos, lastbar).pos;
				return;

			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			// if (((QuoteData)quotes[lastbar]).high > trade.rscEntryPrice )
			if (((QuoteData) quotes[lastbar]).low < ((QuoteData) quotes[lastbar - 1]).low)
			{
				logger.warning(symbol + " " + CHART + " place market buy order");
				trade.price = data.close;// ((QuoteData)quotes[lastbar-1]).high;
				trade.entryPrice = data.close;
				trade.position = Constants.POSITION_LONG;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.status = Constants.STATUS_PLACED;

				// trade.stop = Utility.getLow(quotes, trade.detectPos,
				// lastbar).low;
				trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				trade.profitStartingPointing = Utility.getLow(quotes, trade.detectPos, lastbar).pos;
				return;

			}
		}
	}


	public void trackNormalPullBackEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " + trade.detectTime);
		Object[] quotes = getQuoteData();
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
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

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

				// trade.stop = Utility.getHigh(quotes, trade.detectPos,
				// lastbar).high;
				trade.stop = trade.price + 20 * PIP_SIZE;
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
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				trade.entryTimeL = ((QuoteData) quotesL[lastbarL]).time;

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

				// trade.stop = Utility.getLow(quotes, trade.detectPos,
				// lastbar).low;
				trade.stop = trade.price - 20 * PIP_SIZE;
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
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		double entryPrice = trade.entryPrice;

		// Use 15 minute chart to verify pull back is within range
		int start15 = Utility.findPositionByHour(quotes, quotesL[start].time, Constants.FRONT_TO_BACK );
		int firstTouch = Utility.findFirstPriceLow(quotes, start15, entryPrice);
		
		if ((firstTouch != Constants.NOT_FOUND))
		{
			QuoteData highAfterFirstBreakOut15 = Utility.getHigh( quotes, firstTouch+1, lastbar );
			trade.pullBackPos = highAfterFirstBreakOut15.pos;
			if (( highAfterFirstBreakOut15 != null ) && ((highAfterFirstBreakOut15.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
			{
				double diverage = (highAfterFirstBreakOut15.high - entryPrice)/PIP_SIZE;
				if ( diverage > 1.5 * FIXED_STOP )
				{
					trade.largeDiverageFlag = true;
					//info("entry sell diverage low is" + highAfterFirstBreakOut15.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
					//removeTrade();   
					//return;
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
				// check if this is the first pull back of a large trend move
				System.out.println(symbol + " Sell Entry detected at " + quotesL[lastbarL].time );
				BigTrend bt = determineBigTrend( quotesL);
				System.out.print(bt.toString( quotesL) );
				System.out.println("Large Diverage:" + trade.largeDiverageFlag );
				
				// reverse only pullback <= 3 and no big runups
				
				if (( bt.direction == Constants.DIRECTION_UP ) && ( bt. maTouches.length <= 3 ))
				{
					boolean reverse = true;
					int onTheOtherSide = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
					int onTheOtherSideStart = onTheOtherSide - 1;
					while ( !(quotesL[onTheOtherSideStart].high < ema20L[onTheOtherSideStart]))
							onTheOtherSideStart--;
					QuoteData highestPoint = Utility.getHigh( quotesL,onTheOtherSideStart,onTheOtherSide+1 );
				
					
					Push push = Utility.findLargestConsectiveDownBars(quotesL, highestPoint.pos, lastbarL);
					if ( push != null )
					{
						int numOfPush = push.pushEnd - push.pushStart + 1;
						double totalPushSize = quotesL[push.pushStart].open - quotesL[push.pushEnd].close;
						double avgPushSize = totalPushSize/numOfPush;
						double totalAverageDownSizeBar = Utility.averageDownSizeBar(quotesL);
						System.out.println("Largest Consective Push:" + quotesL[push.pushStart].time + " " + quotesL[push.pushStart].high + " Large PushEnd:" + quotesL[push.pushEnd].time + " " + quotesL[push.pushEnd].low +
					       " total push size:" +  totalPushSize );
						System.out.println("No Of Push:" + numOfPush +  " Avg Largest Size:" + avgPushSize);
						System.out.println("Avg Down Bar Size:" + totalAverageDownSizeBar);
						
						if ((( numOfPush == 2 ) && (avgPushSize > 1.5 * totalAverageDownSizeBar)) || (( numOfPush == 3 ) && (avgPushSize > 1.25 * totalAverageDownSizeBar)) ||
						    (( numOfPush == 4 ) && (avgPushSize > 1.1 * totalAverageDownSizeBar)) || (( numOfPush >= 5 ) && (avgPushSize > totalAverageDownSizeBar)))
						{
							warning("large revseral bar detected");
							reverse = false;
							//warning("big up bar detect, do not reverse");
							//enterMarketPosition(entryPrice);
							//return;
						}
					}

					
					//for ( int s = 0; s <=1; s++ )  // to prevet a big up start bar screw up the check
					{	
						//PushList pl = Pattern.findPast2Lows2(quotesL, highestPoint.pos, lastbarL);
						PushList pl = Pattern.findMostPast2Low2( quotesL, onTheOtherSideStart,onTheOtherSide+1, lastbarL);
						PushHighLow[] phls = pl.phls;
						if ( phls != null )
						{
							System.out.println(pl.toString(quotesL));
							for ( int i = 0; i < phls.length; i++)
								if ( phls[i].pushSize > 1.8 * FIXED_STOP * PIP_SIZE)
									reverse = false;
						}
					}
						
						//if ((( phls.length >= 2 ) && (phls[0].pullBackRatio > 0.618 )) ||
						//    (( phls.length >= 3 ) && ((phls[0].pullBackRatio > 0.618 ) || (phls[1].pullBackRatio > 0.618 ))))
					if ( reverse == true )
				    {
						trade.type = Constants.TRADE_CNT;
						trade.action = Constants.ACTION_BUY;
						enterMarketPosition(entryPrice);
						return;
						
					}

				}
				
				removeTrade();   
				return;
				
				/*
				if (( trade.largeDiverageFlag == true ) /*&& (bt.direction == Constants.DIRECTION_UP))
				{
					System.out.println(symbol + " large diverage + against trend, trade removed");
					removeTrade();   
					return;
					
				}
				
				warning("break DOWN trade entered at " + quotes[lastbar].time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
				if ( data != null )
					warning(data.time + " " + data.high + " " + data.low );
				else
					warning("last tick:" + price);

				if ( MODE == Constants.REAL_MODE )
				{	
					if (((( data.timeInMillSec - firstRealTime ) < 60*60000L ) && (Math.abs( triggerPrice - entryPrice) > 5  * PIP_SIZE ))
						||  (Math.abs( triggerPrice - entryPrice) > 7  * PIP_SIZE ))
					{
						warning("Entry missed, set limit order of " + trade.entryPrice);
						entryPrice = adjustPrice( entryPrice, Constants.ADJUST_TYPE_UP);
						trade.openOrderPlacedTimeInMill = currQuoteData.timeInMillSec;
						trade.openOrderDurationInHour = 3;
						enterLimitPosition1(entryPrice); 
						return;
					}
				}
				
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);*/
				
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


	
	public void trackPullBackTradeBuy(QuoteData data, double price )
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		double entryPrice = trade.entryPrice;

		int start15 = Utility.findPositionByHour(quotes, quotesL[start].time, Constants.FRONT_TO_BACK );
		int firstTouch = Utility.findFirstPriceHigh(quotes, start15, entryPrice);
		
		if ((firstTouch != Constants.NOT_FOUND))
		{
			QuoteData lowAfterFirstBreakOut15 = Utility.getLow( quotes, firstTouch+1, lastbar );
			if ( lowAfterFirstBreakOut15 != null )
			{
				double diverage = (entryPrice - lowAfterFirstBreakOut15.low)/PIP_SIZE;
				if ( diverage > 1.5 * FIXED_STOP )
				{
					trade.largeDiverageFlag = true;
					//info("entry buy diverage low is" + lowAfterFirstBreakOut15.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
					//removeTrade();
					//return;
				}
			}
		}
		
		
		
		double triggerPrice = 0;
		if ( data != null )
			triggerPrice = data.high;
		else if ( price != 0 )
			triggerPrice = price;

		if ( market_order == true )
		{
			if (triggerPrice > entryPrice) 
			{
				// check if this is the first pull back of a large trend move
				System.out.println(symbol + " Buy Entry detected at " + quotesL[lastbarL].time );
				BigTrend bt = determineBigTrend( quotesL);
				System.out.print(bt.toString( quotesL) );
				System.out.println("Large Diverage:" + trade.largeDiverageFlag );

				if (( bt.direction == Constants.DIRECTION_DOWN ) && ( bt.maTouches.length <= 3) )
				{
					boolean reverse = true;
					int onTheOtherSide = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
					int onTheOtherSideStart = onTheOtherSide - 1;
					while ( !(quotesL[onTheOtherSideStart].low > ema20L[onTheOtherSideStart]))
							onTheOtherSideStart--;
					QuoteData lowestPoint = Utility.getLow( quotesL,onTheOtherSideStart,onTheOtherSide+1 );

					Push push = Utility.findLargestConsectiveUpBars(quotesL, lowestPoint.pos, lastbarL);
					if ( push != null )
					{
						int numOfPush = push.pushEnd - push.pushStart + 1;
						double totalPushSize = quotesL[push.pushEnd].close - quotesL[push.pushStart].open;
						double avgPushSize = totalPushSize/numOfPush;
						double totalAverageUpSizeBar = Utility.averageUpSizeBar(quotesL);
						System.out.println("Largest Consective Push:" + quotesL[push.pushStart].time + " " + quotesL[push.pushStart].low + " Large PushEnd:" + quotesL[push.pushEnd].time + " " + quotesL[push.pushEnd].high +
					       " total push size:" +  totalPushSize );
						System.out.println("No Of Push:" + numOfPush +  " Avg Largest Size:" + avgPushSize);
						System.out.println("Avg Up Bar Size:" + totalAverageUpSizeBar);
						
						if ((( numOfPush == 2 ) && (avgPushSize > 1.5 * totalAverageUpSizeBar)) || (( numOfPush == 3 ) && (avgPushSize > 1.25 * totalAverageUpSizeBar)) ||
						    (( numOfPush >= 4 ) && (avgPushSize > 1.1 * totalAverageUpSizeBar)) /*|| (( numOfPush >= 5 ) && (avgPushSize > totalAverageUpSizeBar))*/)
						{
							warning("large revseral bar detected");
							reverse = false;
							//warning("big up bar detect, do not reverse");
							//enterMarketPosition(entryPrice);
							//return;
						}
					}
							
					
//					for ( int s = 0; s <=1; s++ )  // to prevet a big down start bar screw up the check
					{	
//						PushList pl = Pattern.findPast2Highs2(quotesL, lowestPoint.pos+s, lastbarL);
						PushList pl = Pattern.findMostPast2Highs2( quotesL, onTheOtherSideStart,onTheOtherSide+1, lastbarL);
						PushHighLow[] phls = pl.phls;
						if ( phls != null )
						{
							System.out.println(pl.toString(quotesL));
							for ( int i = 0; i < phls.length; i++)
								if ( phls[i].pushSize > 1.8 * FIXED_STOP * PIP_SIZE)
									reverse = false;
						}
					}
						//if ((( phls.length >= 2 ) && (phls[0].pullBackRatio > 0.618 )) ||
						//    (( phls.length >= 3 ) && ((phls[0].pullBackRatio > 0.618 ) || (phls[1].pullBackRatio > 0.618 ))))
					if ( reverse == true )
				    {
						trade.type = Constants.TRADE_CNT;
						trade.action = Constants.ACTION_SELL;
						enterMarketPosition(entryPrice);
						return;
				    }	
						
				}
			
				removeTrade();   
				return;
				
/*
				// Rule 1.
				if (( trade.largeDiverageFlag == true )/* && (bt.direction == Constants.DIRECTION_DOWN)/)
				{
					System.out.println(symbol + " large diverage + against trend, trade removed");
					removeTrade();   
					return;
				}
				
				// Rule 2.
				/*
				Push lastPush = bt.getLastPush(quotesL);
				System.out.println("last push: " + quotesL[lastPush.pushStart].time + " " + quotesL[lastPush.pushEnd].time);
				int last2MaBelow = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, start, 2);
				int prev2MaAbove = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, last2MaBelow, 2);
				while ( quotesL[prev2MaAbove].high > ema20L[prev2MaAbove])
					prev2MaAbove--;
				QuoteData pushStart = Utility.getHigh( quotesL, prev2MaAbove, start);
				QuoteData pushEnd = Utility.getLow( quotesL, pushStart.pos, start);
				System.out.println("push start:" + pushStart.time + " push end:" + pushEnd.time);
				double pushDist = pushStart.high - pushEnd.low;
				double pullBackDist = trade.entryPrice - pushEnd.low;
				
				if (( pushDist > 2 * FIXED_STOP * PIP_SIZE ) && ( pullBackDist/pushDist < 0.5))
				{
					System.out.println(symbol + " small poll back in a large trend");
					removeTrade();   
					return;
				}*/
/*
			
				warning("break UP trade entered at " + quotes[lastbar].time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
				if ( data != null )
					warning(data.time + " " + data.high + " " + data.low );
				else
					warning("last tick:" + price);
					
				if ( MODE == Constants.REAL_MODE )
				{	
					if (((( data.timeInMillSec - firstRealTime ) < 60*60000L ) && (Math.abs( triggerPrice - entryPrice) > 5  * PIP_SIZE ))
						||  (Math.abs( triggerPrice - entryPrice) > 7  * PIP_SIZE ))
					{
						warning("Entry missed, set limit order of " + trade.entryPrice);
						entryPrice = adjustPrice( entryPrice, Constants.ADJUST_TYPE_DOWN);
						trade.openOrderPlacedTimeInMill = currQuoteData.timeInMillSec;
						trade.openOrderDurationInHour = 3;
						enterLimitPosition1(entryPrice);
						return;
					}
				}
	
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);*/
			}
		}
		else
		{
			if ((triggerPrice >  entryPrice - 0.25 * FIXED_STOP * PIP_SIZE) && ( trade.stopMarketPlaced == false ))
			{
				trade.stopMarketStopPrice = adjustPrice(entryPrice, Constants.ADJUST_TYPE_DOWN);
				trade.stop = entryPrice - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
	
				Date time = new Date();
				trade.openOrderPlacedTimeInMill = time.getTime();
				trade.openOrderDurationInHour = 3;
				trade.openOrderExpireInMill = trade.openOrderPlacedTimeInMill + 3*60*60000L;
				time.setTime(trade.openOrderExpireInMill);
				String goodTill = OrderDateFormatter.format(time);
				enterStopMarketPosition(goodTill);

				trade.stopMarketPlaced = true;
				warning(data.time + " place stop market " + trade.action + " order, orderId:" + trade.stopMarketId + " stop triggerPrice:" + trade.stopMarketStopPrice + " good till:" + goodTill );
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
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED)))
				//exit123_new9_org( data );
				//exit123_new9b( data );
			    
				exit123_new9c( data );
					
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
				trade.entryTime = prevEntryTime;
				
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
				trade.entryTime = prevEntryTime;
			}
		}
		
	}

	

	
	public void exit123_new9c( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = smallTimeFrameTraderManager.getQuoteData();
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;

		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL <= tradeEntryPosL )
			return;

		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		int timePassed = lastbarL - tradeEntryPosL; 
		int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.3 ))
			{
				if ( quotes[lastbar].high > (lowestPointAfterEntry + trade.FIXED_STOP * PIP_SIZE ))
				{
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * trade.FIXED_STOP)
							reversequalified = false;
					}

					BigTrend bt = determineBigTrend( quotesL);
					if ( bt.direction == Constants.DIRECTION_DOWN )
						reversequalified = false;
					
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
				if (( trade.type.equals(Constants.TRADE_EUR) && ((trade.entryPrice - quotes[lastbar].low) > 2 * trade.FIXED_STOP * PIP_SIZE)) ||
				    ( trade.type.equals(Constants.TRADE_CNT) && ((trade.entryPrice - quotes[lastbar].low) > 2 * trade.FIXED_STOP * PIP_SIZE)))		
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}

			/*
			if ( quotesL[lastbarL].close < quotesL[lastbarL].open) 
			{
				int bigmovestart = lastbarL-1;
				while ( quotesL[bigmovestart].close < quotesL[bigmovestart].open)
					bigmovestart--;
				
				double bigmove = (quotesL[bigmovestart+1].open - quotesL[lastbarL].close )/PIP_SIZE;
				if ( bigmove > 3 * FIXED_STOP)
				{
					if  ( bigmovestart+1 != trade.bigMoveStartPos )
					{	
						warning( "big move detedted at" + quotesL[lastbarL].time);
						trade.bigMoveStartPos = bigmovestart+1;
					}
				}
			}*/
/*
			if (!(( quotesL[lastbarL].high > quotesL[lastbarL-1].high) && ( quotesL[lastbarL].low > quotesL[lastbarL].low))) 
			{
				int bigmovestart = lastbarL-1;
				while ( !(( quotesL[bigmovestart].high > quotesL[bigmovestart-1].high) && ( quotesL[bigmovestart].low > quotesL[bigmovestart-1].low)))
					bigmovestart--;
				
				double bigmove = (quotesL[bigmovestart+1].open - quotesL[lastbarL].close )/PIP_SIZE;
				if ( bigmove > 3 * FIXED_STOP)
				{
					if  ( bigmovestart+1 != trade.bigMoveStartPos )
					{	
						warning( "big move detedted at" + quotesL[lastbarL].time);
						trade.bigMoveStartPos = bigmovestart+1;
					}
				}
			}
	*/		
			int pushStart = tradeEntryPosL;
			//if ( trade.bigMoveStartPos != 0 )
			//	pushStart = trade.bigMoveStartPos;q

			
			int wave2PtL = 0;
			maTouches = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
			if ( trade.type.equals(Constants.TRADE_EUR))
			{
				if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
				{
					wave2PtL =  maTouches[0].touchBegin;
					//info("Wave2 touch point is " + quotesL[wave2PtL].time);
				}
			}
			else if ( trade.type.equals(Constants.TRADE_CNT))
			{
				if ( maTouches.length > 1 )
				{
					if ( maTouches[1].touchBegin != 0 )
					{
						wave2PtL =  maTouches[1].touchBegin;
						//info("Wave2 touch point is " + quotesL[wave2PtL].time);
					}
				}
				else if ( maTouches.length > 0 ) 
				{
					if (( maTouches[0].lowEnd != 0 ) && ((  maTouches[0].lowEnd -  maTouches[0].lowBegin) >= 12))
					{
						wave2PtL =  maTouches[0].lowEnd + 1;
						//info("Wave2 touch point is " + quotesL[wave2PtL].time);
					}
				}
			}
			
			
			
			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, pushStart, lastbarL).phls;
			PushHighLow phl = null;
			if ((phls != null) && (phls.length >= 1 ))
			{
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
						if ( trade.bigMoveStartPos != 0 )
						{
							if ( positive > 0 )
							{	
								warning(data.time + " take profit on 2.0 & MACD diverage");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
							else if ( numOfPush > 3 )
							{
								warning(data.time + " take profit on 2.0 and 3 push");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
						else 
						{
							if ( wave2PtL != 0 )
							{
								warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						if ( positive > 0 )
						{	
							if ( trade.bigMoveStartPos != 0 )
							{
								if ( numOfPush > 3 )
								{
									warning(data.time + " take prift buy on MACD with pullback > 1.5");
									takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
									takeProfit( adjustPrice(triggerPrice - 30 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
								}
							}
							else
							{	
								if ( wave2PtL != 0 )
								{	
									warning(data.time + " take prift buy on MACD with pullback > 1.5");
									takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
									takeProfit( adjustPrice(triggerPrice - 30 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
								}
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

							
						if (( wave2PtL != 0 ) && (trade.stopAdjusted == false) && ( timePassed >= 24 ))
						{
							MATouch[] maTouches2 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
							MATouch[] maTouches1 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);
							//if ( maTouches2 != null )
							//	System.out.println("2  touch2 20MA " + maTouches2.length + " times");
							//if ( maTouches1 != null )
							//	System.out.println("1  touch2 20MA " + maTouches1.length + " times");
							// Exit Scenario 2:  disporportional pullback
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
				// do not move stop until it touch 20MA at least once
				if (( trade.stopAdjusted == false ) && ( trade.bigMoveStartPos == 0 ))
				{
					if (( wave2PtL != 0 ) && ( lastbarL > wave2PtL+1 ) && ( quotesL[lastbarL-1].high < ema20L[lastbarL-1]))
					{
						trade.wave2Pt = Utility.getHigh(quotesL, wave2PtL, lastbarL-1);
						double stop = adjustPrice(trade.wave2Pt.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{	
							info(data.time + " move initial stop to " + trade.stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
						
						trade.stopAdjusted = true;
					}
				}
				else
				{
					//phl = Pattern.findLastNLow(quotesL, tradeEntryPosL, lastbarL, 1);
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
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.3 ))
			{
				if ( quotes[lastbar].low <  (highestPointAfterEntry - trade.FIXED_STOP * PIP_SIZE ))
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * trade.FIXED_STOP)
							reversequalified = false;
					}

					BigTrend bt = determineBigTrend( quotesL);
					if ( bt.direction == Constants.DIRECTION_UP )
						reversequalified = false;

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
				if (( trade.type.equals(Constants.TRADE_EUR) && ((quotes[lastbar].high - trade.entryPrice) >= 2 * trade.FIXED_STOP * PIP_SIZE)) ||
				    ( trade.type.equals(Constants.TRADE_CNT) && ((quotes[lastbar].high - trade.entryPrice) >= 2 * trade.FIXED_STOP * PIP_SIZE)))		
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}
			
			
			/*
			if ( quotesL[lastbarL].close > quotesL[lastbarL].open)
			{
				int bigmovestart = lastbarL-1;
				while ( quotesL[bigmovestart].close > quotesL[bigmovestart].open)
					bigmovestart--;
				
				double bigmove = (quotesL[lastbarL].close - quotesL[bigmovestart+1].open)/PIP_SIZE;
				if ( bigmove > 3 * FIXED_STOP)
				{
					if  ( bigmovestart+1 != trade.bigMoveStartPos )
					{	
						warning( "big move detedted at" + quotesL[lastbarL].time);
						trade.bigMoveStartPos = bigmovestart+1;
					}
				}
			}*/
			
			
			int pushStart = tradeEntryPosL;
			if ( trade.bigMoveStartPos != 0 )
				pushStart = trade.bigMoveStartPos;
			

			int wave2PtL = 0;
			maTouches = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, pushStart, 2);
			if ( trade.type.equals(Constants.TRADE_EUR))
			{
				if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
				{
					wave2PtL =  maTouches[0].touchBegin;
//					info("Wave2 touch point is " + quotesL[wave2PtL].time);
				}
			}
			else if ( trade.type.equals(Constants.TRADE_CNT))
			{
				if (( maTouches.length > 1 ) )
				{
					if ( maTouches[1].touchBegin != 0 )
					{
						wave2PtL =  maTouches[1].touchBegin;
						//info("Wave2 touch point is " + quotesL[wave2PtL].time);
					}
				}
				else if ( maTouches.length > 0 )
				{
					if ( ( maTouches[0].highEnd != 0 ) && ((  maTouches[0].highEnd -  maTouches[0].highBegin) >= 12))
					{
						wave2PtL =  maTouches[0].highEnd + 1;
						//info("Wave2 touch point is " + quotesL[wave2PtL].time);
					}
				}
			}

			
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
						if ( trade.bigMoveStartPos != 0 )
						{
							if ( negatives > 0 )
							{	
								warning(data.time + " take profit on 2.0 & MACD diverage");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
							else if ( numOfPush > 3 )
							{
								warning(data.time + " take profit on 2.0 & MACD diverage");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
						else 
						{
							if ( wave2PtL != 0 )
							{
								warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
					}
					else if (  pullback  > 1.5 * FIXED_STOP * PIP_SIZE )
					{
						if ( negatives > 0 )
						{	
							if ( trade.bigMoveStartPos != 0 )
							{
								if ( numOfPush > 3 )
								{
									warning(data.time + " take prift buy on MACD with pullback > 1.5");
									takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
									takeProfit( adjustPrice(triggerPrice + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
								}
							}
							else
							{	
								if ( wave2PtL != 0 )
								{	
									warning(data.time + " take prift buy on MACD with pullback > 1.5");
									takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
									takeProfit( adjustPrice(triggerPrice + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
								}
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

							
						if (( wave2PtL != 0 ) && (trade.stopAdjusted == false) && ( timePassed >= 24 ))
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
				// do not move stop until it touch 20MA at least once
				if (( trade.stopAdjusted == false ) && ( trade.bigMoveStartPos == 0 ))
				{
					if (( wave2PtL != 0 ) && ( lastbarL > wave2PtL+1 ) && ( quotesL[lastbarL-1].low > ema20L[lastbarL-1]))
					{
						trade.wave2Pt = Utility.getLow(quotesL, wave2PtL, lastbarL-1);
						double stop = adjustPrice(trade.wave2Pt.low, Constants.ADJUST_TYPE_DOWN);
						if ( stop > trade.stop )
						{	
							info(data.time + " move initial stop to " + trade.stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
						
						trade.stopAdjusted = true;
					}
				}
				else
				{
					//phl = Pattern.findLastNHigh(quotesL, tradeEntryPosL, lastbarL, 1);
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
	}
	

	
	
	
	public void exit123_new9b( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = smallTimeFrameTraderManager.getQuoteData();
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;

		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL <= tradeEntryPosL )
			return;

		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		int timePassed = lastbarL - tradeEntryPosL; 
		int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.3 ))
			{
				if ( quotes[lastbar].high > (lowestPointAfterEntry + trade.FIXED_STOP * PIP_SIZE ))
				{
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * trade.FIXED_STOP)
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
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						
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

			if (trade.reach2FixedStop == false)
			{
				if (( trade.type.equals(Constants.TRADE_EUR) && ((trade.entryPrice - quotes[lastbar].low) > 2 * trade.FIXED_STOP * PIP_SIZE)) ||
				    ( trade.type.equals(Constants.TRADE_CNT) && ((trade.entryPrice - quotes[lastbar].low) > 2 * trade.FIXED_STOP * PIP_SIZE)))		
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}

			if ( trade.wave2PtL == 0 )
			{	
				maTouches = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
				//int touch1=0;
				//int touch2=0;
				if ( trade.type.equals(Constants.TRADE_EUR))
				{
					if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
					{
						trade.wave2PtL =  maTouches[0].touchBegin;
//						info("Wave2 touch point is " + quotesL[trade.wave2PtL].time);
					}
				}
				else if ( trade.type.equals(Constants.TRADE_CNT))
				{
					if (( maTouches.length > 1 ) && ( maTouches[1].touchBegin != 0 ))
					{
						trade.wave2PtL =  maTouches[1].touchBegin;
//						info("Wave2 touch point is " + quotesL[trade.wave2PtL].time);
					}
				}
			}
					
			/*
			if ( trade.wave2TouchPointL == 0 )
			{
				int leave20MA = tradeEntryPosL;
				while (( quotesL[leave20MA].high >= ema20L[leave20MA]) && (leave20MA < lastbarL))
					leave20MA++;
				
				if ( leave20MA < lastbarL )
				{	
					for ( int i = leave20MA; i <=lastbarL; i++)
						if (quotesL[i].high >= ema20L[leave20MA])
						{
							trade.wave2TouchPointL = i;
							info("Touch 20MA point is " + quotesL[i].time);
							break;
						}
				}
			}*/

			
			//int exitStartPosL = Utility.getHigh(quotesL, tradeEntryPosL-5, lastbarL-1).pos;
			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL).phls;
			if ((phls != null) && (phls.length >= 1 ))
			{
				PushHighLow phl = phls[0];
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
				
				if (/* (phls.length >= 2 ) && (trade.breakeven == true ) &&*/ ( trade.wave2PtL != 0 ))
				{
					if ( positive > 0 )
					{
						if ( pullback  < 1.5 * FIXED_STOP * PIP_SIZE )
						{	
							if 	(!takeProfit2_set())
							{	
								double targetPrice = adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								takeProfit2(targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift on MACD with pullback < 1.5 targetPrice:" + targetPrice );
							}
						}
						else
						{
							warning(data.time + " take prift sellon MACD with pullback > 1.5");
							takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
							takeProfit( adjustPrice(triggerPrice - 30 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
						}
					}
				}
				
				if (!exitTradePlaced() && ( trade.wave2PtL != 0 ) && ( pullback >  2 * FIXED_STOP * PIP_SIZE ))
				{
					warning(data.time + " take profit on 2.0");
					takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
				}
				
				if (!exitTradePlaced() && (trade.stopAdjusted == false) && ( trade.wave2PtL != 0 ) && ( timePassed >= 24 ))
				{
					MATouch[] maTouches2 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 2);
					MATouch[] maTouches1 = Pattern.findNextMATouchUpFromGoingDowns( quotesL, ema20L, tradeEntryPosL, 1);
					//if ( maTouches2 != null )
					//	System.out.println("2  touch2 20MA " + maTouches2.length + " times");
					//if ( maTouches1 != null )
					//	System.out.println("1  touch2 20MA " + maTouches1.length + " times");
					// Exit Scenario 2:  disporportional pullback
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
				}
			}
			
			
			// check on small time frame
			int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
			int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
			int tradeEntryPosS = Utility.getHigh( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
			
			PushHighLow phlS = Pattern.findLastNLow(quotesS, tradeEntryPosS, lastbarS, 1);
			if (phlS != null)
			{
				double pullBackDist =  phlS.pullBack.high - quotesS[phlS.prePos].low;

				// Exit Scenarios; exit at large profit
				if ( ( phlS.curPos - phlS.prePos) <= 48 )
				{	
					if (( profit > 200 ) && ( pullBackDist >  1.8 * FIXED_STOP * PIP_SIZE))
					{
						warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
						takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
					}
					else if ( pullBackDist >  2 * FIXED_STOP * PIP_SIZE )
					{
						warning(data.time + " take profit on 2.0");
						takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach2FixedStop == true)
			{	
				// do not move stop until it touch 20MA at least once
				if ( trade.stopAdjusted == false )  
				{
					if (( trade.wave2PtL != 0 ) && ( lastbarL > trade.wave2PtL+1 ) && ( quotesL[lastbarL-1].high < ema20L[lastbarL-1]))
					{
						trade.wave2Pt = Utility.getHigh(quotesL, trade.wave2PtL, lastbarL-1);
						double stop = adjustPrice(trade.wave2Pt.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{	
							info(data.time + " move initial stop to " + trade.stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
						
						trade.stopAdjusted = true;
					}
				}
				else
				{
					//PushHighLow phl = Pattern.findLastNLow(quotesL, trade.wave2Pt.pos, lastbarL, 1);
					PushHighLow phl = Pattern.findLastNLow(quotesL, tradeEntryPosL, lastbarL, 1);
					if (phl != null)
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
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.3 ))
			{
				if ( quotes[lastbar].low <  (highestPointAfterEntry - trade.FIXED_STOP * PIP_SIZE ))
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * trade.FIXED_STOP)
							reversequalified = false;
					}

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						
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
				if (( trade.type.equals(Constants.TRADE_EUR) && ((quotes[lastbar].high - trade.entryPrice) >= 2 * trade.FIXED_STOP * PIP_SIZE)) ||
				    ( trade.type.equals(Constants.TRADE_CNT) && ((quotes[lastbar].high - trade.entryPrice) >= 2 * trade.FIXED_STOP * PIP_SIZE)))		
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}
			
			
			if ( trade.wave2PtL == 0 )
			{	
				maTouches = Pattern.findNextMATouchDownsFromGoingUps( quotesL, ema20L, tradeEntryPosL, 2);
				//int touch1=0;
				//int touch2=0;
				if ( trade.type.equals(Constants.TRADE_EUR))
				{
					if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
					{
						trade.wave2PtL =  maTouches[0].touchBegin;
//						info("Wave2 touch point is " + quotesL[trade.wave2PtL].time);
					}
				}
				else if ( trade.type.equals(Constants.TRADE_CNT))
				{
					if (( maTouches.length > 1 ) && ( maTouches[1].touchBegin != 0 ))
					{
						trade.wave2PtL =  maTouches[1].touchBegin;
//						info("Wave2 touch point is " + quotesL[trade.wave2PtL].time);
					}
				}
			}


			/*
			QuoteData recentHigh = findLastSignificentHigh( quotesL, tradeEntryPosL);
			if (( recentHigh != null ) && ( data.high > recentHigh.high))
			{
				info("recent high is " + recentHigh.time + " " + recentHigh.high);
				AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize,  recentHigh.high);
				closePositionByMarket(trade.remainingPositionSize, recentHigh.high);
				return;
			}*/
			

			
			//int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, lastbarL-1).pos;
			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, tradeEntryPosL, lastbarL).phls;
			if ((phls != null) && (phls.length >=1))
			{
				PushHighLow phl = phls[0];
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

				if (/* (phls.length >= 2 ) && (trade.breakeven == true ) &&*/ ( trade.wave2PtL != 0 ))
				{
					if ( negatives > 0 )
					{	
						if ( pullback  < 1.5 * FIXED_STOP * PIP_SIZE ) 
						{
							if 	(!takeProfit2_set())
							{	
								double targetPrice = adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift buy on MACD with pullback < 1.5");
							}
						}
						else
						{
							warning(data.time + " take prift buy on MACD with pullback > 1.5");
							takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
							takeProfit( adjustPrice(triggerPrice + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
						}
					}
				}

				if (!exitTradePlaced() && ( trade.wave2PtL != 0 ) && ( pullback >  2 * FIXED_STOP * PIP_SIZE ))
				{
					warning(data.time + " take profit on 2.0");
					takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
				}

				if (!exitTradePlaced() && ( trade.wave2PtL != 0 ) && (trade.stopAdjusted == false) && ( timePassed >= 24 ))
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
				}
			}


			// check on small time frame
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
					if (( profit > 200 ) && ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE))
					{
						warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
						takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
					}
					if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
					{
						warning(data.time + " take profit > 200 on 2.0");
						takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
					}
				}
			}

			
			// move stop
			if (trade.reach2FixedStop == true)
			{	
				// do not move stop until it touch 20MA at least once
				if ( trade.stopAdjusted == false ) 
				{
					if (( trade.wave2PtL != 0 ) && ( lastbarL > trade.wave2PtL+1 ) && ( quotesL[lastbarL-1].low > ema20L[lastbarL-1]))
					{
						trade.wave2Pt = Utility.getLow(quotesL, trade.wave2PtL, lastbarL-1);
						double stop = adjustPrice(trade.wave2Pt.low, Constants.ADJUST_TYPE_DOWN);
						if ( stop > trade.stop )
						{	
							info(data.time + " move initial stop to " + trade.stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
						
						trade.stopAdjusted = true;
					}
				}
				else
				{
					//PushHighLow phl = Pattern.findLastNLow(quotesL, trade.wave2Pt.pos, lastbarL, 1);
					PushHighLow phl = Pattern.findLastNHigh(quotesL, tradeEntryPosL, lastbarL, 1);
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
	}
	
	
	
	
	
	
	


	
	
	public void exit123_new9_org( QuoteData data )
	{
		
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = smallTimeFrameTraderManager.getQuoteData();
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL <= tradeEntryPosL )
			return;

		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		int timePassed = lastbarL - trade.entryPosL; 
		int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.3 ))
			{
				if ( quotes[lastbar].high > (lowestPointAfterEntry + trade.FIXED_STOP * PIP_SIZE ))
				{
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);

					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL,touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * trade.FIXED_STOP)
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
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						
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

			if (trade.stopAdjusted == false)
			{
				if (( trade.type.equals(Constants.TRADE_EUR) && ((trade.entryPrice - quotes[lastbar].low) > 2 * trade.FIXED_STOP * PIP_SIZE)) ||
				    ( trade.type.equals(Constants.TRADE_CNT) && ((trade.entryPrice - quotes[lastbar].low) > 2 * trade.FIXED_STOP * PIP_SIZE)))		
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					//trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopId = placeStopLimitOrder(Constants.ACTION_BUY, trade.stop, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}


			int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
			int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
			int tradeEntryPosS = Utility.getHigh( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
			
			PushHighLow phlS = Pattern.findLastNLow(quotesS, tradeEntryPosS, lastbarS, 1);
			if (phlS != null)
			{
				double pullBackDist =  phlS.pullBack.high - quotesS[phlS.prePos].low;

				
				/*
				// detect large pullback after the first breakout
				int pushStartL = start-1;
				while (!(( quotesL[pushStartL].low < quotesL[pushStartL-1].low ) && ( quotesL[pushStartL].low < quotesL[pushStartL-2].low )))
					pushStartL--;
				double pullBackMark = firstBreakOut.high - 0.618 * ( firstBreakOut.high - quotesL[pushStartL].low);
				double pullBackLow = Utility.getLow(quotesL, firstBreakOut.pos, lastbarL).low;
				//info("pullbackCalculating Startpoint is " + quotesL[pushStartL].time + " pullback 0.618Mark:" + pullBackMark + " pullbackLow:" + pullBackLow );

				if ((( entryPrice - pullBackLow) > FIXED_STOP * PIP_SIZE ) && ( pullBackLow < pullBackMark)) 
				{
					info("lowest below 20MA > 0.618 stop size, trade removed");
					removeTrade();
					return;
				}*/

				
				
				
				// Exit Scenarios; exit at large profit
				if (( profit > 200 ) && ( pullBackDist >  1.5 * FIXED_STOP * PIP_SIZE))
				{
					warning("take profit > 100 on 15 gap is " + (phlS.curPos - phlS.prePos));
					takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
				}
				else if ( pullBackDist >  2 * FIXED_STOP * PIP_SIZE )
				{
					warning("take profit on 2.0");
					takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
				}
		/*		else if ((trade.stopAdjusted == false) && ( timePassed >= 24 ))
				{
					// Exit Scenario 2:  disporportional pullback
					double prevProfit = trade.entryPrice - quotesS[phlS.prePos].low;
					double avgProfit = prevProfit / ( lastbarS - tradeEntryPosS );
					if ( avgProfit > 0 )
					{	
						double avgPullBack = pullBackDist / ( lastbarS - phlS.prePos);
						//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
						if (( pullBackDist > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
						{
							System.out.println(data.time + " take profit on disporportional pull back");
							takeProfit( adjustPrice(quotesS[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
				}*/
			}

			if ( trade.touch20MAPosL == 0 )
			{
				int leave20MA = tradeEntryPosL;
				while (( quotesL[leave20MA].high >= ema20L[leave20MA]) && (leave20MA < lastbarL))
					leave20MA++;
				
				if ( leave20MA < lastbarL )
				{	
					for ( int i = leave20MA; i <=lastbarL; i++)
						if (quotesL[i].high >= ema20L[i])
						{
							trade.touch20MAPosL = i;
							warning("first touch 20MA detected at " + quotesL[trade.touch20MAPosL].time);
							break;
						}
				}
			}

			
			//int exitStartPosL = Utility.getHigh(quotesL, tradeEntryPosL-5, lastbarL-1).pos;
			PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL,/*exitStartPosL,*/ lastbarL).phls;
			if ((phls != null) && (phls.length >= 2 ))
			{
				PushHighLow phl = phls[0];
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
				//System.out.println("exit detected " + data.time);
				//if (data.time.equals("20110707  07:45:00"))
				//	System.out.println("exit detected " + data.time);
				
				
				double pullback = phl.pullBack.high - quotesL[phl.prePos].low;
				double triggerPrice = quotesL[phl.prePos].low;

				
				if ( (trade.stopAdjusted == true ) && ( trade.touch20MAPosL != 0 ))
				{
					if ( positive > 0 )
					{
						if ( pullback  < 1.5 * FIXED_STOP * PIP_SIZE )
						{	
							warning("take half prift big on < 1.5");
							takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
						}
						else
						{
							warning("take prift big on > 1.5");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
				}
				
				
				if (!exitTradePlaced() && (trade.stopAdjusted == false) && ( timePassed >= 24 ))
				{
					// Exit Scenario 2:  disporportional pullback
					double prevProfit = trade.entryPrice - quotesL[phl.prePos].low;
					double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
					if ( avgProfit > 0 )
					{	
						double avgPullBack = pullback / ( lastbarL - phl.prePos);
						//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
						
						if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
						//if (( pullback > FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
						//if ( pullback > FIXED_STOP * PIP_SIZE ) 
						{
							System.out.println(data.time + " take profit on disporportional pull back avgPullBack:" + avgPullBack + " avgProfit:" + avgProfit);
							takeProfit( adjustPrice(quotesL[phl.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
				}

			}
			
			
			// move stop
			// do not move stop until it touch 20MA at least once
			if ( (trade.stopAdjusted == true ) && ( trade.touch20MAPosL != 0 ))
			{
				if ((phls != null) && (phls.length > 0))
				{
					PushHighLow phl = phls[0];
					double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
					if ( stop < trade.stop )
					{
						System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						//trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						trade.stopId = placeStopLimitOrder(Constants.ACTION_BUY, trade.stop, trade.stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.3 ))
			{
				if ( quotes[lastbar].low <  (highestPointAfterEntry - trade.FIXED_STOP * PIP_SIZE ))
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
					int touch20MAPosL = trade.getTouch20MAPosL(quotesL);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotesL);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotesL,firstBreakOutStartL,touch20MAPosL-1).high;
						double low = Utility.getLow(quotesL,firstBreakOutStartL,touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * trade.FIXED_STOP)
							reversequalified = false;
					}

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					if ( reversequalified == false )
					{
						closePositionByMarket(trade.remainingPositionSize, reversePrice);
					}
					else
					{	
						logger.warning(symbol + " " + CHART + " reverse opportunity detected");
						int prevPosionSize = trade.remainingPositionSize;
						
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
			
			if (trade.stopAdjusted == false)
			{
				if (( trade.type.equals(Constants.TRADE_EUR) && ((quotes[lastbar].high - trade.entryPrice) >= 2 * trade.FIXED_STOP * PIP_SIZE)) ||
					( trade.type.equals(Constants.TRADE_CNT) && ((quotes[lastbar].high - trade.entryPrice) >= 2 * trade.FIXED_STOP * PIP_SIZE)))		
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					//trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopId = placeStopLimitOrder(Constants.ACTION_SELL, trade.stop, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}
			
			
			int tradeEntryPosS1 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.FRONT_TO_BACK);
			int tradeEntryPosS2 = Utility.findPositionByMinute( quotesS, trade.entryTime, Constants.BACK_TO_FRONT);
			int tradeEntryPosS = Utility.getLow( quotesS, tradeEntryPosS1,tradeEntryPosS2).pos;
			
			PushHighLow phlS = Pattern.findLastNHigh(quotesS, tradeEntryPosS, lastbarS, 1);
			if (phlS != null)
			{
				double pullBackDist =  quotesS[phlS.prePos].high - phlS.pullBack.low;
				
				// exit scenario1, large parfit
				if (( profit > 200 ) && ( pullBackDist > 1.5 * FIXED_STOP * PIP_SIZE))
				{
					warning("take profit > 100 on 15 gap is " + (phlS.curPos - phlS.prePos));
					takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
				}
				else if ( pullBackDist > 2 * FIXED_STOP * PIP_SIZE)
				{
					warning("take profit > 200 on 2.0");
					takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
				}
		/*		else if ((trade.stopAdjusted == false) && ( timePassed >= 24 ))
				{	
					// Exit Scenario 2:  disporportional pullback
					double prevProfit = quotesS[phlS.prePos].high - trade.entryPrice;
//					System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
					double avgProfit = prevProfit / ( lastbarS - tradeEntryPosS );
					if ( avgProfit > 0 )
					{	
						//System.out.println(data.time + " take profit on disporportional pull back");
						double avgPullBack = pullBackDist / ( lastbarS - phlS.prePos);
						System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
						if (( pullBackDist > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
						{
							System.out.println(data.time + " take profit on disporportional pull back");
							takeProfit( adjustPrice(quotesS[phlS.prePos].high, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
				}*/
				
			}
			
			if ( trade.touch20MAPosL == 0 )
			{
				int leave20MA = tradeEntryPosL;
				while (( quotesL[leave20MA].low <= ema20L[leave20MA]) && (leave20MA < lastbarL))
					leave20MA++;
				
				if (leave20MA < lastbarL)
				{
					for ( int i = leave20MA; i <=lastbarL; i++)
					if (quotesL[i].low <= ema20L[i])
					{
						trade.touch20MAPosL = i;
						warning("first touch 20MA detected at " + quotesL[trade.touch20MAPosL].time);
						break;
					}
				}
			}
			
			//int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, lastbarL-1).pos;
			PushHighLow[] phls = Pattern.findPast2Highs(quotesL, tradeEntryPosL, lastbarL).phls;
			if ((phls != null) && (phls.length >=2))
			{
				PushHighLow phl = phls[0];
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

				if ( (trade.stopAdjusted == true ) && ( trade.touch20MAPosL != 0 ))
				{
					if ( negatives > 0 )
					{	
						if ( pullback  < 1.5 * FIXED_STOP * PIP_SIZE ) 
						{
							warning("take half prift big on < 1.5");
							takeProfit2( adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), POSITION_SIZE/2 );
						}
						else
						{
							warning("take prift big on > 1.5");
							takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
					}
				}
				
				if (!exitTradePlaced() && (trade.stopAdjusted == false) && ( timePassed >= 24 ))
				{	
					// Exit Scenario 2:  disporportional pullback
					double prevProfit = quotesL[phl.prePos].high - trade.entryPrice;
					double avgProfit = prevProfit / ( lastbarL - tradeEntryPosL );
					if ( avgProfit > 0 )
					{	
						//System.out.println(data.time + " take profit on disporportional pull back");
						double avgPullBack = pullback / ( lastbarL - phl.prePos);
						//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
						if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
						//if (( pullback > FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
						//if ( pullback > FIXED_STOP * PIP_SIZE ) 
						{
							System.out.println(data.time + " take profit on disporportional pull back avgPullBack:" + avgPullBack + " avgProfit:" + avgProfit);
							takeProfit( adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
					}
				}

				
			}

			// move stop
			if ( (trade.stopAdjusted == true ) && ( trade.touch20MAPosL != 0 ))
			{	
				if ((phls != null) && (phls.length > 0))
				{	
					PushHighLow phl = phls[0];
	
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
					if ( stop > trade.stop )
					{
						cancelOrder(trade.stopId);
						trade.stop = stop;
						//trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						trade.stopId = placeStopLimitOrder(Constants.ACTION_SELL, trade.stop, trade.stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
				}
			}
		}
	}
	
	

	
	
	
	public void exit123_new4(int field, double currPrice)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		//int tradeEntryPos = Utility.findPositionByHour(quotes, trade.entryTime, 2 );

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

//		if (lastbarL < tradeEntryPosL + 2)
//			return;
		if (lastbarL <= tradeEntryPosL )
			return;


/*		Date now = new Date(data.timeInMillSec);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( now );
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);
		int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
		int minute=calendar.get(Calendar.MINUTE);
		boolean marketCloseSoon = false;
		if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
			marketCloseSoon = true;
*/	

//		String hr = data.time.substring(9,12).trim();
//		String min = data.time.substring(13,15);
//		int hour = new Integer(hr);
//		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  to be reviewed later
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.3 ))
			{
				if ( quotes[lastbar].high > (lowestPointAfterEntry + trade.FIXED_STOP * PIP_SIZE ))
				{
					logger.warning(symbol + " " + CHART + " reverse opportunity detected");
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					return;

				}
			}			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			if (trade.stopAdjusted == false)
			{
				/*
				if ((trade.entryPrice - data.low > trade.FIXED_STOP * PIP_SIZE) && ( trade.positionUpdated == false ))
				{
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, data.close);
					trade.positionSize += POSITION_SIZE;
					trade.remainingPositionSize += POSITION_SIZE;
					trade.positionUpdated = true;
				}*/

				if (trade.entryPrice - quotes[lastbar].low > 2*trade.FIXED_STOP * PIP_SIZE) 
				{
					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice - 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + quotes[lastbar].time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}
					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
			//		trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getHigh(quotesL, tradeEntryPosL-5, lastbarL-1).pos;
					double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
					int time = lastbarL - trade.entryPosL; 

					PushHighLow[] phls = Pattern.findPast1Lows(quotesL, exitStartPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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

						/*
						if ( profit < 50 )
						{
							//if (( negatives > 0 ) && (pullback > 1.5 * FIXED_STOP * PIP_SIZE ))
							//	takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							if ( positive > 0 )
							{	
								if ( pullback < 1.5 * FIXED_STOP * PIP_SIZE ) 
								{
									warning("take profit half profit at < 50");
									takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.positionSize/2 );
								}
								else
								{	
									warning("take profit < 50");
									takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								}
							}
						}
						else if (profit < 200)*/
						{
							if ( positive > 0 )
							{	
								if ( pullback < 1.5 * FIXED_STOP * PIP_SIZE ) 
								{
									warning("take profit half profit at < 100");
									takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.POSITION_SIZE/2 );
								}
								else
								{	
									warning("take profit < 100");
									takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								}
							}
						}/*
						else  // profit > 100
						{
							if (( positive > 0 ) || (pullback > 1.5 * FIXED_STOP * PIP_SIZE))
							{
								warning("take profit > 100");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}*/
					}
/*
					if ( profit > 200 )
					{
						PushHighLow phl15 = Pattern.findLastNLow(quotes, tradeEntryPos, lastbar, 1);
						if (phl15 != null)
						{
							if (( phl15.pullBack.high - quotes[phl15.prePos].low) > 1.5 * FIXED_STOP * PIP_SIZE)
							{
								warning("take profit > 100 on 15");
								takeProfit( adjustPrice(quotes[phl15.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								return;
							}
						}
					}
*/

					// move stops
					int gapRequired = 2;
					//if ( profit > 100 )
					//	gapRequired = 1;
					
					PushHighLow phl = Pattern.findLastNLow(quotesL, exitStartPosL, lastbarL, gapRequired);
					if (phl != null)
					{
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
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
					
					
					// add other profit taken 
					/* it works but not that great
					int prevPushLow = lastbarL;
					while ( quotesL[prevPushLow].close < quotesL[prevPushLow].open)
						prevPushLow--;
					
					if (prevPushLow < lastbarL )
					{	
						double highStart = quotesL[prevPushLow+1].high;
						if (( highStart - data.low  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//  to be reviewed later
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.3 ))
			{
				//if (( highestPointAfterEntry - data.low ) > trade.FIXED_STOP * PIP_SIZE )
				if ( quotes[lastbar].low <  (highestPointAfterEntry - trade.FIXED_STOP * PIP_SIZE ))
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " reverse opportunity detected");
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterSellPosition(data.close, false);
					//enterSellPosition(reversePrice, false);
					enterMarketPosition(reversePrice);

					/*
					int prevLowPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevLowStartPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, prevLowPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getLow( quotesL, prevLowStartPos, prevLowPos).high, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;*/
					return;
				}
			}

			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.stopAdjusted == false)
			{
				if (quotes[lastbar].high - trade.entryPrice > 2*trade.FIXED_STOP * PIP_SIZE)
				{

					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice + 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + quotes[lastbar].time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}

					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					//trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}

				// looking to actively close the position if the trend reverses
				/*
				int lastPosDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 1);
				
				if ( lastPosDown > tradeEntryPosL )
				{
					int touch20MAPos = -1;
					for ( int i = lastPosDown ; i < lastbarL; i++)
					{
						if ( quotesL[i].high > ema20L[i])
						{
							touch20MAPos = i;
							break;
						}
					}
					
					if ( touch20MAPos != -1 )
					{
						double lowest = Utility.getLow ( quotesL, tradeEntryPosL, touch20MAPos).low;
						if ( data.low < lowest )
						{
							AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);

							// enter new trade;
							createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
							trade.detectTime = quotesL[lastbarL].time;
							trade.pushStartL = lastPosDown;
							trade.pullBackPos = lastPosDown;//pullback.pos;
							trade.positionSize = POSITION_SIZE;
							
							trade.triggerPos = lastbarL;
							enterSellPosition( lowest);
							return;

						}
					}
				}*/
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, lastbarL-1).pos;
					double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
					int time = lastbarL - trade.entryPosL; 

					PushHighLow[] phls = Pattern.findPast1Highs(quotesL, exitStartPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
	
						/*
						if ( profit < 50 )
						{
							//if (( negatives > 0 ) && (pullback > 1.5 * FIXED_STOP * PIP_SIZE ))
							//	takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							if ( negatives > 0 )
							{	
								if ( pullback < 1.5 * FIXED_STOP * PIP_SIZE ) 
								{
									warning("take profit half profit at < 50");
									takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.positionSize/2 );
								}
								else
								{	
									warning("take profit < 50");
									takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								}
							}
						}
						else if (profit < 200)*/
						{
							if ( negatives > 0 )
							{	
								if ( pullback < 1.5 * FIXED_STOP * PIP_SIZE ) 
								{
									warning("take profit half profit at < 100");
									takeProfit2( adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.POSITION_SIZE/2 );
								}
								else
								{	
									warning("take profit < 100");
									takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								}
							}
						}/*
						else  // profit > 100
						{
							if (( negatives > 0 ) || (pullback > 1.5 * FIXED_STOP * PIP_SIZE))
							{
								warning("take profit > 100");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}*/
					}
					
					/*
					if ( profit > 200 )
					{
						PushHighLow phl15 = Pattern.findLastNHigh(quotes, tradeEntryPos, lastbar, 1);
						if (phl15 != null)
						{
							if (( quotes[phl15.prePos].high - phl15.pullBack.low ) > 1.5 * FIXED_STOP * PIP_SIZE)
							{
								warning("take profit > 100 on 15");
								takeProfit( adjustPrice(quotes[phl15.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								return;
							}
						}
					}*/
					
					// move stop
					int gapRequired = 2;
					//if ( profit > 100 )
						//gapRequired = 1;
					
					PushHighLow phl = Pattern.findLastNHigh(quotesL, exitStartPosL, lastbarL, gapRequired);
					if (phl != null)
					{
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
					/*

					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNHigh(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].high + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/

					
					// add other profit taken
					/* it works but not that great
					int prevPushHigh = lastbarL;
					while ( quotesL[prevPushHigh].close > quotesL[prevPushHigh].open)
						prevPushHigh--;
					
					if (prevPushHigh < lastbarL )
					{	
						double lowStart = quotesL[prevPushHigh+1].low;
						if (( data.high - lowStart  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.high, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/

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
		
		/*
		if ( trade.targetId2 != 0 )
		{
			cancelOrder(trade.targetId2);
			trade.targetId2 = 0;
		}*/

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
	

	
	
	public void exit123_new3(int field, double currPrice)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
//		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

//		if (lastbarL < tradeEntryPosL + 2)
//			return;
		if (lastbarL <= tradeEntryPosL )
			return;


/*		Date now = new Date(data.timeInMillSec);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( now );
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);
		int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
		int minute=calendar.get(Calendar.MINUTE);
*/	

//		String hr = data.time.substring(9,12).trim();
//		String min = data.time.substring(13,15);
//		int hour = new Integer(hr);
//		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  to be reviewed later
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.3 ))
			{
				if ( quotes[lastbar].high > (lowestPointAfterEntry + trade.FIXED_STOP * PIP_SIZE ))
				{
					logger.warning(symbol + " " + CHART + " reverse opportunity detected");
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					// reverse;
					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterBuyPosition(reversePrice, false);
					enterMarketPosition(reversePrice);
					/*
					int prevHighPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevHighStartPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevHighPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getHigh( quotesL, prevHighStartPos, prevHighPos).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;*/
					return;

				}
			}			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			if (trade.stopAdjusted == false)
			{
				/*
				if ((trade.entryPrice - data.low > trade.FIXED_STOP * PIP_SIZE) && ( trade.positionUpdated == false ))
				{
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, data.close);
					trade.positionSize += POSITION_SIZE;
					trade.remainingPositionSize += POSITION_SIZE;
					trade.positionUpdated = true;
				}*/

				if (trade.entryPrice - quotes[lastbar].low > 2*trade.FIXED_STOP * PIP_SIZE) 
				{
					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice - 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + quotes[lastbar].time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}
					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
			//		trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL).phls;
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
						
						//if ( positive > = 2 )
						/*
						if (( positive > 0  ) && ((( phl.pullBack.high - quotesL[phl.prePos].low)  > 1.5 * FIXED_STOP * PIP_SIZE)))
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
							
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).low/* - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							return;
						}*/
						
						if ( positive > 0 )
						{	
							if ((( phl.pullBack.high - quotesL[phl.prePos].low)  < 1.5 * FIXED_STOP * PIP_SIZE)) 
							{
								if ( !trade.targetPlaced2 )
								{	
									logger.warning(symbol + " " + CHART + " " + trade.action + " exit 1/2 postion on small MACD diverage " + quotes[lastbar].time);
									trade.targetPrice2 = adjustPrice((quotesL[phl.prePos]).low  - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.targetPos2 = POSITION_SIZE/2;
									trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
									logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + quotes[lastbar].time);
			
									trade.targetPlaced2 = true;
									return;
								}
							}
							else
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + quotes[lastbar].time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(( quotesL[phl.prePos]).low, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + quotes[lastbar].time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}

					
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
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
					
					/*
					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNLow(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].low - 20 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/
					
					
					// add other profit taken 
					/* it works but not that great
					int prevPushLow = lastbarL;
					while ( quotesL[prevPushLow].close < quotesL[prevPushLow].open)
						prevPushLow--;
					
					if (prevPushLow < lastbarL )
					{	
						double highStart = quotesL[prevPushLow+1].high;
						if (( highStart - data.low  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//  to be reviewed later
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.3 ))
			{
				//if (( highestPointAfterEntry - data.low ) > trade.FIXED_STOP * PIP_SIZE )
				if ( quotes[lastbar].low <  (highestPointAfterEntry - trade.FIXED_STOP * PIP_SIZE ))
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " reverse opportunity detected");
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;

					AddCloseRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterSellPosition(data.close, false);
					//enterSellPosition(reversePrice, false);
					enterMarketPosition(reversePrice);

					/*
					int prevLowPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevLowStartPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, prevLowPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getLow( quotesL, prevLowStartPos, prevLowPos).high, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;*/
					return;
				}
			}

			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.stopAdjusted == false)
			{
				if (quotes[lastbar].high - trade.entryPrice > 2*trade.FIXED_STOP * PIP_SIZE)
				{

					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice + 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + quotes[lastbar].time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}

					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					//trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + trade.FIXED_STOP);
				}

				// looking to actively close the position if the trend reverses
				/*
				int lastPosDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 1);
				
				if ( lastPosDown > tradeEntryPosL )
				{
					int touch20MAPos = -1;
					for ( int i = lastPosDown ; i < lastbarL; i++)
					{
						if ( quotesL[i].high > ema20L[i])
						{
							touch20MAPos = i;
							break;
						}
					}
					
					if ( touch20MAPos != -1 )
					{
						double lowest = Utility.getLow ( quotesL, tradeEntryPosL, touch20MAPos).low;
						if ( data.low < lowest )
						{
							AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);

							// enter new trade;
							createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
							trade.detectTime = quotesL[lastbarL].time;
							trade.pushStartL = lastPosDown;
							trade.pullBackPos = lastPosDown;//pullback.pos;
							trade.positionSize = POSITION_SIZE;
							
							trade.triggerPos = lastbarL;
							enterSellPosition( lowest);
							return;

						}
					}
				}*/
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, tradeEntryPosL).pos;
					PushHighLow[] phls = Pattern.findPast2Highs(quotesL, exitStartPosL, lastbarL).phls;
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
						//System.out.println();
						
						//if ( negatives >= 2 )
						/*
						if (( negatives > 0  ) && ((( quotesL[phl.prePos].high - phl.pullBack.low)  > 1.5 * FIXED_STOP * PIP_SIZE )))
						{
							System.out.println("pre=" + quotesL[phl.prePos].time + " cur=" + quotesL[phl.curPos].time);
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit sell on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).high /*+ 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}*/
						
						if ( negatives > 0 )
						{	
							if ((( quotesL[phl.prePos].high - phl.pullBack.low)  < 1.5 * FIXED_STOP * PIP_SIZE ) ) 
							{
								if ( !trade.targetPlaced2)
								{	
									logger.warning(symbol + " " + CHART + " " + trade.action + " exit 1/2 postion on small MACD diverage " + quotes[lastbar].time);
									trade.targetPrice2 = adjustPrice(quotesL[phl.prePos].high + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.targetPos2 = POSITION_SIZE/2;
									trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
									logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + quotes[lastbar].time);
			
									trade.targetPlaced2 = true;
									return;
								}
							}
							else
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + quotes[lastbar].time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + quotes[lastbar].time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}
					
					if ((phls != null) && (phls.length > 0))
					{	
						PushHighLow phl = phls[0];

						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							//trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
					/*

					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNHigh(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].high + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/

					
					// add other profit taken
					/* it works but not that great
					int prevPushHigh = lastbarL;
					while ( quotesL[prevPushHigh].close > quotesL[prevPushHigh].open)
						prevPushHigh--;
					
					if (prevPushHigh < lastbarL )
					{	
						double lowStart = quotesL[prevPushHigh+1].low;
						if (( data.high - lowStart  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.high, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/

				}
			}
		}
	}
	

	
	
	
	public void exit123_new2(QuoteData data,  int field, double currPrice)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
//		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

//		if (lastbarL < tradeEntryPosL + 2)
//			return;
		if (lastbarL <= tradeEntryPosL )
			return;


/*		Date now = new Date(data.timeInMillSec);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( now );
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);
		int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
		int minute=calendar.get(Calendar.MINUTE);
*/	

//		String hr = data.time.substring(9,12).trim();
//		String min = data.time.substring(13,15);
//		int hour = new Integer(hr);
//		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  to be reviewed later
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.3 ))
			{
				if ( data.high > (lowestPointAfterEntry + trade.FIXED_STOP * PIP_SIZE ))
				{
					logger.warning(symbol + " " + CHART + " reverse opportunity detected");
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					// reverse;
					AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterBuyPosition(reversePrice, false);
					enterMarketPosition(reversePrice);
					/*
					int prevHighPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevHighStartPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevHighPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getHigh( quotesL, prevHighStartPos, prevHighPos).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;*/
					return;

				}
			}			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			if (trade.stopAdjusted == false)
			{
				/*
				if ((trade.entryPrice - data.low > trade.FIXED_STOP * PIP_SIZE) && ( trade.positionUpdated == false ))
				{
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, data.close);
					trade.positionSize += POSITION_SIZE;
					trade.remainingPositionSize += POSITION_SIZE;
					trade.positionUpdated = true;
				}*/

				if (trade.entryPrice - data.low > 2*trade.FIXED_STOP * PIP_SIZE) 
				{
					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice - 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}
					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time + " break even size is " + trade.FIXED_STOP);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL).phls;
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
						
						//if ( positive > = 2 )
						/*
						if (( positive > 0  ) && ((( phl.pullBack.high - quotesL[phl.prePos].low)  > 1.5 * FIXED_STOP * PIP_SIZE)))
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
							
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).low/* - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							return;
						}*/
						
						if ( positive > 0 )
						{	
							if ((( phl.pullBack.high - quotesL[phl.prePos].low)  < 1.5 * FIXED_STOP * PIP_SIZE)) 
							{
								if ( !trade.targetPlaced2 )
								{	
									logger.warning(symbol + " " + CHART + " " + trade.action + " exit 1/2 postion on small MACD diverage " + data.time);
									trade.targetPrice2 = adjustPrice((quotesL[phl.prePos]).low  - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.targetPos2 = POSITION_SIZE/2;
									trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
									logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
			
									trade.targetPlaced2 = true;
									return;
								}
							}
							else
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(( quotesL[phl.prePos]).low, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}

					
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
						if ( stop < trade.stop )
						{
							
							System.out.println(symbol + " " + CHART + " " + data.time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
					
					/*
					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNLow(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].low - 20 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/
					
					
					// add other profit taken 
					/* it works but not that great
					int prevPushLow = lastbarL;
					while ( quotesL[prevPushLow].close < quotesL[prevPushLow].open)
						prevPushLow--;
					
					if (prevPushLow < lastbarL )
					{	
						double highStart = quotesL[prevPushLow+1].high;
						if (( highStart - data.low  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//  to be reviewed later
			double highestPointAfterEntry = Utility.getHigh(quotesL, tradeEntryPosL, lastbarL).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.3 ))
			{
				//if (( highestPointAfterEntry - data.low ) > trade.FIXED_STOP * PIP_SIZE )
				if ( data.low <  (highestPointAfterEntry - trade.FIXED_STOP * PIP_SIZE ))
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " reverse opportunity detected");
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;

					AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryPrice = reversePrice;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterSellPosition(data.close, false);
					//enterSellPosition(reversePrice, false);
					enterMarketPosition(reversePrice);

					/*
					int prevLowPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevLowStartPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, prevLowPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getLow( quotesL, prevLowStartPos, prevLowPos).high, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;*/
					return;
				}
			}

			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > 2*trade.FIXED_STOP * PIP_SIZE)
				{

					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice + 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}

					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time + " break even size is " + trade.FIXED_STOP);
				}

				// looking to actively close the position if the trend reverses
				/*
				int lastPosDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 1);
				
				if ( lastPosDown > tradeEntryPosL )
				{
					int touch20MAPos = -1;
					for ( int i = lastPosDown ; i < lastbarL; i++)
					{
						if ( quotesL[i].high > ema20L[i])
						{
							touch20MAPos = i;
							break;
						}
					}
					
					if ( touch20MAPos != -1 )
					{
						double lowest = Utility.getLow ( quotesL, tradeEntryPosL, touch20MAPos).low;
						if ( data.low < lowest )
						{
							AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);

							// enter new trade;
							createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
							trade.detectTime = quotesL[lastbarL].time;
							trade.pushStartL = lastPosDown;
							trade.pullBackPos = lastPosDown;//pullback.pos;
							trade.positionSize = POSITION_SIZE;
							
							trade.triggerPos = lastbarL;
							enterSellPosition( lowest);
							return;

						}
					}
				}*/
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, tradeEntryPosL).pos;
					PushHighLow[] phls = Pattern.findPast2Highs(quotesL, exitStartPosL, lastbarL).phls;
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
						//System.out.println();
						
						//if ( negatives >= 2 )
						/*
						if (( negatives > 0  ) && ((( quotesL[phl.prePos].high - phl.pullBack.low)  > 1.5 * FIXED_STOP * PIP_SIZE )))
						{
							System.out.println("pre=" + quotesL[phl.prePos].time + " cur=" + quotesL[phl.curPos].time);
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit sell on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).high /*+ 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}*/
						
						if ( negatives > 0 )
						{	
							if ((( quotesL[phl.prePos].high - phl.pullBack.low)  < 1.5 * FIXED_STOP * PIP_SIZE ) ) 
							{
								if ( !trade.targetPlaced2)
								{	
									logger.warning(symbol + " " + CHART + " " + trade.action + " exit 1/2 postion on small MACD diverage " + data.time);
									trade.targetPrice2 = adjustPrice(quotesL[phl.prePos].high + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.targetPos2 = POSITION_SIZE/2;
									trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
									logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
			
									trade.targetPlaced2 = true;
									return;
								}
							}
							else
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}
					
					if ((phls != null) && (phls.length > 0))
					{	
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
					/*

					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNHigh(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].high + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/

					
					// add other profit taken
					/* it works but not that great
					int prevPushHigh = lastbarL;
					while ( quotesL[prevPushHigh].close > quotesL[prevPushHigh].open)
						prevPushHigh--;
					
					if (prevPushHigh < lastbarL )
					{	
						double lowStart = quotesL[prevPushHigh+1].low;
						if (( data.high - lowStart  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.high, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/

				}
			}
		}
	}
	

	
	
	
	
	
	
	public void exit123_new(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}



		Date now = new Date(data.timeInMillSec);
		Calendar calendar = Calendar.getInstance();
		calendar.setTime( now );
		int weekday = calendar.get(Calendar.DAY_OF_WEEK);
		int hour_of_day=calendar.get(Calendar.HOUR_OF_DAY);
		int minute=calendar.get(Calendar.MINUTE);
		

//		String hr = data.time.substring(9,12).trim();
//		String min = data.time.substring(13,15);
//		int hour = new Integer(hr);
//		int minute = new Integer(min);

		if (lastbar <= tradeEntryPos )
			return;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			/*
			//  to be reviewed later
			double lowestPointAfterEntry = Utility.getLow(quotes, tradeEntryPos+1, lastbar).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && (( trade.entryPrice - lowestPointAfterEntry) < trade.FIXED_STOP * PIP_SIZE * 0.5 )&& (( lastbarL - tradeEntryPosL) < 3 ))
			{
				if (( data.high - lowestPointAfterEntry ) > trade.FIXED_STOP * PIP_SIZE )
				{
					logger.warning(symbol + " " + CHART + " reverse opportunity detected " + data.time);
					double reversePrice = lowestPointAfterEntry +  trade.FIXED_STOP * PIP_SIZE;
					// reverse;
					AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.detectTimeInMill = data.timeInMillSec;
					//trade.positionSize = POSITION_SIZE;
					//trade.entryPrice = reversePrice;
					//trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterBuyPosition(data.close);
					//enterBuyPosition(reversePrice, false);
					enterMarketPosition( reversePrice );
					//trade.limitPrice1 = adjustPrice(lowestPointAfterEntry + FIXED_STOP/2 * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					//enterLimitPosition1(trade.limitPrice1, POSITION_SIZE);
					/*
					int prevHighPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevHighStartPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevHighPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getHigh( quotesL, prevHighStartPos, prevHighPos).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;/
					return;

				}
			}*/			

			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.stopAdjusted == false)
			{
				/*
				if ((trade.entryPrice - data.low > trade.FIXED_STOP * PIP_SIZE) && ( trade.positionUpdated == false ))
				{
					AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, data.close);
					trade.positionSize += POSITION_SIZE;
					trade.remainingPositionSize += POSITION_SIZE;
					trade.positionUpdated = true;
				}*/

				if (trade.entryPrice - data.low > 2*trade.FIXED_STOP * PIP_SIZE) 
				{
					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice - 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}
					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time + " break even size is " + trade.FIXED_STOP);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL).phls;
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
						
						//if ( positive > = 2 )
						/*
						if (( positive > 0  ) && ((( phl.pullBack.high - quotesL[phl.prePos].low)  > 1.5 * FIXED_STOP * PIP_SIZE)))
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
							
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).low/* - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							return;
						}*/
						
						if ( positive > 0 )
						{	
							if ((( phl.pullBack.high - quotesL[phl.prePos].low)  < 1.5 * FIXED_STOP * PIP_SIZE)) 
							{
								if ( !trade.targetPlaced2 )
								{	
									logger.warning(symbol + " " + CHART + " " + trade.action + " exit 1/2 postion on small MACD diverage " + data.time);
									trade.targetPrice2 = adjustPrice((quotesL[phl.prePos]).low  - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.targetPos2 = POSITION_SIZE/2;
									trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
									logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
			
									trade.targetPlaced2 = true;
									return;
								}
							}
							else
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(( quotesL[phl.prePos]).low, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}

					
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
						if ( stop < trade.stop )
						{
							
							System.out.println(symbol + " " + CHART + " " + data.time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
					
					/*
					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNLow(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].low - 20 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/
					
					
					// add other profit taken 
					/* it works but not that great
					int prevPushLow = lastbarL;
					while ( quotesL[prevPushLow].close < quotesL[prevPushLow].open)
						prevPushLow--;
					
					if (prevPushLow < lastbarL )
					{	
						double highStart = quotesL[prevPushLow+1].high;
						if (( highStart - data.low  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			//  to be reviewed later
			/*
			double highestPointAfterEntry = Utility.getHigh(quotes, tradeEntryPos+1, lastbar).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && (( highestPointAfterEntry - trade.entryPrice) < trade.FIXED_STOP * PIP_SIZE *0.5 ) && (( lastbarL - tradeEntryPosL) < 3 ))
			{
				//System.out.println(data.time + " high:" + highestPointAfterEntry + " datalow:" + data.low );
				if (( highestPointAfterEntry - data.low ) > trade.FIXED_STOP * PIP_SIZE )
				{
					// reverse;
					logger.warning(symbol + " " + CHART + " reverse opportunity detected " + data.time);
					double reversePrice = highestPointAfterEntry -  trade.FIXED_STOP * PIP_SIZE;

					AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
					closePositionByMarket(trade.remainingPositionSize, reversePrice);
					
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.detectTimeInMill = data.timeInMillSec;
					//trade.positionSize = POSITION_SIZE;
					//trade.entryPrice = reversePrice;
					//trade.entryTime = ((QuoteData) quotes[lastbar]).time;

					//enterSellPosition(data.close);
					enterMarketPosition( reversePrice );
					//enterLimitPosition1(reversePrice, false);
					//trade.limitPrice1 = adjustPrice(highestPointAfterEntry -  FIXED_STOP * PIP_SIZE * 0.5,  Constants.ADJUST_TYPE_UP);
					//enterLimitPosition1(trade.limitPrice1, POSITION_SIZE);

					/*
					int prevLowPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, tradeEntryPosL, 2);
					int prevLowStartPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, prevLowPos, 2);
					
					trade.targetPrice1 = adjustPrice(Utility.getLow( quotesL, prevLowStartPos, prevLowPos).high, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.positionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " BUY target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

					trade.targetPlaced1 = true;/
					return;
				}
			}*/

			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > 2*trade.FIXED_STOP * PIP_SIZE)
				{

					if ( trade.type.equals( Constants.TRADE_CNT))
					{
						trade.targetPrice1 = adjustPrice(trade.entryPrice + 2*trade.FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);

						trade.stopAdjusted = true;
						trade.targetPlaced1 = true;
						return;
						
					}

					
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					trade.lastStopAdjustTime = data.time;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time + " break even size is " + trade.FIXED_STOP);
				}

				// looking to actively close the position if the trend reverses
				/*
				int lastPosDown = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, lastbarL, 1);
				
				if ( lastPosDown > tradeEntryPosL )
				{
					int touch20MAPos = -1;
					for ( int i = lastPosDown ; i < lastbarL; i++)
					{
						if ( quotesL[i].high > ema20L[i])
						{
							touch20MAPos = i;
							break;
						}
					}
					
					if ( touch20MAPos != -1 )
					{
						double lowest = Utility.getLow ( quotesL, tradeEntryPosL, touch20MAPos).low;
						if ( data.low < lowest )
						{
							AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);

							// enter new trade;
							createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
							trade.detectTime = quotesL[lastbarL].time;
							trade.pushStartL = lastPosDown;
							trade.pullBackPos = lastPosDown;//pullback.pos;
							trade.positionSize = POSITION_SIZE;
							
							trade.triggerPos = lastbarL;
							enterSellPosition( lowest);
							return;

						}
					}
				}*/
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, tradeEntryPosL).pos;
					PushHighLow[] phls = Pattern.findPast2Highs(quotesL, exitStartPosL, lastbarL).phls;
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
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
						//System.out.println();
						
						//if ( negatives >= 2 )
						/*
						if (( negatives > 0  ) && ((( quotesL[phl.prePos].high - phl.pullBack.low)  > 1.5 * FIXED_STOP * PIP_SIZE )))
						{
							System.out.println("pre=" + quotesL[phl.prePos].time + " cur=" + quotesL[phl.curPos].time);
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit sell on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).high /*+ 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}*/
						
						if ( negatives > 0 )
						{	
							if ((( quotesL[phl.prePos].high - phl.pullBack.low)  < 1.5 * FIXED_STOP * PIP_SIZE ) ) 
							{
								if ( !trade.targetPlaced2)
								{	
									logger.warning(symbol + " " + CHART + " " + trade.action + " exit 1/2 postion on small MACD diverage " + data.time);
									trade.targetPrice2 = adjustPrice(quotesL[phl.prePos].high + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.targetPos2 = POSITION_SIZE/2;
									trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
									logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
			
									trade.targetPlaced2 = true;
									return;
								}
							}
							else
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(quotesL[phl.prePos].high, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " SELL target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}
					
					if ((phls != null) && (phls.length > 0))
					{	
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							trade.lastStopAdjustTime = data.time;
							warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
						}
					}
					/*

					if (( weekday == Calendar.FRIDAY ) && ( hour_of_day >= 11 ))
					{
						PushHighLow phl = Pattern.findLastNHigh(quotes, tradeEntryPos, lastbar, 2);
						if (phl != null)
						{
							trade.targetPrice1 =  adjustPrice(quotes[phl.curPos].high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize/2;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							
							trade.targetPrice2 =  adjustPrice(quotes[phl.curPos].high + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = trade.remainingPositionSize/2;
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice2 + " " + trade.targetPos2 + " " + data.time);
							
							//trade.profitTake1 = true;
						}
					}*/

					
					// add other profit taken
					/* it works but not that great
					int prevPushHigh = lastbarL;
					while ( quotesL[prevPushHigh].close > quotesL[prevPushHigh].open)
						prevPushHigh--;
					
					if (prevPushHigh < lastbarL )
					{	
						double lowStart = quotesL[prevPushHigh+1].low;
						if (( data.high - lowStart  > 2.8 * trade.FIXED_STOP * PIP_SIZE ) && ( trade.advancedPlacement == false ))
						{
							trade.targetPrice2 = adjustPrice(data.high, Constants.ADJUST_TYPE_UP);
							trade.targetPos2 = POSITION_SIZE/2;
							logger.warning(symbol + " " + CHART + " " + trade.action + " place order to exit 1/2 postion on large push " + data.time + "@" + trade.targetPrice2);
							trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);
	
							trade.advancedPlacement = true;
							
						}
					}*/

				}
			}
		}
	}
	

	
	public void exit123_trad(QuoteData data )
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL < tradeEntryPosL + 2)
			return;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > trade.FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time + " break even size is " + trade.FIXED_STOP);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					if ( quotes[lastbar].high > quotes[lastbar-1].high)
					{
						PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL).phls;
						if ( phls == null )
							phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL-1).phls;
						
						if ((phls != null) && (phls.length >=2))
						{
							PushHighLow phl = phls[0];
							MACD[] macd = Indicator.calculateMACD( quotesL );
							int positive = 0;
							for ( int j = phl.prePos; j <= phl.curPos; j++)
							{
								if ( macd[j].histogram > 0 )
									positive ++;
							}
							
							if ( positive >= 1 )
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(Utility.getLow( quotesL, tradeEntryPosL, lastbarL).low, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}
					
					int highestPos = Utility.getHigh( quotesL, tradeEntryPosL, lastbarL).pos;
					PushHighLow[] phls = Pattern.findPast1Lows(quotesL, highestPos, lastbarL);

					if ((phls != null) && (phls.length >=2))
					{
						int last = phls.length-1;
						//System.out.println("total length is  "  + phls.length );
						//for ( int i = 0; i < last; i++)
						//{
							//System.out.println("push"+ i + " prePos:" + quotesL[phls[i].prePos].time + " curPos:" + quotesL[phls[i].curPos].time);
						//}
						if (( trade.stop - quotesL[phls[1].curPos].low) > 1.5 * trade.FIXED_STOP * PIP_SIZE)
						{
							trade.targetPrice1 = adjustPrice(quotesL[phls[0].prePos].low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							
						}
					}

					
					
					
					phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL).phls;
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
						if ( stop < trade.stop )
						{
							
							System.out.println(symbol + " " + CHART + " " + data.time + " move stop to " + stop );
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > trade.FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + data.time + " break even size is " + trade.FIXED_STOP);
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					if ( quotes[lastbar].low < quotes[lastbar-1].low)
					{
						PushHighLow[] phls = Pattern.findPast2Highs(quotesL, tradeEntryPosL, lastbarL).phls;
						if ( phls == null )
							phls = Pattern.findPast2Highs(quotesL, tradeEntryPosL, lastbarL-1).phls;

						if ((phls != null) && (phls.length >=2))
						{
							PushHighLow phl = phls[0];
							MACD[] macd = Indicator.calculateMACD( quotesL );
							int negatives = 0;
							for ( int j = phl.prePos; j <= phl.curPos; j++)
							{
								if ( macd[j].histogram < 0 )
									negatives ++;
							}
							
							if ( negatives >= 1 )
							{
								System.out.println("pre=" + quotesL[phl.prePos].time + " cur=" + quotesL[phl.curPos].time);
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit sell on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
		
								trade.targetPrice1 = adjustPrice(Utility.getHigh( quotesL, tradeEntryPosL, lastbarL ).high, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}
					}
					
					int lowestPos = Utility.getLow( quotesL, tradeEntryPosL, lastbarL).pos;
					PushHighLow[] phls = Pattern.findPast1Highs(quotesL, lowestPos, lastbarL);

					if ((phls != null) && (phls.length >=2))
					{
						int last = phls.length-1;

						if (( quotesL[phls[1].curPos].high - trade.stop ) > 1.5 * trade.FIXED_STOP * PIP_SIZE)
						{
							trade.targetPrice1 = adjustPrice(quotesL[phls[0].prePos].high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
	
							trade.targetPlaced1 = true;
							
						}
					}

					
					
					phls = Pattern.findPast2Highs(quotesL, tradeEntryPosL, lastbarL).phls;
					if ((phls != null) && (phls.length > 0))
					{	
						PushHighLow phl = phls[0];
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
						if ( stop > trade.stop )
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
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

	void resetHighEntryQualifyPrice(Trade trade, Object[] quotes, int reachEntryQualifyPricePos, int lastbar)
	{
		QuoteData qdata = Utility.getHigh(quotes, reachEntryQualifyPricePos, lastbar);
		trade.entryQualifyPrice = qdata.high;
		trade.entryQualifyPricePos = lastbar;
		trade.entryQualifyCount++;

	}

	void resetLowEntryQualifyPrice(Trade trade, Object[] quotes, int reachEntryQualifyPricePos, int lastbar)
	{
		QuoteData qdata = Utility.getLow(quotes, reachEntryQualifyPricePos, lastbar);
		trade.entryQualifyPrice = qdata.low;
		trade.entryQualifyPricePos = lastbar;
		trade.entryQualifyCount++;

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

	public void calculateInitialBreakOut()
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		toBreakOutUp = toBreakOutDown = false;

		int aboveMA = 0;
		int belowMA = 0;

		for (int i = lastbar - 3; i > lastbar - 30; i--)
		{
			if (((QuoteData) quotes[i]).low > ema20[i])
				aboveMA++;
			if (((QuoteData) quotes[i]).high < ema20[i])
				belowMA++;
		}

		if ((aboveMA < 5) && (belowMA > 10))
			toBreakOutUp = true;

		if ((belowMA < 5) && (aboveMA > 10))
			toBreakOutDown = true;

	}

	public boolean checkDetectHistoy(int detectPos)
	{
		int count = 0;
		Iterator it = detectHistory.iterator();
		while (it.hasNext())
		{
			Integer pos = (Integer) it.next();
			if ( pos == detectPos )
				return true;
		}

		return false;
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
			if ( /*t.action.equals(action) &&*/ (firstBreakOutStartTime.equals(t.firstBreakOutStartTimeL)))
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
	
	
	public boolean inTradingTime( int hour, int min )
	{
		int minute = hour * 60 + min;
		if (( minute >= 480) && (minute <= 600))//  between 8:am - 10am
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


	
}


