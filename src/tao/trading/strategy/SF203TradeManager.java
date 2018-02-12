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
import tao.trading.TradeEvent;
import tao.trading.TradePosition;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.MABreakOutList;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.MABreakOutAndTouch;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.util.NameValue;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;
import tao.trading.trend.analysis.TrendLine;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

public class SF203TradeManager extends TradeManager2
{
	SF203 mainProgram;
	boolean CNT = false;
    String TIME_ZONE;
	boolean oneMinutChart = false;
	String tradeZone1, tradeZone2;
	double minTarget1, minTarget2;
	String currentTime;
	int DEFAULT_RETRY = 0;
	int DEFAULT_PROFIT_TARGET = 400;
	QuoteData currQuoteData;
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
	boolean average_up = false;

	int LOT_SIZE; 
	int TAKE_PROFIT_LOT_SIZE;

	
	/********************************
	 *  reverse trade setting
	 *******************************/
	boolean tip_reverse = true;
	boolean stop_reverse = true;


	
	
	
	public SF203TradeManager()
	{
		super();
	}

	public SF203TradeManager(String account, EClientSocket client, Contract contract, int symbolIndex, Logger logger)
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
			if ( trade.entryPrice == 0 )
				trade.entryPrice = trade.limitPrice1;
			trade.remainingPositionSize += trade.limitPos1; //+= filled;
			if ( trade.getEntryTime() == null )
				trade.setEntryTime(quotes[lastbar].time);

			// calculate stop here
			String oca = new Long(new Date().getTime()).toString();
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				if ( trade.stop == 0 )
					trade.stop = (trade.limit1Stop==0)? adjustPrice(trade.limitPrice1 + FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP): trade.limit1Stop;
				if ( trade.stopId != 0 )
					cancelOrder(trade.stopId);
				warning("place stop order : " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, oca);

				if ( trade.targetId != 0 )
					cancelOrder(trade.targetId);

