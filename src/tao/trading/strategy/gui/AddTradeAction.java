package tao.trading.strategy.gui;

import java.awt.event.ActionEvent;
import java.util.Date;
import java.util.StringTokenizer;

import javax.swing.AbstractAction;
import javax.swing.JTextField;

import tao.trading.Constants;
import tao.trading.Trade;
import tao.trading.strategy.SFTest;

public class AddTradeAction extends AbstractAction
{
	JTextField addTradeText;
	TradeTableModel tradeTableModel;

	public AddTradeAction(JTextField addTradeText, TradeTableModel ttm )
	{
		super();
		this.addTradeText = addTradeText;
		this.tradeTableModel = ttm;
	}

	@Override
    public void actionPerformed(ActionEvent e) {
         //Do something
		String input = addTradeText.getText().trim();
		
		StringTokenizer st = new StringTokenizer(input);
		
		if ( st.countTokens() == 2)
		{
			String symbol = st.nextToken().toUpperCase();
			String action = st.nextToken().toUpperCase();

			Trade t = new Trade(symbol);
			t.action = action;
			t.detectTime = SFTest.IBDataFormatter.format(new Date());
			t.status = Constants.STATUS_DETECTED;
			t.type = Constants.TRADE_RM;
			t.pullBackStartTime = SFTest.IBDataFormatter.format(new Date());
			tradeTableModel.addTrade(t);
		}

    }

}
