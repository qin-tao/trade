package tao.trading.strategy.tm;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

import tao.trading.QuoteData;
import tao.trading.strategy.TradeManager2;

public class TradeManagerBasic extends TradeManager2  {


	public TradeManagerBasic(String account, EClientSocket client, Contract contract, int clientId) {

		super(account,client,contract,clientId);
	}
	
	@Override
	public void process(int req_id, QuoteData data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void checkOrderFilled(int orderId, int filled) {
		// TODO Auto-generated method stub
		
	}

}
