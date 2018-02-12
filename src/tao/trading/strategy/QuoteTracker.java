package tao.trading.strategy;

import java.awt.Toolkit;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.AccountManager;
import tao.trading.Indicator;
import tao.trading.OrderManager;
import tao.trading.QuoteData;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class QuoteTracker implements EWrapper
{
	private static Logger logger = Logger.getLogger("QuoteTracker");
	protected Indicator indicator = new Indicator();
	protected Contract contract;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	protected OrderManager orderManager = OrderManager.getInstance();	
	protected AccountManager accountManager = AccountManager.getInstance();
	String filename = null;
	Writer output = null;
	double pullback;
	int numOfBars;
	String symbol;
	int id_1min_history = 1001;
	int id_60min_history = 1002;
	int id_realtime_bar = 1003;
	Vector<QuoteData> quotes1m = new Vector<QuoteData>(200);
	Vector<QuoteData> quotes60m = new Vector<QuoteData>(200);
	double high60min = 0;
	double high1min = 0;
	double low60min = 999;
	double low1min = 999;
	boolean history60m = false;
	boolean history1m = false;

	public QuoteTracker( String symbol, String secType, String exchange, String currency)
	{
		contract = new Contract();
    	contract.m_conId=2316;//accountManager.getConnectionId();
    	contract.m_symbol = symbol;
        contract.m_secType = secType;
        contract.m_exchange = exchange;
        contract.m_currency=currency;

        m_client.eConnect("127.0.0.1" , 7496, contract.m_conId);
        m_client.setServerLogLevel(5);
	}

	public QuoteTracker( int connId, String symbol, String secType, String exchange, String currency, double pullback, int numOfBars)
	{
		contract = new Contract();
    	contract.m_conId=connId;//accountManager.getConnectionId();
    	contract.m_symbol = symbol;
        contract.m_secType = secType;
        contract.m_exchange = exchange;
        contract.m_currency=currency;

        m_client.eConnect("127.0.0.1" , 7496, contract.m_conId);
        m_client.setServerLogLevel(5);

        this.pullback = pullback;
        this.numOfBars = numOfBars;
        this.symbol = symbol + "_" + currency;
        
        this.filename = "C:/trade_logs/" + this.symbol + ".log";
        writeToFile( "\n\n" + IBDataFormatter.format(new Date())+"\n" );

	}

	public QuoteTracker( Contract contract )
	{
		this.contract = contract;
        m_client.eConnect("127.0.0.1" , 7496, contract.m_conId);
        m_client.setServerLogLevel(5);
	}

	
	public void run() {
		
		// this will kick off the sequence
		m_client.reqCurrentTime();
		
	}

	@Override
	public void error(Exception e) {
		System.out.println("Error: " );
		e.printStackTrace();
		
	}

	@Override
	public void error(String str) {
		System.out.println("Error: " + str);
		
	}

	@Override
	public void error(int id, int errorCode, String errorMsg) {
		System.out.println("Error: " + id + " " + errorCode + " " + errorMsg);
		
		
	}

	@Override
	public void accountDownloadEnd(String accountName)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void bondContractDetails(int reqId, ContractDetails contractDetails)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetails(int reqId, ContractDetails contractDetails)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void contractDetailsEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void currentTime(long time)
	{
		String currentTime = IBDataFormatter.format(new Date( time*1000));
		logger.info(symbol + ":currnetTime:" + currentTime );
		
    	m_client.reqHistoricalData( id_1min_history, contract,  
    			currentTime, //"20091015 16:26:44",
                "72000 S", "1 min", "MIDPOINT", 1, 1);               

    	m_client.reqHistoricalData( id_60min_history, contract,  
    			currentTime, //"20091015 16:26:44",
           		"10 D", "1 hour", "MIDPOINT", 1, 1);               
		
	}

	@Override
	public void deltaNeutralValidation(int reqId, UnderComp underComp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetails(int reqId, Contract contract, Execution execution)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void execDetailsEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void fundamentalData(int reqId, String data)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void historicalData(int reqId, String date, double open, double high, double low, double close,
			int volume, int count, double WAP, boolean hasGaps)
	{
		
		//System.out.println(symbol + " id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
		//		" WAP:" + WAP + " hasGaps:" + hasGaps);
		
		if (date.indexOf("finished-") == -1)
		{
			if (reqId == id_1min_history)
			{
				QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
				quotes1m.add(data);
			}
			else if (reqId == id_60min_history)
			{
				QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
				quotes60m.add(data);
			}
		}
		else
		{
			if (reqId == id_60min_history)
			{	
				history60m = true;
			}
			
			if (reqId == id_1min_history)
			{	
				history1m = true;
			}
			
				//calculateMovingAverages();
			if ( history60m && history1m)
			{
				m_client.reqRealTimeBars(id_realtime_bar, contract, 60, "MIDPOINT", true);
				System.out.println(symbol + " started");
			}

		}
		
	}

	@Override
	public void managedAccounts(String accountsList)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void nextValidId(int orderId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrder(int orderId, Contract contract, Order order, OrderState orderState)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void openOrderEnd()
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice,
			int permId, int parentId, double lastFillPrice, int clientId, String whyHeld)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9, 11);
		String min = timeStr.substring(12, 14);
		String sec = timeStr.substring(15,17);
		//logger.info(symbol + " hr:" + hr + " min:"+min+ " sec:" + sec);
	
		// the goal is once the current bar does not make new high
		if (high > high60min)
			high60min = high;
		if (high > high1min)
			high1min = high; 
		if ( low < low60min )
			low60min = low;
		if ( low < low1min )
			low1min = low;

		if (min.equals("00") && sec.equals("00"))
		{
			QuoteData data = new QuoteData(timeStr, 0.0, high60min, low60min, close, 0, 0, wap, false);
			quotes60m.add(data);
			
			if ( quotes60m.size() > 1000)
				quotes60m.removeElementAt(0); // to prevent it running too long
			
			calculate60MACrossOver();
			
			// reset counter
			high60min=0;
			low60min=999;
		}

		if (sec.equals("00"))
		{
			QuoteData data = new QuoteData(timeStr, 0.0, high1min, low1min, close, 0, 0, wap, false);
			quotes1m.add(data);
			
			if ( quotes1m.size() > 1000)
				quotes1m.removeElementAt(0); // to prevent it running too long
			//quotes1m.add(data);
			calculateTopButtom(timeStr, close);
			
			// reset counter
			high1min=0;
			low1min=999;
		}
		
		
	}

	@Override
	public void receiveFA(int faDataType, String xml)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance,
			String benchmark, String projection, String legsStr)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerDataEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void scannerParameters(String xml)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints,
			double impliedFuture, int holdDays, String futureExpiry, double dividendImpact,
			double dividendsToExpiry)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickGeneric(int tickerId, int tickType, double value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta,
			double modelPrice, double pvDividend)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickPrice(int tickerId, int field, double price, int canAutoExecute)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickSize(int tickerId, int field, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickSnapshotEnd(int reqId)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void tickString(int tickerId, int tickType, String value)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountTime(String timeStamp)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateAccountValue(String key, String value, String currency, String accountName)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side,
			double price, int size)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue,
			double averageCost, double unrealizedPNL, double realizedPNL, String accountName)
	{
		// TODO Auto-generated method stub
		
	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}

	private void calculate60MACrossOver()
	{
		Vector<QuoteData> quotes = quotes60m;

		double[] ema5 = indicator.calculateEMA(quotes, 5);
		double[] ema20 = indicator.calculateEMA(quotes, 20);
			
		//int last = quotes60m.size() -1;
		int last = quotes.size() -1;
		
			
		if (( ema5[last] > ema20[last] ) && (ema5[last-1] < ema20[last-1])) 
		{
				// this is a buy signal
			    writeToFile( symbol + "5 cross 20 up :" + ((QuoteData)quotes.elementAt(last)).time +  " buy at " + ((QuoteData)quotes.elementAt(last)).close);
				// if position is open, close position
				/*
				//else add buy position
				Integer existPos = accountManager.getPosition(contract.m_symbol);
				// logic to handle existing position
				int positionsize = 10;
				if (( existPos != null ) && ( existPos < 0))
				{
					// cover the existing position as well
					positionsize -= existPos;
				}
				
				//Order order = orderManager.createOrder("BUY",positionsize,"MKT",0);
				//m_client.placeOrder(order.m_orderId, contract, order);
				*/
			}

			if (( ema5[last] < ema20[last] ) && ( ema5[last-1] > ema20[last-1])) 
			{
				// this is a sell signal
				writeToFile( symbol + "5 cross 20 down :" + ((QuoteData)quotes.elementAt(last)).time +  " sell at " + ((QuoteData)quotes.elementAt(last)).close);
				/*
				Integer existPos = accountManager.getPosition(contract.m_symbol);
				// logic to handle existing position
				int positionsize = 10;
				if (( existPos != null ) && ( existPos > 0))
				{
					// cover the existing position as well
					positionsize -= existPos;
				}
				
				//OrderManager orderManager = OrderManager.getInstance();	
				//Order order = orderManager.createOrder("SELL",positionsize,"MKT",0);
				//m_client.placeOrder(order.m_orderId, contract, order);
				*/
			}
			
		
	}
	
	
	
	private void calculateTopButtom( String timeStr, double close)
	{
		Vector<QuoteData> quotes = quotes1m;
		
		if (( indicator.strategyNewHighAfterPullBack(quotes, pullback, 60, close  )) ||
				( indicator.strategyNewHighAfterPullBack(quotes, pullback, 120, close )) ||
				( indicator.strategyNewHighAfterPullBack(quotes, pullback, 240, close )) ||
				( indicator.strategyNewHighAfterPullBack(quotes, pullback, 480, close )))
			{
					Toolkit.getDefaultToolkit().beep();
					String log = timeStr + " " + contract.m_symbol + "." + contract.m_currency + " to sell at " + close;
					logger.info(log);
					writeToFile(log);
					//Order = new Order(symbol, )
			}

			if (( indicator.strategyNewLowAfterPullBack(quotes, pullback, 60, close  )) ||
					( indicator.strategyNewLowAfterPullBack(quotes, pullback, 120, close )) ||
					( indicator.strategyNewLowAfterPullBack(quotes, pullback, 240, close )) ||
					( indicator.strategyNewLowAfterPullBack(quotes, pullback, 480, close )))
			{
					Toolkit.getDefaultToolkit().beep();
					String log = timeStr + " " + contract.m_symbol + "." + contract.m_currency + " to buy at " + close;
					logger.info(log);
					writeToFile(log);
			}
				
	}			


	private void writeToFile( String str )
	{

		try {
		        BufferedWriter out = new BufferedWriter(new FileWriter("filename", true));
		        out.write(str );
		        out.close();
		    } catch (IOException e) {
		    }
	}

	

}
