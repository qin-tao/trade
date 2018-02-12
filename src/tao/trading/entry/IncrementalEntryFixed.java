package tao.trading.entry;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import tao.trading.Constants;
import tao.trading.Position;
import tao.trading.QuoteData;
import tao.trading.Trade;
import tao.trading.TradePosition;
import tao.trading.dao.PushHighLow;
import tao.trading.dao.PushList;
import tao.trading.setup.PushSetup;
import tao.trading.strategy.tm.TradeManagerInf;
import tao.trading.strategy.util.Utility;

public class IncrementalEntryFixed
{
	public String action;
	public int timeFrame;
	public String startTime;
	public int positionSize;
	List<Position> positions; 

	
	public static void entry(QuoteData data, TradeManagerInf tradeManager)
	{
		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();
		
		// entry
		TradePosition[] positions = trade.getFilledPositions();

		if ( !trade.status.equals(Constants.STATUS_FILLED)){
			TradePosition initPosition = positions[0];
			
			for ( int i = 1; i<=10; i++){
				if (Constants.ACTION_SELL.equals(trade.action)){
					if  ( data.high > initPosition.price + i * FIXED_STOP * PIP_SIZE ){
						double entryPrice = initPosition.price + i * FIXED_STOP * PIP_SIZE;
						if (!trade.findFilledPosition(entryPrice)){
							System.out.println("place incremental sell order " + entryPrice);
							tradeManager.enterMarketPositionMulti(entryPrice, POSITION_SIZE);
							trade.status = Constants.STATUS_PARTIAL_FILLED;
							trade.stopId = 0;
						}
					}
				}
				else if (Constants.ACTION_BUY.equals(trade.action)){
					if  ( data.low < initPosition.price - i * FIXED_STOP * PIP_SIZE ){
						double entryPrice = initPosition.price - i * FIXED_STOP * PIP_SIZE;
						if (!trade.findFilledPosition(entryPrice)){
							tradeManager.enterMarketPositionMulti(entryPrice, POSITION_SIZE);
							trade.status = Constants.STATUS_PARTIAL_FILLED;
							trade.stopId = 0;
						}
					}
				}
			}
		}

		// unload positions
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
		}
		
		
		// set stop
	}		

	
	
	
	
	
	public IncrementalEntryFixed(int positionSize)
	{
		this.positionSize = positionSize;
		positions = new ArrayList<Position>();
	}
	
	public IncrementalEntryFixed(String action, int timeFrame, String startTime, int positionSize)
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
