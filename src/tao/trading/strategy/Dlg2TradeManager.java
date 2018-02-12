package tao.trading.strategy;

import java.util.logging.Logger;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;

public class Dlg2TradeManager extends TradeManager
{
	public Dlg2TradeManager(EClientSocket client, Contract contract, int symbolIndex, Logger logger)
	{
		super( client, contract, symbolIndex, logger );
	}

	
	
	

}
