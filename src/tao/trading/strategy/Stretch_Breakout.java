package tao.trading.strategy;

import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.Trade;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class Stretch_Breakout implements EWrapper
{
	private static Logger logger = Logger.getLogger("Stretch");
	protected Indicator indicator = new Indicator();
	protected Contract contract;
	protected EClientSocket m_client = new EClientSocket(this);
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	protected StretchTradeManager[] tradeManager;	
	String filename = null;
	Writer output = null;
	double pullback;
	int numOfBars;
	boolean history60m = false;
	boolean history1m = false;
	Contract[] contracts;
	int[] id_1m_history;
	int[] id_realtime;
	int totalSymbol;
	Vector<QuoteData>[] quotes1m; 
	double[] high1min;
	double[] low1min;
	double[] entryPrice;
	double[] targetPrice;
	String[] action;
	double[][] sma200_1m;
	boolean [] tradePlaced;
	int totalSymbolIndex = 0;
	int connectionId = 100;
	
	public static void main(String[] args)
	{
		Stretch_Breakout stretch = new Stretch_Breakout();
		stretch.run();
		
			try{
				
				Thread.sleep(990000000);
			}
			catch( Exception e)
			{
			}
		}

	public void run() {
		
		// this will kick off the sequence
		m_client.reqCurrentTime();
		
	}

	public Stretch_Breakout()
	{
        totalSymbol = 8;
		
		contracts = new Contract[totalSymbol];
		entryPrice = new double[totalSymbol];
		quotes1m = new Vector[totalSymbol];
		high1min = new double[totalSymbol];
		low1min = new double[totalSymbol];
		tradeManager = new StretchTradeManager[totalSymbol];	
		sma200_1m = new double[totalSymbol][];
		tradePlaced = new boolean[totalSymbol];
        entryPrice = new double[totalSymbol];
        targetPrice = new double[totalSymbol];
        action = new String[totalSymbol];
		
		for ( int i = 0; i < totalSymbol; i++)
		{
			contracts[i] = new Contract();
			quotes1m[i] = new Vector<QuoteData>(200);
			high1min[i] = 0;
			low1min[i] = 999;
			tradePlaced[i] = false;
		}
		
		//createContract( 0, 100, "EUR", "CASH", "IDEALPRO", "USD", "BUY", entryPrice, targetPrice);	
		
		//createContract( "GBP", "CASH", "IDEALPRO", "JPY", "SELL", 148.96, -1);	
		createContract( "USD", "CASH", "IDEALPRO", "CHF", "SELL", 1.0322, -1);
		//createContract( "USD", "CASH", "IDEALPRO", "CAD", "SELL", 1.058, -1);
		//createContract( "USD", "CASH", "IDEALPRO", "JPY", "SELL", 92.7725, -1);
		createContract( "USD", "CASH", "IDEALPRO", "JPY", "BUY", 92.3, -1);
		createContract( "EUR", "CASH", "IDEALPRO", "CAD", "BUY", 1.5094, -1);
		createContract( "EUR", "CASH", "IDEALPRO", "JPY", "SELL", 133.44, -1);
		createContract( "EUR", "CASH", "IDEALPRO", "GBP", "SELL", 0.9042, -1);
		//createContract( "EUR", "CASH", "IDEALPRO", "GBP", "SELL", 0.9055, -1);
		createContract( "EUR", "CASH", "IDEALPRO", "USD", "BUY", 1.4375, -1);
		createContract( "EUR", "CASH", "IDEALPRO", "CHF", "BUY", 1.4830, -1);
		createContract( "GBP", "CASH", "IDEALPRO", "USD", "SELL", 1.6236,-1 );
		
		id_1m_history = new int[]{0,1,2,3,4,5,6,7,8,9};
		id_realtime = new int[]{100,101,102,103,104,105,106,107,108,109};

        m_client.eConnect("127.0.0.1" , 7496, 8);
        m_client.setServerLogLevel(5);

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
		logger.info("CurrnetTime:" + currentTime );

		for ( int i = 0; i < totalSymbol; i++)
		{
	    	m_client.reqHistoricalData( id_1m_history[i], contracts[i],  
	    			currentTime, //"20091015 16:26:44",
	                "24000 S", "3 mins", "MIDPOINT", 1, 1);     //72000S          
		}
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
		
		//System.out.println("id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
		//		" WAP:" + WAP + " hasGaps:" + hasGaps);
		
		if (date.indexOf("finished-") == -1)
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_1m_history[i])
				{
					QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
					quotes1m[i].add(data);
					break;
				}
			}
		}
		else
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				if (reqId == id_1m_history[i])
				{
					m_client.reqRealTimeBars(id_realtime[i], contracts[i], 60, "MIDPOINT", true);
					System.out.println(contracts[i].m_symbol + "." + contracts[i].m_currency + " started");
				}
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
		if ("Filled".equals(status)) //&& (orderId > Constants.ORDER_ID_STOP_OFFSET))
		{
			for ( int i = 0; i < totalSymbol; i++)
			{
				tradeManager[i].checkStopTriggered(orderId);
			}
		}
		
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9, 11);
		String min = timeStr.substring(12, 14);
		String sec = timeStr.substring(15,17);
		//logger.info(reqId + " " + " hr:" + hr + " min:"+min+ " sec:" + sec);
		//System.out.println("reqId:" + reqId + " time:" + time + " high:" + high + " low:" + low + " close:" + close);
		// the goal is once the current bar does not make new high
		for ( int i = 0; i < totalSymbol; i++)
		{
			if (reqId == id_realtime[i])
			{
				if (high > high1min[i])
					high1min[i] = high; 
				if ( low < low1min[i] )
					low1min[i] = low;

				// this is for better entry
				Trade trade = tradeManager[i].getTrade();
				if ( trade != null )
				{
					// trade already exists
					QuoteData data = new QuoteData(timeStr, open, high, low, close, 0, 0, wap, false);
					if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) || trade.status.equals(Constants.STATUS_REVERSAL))
					{
						//logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency + "trackTradeEntry");
						tradeManager[i].trackTradeEntry(data);
					}
					else if (trade.status.equals(Constants.STATUS_PLACED))
					{
						// this is to check whether it needs any "stop and reversal"
						//logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency + "trackTradeExit");
						tradeManager[i].trackTradeExit(data, targetPrice[i], sma200_1m[i]);
					}
				}
				
				int second = new Integer(sec);
				int minute = new Integer(min);
				if ((second == 0) && (minute % 3 == 0 ))
				{
					//logger.info(timeStr + "-" + contracts[i].m_symbol+"."+contracts[i].m_currency+ ": high-" + high1min[i] + " low-" + low1min[i] );

					QuoteData data = new QuoteData(timeStr, 0.0, high1min[i], low1min[i], close, 0, 0, wap, false);
					quotes1m[i].add(data);
					
					if ( quotes1m[i].size() > 500)
						quotes1m[i].removeElementAt(0); // to prevent it running too long
					
					sma200_1m[i] = indicator.calculateSMA(quotes1m[i], 200);
					
					trade = tradeManager[i].getTrade();
					if ( trade != null )
					{
						if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) || trade.status.equals(Constants.STATUS_REVERSAL))
						{
							logger.info("00" + contracts[i].m_symbol+"."+contracts[i].m_currency + "trackTradeEntry");
							tradeManager[i].addTradeEntryData(data);
						}
					}
					else 
					{
						if ( tradePlaced[i] == false )	// we only do this once, prevent keep hitting unsuccessful trades
						{
							// no trade exist
							logger.info(contracts[i].m_symbol+"."+contracts[i].m_currency+ ": calculate Stretch");
							if ((  trade = tradeManager[i].calculateStretch(quotes1m[i], sma200_1m[i], action[i], entryPrice[i]) ) != null  )
							{
								trade.POSITION_SIZE = calculatePositionSize();
								trade.status = Constants.STATUS_OPEN;
								tradeManager[i].setTrade(trade);
								tradePlaced[i] = true;
								logger.info(contracts[i].m_symbol +"." + contracts[i].m_currency  + " trade placed ");
								logger.info(trade.toString());
							}
						}
					}
					// reset counter
					high1min[i]=0;
					low1min[i]=999;
				}
			}
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


	private void createContract(String symbol, String type, String exchange, String currency, String act, double entry, double target )
	{
		Contract contract = contracts[totalSymbolIndex];
    	contract.m_conId = connectionId++;
    	contract.m_symbol = symbol;
        contract.m_secType = type;
        contract.m_exchange = exchange;
        contract.m_currency = currency;
        
        entryPrice[totalSymbolIndex] = entry;
        targetPrice[totalSymbolIndex] = target;
        action[totalSymbolIndex] = act;
        
        tradeManager[totalSymbolIndex] = new StretchTradeManager(m_client,contract, totalSymbolIndex, logger );
        
        // increase one for next
        totalSymbolIndex++;
	}
	

	private int calculatePositionSize()
	{
		return 1;
	}
}
