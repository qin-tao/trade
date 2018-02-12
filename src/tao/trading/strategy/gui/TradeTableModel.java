package tao.trading.strategy.gui;

import java.util.ArrayList;

import javax.swing.table.AbstractTableModel;

import tao.trading.Trade;
import tao.trading.strategy.SFTest;
import tao.trading.strategy.tm.Strategy;
import tao.trading.strategy.tm.TradeManagerInf;


class TradeTableModel extends AbstractTableModel {
	
	int COL_MARKED = 0;
	int COL_SYMBOL = 1;
	int COL_ACTION = 2;
	int COL_DETECT_TIME = 3;
	int COL_SET_LIMIT1 = 4;
	int COL_LIMIT1_PRICE = 5;
	int COL_LIMIT1_SIZE = 6;
	int COL_ENTRY_TIME = 7;
	int COL_ENTRY_PRICE = 8;
	int COL_PROFIT_LOSS = 9;
	
	
	
    private String[] columnNames = {"Marked",
                                    "Symbol",
                                    "Action",
                                    "Detect Time",
                                    "Set Limit1",
                                    "Limit1 Price",
                                    "Limit1 Size",
                                    "Entry Time",
                                    "Entry Price",
                                    "Profit/Loss"
                                 //   "# of Entry",
                                    };
    /*
    private Object[][] data = {
    {"Kathy", "Smith",
     "Snowboarding", new Integer(5), new Boolean(false)},
    {"John", "Doe",
     "Rowing", new Integer(3), new Boolean(true)},
    {"Sue", "Black",
     "Knitting", new Integer(2), new Boolean(false)},
    {"Jane", "White",
     "Speed reading", new Integer(20), new Boolean(true)},
    {"Joe", "Brown",
     "Pool", new Integer(10), new Boolean(false)}
    };*/

    public Strategy stragety;
    public SFTest sftest;
    ArrayList<TradeManagerInf> tradeManagers;; 
    
    public TradeTableModel( SFTest sf, Strategy s )
    {
    	stragety = s;
    	//tradeManagers = tms;
    	sftest = sf;
    }

    public void setTradeModelContent( ArrayList<TradeManagerInf> tms)
    {
        fireTableDataChanged();
    }
    
	public int getColumnCount() {
      //  return columnNames.length;
		return 10;
    }

    public int getRowCount() {
        //return data.length;
    	tradeManagers = stragety.getAllTraderManagerWithTrade();
 
    	if ( tradeManagers != null )
    		return tradeManagers.size();
    	else 
    		return 0;
    }

    public String getColumnName(int col) {
        return columnNames[col];
    	
    }

    public Object getValueAt(int row, int col) {
     
    	tradeManagers = stragety.getAllTraderManagerWithTrade();
    	if (( tradeManagers == null ) || (tradeManagers.size() == 0 ))
    		return null;
    				
    	if ( col == COL_MARKED )
    		return tradeManagers.get(row).getTrade().enabled;
    	else if ( col == COL_SYMBOL )
    		return tradeManagers.get(row).getTrade().symbol;
    	else if ( col == COL_ACTION )
    		return tradeManagers.get(row).getTrade().action;
    	else if ( col == COL_DETECT_TIME )
    		return (tradeManagers.get(row).getTrade().detectTime==null)?"":tradeManagers.get(row).getTrade().detectTime;
    	else if ( col == COL_SET_LIMIT1 )
    		return tradeManagers.get(row).getTrade().limit1Placed;
    	else if ( col == 5 )
    		return tradeManagers.get(row).getTrade().limitPrice1;
    	else if ( col == 6 )
    		return tradeManagers.get(row).getTrade().limitPos1;
    	else if ( col == 7 )
    		return (tradeManagers.get(row).getTrade().entryTime==null)?"":tradeManagers.get(row).getTrade().entryTime;
    	else if ( col == 8 )
    		return (tradeManagers.get(row).getTrade().entryPrice==0)?"":new Double(tradeManagers.get(row).getTrade().entryPrice).toString();
    	else if ( col == 9 )
    		return (tradeManagers.get(row).getTrade().getProfitLoss()==0)?"":new Integer(tradeManagers.get(row).getTrade().getProfitLoss()).toString();
    	else
    		return "";
 

    
    
    }

    
    /*
     * JTable uses this method to determine the default renderer/
     * editor for each cell.  If we didn't implement this method,
     * then the last column would contain text ("true"/"false"),
     * rather than a check box.
     */
    public Class getColumnClass(int c) {
        return getValueAt(0, c).getClass();
    }

    /*
     * Don't need to implement this method unless your table's
     * editable.
     */
    public boolean isCellEditable(int row, int col) {
        //Note that the data/cell address is constant,
        //no matter where the cell appears onscreen.
        /*if (col < 2) {
            return false;
        } else {
            return true;
        }*/
    	
        if (( col == 0 ) || ( col == 4 ) || (col == 5 ) || ( col == 6))
        	return true;
        else
        	return false;
    }

    /*
     * Don't need to implement this method unless your table's
     * data can change.
     */
    public void setValueAt(Object value, int row, int col) {

    	if ( col == COL_MARKED )
       		tradeManagers.get(row).getTrade().enabled = !tradeManagers.get(row).getTrade().enabled;
    	else if ( col == COL_LIMIT1_PRICE) 
    		tradeManagers.get(row).getTrade().limitPrice1 = (Double)value;
    	else if ( col == COL_LIMIT1_SIZE) 
    		tradeManagers.get(row).getTrade().limitPos1 = (int)(new Double(value.toString()) * tradeManagers.get(row).getTrade().POSITION_SIZE);
    	else if ( col == COL_SET_LIMIT1 )
    	{	
    		if (Boolean.TRUE.equals(value))
    			tradeManagers.get(row).enterLimitPositionMulti( tradeManagers.get(row).getTrade().limitPos1, tradeManagers.get(row).getTrade().limitPrice1 );
    	}
    }

	public void deleteTrade(int row)
	{
	   //data[row][0] = "";
		if ( sftest != null )
		{
			System.out.println("delete trade:" + stragety + (String)tradeManagers.get(row).getTrade().symbol);
			tradeManagers.get(row).removeTrade();
		}
        fireTableDataChanged();
		
	}

	public void addTrade(Trade t)
	{
		//if ( sftest != null )
			//sftest.addTrade("MC", t);

		//tradeManagers.add(t);
        fireTableDataChanged();
	}
	
}
