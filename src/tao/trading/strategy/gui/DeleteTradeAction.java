package tao.trading.strategy.gui;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JTable;

public class DeleteTradeAction extends AbstractAction
{
	 JTable tradeTable;
	 TradeTableModel tradeTableModel;
	 
    public DeleteTradeAction(JTable tradeTable, TradeTableModel ttm)
	{
		super();
		this.tradeTable = tradeTable;
		this.tradeTableModel = ttm;
	}


	@Override
    public void actionPerformed(ActionEvent e) {
         //Do something
   	   int[] selection = tradeTable.getSelectedRows();
   	   for (int i = 0; i < selection.length; i++) {
   		   tradeTableModel.deleteTrade(selection[i]);
    	}

    }

}
