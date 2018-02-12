package tao.trading.strategy;

import java.util.logging.Logger;

import tao.trading.Constants;
import tao.trading.strategy.tm.TradeManagerBasic;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class CloseAllTrades implements EWrapper
{
	protected EClientSocket m_client = new EClientSocket(this);
	protected TradeManager2 tradeManager;	
	int connectionId = 100;
	private static Logger logger;
	
	public static void main(String[] args)
	{
		String account = args[0];
		int port = new Integer(args[1]);
		int conn_id = new Integer(args[2]);
    	CloseAllTrades open = new CloseAllTrades( account, port, conn_id );
		open.run();

	}

	public void run() {
		
		m_client.reqAccountUpdates(true,Constants.ACCOUNT);		
		m_client.reqAccountUpdates(false,Constants.ACCOUNT);	
		m_client.reqOpenOrders();	
		try{
			Thread.sleep(10000);	// running time in milli seconds
		}
		catch( Exception e)
		{
		}
		
        m_client.eDisconnect();
		
	}

	public CloseAllTrades(String account, int port, int conn_id )
	{
        m_client.eConnect("127.0.0.1" , port, conn_id);
        m_client.setServerLogLevel(5);

        logger = Logger.getLogger("CAT_" + account);

        
        //tradeManager = new TradeManager2(m_client, 499, null );
        tradeManager = new TradeManagerBasic(account, m_client, null, 499);
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
		System.out.println("Oper Order:" + orderId + "  Contract " + contract.m_symbol + "." + contract.m_currency + " orderState: " + orderState );
		tradeManager.cancelOrder(orderId);
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
		// no need to re-enter
	}

	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{

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
		System.out.println("Contract:" + contract.m_symbol + "." + contract.m_currency + " position size: " + position );
		//// close each position
		if ( position != 0 )
		{
			contract.m_exchange = "IDEALPRO";
			tradeManager.closePosition( contract, position>0?Constants.ACTION_SELL:Constants.ACTION_BUY, Math.abs(position));
		}

	}

	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}


}
