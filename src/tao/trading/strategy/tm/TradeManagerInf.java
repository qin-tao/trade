package tao.trading.strategy.tm;

import java.util.Vector;

import tao.trading.Instrument;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.thoughtworks.xstream.XStream;

public interface TradeManagerInf
{
	public void setStrategy(Strategy s);
	public String getSymbol();
	public String getAccount();
	
	public Trade getTrade();
	public void setTrade(Trade t);
	public void removeTrade();

	public Instrument getInstrument();
	public QuoteData[] getQuoteData(int chart);
	public double getPIP_SIZE();
	public int getFIXED_STOP();
	public int getPOSITION_SIZE();
	public void setEnable(boolean enable);
	
	public Trade createOpenTrade(String tradeType, String action);
	public Trade createOpenTrade(String tradeType, String action, String option, int posSize, double triggerPrice);
	
	
	public void process( int req_id, QuoteData data );
	//public abstract Trade detect( QuoteData data );
	//public abstract void entry( QuoteData data );
	//spublic abstract void manage( QuoteData data );
	//public abstract void checkOrderFilled(int orderId, int filled);
	
	//public void saveTradeData(XStream xstream);
	//public void loadTradeData(XStream xstream);
	//public void saveTradeSignal();
	public void loadAllTradeData( XStream xstream );
	public void saveAllTradeData( XStream xstream );
	public String getTradeStatus();

	public void checkOrderFilled(int orderId, String status, int filled, int remaining, double avgFillPrice,
			int permId, int parentId, double lastFillPrice, int clientId, String whyHeld);
	public void checkOrderFilled(int orderId, int filled);

	public void createTradePlaceLimit( String action, double quantity, double price1 );
	public void createTradeTargetOrder( int quantity, double price );
	public void createStopOrder(double stopPrice);
	public void cancelAllOrders();
	public void cancelTargets();
	public void cancelLimits();
	public void cancelStop();
	public int placeLmtOrder(String action, double limitPrice, int posSize, String ocaGroup);
	//public void enterLimitPosition1(double price, int size);
	public int enterLimitPositionMulti( int quantity, double price );
	public int enterMarketPositionMulti(double price, int positionSize);
	public int enterIncrementalMarketPosition(double price, int positionSize, String time);
	public void closePositionByMarket( int posSize, double currentPrice );


	
	public void AddCloseRecord( String time, String action, int posSize, double price);
	public Vector<Trade> getTradeHistory();
	public TradeReport getTradeReport();
	public String getTradeEvents(Trade t);
	public String report2(double currentPrice);
	//public boolean createTradeReport( String reportFileName );	
	public void removeTradeNotUpdated();
	public void initEntryMode(int mode, double init_price, double init_quantity);
	
	
	public double adjustPrice( double price, int adjustType );

	
}
