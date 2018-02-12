package tao.trading.entry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Position;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradePosition;
import tao.trading.strategy.TradeManager2;
import tao.trading.strategy.tm.TradeManagerInf;

public class AfterEntryAddMonitor
{
	public String action;
	public int timeFrame;
	public String startTime;
	public int positionSize;
	List<Position> positions; 
	private boolean average_up = true;

	Logger logger = Logger.getLogger("PullbackConfluenceEntry");
	
	
	public AfterEntryAddMonitor(TradeManagerInf tradeManager) {
		super();
	}

	public void entry_manage( QuoteData data, TradeManager2 tradeManager )
	{
		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();

		TradePosition firstlimit = trade.limits[0];

		if ( trade.startMonitoring == false ){
			logger.info("Start to monitor " + tradeManager.getSymbol() + " initial entry:" + firstlimit.price);
			trade.startMonitoring = true;
		}
		
		// entry
		/*
		for ( int i = 1; i<=10; i++){
			if (Constants.ACTION_SELL.equals(trade.action)){
				if  ( data.high > firstlimit.price + i * FIXED_STOP * PIP_SIZE ){
					double entryPrice = firstlimit.price + i * FIXED_STOP * PIP_SIZE;
					if (!trade.findFilledPosition(entryPrice)){
						logger.info("place incremental sell order " + entryPrice);
						tradeManager.enterLimitPositionMulti(POSITION_SIZE/2, entryPrice);
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action)){
				if  ( data.low < firstlimit.price - i * FIXED_STOP * PIP_SIZE ){
					double entryPrice = firstlimit.price - i * FIXED_STOP * PIP_SIZE;
					if (!trade.findFilledPosition(entryPrice)){
						logger.info("place incremental buy order " + entryPrice);
						tradeManager.enterLimitPositionMulti(POSITION_SIZE/2, entryPrice);
					}
				}
			}
		}*/

		
		
		/*********************************************************************
		 *  status: detect profit and move stop
		 *********************************************************************/
		double profitInPip = 0;
		if (Constants.ACTION_SELL.equals(trade.action)){
			profitInPip = (trade.entryPrice - data.low)/PIP_SIZE;
			if ((trade.reach2FixedStop == false) &&  (profitInPip > 2 * FIXED_STOP )){
				// entryPrice is the first entry price
				trade.stop = tradeManager.adjustPrice(trade.entryPrice+FIXED_STOP*PIP_SIZE, Constants.ADJUST_TYPE_UP);
				tradeManager.createStopOrder(trade.stop);
				trade.reach2FixedStop = true;
				logger.info("move stop to break even");
			}
		}
		else if (Constants.ACTION_BUY.equals(trade.action)){
			profitInPip = ( data.high - trade.entryPrice )/PIP_SIZE;
			if ((trade.reach2FixedStop == false) &&  (profitInPip > 2 * FIXED_STOP )){
				// entryPrice is the first entry price
				trade.stop = tradeManager.adjustPrice(trade.entryPrice-FIXED_STOP*PIP_SIZE, Constants.ADJUST_TYPE_UP);
				tradeManager.createStopOrder(trade.stop);
				trade.reach2FixedStop = true;
				logger.info("move stop to break even");
			}
		}
		

		
		/*********************************************************************
		 *  add addtional positions
		 *********************************************************************/
		if ((average_up == true ) && (trade.averageUp == false ))
		{
			if (Constants.ACTION_SELL.equals(trade.action) &&  (( trade.entryPrice - data.low ) > FIXED_STOP * PIP_SIZE )){
				tradeManager.enterMarketPositionAdditional(trade.entryPrice - FIXED_STOP * PIP_SIZE, POSITION_SIZE);
				trade.averageUp = true;
			}
			else if (Constants.ACTION_BUY.equals(trade.action) &&  (( data.high - trade.entryPrice ) > FIXED_STOP * PIP_SIZE )){
				tradeManager.enterMarketPositionAdditional(trade.entryPrice + FIXED_STOP * PIP_SIZE, POSITION_SIZE);
				trade.averageUp = true; 
			}
		}

		
		
		
		
		
		
		
		
		// unload positions
		/*
		int posSize = 0;
		while( positions[posSize] != null ){
			posSize++;
		}
		
		if ( posSize > 0 ){
			// last one
			TradePosition position = positions[posSize-1]; // last one
			if (!Constants.STATUS_CLOSED.equals( position.getStatus())){
				if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  2 * FIXED_STOP * PIP_SIZE )){
					double closePrice = position.price -  2 * FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  2 * FIXED_STOP * PIP_SIZE )){
					double closePrice = position.price +  2 * FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
			} 
			
			// all the ones before, other than the first 1
			Constants.STATUS_CLOSED.equals( position.getStatus());
			for ( int i = posSize-2; i > 0; i--){
				position = positions[i];
				if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  FIXED_STOP * PIP_SIZE ) && !Constants.STATUS_CLOSED.equals(position.getStatus())){
					double closePrice = position.price -  FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
				else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  FIXED_STOP * PIP_SIZE )&& !Constants.STATUS_CLOSED.equals(position.getStatus())){
					double closePrice = position.price +  FIXED_STOP * PIP_SIZE;
					tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
					tradeManager.closePositionByMarket( position.position_size, closePrice);
					position.setStatus(Constants.STATUS_CLOSED);
					trade.status = Constants.STATUS_FILLED;
				}
			}
		}*/
		
		
		// set stop
	}		

	
	
	
	
	
	public AfterEntryAddMonitor(int positionSize)
	{
		this.positionSize = positionSize;
		positions = new ArrayList<Position>();
	}
	
	public AfterEntryAddMonitor(String action, int timeFrame, String startTime, int positionSize)
	{
		super();
		this.action = action;
		this.timeFrame = timeFrame;
		this.startTime = startTime;
		this.positionSize = positionSize;
		positions = new ArrayList<Position>();
	}

	public boolean findEntry(double price)
	{
		Iterator<Position> it = positions.iterator();
		while (it.hasNext())
		{
			Position p = it.next();
			if ( p.price == price )
				return true;
		}
		
		return false;
	}
	
	public void addPosition(double price, int size)
	{
		Position p = new Position();
		p.price = price;
		positions.add(p);
	}

	public List<Position> getPositions() {
		return positions;
	}
	
	
}
