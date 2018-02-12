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
import tao.trading.strategy.util.BarUtil;
import tao.trading.strategy.util.Utility;

public class IncrementalEntryFollowUpBar implements EntryInf
{
	public String action;
	public String startTime;

	public int timeFrame;
	public int positionSize;
	List<Position> positions; 

	
	public IncrementalEntryFollowUpBar() {
		super();
		// TODO Auto-generated constructor stub
	}


	public void entry_manage( QuoteData data, TradeManagerInf tradeManager )
	{
		QuoteData[] quotes60 = tradeManager.getInstrument().getQuoteData(Constants.CHART_60_MIN);
		int lastbar60 = quotes60.length - 1;

		int FIXED_STOP = tradeManager.getFIXED_STOP();
		double PIP_SIZE = tradeManager.getPIP_SIZE();
		int POSITION_SIZE = tradeManager.getPOSITION_SIZE();
		Trade trade = tradeManager.getTrade();
		
		int detectPos = Utility.findPositionByHour(quotes60, trade.detectTime, Constants.BACK_TO_FRONT );

		if ( !trade.incrementalComplete){
			if (Constants.ACTION_SELL.equals(trade.action)){
				if ((lastbar60-1 > detectPos) && BarUtil.isDownBar(quotes60[lastbar60-1]) && (data.low < quotes60[lastbar60-1].low)){ 
					double entryPrice = quotes60[lastbar60-1].low;
					TradePosition[] positions = trade.getFilledPositions();
					int posSize = positions.length;
					if ((posSize == 0) || ((posSize > 0) && ( posSize < 4 ) && (entryPrice > positions[posSize-1].price))){
						System.out.println("place incremental sell order " + entryPrice);
						tradeManager.enterMarketPositionMulti(entryPrice, POSITION_SIZE);
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						trade.stopId = 0;
					}
				}
			}
			else if (Constants.ACTION_BUY.equals(trade.action)){
				if ((lastbar60-1 > detectPos) && BarUtil.isUpBar(quotes60[lastbar60-1]) && (data.high > quotes60[lastbar60-1].high)){ 
					double entryPrice = quotes60[lastbar60-1].high;
					TradePosition[] positions = trade.getFilledPositions();
					int posSize = positions.length;
					if ((posSize == 0) || ( (posSize > 0) && ( posSize < 4 ) &&(entryPrice < positions[posSize-1].price))){
						System.out.println("place incremental buy order " + entryPrice);
						tradeManager.enterMarketPositionMulti(entryPrice, POSITION_SIZE);
						trade.status = Constants.STATUS_PARTIAL_FILLED;
						trade.stopId = 0;
					}
				}
			}
		}

		// set stop

		// unload positions
		TradePosition[] positions = trade.getFilledPositions();
		int posSize = positions.length;
		
		if ( posSize > 1 ){

			if ( !trade.status.equals(Constants.STATUS_INCREMENTAL_COMPLETED)){
				// last one
				TradePosition position = positions[posSize-1]; // last one
				if (!Constants.STATUS_CLOSED.equals( position.getStatus())){
					if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  2 * FIXED_STOP * PIP_SIZE )){
						double closePrice = position.price -  2 * FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
						System.out.println("initial close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
						trade.incrementalComplete = true;
					}
					else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  2 * FIXED_STOP * PIP_SIZE )){
						double closePrice = position.price +  2 * FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_SELL, position.position_size, closePrice);
						System.out.println("initial close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
						trade.incrementalComplete = true;
					}
				}
			}
			else{
				// all the ones before, other than the first 1
				for ( int i = posSize-2; i > 0; i--){
					TradePosition position = positions[i];
					if (Constants.ACTION_SELL.equals(trade.action) && ( data.low < position.price -  FIXED_STOP * PIP_SIZE ) && !Constants.STATUS_CLOSED.equals(position.getStatus())){
						double closePrice = position.price -  FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_BUY, position.position_size, closePrice);
						System.out.println("close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
					}
					else if (Constants.ACTION_BUY.equals(trade.action) && ( data.high > position.price +  FIXED_STOP * PIP_SIZE )&& !Constants.STATUS_CLOSED.equals(position.getStatus())){
						double closePrice = position.price +  FIXED_STOP * PIP_SIZE;
						tradeManager.AddCloseRecord(data.time, Constants.ACTION_SELL, position.position_size, closePrice);
						System.out.println("close position by market " + closePrice);
						tradeManager.closePositionByMarket( position.position_size, closePrice);
						position.setStatus(Constants.STATUS_CLOSED);
					}
				}
			}
		}
		
		
		// set stop
	}		

	

	
	
	
	
	public IncrementalEntryFollowUpBar(int positionSize)
	{
		this.positionSize = positionSize;
		positions = new ArrayList<Position>();
	}
	
	public IncrementalEntryFollowUpBar(String action, int timeFrame, String startTime, int positionSize)
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