				if ( trade.targetPrice == 0 )
					trade.targetPrice = adjustPrice(trade.limitPrice1 - DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
				
				warning("place target order : " + trade.targetPrice);
				trade.targetPos = trade.remainingPositionSize;
				trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, oca);

			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				if ( trade.stop == 0 )
					trade.stop = (trade.limit1Stop==0)? adjustPrice(trade.limitPrice1 - FIXED_STOP * PIP_SIZE, Constants.ADJUST_TYPE_UP): trade.limit1Stop;
				if ( trade.stopId != 0 )
						cancelOrder(trade.stopId);
				warning("place stop order : " + trade.stop);
				trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, oca);

				if ( trade.targetId != 0 )
					cancelOrder(trade.targetId);

				if ( trade.targetPrice == 0 )
					trade.targetPrice = adjustPrice(trade.limitPrice1 + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
				
				warning("place target order : " + trade.targetPrice);
				trade.targetPos = trade.remainingPositionSize;
				trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPos, oca);
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
			trade.remainingPositionSize -= trade.targetPos;
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
			else if (trade.remainingPositionSize < 0)
			{
				String prevTradeType = trade.action;
				int prevPosionSize = trade.remainingPositionSize;
				double prevPrice = trade.targetPrice;
				
				cancelTargets();
				removeTrade();
				
				String oca = new Long(new Date().getTime()).toString();

				// reverse to a CNT trade, for manual trades
				if (Constants.ACTION_SELL.equals(prevTradeType))
				{
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_BUY);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = trade.remainingPositionSize = prevPosionSize;
					trade.entryPrice = prevPrice;
					trade.entryTime = quotes[lastbar].time;
					trade.stop = adjustPrice(trade.entryPrice - FIXED_STOP * PIP_SIZE * 1.5, Constants.ADJUST_TYPE_DOWN); 

					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
			
					trade.targetPrice = adjustPrice(trade.entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					trade.targetId = placeLmtOrder(Constants.ACTION_SELL, trade.targetPrice, trade.remainingPositionSize, oca);
				}
				else if (Constants.ACTION_BUY.equals(prevTradeType))
				{
					createOpenTrade(Constants.TRADE_CNT, Constants.ACTION_SELL);
					trade.detectTime = quotes[lastbar].time;
					trade.POSITION_SIZE = trade.remainingPositionSize = prevPosionSize;
					trade.entryPrice = prevPrice;
					trade.entryTime = quotes[lastbar].time;
					trade.stop = adjustPrice(trade.entryPrice + FIXED_STOP * PIP_SIZE * 1.5, Constants.ADJUST_TYPE_DOWN); 

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
			
					trade.targetPrice = adjustPrice(trade.entryPrice - DEFAULT_PROFIT_TARGET * PIP_SIZE,  Constants.ADJUST_TYPE_DOWN);
					trade.targetId = placeLmtOrder(Constants.ACTION_BUY, trade.targetPrice, trade.remainingPositionSize, oca);
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
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
					
				if ( stop_reverse)
					processAfterHitStopLogic_c();
				else
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
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					if (trade.targetId != 0)
					{
						cancelOrder(trade.targetId);
						trade.targetPos = trade.remainingPositionSize;
						trade.targetId = placeStopOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
					}
						
				}

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
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					if (trade.targetId != 0)
					{
						cancelOrder(trade.targetId);
						trade.targetPos = trade.remainingPositionSize;
						trade.targetId = placeStopOrder(Constants.ACTION_BUY, trade.targetPrice, trade.targetPos, null);
					}
				}
				return;

			}
			else if ((trade.targetId != 0) && (data.low < trade.targetPrice))
			{
				warning("target hit, close " + trade.targetPos + " @ " + trade.targetPrice);
				
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.targetPos, trade.targetPrice);
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
			else if ((trade.takeProfitPartialId != 0) && (data.low < trade.takeProfitPartialPrice))
			{
				warning("take partial profit target hit, close " + trade.takeProfitPartialPosSize + " @ " + trade.takeProfitPartialPrice);
				
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.takeProfitPartialPosSize, trade.takeProfitPartialPrice);
				trade.takeProfitPartialId = 0;
				trade.takeProfitHistory.add(new TradePosition(trade.takeProfitPartialPrice, trade.takeProfitPartialPosSize));
				
				trade.remainingPositionSize -= trade.takeProfitPartialPosSize;
				if (trade.remainingPositionSize <= 0)
				{
					removeTrade();
				}
				else
				{
					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					
					// place next take partial profit trade if it exists
					TradePosition tr = trade.takeProfitQueue.poll();
					if ( tr != null )
					{
						trade.takeProfitHistory.add(tr);

						trade.takeProfitPartialPrice = tr.price;
						trade.takeProfitPartialPosSize = tr.position_size;

						trade.takeProfitPartialId = placeLmtOrder(Constants.ACTION_BUY, trade.takeProfitPartialPrice, trade.takeProfitPartialPosSize, null);
						warning("take partial profit " + Constants.ACTION_BUY + " order placed@ " + trade.takeProfitPartialPrice + " " + trade.takeProfitPartialPosSize );
			
					}
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

				if ( stop_reverse)
					processAfterHitStopLogic_c();
				else
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
				{
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					if (trade.targetId != 0)
					{
						cancelOrder(trade.targetId);
						trade.targetPos = trade.remainingPositionSize;
						trade.targetId = placeStopOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPos, null);
					}
				}
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
				{
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					if (trade.targetId != 0)
					{
						cancelOrder(trade.targetId);
						trade.targetPos = trade.remainingPositionSize;
						trade.targetId = placeStopOrder(Constants.ACTION_SELL, trade.targetPrice, trade.targetPos, null);
					}
				}
				return;
			}
			else if ((trade.targetId != 0) && (data.high > trade.targetPrice))
			{
				warning("target hit, close " + trade.targetPos + " @ " + trade.targetPrice);
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.targetPos, trade.targetPrice);
				trade.targetId = 0;

				cancelStop();
				removeTrade();
				return;
			}
			else if ((trade.takeProfitPartialId != 0) && (data.high > trade.takeProfitPartialPrice))
			{
				warning("take partial profit target hit, close " + trade.takeProfitPartialPosSize + " @ " + trade.takeProfitPartialPrice);
				
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.takeProfitPartialPosSize, trade.takeProfitPartialPrice);
				trade.takeProfitPartialId = 0;

				trade.remainingPositionSize -= trade.takeProfitPartialPosSize;
				if (trade.remainingPositionSize <= 0)
				{
					removeTrade();
				}
				else
				{
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					
					// place next take partial profit trade if it exists
					TradePosition tr = trade.takeProfitQueue.poll();
					if ( tr != null )
					{
						trade.takeProfitHistory.add(tr);

						trade.takeProfitPartialPrice = tr.price;
						trade.takeProfitPartialPosSize = tr.position_size;

						trade.takeProfitPartialId = placeLmtOrder(Constants.ACTION_SELL, trade.takeProfitPartialPrice, trade.takeProfitPartialPosSize, null);
						warning("take partial profit " + Constants.ACTION_SELL + " order placed@ " + trade.takeProfitPartialPrice + " " + trade.takeProfitPartialPosSize );
					}
				}
				return;
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
		if (mainProgram.isNoReverse(symbol ))
		{
			warning("no reverse");
			return;
		}
		
		
		BigTrend bt = determineBigTrend( quotesL);
		//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		//BigTrend bt = calculateTrend( quotes240);
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
	public Trade checkBreakOut5a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);                             //  can be switched to 1
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			fine("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;
			
			int lastUpPosStart = lastUpPos;
			while ( quotesL[lastUpPosStart].low > ema20L[lastUpPosStart])
				lastUpPosStart--;
			
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastUpPosStart, 1);
			if ( prevDownPos == Constants.NOT_FOUND )  
				return null;

			// looking for upside
			for ( start = prevDownPos+1; start < lastbarL; start++)														// here is switch 1
				if (( quotesL[start].low > ema20L[start]) && ( quotesL[start+1].low > ema20L[start+1]))
				//if ( quotesL[start].low > ema20L[start])
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			// now it is the first up
			int touch20MA = 0;																							// here is switch 2
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
				
				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
				trade.POSITION_SIZE = POSITION_SIZE;
				FIXED_STOP = FIXED_STOP;

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
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 1);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			

			// looking for upside																						// here is switch 1
			for ( start = prevUpPos+1; start < lastbarL; start++)
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
				//if (quotesL[start].high < ema20L[start])
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;

			// now it is the first up
			int touch20MA = 0;																							// here is switch 2
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

				trade.setFirstBreakOutTime(firstBreakOut.time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				
				trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
				trade.POSITION_SIZE = POSITION_SIZE;
				FIXED_STOP = FIXED_STOP;
				
				warning("break DOWN detected at " + quotesL[lastbarL].time + " start:" + quotesL[start].time + " touch20MA:" + quotesL[touch20MA].time + " breakout tip is " + trade.entryPrice + "@" + firstBreakOut.time + " touch20MA:" + quotesL[touch20MA].time  );
				return trade;
			}
		}
		
		return null;
	}

	
	
	

	

	

	/***************************
	 * This is based on 4a but adding big push bar in the initial setup
	 * @param data
	 * @param lastTick_bid
	 * @param lastTick_ask
	 * @return
	 */
	
	public Trade checkBreakOut6a(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		System.out.println("check break out");
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
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
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

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
				String tradeType = Constants.TRADE_EUR;
				/*
				int lastUpTrend = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 24);
				if ( lastUpTrend < start - 24 )
				{

				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				/*
				int prevDownStart = prevDownPos-1;
				while ( ! (( quotesL[prevDownStart-1].high > ema20L[prevDownStart-1]) && ( quotesL[prevDownStart-2].high > ema20L[prevDownStart-2])))
						prevDownStart--;
				
				if ( prevDownPos - prevDownStart < 24 )
					return null;
					*/
				
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				//System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;
						
						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;
						
						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}*/

				
				createOpenTrade(tradeType, Constants.ACTION_BUY);
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
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
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
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

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


				// run trend analyser
			//	String tradeType = Constants.TRADE_EUR;
				/*
				int lastDownTrend = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 24);
				if ( lastDownTrend < start - 24 )
				{
				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				
				/*
				int prevUpStart = prevUpPos1-1;
				while ( ! (( quotesL[prevUpStart-1].low < ema20L[prevUpStart-1]) && ( quotesL[prevUpStart-2].low < ema20L[prevUpStart-2])))
					prevUpStart--;
				
				if ( prevUpPos - prevUpStart < 24 )
					return null;
*/
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;

						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;

						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}
				
				/*
				if ( tradeType == Constants.TRADE_RVS )
				{	
				System.out.println("large move end :" + lastLargeMoveEnd.time);
				for ( int i =lastLargeMoveEnd.pos+1; i < lastbarL-2; i++)
				{
					if (( quotesL[i].low < ema20L[i]) && ( quotesL[i+1].low < ema20L[i+1]))
					{
						int latestUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
						
						if ( latestUp > i+1 )
						{
							warning (" find latest diverage at " + quotesL[latestUp].time + " trade removed");
							//return null;
						}
						break;
					}
				}
				
				}*/
				
				
				
				
				
				
				
				
				
				
				
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
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

	

	
	
	
	
	
	
	
	
	public Trade checkPullBacktoBigMoves(QuoteData data)
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema10L = Indicator.calculateEMA(quotesL, 10);

		int pushTip = lastbarL-2;
		if ((quotesL[lastbarL-2].close > ema10L[lastbarL-2]) && (quotesL[lastbarL-1].high < quotesL[lastbarL-2].high))
		{
			if (( trade != null ) &&  trade.action.equals(Constants.ACTION_BUY))
				return null;

			Push lastPush = Utility.findLargestConsectiveUpBars(quotesL, lastbarL-12, lastbarL-2);
			if (( quotesL[lastPush.pushEnd].close - quotesL[lastPush.pushStart].open ) > 2 * FIXED_STOP * PIP_SIZE )
			{
				if  (findTradeHistory( Constants.ACTION_BUY, quotesL[pushTip].time) != null) 
					return null;

				System.out.println(symbol + " " + CHART + " " + " BUY detected " + quotesL[lastbarL].time);
				createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
				trade.status = Constants.STATUS_DETECTED;
				
				trade.pullBackStartL = pushTip; 
				trade.detectPos = lastbarL;
				trade.setFirstBreakOutTime(quotesL[pushTip].time);
				trade.setFirstBreakOutStartTime(quotesL[pushTip].time);
				trade.setTouch20MATime(quotesL[lastbarL-1].time);
				return trade;
				
			}

		}
		else if ((quotesL[lastbarL-2].close < ema10L[lastbarL-2]) && (quotesL[lastbarL-1].low > quotesL[lastbarL-2].low))
		{
			
		}
		
		return null;
	}
		

	
	
	
	
	
	
	
	
	public Trade checkBreakOut_withPush(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		//System.out.println("check break out");
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
			//	return null;
			
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
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

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
				String tradeType = Constants.TRADE_EUR;
				/*
				int lastUpTrend = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 24);
				if ( lastUpTrend < start - 24 )
				{

				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				/*
				int prevDownStart = prevDownPos-1;
				while ( ! (( quotesL[prevDownStart-1].high > ema20L[prevDownStart-1]) && ( quotesL[prevDownStart-2].high > ema20L[prevDownStart-2])))
						prevDownStart--;
				
				if ( prevDownPos - prevDownStart < 24 )
					return null;
					*/
				
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				//System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;
						
						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;
						
						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}*/

				
				//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_UP.equals(bt.direction))
				//		return null;

				
				
				createOpenTrade(tradeType, Constants.ACTION_BUY);
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
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
			//	return null;

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
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

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


				// run trend analyser
			//	String tradeType = Constants.TRADE_EUR;
				/*
				int lastDownTrend = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 24);
				if ( lastDownTrend < start - 24 )
				{
				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				
				/*
				int prevUpStart = prevUpPos1-1;
				while ( ! (( quotesL[prevUpStart-1].low < ema20L[prevUpStart-1]) && ( quotesL[prevUpStart-2].low < ema20L[prevUpStart-2])))
					prevUpStart--;
				
				if ( prevUpPos - prevUpStart < 24 )
					return null;
*/
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;

						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;

						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}
				
				/*
				if ( tradeType == Constants.TRADE_RVS )
				{	
				System.out.println("large move end :" + lastLargeMoveEnd.time);
				for ( int i =lastLargeMoveEnd.pos+1; i < lastbarL-2; i++)
				{
					if (( quotesL[i].low < ema20L[i]) && ( quotesL[i+1].low < ema20L[i+1]))
					{
						int latestUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
						
						if ( latestUp > i+1 )
						{
							warning (" find latest diverage at " + quotesL[latestUp].time + " trade removed");
							//return null;
						}
						break;
					}
				}
				
				}*/
				
				
				
				
				
				
				
				
				
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_DOWN.equals(bt.direction))
				//		return null;
				
				
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		//System.out.println("check break out");
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
			//	return null;
			
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
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

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
				String tradeType = Constants.TRADE_EUR;
				/*
				int lastUpTrend = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 24);
				if ( lastUpTrend < start - 24 )
				{

				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				/*
				int prevDownStart = prevDownPos-1;
				while ( ! (( quotesL[prevDownStart-1].high > ema20L[prevDownStart-1]) && ( quotesL[prevDownStart-2].high > ema20L[prevDownStart-2])))
						prevDownStart--;
				
				if ( prevDownPos - prevDownStart < 24 )
					return null;
					*/
				
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				//System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;
						
						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;
						
						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}*/

				
				//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_UP.equals(bt.direction))
				//		return null;

				
				
				createOpenTrade(tradeType, Constants.ACTION_BUY);
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
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
			//	return null;

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
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

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


				// run trend analyser
			//	String tradeType = Constants.TRADE_EUR;
				/*
				int lastDownTrend = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 24);
				if ( lastDownTrend < start - 24 )
				{
				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				
				/*
				int prevUpStart = prevUpPos1-1;
				while ( ! (( quotesL[prevUpStart-1].low < ema20L[prevUpStart-1]) && ( quotesL[prevUpStart-2].low < ema20L[prevUpStart-2])))
					prevUpStart--;
				
				if ( prevUpPos - prevUpStart < 24 )
					return null;
*/
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;

						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;

						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}
				
				/*
				if ( tradeType == Constants.TRADE_RVS )
				{	
				System.out.println("large move end :" + lastLargeMoveEnd.time);
				for ( int i =lastLargeMoveEnd.pos+1; i < lastbarL-2; i++)
				{
					if (( quotesL[i].low < ema20L[i]) && ( quotesL[i+1].low < ema20L[i+1]))
					{
						int latestUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
						
						if ( latestUp > i+1 )
						{
							warning (" find latest diverage at " + quotesL[latestUp].time + " trade removed");
							//return null;
						}
						break;
					}
				}
				
				}*/
				
				
				
				
				
				
				
				
				
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_DOWN.equals(bt.direction))
				//		return null;
				
				
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
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


	
	
	
	public Trade checkBreakOut4a1(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		//System.out.println("check break out");
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
				return null;

			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
			//	return null;
			
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
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

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
				String tradeType = Constants.TRADE_EUR;
				/*
				int lastUpTrend = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 24);
				if ( lastUpTrend < start - 24 )
				{

				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				/*
				int prevDownStart = prevDownPos-1;
				while ( ! (( quotesL[prevDownStart-1].high > ema20L[prevDownStart-1]) && ( quotesL[prevDownStart-2].high > ema20L[prevDownStart-2])))
						prevDownStart--;
				
				if ( prevDownPos - prevDownStart < 24 )
					return null;
					*/
				
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				//System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;
						
						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;
						
						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}*/

				
				//QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_UP.equals(bt.direction))
				//		return null;

				
				
				createOpenTrade(tradeType, Constants.ACTION_SELL);
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
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			//BigTrend bt = determineBigTrend2( quotes240);
			//if (!( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
			//	return null;

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
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

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


				// run trend analyser
			//	String tradeType = Constants.TRADE_EUR;
				/*
				int lastDownTrend = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 24);
				if ( lastDownTrend < start - 24 )
				{
				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				
				/*
				int prevUpStart = prevUpPos1-1;
				while ( ! (( quotesL[prevUpStart-1].low < ema20L[prevUpStart-1]) && ( quotesL[prevUpStart-2].low < ema20L[prevUpStart-2])))
					prevUpStart--;
				
				if ( prevUpPos - prevUpStart < 24 )
					return null;
*/
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;

						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;

						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}
				
				/*
				if ( tradeType == Constants.TRADE_RVS )
				{	
				System.out.println("large move end :" + lastLargeMoveEnd.time);
				for ( int i =lastLargeMoveEnd.pos+1; i < lastbarL-2; i++)
				{
					if (( quotesL[i].low < ema20L[i]) && ( quotesL[i+1].low < ema20L[i+1]))
					{
						int latestUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
						
						if ( latestUp > i+1 )
						{
							warning (" find latest diverage at " + quotesL[latestUp].time + " trade removed");
							//return null;
						}
						break;
					}
				}
				
				}*/
				
				
				
				
				
				
				
				
				
				//BigTrend bt = TrendAnalysis.determineBigTrend_Trendy(quotes240 );
				//if (!Constants.DIRT_DOWN.equals(bt.direction))
				//		return null;
				
				
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_BUY);
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

	

	
	public Trade checkBreakOut4a_bak(QuoteData data, double lastTick_bid, double lastTick_ask )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		//double[] ema50L = Indicator.calculateEMA(quotesL, 50);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		//System.out.println("check break out");
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
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
			/*
			int touch20MA = 0;
			for ( int i = start+1 ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].high < quotesL[i].high ) && ( quotesL[i+2].high < quotesL[i].high ))
				{
					touch20MA = i+1;
					debug("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
			*/

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
				String tradeType = Constants.TRADE_EUR;
				/*
				int lastUpTrend = Pattern.findLastPriceConsectiveAboveOrAtMA(quotesL, ema20L, start, 24);
				if ( lastUpTrend < start - 24 )
				{

				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				/*
				int prevDownStart = prevDownPos-1;
				while ( ! (( quotesL[prevDownStart-1].high > ema20L[prevDownStart-1]) && ( quotesL[prevDownStart-2].high > ema20L[prevDownStart-2])))
						prevDownStart--;
				
				if ( prevDownPos - prevDownStart < 24 )
					return null;
					*/
				
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				//System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;
						
						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;
						
						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}*/

				
				createOpenTrade(tradeType, Constants.ACTION_BUY);
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
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
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
			/*
			int touch20MA = 0;
			for ( int i = start ; i < lastbarL-1; i++)
			{
				if (( quotesL[i+1].low > quotesL[i].low ) && ( quotesL[i+2].low > quotesL[i].low ))
				{
					touch20MA = i+1;
					warning("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			if ( touch20MA == 0 )
				return null;
*/

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


				// run trend analyser
			//	String tradeType = Constants.TRADE_EUR;
				/*
				int lastDownTrend = Pattern.findLastPriceConsectiveBelowOrAtMA(quotesL, ema20L, start, 24);
				if ( lastDownTrend < start - 24 )
				{
				}
				else
				{
					// contiunation?
					tradeType = Constants.TRADE_PBK;
					return null;
					
				}*/
				
				/*
				int prevUpStart = prevUpPos1-1;
				while ( ! (( quotesL[prevUpStart-1].low < ema20L[prevUpStart-1]) && ( quotesL[prevUpStart-2].low < ema20L[prevUpStart-2])))
					prevUpStart--;
				
				if ( prevUpPos - prevUpStart < 24 )
					return null;
*/
				
				/*
				BigTrend bt = determineBigTrend( quotesL);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotesL));
				int lastLargeMoveInd = -1;
				Push lastLargeMovePush = null;
				int lastLargeMoveStart = -1;
				QuoteData lastLargeMoveEnd = null;
				int lastLargeMoveDirection = 0; 
				
				for ( int i = pushes.size()-1; i >=0; i--)
				{
					lastLargeMovePush = pushes.elementAt(i);
					if ( lastLargeMovePush.duration >= 48 )
					{
						lastLargeMoveInd = i;
						break;
					}
				}
				
				if ( lastLargeMoveInd != -1 )
				{
					if ( lastLargeMovePush.direction == Constants.DIRECTION_UP )
					{
						lastLargeMoveDirection = Constants.DIRECTION_UP;
						lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_RVS;

						System.out.println("Last large Move UP:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (lastLargeMoveEnd.high - quotesL[lastLargeMoveStart].low)/PIP_SIZE );
					}
					else if ( lastLargeMovePush.direction == Constants.DIRECTION_DOWN )
					{
						lastLargeMoveDirection = Constants.DIRECTION_DOWN;
						lastLargeMoveEnd = Utility.getLow( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						lastLargeMoveStart = lastLargeMovePush.pushStart;
						tradeType = Constants.TRADE_PBK;

						System.out.println("Last large Move DOWN:  start:" + quotesL[lastLargeMoveStart].time + " end:" + lastLargeMoveEnd.time + " size:" + (quotesL[lastLargeMoveStart].high - lastLargeMoveEnd.low)/PIP_SIZE );
					}
				}
				
				/*
				if ( tradeType == Constants.TRADE_RVS )
				{	
				System.out.println("large move end :" + lastLargeMoveEnd.time);
				for ( int i =lastLargeMoveEnd.pos+1; i < lastbarL-2; i++)
				{
					if (( quotesL[i].low < ema20L[i]) && ( quotesL[i+1].low < ema20L[i+1]))
					{
						int latestUp = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, 2);
						
						if ( latestUp > i+1 )
						{
							warning (" find latest diverage at " + quotesL[latestUp].time + " trade removed");
							//return null;
						}
						break;
					}
				}
				
				}*/
				
				
				
				
				
				
				
				
				
				
				
				
				createOpenTrade(Constants.TRADE_EUR, Constants.ACTION_SELL);
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


	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public Trade checkBreakOut5(QuoteData data, double price )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA_mid(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
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
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].low <= ema20L[i] )
				{
					touch20MA = i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			
			if ( touch20MA == 0 )
				return null;

			
			QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED))
			{
				return null;
			}

			//for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
			for ( int i = touch20MA+1; i < lastbarL; i++)
			{
				if ( quotesL[i].high > firstBreakOut.high )
				{
					fine("first breakout high missed at " + quotesL[i].time);
					return null;
				}
			}
				

			double entryPrice = firstBreakOut.high;

			double triggerPrice = 0;
			if ( data != null )
				triggerPrice = data.high;
			else if ( price != 0 )
				triggerPrice = price;

			if ( market_order == true )
			{
				if (triggerPrice > entryPrice) 
				{
					info("entry buy detected at " + data.time);

					// now run the filter
					// 1 deep pull backs
					int firstBreakOutL = firstBreakOut.pos;
					QuoteData lowAfterFirstBreakOut = Utility.getLow( quotesL, firstBreakOutL+1, lastbarL );
					if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * FIXED_STOP * PIP_SIZE))
					{
						double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
						if ( diverage > 1.5 * FIXED_STOP )
						{
							info("entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
							return null;
						}
					}

					
					// trend analyser
					String tradeType = Constants.TRADE_EUR;
					QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
					BigTrend bt = determineBigTrend2( quotes240);
					Vector<Push> pushes = bt.pushes;
					System.out.println(bt.toString(quotes240));

					if (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
					{
						tradeType = Constants.TRADE_RVS;
						//lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						//lastLargeMoveStart = lastLargeMovePush.pushStart;
					}
					else if (( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
					{
						tradeType = Constants.TRADE_PBK;
						if ( bt.resistance != null )
							System.out.println("Resistence is at " + bt.resistance.high);
					}

					
					
					
					createOpenTrade(tradeType, Constants.ACTION_BUY);

					trade.setFirstBreakOutTime(firstBreakOut.time);
					trade.setFirstBreakOutStartTime(quotesL[start].time);
					trade.setTouch20MATime(quotesL[touch20MA].time);
					trade.prevDownStart = prevDownPos;
					
					trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.setEntryTime(quotes[lastbar].time);

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
							trade.status = Constants.STATUS_DETECTED;
							return trade;
						}
					}
		
					enterMarketPosition(entryPrice);
					//enterLimitPosition1(entryPrice);
		
					if (MODE == Constants.REAL_MODE)
					{
						EmailSender es = EmailSender.getInstance();
						es.sendYahooMail(symbol + " BUY order placed", "sent from automated trading system");
					}
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
		else if ( lastDownPos > lastUpPos )
		{	
			//debug("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

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
			if ( firstBreakOut == null )
				return null;
			
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED))
			{
				return null;
			}

			//for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
			for ( int i = touch20MA+1; i < lastbarL; i++)
			{
				if ( quotesL[i].low < firstBreakOut.low )
				{
					fine("first breakout low missed at " + quotesL[i].time);
					return null;
				}
			}


			double entryPrice = firstBreakOut.low;
			
			double triggerPrice = 99999;
			if ( data != null )
				triggerPrice = data.low;
			else if ( price != 0 )
				triggerPrice = price;
				
				
			if ( market_order == true ) 
			{
				if (triggerPrice < entryPrice ) 
				{
					// run the filters
					
					// filter 1:  deep pullbacks
					int firstBreakOutL = firstBreakOut.pos;
					QuoteData highAfterFirstBreakOut = Utility.getHigh( quotesL, firstBreakOutL+1, lastbarL );
					if (( highAfterFirstBreakOut != null ) && ((highAfterFirstBreakOut.high - entryPrice) > 1.5 * FIXED_STOP * PIP_SIZE))
					{
						double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
						if ( diverage > 1.5 * FIXED_STOP )
						{
							//double push = (quotesL[trade.prevUpStart].high - entryPrice)/PIP_SIZE;
							{
								info("entry sell diverage low is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
								//removeTrade();   
								return null;
							}
						}
					}
					

					// filter 2: trend analyser
					String tradeType = Constants.TRADE_EUR;
					QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
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

					

					createOpenTrade(tradeType, Constants.ACTION_SELL);

					trade.setFirstBreakOutTime(firstBreakOut.time);
					trade.setFirstBreakOutStartTime(quotesL[start].time);
					trade.setTouch20MATime(quotesL[touch20MA].time);
					trade.prevUpStart = prevUpPos;

					trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.setEntryTime(quotes[lastbar].time);
					
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
							trade.status = Constants.STATUS_DETECTED;
							return trade;
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
		
		return null;
	}


	
	
	
	
	
	public Trade checkBreakOut5a(QuoteData data, double price )
	{
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);
		int lastbarS = quotesS.length -1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
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
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			// now it is the first up

			// check touch 20MA
			int touch20MA = 0;
			for ( int i = start+1 ; i <= lastbarL; i++)
			{
				if ( quotesL[i].low <= ema20L[i] )
				{
					touch20MA = i;
					fine("touch 20MA detected at " + quotesL[touch20MA].time);
					break;
				}
			}
			
			if ( touch20MA == 0 )
				return null;

			
			
			//  here added
			QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			if ( firstBreakOut == null )
				return null;

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED))
			{
				return null;
				//removeTrade();
			}

			//for ( int i = firstBreakOut.pos+1; i < lastbarL; i++)
			for ( int i = touch20MA+1; i < lastbarL; i++)
			{
				if ( quotesL[i].high > firstBreakOut.high )
				{
					fine("first breakout high missed at " + quotesL[i].time);
					return null;
				}
			}
				

			double entryPrice = firstBreakOut.high;

			double triggerPrice = 0;
			if ( data != null )
				triggerPrice = data.high;
			else if ( price != 0 )
				triggerPrice = price;

			if ( market_order == true )
			{
				if (triggerPrice > entryPrice) 
				{
					info("entry buy detected at " + quotes[lastbar].time);
					
					double firstBreakOutHigh = firstBreakOut.high;
					int firstBreakOutS = Utility.findPositionByHour(quotesS, firstBreakOut.time, Constants.BACK_TO_FRONT );
					
					// 1 check entry missing
					for ( int i = firstBreakOutS+1; i < lastbarS; i++)
					{
						if ( quotesS[i].high > firstBreakOutHigh )
						{
							info("first breakout high missed at " + quotesS[i].time);
							return null;
						}
					}
		
		
					// 2.  check diverage
					QuoteData lowAfterFirstBreakOut = Utility.getLow( quotesS, firstBreakOutS+1, lastbarS );
					if ( lowAfterFirstBreakOut == null )
						return null;
					double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						info(data.time + " entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}

					

					// run trend analyser
					String tradeType = Constants.TRADE_EUR;
					QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
					BigTrend bt = determineBigTrend2( quotes240);
					Vector<Push> pushes = bt.pushes;
					System.out.println(bt.toString(quotes240));

					if (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
					{
						tradeType = Constants.TRADE_RVS;
						//lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
						//lastLargeMoveStart = lastLargeMovePush.pushStart;
					}
					else if (( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
					{
						tradeType = Constants.TRADE_PBK;
						if ( bt.resistance != null )
							System.out.println("Resistence is at " + bt.resistance.high);
					}

					
					
					
					createOpenTrade(tradeType, Constants.ACTION_BUY);

					trade.setFirstBreakOutTime(firstBreakOut.time);
					trade.setFirstBreakOutStartTime(quotesL[start].time);
					trade.setTouch20MATime(quotesL[touch20MA].time);
					trade.prevDownStart = prevDownPos;
					
					trade.entryPrice = trade.triggerPrice = firstBreakOut.high;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.setEntryTime(quotes[lastbar].time);

					trade.targetPrice = adjustPrice(entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);
					warning("break UP trade entered at " + quotes[lastbar].time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
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
							trade.status = Constants.STATUS_DETECTED;
							return trade;
						}
					}
		
					enterMarketPosition(entryPrice);
					//enterLimitPosition1(entryPrice);
		
					if (MODE == Constants.REAL_MODE)
					{
						EmailSender es = EmailSender.getInstance();
						es.sendYahooMail(symbol + " BUY order placed", "sent from automated trading system");
					}
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
		else if ( lastDownPos > lastUpPos )
		{	
			//debug("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

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
			if ( firstBreakOut == null )
				return null;
			
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED))
			{
				return null;
				//removeTrade();
			}

			double entryPrice = firstBreakOut.low;
			
			double triggerPrice = 99999;
			if ( data != null )
				triggerPrice = data.low;
			else if ( price != 0 )
				triggerPrice = price;
				
				
				

				
			if ( market_order == true ) 
			{
				if (triggerPrice < entryPrice ) 
				{
					double firstBreakOutLow = firstBreakOut.low;
					int firstBreakOutS = Utility.findPositionByHour(quotesS, firstBreakOut.time, Constants.BACK_TO_FRONT );
					
					for ( int i = firstBreakOutS+1; i < lastbarS; i++)
					{
						if ( quotesS[i].low < firstBreakOutLow )
						{
							info("first breakout low missed at " + quotesS[i].time);
							return null;
						}
					}

					// 3.  check diverage
					QuoteData highAfterFirstBreakOut = Utility.getHigh( quotesS, firstBreakOutS+1, lastbarS );
					if ( highAfterFirstBreakOut == null )
						return null;
					double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
					if ( diverage > 1.5 * FIXED_STOP )
					{
						info(data.time + " entry sell diverage high is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
						return null;
					}


					// run trend analyser
					String tradeType = Constants.TRADE_EUR;
					QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
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

					

					createOpenTrade(tradeType, Constants.ACTION_SELL);

					trade.setFirstBreakOutTime(firstBreakOut.time);
					trade.setFirstBreakOutStartTime(quotesL[start].time);
					trade.setTouch20MATime(quotesL[touch20MA].time);
					trade.prevUpStart = prevUpPos;

					trade.entryPrice = trade.triggerPrice = firstBreakOut.low;
					trade.POSITION_SIZE = POSITION_SIZE;
					trade.setEntryTime(quotes[lastbar].time);
					
					warning("break DOWN trade entered at " + quotes[lastbar].time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice );
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
							trade.status = Constants.STATUS_DETECTED;
							return trade;
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
		
		return null;
	}

	

	
	// use momentum
	public Trade checkFirstStrongBreakOutofMA(QuoteData data, double price )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		if ( quotesL[lastbarL].close > ema20L[lastbarL])
		{
			prevDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 1);
	
			if ( lastbarL > prevDownPos + 15 )
				return null;
			
			for ( int i = lastbarL-3; i > prevDownPos - 20; i-- )
			{
				PushHighLow phl = Pattern.findLastNHigh(quotesL, i, lastbarL, 1);
				if ( phl != null )
				{
					for ( int j = phl.prePos+1; j < lastbarL; j++)
						if ( quotesL[j].high > quotesL[phl.prePos].high )
							return null;
					
					int pushEnd = phl.prePos;
					int pushStart = pushEnd;
					while ( quotesL[pushStart-1].low < quotesL[pushStart].low)
						pushStart--;
					
					if (( quotesL[pushEnd].high - quotesL[pushStart].low)  > 1.5 * FIXED_STOP * PIP_SIZE)
					{
						if (( quotesL[phl.prePos].high - phl.pullBack.low ) < 0.8 * FIXED_STOP * PIP_SIZE )
						{
							System.out.println( symbol + " breakout BUY detected at " + quotesL[lastbarL].time + " previous tip is " + quotesL[phl.prePos].time + " " + quotesL[phl.prePos].high + " pushStart:" + quotesL[pushStart].time);
							createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_BUY);
							trade.status = Constants.STATUS_DETECTED;
							trade.entryPrice = trade.triggerPrice = quotesL[phl.prePos].high;
							
							trade.pullBackStartL = pushStart; 
							trade.detectPos = lastbarL;
							trade.setFirstBreakOutTime(quotesL[pushStart].time);
							trade.setFirstBreakOutStartTime(quotesL[pushStart].time);
							trade.setTouch20MATime(phl.pullBack.time);
							enterMarketPosition(trade.entryPrice);
							return trade;
						}
					}
				}
			}
		}
		else if ( quotesL[lastbarL].close < ema20L[lastbarL])
		{
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 1);
	
			if ( lastbarL > prevUpPos + 15 )
				return null;
			
			for ( int i = lastbarL-3; i > prevUpPos - 20; i-- )
			{
				PushHighLow phl = Pattern.findLastNLow(quotesL, i, lastbarL, 1);
				if ( phl != null )
				{
					for ( int j = phl.prePos+1; j < lastbarL; j++)
						if ( quotesL[j].low < quotesL[phl.prePos].low )
							return null;

					int pushEnd = phl.prePos;
					int pushStart = pushEnd;
					while ( quotesL[pushStart-1].high > quotesL[pushStart].high)
						pushStart--;
					
					if (( quotesL[pushStart].high - quotesL[pushEnd].low )  > 1.5 * FIXED_STOP * PIP_SIZE)
					{
						if (( phl.pullBack.high - quotesL[phl.prePos].low ) < 0.8 * FIXED_STOP * PIP_SIZE )
						{
							System.out.println( symbol + " breakout SELL detected at " + quotesL[lastbarL].time + " previous tip is " + quotesL[phl.prePos].time + " " + quotesL[phl.prePos].low + " pushStart:" + quotesL[pushStart].time );
							createOpenTrade(Constants.TRADE_PBK, Constants.ACTION_SELL);
							trade.status = Constants.STATUS_DETECTED;
							trade.entryPrice = trade.triggerPrice = quotesL[phl.prePos].low;
							
							trade.pullBackStartL = pushStart; 
							trade.detectPos = lastbarL;
							trade.setFirstBreakOutTime(quotesL[pushStart].time);
							trade.setFirstBreakOutStartTime(quotesL[pushStart].time);
							trade.setTouch20MATime(phl.pullBack.time);
							enterMarketPosition(trade.entryPrice);
							return trade;
						}
					}
				}
			}
		}
		
		return null;
	}


	
	
	
	
	
	
	
	
	
	

	
	
	public Trade checkBreakOut6(QuoteData data, double price )
	{
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);
		int lastbarS = quotesS.length -1;
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length -1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		
		int lastUpPos, lastDownPos, prevUpPos, prevDownPos;
		int start = lastbarL;
		
		labelPositions( quotesL );
		
		
		// now it is touching 20MA
		lastUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastbarL, 2);
		lastDownPos = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, lastbarL, 2);
	
		
		if ( lastUpPos > lastDownPos)
		{
			//debug("check buy");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)))
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
			if ( findTradeHistory( Constants.ACTION_BUY, quotesL[start].time) != null )
				return null;
			
			// now it is the first up

			//  here added
			//QuoteData firstBreakOut = Utility.getHigh( quotesL, start, touch20MA-1);
			//if ( firstBreakOut == null )
			//	return null;

			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)) && !trade.status.equals(Constants.STATUS_PLACED))
			{
				return null;
			}

			double triggerPrice = 0;
			if ( data != null )
				triggerPrice = data.high;
			else if ( price != 0 )
				triggerPrice = price;

			// calculate first breakOut
			int firstBreakOutL = start;
			while ( (firstBreakOutL < lastbarL) && quotesL[firstBreakOutL+1].high > quotesL[firstBreakOutL].high )
				firstBreakOutL++;
			if ( firstBreakOutL >= lastbarL )
				return null;
			firstBreakOutL = Utility.getHigh( quotesL, start, firstBreakOutL).pos;

				
			double entryPrice = quotesL[firstBreakOutL].high;

			if ( triggerPrice > entryPrice )
			{	
				// 1 check 20MA
				int touch20MA = 0;
				for ( int i = start+1 ; i <= lastbarL; i++)
				{
					if ( quotesL[i].low <= ema20L[i] )
					{
						touch20MA = i;
						fine("touch 20MA detected at " + quotesL[touch20MA].time);
						break;
					}
				}
				
				if ( touch20MA == 0 )
					return null;
	
				
				// 2 check it is the first break out on the samll chart
				double firstBreakOutHigh = quotesL[firstBreakOutL].high;
				int firstBreakOutS = Utility.findPositionByHour(quotesS, quotesL[firstBreakOutL].time, Constants.BACK_TO_FRONT );
				
				for ( int i = firstBreakOutS+1; i < lastbarS; i++)
				{
					if ( quotesS[i].high > firstBreakOutHigh )
					{
						info("first breakout high missed at " + quotesS[i].time);
						return null;
					}
				}
	
	
				// 3.  check diverage
				QuoteData lowAfterFirstBreakOut = Utility.getLow( quotesS, firstBreakOutS+1, lastbarS );
				if ( lowAfterFirstBreakOut == null )
					return null;
				double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
				if ( diverage > 1.5 * FIXED_STOP )
				{
					info(data.time + " entry buy diverage low is" + lowAfterFirstBreakOut.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
					return null;
				}
	
					
				// 4. run trend analyser
				String tradeType = Constants.TRADE_EUR;
				QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				BigTrend bt = determineBigTrend2( quotes240);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotes240));
	
				if (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					tradeType = Constants.TRADE_RVS;
					//lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
					//lastLargeMoveStart = lastLargeMovePush.pushStart;
				}
				else if (( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
				{
					tradeType = Constants.TRADE_PBK;
					if ( bt.resistance != null )
						System.out.println("Resistence is at " + bt.resistance.high);
				}
	
				
				
				
				createOpenTrade(tradeType, Constants.ACTION_BUY);
	
				trade.setFirstBreakOutTime(quotesL[firstBreakOutL].time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevDownStart = prevDownPos;
				
				trade.entryPrice = trade.triggerPrice = quotesL[firstBreakOutL].high;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.setEntryTime(quotes[lastbar].time);
	
				trade.targetPrice = adjustPrice(entryPrice + DEFAULT_PROFIT_TARGET * PIP_SIZE, Constants.ADJUST_TYPE_UP);
				warning(data.time + " break UP trade entered at " + quotes[lastbar].time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice + " diverage:" + diverage);
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
						trade.status = Constants.STATUS_DETECTED;
						return trade;
					}
				}
	
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);
	
				if (MODE == Constants.REAL_MODE)
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail(symbol + " BUY order placed", "sent from automated trading system");
				}
			}
		}
		else if ( lastDownPos > lastUpPos )
		{	
			//debug("check sell");
			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_SELL)))
				return null;
			
			int lastDownPosStart = lastDownPos;
			while ( quotesL[lastDownPosStart].high < ema20L[lastDownPosStart])
				lastDownPosStart--;
			
			prevUpPos = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, lastDownPosStart, 2);
			if ( prevUpPos == Constants.NOT_FOUND )  
				return null;
			
			while ( quotesL[prevUpPos-1].high > quotesL[prevUpPos].high )
				prevUpPos--;

			// looking for upside
			for ( start = prevUpPos+1; start < lastbarL; start++)
				if (( quotesL[start].high < ema20L[start]) && ( quotesL[start+1].high < ema20L[start+1]))
					break;
			
			if ( start == lastbarL )
				return null;

			fine("break out start detected at " + quotesL[start].time);
			if ( findTradeHistory( Constants.ACTION_SELL, quotesL[start].time) != null )
				return null;


			if (( trade != null ) && ( trade.action.equals(Constants.ACTION_BUY)) && !trade.status.equals(Constants.STATUS_PLACED))
			{
				return null;
				//removeTrade();
			}


			double triggerPrice = 99999;
			if ( data != null )
				triggerPrice = data.low;
			else if ( price != 0 )
				triggerPrice = price;
				
				
			// calculate first breakOut
			int firstBreakOutL = start;
			while ( (firstBreakOutL < lastbarL) && quotesL[firstBreakOutL+1].low < quotesL[firstBreakOutL].low )
				firstBreakOutL++;
			if ( firstBreakOutL >= lastbarL )
				return null;
			firstBreakOutL = Utility.getLow( quotesL, start, firstBreakOutL).pos;

				
			double entryPrice = quotesL[firstBreakOutL].low;

			if ( triggerPrice < entryPrice )
			{	

				// 1 check 20MA
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

				
				
				// 2 check it is the first break out on the samll chart
				double firstBreakOutLow = quotesL[firstBreakOutL].low;
				int firstBreakOutS = Utility.findPositionByHour(quotesS, quotesL[firstBreakOutL].time, Constants.BACK_TO_FRONT );
				
				for ( int i = firstBreakOutS+1; i < lastbarS; i++)
				{
					if ( quotesS[i].low < firstBreakOutLow )
					{
						info("first breakout low missed at " + quotesS[i].time);
						return null;
					}
				}

				// 3.  check diverage
				QuoteData highAfterFirstBreakOut = Utility.getHigh( quotesS, firstBreakOutS+1, lastbarS );
				if ( highAfterFirstBreakOut == null )
					return null;
				double diverage = (highAfterFirstBreakOut.high - entryPrice)/PIP_SIZE;
				if ( diverage > 1.5 * FIXED_STOP )
				{
					info(data.time + " entry sell diverage high is" + highAfterFirstBreakOut.high + " diverage is "+  + diverage + "pips,  too large, trade removed");
					return null;
				}


				// 4. run trend analyser
				String tradeType = Constants.TRADE_EUR;
				QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
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

				
				createOpenTrade(tradeType, Constants.ACTION_SELL);

				trade.setFirstBreakOutTime(quotesL[firstBreakOutL].time);
				trade.setFirstBreakOutStartTime(quotesL[start].time);
				trade.setTouch20MATime(quotesL[touch20MA].time);
				trade.prevUpStart = prevUpPos;

				trade.entryPrice = trade.triggerPrice = quotesL[firstBreakOutL].low;
				trade.POSITION_SIZE = POSITION_SIZE;
				trade.setEntryTime(quotes[lastbar].time);
				
				warning("break DOWN trade entered at " + quotes[lastbar].time + " start:" + quotesL[start].time +  " breakout tip:" + entryPrice + " diverage:" + diverage );
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
						trade.status = Constants.STATUS_DETECTED;
						return trade;
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
		Object[] quotes = getQuoteData(Constants.CHART_15_MIN);
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
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
				// check line-ups with larger trend
				String tradeType = Constants.TRADE_EUR;
				QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				int lastbar240 = quotes240.length - 1;
				BigTrend btt = calculateTrend( quotes240);
				int prevUpStart = trade.prevUpStart;
				int prevUpStart0 = Pattern.findLastPriceConsectiveBelowMA(quotesL, ema20L, prevUpStart, 2);
				if ( prevUpStart0 == Constants.NOT_FOUND )
					prevUpStart0 = 0;
				int pushStart = Utility.getHigh(quotesL, prevUpStart0, prevUpStart ).pos;
				trade.pushStart = pushStart;
				System.out.println("Entry PushStart " + quotesL[pushStart].time);
				System.out.println("Reverse Candidate:" + calculateSmoothGoingHigh( quotesL, Constants.DIRECTION_DOWN, pushStart ));

				if (Constants.ANALYSIS_SKIP == analysisPushes( Constants.ACTION_SELL, quotesL, pushStart))
				{
				//	removeTrade();   
				//	return;
				}
				
				/*
				MACD[] macd = Indicator.calculateMACD( quotes240 );
				if (( macd[lastbar240].histogram > 0 ) && ( btt.direction.equals(Constants.DIRT_UP)))
				{
					warning("entry sell does not line up with trend, trade discarded");
					removeTrade();   
					return;
				}*/

				
				/*BigTrend bt = determineBigTrend2( quotes240);
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
				}*/

				
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
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
				trade.addTradeEvent(tv);
	
				if (MODE == Constants.REAL_MODE)
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail(symbol + " SELL order placed", "sent from automated trading system");
				}
				else if ( MODE == Constants.SIGNAL_MODE )
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail("202 Detection", symbol + " SELL detected " + data.time );
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


	
	public void trackPullBackTradeBuy(QuoteData data, double price )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);

		int start = trade.getFirstBreakOutStartPosL(quotesL);
		int touch20MA = trade.getTouch20MAPosL(quotesL);
		double entryPrice = trade.entryPrice;

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
		
		
		int start15 = Utility.findPositionByHour(quotes, quotesL[start].time, Constants.FRONT_TO_BACK );
		int firstTouch = Utility.findFirstPriceHigh(quotes, start15, entryPrice);
		/*
		if ((firstTouch != Constants.NOT_FOUND))
		{
			QuoteData lowAfterFirstBreakOut15 = Utility.getLow( quotes, firstTouch+1, lastbar );
			if ( lowAfterFirstBreakOut15 != null )
			{
				double diverage = (entryPrice - lowAfterFirstBreakOut15.low)/PIP_SIZE;
				//int pushStart = start;
				//while ( quotesL[pushStart-1].low < quotesL[pushStart].low)
				//	pushStart--;
				
				//double highest = Utility.getHigh(quotesL, pushStart, touch20MA).high;
				//if (( highest - lowAfterFirstBreakOut15.low ) > 0.5 * ( highest - quotesL[pushStart].low)) 
				if ( diverage > 1.5 * FIXED_STOP )
				{
					double push = (entryPrice - quotesL[trade.prevDownStart].low)/PIP_SIZE;
					//if (diverage/push > 0.39 )
					{
					info("entry buy diverage low is" + lowAfterFirstBreakOut15.low + " diverage is "+  + diverage + "pips,  too large, trade removed");
					removeTrade();
					return;
					}
				}
			}
		}*/
		
		
		
		int firstBreakOutL = trade.getFirstBreakOutPos(quotesL);
		QuoteData lowAfterFirstBreakOut = Utility.getLow( quotesL, firstBreakOutL+1, lastbarL );
		if (( lowAfterFirstBreakOut != null ) && ((entryPrice - lowAfterFirstBreakOut.low) > 1.5 * FIXED_STOP * PIP_SIZE))
		{
			double diverage = (entryPrice - lowAfterFirstBreakOut.low)/PIP_SIZE;
			if ( diverage > 1.5 * FIXED_STOP )
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

		if ( market_order == true )
		{
			if (triggerPrice > entryPrice) 
			{
				String tradeType = Constants.TRADE_EUR;
				QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);
				int lastbar240 = quotes240.length - 1;
				
				BigTrend btt = calculateTrend( quotes240);
				if ( btt.pushList != null )
					System.out.println("Big Trend Analysis: " + btt.pushList.toString(quotes240, PIP_SIZE));
		
				int prevDownStart = trade.prevDownStart;
				int prevDownStart0 = Pattern.findLastPriceConsectiveAboveMA(quotesL, ema20L, prevDownStart, 2);
				if ( prevDownStart0 == Constants.NOT_FOUND )
					prevDownStart0 = 0;
				int pushStart = Utility.getLow(quotesL, prevDownStart0, prevDownStart ).pos;
				trade.pushStart = pushStart;
				System.out.println("Entry PushStart " + quotesL[pushStart].time);
				System.out.println("Reverse Candidate:" + calculateSmoothGoingHigh( quotesL, Constants.DIRECTION_UP, pushStart ));
				
				if (Constants.ANALYSIS_SKIP == analysisPushes( Constants.ACTION_BUY, quotesL, pushStart))
				{
					//removeTrade();   
					//return;
				}
				/*
				MACD[] macd = Indicator.calculateMACD( quotes240 );
				if (( macd[lastbar240].histogram > 0 ) && ( btt.direction.equals(Constants.DIRT_DOWN)))
				{
					warning("entry buy does not line up with trend, trade discarded");
					removeTrade();   
					return;
				}*/

				/*
				BigTrend bt = determineBigTrend2( quotes240);
				Vector<Push> pushes = bt.pushes;
				System.out.println(bt.toString(quotes240));

				if (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					tradeType = Constants.TRADE_RVS;
					//lastLargeMoveEnd = Utility.getHigh( quotesL, lastLargeMovePush.pushStart,lastLargeMovePush.pushEnd );
					//lastLargeMoveStart = lastLargeMovePush.pushStart;
				}
				else if (( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)))
				{
					tradeType = Constants.TRADE_PBK;
					if ( bt.resistance != null )
						System.out.println("Resistence is at " + bt.resistance.high);
				}*/

				trade.type = tradeType;
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
	
				enterMarketPosition(entryPrice);
				//enterLimitPosition1(entryPrice);
	
				TradeEvent tv = new TradeEvent(TradeEvent.TRADE_FILLED, quotes[lastbar].time);
				trade.addTradeEvent(tv);
				if (MODE == Constants.REAL_MODE)
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail(symbol + " BUY order placed", "sent from automated trading system");
				}
				else if ( MODE == Constants.SIGNAL_MODE )
				{
					EmailSender es = EmailSender.getInstance();
					es.sendYahooMail("202 Detection", symbol + " BUY detected " + data.time );
				}
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
	
	
	

	
	

	
	
	
	

	
	
	
	
	public void smoothEntry(QuoteData data, double limitPrice)
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
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

	
	public void reversalEntry(QuoteData data)
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);
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
			
			int pushDuration = phl.pushWidth;
			double pushSize = phl.pushSize;

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
			
			if (( trade != null ) && ( trade.status.equals(Constants.STATUS_PLACED) || ( trade.status.equals(Constants.STATUS_FILLED))))
			{
				//exit123_close_monitor(data);
				//exit123_new9c7( data );  //   this is the latest
				exit123_new9c4( data );  // this is default 
				//exit123_new9c4_adjustStopOnly( data);
				//exit123_new9c6( data );  c6 addes to close position quick if it does not move  
				//exit123_close_monitor2( data );
			}		
		}
	}

	
	
	
	public void exit123_close_monitor2( QuoteData data )
	{
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		
		QuoteData[] quotes5 = getQuoteData(Constants.CHART_5_MIN);;
		int lastbar5 = quotes5.length - 1;
		double[] ema20_5 = Indicator.calculateEMA(quotes60, 20);
		
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		double[] ema20_240 = Indicator.calculateEMA(quotes240, 20);

		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		double avgSize1 = Utility.averageBarSizeOpenClose( quotes1 );

		MATouch[] maTouches = null;

		
		int LOT_SIZE = POSITION_SIZE/2;
		int tradeEntryPosL = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes15, trade.entryTime, Constants.BACK_TO_FRONT);
		int timePassed = lastbar60 - tradeEntryPosL; 

		
		//System.out.print("x");
		
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
					//removeTrade(); temporily not remove the file
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
		BigTrend bt = TrendAnalysis.determineBigTrend2050( quotes60);
		if (  Constants.STATUS_STOPPEDOUT.equals(trade.status))	
		{
			if (( reverse_trade == true ) && (trade.type != Constants.TRADE_CNT ))
			{	
				double prevStop = trade.stop;
				String prevAction = trade.action;
				double prevEntryPrice = trade.entryPrice;
	
				if (Constants.ACTION_SELL.equals(prevAction))
				{
					//  look to reverse if it goes against me soon after entry
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
					}
				}
				else if (Constants.ACTION_BUY.equals(prevAction))
				{
					//  look to reverse if it goes against me soon after entry
					double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPosL, lastbar60).high;
					info("highest point after entry is " + highestPointAfterEntry + " entry price:" + prevEntryPrice); 
	 
					if ((( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE * 0.3 ) ||
					     (( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - prevEntryPrice) < FIXED_STOP * PIP_SIZE )))
					{
						//bt = determineBigTrend( quotesL);
						warning(" close trade with small tip");
						reverseTrade( prevStop );
						return;
					}
				}
			}
			
			// stay to see if there is further opportunity
			trade.closeTime = data.time;
			trade.status = Constants.STATUS_CLOSED;

			TradeEvent tv = new TradeEvent(TradeEvent.TRADE_STOPPEDOUT, quotes1[lastbar1].time);
			trade.addTradeEvent(tv);
			
			return;
		}

		
		int profit = 0;
		if (Constants.ACTION_SELL.equals(trade.action))
			profit = (int)((trade.entryPrice - data.close)/ PIP_SIZE);
		else if (Constants.ACTION_BUY.equals(trade.action))
			profit = (int)((data.close - trade.entryPrice )/ PIP_SIZE);;

		
		/*********************************************************************
		 *  status: detect an counter spike move
		 *********************************************************************/
		if ( lastbar1 > 10 )
		{	
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				// check if there is a big move against the trade
				double spike5S = data.close - data.open;
				if (spike5S > 8 * PIP_SIZE) 
				{
					//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_5S, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike5S/PIP_SIZE))));
					trade.addTradeEvent(tv);
					/*if (trade.type != Constants.TRADE_CNT )
						reverseTrade( data.close );
					else
						closeTrade();*/
				}
				
				// check 1 minute
				double spike1M = Math.abs( quotes1[lastbar1-1].close - quotes1[lastbar1-1].open);
				if (Utility.isUpBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 5 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
				}
				
				if (Utility.isDownBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 5 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.PROFIT_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
				}

				// check multiple minutes
				ConsectiveBars consec1 = Utility.getLastConsectiveUpBars(quotes1);
				if ( consec1 != null )
				{
					double upSize = quotes1[consec1.getEnd()].close - quotes1[consec1.getBegin()].open;
					if ( upSize > 12 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_1M, quotes1[lastbar1].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
					}
				}

				ConsectiveBars consec15 = Utility.getLastConsectiveUpBars(quotes15);
				if ( consec15 != null )
				{
					double upSize = quotes15[consec15.getEnd()].close - quotes15[consec15.getBegin()].open;
					if ( upSize > 25 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
					}
				}

				ConsectiveBars consec60 = Utility.getLastConsectiveUpBars(quotes60);
				if ( consec60 != null )
				{
					double upSize = quotes60[consec60.getEnd()].close - quotes60[consec60.getBegin()].open;
					if ( upSize > FIXED_STOP * 1.5 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_60M, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
					}
				}
				
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				double spike5S = data.open - data.close;
				if (spike5S > 8 * PIP_SIZE) 
				{
					//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_5S, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike5S/PIP_SIZE))));
					trade.addTradeEvent(tv);
					/*if (trade.type != Constants.TRADE_CNT )
						reverseTrade( data.close );
					else
						closeTrade();*/
				}

				
				// check 1 minute
				double spike1M = Math.abs( quotes1[lastbar1-1].close - quotes1[lastbar1-1].open);
				if (Utility.isDownBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 5 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
				}

				if (Utility.isUpBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 5 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.PROFIT_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
				}

				
				// check multiple minutes
				ConsectiveBars consec1 = Utility.getLastConsectiveDownBars(quotes1);
				if ( consec1 != null )
				{
					double downSize = quotes1[consec1.getBegin()].open - quotes1[consec1.getEnd()].close;
					if ( downSize > 12 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_1M, quotes1[lastbar1].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
					}
				}

				ConsectiveBars consec15 = Utility.getLastConsectiveDownBars(quotes15);
				if ( consec15 != null )
				{
					double downSize = quotes15[consec15.getBegin()].open - quotes15[consec15.getEnd()].close;
					if ( downSize > 25 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
					}
				}

				ConsectiveBars consec60 = Utility.getLastConsectiveDownBars(quotes60);
				if ( consec60 != null )
				{
					double downSize = quotes60[consec60.getBegin()].open - quotes60[consec60.getEnd()].close;
					if ( downSize > FIXED_STOP * 1.5 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_60M, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
					}
				}

			}
		}

		
		
		/*********************************************************************
		 *  EXIT if there is a contra move???
		 *********************************************************************/
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
		}
		
		
		
		
		/*********************************************************************
		 *  status: detect profit and move stop
		 *********************************************************************/
		//double profit = Math.abs( quotes60[lastbar60].close - trade.entryPrice)/ PIP_SIZE;
		double profitInPip = 0;
		if (Constants.ACTION_SELL.equals(trade.action))
			profitInPip = (trade.entryPrice - data.low)/PIP_SIZE;
		else if (Constants.ACTION_BUY.equals(trade.action))
			profitInPip = ( data.high - trade.entryPrice )/PIP_SIZE;
		
		if (( trade.reach1FixedStop == false ) && (profitInPip > FIXED_STOP ))
			trade.reach1FixedStop = true;

		int profitTimesStop = (int)(profitInPip/FIXED_STOP);
		if ((trade.reach2FixedStop == false) && (profitTimesStop >= 2 ))
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
		

		
		
		if (lastbar60 < tradeEntryPosL + 2)
		return;

		/*********************************************************************
		 *  status: detect peaks
		 *********************************************************************/
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int wave2PtL = 0;
			/*MABreakOutList molL = MABreakOutTouchSetup.findMABreakOutsDown(quotesL, ema20L, tradeEntryPosL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				wave2PtL = 1;
			}
			/*
		
			
			public static MABreakOutList findMABreakOutsDown(QuoteData[] quotes, double[] ema, int start )*/
