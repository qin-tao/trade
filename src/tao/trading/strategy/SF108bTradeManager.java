package tao.trading.strategy;

import java.io.Serializable;
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
import tao.trading.Peak;
import tao.trading.QuoteData;
import tao.trading.Reversal123;
import tao.trading.Trade;
import tao.trading.dao.PushHighLow;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

import java.util.Arrays;

public class SF108bTradeManager extends TradeManager2 implements Serializable
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
	protected SF108bTradeManager largerTimeFrameTraderManager;
	boolean breakOutCalculated = false;
	boolean toBreakOutUp = false;
	boolean toBreakOutDown = false;
	boolean timeUp = false;
	public Vector<Integer> detectHistory = new Vector<Integer>();

	// important switch
	boolean prremptive_limit = false;
	boolean breakout_limit = false;
	boolean reverse_trade = false; // reverse after stopped out
	boolean rsc_trade = false; // do a rsc trade instead of rst if there is
								// consective 3 bars
	boolean reverse_after_profit = false; // reverse after there is a profit
	boolean after_target_reversal = false;

	int BREAK_EVEN_POINT = 20;

	int ENTRY_LARGE_PULLBACK = 12;
	int ENTRY_LARGE_BREAKOUT = 20;
	int EXIT_LARGE_PULLBACK = 10;
	int EXIT_LARGE_BREAKOUT = 20;

	boolean LIMIT_ORDER = false;

	public SF108bTradeManager()
	{
		super();
	}

	public SF108bTradeManager(String account, EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super(account, client, contract, symbolIndex, logger);
	}

	public QuoteData[] getQuoteData()
	{
		Object[] quotes = this.qts.values().toArray();
		Arrays.sort(quotes);
		return Arrays.copyOf(quotes, quotes.length, QuoteData[].class);
	}

	
	public boolean inJPTradingTime( int hour, int min )
	{
		boolean inTradingTime = false;
		int minute = hour * 60 + min;
		
		if ( TIME_ZONE.indexOf("A") != -1 )
		{	
			if (( minute >= 1080) && (minute <= 1380))
				inTradingTime = true;
		}
		
		return inTradingTime;

	}

	public boolean inEUTradingTime( int hour, int min )
	{
		boolean inTradingTime = false;
		int minute = hour * 60 + min;
		
		if ( TIME_ZONE.indexOf("E") != -1 )
		{	
			if (( minute >= 90) && (minute <= 420))
				inTradingTime = true;
		}
		
		return inTradingTime;

	}

	public boolean inUSTradingTime( int hour, int min )
	{
		boolean inTradingTime = false;
		int minute = hour * 60 + min;
		
		if ( TIME_ZONE.indexOf("U") != -1 )
		{	
			if (( minute >= 420) && (minute <= 600))
				inTradingTime = true;
		}
		
		return inTradingTime;

	}

	
	
	public boolean inEUExitingTime( int hour, int min )
	{
		boolean inExitTime = false;
		int minute = hour * 60 + min;
		
		if ( TIME_ZONE.indexOf("E") != -1 )
		{	
			if ( minute >= 780)   // > 13:00
				inExitTime = true;
		}
		return inExitTime;
	}

	
	public boolean inJPExitingTime( int hour, int min )
	{
		boolean inExitTime = false;
		int minute = hour * 60 + min;
		
		if ( TIME_ZONE.indexOf("A") != -1 )
		{	
			//if (( minute >= 780) && ( minute < 840))   // > 13:00
			if (( minute > 120 ) && ( minute < 180))
				inExitTime = true;
		}
		return inExitTime;
	}

	
	public boolean inEUTakeProfitTime( int hour, int min )
	{
		boolean inExitTime = false;
		int minute = hour * 60 + min;
		
		if ( TIME_ZONE.indexOf("E") != -1 )
		{	
			if (( minute >= 660) && (minute < 780 ))  //  11:00 - 13:00
				inExitTime = true;
		}
		
		return inExitTime;
	}

	public boolean inJPTakeProfitTime( int hour, int min )
	{
		boolean inExitTime = false;
		int minute = hour * 60 + min;

		if ( TIME_ZONE.indexOf("A") != -1 )
		{	
			//if (( minute >= 660) && (minute < 780 ))  //  11:00 - 13:00
			if (( minute >= 1320) || ( minute < 120 ))  //  after 9 pm or < 11am
				inExitTime = true;
		}
		
		return inExitTime;
	}
	
	
	
	
	public void closePositionByMarket(int posSize, double currentPrice)
	{
		String action = trade.action.equals(Constants.ACTION_BUY) ? Constants.ACTION_SELL : Constants.ACTION_BUY;

		// this is to place the real order
		placeMktOrder(action, posSize);

		trade.remainingPositionSize -= posSize;

		if (trade.remainingPositionSize == 0)
		{
			if (trade.stopId != 0)
				cancelOrder(trade.stopId);
			tradeHistory.add(trade);
			trade = null;
		}
	}

	public void closePositionByLimit(String time, double limitPrice, int positionSize)
	{
		if (trade.profitTake1 == true )
		{
			cancelOrder( trade.targetId);
		}
		
		String action = trade.action.equals("BUY") ? "SELL" : "BUY";
		trade.targetId = placeLmtOrder(action, limitPrice, positionSize, null);
		trade.profitTake1 = true;

	}

	
	public void cancelTargets()
	{
		if (MODE == Constants.REAL_MODE)
		{
			if (trade.targetId1 != 0)
			{
				cancelOrder(trade.targetId1);
				trade.targetId1 = 0;
			}
			if (trade.targetId2 != 0)
			{
				cancelOrder(trade.targetId2);
				trade.targetId2 = 0;
			}
		}
		else if (MODE == Constants.TEST_MODE)
		{
			trade.targetId1 = trade.targetId2 = 0;
		}
	}

	
	public void cancelStop()
	{
		if (MODE == Constants.REAL_MODE)
		{
			if (trade.stopId != 0)
			{
				cancelOrder(trade.stopId);
				trade.stopId = 0;
			}
		}
		else if (MODE == Constants.TEST_MODE)
		{
			trade.stopId = 0;
		}
	}
	
	
	
	
	public void createOpenTrade(String tradeType, String action)
	{
		trade = new Trade(symbol);
		trade.type = tradeType;
		trade.action = action;
		trade.POSITION_SIZE = POSITION_SIZE;
		trade.status = Constants.STATUS_OPEN;
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

		if (orderId == trade.stopId)
		{
			logger.warning(symbol + " " + CHART + " minute " + filled + " stopped out ");
			trade.stoppedOutPos = ((QuoteData) quotes[lastbar]).pos;
			trade.stopId = 0;

			cancelTargets();
			
			removeTrade();
			//processAfterHitStopLogic_c();

		}
		else if ((orderId == trade.limitId1) && ( trade.limitPos1Filled == 0 ))
		{
			logger.warning(symbol + " " + CHART + " minute " + " limit order: " + orderId + " " + filled + " filled");

			trade.limitPos1Filled = trade.limitPos1;
			trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize = trade.limitPos1; //+= filled;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			if (trade.stopId != 0)
				cancelOrder(trade.stopId);

			// calculate stop here
			trade.stop = trade.limit1Stop;
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

			if (trade.remainingPositionSize == trade.POSITION_SIZE)
				trade.status = Constants.STATUS_PLACED;
		}
		else if ((orderId == trade.limitId2)&& ( trade.limitPos2Filled == 0 ))
		{
			logger.warning(symbol + " " + CHART + " minute " + " limit order2: " + orderId + " " + filled + " filled");
			
			trade.limitPos2Filled = trade.limitPos2;
			trade.entryPrice = trade.limitPrice2;
			trade.remainingPositionSize += filled;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			if (trade.stopId != 0)
				cancelOrder(trade.stopId);

			// calculate stop here
			trade.stop = trade.limit2Stop;
			if (Constants.ACTION_SELL.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			else if (Constants.ACTION_BUY.equals(trade.action))
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);

			if (trade.remainingPositionSize == trade.POSITION_SIZE)
				trade.status = Constants.STATUS_PLACED;
		}
		else if (orderId == trade.targetId1)
		{
			logger.warning(symbol + " " + CHART + " minute target1 filled, " + filled + " closed @ " + trade.targetPrice);
			trade.targetId1 = 0;
			//trade.targetReached = true;
			//trade.remainingPositionSize -= filled;

			cancelStop();
			cancelProfits();
			
			/*
			logger.warning(symbol + " " + CHART + " remainning position is " + trade.remainingPositionSize);
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}*/
			removeTrade();
			return;
		}
		else if (orderId == trade.takeProfit1Id)
		{
			logger.warning(symbol + " " + CHART + " minute take profit 1 filled, " + filled + " closed @ " + trade.targetPrice);
			trade.takeProfit1Id = 0;
			trade.remainingPositionSize -= trade.takeProfit1PosSize;

			cancelStop();
			
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				removeTrade();
			}
			
			return;
		}
		else if (orderId == trade.takeProfit2Id)
		{
			logger.warning(symbol + " " + CHART + " minute take profit 1 filled, " + filled + " closed @ " + trade.targetPrice);
			trade.takeProfit2Id = 0;
			trade.remainingPositionSize -= trade.takeProfit2PosSize;

			cancelStop();
			
			if (trade.remainingPositionSize > 0)
			{
				if (Constants.ACTION_SELL.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				else if (Constants.ACTION_BUY.equals(trade.action))
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			}
			else
			{
				removeTrade();
			}
			
			return;
		}
	}


	public void checkStopTarget(QuoteData data)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.high >= trade.limitPrice1))
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
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				// calculate stop here
				trade.stop = trade.limit1Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId1 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			if ((trade.limitId2 != 0) && (data.high >= trade.limitPrice2))
			{
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
				AddOpenRecord(data.time, Constants.ACTION_SELL, trade.limitPos2, trade.limitPrice2);
				trade.limitPos2Filled = trade.limitPos2;

				trade.entryPrice = trade.limitPrice2;
				trade.remainingPositionSize += trade.limitPos2Filled;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.stopId != 0)
					cancelOrder(trade.stopId);
				// calculate stop here
				trade.stop = trade.limit2Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;
			}

			// check stop;
			if ((trade.stopId != 0) && (data.high > trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out @ data.high:" + data.high + " " + data.time);
				trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;

				trade.preStopPrice = trade.stop;
				cancelTargets();

				//removeTrade();
				processAfterHitStopLogic_c();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.low < trade.targetPrice1))
			{
				logger.info(symbol + " target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				cancelProfits();
				
				//trade.remainingPositionSize -= trade.targetPos1;
				//if (trade.remainingPositionSize <= 0)
				removeTrade();
				return;

			}

			if ((trade.takeProfit1Id != 0) && (data.low < trade.takeProfit1Price))
			{
				logger.info(symbol + " take profit 1 hit, close " + trade.takeProfit1PosSize + " @ " + trade.takeProfit1Price);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos1, trade.targetPrice1);
				trade.takeProfit1Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit1PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
			
			if ((trade.takeProfit2Id != 0) && (data.low < trade.takeProfit2Price))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PosSize + " @ " + trade.takeProfit2Price);
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos2, trade.targetPrice2);
				trade.takeProfit2Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit2PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
			

		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check entry limit;
			if ((trade.limitId1 != 0) && (data.low <= trade.limitPrice1))
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
				logger.warning(symbol + " " + CHART + " limit order of " + trade.limitPrice2 + " filled " + ((QuoteData) quotes[lastbar]).time);
				AddOpenRecord(data.time, Constants.ACTION_BUY, trade.limitPos2, trade.limitPrice2);
				trade.limitPos2Filled = trade.limitPos2;

				trade.entryPrice = trade.limitPrice2;
				trade.remainingPositionSize += trade.limitPos2Filled;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.stopId != 0)
					cancelOrder(trade.stopId);
				// calculate stop here
				trade.stop = trade.limit2Stop;
				trade.stopId = placeStopOrder(Constants.ACTION_BUY.equals(trade.action) ? Constants.ACTION_SELL : Constants.ACTION_BUY, trade.stop,
						trade.remainingPositionSize, null);
				trade.limitId2 = 0;

				if (trade.remainingPositionSize == trade.POSITION_SIZE)
					trade.status = Constants.STATUS_PLACED;

			}

			// check stop;
			if ((trade.stopId != 0) && (data.low < trade.stop))
			{
				logger.warning(symbol + " " + CHART + " minute stopped out @ data.low:" + data.low + " " + data.time);
				trade.stoppedOutPos = lastbar;
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.stop);
				trade.stopId = 0;

				trade.preStopPrice = trade.stop;
				cancelTargets();

				processAfterHitStopLogic_c();
				//removeTrade();
				return;
			}

			// check target;
			if ((trade.targetId1 != 0) && (data.high > trade.targetPrice1))
			{
				logger.info(symbol + " target1 hit, close " + trade.targetPos1 + " @ " + trade.targetPrice1);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.targetId1 = 0;

				cancelStop();
				cancelProfits();
				
				//trade.remainingPositionSize -= trade.targetPos1;
				//if (trade.remainingPositionSize <= 0)
				removeTrade();
				return;
			}

			if ((trade.takeProfit1Id != 0) && (data.high > trade.takeProfit1Price))
			{
				logger.info(symbol + " take profit 1 hit, close " + trade.takeProfit1PosSize + " @ " + trade.takeProfit1Price);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos1, trade.targetPrice1);
				trade.takeProfit1Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit1PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
			
			if ((trade.takeProfit2Id != 0) && (data.high > trade.takeProfit2Price))
			{
				logger.info(symbol + " take profit 2 hit, close " + trade.takeProfit2PosSize + " @ " + trade.takeProfit2Price);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos2, trade.targetPrice2);
				trade.takeProfit2Id = 0;

				cancelStop();
				trade.remainingPositionSize = trade.remainingPositionSize - trade.takeProfit2PosSize;
				
				if (trade.remainingPositionSize > 0)
				{
					if (Constants.ACTION_SELL.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					else if (Constants.ACTION_BUY.equals(trade.action))
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
				}
				else
				{
					removeTrade();
				}
			}
		}
	}



	
	void processAfterHitStopLogic_c()
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		Object[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();

		double prevStop = trade.stop;
		String prevAction = trade.action;
		String prevType = trade.type;
		boolean preStopAdjusted = trade.stopAdjusted;
		
		removeTrade();
		if ((reverse_trade == false) || (prevType == Constants.TRADE_CNT) || (preStopAdjusted == true ))
		{
			return;
		}

		if (Constants.ACTION_SELL.equals(prevAction))
		{
				//int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				//QuoteData lowAfterEntry = Utility.getLow(quotes, tradeEntryPos, lastbar);

				logger.warning(symbol + " " + CHART + " place reverse order at " + ((QuoteData) quotes[lastbar]).time + " @ " + prevStop);
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = trade.price = prevStop;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				CreateTradeRecord(trade.type, Constants.ACTION_BUY);
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);

				trade.stop = trade.price - FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());
				return;
			}
			else if (Constants.ACTION_BUY.equals(prevAction))
			{
				logger.warning(symbol + " " + CHART + " place reverse order at " + ((QuoteData) quotes[lastbar]).time + " @ " + prevStop);
				createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
				trade.detectTime = quotes[lastbar].time;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.entryPrice = trade.price = prevStop;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;

				CreateTradeRecord(trade.type, Constants.ACTION_SELL);
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

				trade.stop = trade.price + FIXED_STOP * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																													// Long(oca).toString());
				return;
		}

	}

	
	
	
	
	protected void setMktTrade(String type, String action, int positionSize, double price)
	{
		trade.type = type;
		trade.action = action;
		trade.POSITION_SIZE = positionSize;

		trade.price = price;
		trade.entryPrice = trade.price;

		logger.warning(symbol + " " + CHART + " place market " + action + " order");
		trade.orderId = placeMktOrder(action, trade.POSITION_SIZE);

		trade.status = Constants.STATUS_PLACED;

		trade.remainingPositionSize = trade.POSITION_SIZE;

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
				checkTriggerMarketSell(price);
			}
			else if (Constants.ACTION_BUY.equals( trade.action))
			{
				checkTriggerMarketBuy(price);
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
	public Trade HigherHighLowerLowSetupOrg(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastConsectiveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 5);
		int lastConsectiveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 5);

		if (lastConsectiveUp > lastConsectiveDown)
		{
			MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, lastbar);
			/*
			 * logger.warning(symbol + " " + CHART + " trend is up " +
			 * data.time); logger.warning(symbol + " " + CHART +
			 * " last consecitveAbove20MA is " +
			 * ((QuoteData)quotes[lastConsectiveUp]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last consecitveBelow20MA is " +
			 * ((QuoteData)quotes[lastConsectiveDown]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last 20MA touch up low is " + mat.lowest );
			 */
			if (data.low < mat.low.low)
			{
				// requires two higher high
				int lastlow = mat.low.pos;
				while (((QuoteData) quotes[lastlow]).low < ema20[lastlow])
					lastlow--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchUp(quotes, ema20, lastlow);
				if (!(mat.low.low > mat2.low.low))
					return null;

				// last check large data chart
				trade = new Trade(symbol);
				logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch up low is " + mat.low.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.low.low;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = mat.low.low;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_SELL, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_SELL, trade.POSITION_SIZE, mat.low.low);

				trade.stop = Utility.getHigh(quotes, mat.low.pos, lastbar).high;
				if (trade.stop - mat.low.low > 14 * PIP_SIZE)
					trade.stop = mat.low.low + 14 * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return trade;
			}

		}
		else if (lastConsectiveUp < lastConsectiveDown)
		{
			MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, lastbar);

			if (data.high > mat.high.high)
			{
				// requires two higher high
				int lasthigh = mat.high.pos;
				while (((QuoteData) quotes[lasthigh]).high > ema20[lasthigh])
					lasthigh--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchDown(quotes, ema20, lasthigh);
				if (!(mat.high.high < mat2.high.high))
					return null;

				trade = new Trade(symbol);

				logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch down high is " + mat.high.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.high.high;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = mat.high.high;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_BUY, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_BUY, trade.POSITION_SIZE, mat.high.high);

				trade.stop = Utility.getLow(quotes, mat.high.pos, lastbar).high;
				if (mat.high.high - trade.stop > 14 * PIP_SIZE)
					trade.stop = mat.high.high - 14 * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());
				return trade;

			}

		}
		return null;

	}


	// this has not been finished, there's some problem with
	// findPastHighPeaksAboveMA
	public Trade checkHigherHighLowerLowSetup3(QuoteData data, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		calculateInitialBreakOut();

		if ((toBreakOutUp == false) && (toBreakOutDown == false))
			return null;

		/*
		 * Vector<Trade> previousTrade = findPreviousTrade(Constants.TRADE_RST);
		 * int preTrades = previousTrade.size(); if ( preTrades > 0 ) return
		 * null;
		 */

		// detect to see if there is a change of direction in the last 4-6 hours
		// Direction direction = calculatDirectionByCount2( quotes, ema20, 20);
		// if (data.close > ema20[lastbar])
		if ((this.toBreakOutDown == true) && (ema20L[lastbarL] < ema50L[lastbarL]))
		{
			int above20MA = lastbar;
			while (((QuoteData) quotes[above20MA]).low <= ema20[above20MA])
				above20MA--;

			logger.warning(symbol + " " + CHART + " touch calculate start " + ((QuoteData) quotes[above20MA]).time);
			MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, above20MA - 42, above20MA);

			if ((mat != null) && (mat.low != null))
			{
				if (data.low < mat.low.low)
				{
					for (int i = mat.low.pos; i < lastbar; i++)
					{
						if (((QuoteData) quotes[i]).low < mat.low.low)
							return null;
					}

					logger.warning(symbol + " " + CHART + " last touch up low is " + ((QuoteData) quotes[mat.low.pos]).time + "@" + mat.low.low);

					QuoteData high = Utility.getHigh(quotes, mat.low.pos, lastbar);
					if (high.high - mat.low.low > 40 * PIP_SIZE)
					{
						logger.warning(symbol + " " + CHART + " pull back too large, missed the boat");
						return null;
					}

					// if (((QuoteData)quotes[lastbar-1]).low < mat.low.low )
					// return null;
					trade = new Trade(symbol);
					trade.type = Constants.TRADE_RST;
					trade.action = Constants.ACTION_SELL;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.detectTime = ((QuoteData) quotes[lastbar]).time;
					trade.detectPrice = mat.low.low;
					trade.detectPos = lastbar;
					trade.status = Constants.STATUS_OPEN;

					// place order
					trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
					trade.price = mat.low.low;
					trade.entryPrice = trade.price;
					trade.status = Constants.STATUS_PLACED;
					trade.remainingPositionSize = trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
					logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);

					trade.stop = Utility.getHigh(quotes, mat.touchBegin, lastbar).high;
					if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
						trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return trade;
				}
			}
		}
		else if ((this.toBreakOutUp == true) && (ema20L[lastbarL] > ema50L[lastbarL]))
		{
			int below20MA = lastbar;
			while (((QuoteData) quotes[below20MA]).high >= ema20[below20MA])
				below20MA--;

			MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, below20MA - 42, below20MA);

			if ((mat != null) && (mat.high != null))
			{
				if (data.high > mat.high.high)
				{
					for (int i = mat.high.pos; i < lastbar; i++)
					{
						if (((QuoteData) quotes[i]).high > mat.high.high)
							return null;
					}

					logger.warning(symbol + " " + CHART + " last touch up high is " + ((QuoteData) quotes[mat.high.pos]).time + "@" + mat.high.high);

					// to see if we already missed the opportunity
					QuoteData low = Utility.getLow(quotes, mat.high.pos, lastbar);
					if (mat.high.high - low.low > 40 * PIP_SIZE)
					{
						logger.warning(symbol + " " + CHART + " pull back too large, missed the boat");
						return null;
					}

					// if (((QuoteData)quotes[lastbar-1]).high > mat.high.high )
					// return null;
					trade = new Trade(symbol);
					trade.type = Constants.TRADE_RST;
					trade.action = Constants.ACTION_BUY;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.detectTime = ((QuoteData) quotes[lastbar]).time;
					trade.detectPrice = mat.high.high;
					trade.detectPos = lastbar;
					trade.status = Constants.STATUS_OPEN;

					// place order
					trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
					trade.price = mat.high.high;
					trade.entryPrice = trade.price;
					trade.status = Constants.STATUS_PLACED;
					trade.remainingPositionSize = trade.POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;
					AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
					logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);

					trade.stop = Utility.getLow(quotes, mat.touchBegin, lastbar).low;
					if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
						trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

					trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
					logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																												// Long(oca).toString());

					return trade;
				}
			}
		}

		return null;
	}


	


	
	public Trade checkHigherHighLowerLowSetup3d(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			int startL = downPos;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;

			int downPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 3);
			if (downPos2 == Constants.NOT_FOUND)
				downPos2 = 3;
			int pushStartL = Utility.getHigh( quotesL, downPos2-3, startL).pos;
			int pullBackStart = Utility.getLow( quotesL, startL, downPos).pos;

			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last down open is at " + ((QuoteData)quotesL[downPos]).time);
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
			trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			trade.pullBackPos = pullBackStart;
	        return trade;

		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			int startL = upPos;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;

			int upPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 3);
			if (upPos2 == Constants.NOT_FOUND)
				upPos2 = 3;
			int pushStartL = Utility.getLow( quotesL, upPos2-3, startL).pos;
			int pullBackStart = Utility.getHigh( quotesL, startL, upPos).pos;

			//logger.warning(symbol + " " + CHART + " " + " buy detected " + data.time + " last up open is at " + ((QuoteData)quotesL[upPos]).time);
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
			trade.detectTime = quotes[lastbar].time;
			trade.pushStartL = pushStartL;
			trade.pullBackStartL = pullBackStart; 
			trade.pullBackPos = pullBackStart;
			return trade;
		}

		return null;
	}

	
	

	public Trade checkHigherHighLowerLowSetup3d_org(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema68L = Indicator.calculateEMA(quotesL, 68);
		double lastHighLow, thisHighLow;
		
		int direction = Constants.DIRECTION_UNKNOWN;
		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 3);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 3);

		if (upPos > downPos)
			direction = Constants.DIRECTION_UP;
		else if (downPos > upPos)
			direction = Constants.DIRECTION_DOWN;
		
		if ((direction == Constants.DIRECTION_DOWN)
				&& (( quotesL[lastbarL].high > ema20L[lastbarL]) || ( quotesL[lastbarL - 1].high > ema20L[lastbarL - 1])))
		{
			// list of filters
			//if ( EF_ifFirstSecondPullBackDown(quotesL, ema20L, 3, 2 ) == false)
			//	return null;
			
			int downBegin = downPos -1;
			while ((downBegin >= 0) && (((QuoteData)quotesL[downBegin]).high < ema20L[downBegin]))
				downBegin--;

			if ( downBegin > 0 )
			{
				/*
				for ( int i = lastbarL; i > downBegin; i--)
				{
					PushHighLow phl = Pattern.findLastNLow(quotesL, downBegin, i, 2);
					if (phl != null)
					{
						for ( int j = phl.pullBack.pos+1; j <=lastbarL; j++)
						{
							if ( quotesL[j].high > phl.pullBack.high )
								return null;
						}

						break;
					}
					
				}*/
				
				
				
				
				// check 1: to see if it did not make a newer high/low
				thisHighLow = Utility.getLow(quotesL, downBegin, downPos).low;
				
				// is there a chunk before this
				int upPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, downBegin-1, 3);
				int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, downBegin-1, 3);

				if ( downPos2 > upPos2 )
				{
					int downBegin2 = downPos2 -1;
					while (( downBegin2 >= 0) && (((QuoteData)quotesL[downBegin2]).high < ema20L[downBegin2]))
						downBegin2--;

					if ( downBegin2 > 0 )
					{
						lastHighLow = Utility.getLow(quotesL, downBegin2, downPos2+1).low;

						//if ( thisLow > lastLow )
						//{
							//logger.warning(symbol + " " + CHART + " " + " this low did not break new low, trade cancelled ");
							//logger.warning(symbol + " " + CHART + " " + " last high is between " + ((QuoteData)quotesL[upBegin2]).time + ((QuoteData)quotesL[upPos2+1]).time );
							//logger.warning(symbol + " " + CHART + " " + " this high is between " + ((QuoteData)quotesL[upBegin]).time + ((QuoteData)quotesL[upPos+1]).time );
						//	return null;
						//}
					}
				}
				
				/*
				// check 2, is there a reverse of trend, helps, not but that much
				int lowPos = Utility.getLow( quotesL, downBegin, lastbarL).pos;
				PushHighLow[] highs = Pattern.findPastHighs_no_need_highest(quotesL, lowPos, lastbarL);
				if ( highs != null )
				{
					for ( int i = 0; i < highs.length; i++)
					{
						if ( highs[i].curPos > highs[i].prePos + 4 )
						{
							logger.warning(symbol + " " + CHART + " " + " reversal potential detected, trade removed");
							trade = null;
							return null;
						}
					}
				}*/
			}

			/*
				// check the number of pull backs
				boolean pullBackRequired = false;
				QuoteData lastLow = Utility.getLow(quotesL, downBegin, lastbarL);
				for ( int i = lastLow.pos; i < lastbarL; i++)
				{
					if (((QuoteData)quotesL[i]).close > ((QuoteData)quotesL[i]).open)  // up green bar
					{
					    int j = i;
					    while (((j+1) <= lastbarL) && (((QuoteData)quotesL[j+1]).close > ((QuoteData)quotesL[j+1]).open))
					    	j++;
					    double extend = ((QuoteData)quotesL[j]).high - ((QuoteData)quotesL[i]).low;

					    if ((j > i) && ( extend > 30 * PIP_SIZE ))
					    {
							logger.warning(symbol + " " + CHART + " " + " down begin " + ((QuoteData)quotesL[downBegin]).time);
							logger.warning(symbol + " " + CHART + " " + " last low " + lastLow.time);
							logger.warning(symbol + " " + CHART + " " + " strong pull back up detect between " + ((QuoteData)quotesL[i]).time + " - " + ((QuoteData)quotesL[j]).time + ":" + extend/PIP_SIZE + " pips");
					    	pullBackRequired = true;
					    	break;
					    }

					}
				}

				if ( pullBackRequired )
				{
					for (int s = lastbarL; s > lastLow.pos; s--)
					{
						PushHighLow phl_cur = Pattern.findLastNHigh(quotesL, lastLow.pos, s, 1);
						if (phl_cur != null)
						{
							logger.warning(symbol + " " + CHART + " " + " pull back requirement met");
							pullBackRequired = false;
							break;
						}
					}

				}

				if ( pullBackRequired == true )
					return null;
					 	
			}*/


			//logger.warning(symbol + " " + CHART + " " + " sell detected " + data.time + " last down open is at " + ((QuoteData)quotesL[downPos]).time);
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
			trade.detectTime = quotes[lastbar].time;
			//trade.lastHighLow = thisHighLow;
	        return trade;

		}
		else if ((direction == Constants.DIRECTION_UP)
				&& ((quotesL[lastbarL].low < ema20L[lastbarL]) || ( quotesL[lastbarL - 1].low < ema20L[lastbarL - 1])))
		{
			//if ( EF_ifFirstSecondPullBackUp(quotesL, ema20L, 3, 2 ) == false)
			//	return null;

			int upBegin = upPos -1;
			while ((upBegin >= 0) && (((QuoteData)quotesL[upBegin]).low > ema20L[upBegin]))
				upBegin--;


			if ( upBegin > 0 )
			{
				/*
				for ( int i = lastbarL; i > upBegin; i--)
				{
					PushHighLow phl = Pattern.findLastNHigh(quotesL, upBegin, i, 2);
					if (phl != null)
					{
						for ( int j = phl.pullBack.pos+1; j <=lastbarL; j++)
						{
							if ( quotesL[j].low < phl.pullBack.low )
								return null;
						}
						break;
					}
					
				}*/

				

				// check 1: to see if it did not make a newer high/low
				thisHighLow = Utility.getHigh(quotesL, upBegin, upPos).high;

				// is there a chunk before this
				int upPos2 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, upBegin-1, 3);
				int downPos2 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, upBegin-1, 3);

				if ( upPos2 > downPos2 )
				{
					int upBegin2 = upPos2 -1;
					while (( upBegin2 >= 0) && (((QuoteData)quotesL[upBegin2]).low > ema20L[upBegin2]))
					    upBegin2--;

					if ( upBegin2 > 0 )
					{
						lastHighLow = Utility.getHigh(quotesL, upBegin2, upPos2+1).high;

						/*
						if ( thisHigh < lastHigh.high )
						{
							//logger.warning(symbol + " " + CHART + " " + " this high did not break new high, trade cancelled ");
							//logger.warning(symbol + " " + CHART + " " + " last high is between " + ((QuoteData)quotesL[upBegin2]).time + ((QuoteData)quotesL[upPos2+1]).time );
							//logger.warning(symbol + " " + CHART + " " + " this high is between " + ((QuoteData)quotesL[upBegin]).time + ((QuoteData)quotesL[upPos+1]).time );
							return null;
						}*/
					}
				}
				

				// check 2, is there a reverse of trend
				/*
				int highPos = Utility.getHigh( quotesL, upBegin, lastbarL).pos;
				PushHighLow[] lows = Pattern.findPast2Lows_no_need_lowest(quotesL, highPos, lastbarL);
				if ( lows != null )
				{
					for ( int i = 0; i < lows.length; i++)
					{
						if ( lows[i].curPos > lows[i].prePos + 4 )
						{
							logger.warning(symbol + " " + CHART + " " + " reversal potential detected, trade removed");
							return null;
						}
					}
				}*/

			}

					/*
					// counting if it is a large push from the top
					boolean pullBackRequired = false;
					QuoteData lastHigh = Utility.getHigh(quotesL, upBegin, lastbarL);
					for ( int i = lastHigh.pos; i < lastbarL; i++)
					{
						if (((QuoteData)quotesL[i]).close < ((QuoteData)quotesL[i]).open)
						{
						    int j = i;
						    while (((j+1) <= lastbarL) && (((QuoteData)quotesL[j+1]).close < ((QuoteData)quotesL[j+1]).open))
						    	j++;
						    double extend = ((QuoteData)quotesL[i]).high - ((QuoteData)quotesL[j]).low;

						    if ((j > i) &&( extend > 30 * PIP_SIZE ))
						    {
								logger.warning(symbol + " " + CHART + " " + " strong pull back detect between " + ((QuoteData)quotesL[i]).time + " - " + ((QuoteData)quotesL[j]).time + ":" + extend/PIP_SIZE + " pips");
						    	pullBackRequired = true;
						    	break;
						    }

						}
					}

					if ( pullBackRequired )
					{
						for (int s = lastbarL; s > lastHigh.pos; s--)
						{
							PushHighLow phl_cur = Pattern.findLastNLow(quotesL, lastHigh.pos, s, 1);
							if (phl_cur != null)
							{
								pullBackRequired = false;
								break;
							}
						}

					}

					if ( pullBackRequired == true )
						return null;
						
				}
			}*/

		   //logger.warning(symbol + " " + CHART + " " + " buy detected " + data.time + " last up open is at " + ((QuoteData)quotesL[upPos]).time);
			createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
			trade.detectTime = quotes[lastbar].time;
			//trade.lastHighLow = thisHighLow;//lastHighLow;
			return trade;
		}

		return null;
	}




	
	public Trade checkBreakingPullBackSetup(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);     // 5 = 60 
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);     // 5 = 60 
		double[] ema68L = Indicator.calculateEMA(quotesL, 68);     // 5 = 60 

		int lastConsecitveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar, 2 );
		int lastConsecitveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar, 2 );
		
		if ( ema20L[lastbarL] < ema68L[lastbarL])  
		{	
			lastConsecitveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastConsecitveUp, 2 );

			if ( lastConsecitveDown == Constants.NOT_FOUND )
				return null;
			
			int pullbackStart = Utility.getLow(quotes, lastConsecitveDown-10, lastConsecitveDown).pos;
			System.out.println("Pullback start is " + quotes[pullbackStart].time);
			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotes, pullbackStart, i, 2 );
				if ( phl_pre != null )
				{
					System.out.println("Last pullback from up is " + phl_pre.pullBack.time);
					if ( data.low < phl_pre.pullBack.low )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].low < phl_pre.pullBack.low)
								return null;

						trade = new Trade(symbol);
						
						logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );
						logger.warning(symbol + " " + CHART + "last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@" + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotes[phl_pre.curPos]).time + "@" + ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 

						// enter half position now and enter half at a limit order
						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_JPY;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = data.time;
						trade.detectPrice = phl_pre.pullBack.low;

						if (previousTradeExist(trade.type,Constants.ACTION_SELL)  )
						{
							logger.info(symbol + " " + CHART + " previous trade exist " + data.time);
							return null;
						}

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getHigh( quotes, pullbackStart, lastbar).high;
						if ( trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
						
						return trade;
					}
					break;
				}
			}
		}
		else if ( ema20L[lastbarL] < ema68L[lastbarL])  
		{	
			lastConsecitveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastConsecitveDown, 2 );

			if ( lastConsecitveUp == Constants.NOT_FOUND )
				return null;
					
			int pullbackStart = Utility.getHigh(quotes, lastConsecitveUp-10, lastConsecitveUp).pos;
			System.out.println("Pullback start is " + quotes[pullbackStart].time);
			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotes, pullbackStart, i, 2 );
				if ( phl_pre != null )
				{
					System.out.println("Last pullback from low is " + phl_pre.pullBack.time);
					if ( data.high > phl_pre.pullBack.high )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].high > phl_pre.pullBack.high)
								return null;
						
						trade = new Trade(symbol);

						logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );
						logger.warning(symbol + " " + CHART + " last low between " + ((QuoteData)quotes[phl_pre.prePos]).time + "-" + ((QuoteData)quotes[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 

						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_JPY;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = data.time;
						trade.detectPrice = phl_pre.pullBack.high;

						if (previousTradeExist(trade.type,Constants.ACTION_BUY) )
							return null;	

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						//AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.positionSize, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getLow( quotes, pullbackStart, lastbar).low;
						if ( trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new

						return trade;

					}
					break;
				}
			}
		}

		return null;
			
	}

	
	public Trade checkBreakingPullBackSetup2(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);     // 5 = 60 
		double[] ema100 = Indicator.calculateEMA(quotes, 240);   // 20 = 240
		double[] ema200 = Indicator.calculateEMA(quotes, 816);   // 68 = 816 

		
		if ( ema100[lastbar] < ema200[lastbar])  // trend is down, looking for pull backs up 
		{
			/*
			int crossOverPos = Pattern.findLastMACrossDown(ema100, ema200);
			if (crossOverPos > lastbar - 100 )
				return null;
			*/
			
			
			// looking for last close below 20MA
			int pullbackend = lastbar;
			while ( quotes[pullbackend].low < ema20[pullbackend] )
				pullbackend--;
			
			int lastConsecitveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, pullbackend, 5 );
			if (lastConsecitveDown == Constants.NOT_FOUND )
				return null;
			
			int crossDown = Pattern.findPriceCross20MADown(quotes, ema20, lastConsecitveDown, 5);
			if (crossDown == Constants.NOT_FOUND)
				return null;
			
			logger.warning(symbol + " " + CHART + " consective down is " + quotes[lastConsecitveDown].time );
			logger.warning(symbol + " " + CHART + " cross down is " + quotes[crossDown].time );
			
			int pullbackStart = Utility.getLow(quotes, lastConsecitveDown, crossDown).pos;
			
			if ( pullbackStart == Constants.NOT_FOUND )
				return null;
			
			logger.warning(symbol + " " + CHART + " pull back starting point is " + quotes[lastConsecitveDown].time );
			
			if (previousTradeExist(Constants.TRADE_USD,Constants.ACTION_SELL) || previousTradeExist(Constants.TRADE_CNT,Constants.ACTION_BUY) )
				return null;

			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotes, pullbackStart, i, 1 );
				if ( phl_pre != null )
				{
					if ( data.low < phl_pre.pullBack.low )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].low < phl_pre.pullBack.low)
								return null;

						/*
						boolean touch100ma = false;
						for ( int j = phl_pre.pullBack.pos; j <= lastbar; j++ )
							if ( quotes[j].high >= ema100[j])
								touch100ma = true;
						
						if ( touch100ma == false )
							return null;
						*/
						
						trade = new Trade(symbol);
						
						logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );
						logger.warning(symbol + " " + CHART + "last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@" + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotes[phl_pre.curPos]).time + "@" + ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 

						// enter half position now and enter half at a limit order
						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_pre.pullBack.low;

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getHigh( quotes, pullbackStart, lastbar).high;
						if ( trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
						
						// place limit order
						/*
						trade.limitPrice1 = adjustPrice(phl_pre.pullBack.low + FIXED_STOP/2 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.limitPos1 = trade.positionSize;
						trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

						trade.limit1Stop = adjustPrice(trade.limitPrice1 + FIXED_STOP  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.limit1Placed = true;
						logger.warning(symbol + " " + CHART + " limit order " + trade.limitPrice1 + "@" + trade.positionSize + " placed");
						*/
						return trade;
					}
					break;
				}
			}
		}
		else if ( ema100[lastbar] > ema200[lastbar])  // trend is down, looking for pull backs up 
		{
			/*
			int crossOverPos = Pattern.findLastMACrossUp(ema100, ema200);
			if (crossOverPos > lastbar - 100 )
				return null;
			 */
			
			// looking for last close below 20MA
			int pullbackend = lastbar;
			while ( quotes[pullbackend].high > ema20[pullbackend] )
				pullbackend--;
			
			int lastConsecitveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, pullbackend, 5 );
			if (lastConsecitveUp == Constants.NOT_FOUND )
				return null;
			
			int crossUp = Pattern.findPriceCross20MAUp(quotes, ema20, lastConsecitveUp, 5);
			if (crossUp == Constants.NOT_FOUND)
				return null;
			
			logger.warning(symbol + " " + CHART + " consective up is " + quotes[lastConsecitveUp].time );
			logger.warning(symbol + " " + CHART + " cross up is " + quotes[crossUp].time );
			
			int pullbackStart = Utility.getHigh(quotes, lastConsecitveUp, crossUp).pos;
			
			if ( pullbackStart == Constants.NOT_FOUND )
				return null;

			if (previousTradeExist(Constants.TRADE_USD,Constants.ACTION_BUY) || previousTradeExist(Constants.TRADE_CNT,Constants.ACTION_SELL) )
				return null;

			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotes, pullbackStart, i, 1 );
				if ( phl_pre != null )
				{
					if ( data.high > phl_pre.pullBack.high )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].high > phl_pre.pullBack.high)
								return null;
						
						/*
						boolean touch100ma = false;
						for ( int j = phl_pre.pullBack.pos; j <= lastbar; j++ )
							if ( quotes[j].low <= ema100[j])
								touch100ma = true;
						
						if ( touch100ma == false )
							return null;
*/
						trade = new Trade(symbol);
						/*
						if (ema20L[lastbarL] < ema50L[lastbarL])
						{
							//int maCrossOverPosL = Pattern.findLastMACrossDown( ema20L, ema50L, 20 );
							//if ( maCrossOverPosL == Constants.NOT_FOUND )
							//	maCrossOverPosL = lastbarL-16;
							//double low = Utility.getLow(quotesL, maCrossOverPosL, lastbarL).low;
							double low = Utility.getLow(quotesL, lastbarL-16, lastbarL).low;
							if ( low < entryQualifyPrice )
								entryQualifyPrice = low;
						}*/

						logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );
						logger.warning(symbol + " " + CHART + " last low between " + ((QuoteData)quotes[phl_pre.prePos]).time + "-" + ((QuoteData)quotes[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 

						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_RST;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = ((QuoteData) quotes[lastbar]).time;
						trade.detectPrice = phl_pre.pullBack.high;

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						//AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.positionSize, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getLow( quotes, pullbackStart, lastbar).low;
						if ( trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new

						// place limit order
						/*
						trade.limitPrice1 = adjustPrice(trade.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.limitPos1 = trade.positionSize;
						trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);

						trade.limit1Stop = adjustPrice(trade.limitPrice1 - FIXED_STOP * 2 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.limit1Placed = true;
						logger.warning(symbol + " " + CHART + " limit order " + trade.limitPrice1 + "@" + trade.positionSize + " placed");
						*/
						return trade;

					}
					break;
				}
			}
		}

		return null;
			
	}
	
	
	public Trade checkBreakingPullBackSetup3(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbarL = quotesL.length - 1;
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);     // 5 = 60 
		double[] ema50 = Indicator.calculateEMA(quotes, 50);     // 5 = 60 
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);     // 5 = 60 
		double[] ema68L = Indicator.calculateEMA(quotesL, 50);     // 5 = 60 

		if ((ema20[lastbar] > ema50[lastbar]) || (ema20[lastbar-1] > ema50[lastbar-1]) ||
		   (ema20[lastbar-2] > ema50[lastbar-2]) || (ema20[lastbar-3] > ema50[lastbar-3]))
		{	
			if (ema20L[lastbarL] > ema68L[lastbarL])
				return null;
			
			int crossUp = Pattern.findLastMACrossUp(ema20, ema50);
			if ( crossUp == Constants.NOT_FOUND )
				crossUp = Utility.getLow( quotes, lastbar - 20, lastbar ).pos;

			int pullbackStart = Utility.getLow(quotes, crossUp-10, crossUp).pos;
			
			//System.out.println(symbol + " " + CHART + "Pullback start is " + quotes[pullbackStart].time);
			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotes, pullbackStart, i, 2 );
				if ( phl_pre != null )
				{
					System.out.println("Last pullback from up is " + phl_pre.pullBack.time);
					if ( data.low < phl_pre.pullBack.low )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].low < phl_pre.pullBack.low)
								return null;

						trade = new Trade(symbol);
						
						logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );
						logger.warning(symbol + " " + CHART + "last high between " + ((QuoteData)quotes[phl_pre.prePos]).time + "@" + ((QuoteData)quotes[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotes[phl_pre.curPos]).time + "@" + ((QuoteData)quotes[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 

						// enter half position now and enter half at a limit order
						logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_JPY;
						trade.action = Constants.ACTION_SELL;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = data.time;
						trade.detectPrice = phl_pre.pullBack.low;

						if (previousTradeExist(trade.type,Constants.ACTION_SELL)  )
						{
							logger.info(symbol + " " + CHART + " previous trade exist " + data.time);
							return null;
						}

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.low;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;
						
						CreateTradeRecord(trade.type, Constants.ACTION_SELL);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getHigh( quotes, pullbackStart, lastbar).high;
						if ( trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
						
						return trade;
					}
					break;
				}
			}
		}
		else if ((ema20[lastbar] < ema50[lastbar]) || (ema20[lastbar-1] < ema50[lastbar-1]) ||
				   (ema20[lastbar-2] < ema50[lastbar-2]) || (ema20[lastbar-3] < ema50[lastbar-3]))
		{	
			if (ema20L[lastbarL] < ema68L[lastbarL])
				return null;

			int crossDown = Pattern.findLastMACrossUp(ema20, ema50);
			if ( crossDown == Constants.NOT_FOUND )
				crossDown = Utility.getHigh( quotes, lastbar - 20, lastbar ).pos;
			
			int pullbackStart = Utility.getHigh(quotes, crossDown-10, crossDown).pos;
					
			System.out.println(symbol + " " + CHART + "Pullback start is " + quotes[pullbackStart].time);
			for ( int i = lastbar; i > pullbackStart; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotes, pullbackStart, i, 2 );
				if ( phl_pre != null )
				{
					System.out.println("Last pullback from low is " + phl_pre.pullBack.time);
					if ( data.high > phl_pre.pullBack.high )       
					{	
						for ( int j = phl_pre.pullBack.pos+1; j < lastbar; j++ )
							if ( quotes[j].high > phl_pre.pullBack.high)
								return null;
						
						trade = new Trade(symbol);

						logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );
						logger.warning(symbol + " " + CHART + " last low between " + ((QuoteData)quotes[phl_pre.prePos]).time + "-" + ((QuoteData)quotes[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 

						logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);
						trade = new Trade(symbol);
						trade.type = Constants.TRADE_JPY;
						trade.action = Constants.ACTION_BUY;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.detectTime = data.time;
						trade.detectPrice = phl_pre.pullBack.high;

						if (previousTradeExist(trade.type,Constants.ACTION_BUY) )
							return null;	

						// place market order
						trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
						trade.price = phl_pre.pullBack.high;
						trade.entryPrice = trade.price;
						trade.status = Constants.STATUS_PLACED;
						trade.remainingPositionSize = trade.POSITION_SIZE;
						trade.entryTime = ((QuoteData) quotes[lastbar]).time;
						trade.entryPos = lastbar;
						trade.entryPosL = lastbarL;

						
						CreateTradeRecord(trade.type, Constants.ACTION_BUY);
						trade.recordOpened = true;
						AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
						//AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.positionSize, trade.price);
						logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

						trade.stop = Utility.getLow( quotes, pullbackStart, lastbar).low;
						if ( trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
							trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

						trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
						logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new

						return trade;

					}
					break;
				}
			}
		}

		return null;
			
	}
	

	
	
	
	
	public Trade checkBreakingHigherHighLowerLowSetup(QuoteData data, QuoteData prevData, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 50);

		int upPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 1);
		int downPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 1);
		/*
		if ( upPos > downPos)
		{
			if (previousTradeExist(Constants.ACTION_SELL, -1))
				return null;

			int start = -1;

			for ( int i = upPos-3; i > 0; i--)
			{
				if ( quotesL[i].low < ema20L[i] )
				{
					start = i;
					//System.out.println("start pos is " + quotesL[start].time);
					break;
				}
			}
			for ( int i = lastbarL; i > start; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotesL, start, i, 1 );
				if ( phl_pre != null )
				{
					//System.out.println(symbol + " " + CHART + "last high between " + ((QuoteData)quotesL[phl_pre.prePos]).time + "@" + ((QuoteData)quotesL[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotesL[phl_pre.curPos]).time + "@" + ((QuoteData)quotesL[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 
					if (( data.low < phl_pre.pullBack.low) && (( prevData != null ) && (prevData.low > phl_pre.pullBack.low)))       
					{	
						return placeBreakingHigherHighLowerLowSellTrade(data, phl_pre, quotes);
					}
					break;
				}
			}

			//logger.warning(symbol + " " + CHART + " upPos is " + quotesL[upPos].time );
			// look for immediate 20MA pull back
			
			// looking for breaking up trend
			start = Utility.getLow(quotesL, upPos-15, upPos).pos;
			int preDown = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, upPos, 1);
			if ( preDown != Constants.NOT_FOUND )
			{
				start = Utility.getLow(quotesL, preDown-5, preDown).pos;      ////this is wrong!!!
			}
			
			//logger.warning(symbol + " " + CHART + " startPos is " + quotesL[start].time );
			for ( int i = lastbarL; i > start; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotesL, start, i, 1 );
				if ( phl_pre != null )
				{
					//System.out.println(symbol + " " + CHART + "last high between " + ((QuoteData)quotesL[phl_pre.prePos]).time + "@" + ((QuoteData)quotesL[phl_pre.prePos]).high + "  -  " + ((QuoteData)quotesL[phl_pre.curPos]).time + "@" + ((QuoteData)quotesL[phl_pre.curPos]).high + " pullback@" + phl_pre.pullBack ); 
					if (( data.low < phl_pre.pullBack.low) && (( prevData != null ) && (prevData.low > phl_pre.pullBack.low)))       
					{	
						return placeBreakingHigherHighLowerLowSellTrade(data, phl_pre, quotes);
					}
					break;
				}
			}
		}
		else*/ if ( downPos > upPos )
		{
			if (previousTradeExist(Constants.ACTION_BUY, -1))
				return null;

			// looking for breaking up trend
			// 1. Look for immediate break from 20 MA
			int start = -1;
			//PushHighLow phl_pre1 = null;
			//PushHighLow phl_pre2 = null;

			for ( int i = downPos-3; i > 0; i--)
			{
				if ( quotesL[i].high > ema20L[i] )
				{
					start = i;
					//System.out.println("start pos is " + quotesL[start].time);
					break;
				}
			}
			for ( int i = lastbarL; i > start; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotesL, start, i, 1 );
				if ( phl_pre != null )
				{
					//logger.warning(symbol + " " + CHART + " last low between " + ((QuoteData)quotesL[phl_pre.prePos]).time + "-" + ((QuoteData)quotesL[phl_pre.curPos]).time + " pullback@" + phl_pre.pullBack ); 
					if (( data.high > phl_pre.pullBack.high) && ( prevData != null ) && ( prevData.high < phl_pre.pullBack.high))       
					//if (( data.high > phl_pre.pullBack.high) )       
					{	
						logger.warning(symbol + " " + CHART + " break last pull back at " + data.time);
						return placeBreakingHigherHighLowerLowBuyTrade(data, phl_pre, quotes);
					}
					break;
				}
			}
			
			
			start = Utility.getHigh(quotesL, downPos-15, downPos).pos;
			int preUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, upPos, 1);
			if ( preUp != Constants.NOT_FOUND )
			{
				start = Utility.getHigh(quotesL, preUp-5, preUp).pos;
			}

			for ( int i = lastbarL; i > start; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotesL, start, i, 1 );
				if ( phl_pre != null )
				{
					if (( data.high > phl_pre.pullBack.high) && ( prevData != null ) && ( prevData.high < phl_pre.pullBack.high))       
					{	
						return placeBreakingHigherHighLowerLowBuyTrade(data, phl_pre, quotes);
					}
					break;
				}
			}
		}

		return null;
			
	}


	

	private Trade placeBreakingHigherHighLowerLowSellTrade(QuoteData data, PushHighLow phl_pre, QuoteData[] quotes)
	{
		int lastbar = quotes.length - 1;
		
		trade = new Trade(symbol);
		
		logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time );

		// enter half position now and enter half at a limit order
		logger.warning(symbol + " " + CHART + " trade detected SELL at " + data.time + "@" + trade.price);
		trade = new Trade(symbol);
		trade.type = Constants.TRADE_EUR;
		trade.action = Constants.ACTION_SELL;
		trade.pullBackPos = phl_pre.pullBack.pos;
		trade.POSITION_SIZE = POSITION_SIZE;
		trade.detectTime = ((QuoteData) quotes[lastbar]).time;
		trade.detectPrice = phl_pre.pullBack.low;

		if (previousTradeExist(Constants.TRADE_EUR, -1))
			return null;

		// place market order
		trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
		trade.price = phl_pre.pullBack.low;
		trade.entryPrice = trade.price;
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.entryTime = ((QuoteData) quotes[lastbar]).time;
		CreateTradeRecord(trade.type, Constants.ACTION_SELL);
		trade.recordOpened = true;
		AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
		logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

		trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

		trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
		
		// place limit order
		/*
		trade.limitPrice1 = adjustPrice(phl_pre.pullBack.low + FIXED_STOP/2 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
		trade.limitPos1 = trade.positionSize;
		trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

		trade.limit1Stop = adjustPrice(trade.limitPrice1 + FIXED_STOP  * PIP_SIZE, Constants.ADJUST_TYPE_UP);
		trade.limit1Placed = true;
		logger.warning(symbol + " " + CHART + " limit order " + trade.limitPrice1 + "@" + trade.positionSize + " placed");
		*/
		return trade;
		
	}
	
	private Trade placeBreakingHigherHighLowerLowBuyTrade(QuoteData data, PushHighLow phl_pre, QuoteData[] quotes)
	{
		int lastbar = quotes.length - 1;
		
		trade = new Trade(symbol);

		logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time );

		logger.warning(symbol + " " + CHART + " trade detected BUY at " + data.time + "@" + trade.price);
		trade = new Trade(symbol);
		trade.type = Constants.TRADE_EUR;
		trade.action = Constants.ACTION_BUY;
		trade.pullBackPos = phl_pre.pullBack.pos;
		trade.POSITION_SIZE = POSITION_SIZE;
		trade.detectTime = ((QuoteData) quotes[lastbar]).time;
		trade.detectPrice = phl_pre.pullBack.high;

		if (previousTradeExist(Constants.TRADE_EUR, -1))
			return null;
		// place market order
		trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
		trade.price = phl_pre.pullBack.high;
		trade.entryPrice = trade.price;
		trade.status = Constants.STATUS_PLACED;
		trade.remainingPositionSize = trade.POSITION_SIZE;
		trade.entryTime = ((QuoteData) quotes[lastbar]).time;
		CreateTradeRecord(trade.type, Constants.ACTION_BUY);
		trade.recordOpened = true;
		AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
		//AddTradeOpenRecord(trade.type, data.time, Constants.ACTION_BUY, trade.positionSize, trade.price);
		logger.warning(symbol + " " + CHART + " market order " + trade.price + "@" + trade.POSITION_SIZE + " placed");

		trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

		trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
		logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
		trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new

		// place limit order
		/*
		trade.limitPrice1 = adjustPrice(trade.price - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
		trade.limitPos1 = trade.positionSize;
		trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);

		trade.limit1Stop = adjustPrice(trade.limitPrice1 - FIXED_STOP * 2 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
		trade.limit1Placed = true;
		logger.warning(symbol + " " + CHART + " limit order " + trade.limitPrice1 + "@" + trade.positionSize + " placed");
		*/
		return trade;
		
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

	public Trade CrossSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarL = quotesL.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		double[] ema50L = Indicator.calculateEMA(quotesL, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);
		// int hr = new Integer(data.time.substring(9,12).trim());
		// String min = data.time.substring(13,15);

		// int lastMACrossUp = Pattern.findLastMACrossUp(ema20, ema50, 12);
		// int lastMACrossDown = Pattern.findLastMACrossDown(ema20, ema50, 12);
		// int lastPriceConsectiveUp =
		// Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar-2, 3);
		// int lastPriceConsectiveDown =
		// Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar -2, 3);
		// int lastConsectiveUp = Pattern.findLastPriceConsectiveAboveMA(
		// quotes, ema20, 5);
		// int lastConsectiveDown = Pattern.findLastPriceConsectiveBelowMA(
		// quotes, ema20, 5);
		int lastPriceConsectiveUpL = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		int lastPriceConsectiveDownL = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);

		if ((((QuoteData) quotes[lastbar]).high < ema20[lastbar]) && (((QuoteData) quotes[lastbar - 1]).high < ema20[lastbar - 1]))
		{
			int pos = lastbar;
			while (((QuoteData) quotes[pos]).low < ema20[pos])
				pos--;

			// now it is the touch point
			// 1. look for the first place that it was below MA
			int begin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, pos, 3);

			// 2. check how many "up" peaks in between
			Object[] peaks = Pattern.findPastHighPeaks2AboveMA(quotes, ema20, begin, pos);

			if ((peaks == null) || (peaks.length < 2))
				return null;

			// if ( ema20L[lastbarL]> ema50L[lastbarL])
			if (lastPriceConsectiveUpL > lastPriceConsectiveDownL)
				return null;

			trade = new Trade(symbol);
			logger.warning(symbol + " " + CHART + " minute cross to low detected at " + data.time);
			trade.type = Constants.TRADE_CRS;
			trade.action = Constants.ACTION_SELL;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPos = lastbar;
			trade.status = Constants.STATUS_OPEN;

			return trade;
		}
		else if ((((QuoteData) quotes[lastbar]).low > ema20[lastbar]) && (((QuoteData) quotes[lastbar - 1]).low > ema20[lastbar - 1]))
		{
			int pos = lastbar;
			while (((QuoteData) quotes[pos]).high > ema20[pos])
				pos--;

			// now it is the touch point
			// 1. look for the first place that it was below MA
			int begin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, pos, 3);

			// 2. check how many "up" peaks in between
			Object[] peaks = Pattern.findPastLowPeaks2BelowMA(quotes, ema20, begin, pos);

			if ((peaks == null) || (peaks.length < 2))
				return null;

			// if ( ema20L[lastbarL]< ema50L[lastbarL])
			if (lastPriceConsectiveUpL < lastPriceConsectiveDownL)
				return null;

			trade = new Trade(symbol);
			logger.warning(symbol + " " + CHART + " minute cross to high detected at " + data.time);
			trade.type = Constants.TRADE_CRS;
			trade.action = Constants.ACTION_BUY;
			trade.POSITION_SIZE = POSITION_SIZE;
			trade.detectTime = ((QuoteData) quotes[lastbar]).time;
			trade.detectPos = lastbar;
			trade.status = Constants.STATUS_OPEN;

			return trade;
		}

		return null;
	}



	public Trade HigherHighLowerLowRSTSetup(QuoteData data, int minCrossBar, Object[] quotesL, Object[] quotesS)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		// double[] ema50 = Indicator.calculateEMA(quotes, 50);

		int lastConsectiveUp = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, 5);
		int lastConsectiveDown = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, 5);

		if (lastConsectiveUp > lastConsectiveDown)
		{
			int begin = Pattern.findLastPriceConsectiveBelowMA(quotes, ema20, lastbar - 3, 5);

			MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, lastbar);
			/*
			 * logger.warning(symbol + " " + CHART + " trend is up " +
			 * data.time); logger.warning(symbol + " " + CHART +
			 * " last consecitveAbove20MA is " +
			 * ((QuoteData)quotes[lastConsectiveUp]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last consecitveBelow20MA is " +
			 * ((QuoteData)quotes[lastConsectiveDown]).time);
			 * logger.warning(symbol + " " + CHART +
			 * " last 20MA touch up low is " + mat.lowest );
			 */
			if (data.low < mat.low.low)
			{
				// requires two higher high
				int lastlow = mat.low.pos;
				while (((QuoteData) quotes[lastlow]).low < ema20[lastlow])
					lastlow--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchUp(quotes, ema20, lastlow);
				if (!(mat.low.low > mat2.low.low))
					return null;

				// last check large data chart
				trade = new Trade(symbol);
				logger.warning(symbol + " " + CHART + " minute Higher high break up SELL at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch up low is " + mat.low.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_SELL;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.low.low;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				trade.entryPrice = Utility.getHigh(quotes, begin, lastbar).high;
				return trade;

			}

		}
		else if (lastConsectiveUp < lastConsectiveDown)
		{
			int begin = Pattern.findLastPriceConsectiveAboveMA(quotes, ema20, lastbar - 3, 5);
			MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, lastbar);

			if (data.high > mat.high.high)
			{
				// requires two higher high
				int lasthigh = mat.high.pos;
				while (((QuoteData) quotes[lasthigh]).high > ema20[lasthigh])
					lasthigh--;
				// now lastlow is above again
				MATouch mat2 = Pattern.findLastMATouchDown(quotes, ema20, lasthigh);
				if (!(mat.high.high < mat2.high.high))
					return null;

				trade = new Trade(symbol);

				logger.warning(symbol + " " + CHART + " minute Lower low break up BUY at " + data.time);
				logger.warning(symbol + " " + CHART + " last 20MA touch down high is " + mat.high.time);

				trade.type = Constants.TRADE_RST;
				trade.action = Constants.ACTION_BUY;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.detectTime = ((QuoteData) quotes[lastbar]).time;
				trade.detectPrice = mat.high.high;
				trade.detectPos = lastbar;
				trade.status = Constants.STATUS_OPEN;

				trade.entryPrice = Utility.getLow(quotes, begin, lastbar).low;
				return trade;

			}

		}
		return null;

	}

	public void HigherHighLowerLowRSTSetupEntry(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		if (trade.action.equals(Constants.ACTION_SELL))
		{
			if (data.close > trade.entryPrice)
			{
				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = data.close;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_SELL, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_SELL, trade.POSITION_SIZE, data.close);

				trade.stop = data.close + 14 * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());

				return;
			}
		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			if (data.close < trade.entryPrice)
			{
				// place order
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = data.close;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = ((QuoteData) quotes[lastbar]).time;
				trade.entryPos = lastbar;
				// AddTradeOpenRecord( trade.type, data.time,
				// Constants.ACTION_BUY, trade.positionSize, data.close);
				AddTradeOpenRecord(trade.type, ((QuoteData) quotes[lastbar]).time, Constants.ACTION_BUY, trade.POSITION_SIZE, data.close);

				trade.stop = data.close - 14 * PIP_SIZE;
				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																											// Long(oca).toString());
				return;

			}
		}
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//
	// Trade Entry
	//
	//
	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	public void trackTradeEntry(QuoteData data, QuoteData[] quotesS, QuoteData[] quotesL)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if (trade != null)
		{
			//if (Constants.TRADE_EUR.equals(trade.type))
				trackHigherHighLowerLowEntry(data, quotesL);
				//trackHigherHighLowerLowCupAndHandleEntry(data, quotesL);
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

	public void trackCrossEntry(QuoteData data, Object[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +
		// trade.detectTime);
		Object[] quotes = getQuoteData();
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbar = quotes.length - 1;

		if (lastbar == trade.detectPos)
			return;

		int touch20MAPos = -1;

		for (int i = trade.detectPos + 1; i < lastbar; i++)
		{
			if ((((QuoteData) quotes[i]).low < ema20[i]) && (((QuoteData) quotes[i]).high > ema20[i]))
			{
				touch20MAPos = i;
				break;
			}
		}

		if (touch20MAPos == -1)
			return;

		for (int i = touch20MAPos + 2; i <= lastbar; i++)
		{
			if ((((QuoteData) quotes[i - 1]).high < ema20[i - 1]) && (((QuoteData) quotes[i - 2]).high < ema20[i - 2]))
			{
				// first breakout downside
				if (trade.action.equals(Constants.ACTION_SELL))
				{
					// place order
					logger.warning(symbol + " " + CHART + " cross sell triggered at " + data.time);
					trade.price = data.close;
					trade.entryPrice = trade.price;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;

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

					return;

				}
				else if (trade.action.equals(Constants.ACTION_BUY))
				{
					trade = null;
					return;
				}
			}
			else if ((((QuoteData) quotes[i - 1]).low > ema20[i - 1]) && (((QuoteData) quotes[i - 2]).low > ema20[i - 2]))
			{
				if (trade.action.equals(Constants.ACTION_BUY))
				{
					// place order
					logger.warning(symbol + " " + CHART + " cross buy triggered at " + data.time);
					trade.price = data.close;
					trade.entryPrice = data.close;
					trade.position = Constants.POSITION_LONG;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.entryTime = ((QuoteData) quotes[lastbar]).time;
					trade.entryPos = lastbar;

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

				}
				else if (trade.action.equals(Constants.ACTION_SELL))
				{
					trade = null;
					return;
				}

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



	
	
	public void trackHigherHighLowerLowEntry(QuoteData data, QuoteData[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +  trade.detectTime);
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		if ( trade.limit1Placed == true )
			return;

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			// filter out bad entries
			int startL = lastbarL;
			while ( quotesL[startL].high >= ema20L[startL])
				startL--;
			while ( quotesL[startL].high < ema20L[startL])
				startL--;
			
			int startL0 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, startL, 2);
			if ( startL0 == Constants.NOT_FOUND )
				startL0 = startL - 10;
			
			startL = Utility.getHigh( quotesL, startL0, startL).pos;
			//logger.warning(symbol + " " + CHART + " going down last push starting point is " + quotesL[startL].time);

			/*
			if ( data.high > quotesL[startL].high )
			{
				trade = null;
				return;
			}
			for ( int i = lastbarL; i > startL; i--)
			{	
				PushHighLow phl_pre = findLastNLow( quotesL, startL, i, 1 );
				if ( phl_pre != null )
				{
					if (( data.high > phl_pre.pullBack.high))
					{
						logger.warning(symbol + " " + CHART + " breaks higher high lower low, remove trade" + data.time);
						trade = null;
						return;
					}
					break;
				}
			}*/

			
			
			
			
			
			
			
			
			//int start = Utility.getHigh(quotes, lastbar - 36, lastbar - 3).pos;
			int detectpos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getLow(quotes, detectpos-10, detectpos-1).pos;
			for (int s = lastbar; s > start; s--)
			{
				PushHighLow phl_cur = Pattern.findLastNHigh(quotes, start, s, 2);
				if (phl_cur != null)
				{
					/*
					logger.warning(symbol + " " + CHART + "@@@this entry low between " +
							 ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " +
							 ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" +
							 phl_cur.pullBack.time );
					 */

					//if (previousTradeExist(trade.type, phl_cur.pullBack.pos))
					if (previousTradeExist(trade.type, Constants.ACTION_SELL))
						return;

					if ( phl_cur.curPos == lastbar )
					{
						if ((prremptive_limit == true) && (( quotes[phl_cur.prePos].high - phl_cur.pullBack.low )  > FIXED_STOP * 1.5 * PIP_SIZE))
						{
							/*
							logger.warning(symbol + " " + CHART + " detected for limit order " + data.time);
							trade.pullBackPos = phl_cur.pullBack.pos;

							trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limitPos1 = trade.positionSize;
							trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

							trade.limit1Stop = adjustPrice(trade.limitPrice1 + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.limit1Placed = true;
							return;*/
							
							trade.orderId = placeMktOrder(Constants.ACTION_SELL, POSITION_SIZE);
							trade.price = quotes[phl_cur.prePos].high;
							trade.entryPrice = trade.price;
							trade.status = Constants.STATUS_PLACED;
							trade.POSITION_SIZE = POSITION_SIZE;
							trade.remainingPositionSize = POSITION_SIZE;
							trade.entryTime = quotes[lastbar].time;

							if (trade.recordOpened == false)
							{
								CreateTradeRecord(trade.type, Constants.ACTION_SELL);
								trade.recordOpened = true;
							}
							AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, trade.price);

							trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

							trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
							logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);
							return;

						}

						if (breakout_limit == true)
						{	
							for ( int i = phl_cur.prePos; i > start; i--)
							{
								PushHighLow phl_pre = Pattern.findLastNHigh( quotes, start, i, 3 );
								if (( phl_pre != null ))//&& (prremptive_limit == true))
								{
	
									// check 2: large break outs
									if (((((QuoteData) quotes[phl_pre.prePos]).high - phl_pre.pullBack.low)  > ENTRY_LARGE_PULLBACK * PIP_SIZE))
									{
										// if the pull back is larger than 20 pips, put one
										// limit positions
										if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > ENTRY_LARGE_BREAKOUT * PIP_SIZE) && (trade.limit2Placed == false))
										{
											logger.warning(symbol + " " + CHART + " detected for limit order " + data.time);
											trade.pullBackPos = phl_cur.pullBack.pos;
	
											trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + PIP_SIZE, Constants.ADJUST_TYPE_UP);
											trade.limitPos1 = trade.POSITION_SIZE;
											trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
	
											trade.limit1Stop = adjustPrice(trade.limitPrice1 + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
											trade.limit1Placed = true;
											return;
										}
									}
									
									break;
								}
							}
						}
					}
					else
					{
						trade.entryPullBackLow = phl_cur.pullBack.low;
						trade.entryPullBackTime = phl_cur.pullBack.time;
						break;
					}
				}
			}

			checkTriggerMarketSell(data.low);
			return;

		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			int startL = lastbarL;
			while ( quotesL[startL].low <= ema20L[startL])
				startL--;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			
			int startL0 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 2);
			if ( startL0 == Constants.NOT_FOUND )
				startL0 = startL - 10;
			
			startL = Utility.getLow( quotesL, startL0, startL).pos;
			//logger.warning(symbol + " " + CHART + " going up last push starting point is " + quotesL[startL].time);

			/*
			if ( data.low < quotesL[startL].low )
			{
				trade = null;
				return;
			}
			for ( int i = lastbarL; i > startL; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotesL, startL, i, 1 );
				if ( phl_pre != null )
				{
					if (( data.low < phl_pre.pullBack.low))
					{
						//logger.warning(symbol + " " + CHART + " touched 20MA but already broken higher high" + data.time);
						trade = null;
						return;
					}
					break;
				}
			}*/
			
			

			
			int detectpos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getHigh(quotes, detectpos-10, detectpos-1).pos;
			for (int s = lastbar; s > start; s--)
			{
				PushHighLow phl_cur = Pattern.findLastNLow(quotes, start, s, 2);
				if (phl_cur != null)
				{
					/*logger.warning(symbol + " " + CHART + "@@@this entry low between " +
							 ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " +
							 ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" +
							 phl_cur.pullBack.time );
					*/
					//if (previousTradeExist(trade.type,phl_cur.pullBack.pos))
					if (previousTradeExist(trade.type,Constants.ACTION_BUY))
						return;

					if ( phl_cur.curPos == lastbar )
					{
						for ( int i = phl_cur.prePos; i > start; i--)
						{
							PushHighLow phl_pre = Pattern.findLastNLow( quotes, start, i, 3 );
							if (( phl_pre != null ))//&& (prremptive_limit == true))
							{
								if ((prremptive_limit == true)&&(((QuoteData) quotes[phl_pre.prePos]).low - ((QuoteData) quotes[phl_pre.curPos]).low > ENTRY_LARGE_BREAKOUT * PIP_SIZE))
								{
									logger.warning(symbol + " " + CHART + " detected for buy limit order for large pull backs" + data.time);
									trade.pullBackPos = phl_cur.pullBack.pos;

									// place order public int placeLmtOrder(String
									// action, double limitPrice, int posSize, String
									// ocaGroup)
									trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.limitPos1 = trade.POSITION_SIZE;
									trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
									logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

									trade.limit1Stop = adjustPrice(trade.limitPrice1 - 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.limit1Placed = true;
									return;

								}

								// check 2: large break outs
								if ((breakout_limit == true) &&(phl_pre.pullBack.high - ((QuoteData) quotes[phl_pre.prePos]).low > ENTRY_LARGE_PULLBACK * PIP_SIZE))
								{
									logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
									trade.pullBackPos = phl_cur.pullBack.pos;

									trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.limitPos1 = trade.POSITION_SIZE;
									trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
									logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

									trade.limit1Stop = adjustPrice(trade.limitPrice1 - 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.limit1Placed = true;
									return;
								}

								break;
							}
						}
					}
					else
					{
						trade.entryPullBackHigh = phl_cur.pullBack.high;
						trade.entryPullBackTime = phl_cur.pullBack.time;
						break;
					}
				}
			}

			checkTriggerMarketBuy(data.high);
			return;
		}
	}

	

	
	public void trackHigherHighLowerLowCupAndHandleEntry(QuoteData data, QuoteData[] quotesL)
	{
		// logger.info(symbol + " " + trade.type + " track trade entry " +  trade.detectTime);
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		//double[] ema20 = Indicator.calculateEMA(quotes, 20);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		if (trade.action.equals(Constants.ACTION_SELL))
		{
			//int prevPos = trade.phl.prePos;
			//int triggerPos = trade.phl.curPos;
			
			if (data.low < quotesL[trade.pullBackStartL].low) 
			{
				logger.info(symbol + " " + CHART + " price return to below pull back low, trade missed");
				trade = null;
				
				// cancel all limit orders here
				return;
				
			}

			int pullbackStart = Utility.findPositionByHour(quotes, quotesL[trade.pullBackStartL].time, Constants.BACK_TO_FRONT );
			System.out.println(symbol + " " + CHART + " pull back start is " + quotes[pullbackStart].time );
			
			/*
			int touchPos = 	Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, 2);
			while ( quotesL[touchPos].high < ema20L[touchPos])
				touchPos--;
			int touchPosBegin = touchPos;
			while ( quotesL[touchPosBegin].high > ema20L[touchPosBegin])
				touchPosBegin--;
			QuoteData lastTouch20MA = Utility.getHigh(quotesL, touchPosBegin+1, touchPos);
			if (( lastTouch20MA.pos > trade.pushStartL ) && ( quotesL[lastbarL].high > lastTouch20MA.high ))
			{
				System.out.println(symbol + " " + CHART + " breaks last low, trade cancelled");
				trade = null;
				return;
				
			}

			
			for ( int i = lastbarL; i > trade.pushStartL + 2; i--)
			{
				PushHighLow[] phls = Pattern.findPast2Lows(quotesL, trade.pushStartL, i );
				if ( phls != null )
				{
					if ( quotesL[lastbarL].high > phls[0].pullBack.high )
					{
						System.out.println(symbol + " " + CHART + " breaks last high, trade cancelled");
						trade = null;
						return;
					}
					break;
				}
			}
			

			
			System.out.println(symbol + " " + CHART + " trigger position is at " + quotes[triggerPos].time);
			if (lastbar == triggerPos + 2 )
			{
				// now is the third bar;
				if (( quotes[triggerPos].high - quotes[prevPos].high) < 5 * PIP_SIZE )
				{
					if  (( quotes[triggerPos+1].high - quotes[triggerPos].high) < 3 * PIP_SIZE )
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar].close + " @ " + quotes[lastbar].time);
						enterSellPosition(quotes[lastbar].close);
						trade.status = Constants.STATUS_PLACED;
						return;
					}
				}
			}
			
			
			for ( int i = triggerPos + 2; i <= lastbar; i++)
			{	
				PushHighLow phl = Pattern.findLastNHigh(quotes, triggerPos, i, 1 );
				if ( phl!= null )
				{
					logger.info(symbol + " " + CHART + " trade entry push detected at " + quotes[phl.curPos].time );
					if  (( lastbar >= phl.curPos + 1 ) && ( quotes[lastbar].low < quotes[lastbar-1].low ))
					{
						logger.info(symbol + " " + CHART + " trade entered at " + quotes[lastbar-1].low + " @ " + quotes[lastbar].time);
						//trade.stop = adjustPrice(Utility.getHigh(quotes,triggerPos, lastbar).high, Constants.ADJUST_TYPE_UP);
						enterSellPosition(quotes[lastbar].close);
						trade.status = Constants.STATUS_PLACED;
						return;
					}
				}
				
			}

			// enter at the break of lower low 
			/*
			for ( int i = lastbar-2; i > triggerPos; i--)
			{
				if ( quotes[i+1].low > quotes[i].low )
				{
					for ( int j = i+2; j < lastbar; j++ )
					{
						if 	( quotes[j].low < quotes[i].low)
							break;	// already passed
					}
					
					if ( quotes[lastbar].low < quotes[i].low)
					{
						logger.info(symbol + " " + CHART + " trade entered with breaking lower high at " + quotes[i].high + " @ " + quotes[lastbar].time);
						enterBuyPosition(quotes[lastbar].close);
						trade.status = Constants.STATUS_PLACED;
						return;
					}
					break;
				}
			}*/

			checkTriggerMarketSell(data.low);
			return;

		}
		else if (trade.action.equals(Constants.ACTION_BUY))
		{
			int startL = lastbarL;
			while ( quotesL[startL].low <= ema20L[startL])
				startL--;
			while ( quotesL[startL].low > ema20L[startL])
				startL--;
			
			int startL0 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, startL, 2);
			if ( startL0 == Constants.NOT_FOUND )
				startL0 = startL - 10;
			
			startL = Utility.getLow( quotesL, startL0, startL).pos;
			//logger.warning(symbol + " " + CHART + " going up last push starting point is " + quotesL[startL].time);

			/*
			if ( data.low < quotesL[startL].low )
			{
				trade = null;
				return;
			}
			for ( int i = lastbarL; i > startL; i--)
			{	
				PushHighLow phl_pre = findLastNHigh( quotesL, startL, i, 1 );
				if ( phl_pre != null )
				{
					if (( data.low < phl_pre.pullBack.low))
					{
						//logger.warning(symbol + " " + CHART + " touched 20MA but already broken higher high" + data.time);
						trade = null;
						return;
					}
					break;
				}
			}*/
			
			

			
			int detectpos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			int start = Utility.getHigh(quotes, detectpos-10, detectpos-1).pos;
			for (int s = lastbar; s > start; s--)
			{
				PushHighLow phl_cur = Pattern.findLastNLow(quotes, start, s, 2);
				if (phl_cur != null)
				{
					/*logger.warning(symbol + " " + CHART + "@@@this entry low between " +
							 ((QuoteData)quotes[phl_cur.prePos]).time + "@" + ((QuoteData)quotes[phl_cur.prePos]).high + "  -  " +
							 ((QuoteData)quotes[phl_cur.curPos]).time + "@" + ((QuoteData)quotes[phl_cur.curPos]).high + " pullback@" +
							 phl_cur.pullBack.time );
					*/
					//if (previousTradeExist(trade.type,phl_cur.pullBack.pos))
					if (previousTradeExist(trade.type,Constants.ACTION_BUY))
						return;

					if ( phl_cur.curPos == lastbar )
					{
						for ( int i = phl_cur.prePos; i > start; i--)
						{
							PushHighLow phl_pre = Pattern.findLastNLow( quotes, start, i, 3 );
							if (( phl_pre != null ))//&& (prremptive_limit == true))
							{
								if ((prremptive_limit == true)&&(((QuoteData) quotes[phl_pre.prePos]).low - ((QuoteData) quotes[phl_pre.curPos]).low > ENTRY_LARGE_BREAKOUT * PIP_SIZE))
								{
									logger.warning(symbol + " " + CHART + " detected for buy limit order for large pull backs" + data.time);
									trade.pullBackPos = phl_cur.pullBack.pos;

									// place order public int placeLmtOrder(String
									// action, double limitPrice, int posSize, String
									// ocaGroup)
									trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.limitPos1 = trade.POSITION_SIZE;
									trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
									logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

									trade.limit1Stop = adjustPrice(trade.limitPrice1 - 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.limit1Placed = true;
									return;

								}

								// check 2: large break outs
								if ((breakout_limit == true) &&(phl_pre.pullBack.high - ((QuoteData) quotes[phl_pre.prePos]).low > ENTRY_LARGE_PULLBACK * PIP_SIZE))
								{
									logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
									trade.pullBackPos = phl_cur.pullBack.pos;

									trade.limitPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
									trade.limitPos1 = trade.POSITION_SIZE;
									trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);
									logger.warning(symbol + " " + CHART + " limit order place " + trade.limitPrice1 + " " + trade.limitPos1 + " " + data.time);

									trade.limit1Stop = adjustPrice(trade.limitPrice1 - 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
									trade.limit1Placed = true;
									return;
								}

								break;
							}
						}
					}
					else
					{
						trade.entryPullBackHigh = phl_cur.pullBack.high;
						trade.entryPullBackTime = phl_cur.pullBack.time;
						break;
					}
				}
			}

			checkTriggerMarketBuy(data.high);
			return;
		}
	}


	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	


	synchronized void checkTriggerMarketSell(double price)
	{
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if ( trade.limitEntryOrderPlaced == true )
			return;

		if (( trade.entryPullBackLow != 0 ) && ( price < trade.entryPullBackLow))
		{
			/*
			logger.warning(symbol + " " + CHART + "!!!!!!!!" + ((QuoteData)quotes[lastbar]).time + " detected below last low - "  );
    	    logger.warning(symbol + " " + CHART + " last pull back is " +
					 ((QuoteData)quotes[trade.phl_cur.prePos]).time + "@" +
					 ((QuoteData)quotes[trade.phl_cur.prePos]).high + "  -  " +
					 ((QuoteData)quotes[trade.phl_cur.curPos]).time + "@" +
					 ((QuoteData)quotes[trade.phl_cur.curPos]).high + " pullback@" +
					 trade.phl_cur.pullBack.time);
			*/

			int entryPullBackPos = 	Utility.findPositionByMinute(quotes, trade.entryPullBackTime, Constants.BACK_TO_FRONT );
			for (int j = entryPullBackPos + 1; j < lastbar; j++)
			{
				if ( quotes[j].low < trade.entryPullBackLow)
				{
					logger.warning(symbol + " " + CHART + quotes[j].time + " below pullback low of "
							+ trade.entryPullBackTime + " , trade missed");
					trade = null;
					return;
				}
			}

			double highest = Utility.getHigh(quotes, entryPullBackPos, lastbar).high;
			if ((highest - price ) > 40 * PIP_SIZE)
			{
				logger.warning(symbol + " " + CHART + " entry too wide ");
				trade = null;
				return;
			}
			
			
			// remove all limit order
			cancelOrder(trade.limitId1);
			cancelOrder(trade.limitId2);
			trade.limitId1 = trade.limitId2 = 0;

			trade.pullBackPos = entryPullBackPos;  // required for history

			if (( LIMIT_ORDER == false ) || ( MODE == Constants.TEST_MODE ))
			{
				// place market order
				logger.warning(symbol + " " + CHART + " sell order triggered at " + quotes[lastbar].time);
				trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
				trade.price = trade.entryPullBackLow;//(MODE == Constants.TEST_MODE)?trade.entryPullBackLow:price;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_SELL);
					trade.recordOpened = true;
				}
				AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);
				// AddTradeOpenRecord( trade.type,data.time, Constants.ACTION_SELL, trade.positionSize, trade.price);

				// re-addjust stop
				cancelOrder(trade.stopId);
				//trade.stop = Utility.getHigh(quotes, trade.phl_cur.pullBack.pos, lastbar).high;
				//if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
					trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);
			}
			else if ( trade.limitEntryOrderPlaced == true ) 
			{
				// limit order
				trade.limitPrice1 = adjustPrice(trade.entryPullBackLow, Constants.ADJUST_TYPE_UP);
				trade.limitPos1 = trade.POSITION_SIZE;
				logger.warning(symbol + " " + CHART + " place limite entry order of " + trade.limitPrice1 + " on " + trade.limitPos1);
				trade.limitId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);

				trade.limit1Stop = Utility.getHigh(quotes, entryPullBackPos, lastbar).high;
				if (trade.limit1Stop - trade.limitPrice1 > FIXED_STOP * PIP_SIZE)
					trade.limit1Stop = trade.limitPrice1 + FIXED_STOP * PIP_SIZE;

				trade.limit1Stop = adjustPrice(trade.limit1Stop, Constants.ADJUST_TYPE_UP);
				trade.limitEntryOrderPlaced = true;

			}
			return;
		}

	}


	
	
	
	synchronized void checkTriggerMarketBuy(double price)
	{
		QuoteData[] quotesL = largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		if ( trade.limitEntryOrderPlaced == true )
			return;

		if (( trade.entryPullBackHigh != 0 ) && ( price > trade.entryPullBackHigh))
		{
			/*check
			 * logger.warning("@@@@@@@@@@@@@-" + data.time); for (
			 * int j = 0; j < phls.length; j++) { PushHighLow ppp =
			 * phls[j]; logger.warning(symbol + " " + CHART +
			 * "@@@this high between " +
			 * ((QuoteData)quotes[ppp.prePos]).time + "@" +
			 * ((QuoteData)quotes[ppp.prePos]).high + "  -  " +
			 * ((QuoteData)quotes[ppp.curPos]).time + "@" +
			 * ((QuoteData)quotes[ppp.curPos]).high + " pullback@" +
			 * ppp.pullBack.time ); }
			 */

			int entryPullBackPos = 	Utility.findPositionByMinute(quotes, trade.entryPullBackTime, Constants.BACK_TO_FRONT );
			for (int j = entryPullBackPos + 1; j < lastbar; j++)
			{
				if ( quotes[j].high > trade.entryPullBackHigh)
				{
					logger.warning(symbol + " " + CHART + " " + ((QuoteData) quotes[j]).time + " high > pull back high of "
							+ trade.entryPullBackTime + " trade missed");
					trade = null;
					return;
				}
			}

			double lowest = Utility.getLow(quotes, entryPullBackPos, lastbar).low;
			if ((price - lowest) > 40 * PIP_SIZE)
			{
				logger.warning(symbol + " " + CHART + " entry too wide ");
				trade = null;
				return;
			}

			cancelOrder(trade.limitId1);
			cancelOrder(trade.limitId2);
			trade.limitId1 = trade.limitId2 = 0;

			trade.pullBackPos = entryPullBackPos;  // required for history

			if (( LIMIT_ORDER == false ) || ( MODE == Constants.TEST_MODE ))
			{
				// place order
				logger.warning(symbol + " " + CHART + " buy order triggered at " + quotes[lastbar].time);
				trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
				trade.price = trade.entryPullBackHigh;//(MODE == Constants.TEST_MODE)?trade.entryPullBackHigh:price;
				trade.entryPrice = trade.price;
				trade.status = Constants.STATUS_PLACED;
				trade.remainingPositionSize = trade.POSITION_SIZE;
				trade.entryTime = quotes[lastbar].time;
				//trade.entryPos = lastbar;
				//trade.entryPosL = lastbarL;

				if (trade.recordOpened == false)
				{
					CreateTradeRecord(trade.type, Constants.ACTION_BUY);
					trade.recordOpened = true;
				}
				AddOpenRecord( quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
				// AddTradeOpenRecord( trade.type,data.time,
				// Constants.ACTION_BUY, trade.positionSize,
				// trade.price);

				//trade.stop = Utility.getLow(quotes, trade.phl_cur.pullBack.pos, lastbar).low;
				//if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
					trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

				trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
				logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);
			}
			else if ( trade.limitEntryOrderPlaced == false)
			{
				// limit order
				trade.limitPrice1 = adjustPrice(trade.entryPullBackHigh, Constants.ADJUST_TYPE_DOWN);
				trade.limitPos1 = trade.POSITION_SIZE;
				logger.warning(symbol + " " + CHART + " place limite entry order of " + trade.limitPrice1 + " on " + trade.limitPos1);
				trade.limitId1 = placeLmtOrder(Constants.ACTION_BUY, trade.limitPrice1, trade.limitPos1, null);

				trade.limit1Stop = Utility.getLow(quotes, entryPullBackPos, lastbar).low;
				if (trade.limitPrice1 - trade.limit1Stop > FIXED_STOP * PIP_SIZE)
						trade.limit1Stop = trade.limitPrice1 - FIXED_STOP * PIP_SIZE;

				trade.limit1Stop = adjustPrice(trade.limit1Stop, Constants.ADJUST_TYPE_DOWN);
				trade.limitEntryOrderPlaced = true;

			}

		}
		// end of if, need to break the for loop
		return;

	}

	
	
	
	synchronized void checkTriggerMarketSell1(double price)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if ( price < quotesL[lastbarL-1].low )
		{
			int detectPos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			for (int j = detectPos; j < lastbarL-1; j++)
			{
				if ( quotesL[j+1].low < quotesL[j].low )
				{
					logger.warning(symbol + " " + CHART + " trade missed");
					trade = null;
					return;
				}
			}

			// place market order
			logger.warning(symbol + " " + CHART + " sell order triggered at " + quotes[lastbar].time);
			trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
			trade.price = (MODE == Constants.TEST_MODE)?quotesL[lastbarL-1].low:price;
			trade.entryPrice = trade.price;
			trade.status = Constants.STATUS_PLACED;
			trade.remainingPositionSize = trade.POSITION_SIZE;
			trade.entryTime = quotes[lastbar].time;

			if (trade.recordOpened == false)
			{
				CreateTradeRecord(trade.type, Constants.ACTION_SELL);
				trade.recordOpened = true;
			}
			AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, trade.POSITION_SIZE, trade.price);

			// re-addjust stop
			cancelOrder(trade.stopId);
			//trade.stop = Utility.getHigh(quotes, trade.phl_cur.pullBack.pos, lastbar).high;
			//if (trade.stop - trade.price > FIXED_STOP * PIP_SIZE)
				trade.stop = trade.price + FIXED_STOP * PIP_SIZE;

			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);
			logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
			trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);

			return;
		}

	}

	
	
	synchronized void checkTriggerMarketBuy1(double price)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;

		QuoteData[] quotesL = this.largerTimeFrameTraderManager.getQuoteData();
		int lastbarL = quotesL.length - 1;

		if ( price > quotesL[lastbarL-1].high )
		{
			int detectPos = Utility.findPositionByMinute(quotes, trade.detectTime, Constants.BACK_TO_FRONT );
			for (int j = detectPos; j < lastbarL-1; j++)
			{
				if ( quotesL[j+1].high > quotesL[j].high )
				{
					logger.warning(symbol + " " + CHART + " trade missed");
					trade = null;
					return;
				}
			}

			// place order
			trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
			trade.price = (MODE == Constants.TEST_MODE)?quotesL[lastbarL-1].high:price;
			trade.entryPrice = trade.price;
			trade.status = Constants.STATUS_PLACED;
			trade.remainingPositionSize = trade.POSITION_SIZE;
			trade.entryTime = quotes[lastbar].time;
			//trade.entryPos = lastbar;
			//trade.entryPosL = lastbarL;

			if (trade.recordOpened == false)
			{
				CreateTradeRecord(trade.type, Constants.ACTION_BUY);
				trade.recordOpened = true;
			}
			AddOpenRecord( quotes[lastbar].time, Constants.ACTION_BUY, trade.POSITION_SIZE, trade.price);
			// AddTradeOpenRecord( trade.type,data.time,
			// Constants.ACTION_BUY, trade.positionSize,
			// trade.price);

			//trade.stop = Utility.getLow(quotes, trade.phl_cur.pullBack.pos, lastbar).low;
			//if (trade.price - trade.stop > FIXED_STOP * PIP_SIZE)
				trade.stop = trade.price - FIXED_STOP * PIP_SIZE;

			trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_DOWN);
			logger.warning(symbol + " " + CHART + " adjusted stop is " + trade.stop);
			trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);

		}

		// end of if, need to break the for loop
		return;

	}

	
	
	
	
	
	
	
	
	
	
	
	
	boolean previousTradeExist(String tradeType, int pullBackPos)
	{
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0)
		{
			Iterator it = previousTrade.iterator();
			while (it.hasNext())
			{
				Trade t = (Trade) it.next();
				String detectDay = t.detectTime.substring(0,8);
				String detectHour = t.detectTime.substring(9,12).trim();
				String detectMin = t.detectTime.substring(13,15);

				int hour = new Integer(detectHour);
				int minute = new Integer(detectMin);
				minute = hour * 60 + minute;
				String tz1= "";
				if (( minute >= 90) && (minute <= 420))
					tz1 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz1 = "A";
				
				
				String detectDay2 = trade.detectTime.substring(0,8);
				String detectHour2 = trade.detectTime.substring(9,12).trim();
				String detectMin2 = trade.detectTime.substring(13,15);

				hour = new Integer(detectHour2);
				minute = new Integer(detectMin2);
				minute = hour * 60 + minute;
				String tz2= "";
				if (( minute >= 90) && (minute <= 420))
					tz2 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz2 = "A";
				
				if (detectDay.equals(detectDay2)&&(tz1.equals(tz2))&& (( t.action.equals(this.trade.action))))
				{
					if ( pullBackPos != -1 )
					{
						if (t.pullBackPos == pullBackPos)
							return true;
					}
					else
					{	
						//logger.warning(symbol + " " + CHART + " find previous trade, trade should not be entered");
						return true;
					}
				}
			}
		}

		return false;

	}

	
	boolean previousTradeExist(String tradeType, String action)
	{
		Vector<Trade> previousTrade = findPreviousTrade(tradeType);
		if (previousTrade.size() > 0)
		{
			Iterator it = previousTrade.iterator();
			while (it.hasNext())
			{
				Trade t = (Trade) it.next();
				String detectDay = t.detectTime.substring(0,8);
				String detectHour = t.detectTime.substring(9,12).trim();
				String detectMin = t.detectTime.substring(13,15);

				int hour = new Integer(detectHour);
				int minute = new Integer(detectMin);
				minute = hour * 60 + minute;
				String tz1= "";
				if (( minute >= 90) && (minute <= 420))
					tz1 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz1 = "A";
				
				
				String detectDay2 = trade.detectTime.substring(0,8);
				String detectHour2 = trade.detectTime.substring(9,12).trim();
				String detectMin2 = trade.detectTime.substring(13,15);

				hour = new Integer(detectHour2);
				minute = new Integer(detectMin2);
				minute = hour * 60 + minute;
				String tz2= "";
				if (( minute >= 90) && (minute <= 420))
					tz2 = "E";
				else if (( minute >= 1080) && (minute <= 1380))
					tz2 = "A";

				if (detectDay.equals(detectDay2)&& tz1.equals(tz2) && t.action.equals(action))
				{
					//logger.warning(symbol + " " + CHART + " find previous trade, trade should not be entered");
					return true;
				}
			}
		}

		return false;

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
	public void trackTradeTarget(QuoteData data, Object[] quotesS, QuoteData[] quotesL)
	{
		if (MODE == Constants.TEST_MODE)
			checkStopTarget(data);

		if (trade != null)
			exit123_3_c_org_2(data, quotesL);
		    //exit123_new(data, quotesL);
	}


	
	

	
	public void exit123_3_c_org(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
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
				if (trade.entryPrice - data.low > FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
				}

				// normal exit stragety, this is to use the small timeframe
				/*
				if ( trade.lastHighLow == 0)
					trade.reachExitBench = true;
				else if ( data.low < trade.lastHighLow )
				{
					//logger.warning(symbol + " " + CHART + " previous low taken out at " + data.time );
					trade.reachExitBench = true;
				}
				if ( trade.reachExitBench == false )
					return;*/
					
				//double[] ema20 = Indicator.calculateEMA(quotes, 20);

				PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					if ((trade.profitTake1 == false) && /*( phl_cur.pullBack.high > phl_pre.pullBack.high) &&*/ ( pullback_cur > pullback_pre * 1.4 ) )
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 8 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_pre.prePos].low - quotes[phl_cur.prePos].low > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on high break out fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_pre.prePos].low - quotes[phl_pre.curPos].low > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					// normal exit stragety, this is to use the small timeframe
					/*
					if ( trade.entryPrice - data.low > 2 * FIXED_STOP * PIP_SIZE )//data.low < trade.lastHighLow )
						trade.reachExitBench = true;
					if ( trade.reachExitBench == false )
						return;

					double moveStop = adjustPrice( phl_cur.pullBack.high, Constants.ADJUST_TYPE_UP);
					if ( trade.stop != moveStop )
					{	
						cancelOrder(trade.stopId);
						trade.stop = moveStop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					}*/
				}

				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNLow(quotesL, top, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
					if (trade.stop != stop)
					{
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
				}

				// normal exit stragety, this is to use the small time frame
/*				if ( trade.lastHighLow == 0)
					trade.reachExitBench = true;
				else if ( data.high > trade.lastHighLow )
				{
					//logger.warning(symbol + " " + CHART + " previous high taken out at " + data.time );
					trade.reachExitBench = true;
				}
				if ( trade.reachExitBench == false )
					return;
*/
				int exitStartPos = Utility.getLow(quotes, tradeEntryPos-5, tradeEntryPos).pos;
				//System.out.println(data.time + " Exit Starting pos is " + quotes[exitStartPos].time);
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, exitStartPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					//System.out.println("double phls detect at " + data.time);
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					if ((trade.profitTake1 == false ) && /*(phl_cur.pullBack.low < phl_pre.pullBack.low) && */( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 8 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_cur.prePos].high - quotes[phl_pre.prePos].high > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 10 pip break out" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);

						trade.profitTake1 = true;
						return;
					}
					
					/*
					if ((trade.profitTake1 == false) && ( quotes[phl_cur.prePos].high - quotes[phl_pre.prePos].high > FIXED_STOP /2 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 10 pip break out" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);

						trade.profitTake1 = true;
						return;
					}*/
					
					/*
					if ( data.high - trade.entryPrice > 2 * FIXED_STOP * PIP_SIZE)//data.high > trade.lastHighLow )
						trade.reachExitBench = true;
					if ( trade.reachExitBench == false )
						return;

					double moveStop = adjustPrice( phl_cur.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( trade.stop != moveStop )
					{	
						cancelOrder(trade.stopId);
						trade.stop = moveStop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					}*/
				}

				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNHigh(quotesL, bottom, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
					if (trade.stop != stop)
					{
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
					}
				}
			}
		}
	}
	

	
	
	
	
	public void exit123_3_c_org_2(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
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

		String hr = data.time.substring(9,12).trim();
		String min = data.time.substring(13,15);
		int hour = new Integer(hr);
		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}

			if ( trade.stopAdjusted == true )
			{
				// caluculate quick profit take needs to be taken
				if ((( quotes[lastbar-1].high - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE ) ||
				   (( quotes[lastbar-2].high - quotes[lastbar].low) > 2.25 * FIXED_STOP * PIP_SIZE ) ||
				   (( quotes[lastbar-3].high - quotes[lastbar].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					double takeProfit1Price = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN); 
					double takeProfit2Price = adjustPrice(data.close - 20 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN); 
					
					if (( trade.takeProfit1Id != 0 ) && ( takeProfit1Price > trade.takeProfit1Price ))
					{
						cancelProfits();
						trade.takeProfit1Price = takeProfit1Price;
						trade.takeProfit1PosSize = trade.POSITION_SIZE/2;
						trade.takeProfit1Id = placeLmtOrder(Constants.ACTION_BUY, trade.takeProfit1Price, trade.takeProfit1PosSize, null);

						if ( trade.remainingPositionSize - trade.takeProfit1PosSize > 0 )
						{	
							trade.takeProfit2Price = takeProfit2Price;
							trade.takeProfit2PosSize = trade.remainingPositionSize - trade.takeProfit1PosSize;
							trade.takeProfit2Id = placeLmtOrder(Constants.ACTION_BUY, trade.takeProfit2Price, trade.takeProfit2PosSize, null);
						}
					}
				}


				if (trade.targetPlaced1 == false )
				{
					if ((Constants.TRADE_EUR.equals(trade.type) && inEUExitingTime(hour, minute))||
						(Constants.TRADE_USD.equals(trade.type) && inEUExitingTime(hour, minute))||
						( Constants.TRADE_JPY.equals(trade.type) && inJPTakeProfitTime(hour, minute)))
					{
						logger.warning(symbol + " " + CHART + " " + " exit time reached, close position" + data.time);
						trade.targetPrice1 = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						trade.targetPlaced1 = true;
						return;
					}

					// normal exit stragety, this is to use the small timeframe
					/*
					if ( trade.lastHighLow == 0)
						trade.reachExitBench = true;
					else if ( data.low < trade.lastHighLow )
					{
						//logger.warning(symbol + " " + CHART + " previous low taken out at " + data.time );
						trade.reachExitBench = true;
					}
					if ( trade.reachExitBench == false )
						return;*/
						
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
	
					PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl_cur = phls[0];
						PushHighLow phl_pre = phls[1];
	
						double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
						double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;
	
						if ( ( pullback_cur > pullback_pre * 1.4 ) /*( phl_cur.pullBack.high > phl_pre.pullBack.high) &&*/  )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
							
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 8 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						if ( pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						if (( Constants.TRADE_EUR.equals(trade.type) && inEUTakeProfitTime(hour, minute)) ||
							( Constants.TRADE_USD.equals(trade.type) && inEUTakeProfitTime(hour, minute)) ||
							( Constants.TRADE_JPY.equals(trade.type) && inJPTakeProfitTime(hour, minute)))
						{
							if ( phl_cur.curPos == lastbar )
							{
								// exit when making a new high/low at exi
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 4 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);
		
								trade.targetPlaced1 = true;
								return;
							}

							double moveStop = adjustPrice( phl_cur.pullBack.high, Constants.ADJUST_TYPE_UP);
							if ( trade.stop != moveStop )
							{	
								cancelOrder(trade.stopId);
								trade.stop = moveStop;
								trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
							}
						}

						/*
						if ((trade.profitTake1 == false) && ( quotes[phl_pre.prePos].low - quotes[phl_cur.prePos].low > FIXED_STOP /2 * PIP_SIZE ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on high break out fixed stop " + data.time);
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.profitTake1 = true;
							return;
						}
	
						/*
						if ((trade.profitTake1 == false) && ( quotes[phl_pre.prePos].low - quotes[phl_pre.curPos].low > FIXED_STOP /2 * PIP_SIZE ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.profitTake1 = true;
							return;
						}
	
						// normal exit stragety, this is to use the small timeframe
						/*
						if ( trade.entryPrice - data.low > 2 * FIXED_STOP * PIP_SIZE )//data.low < trade.lastHighLow )
							trade.reachExitBench = true;
						if ( trade.reachExitBench == false )
							return;
	
						double moveStop = adjustPrice( phl_cur.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( trade.stop != moveStop )
						{	
							cancelOrder(trade.stopId);
							trade.stop = moveStop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}*/
					}
				}
	
				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNLow(quotesL, top, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
					if ( stop < trade.stop )
					{
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
					}
				}
			}
				
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (((quotes[lastbar].high - quotes[lastbar-1].low) > 2 * FIXED_STOP * PIP_SIZE ) ||
				    ((quotes[lastbar].high - quotes[lastbar-2].low) > 2.25 * FIXED_STOP * PIP_SIZE ) ||
				    ((quotes[lastbar].high - quotes[lastbar-3].low) > 2.5 * FIXED_STOP * PIP_SIZE ))
				{
					double takeProfit1Price = adjustPrice(data.close, Constants.ADJUST_TYPE_UP); 
					double takeProfit2Price = adjustPrice(data.close + 20 * PIP_SIZE, Constants.ADJUST_TYPE_UP); 
					
					if (( trade.takeProfit1Id != 0 ) && ( takeProfit1Price > trade.takeProfit1Price ))
					{
						cancelProfits();
						trade.takeProfit1Price = takeProfit1Price;
						trade.takeProfit1PosSize = trade.POSITION_SIZE/2;
						trade.takeProfit1Id = placeLmtOrder(Constants.ACTION_SELL, trade.takeProfit1Price, trade.takeProfit1PosSize, null);

						if ( trade.remainingPositionSize - trade.takeProfit1PosSize > 0 )
						{	
							trade.takeProfit2Price = takeProfit2Price;
							trade.takeProfit2PosSize = trade.remainingPositionSize - trade.takeProfit1PosSize;
							trade.takeProfit2Id = placeLmtOrder(Constants.ACTION_SELL, trade.takeProfit2Price, trade.takeProfit2PosSize, null);
						}
					}
				}

				if (trade.targetPlaced1 == false )
				{
					if ((Constants.TRADE_EUR.equals(trade.type) && inEUExitingTime(hour, minute)) ||
						(Constants.TRADE_USD.equals(trade.type) && inEUExitingTime(hour, minute))||
						( Constants.TRADE_JPY.equals(trade.type) && inJPTakeProfitTime(hour, minute)))
					{
						logger.warning(symbol + " " + CHART + " " + " exit time reached, close position ");
						
						trade.targetPrice1 = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						trade.targetPlaced1 = true;
						return;
					}


					// normal exit stragety, this is to use the small time frame
	/*				if ( trade.lastHighLow == 0)
						trade.reachExitBench = true;
					else if ( data.high > trade.lastHighLow )
					{
						//logger.warning(symbol + " " + CHART + " previous high taken out at " + data.time );
						trade.reachExitBench = true;
					}
					if ( trade.reachExitBench == false )
						return;
	*/
					int exitStartPos = Utility.getLow(quotes, tradeEntryPos-5, tradeEntryPos).pos;
					//System.out.println(data.time + " Exit Starting pos is " + quotes[exitStartPos].time);
					PushHighLow[] phls = Pattern.findPast2Highs(quotes, exitStartPos, lastbar);
					if ((phls != null) && (phls.length >=2))
					{
						//System.out.println("double phls detect at " + data.time);
						PushHighLow phl_cur = phls[0];
						PushHighLow phl_pre = phls[1];
	
						double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
						double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;
	
						if ( ( pullback_cur > pullback_pre * 1.4 ) )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 8 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						if (pullback_cur > FIXED_STOP * 1.75 * PIP_SIZE )
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop" + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}
	
						if (( Constants.TRADE_EUR.equals(trade.type) && inEUTakeProfitTime(hour, minute))||
							( Constants.TRADE_USD.equals(trade.type) && inEUTakeProfitTime(hour, minute))||
							( Constants.TRADE_JPY.equals(trade.type) && inJPTakeProfitTime(hour, minute)))
						{
							if ( phl_cur.curPos == lastbar )
							{	
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
		
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 4 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);
		
								trade.profitTake1 = true;
								return;
							}							

							double moveStop = adjustPrice( phl_cur.pullBack.low, Constants.ADJUST_TYPE_DOWN);
							if ( trade.stop != moveStop )
							{	
								cancelOrder(trade.stopId);
								trade.stop = moveStop;
								trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
							}
						}
						
						/*
						if ((trade.profitTake1 == false) && ( quotes[phl_cur.prePos].high - quotes[phl_pre.prePos].high > FIXED_STOP /2 * PIP_SIZE ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 10 pip break out" + data.time);
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
	
							trade.profitTake1 = true;
							return;
						}
						
						/*
						if ((trade.profitTake1 == false) && ( quotes[phl_cur.prePos].high - quotes[phl_pre.prePos].high > FIXED_STOP /2 * PIP_SIZE ))
						{
							// exit when making a new high/low at exi
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 10 pip break out" + data.time);
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
	
							trade.profitTake1 = true;
							return;
						}*/
						
						/*
						if ( data.high - trade.entryPrice > 2 * FIXED_STOP * PIP_SIZE)//data.high > trade.lastHighLow )
							trade.reachExitBench = true;
						if ( trade.reachExitBench == false )
							return;
	
						double moveStop = adjustPrice( phl_cur.pullBack.low, Constants.ADJUST_TYPE_DOWN);
						if ( trade.stop != moveStop )
						{	
							cancelOrder(trade.stopId);
							trade.stop = moveStop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						}*/
					}
				}
				
				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNHigh(quotesL, bottom, lastbarL, 1);
				if (phl != null)
				{
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

	
	
	public void exit123_new(QuoteData data, QuoteData[] quotesL)
	{
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
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

		String hr = data.time.substring(9,12).trim();
		String min = data.time.substring(13,15);
		int hour = new Integer(hr);
		int minute = new Integer(min);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					//double[] ema20 = Indicator.calculateEMA(quotes, 20);
					PushHighLow[] phls = Pattern.findPast2Lows(quotesL, tradeEntryPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotesL );
						int positive = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram > 0 )
								positive ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( positive >= 2 )
						{
							/*
							int prevNegative = 0;
							for ( int j = phls[1].prePos; j <= phls[1].curPos; j++)
							{
								if ( macd[j].histogram < 0 )
									prevNegative ++;
							}
							logger.warning(symbol + " " + CHART + " " + " prev Negative is " + prevNegative);
							
							if ( prevNegative > 2 )*/
							{
								logger.warning(symbol + " " + CHART + " " + trade.action + " exit buy on MACD diverage " + data.time);
								if ( trade.targetId2 != 0 )
								{
									cancelOrder(trade.targetId2);
									trade.targetId2 = 0;
								}
								
								trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).low - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " " + data.time);
		
								trade.targetPlaced1 = true;
								return;
							}
						}

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
				if (data.high - trade.entryPrice > FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (trade.targetPlaced1 == false )
				{
					int exitStartPosL = Utility.getLow(quotesL, tradeEntryPosL-5, tradeEntryPosL).pos;
					PushHighLow[] phls = Pattern.findPast2Highs(quotesL, exitStartPosL, lastbarL);
					if ((phls != null) && (phls.length >=2))
					{
						PushHighLow phl = phls[0];
						MACD[] macd = Indicator.calculateMACD( quotesL );
						int negatives = 0;
						for ( int j = phl.prePos; j <= phl.curPos; j++)
						{
							if ( macd[j].histogram < 0 )
								negatives ++;
							//System.out.print(macd[j].macd + "   ");
						}
						//System.out.println();
						
						if ( negatives >= 2 )
						{
							System.out.println("pre=" + quotesL[phl.prePos].time + " cur=" + quotesL[phl.curPos].time);
							logger.warning(symbol + " " + CHART + " " + trade.action + " exit sell on MACD diverage " + data.time);
							if ( trade.targetId2 != 0 )
							{
								cancelOrder(trade.targetId2);
								trade.targetId2 = 0;
							}
	
							trade.targetPrice1 = adjustPrice(((QuoteData) quotesL[phl.prePos]).high + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
	
							trade.targetPlaced1 = true;
							return;
						}

	
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
	
	

	
	
	
	
	
	
	
	
	
	
	public void exit123_3_c_org_movingstop(QuoteData data, QuoteData[] quotesL)
	{
		//int lastbarL = quotesL.length - 1;
		//int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );

		/*
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (lastbarL < tradeEntryPosL + 2)
			return;

		*/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * PIP_SIZE) 
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
				}

				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;
				//double[] ema20 = Indicator.calculateEMA(quotes, 20);

				int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					if ((trade.profitTake1 == false) && /*( phl_cur.pullBack.high > phl_pre.pullBack.high) &&*/ ( pullback_cur > pullback_pre * 1.4 ) )
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.25 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					// normal exit stragety, this is to use the small timeframe
					if ( trade.lastHighLow == 0)
						trade.reachExitBench = true;
					else if ( trade.entryPrice - data.low > 2 * FIXED_STOP * PIP_SIZE )//data.low < trade.lastHighLow )
					{
						//logger.warning(symbol + " " + CHART + " previous low taken out at " + data.time );
						trade.reachExitBench = true;
					}
					if ( trade.reachExitBench == false )
						return;

					if ( phl_cur.curPos - phl_cur.prePos >= 2 )
					{
						double moveStop = adjustPrice( phl_cur.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( trade.stop != moveStop )
						{	
							cancelOrder(trade.stopId);
							trade.stop = moveStop;
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
				}

				// normal exit stragety, this is to use the small time frame
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;
				//double[] ema20 = Indicator.calculateEMA(quotes, 20);

				int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					if ((trade.profitTake1 == false ) && /*(phl_cur.pullBack.low < phl_pre.pullBack.low) && */( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.4 pull back " + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.25 * PIP_SIZE ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " exit on 1.75 fixed stop" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}

					if ( trade.lastHighLow == 0)
						trade.reachExitBench = true;
					else if ( data.high - trade.entryPrice > 2 * FIXED_STOP * PIP_SIZE)//data.high > trade.lastHighLow )
					{
						//logger.warning(symbol + " " + CHART + " previous high taken out at " + data.time );
						trade.reachExitBench = true;
					}
					if ( trade.reachExitBench == false )
						return;

					if ( phl_cur.curPos - phl_cur.prePos >= 2 )
					{
						double moveStop = adjustPrice( phl_cur.pullBack.low, Constants.ADJUST_TYPE_DOWN);
						if ( trade.stop != moveStop )
						{	
							cancelOrder(trade.stopId);
							trade.stop = moveStop;
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
						}
					}
				}
			}
		}
	}

	
	
	
	public void exit123_3_c_org_newwww_latest(QuoteData data, QuoteData[] quotesL)
	{
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, Constants.BACK_TO_FRONT );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			
			/*
			if (trade.stopAdjusted == false)
			{
				if (trade.entryPrice - data.low > FIXED_STOP * 1.5 * PIP_SIZE)
				{
					logger.info(symbol + " " + CHART + " move stop to break even " + " " + data.time );

					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else*/
			
			
			// check to move stop
			if (( quotes[lastbar].high < ema20[lastbar]) && ( quotes[lastbar-1].high < ema20[lastbar-1]))
			{
				MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, tradeEntryPos, lastbar);
				double stop = adjustPrice(mat.high.high, Constants.ADJUST_TYPE_UP);
				
				if (stop < trade.stop)
				{
					logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

					cancelOrder(trade.stopId);
					trade.stop = stop;
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
				}
			}

			PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
			if ((phls != null) && (phls.length >=2))
			{
				PushHighLow phl_cur = phls[0];
				PushHighLow phl_pre = phls[1];

				double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
				double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

				if ((trade.profitTake1 == false) && ( pullback_cur > pullback_pre * 1.4 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}
				
				if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}

			}


			{
				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					/*
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// exit when making a new high/low at exi
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
						}
						/*
						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 15 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// check 2: large break outs
							if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								if (previousTradeExist(phl_cur.pullBack.pos))
									return;

								logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
								trade.pullBackPos = phl_cur.pullBack.pos;

								// place order public int placeLmtOrder(String
								// action, double limitPrice, int posSize,
								// String ocaGroup)
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;

							}
							// check 3: small exhausting breakouts
						}*/
				//	}
				}

				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNLow(quotesL, top, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
					if (stop < trade.stop)
					{
						logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						//trade.adjustStop++;

					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			// check to move stop
			if (( quotes[lastbar].low > ema20[lastbar]) && ( quotes[lastbar-1].low > ema20[lastbar-1]))
			{
				MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, tradeEntryPos, lastbar);
				double stop = adjustPrice(mat.low.low, Constants.ADJUST_TYPE_DOWN);
				
				if (stop > trade.stop)
				{
					logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );

					cancelOrder(trade.stopId);
					trade.stop = stop;
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
				}
			}

			PushHighLow[] phls = Pattern.findPast2Highs(quotes, tradeEntryPos, lastbar);
			if ((phls != null) && (phls.length >=2))
			{
				PushHighLow phl_cur = phls[0];
				PushHighLow phl_pre = phls[1];

				double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
				double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

				if ((trade.profitTake1 == false ) && ( pullback_cur > pullback_pre * 1.4 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}
				
				if ((trade.profitTake1 == false) && ( pullback_cur > FIXED_STOP * 1.75 ))
				{
					// exit when making a new high/low at exi
					logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

					trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
					trade.targetPos1 = trade.remainingPositionSize;
					trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
					logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
							+ data.time);

					trade.profitTake1 = true;
					return;
				}

			}

			/*
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > FIXED_STOP * 1.5 * PIP_SIZE)
				{
					logger.info(symbol + " " + CHART + " move stop to break even " + " " + data.time );
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else*/
			{
				/*


				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);




							/*
							if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 15 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large breakouts" + data.time);

								trade.targetPrice1 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}*/
					//	}
					//}
				//}

				// normal exit stragety
				// this is to use the small time frame

				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				PushHighLow phl = findLastNHigh(quotesL, bottom, lastbarL, 1);
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
					if (stop > trade.stop)
					{
						logger.info(symbol + " " + CHART + " trail profit - move stop to " + stop + " " + data.time );
						cancelOrder(trade.stopId);

						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);

					}
					return;
				}
			}
		}
	}


	
	public void exit123_3_c_org_bak(QuoteData data, QuoteData[] quotesL)
	{
		int lastbarL = quotesL.length - 1;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );

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
				if (trade.entryPrice - data.low > BREAK_EVEN_POINT * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					// logger.warning(symbol + " " + CHART +
					// " trail profit - move stop to " + trade.stop + " " +
					// data.time );
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					/*
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Lows(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// exit when making a new high/low at exi
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
							logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);
						}
						/*
						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((phl_cur.pullBack.high - ((QuoteData) quotes[phl_cur.prePos]).low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 5 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - 15 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// check 2: large break outs
							if (((((QuoteData) quotes[phl_cur.prePos]).low - data.low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								if (previousTradeExist(phl_cur.pullBack.pos))
									return;

								logger.warning(symbol + " " + CHART + " detected for limit order for large pull backs" + data.time);
								trade.pullBackPos = phl_cur.pullBack.pos;

								// place order public int placeLmtOrder(String
								// action, double limitPrice, int posSize,
								// String ocaGroup)
								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos1 = trade.remainingPositionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
								logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low - (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);
								logger.warning(symbol + " " + CHART + " target2 order place " + trade.targetPrice2 + " " + trade.targetPos2 + " "
										+ data.time);

								trade.targetPlaced = true;

							}
							// check 3: small exhausting breakouts
						}*/
				//	}
				}

				// normal exit stragety
				// this is to use the small timeframe
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Lows(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur = phl_cur.pullBack.high - ((QuoteData)quotes[phl_cur.prePos]).low;
					double pullback_pre = phl_pre.pullBack.high - ((QuoteData)quotes[phl_pre.prePos]).low;

					if ((trade.profitTake1 == false) && ( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).low, Constants.ADJUST_TYPE_DOWN);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}
				}


				// this is to use the larger timeframe
				int top = Utility.getHigh(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > top; i--)
				{
					PushHighLow phl = findLastNLow(quotesL, top, i, 1);
					if (phl != null)
					{
						if (i == lastbarL)
						{
							if ((trade.profitTake2 == false)
									&& (phl.pullBack.high - (((QuoteData) quotesL[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
								// large pull back, lock in profit
								trade.targetPrice2 = adjustPrice(data.close, Constants.ADJUST_TYPE_DOWN);
								trade.targetPos2 = trade.remainingPositionSize;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice2, trade.targetPos2, null);// new
								trade.profitTake2 = true;
								return;
							}

							double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_DOWN);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								// logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time );
								trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
						}
					}
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.stopAdjusted == false)
			{
				if (data.high - trade.entryPrice > BREAK_EVEN_POINT * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = trade.entryPrice;

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.stopAdjusted = true;
				}
			}
			else
			{
				/*


				if (inExitingTime(data.time))
				{
					// look to lock in profit in exiting time
					Object[] quotes = getQuoteData();
					int lastbar = quotes.length - 1;

					PushHighLow[] phls = Pattern.findPast2Highs(quotes, trade.entryPos, lastbar);
					if ((phls != null) && (phls.length > 0))
					{
						PushHighLow phl_cur = phls[0];

						// check 1: large pull back
						if (phl_cur.curPos == lastbar)
						{
							logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

							trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
							trade.targetPos1 = trade.remainingPositionSize;
							trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
							logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
									+ data.time);




							/*
							if ((((QuoteData) quotes[phl_cur.prePos]).high - phl_cur.pullBack.low > EXIT_LARGE_PULLBACK * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large pull backs" + data.time);

								trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 5 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high + 15 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}

							// if the pull back is larger than 20 pips, put one
							// limit positions
							if ((data.high - (((QuoteData) quotes[phl_cur.prePos]).low) > EXIT_LARGE_BREAKOUT * PIP_SIZE)
									&& (trade.targetPlaced == false))
							{
								logger.warning(symbol + " " + CHART + " exit sell detected for large breakouts" + data.time);

								trade.targetPrice1 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 10) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos1 = trade.positionSize / 2;
								trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice1, trade.limitPos1, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPrice2 = adjustPrice(
										((QuoteData) quotes[phl_cur.prePos]).high + (EXIT_LARGE_BREAKOUT + 15) * PIP_SIZE,
										Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.positionSize / 2;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.limitPrice2, trade.limitPos2, null);
								logger.warning(symbol + " " + CHART + " target sell order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
										+ data.time);

								trade.targetPlaced = true;
								return;
							}*/
					//	}
					//}
				//}

				// normal exit stragety
				// this is to use the small time frame
				QuoteData[] quotes = getQuoteData();
				int lastbar = quotes.length - 1;

				int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);
				PushHighLow[] phls = Pattern.findPast2Highs(quotes, tradeEntryPos, lastbar);
				if ((phls != null) && (phls.length >=2))
				{
					PushHighLow phl_cur = phls[0];
					PushHighLow phl_pre = phls[1];

					double pullback_cur =  ((QuoteData)quotes[phl_cur.prePos]).high - phl_cur.pullBack.low;
					double pullback_pre =  ((QuoteData)quotes[phl_pre.prePos]).high - phl_pre.pullBack.low;

					if ((trade.profitTake1 == false ) && ( pullback_cur > pullback_pre * 1.4 ))
					{
						// exit when making a new high/low at exi
						logger.warning(symbol + " " + CHART + " " + trade.action + " time to exit" + data.time);

						trade.targetPrice1 = adjustPrice(((QuoteData) quotes[phl_cur.prePos]).high, Constants.ADJUST_TYPE_UP);
						trade.targetPos1 = trade.remainingPositionSize;
						trade.targetId1 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice1, trade.targetPos1, null);
						logger.warning(symbol + " " + CHART + " target order place " + trade.targetPrice1 + " " + trade.targetPos1 + " "
								+ data.time);

						trade.profitTake1 = true;
						return;
					}
				}


				// this is to use the larger time frame
				int bottom = Utility.getLow(quotesL, tradeEntryPosL - 3, lastbarL).pos;
				for (int i = lastbarL; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh(quotesL, bottom, i, 1);
					if (phl != null)
					{
						if (i == lastbarL)
						{
							if ((trade.profitTake2 == false)
									&& ((((QuoteData) quotesL[phl.prePos]).high - phl.pullBack.low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								logger.warning(symbol + " " + CHART + " minute target hit for reach in lock in profit of " + LOCKIN_PROFIT + " pips");
								trade.targetPrice2 = adjustPrice(data.close, Constants.ADJUST_TYPE_UP);
								trade.targetPos2 = trade.remainingPositionSize;
								trade.targetId2 = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice2, trade.targetPos2, null);// new
																																			// Long(oca).toString());
								trade.profitTake2 = true;
								return;
							}

							double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_UP);
							if (trade.stop != stop)
							{
								cancelOrder(trade.stopId);
								trade.stop = stop;

								trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
								trade.adjustStop++;

							}
							return;
						}
					}
				}
			}
		}
	}


	public void exit123_peak(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if (trade.entryPos + 2 > lastbar)
			return;

		if (Constants.ACTION_SELL.equals(trade.action))
		{

			// exit 1: close when breaking higher high/lower low
			if ((trade.price - data.close) < 15 * PIP_SIZE)
				return;

			Object[] peaks = Pattern.findPastLowPeaksBelowMA(quotes, ema20, trade.entryPos, lastbar);

			if ((peaks == null) || (peaks.length < 2))
				return;

			int lastPeakPos = peaks.length - 1;
			if (((Peak) peaks[lastPeakPos]).pullbackStartPos == 0)
			{
				QuoteData lastPullbackHigh = Utility.getHigh(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
						((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);

				if ((lastPullbackHigh.high - data.close) >= 60 * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " profit exceeds 60 pips, close trade " + data.time + " @ " + lastPullbackHigh);
					AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
					closePositionByMarket(trade.remainingPositionSize, data.close);
					// removeTrade();
				}
				return;
			}

			// logger.warning(symbol + " " + CHART + " trade target peak = " +
			// peaks.length );

			for (int i = lastPeakPos; i >= 0; i--)
			{
				logger.warning(symbol + " " + CHART + " peak " + i + " lows between " + ((QuoteData) quotes[((Peak) peaks[i]).highlowStartPos]).time
						+ "-" + ((QuoteData) quotes[((Peak) peaks[i]).highlowEndPos]).time);
				logger.warning(symbol + " " + CHART + " peak " + i + " pullbacks btw "
						+ ((QuoteData) quotes[((Peak) peaks[i]).pullbackStartPos]).time + "-"
						+ ((QuoteData) quotes[((Peak) peaks[i]).pullbackEndPos]).time);
			}

			QuoteData lastPullbackHigh = Utility.getHigh(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
					((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);
			QuoteData thisPullbackHigh = Utility.getHigh(quotes, ((Peak) peaks[lastPeakPos]).pullbackStartPos,
					((Peak) peaks[lastPeakPos]).pullbackEndPos);

			if ((thisPullbackHigh.high > lastPullbackHigh.high) && (thisPullbackHigh.pos == lastbar))
			{
				logger.warning(symbol + " " + CHART + " break last high, close trade " + data.time + " @ " + lastPullbackHigh);
				AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
				closePositionByMarket(trade.remainingPositionSize, data.close);
				// removeTrade();
				return;
			}

			// exit 2: exit partial profit when it hit three times in one peak;

			// move stop
			/*
			 * double stop = adjustPrice( mat.high.high,
			 * Constants.ADJUST_TYPE_UP); if ( trade.stop != stop ) {
			 * cancelOrder( trade.stopId); trade.stop = stop;
			 *
			 * logger.warning(symbol + " " + CHART + " last 20MA touch is " +
			 * mat.high.time ); logger.warning(symbol + " " + CHART +
			 * " adjusted stop is " + stop); trade.stopId =
			 * placeStopOrder(Constants.ACTION_BUY, stop,
			 * trade.remainingPositionSize, null); trade.adjustStop++;
			 *
			 * } }
			 */
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ((data.close - trade.price) < 15 * PIP_SIZE)
				return;

			Object[] peaks = Pattern.findPastHighPeaksAboveMA(quotes, ema20, trade.entryPos, lastbar);

			if ((peaks == null) || (peaks.length < 2))
				return;

			int lastPeakPos = peaks.length - 1;
			if (((Peak) peaks[lastPeakPos]).pullbackStartPos == 0)
			{
				QuoteData lastPullbackLow = Utility.getLow(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
						((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);

				if ((data.close - lastPullbackLow.low) >= 60 * PIP_SIZE)
				{
					logger.warning(symbol + " " + CHART + " profit exceeds 60 pips " + data.time + " @ " + lastPullbackLow);
					AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
					closePositionByMarket(trade.remainingPositionSize, data.close);
					// removeTrade();
				}
				return;
			}

			// logger.warning(symbol + " " + CHART + " trade target peak = " +
			// peaks.length );
			/*
			 * for ( int i = lastPeakPos; i >=0; i--) { logger.warning(symbol +
			 * " " + CHART + " peak " + i + " lows between " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).highlowStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).highlowEndPos]).time );
			 * logger.warning(symbol + " " + CHART + " peak " + i +
			 * " pullbacks btw " +
			 * ((QuoteData)quotes[((Peak)peaks[i]).pullbackStartPos]).time + "-"
			 * + ((QuoteData)quotes[((Peak)peaks[i]).pullbackEndPos]).time ); }
			 */

			QuoteData lastPullbackLow = Utility.getLow(quotes, ((Peak) peaks[lastPeakPos - 1]).pullbackStartPos,
					((Peak) peaks[lastPeakPos - 1]).pullbackEndPos);
			QuoteData thisPullbackLow = Utility.getLow(quotes, ((Peak) peaks[lastPeakPos]).pullbackStartPos,
					((Peak) peaks[lastPeakPos]).pullbackEndPos);

			if ((thisPullbackLow.low < lastPullbackLow.low) && (thisPullbackLow.pos == lastbar))
			{
				logger.warning(symbol + " " + CHART + " break last low, close trade " + data.time + " @ " + lastPullbackLow);
				AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
				closePositionByMarket(trade.remainingPositionSize, data.close);
				// removeTrade();
				return;
			}

			/*
			 * double stop = adjustPrice( mat.low.low,
			 * Constants.ADJUST_TYPE_DOWN); // move stop if ( trade.stop != stop
			 * ) { cancelOrder( trade.stopId ); trade.stop = stop;
			 *
			 * logger.warning(symbol + " " + CHART + " last 20MA touch is " +
			 * mat.low.time ); logger.warning(symbol + " " + CHART +
			 * " adjusted stop is " + stop); trade.stopId =
			 * placeStopOrder(Constants.ACTION_SELL, stop,
			 * trade.remainingPositionSize, null); trade.adjustStop++; }
			 *
			 * return;
			 */
		}
	}

	public void exit123_pullback_20ma(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		// int lastbarS = quotesS.length -1;
		double[] ema20 = Indicator.calculateEMA(quotes, 20);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			for (int i = lastbar; i > trade.entryPos; i--)
			{
				MATouch mat = Pattern.findLastMATouchDown(quotes, ema20, trade.entryPos, i);

				if ((mat != null) && (mat.high != null))
				{
					// logger.warning(symbol + " " + CHART +
					// " last 20MAAAAAAAA touch is " + mat.high.time );
					if (data.high > mat.high.high)
					{
						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch is " + mat.high.time );
						// logger.warning(symbol + " " + CHART +
						// " break 20MA, close trade @" + data.time );
						AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
						closePositionByMarket(trade.remainingPositionSize, data.close);
						// removeTrade();
						return;
					}

					double stop = adjustPrice(mat.high.high, Constants.ADJUST_TYPE_UP);
					if (trade.stop != stop)
					{
						// do not move stop until them move out of the slim jim
						// if ( trade.stop > this.slimJimLow)
						// return;

						cancelOrder(trade.stopId);
						trade.stop = stop;

						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch is " + mat.high.time );
						// logger.warning(symbol + " " + CHART +
						// " trail profit - move stop to " + trade.stop + " " +
						// data.time );
						trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
						trade.adjustStop++;

					}
					return;
				}
			}
			/*
			 * double profitInStake = Math.abs(data.close - mat.highest );
			 *
			 * if ( profitInStake > 40 * PIP_SIZE ) { logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close);
			 *
			 * }
			 */
			/*
			 *
			 * for ( int i = lastbar; i > top; i--) { PushHighLow phl =
			 * findLastNLow( quotes, top, i, 1 ); if ( phl != null ) { if ( i ==
			 * lastbar ) { // take partical profit if the pull back is great
			 * than 25pips if (phl.pullBack.high -
			 * (((QuoteData)quotes[phl.prePos]).low ) > LOCKIN_PROFIT * PIP_SIZE
			 * ) { logger.warning(symbol + " " + CHART +
			 * " take profit, last pull back is " +
			 * ((QuoteData)quotes[phl.prePos]).low + " @ " +
			 * ((QuoteData)quotes[phl.prePos]).time ); logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close); //removeTrade();
			 * return; } }
			 *
			 * return; } }
			 */
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.entryPos + 2 > lastbar)
				return;

			for (int i = lastbar; i > trade.entryPos; i--)
			{
				MATouch mat = Pattern.findLastMATouchUp(quotes, ema20, trade.entryPos, i);

				if ((mat != null) && (mat.low != null))
				{
					if (data.low < mat.low.low)
					{
						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch is " + mat.low.time );
						// logger.warning(symbol + " " + CHART +
						// " break 20MA, close trade @" + data.time );
						AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);
						closePositionByMarket(trade.remainingPositionSize, data.close);
						// removeTrade();
						return;
					}

					double stop = adjustPrice(mat.low.low, Constants.ADJUST_TYPE_DOWN);
					// move stop
					if (trade.stop != stop)
					{
						// do not move stop until them move out of the slim jim
						// if ( trade.stop < this.slimJimHigh)
						// return;

						cancelOrder(trade.stopId);
						trade.stop = stop;

						// logger.warning(symbol + " " + CHART +
						// " last 20MA touch between " +
						// ((QuoteData)quotes[mat.touchBegin]).time + "-" +
						// ((QuoteData)quotes[mat.touchEnd]).time );
						logger.warning(symbol + " " + CHART + " trail profit - move stop to " + trade.stop + " " + data.time);
						// logger.warning(symbol + " " + CHART +
						// " adjusted stop is " + stop);
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						trade.adjustStop++;
					}

					return;
				}

			}

			/*
			 * double profitInStake = 0; if ( mat.lowest !=
			 * MATouch.DEFAULT_LOWEST ) Math.abs(data.close - mat.lowest );
			 *
			 * if ( profitInStake > 40 * PIP_SIZE ) { logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_SELL,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close);
			 *
			 * }
			 */

			/*
			 * for ( int i = lastbar; i > bottom; i--) { PushHighLow phl =
			 * findLastNHigh( quotes, bottom, i, 1 ); if ( phl != null ) {
			 * //logger.warning(symbol + " " + CHART + "this high between " +
			 * ((QuoteData)quotes[phl.prePos]).time + "@" +
			 * ((QuoteData)quotes[phl.prePos]).high + "  -  " +
			 * ((QuoteData)quotes[phl.curPos]).time + "@" +
			 * ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
			 * phl.pullBack );
			 *
			 * if ( i == lastbar ) { if ((((QuoteData)quotes[phl.prePos]).high -
			 * phl.pullBack.low ) > LOCKIN_PROFIT * PIP_SIZE ) {
			 * logger.warning(symbol + " " + CHART +
			 * " take profit, last pull back is " +
			 * ((QuoteData)quotes[phl.prePos]).high + " @ " +
			 * ((QuoteData)quotes[phl.prePos]).time ); logger.warning(symbol +
			 * " " + CHART + " take profit, the pull back > " + LOCKIN_PROFIT +
			 * " pips"); AddTradeCloseRecord( data.time, Constants.ACTION_SELL,
			 * trade.remainingPositionSize, data.close); closePositionByMarket(
			 * trade.remainingPositionSize, data.close); //removeTrade();
			 * return; } } return; } }
			 */
		}
	}

	public void exit123_3_c_1m_works_but_no_diffience(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarS = quotesS.length - 1;

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int top;

				if (Constants.TRADE_RST.equals(trade.type))
					top = Utility.getHigh(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					top = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					top = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				PushHighLow phl = findLastNLow(quotes, top, lastbar, 1);
				if (phl != null)
				{
					if (phl.prePos > trade.entryPos)
					{
						// move stop
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if (trade.stop != stop)
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;

							logger.warning(symbol + " " + CHART + " adjusted stop is " + stop);
							trade.stopId = placeStopOrder(Constants.ACTION_BUY, stop, trade.remainingPositionSize, null);
							trade.adjustStop++;

						}
					}

					// take partical profit if the pull back is great than
					// 25pips
					if ((trade.profitTake2 == false) && (phl.pullBack.high - (((QuoteData) quotes[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
					{
						logger.warning(symbol + " " + CHART + " take profit triggered at " + data.time);
						trade.profitTake2Triggered = true;
						trade.profitTake2 = true;
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).high > ((QuoteData) quotesS[lastbarS - 1]).high))
				{
					logger.warning(symbol + " " + CHART + " take profit executed at " + data.time);
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					// large pull back, lock in profit
					/*
					 * trade.targetPrice = data.close; // TODO: this could be
					 * better trade.targetPrice = adjustPrice( data.close,
					 * Constants.ADJUST_TYPE_DOWN);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_BUY,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int bottom;
				if (Constants.TRADE_RST.equals(trade.type))
					bottom = Utility.getLow(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					bottom = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					bottom = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				PushHighLow phl = findLastNHigh(quotes, bottom, lastbar, 1);
				if (phl != null)
				{
					if (phl.prePos > trade.entryPos)
					{
						double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
						// move stop
						if (trade.stop != stop)
						{
							cancelOrder(trade.stopId);
							trade.stop = stop;

							logger.warning(symbol + " " + CHART + " adjusted stop is " + stop);
							trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
							trade.adjustStop++;
						}
					}

					if ((trade.profitTake2 == false) && ((((QuoteData) quotes[phl.prePos]).high - phl.pullBack.low) > 25 * PIP_SIZE))
					{
						logger.warning(symbol + " " + CHART + " take profit triggered at " + data.time);
						trade.profitTake2Triggered = true;
						trade.profitTake2 = true;
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).low < ((QuoteData) quotesS[lastbarS - 1]).low))
				{
					logger.warning(symbol + " " + CHART + " take profit executed at " + data.time);
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					/*
					 * trade.targetPrice = data.close; trade.targetPrice =
					 * adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_SELL,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
	}

	public void exit123_3_c_1m_works_but_no_diffience_bak(QuoteData data, Object[] quotesS, Object[] quotesL)
	{
		Object[] quotes = getQuoteData();
		int lastbar = quotes.length - 1;
		int lastbarS = quotesS.length - 1;

		/*
		 * // if quick profit target set, cancel target if it has not bee a
		 * quick profit if (Constants.TRADE_CNT.equals(trade.type) && ( lastbar
		 * - trade.entryPos > 8 ) && ( trade.targetReached == false)) {
		 * trade.targetPrice = 0; trade.targetPositionSize = 0;
		 *
		 * if ( MODE == Constants.REAL_MODE) cancelOrder( trade.targetId); }
		 */

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int top;

				if (Constants.TRADE_RST.equals(trade.type))
					top = Utility.getHigh(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					top = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					top = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				for (int i = lastbar; i > top; i--)
				{
					PushHighLow phl = findLastNLow(quotes, top, i, 1);
					if (phl != null)
					{
						if (i == lastbar)
						{
							// take partical profit if the pull back is great
							// than 25pips
							if ((trade.profitTake2 == false)
									&& (phl.pullBack.high - (((QuoteData) quotes[phl.prePos]).low) > LOCKIN_PROFIT * PIP_SIZE))
							{
								trade.profitTake2Triggered = true;
								trade.profitTake2 = true;
								break;
							}

							/*
							 * this limits the profit, might not want this for (
							 * int j = i-1; j > trade.profitTakeStartPos; j--) {
							 * PushHighLow phl_pre = findLastNLow( quotes,
							 * trade.profitTakeStartPos, j, 2 ); if ( phl_pre !=
							 * null ) { double low =
							 * ((QuoteData)quotes[phl_pre.prePos]).low;
							 *
							 * QuoteData afterLow = Utility.getLow( quotes,
							 * phl_pre.curPos, i-1); if ( low - afterLow.low <
							 * 2.5 * PIP_SIZE ) { trade.profitTake2Triggered =
							 * true; break; } }
							 *
							 * }
							 */

						}

						/*
						 * // take profit if it is > 30 pips from the last pull
						 * back // make less money this way if (
						 * trade.takePartialProfit == false ) { if (
						 * phl.pullBack.high - data.close > 30 * PIP_SIZE ) {
						 * logger.warning(symbol + " " + CHART +
						 * " take profit, last pull back is " +
						 * ((QuoteData)quotes[phl.prePos]).low + " @ " +
						 * ((QuoteData)quotes[phl.prePos]).time );
						 * logger.warning(symbol + " " + CHART +
						 * " take partical profit to lock in 30 pips");
						 *
						 * AddTradeCloseRecord( data.time, Constants.ACTION_BUY,
						 * trade.remainingPositionSize/2, data.close);
						 * closePositionByMarket( trade.remainingPositionSize/2,
						 * data.close); trade.takePartialProfit = true; } }
						 */

						// take profit if it breaks higher-high/lower-low
						if (data.high > phl.pullBack.high)
						{
							int lastTradeEntryPos = trade.entryPos;
							double lastTradEentryPrice = trade.entryPrice;
							String lastTradeType = trade.type;
							logger.warning(symbol + " " + CHART + " minute target hit for breaking lower low " + data.time);

							AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);
							closePositionByMarket(trade.remainingPositionSize, data.close);

							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST) && (reverse_after_profit == true))
							{
								int lastbarL = quotesL.length - 1;
								double[] ema20L = Indicator.calculateEMA(quotesL, 20);
								double[] ema50L = Indicator.calculateEMA(quotesL, 50);

								if (ema20L[lastbarL] > ema50L[lastbarL])
								{
									QuoteData lowAfterEntry = Utility.getLow(quotes, lastTradeEntryPos, lastbar);
									if (lastTradEentryPrice - lowAfterEntry.low < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
									{
										trade = new Trade(symbol);
										trade.type = Constants.TRADE_CNT;
										trade.action = Constants.ACTION_BUY;
										trade.POSITION_SIZE = POSITION_SIZE;
										trade.lowHighAfterEntry = lowAfterEntry;

										logger.warning(symbol + " " + CHART + " place market reverse buy order");
										trade.orderId = placeMktOrder(Constants.ACTION_BUY, trade.POSITION_SIZE);
										trade.price = data.close;// phl.pullBack.high;
										trade.entryPrice = trade.price;
										trade.status = Constants.STATUS_PLACED;
										trade.remainingPositionSize = trade.POSITION_SIZE;
										trade.entryTime = data.time;// ((QuoteData)quotes[lastbar]).time;
										trade.entryPos = lastbar;
										if (MODE == Constants.TEST_MODE)
											AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_BUY, trade.POSITION_SIZE,
													trade.entryPrice);

										trade.stop = lowAfterEntry.low;
										if ((trade.entryPrice - trade.stop) > FIXED_STOP * PIP_SIZE)
											trade.stop = trade.entryPrice - FIXED_STOP * PIP_SIZE;

										trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

										logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
										trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.POSITION_SIZE, null);// new
																																	// Long(oca).toString());
									}
								}
							}
							return;
						}
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).high > ((QuoteData) quotesS[lastbarS - 1]).high))
				{
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					// large pull back, lock in profit
					/*
					 * trade.targetPrice = data.close; // TODO: this could be
					 * better trade.targetPrice = adjustPrice( data.close,
					 * Constants.ADJUST_TYPE_DOWN);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_BUY,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if (trade.profitTake2Triggered != true)
			{
				if (trade.entryPos + 2 > lastbar)
					return;

				int bottom;
				if (Constants.TRADE_RST.equals(trade.type))
					bottom = Utility.getLow(quotes, trade.entryPos, lastbar).pos;
				else if (Constants.TRADE_CNT.equals(trade.type))
					bottom = trade.lowHighAfterEntry.pos;
				else if (Constants.TRADE_RSC.equals(trade.type))
					bottom = trade.profitStartingPointing;
				else
				{
					logger.severe(symbol + " " + CHART + " no trade status set!");
					return;
				}

				for (int i = lastbar; i > bottom; i--)
				{
					PushHighLow phl = findLastNHigh(quotes, bottom, i, 1);
					if (phl != null)
					{
						// logger.warning(symbol + " " + CHART +
						// "this high between " +
						// ((QuoteData)quotes[phl.prePos]).time + "@" +
						// ((QuoteData)quotes[phl.prePos]).high + "  -  " +
						// ((QuoteData)quotes[phl.curPos]).time + "@" +
						// ((QuoteData)quotes[phl.curPos]).high + " pullback@" +
						// phl.pullBack );

						if (i == lastbar)
						{
							if ((trade.profitTake2 == false) && ((((QuoteData) quotes[phl.prePos]).high - phl.pullBack.low) > 25 * PIP_SIZE))
							{
								trade.profitTake2Triggered = true;
								trade.profitTake2 = true;
								break;
							}

							/*
							 * take quick profit, this limits the profit for (
							 * int j = i-1; j > bottom; j--) { PushHighLow
							 * phl_pre = findLastNHigh( quotes, bottom, j, 2 );
							 * if ( phl_pre != null ) { double high =
							 * ((QuoteData)quotes[phl_pre.prePos]).high;
							 *
							 * QuoteData afterHigh = Utility.getHigh( quotes,
							 * phl_pre.curPos, i-1); if ( afterHigh.high - high
							 * < 2.5 * PIP_SIZE ) { logger.warning(symbol + " "
							 * + CHART + " minute last high was " +
							 * ((QuoteData)quotes[phl_pre.prePos]).time +
							 * " after high was " +
							 * ((QuoteData)quotes[afterHigh.pos]).time +
							 * " difference is less than 2.5 pips, take profit"
							 * ); trade.profitTake2Triggered = true; break; } }
							 *
							 * }
							 */
						}

						/*
						 * take profit if the push is > 30 pips, does not add
						 * profit if ( trade.takePartialProfit == false ) { if (
						 * data.close - phl.pullBack.low > 30 * PIP_SIZE ) {
						 * logger.warning(symbol + " " + CHART +
						 * " take profit, last pull back is " +
						 * ((QuoteData)quotes[phl.prePos]).high + " @ " +
						 * ((QuoteData)quotes[phl.prePos]).time );
						 * logger.warning(symbol + " " + CHART +
						 * " take partical profit to lock in 30 pips");
						 *
						 * AddTradeCloseRecord( data.time,
						 * Constants.ACTION_SELL, trade.remainingPositionSize/2,
						 * data.close); closePositionByMarket(
						 * trade.remainingPositionSize/2, data.close);
						 * trade.takePartialProfit = true; } }
						 */

						if (data.low < phl.pullBack.low)
						{
							int lastTradeEntryPos = trade.entryPos;
							double lastTradEentryPrice = trade.entryPrice;
							String lastTradeType = trade.type;
							// double lastPullbackHigh =
							// ((QuoteData)quotes[trade.pullBackPos]).high;
							logger.warning(symbol + " " + CHART + " minute target hit for breaking higher high at " + data.time);
							if (MODE == Constants.TEST_MODE)
								AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);

							closePositionByMarket(trade.remainingPositionSize, data.close);

							/*
							 * else if ( MODE == Constants.REAL_MODE) {
							 * trade.targetPrice = phl.pullBack.low;
							 * trade.targetPrice = adjustPrice( data.close,
							 * Constants.ADJUST_TYPE_UP);
							 * trade.targetPositionSize =
							 * trade.remainingPositionSize;
							 *
							 * trade.targetId =
							 * placeLmtOrder(Constants.ACTION_SELL,
							 * trade.targetPrice, trade.targetPositionSize,
							 * null);//new Long(oca).toString());
							 */

							// check to see if it worth reversal
							if (lastTradeType.equals(Constants.TRADE_RST) && (reverse_after_profit == true))
							{
								// enter a reverse trade only if following the
								// biger trend
								int lastbarL = quotesL.length - 1;
								double[] ema20L = Indicator.calculateEMA(quotesL, 20);
								double[] ema50L = Indicator.calculateEMA(quotesL, 50);

								if (ema20L[lastbarL] < ema50L[lastbarL])
								{
									QuoteData highAfterEntry = Utility.getHigh(quotes, lastTradeEntryPos, lastbar);
									if (highAfterEntry.high - lastTradEentryPrice < SHALLOW_PROFIT_REVERSAL * PIP_SIZE)
									{
										trade = new Trade(symbol);
										trade.type = Constants.TRADE_CNT;
										trade.action = Constants.ACTION_SELL;
										trade.POSITION_SIZE = POSITION_SIZE;
										trade.lowHighAfterEntry = highAfterEntry;

										logger.warning(symbol + " " + CHART + " place market reverse sell order");
										trade.orderId = placeMktOrder(Constants.ACTION_SELL, trade.POSITION_SIZE);
										trade.price = data.close;// phl.pullBack.low;
										trade.entryPrice = trade.price;
										trade.status = Constants.STATUS_PLACED;
										trade.remainingPositionSize = trade.POSITION_SIZE;
										trade.entryTime = data.time;// ((QuoteData)quotes[lastbar]).time;
										trade.entryPos = lastbar;
										if (MODE == Constants.TEST_MODE)
											AddTradeOpenRecord(trade.type, trade.entryTime, Constants.ACTION_SELL, trade.POSITION_SIZE,
													trade.entryPrice);

										trade.stop = highAfterEntry.high;
										if ((trade.stop - trade.entryPrice) > FIXED_STOP * PIP_SIZE)
											trade.stop = trade.entryPrice + FIXED_STOP * PIP_SIZE;

										trade.stop = adjustPrice(trade.stop, Constants.ADJUST_TYPE_UP);

										logger.warning(symbol + " " + CHART + " reversal order adjusted stop is " + trade.stop);
										trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.POSITION_SIZE, null);// new
																																	// Long(oca).toString());
									}
								}
							}
							return;
						}
					}
				}
			}

			if (trade.profitTake2Triggered == true)
			{
				if ((((QuoteData) quotesS[lastbarS]).low < ((QuoteData) quotesS[lastbarS - 1]).low))
				{
					if (MODE == Constants.TEST_MODE)
						AddTradeCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, data.close);

					closePositionByMarket(trade.remainingPositionSize, data.close);
					// trade.profitTake2 = true;
					// trade.profitTake2Triggered = false;

					/*
					 * trade.targetPrice = data.close; trade.targetPrice =
					 * adjustPrice( data.close, Constants.ADJUST_TYPE_UP);
					 *
					 * trade.targetPositionSize = trade.remainingPositionSize;
					 *
					 * trade.targetId = placeLmtOrder(Constants.ACTION_SELL,
					 * trade.targetPrice, trade.targetPositionSize, null);//new
					 * Long(oca).toString()); trade.profitTake2 = true;
					 * trade.profitTake2Triggered = false;
					 */
					return;
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


	public boolean inExitingTime(String timeStr)
	{
		return false;
		/*
		int hr = new Integer(timeStr.substring(9, 12).trim());
		// String min = timeStr.substring(13,15);

		if (hr >= 11)
			return true;
		else
			return false;
		*/
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


}


