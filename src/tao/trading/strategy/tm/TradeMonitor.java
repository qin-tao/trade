package tao.trading.strategy.tm;

import java.util.Date;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.MATouch;
import tao.trading.Pattern;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradeEvent;
import tao.trading.dao.ConsectiveBars;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.NameValue;
import tao.trading.strategy.util.TimeUtil;
import tao.trading.strategy.util.Utility;
import tao.trading.trend.analysis.BigTrend;
import tao.trading.trend.analysis.TrendAnalysis;

public class TradeMonitor 
{
	TradeManagerInf tradeManager;
	Trade trade;
	double PIP_SIZE;
	int FIXED_STOP;
	Logger logger;

	public TradeMonitor(TradeManagerInf tmi) {
		super();
		this.tradeManager = tmi;
		PIP_SIZE = tradeManager.getPIP_SIZE();
		FIXED_STOP = tradeManager.getFIXED_STOP();
		logger = Logger.getLogger(tradeManager.getSymbol());
	}

	public QuoteData[] getQuoteData(int CHART)
	{
		return tradeManager.getQuoteData(CHART);
	}
	
	public void monitor_exit( QuoteData data )
	{
		trade = tradeManager.getTrade();
		
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
		
		/*int tradeEntryPos15 = Utility.findPositionByHour(quotes15, trade.entryTime, 2 );
		int tradeEntryPos60 = Utility.findPositionByHour(quotes60, trade.entryTime, 2 );
		int tradeEntryPos240 = TimeUtil.get240Pos(trade.entryTime, quotes240);*/
		//int timePassed = lastbar60 - tradeEntryPos60; 
		
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
			
				Date closeTime = Constants.IBDataFormatter.parse(trade.closeTime);
				Date currTime = Constants.IBDataFormatter.parse(data.time);
				
				if ((currTime.getTime() - closeTime.getTime()) > (120 * 60000L))
				{
					//tradeManagerInf.removeTrade(); //temporily not remove the file
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
		//if ( monitor_reverse(data) == Constants.STOP)
		//	return;

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
	
	
/*
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
/*					if ( lastbar15 == trade.stoppedOutPos ) 
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
/*					if ( lastbar15 == trade.stoppedOutPos ) 
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
*/	
	
	
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
//			placeBreakEvenStop();
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
		//monitor_profit_detect_peak(data, quotes1, Constants.CHART_5_MIN);
		//monitor_profit_detect_peak(data, quotes15, Constants.CHART_15_MIN);
		monitor_profit_detect_peak(data, quotes60, Constants.CHART_60_MIN);
		
		
		
		
		
		
		
		/******************************************************************************
		// smaller timefram for detecting sharp pullbacks
		 * ****************************************************************************/
/*		if (!exitTradePlaced())
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
							logger.warning(data.time + " take profit > 200 on 2.0");
							//takePartialProfit( adjustPrice(quotes5[phlS.prePos].low, Constants.ADJUST_TYPE_DOWN), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( trade.monitorProfitInPips > 200 )  
							{
								logger.warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
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
						logger.warning(data.time + " cross over after large rundown detected " + quotes5[lastbar5].time);
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
							logger.warning(data.time + " take profit > 200 on 2.0");
							//takePartialProfit( adjustPrice(quotes5[phlS.prePos].high, Constants.ADJUST_TYPE_UP), trade.remainingPositionSize );
						}
						else if ( pullBackDist > 1.8 * FIXED_STOP * PIP_SIZE)
						{
							if ( trade.monitorProfitInPips > 200 )  
							{
								logger.warning(data.time + " take profit > 200 on 5 gap is " + (phlS.curPos - phlS.prePos));
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
						logger.warning(data.time + " cross over after large runup detected " + quotes5[lastbar5].time);
						//takePartialProfit( quotes5[lastbar5].close, trade.remainingPositionSize );
					}
				}
			}

		}*/
		return Constants.CONTINUE;
	
	}
	
	
	private int monitor_profit_detect_peak(QuoteData data, QuoteData[] quotes, int CHART)
	{
		int lastbar = quotes.length - 1;
		String eventName = "";

		if (Constants.ACTION_SELL.equals(trade.action))
		{
			int tradeEntryPos = Utility.findPosition(quotes, CHART, trade.entryTime, Constants.BACK_TO_FRONT);
			if (tradeEntryPos == Constants.NOT_FOUND)
				return Constants.CONTINUE;
			int pushStart = Utility.getHigh(quotes, tradeEntryPos-12, tradeEntryPos).pos;
			
			PushList pushList = PushSetup.findPast2Lows(quotes, pushStart, lastbar);
			if ((pushList != null ) && (pushList.end == lastbar) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				//PushHighLow lastPush = phls[phls.length - 1]; 
				PushHighLow lastPush = phls[0]; 
				double price = quotes[lastPush.prePos].low;
				int pullback = (int)((lastPush.pullBack.high - quotes[lastPush.prePos].low)/PIP_SIZE);

				/******************************************************************************
				// look to take profit
				 * ****************************************************************************/
				if ( pullback  > FIXED_STOP )
				{
					ConsectiveBars prevConsecDownBars = Utility.findLastConsectiveDownBars( quotes, 0, lastPush.prePos);
					int prevConsecDownSizesInPips = 0;
					//System.out.println(data.time);
					if ("20141002  10:02:00".equals(data.time))
						System.out.println("here");
					if 	( prevConsecDownBars != null ){
						prevConsecDownSizesInPips = (int)((quotes[prevConsecDownBars.getBegin()].high - quotes[prevConsecDownBars.getEnd()].low)/PIP_SIZE);
					}

					if ( CHART == Constants.CHART_60_MIN )
						eventName = TradeEvent.PEAK_LOW_60;
					else if ( CHART == Constants.CHART_15_MIN )
						eventName = TradeEvent.PEAK_LOW_15;
					TradeEvent tv = new TradeEvent(eventName, quotes[lastbar].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.PREVIOUS_PUSH_SIZE, prevConsecDownSizesInPips));
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

			PushList pushList = PushSetup.findPast2Highs(quotes, pushStart, lastbar);
			if ((pushList != null ) && (pushList.end == lastbar) && ( pushList.phls != null ) && ( pushList.phls.length > 0))
			{	
				PushHighLow[] phls = pushList.phls;
				//PushHighLow lastPush = phls[phls.length - 1];
				PushHighLow lastPush = phls[0]; 
				double price = quotes[lastPush.prePos].high;
				int pullback = (int)((quotes[lastPush.prePos].high - lastPush.pullBack.low)/PIP_SIZE);

				ConsectiveBars prevConsecUpBars = Utility.findLastConsectiveUpBars( quotes, 0, lastPush.prePos);
				int prevConsecUpSizesInPips = 0;
				if 	( prevConsecUpBars != null ){
					prevConsecUpSizesInPips = (int)((quotes[prevConsecUpBars.getEnd()].high - quotes[prevConsecUpBars.getBegin()].low)/PIP_SIZE);
				}

				if ( pullback  > FIXED_STOP )
				{
					if ( CHART == Constants.CHART_60_MIN )
						eventName = TradeEvent.PEAK_HIGH_60;
					else if ( CHART == Constants.CHART_15_MIN )
						eventName = TradeEvent.PEAK_HIGH_15;
					TradeEvent tv = new TradeEvent(eventName, quotes[lastbar].time);
					tv.addNameValue(new NameValue(TradeEvent.NAME_PULLBACK_SIZE, (new Integer(pullback)).toString()));
					tv.addNameValue(new NameValue(TradeEvent.PREVIOUS_PUSH_SIZE, prevConsecUpSizesInPips));
					//tv.addNameValue(new NameValue(TradeEvent.NAME_START_TIME, quotes[pushStart].time));
					//tv.addNameValue(new NameValue(TradeEvent.NAME_PRICE, price));
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