/*
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotes60, ema20_5, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotes60, ema20_5, tradeEntryPosL, 2);
			if (( first2above < first2below ) && ( first2above > 0 ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutTouchSetup.findNextMATouchUpFromGoingDowns( quotes60, ema20_5, tradeEntryPosL, 2);
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
	*/		
			
			//int pushStart = (trade.prevUpStart > 12)? trade.prevUpStart-12:0;
			//pushStart = Utility.getHigh(quotesL, pushStart, lastbarL).pos;
			int pushStart = (lastbar60 - tradeEntryPosL > 4)? tradeEntryPosL+4: lastbar60;
			pushStart = Utility.getHigh(quotes60, tradeEntryPosL-24, pushStart).pos;

			PushList pushList = PushSetup.getDown2PushList(quotes60, pushStart, lastbar60);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].low;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				int pullback = (int)((phl.pullBack.high - quotes60[phl.prePos].low)/PIP_SIZE);
				pushList.calculatePushMACD( quotes60);
				int positive = phls[lastPushIndex].pullBackMACDPositive;

				TradeEvent tv = new TradeEvent(TradeEvent.PEAK_LOW_60, quotes60[lastbar60].time);
				tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
				trade.addTradeEvent(tv);
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
						

						if ( pullback  > 2 * FIXED_STOP )
						{
							if ( wave2PtL != 0 )
							{
								warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
						}
						else if (  pullback  > 1.5 * FIXED_STOP )
						{
							if ( positive > 0 )
							{	
								if ( wave2PtL != 0 )
								{	
									for ( int j = 0; j < phls.length; j++ )
									{
										System.out.println(quotes60[phls[j].prePos].time + " " + quotes60[phls[j].breakOutPos].time + " " + quotes60[phls[j].curPos].time); 									
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
								MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotes60, ema20_5, tradeEntryPosL, 2);
								MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchUpFromGoingDowns( quotes60, ema20_5, tradeEntryPosL, 1);
	
								double prevProfit = trade.entryPrice - quotes60[phl.prePos].low;
								double avgProfit = prevProfit / ( lastbar60 - tradeEntryPosL );
								if ( avgProfit > 0 )
								{	
									//double avgPullBack = pullback / ( lastbarL - phl.prePos);
									
									//if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
									if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
									//if ( numOfPush >= 4 )
									{
										System.out.println(data.time + " take profit on disporportional pull back");
										takeProfit( adjustPrice(quotes60[phl.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
									}
								}
								
								
								PositionToMA ptm = Pattern.countAboveMA(quotes60, ema20_5, tradeEntryPosL, lastbar60);
								float numberOfbars = lastbar60-tradeEntryPosL;
								if (ptm.below < ptm.above ) 
								{
									System.out.println(data.time + " take profit on disporportional pull back2");
									takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
	
								if ( lastbar60 >= tradeEntryPos + 8 )
								{	
									float numAbove = 0;
									for ( int j = tradeEntryPosL+1; j <=lastbar60; j++)
									{	
										if ( quotes60[j].low > trade.entryPrice )
											numAbove += 1;
									}
								
									if ( numAbove/numberOfbars > 0.6 )
									{
										System.out.println(data.time + " take profit on disporportional pull back 3");
										takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
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
						if ( quotes60[j+1].high > quotes15[j].high)
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
				}
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{

			if (lastbar60 < tradeEntryPosL + 2)
				return;
			

			// calculate touch 20MA point
			int wave2PtL = 0;
			/*MABreakOutList molL = MABreakOutTouchSetup.findMABreakOutsUp(quotesL, ema20L, tradeEntryPosL );
			if (( molL != null ) && (( molL.getNumOfBreakOuts() > 1 ) || (( molL.getNumOfBreakOuts() == 1 ) && ( molL.getLastMBBreakOut().end > 0 ))))
			{		
				wave2PtL = 1;
			}*/

		/*	
			int first2above = Pattern.findNextPriceConsectiveAboveMA(quotes60, ema20_5, tradeEntryPosL, 2);
			int first2below = Pattern.findNextPriceConsectiveBelowMA(quotes60, ema20_5, tradeEntryPosL, 2);
			if (( first2below > 0 ) && ( first2below < first2above ))
			{
				wave2PtL = -1;
			}
			else
			{	
				maTouches = MABreakOutTouchSetup.findNextMATouchDownsFromGoingUps( quotes60, ema20_5, tradeEntryPosL, 2);
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
*/
			
			//int pushStart = (trade.prevDownStart > 12)? trade.prevDownStart-12:0;
			//pushStart = Utility.getLow(quotesL, pushStart, lastbarL).pos;
			int pushStart = (lastbar60 - tradeEntryPosL > 4)? tradeEntryPosL+4: lastbar60;
			pushStart = Utility.getLow(quotes60, tradeEntryPosL-24, pushStart).pos;
			
			PushList pushList = PushSetup.getUp2PushList(quotes60, pushStart, lastbar60);

			//PushList pushList = Pattern.findPast2Lows(quotesL, pushStart, lastbarL);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				double triggerPrice = quotes60[lastPush.prePos].high;
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				int largePushIndex = 0;
				PushHighLow phl = lastPush;
				int pullback = (int)((quotes60[phl.prePos].high - phl.pullBack.low)/PIP_SIZE);
				pushList.calculatePushMACD( quotes60);
				int negatives = phls[lastPushIndex].pullBackMACDNegative;

				TradeEvent tv = new TradeEvent(TradeEvent.PEAK_HIGH_60, quotes60[lastbar60].time);
				tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
				trade.addTradeEvent(tv);
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
						
	
						if ( pullback  > 2 * FIXED_STOP )
						{
							if ( wave2PtL != 0 )
							{
								warning(data.time + " take profit at " + triggerPrice + " on 2.0 after returned 20MA");
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
						}
						else if (  pullback  > 1.5 * FIXED_STOP )
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
								MATouch[] maTouches2 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotes60, ema20_5, tradeEntryPosL, 2);
								MATouch[] maTouches1 = MABreakOutAndTouch.findNextMATouchDownsFromGoingUps( quotes60, ema20_5, tradeEntryPosL, 1);
								// Exit Scenario 2:  disporportional pullback
								double prevProfit = quotes60[phl.prePos].high - trade.entryPrice;
								double avgProfit = prevProfit / ( lastbar60 - tradeEntryPosL );
								if ( avgProfit > 0 )
								{	
									//double avgPullBack = pullback / ( lastbarL - phl.prePos);
									//System.out.println(data.time + " exit detected average profit:" + avgProfit + " pullback avg:" + avgPullBack + " " + avgPullBack/avgProfit);
			
									//if (( pullback > 0.7 * FIXED_STOP * PIP_SIZE ) && ( avgPullBack > 10 * avgProfit ))
									if (( maTouches2.length >=4 ) || maTouches1.length >= 6 )
									//if ( numOfPush >= 4 )
									{
										System.out.println(data.time + " take profit on disporportional pull back");
										takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
									}
								}
								
								
								PositionToMA ptm = Pattern.countAboveMA(quotes60, ema20_5, tradeEntryPosL, lastbar60);
								float numberOfbars = lastbar60-tradeEntryPosL;
								if (ptm.below > ptm.above ) 
								{
									System.out.println(data.time + " take profit on disporportional pull back 2");
									takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
								}
	
							
								if ( lastbar60 >= tradeEntryPos + 8 )
								{	
									float numBelow = 0;
									for ( int j = tradeEntryPosL+1; j <=lastbar60; j++)
									{	
										if ( quotes60[j].high < trade.entryPrice )
											numBelow += 1;
									}
								
									if ( numBelow/numberOfbars > 0.6 )
									{
										System.out.println(data.time + " take profit on disporportional pull back 3");
										takeProfit( adjustPrice(quotes60[phl.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
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
						//System.out.println(symbol + " " + CHART + " " + quotes[lastbar].time + " move stop to " + stop );
						cancelOrder(trade.stopId);
						trade.stop = stop;
						trade.stopId = placeStopOrder(Constants.ACTION_SELL, stop, trade.remainingPositionSize, null);
						//trade.lastStopAdjustTime = data.time;
						warning(" stop moved to " + trade.stop + " orderId:" + trade.stopId );
					}
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
							takeProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profitInPip > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
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
						takeProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
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
							takeProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( profitInPip > 200 )  
							{
								warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
								takeProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
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
						takeProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}

		}
	
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
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
		
		MABreakOutList mol240 = MABreakOutAndTouch.getMABreakOuts(quotes240, ema20_240 );
		if ( mol240 != null )
		{
			//System.out.print(mol240.toString(quotes240));
		}
		
		
		if (trade.reach1FixedStop == false)
			exit123_new9_checkReversalsOrEalyExit( data );

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
			
			
			//int pushStart = (trade.prevUpStart > 12)? trade.prevUpStart-12:0;
			//pushStart = Utility.getHigh(quotesL, pushStart, lastbarL).pos;
			int pushStart = (lastbarL - tradeEntryPosL > 4)? tradeEntryPosL+4: lastbarL;
			pushStart = Utility.getHigh(quotesL, tradeEntryPosL-12, pushStart).pos;
			
			
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

			
			//int pushStart = (trade.prevDownStart > 12)? trade.prevDownStart-12:0;
			//pushStart = Utility.getLow(quotesL, pushStart, lastbarL).pos;

			int pushStart = (lastbarL - tradeEntryPosL > 4)? tradeEntryPosL+4: lastbarL;
			pushStart = Utility.getLow(quotesL, tradeEntryPosL-12, pushStart).pos;

			
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
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	public void exit123_new9c7( QuoteData data )
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
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}

		if ( trade.potentialExitSize != 0 )
			exit123_new9_potential_exit( data );
		
		
		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Iteger(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if (trade.reach1FixedStop == false)
			{
				if (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE )
				{
					trade.reach1FixedStop = true;
				}
				else
				{
					exit123_new9_checkReversalsOrEalyExit( data );
					return;
				}
			}		

			if (trade.reach2FixedStop == false)
			{
				if (( trade.entryPrice - data.low ) > 2 * FIXED_STOP * PIP_SIZE )
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
					trade.reach2FixedStop = true;
				}
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
								//takeProfit( adjustPrice(triggerPrice - 30 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize/2 );
								trade.createProgramTrailingRange( triggerPrice, triggerPrice - 3 * FIXED_STOP * PIP_SIZE, 1.5*FIXED_STOP * PIP_SIZE );

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
			if (trade.reach1FixedStop == false)
			{
				if (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE )
				{
					trade.reach1FixedStop = true;
				}
				else
				{
					exit123_new9_checkReversalsOrEalyExit( data );
					return;
				}
			}		

			if (trade.reach2FixedStop == false)
				if (( data.high - trade.entryPrice ) > 2 * FIXED_STOP * PIP_SIZE )
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach2FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
								//takeProfit( adjustPrice(triggerPrice + 30 * PIP_SIZE, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize/2 );
								trade.createProgramTrailingRange( triggerPrice, triggerPrice + 3 * FIXED_STOP * PIP_SIZE, 1.5*FIXED_STOP * PIP_SIZE );
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
	
	
	
	
	public void exit123_new9_checkReversalsOrEalyExit( QuoteData data/*, MABreakOutList mol*/ )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotesL, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes, trade.entryTime, Constants.BACK_TO_FRONT);

		BigTrend bt = determineBigTrend( quotesL);
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
					logger.warning(symbol + " " + CHART + " close trade with small tip at " + data.high);
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

	
	/**
	 * 
	 * here we wait to make sure it is not a break out
	 * @param data
	 */
	public void exit123_new9_potential_exit( QuoteData data )
	{
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		
		int potentialExitPosL = Utility.findPositionByHour(quotesL, trade.potentialExitTime, 2 );

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			if ( trade.potentialPriceAdvance < ( trade.potentialExitPrice - data .low ))
				trade.potentialPriceAdvance = trade.potentialExitPrice - data .low;
		}
		else if (Constants.ACTION_BUY.equals(trade.action))
		{
			if ( trade.potentialPriceAdvance < ( data.high - trade.potentialExitPrice ))
				trade.potentialPriceAdvance =  data.high - trade.potentialExitPrice ;
		}
		
		if ( lastbarL == potentialExitPosL + 1 )
		{
			if ( trade.potentialPriceAdvance > FIXED_STOP / 2 * PIP_SIZE )
				trade.reSetPotentialExit();
			else
			{
				if (trade.targetPlaced1 == true)  // order already placed earlier
					return;
				
				String action = Constants.ACTION_BUY;
				if (trade.action.equals(Constants.ACTION_BUY))
					action = Constants.ACTION_SELL;
				
				trade.targetPrice1 = data.close;
				trade.targetPos1 = trade.potentialExitSize;//trade.remainingPositionSize;
				trade.targetId1 = placeLmtOrder(action, trade.targetPrice1, trade.targetPos1, null);
				warning("take profit remainning profit " + action + " target order placed@ " + trade.targetPrice1 + " " + trade.targetPos1 );

				trade.targetPlaced1 = true;
				return;
				
			}
		}
		

	}
	
	
	
	
	
	
	
	

	
	
	public void exit123_new9c4_123( QuoteData data )
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
			if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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
			if (trade.reach1FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
			if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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


			// check break_123
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
			}
			
			
			

			
			
			
			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.reach1FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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
					
					if ( break123 == true)
					{
						warning(data.time + " take profit at " + triggerPrice + " after breaking 123");
						takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
					}
					else if ( pullback  > 2 * FIXED_STOP * PIP_SIZE)
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);;
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotesL, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;

		
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
			if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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
			if (trade.reach1FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true )
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
			if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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
			
			if (trade.reach1FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Iteger(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
		/*	if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
				return;
			}*/

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

				
				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, trade.entryPrice - FIXED_STOP * PIP_SIZE);
				//trade.averageUp = true;
				
			
			}
			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			// gathering parameters
			if (trade.reach1FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
		/*	if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
				return;
			}*/

			
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

			
			if ((average_up == true ) && (trade.averageUp == false ) && (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].high > trade.entryPrice + FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice + FIXED_STOP * PIP_SIZE, LOT_SIZE );

				trade.averageUp = true;
				//trade.averageUp = true;

				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, POSITION_SIZE, trade.entryPrice + FIXED_STOP * PIP_SIZE);
			}

			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.reach1FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
	

	
	
	public void exit123_new9c4_adjustStopOnly( QuoteData data )
	{
		QuoteData[] quotes1 = getQuoteData(Constants.CHART_1_MIN);
		int lastbar1 = quotes1.length - 1;
		double avgSize1 = Utility.averageBarSizeOpenClose( quotes1 );
		QuoteData[] quotes15 = getQuoteData(Constants.CHART_15_MIN);
		int lastbar15 = quotes15.length - 1;
		QuoteData[] quotes60 = getQuoteData(Constants.CHART_60_MIN);;
		int lastbar60 = quotes60.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);;
		int lastbarS = quotesS.length - 1;
		double[] ema20L = Indicator.calculateEMA(quotes60, 20);
		MATouch[] maTouches = null;
		QuoteData[] quotes240 = getQuoteData(Constants.CHART_240_MIN);;
		
		int LOT_SIZE = POSITION_SIZE/2;
		
		int tradeEntryPosL = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos = Utility.findPositionByMinute( quotes15, trade.entryTime, Constants.BACK_TO_FRONT);

		
		if ((trade == null) || (tradeEntryPosL == Constants.NOT_FOUND))
		{
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotes60[lastbar60].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbar60 - tradeEntryPosL; 
		//int timeCurrent = new Iteger(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

		
		
		
		if ( lastbar1 > 10 )
		{	
			if (Constants.ACTION_SELL.equals(trade.action))
			{
				// check if there is a big move against the trade
				double spike5S = data.close - data.open;
				if (spike5S > 8 * PIP_SIZE) 
				{
					//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_5S, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike5S/PIP_SIZE))));
					System.out.println("here");
					trade.addTradeEvent(tv);
					/*if (trade.type != Constants.TRADE_CNT )
						reverseTrade( data.close );
					else
						closeTrade();*/
				}
				
				// check 1 minute
				double spike1M = Math.abs( quotes1[lastbar1-1].close - quotes1[lastbar1-1].open);
				if (Utility.isUpBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 4 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
					trade.addTradeEvent(tv);
				}
				
				if (Utility.isDownBar(quotes1[lastbar1-1]) && ( spike1M > 8 * PIP_SIZE ) && ( spike1M > 4 * avgSize1 ))
				{
					TradeEvent tv = new TradeEvent(TradeEvent.PROFIT_MOVE_1M, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike1M/PIP_SIZE))));
					trade.addTradeEvent(tv);
				}

				// check multiple minutes
				ConsectiveBars consec1 = Utility.getLastConsectiveUpBars(quotes1);
				if ( consec1 != null )
				{
					double upSize = quotes1[consec1.getEnd()].close - quotes1[consec1.getBegin()].open;
					if ( upSize > 12 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_1M, quotes1[lastbar1].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}

				ConsectiveBars consec15 = Utility.getLastConsectiveUpBars(quotes15);
				if ( consec15 != null )
				{
					double upSize = quotes15[consec15.getEnd()].close - quotes15[consec15.getBegin()].open;
					if ( upSize > 25 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}

				ConsectiveBars consec60 = Utility.getLastConsectiveUpBars(quotes60);
				if ( consec60 != null )
				{
					double upSize = quotes60[consec60.getEnd()].close - quotes60[consec60.getBegin()].open;
					if ( upSize > FIXED_STOP * 1.5 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_60M, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(upSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}
				
			}
			else if (Constants.ACTION_BUY.equals(trade.action))
			{
				double spike5S = data.open - data.close;
				if (spike5S > 8 * PIP_SIZE) 
				{
					//System.out.println("spike UP detected at " + quotes1[lastbar1].time + " " + (quotes1[lastbar1].close - quotes1[lastbar1].open)/PIP_SIZE);
					TradeEvent tv = new TradeEvent(TradeEvent.SPIKE_CONTRA_MOVE_5S, quotes1[lastbar1].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(spike5S/PIP_SIZE))));
					trade.addTradeEvent(tv);
					/*if (trade.type != Constants.TRADE_CNT )
						reverseTrade( data.close );
					else
						closeTrade();*/
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
				ConsectiveBars consec1 = Utility.getLastConsectiveDownBars(quotes1);
				if ( consec1 != null )
				{
					double downSize = quotes1[consec1.getBegin()].open - quotes1[consec1.getEnd()].close;
					if ( downSize > 12 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_1M, quotes1[lastbar1].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}

				ConsectiveBars consec15 = Utility.getLastConsectiveDownBars(quotes15);
				if ( consec15 != null )
				{
					double downSize = quotes15[consec15.getBegin()].open - quotes15[consec15.getEnd()].close;
					if ( downSize > 25 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_15M, quotes15[lastbar15].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}

				ConsectiveBars consec60 = Utility.getLastConsectiveDownBars(quotes60);
				if ( consec60 != null )
				{
					double downSize = quotes60[consec60.getBegin()].open - quotes60[consec60.getEnd()].close;
					if ( downSize > FIXED_STOP * 1.5 * PIP_SIZE )
					{
						TradeEvent tv = new TradeEvent(TradeEvent.CONSEC_CONTRA_MOVE_60M, quotes60[lastbar60].time);
						tv.addNameValue(new NameValue(TradeEvent.NAME_MOVE_SIZE, new Integer((int)(downSize/PIP_SIZE))));
						trade.addTradeEvent(tv);
					}
				}
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
		

		
		
		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
		/*	if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
				return;
			}*/

			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotes60, tradeEntryPosL, lastbar60).low;
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

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotes60);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotes60);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotes60,firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotes60,firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					// reverse;
					AddCloseRecord(quotes15[lastbar15].time, Constants.ACTION_BUY, trade.remainingPositionSize, reversePrice);
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
						trade.detectTime = quotes15[lastbar15].time;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes15[lastbar15]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}
					return;
				}
			}			
			
			
			// average up
			if ((average_up == true ) && (trade.averageUp == false ) && (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar15; i++)
					if ( quotes15[i].low < trade.entryPrice - FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice - FIXED_STOP * PIP_SIZE, LOT_SIZE );
				
				trade.averageUp = true;

				
				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, trade.entryPrice - FIXED_STOP * PIP_SIZE);
				//trade.averageUp = true;
				
			
			}
			
			
			if (lastbar60 < tradeEntryPosL + 2)
			return;

			// gathering parameters
			if (trade.reach1FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes15[lastbar15].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes15[lastbar15].time + " break even size is " + FIXED_STOP);
				}
			}


			
			
			int pushStart = tradeEntryPosL-1;
			PushHighLow[] phls = Pattern.findPast2Lows(quotes60, pushStart, lastbar60).phls;
			PushHighLow phl = null;

			
			// move stop
			if (trade.reach1FixedStop == true)
			{	
				if (phl != null)
				{
					// count the pull bacck bars
					int pullbackcount = 0;
					for ( int j = phl.prePos+1; j < phl.curPos; j++)
						if ( quotes60[j+1].high > quotes15[j].high)
							pullbackcount++;
					
					//System.out.println("pullback count=" + pullbackcount);
					//if ( pullbackcount >= 2 )
					{
						double stop = adjustPrice(phl.pullBack.high, Constants.ADJUST_TYPE_UP);
						if ( stop < trade.stop )
						{
							System.out.println(symbol + " " + CHART + " " + quotes15[lastbar15].time + " move stop to " + stop );
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
		/*	if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
				return;
			}*/

			
			//  look to reverse if it goes against me soon after entry
			double highestPointAfterEntry = Utility.getHigh(quotes60, tradeEntryPosL, lastbar60).high;
			if (!trade.type.equals(Constants.TRADE_CNT) && ((( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE *0.3 ))            )/*      || 
				(( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)) && (( highestPointAfterEntry - trade.entryPrice) < FIXED_STOP * PIP_SIZE ))*/
			{
				if (( data.low <  (highestPointAfterEntry - FIXED_STOP * PIP_SIZE ))
					&& ( Constants.DIRT_DOWN.equals(bt.direction) || Constants.DIRT_DOWN_REVERSAL.equals(bt.direction) || Constants.DIRT_DOWN_SEC_2.equals(bt.direction)))
				{
					// reverse;
					System.out.println(bt.toString(quotes60));
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					double reversePrice = highestPointAfterEntry -  FIXED_STOP * PIP_SIZE;
					
					boolean reversequalified = true;
					if (mainProgram.isNoReverse(symbol ))
					{
						warning("no reverse symbol found, do not reverse");
						reversequalified = false;
					}

					
					int touch20MAPosL = trade.getTouch20MAPosL(quotes60);
					int firstBreakOutStartL = trade.getFirstBreakOutStartPosL(quotes60);
					if ( (touch20MAPosL - firstBreakOutStartL) > 5)
					{
						double high = Utility.getHigh(quotes60, firstBreakOutStartL, touch20MAPosL-1).high;
						double low = Utility.getLow(quotes60, firstBreakOutStartL, touch20MAPosL-1).low;
						if (Math.abs(high-low) > 2 * PIP_SIZE * FIXED_STOP)
							reversequalified = false;
					}

					AddCloseRecord(quotes15[lastbar15].time, Constants.ACTION_SELL, trade.remainingPositionSize, reversePrice);
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
						trade.detectTime = quotes15[lastbar15].time;
						trade.POSITION_SIZE = POSITION_SIZE;
						trade.entryPrice = reversePrice;
						trade.entryTime = ((QuoteData) quotes15[lastbar15]).time;
	
						enterMarketPosition(reversePrice, prevPosionSize);
					}
					return;
				}
			}

			
			if ((average_up == true ) && (trade.averageUp == false ) && (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar15; i++)
					if ( quotes15[i].high > trade.entryPrice + FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice + FIXED_STOP * PIP_SIZE, LOT_SIZE );

				trade.averageUp = true;
				//trade.averageUp = true;

				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, POSITION_SIZE, trade.entryPrice + FIXED_STOP * PIP_SIZE);
			}

			if (lastbar60 < tradeEntryPosL + 2)
				return;
			
			if (trade.reach1FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes15[lastbar15].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes15[lastbar15].time + " break even size is " + FIXED_STOP);
				}
			}
			

			
			int pushStart = tradeEntryPosL-1;
			PushHighLow[] phls = Pattern.findPast2Highs(quotes60, pushStart, lastbar60).phls;
			PushHighLow phl = null;

			// move stop
			if (trade.reach1FixedStop == true)
			{	
				if (phl != null)
				{
					double stop = adjustPrice(phl.pullBack.low, Constants.ADJUST_TYPE_DOWN);
					if ( stop > trade.stop )
					{
						System.out.println(symbol + " " + CHART + " " + quotes15[lastbar15].time + " move stop to " + stop );
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


	
	
	
	
	public void exit123_new9c6( QuoteData data )
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
			logger.severe(symbol + " " + CHART + " can not find trade or trade entry point!");
			return;
		}


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Iteger(data.time.substring(9,12).trim()); 

		//BigTrend bt = determineBigTrend( quotesL);
		BigTrend bt = determineBigTrend2( quotes240);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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
			
			
			// average up
			if ((average_up == true ) && (trade.averageUp == false ) && (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].low < trade.entryPrice - FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice - FIXED_STOP * PIP_SIZE, POSITION_SIZE );
				
				trade.averageUp = true;

				
				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_SELL, POSITION_SIZE, trade.entryPrice - FIXED_STOP * PIP_SIZE);
				//trade.averageUp = true;
				
			
			}
			
			
			if (lastbarL < tradeEntryPosL + 2)
			return;

			// gathering parameters
			if (trade.reach1FixedStop == false)
			{
				if/* (((( Constants.DIRT_UP.equals(bt.direction) ||  Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
					&& ((trade.entryPrice - quotes[lastbar].low) >  FIXED_STOP * PIP_SIZE))
					||*/ ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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
				//System.out.println("exit detected at" + data.time);
				//if (data.time.equals("20120619  21:44:00"))
					//System.out.println("here");
				
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
						if ( positive > 0 ) 						{
							if ((timePassed > 12 ) && ( profit < FIXED_STOP / 2 ))
							{	
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
							}
							else if (( wave2PtL != 0 ) && (!takeProfit2_set()))
							{	
								double targetPrice = adjustPrice(triggerPrice - 10 * PIP_SIZE, Constants.ADJUST_TYPE_DOWN);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift buy on MACD with pullback < 1.5");
							}
						}

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
			if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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

			
			if ((average_up == true ) && (trade.averageUp == false ) && (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE ))
			{
				boolean missed = false;
				for ( int i = tradeEntryPos+1; i < lastbar; i++)
					if ( quotes[i].high > trade.entryPrice + FIXED_STOP * PIP_SIZE)
						missed = true;
				
				if (!missed )
					enterMarketPositionAdditional(trade.entryPrice + FIXED_STOP * PIP_SIZE, POSITION_SIZE );

				trade.averageUp = true;
				//trade.averageUp = true;

				//trade.remainingPositionSize += POSITION_SIZE;
				//trade.positionSize += POSITION_SIZE;
				//AddOpenRecord(quotes[lastbar].time, Constants.ACTION_BUY, POSITION_SIZE, trade.entryPrice + FIXED_STOP * PIP_SIZE);
			}

			if (lastbarL < tradeEntryPosL + 2)
				return;
			
			if (trade.reach1FixedStop == false)
			{
				if /*((( Constants.DIRT_DOWN.equals(bt.direction) ||  Constants.DIRT_DOWN_SEC_2.equals(bt.direction))
					&& ((quotes[lastbar].high - trade.entryPrice) >= FIXED_STOP * PIP_SIZE))
				    ||*/  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
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
							if ((timePassed > 12 ) && ( profit < FIXED_STOP / 2 ))
							{	
								takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
							}
							else if (( wave2PtL != 0 ) && (!takeProfit2_set()))
							{	
								double targetPrice = adjustPrice(triggerPrice + 10 * PIP_SIZE, Constants.ADJUST_TYPE_UP);
								takeProfit2( targetPrice, POSITION_SIZE/2 );
								warning(data.time + " take half prift sell on MACD with pullback < 1.5");
							}
						}

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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

	

	
	
	public void exit123_new9c3( QuoteData data )
	{
		QuoteData[] quotes = getQuoteData(Constants.CHART_15_MIN);
		int lastbar = quotes.length - 1;
		QuoteData[] quotesL = getQuoteData(Constants.CHART_60_MIN);
		int lastbarL = quotesL.length - 1;
		QuoteData[] quotesS = getQuoteData(Constants.CHART_5_MIN);
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


		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		double profitInRisk = 0;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		BigTrend bt = determineBigTrend( quotesL);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			profitInRisk =  (trade.stop - data.close)/PIP_SIZE;
			if (( trade.getProgramStop() != 0 ) && ((trade.getProgramStop() - data.close)/PIP_SIZE < profitInRisk ))
				profitInRisk = (trade.getProgramStop() - data.close)/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.high > trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_BUY, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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
			if (trade.reach1FixedStop == false)
			{
				if  ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
			
			
			int pushStart = tradeEntryPosL;
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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
			if (( trade.getProgramStop() != 0 ) && (( data.close )/PIP_SIZE < profitInRisk ))
				profitInRisk = ( data.close - trade.getProgramStop() )/PIP_SIZE;

			if  (( trade.getProgramStop() != 0 ) && ( data.low < trade.getProgramStop()))
			{
				warning(data.time + " program stop tiggered, exit trade");
				AddCloseRecord(data.time, Constants.ACTION_SELL, trade.remainingPositionSize, trade.getProgramStop());
				closePositionByMarket( trade.remainingPositionSize, trade.getProgramStop());
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
			
			if (trade.reach1FixedStop == false)
			{
				if  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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

							
						if ( ( wave2PtL != 0 ) && (trade.reach1FixedStop == false) && ( timePassed >= 24 ))
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
			if (trade.reach1FixedStop == true)
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

		//if (lastbarL <= tradeEntryPosL )
			//return;

		double profit = Math.abs( quotesL[lastbarL].close - trade.entryPrice)/ PIP_SIZE;
		int timePassed = lastbarL - tradeEntryPosL; 
		//int timeCurrent = new Integer(data.time.substring(9,12).trim()); 

		BigTrend bt = determineBigTrend( quotesL);

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			//  look to reverse if it goes against me soon after entry
			double lowestPointAfterEntry = Utility.getLow(quotesL, tradeEntryPosL, lastbarL).low;
			if ( !trade.type.equals(Constants.TRADE_CNT) && ((( trade.entryPrice - lowestPointAfterEntry) < FIXED_STOP * PIP_SIZE * 0.3 )))     
			{
				if (( data.high > (lowestPointAfterEntry + FIXED_STOP * PIP_SIZE )) 
					&& ( Constants.DIRT_UP.equals(bt.direction) || Constants.DIRT_UP_REVERSAL.equals(bt.direction) || Constants.DIRT_UP_SEC_2.equals(bt.direction)) )
				{
					System.out.println(bt.toString(quotesL));
					logger.warning(symbol + " " + CHART + " close trade with small tip");
					//bt = determineBigTrend( quotesL);
					double reversePrice = lowestPointAfterEntry +  FIXED_STOP * PIP_SIZE;
					boolean reversequalified = true;
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
			if (trade.reach1FixedStop == false)
			{
				if  ((trade.entryPrice - quotes[lastbar].low) > 2 * FIXED_STOP * PIP_SIZE) 	
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_DOWN);

					trade.stopId = placeStopOrder(Constants.ACTION_BUY, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
			if ( !trade.type.equals(Constants.TRADE_CNT))
			{
				if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
				{
					wave2PtL =  maTouches[0].touchBegin;
					//info("Wave2 touch point is " + quotesL[wave2PtL].time);
				}
			}
			else 
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
			if (trade.reach1FixedStop == true)
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
			
			if (trade.reach1FixedStop == false)
			{
				if  ((quotes[lastbar].high - trade.entryPrice) >= 2 * FIXED_STOP * PIP_SIZE)
				{
					cancelOrder(trade.stopId);
					trade.stop = adjustPrice(trade.entryPrice, Constants.ADJUST_TYPE_UP);
					trade.stopId = placeStopOrder(Constants.ACTION_SELL, trade.stop, trade.remainingPositionSize, null);
					trade.reach1FixedStop = true;
					logger.warning(symbol + " " + CHART + " " + " move stop to break even " + quotes[lastbar].time + " break even size is " + FIXED_STOP);
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
			if ( !trade.type.equals(Constants.TRADE_CNT))
			{
				if (( maTouches.length > 0 ) && ( maTouches[0].touchBegin != 0 ))
				{
					wave2PtL =  maTouches[0].touchBegin;
//					info("Wave2 touch point is " + quotesL[wave2PtL].time);
				}
			}
			else
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
			if (trade.reach1FixedStop == true)
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
				cancelOrder(trade.targetId);

			trade.targetPrice = price;
			trade.targetPos = (int)quantity;
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
		
		if (!(( trade != null ) && Constants.STATUS_PLACED.equals(trade.status)))
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

	
	public void createLimit2( String action, double quantity, double price, double quantity2, double price2 )
	{
		
		if (!(( trade != null ) && Constants.STATUS_PLACED.equals(trade.status)))
		{
			createOpenTrade("MANUAL", action);
			trade.status = Constants.STATUS_DETECTED;
			
			enterLimitPosition1(price, (int)(POSITION_SIZE*quantity), 0); 
			enterLimitPosition2(price2, (int)(POSITION_SIZE*quantity2), 0); 
		}
		else
		{
			System.out.println(symbol + " can not create limit2, trade exists");
			System.out.println(symbol + " can not create limit2, trade exists");
			System.out.println(symbol + " can not create limit2, trade exists");
		}
	}
	
	public void addLimitPosition( double quantity, double price )
	{
		if ( trade == null )
		{
			System.out.println(symbol + " add position, trade does not exist");
			System.out.println(symbol + " add position, trade does not exist");
			System.out.println(symbol + " add position, trade does not exist");
		}
		else
		{	
			enterLimitPosition1(price, (int)(POSITION_SIZE*quantity), 0);
			trade.status = Constants.STATUS_PLACED; // it was orignally placed
		}
	}

	public void setNoAddition( )
	{
		if ( trade == null )
		{
			System.out.println(symbol + " add position, trade does not exist");
			System.out.println(symbol + " add position, trade does not exist");
			System.out.println(symbol + " add position, trade does not exist");
		}
		else
			trade.averageUp = true;
	}

	
	
	
	int analysisPushes( String action, QuoteData[] quotes, int start )
	{
		int lastbar = quotes.length-1;
		
		if (Constants.ACTION_SELL.equals(action))
		{
			PushList pushList = PushSetup.getDown2PushList(quotes, start, lastbar);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				//System.out.println(pushList.toString(quotesL, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				
				for ( int i = 1; i < numOfPush; i++ )
				{
					if ( phls[i].breakOutSize > FIXED_STOP * PIP_SIZE * 0.8 ) 
					{
						if ((i+1 < numOfPush ) && ( phls[i+1].breakOutSize < FIXED_STOP * PIP_SIZE * 0.25 ))
							return Constants.ANALYSIS_SKIP;
					}
				}
			}
				/*
				double lastBreakOut1, lastBreakOut2;
				double lastPullBack1, lastPullBack2;
				
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
						
				// calculate touch20MA
				//pushList.calculatePushTouchMA( quotesL, ema20L);
				//wave2PtL = pushList.getTouch20MANum(); 

				pushList.calculatePushMACD( quotesL);
				int positive = phls[lastPushIndex].pullBackMACDPositive;
				
				PushHighLow phl = lastPush;
				double pullback = phl.pullBack.high - quotesL[phl.prePos].low;
				
				
				if ( pushList.end == lastbarL)
				{
					if (!exitTradePlaced())
					{
						if ( numOfPush >= 2 )
						{
							if (LESS_VALUE( lastBreakOut1, 6 * PIP_SIZE ) &&  LESS_VALUE( lastBreakOut2, 6 * PIP_SIZE ))
							{
								closeReverseTrade( true, triggerPrice, POSITION_SIZE );
							//takeProfit( adjustPrice(triggerPrice, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
								return;
							}
							*/
		}
		else if (Constants.ACTION_BUY.equals(action))
		{
			PushList pushList = PushSetup.getUp2PushList(quotes, start, lastbar);
			if ((pushList != null ) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				System.out.println(pushList.toString(quotes, PIP_SIZE));
				PushHighLow[] phls = pushList.phls;
				int lastPushIndex = phls.length - 1;
				PushHighLow lastPush = phls[phls.length - 1]; 
				int numOfPush = phls.length;
				
				for ( int i = 1; i < numOfPush; i++ )
				{
					if ( phls[i].breakOutSize > FIXED_STOP * PIP_SIZE * 0.8 ) 
					{
						if ((i+1 < numOfPush ) && ( phls[i+1].breakOutSize < FIXED_STOP * PIP_SIZE * 0.25 ))
							return Constants.ANALYSIS_SKIP;
					}
				}
			}
		}
		
		
		return 0;
	}
	
}


