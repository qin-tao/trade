package tao.trading.strategy.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import tao.trading.Constants;
import tao.trading.Trade;
import tao.trading.strategy.SFTest;
import tao.trading.strategy.tm.Strategy;
import tao.trading.strategy.tm.TradeManagerInf;
 
public class SFGUI extends JPanel implements Runnable, ActionListener { 
    private JTable table, table2, table3;
    private TradeTableModel tradeTableModel, tradeTableModel2, tradeTableModel3;
    private JCheckBox rowCheck;
    private JCheckBox columnCheck;
    private JCheckBox cellCheck;
    private ButtonGroup buttonGroup;
    private JTextArea output;
    private SFGUI newContentPane;
    private SFTest sftest;
    
    public static void main(String[] args) {
        //Schedule a job for the event-dispatching thread:
        //creating and showing this application's GUI.
    	/*
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
            	createAndShowGUI();

            }
        });*/
    	
    	SFGUI sfgui = new SFGUI(null);
    	sfgui.run();
    	sfgui.setTableContent();
    }

	@Override
	public void run()
	{
        UIManager.put("swing.boldMetal", Boolean.FALSE); 
        
        //Create and set up the window.
        JFrame frame = new JFrame("Trade List");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
        //Create and set up the content pane.
        setOpaque(true); //content panes must be opaque
        frame.setContentPane(this);
 
        //Display the window.
        frame.pack();
        frame.setVisible(true);

        //setTableContent();
	}
    
    
    
    public SFGUI( SFTest s) {
        super();
        this.sftest = s;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        
        add(new JLabel("RV"));	// Title
        
        for ( int i = 0; i< sftest.NUM_STRATEGY; i++)
        	if ("RV".equals(sftest.strageties[i].STRATEGY_NAME))
        		tradeTableModel = new TradeTableModel(sftest, sftest.strageties[i]);
        
        table = new JTable(tradeTableModel);
        table.setPreferredScrollableViewportSize(new Dimension(1200,250));
        table.setFillsViewportHeight(true);
        //table.getSelectionModel().addListSelectionListener(new RowListener());
        //table.getColumnModel().getSelectionModel().addListSelectionListener(new ColumnListener());
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
       // table.getModel().addTableModelListener(new TradeTableModelListener(table));
        table.getInputMap().put(KeyStroke.getKeyStroke("DELETE"),
                "deleteRow");
        table.getActionMap().put("deleteRow", new DeleteTradeAction(table, tradeTableModel));
        /*
        table.getActionMap().put("deleteRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                 //Do something
            	System.out.println("delete row");
            }
       });*/
        //table.getColumnModel().getColumn(0).setPreferredWidth(27);
        table.getColumnModel().getColumn(0).setPreferredWidth(1);
        table.getColumnModel().getColumn(4).setPreferredWidth(1);
        add(new JScrollPane(table));

        /*
        add(new JLabel("Diverage"));
        tradeTableModel2 = new TradeTableModel("RV",sftest);
        
        table2 = new JTable(tradeTableModel2);
        table2.setPreferredScrollableViewportSize(new Dimension(1024, 100));
        table2.setFillsViewportHeight(true);
        table2.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table2.getInputMap().put(KeyStroke.getKeyStroke("DELETE"),
                "deleteRow");
        table2.getActionMap().put("deleteRow", new DeleteTradeAction2(table2, tradeTableModel2));
        table2.getColumnModel().getColumn(0).setPreferredWidth(1);
        table2.getColumnModel().getColumn(3).setPreferredWidth(200);
        add(new JScrollPane(table2));
       	*/
        
        add(new JLabel("MC"));
        for ( int i = 0; i< sftest.NUM_STRATEGY; i++)
        	if ("MC".equals(sftest.strageties[i].STRATEGY_NAME))
        		tradeTableModel3 = new TradeTableModel(sftest, sftest.strageties[i]);
        //tradeTableModel3 = new TradeTableModel(sftest, new Strategy("PV", Constants.TEST_MODE));
        
        table3 = new JTable(tradeTableModel3);
        table3.setPreferredScrollableViewportSize(new Dimension(1024, 250));
        table3.setFillsViewportHeight(true);
        table3.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table3.getInputMap().put(KeyStroke.getKeyStroke("DELETE"),
                "deleteRow");
        table3.getActionMap().put("deleteRow", new DeleteTradeAction(table3, tradeTableModel3));
        table3.getColumnModel().getColumn(0).setPreferredWidth(1);
        table3.getColumnModel().getColumn(4).setPreferredWidth(1);
        add(new JScrollPane(table3));
        
        
        JPanel jp = new JPanel();
        jp.setLayout( new FlowLayout());
        JTextField addTradeText = new JTextField("                                                       ");
        jp.add(addTradeText);
        
        JButton addTradeButton = new JButton("Add");
        addTradeButton.addActionListener(new AddTradeAction(addTradeText,tradeTableModel3)); 
        jp.add(addTradeButton);
        add(jp);
        /*
        
        add(new JLabel("Selection Mode"));
        buttonGroup = new ButtonGroup();
        addRadio("Multiple Interval Selection").setSelected(true);
        addRadio("Single Selection");
        addRadio("Single Interval Selection");
 
        add(new JLabel("Selection Options"));
        rowCheck = addCheckBox("Row Selection");
        rowCheck.setSelected(true);
        columnCheck = addCheckBox("Column Selection");
        cellCheck = addCheckBox("Cell Selection");
        cellCheck.setEnabled(false);
 		*/
        output = new JTextArea(5, 40);
        output.setEditable(false);
        add(new JScrollPane(output));
    }

    public void updateContent(){
    	
        tradeTableModel.fireTableDataChanged();
        //tradeTableModel2.fireTableDataChanged();
        tradeTableModel3.fireTableDataChanged();
    }
    
    
    
    private JCheckBox addCheckBox(String text) {
        JCheckBox checkBox = new JCheckBox(text);
        checkBox.addActionListener(this);
        add(checkBox);
        return checkBox;
    }
 
    private JRadioButton addRadio(String text) {
        JRadioButton b = new JRadioButton(text);
        b.addActionListener(this);
        buttonGroup.add(b);
        add(b);
        return b;
    }
    
    private JButton addButton( String text )
    {
    	JButton b2 = new JButton("Add");
        b2.setVerticalTextPosition(AbstractButton.BOTTOM);
        b2.setHorizontalTextPosition(AbstractButton.CENTER);
        b2.setMnemonic(KeyEvent.VK_M);
        return b2;
    }
 
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();
        //Cell selection is disabled in Multiple Interval Selection
        //mode. The enabled state of cellCheck is a convenient flag
        //for this status.
        System.out.println(command);
        if ("Row Selection" == command) {
            table.setRowSelectionAllowed(rowCheck.isSelected());
            //In MIS mode, column selection allowed must be the
            //opposite of row selection allowed.
            if (!cellCheck.isEnabled()) {
                table.setColumnSelectionAllowed(!rowCheck.isSelected());
            }
        } else if ("Column Selection" == command) {
            table.setColumnSelectionAllowed(columnCheck.isSelected());
            //In MIS mode, row selection allowed must be the
            //opposite of column selection allowed.
            if (!cellCheck.isEnabled()) {
                table.setRowSelectionAllowed(!columnCheck.isSelected());
            }
        } else if ("Cell Selection" == command) {
            table.setCellSelectionEnabled(cellCheck.isSelected());
        } else if ("Multiple Interval Selection" == command) { 
            table.setSelectionMode(
                    ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            //If cell selection is on, turn it off.
            if (cellCheck.isSelected()) {
                cellCheck.setSelected(false);
                table.setCellSelectionEnabled(false);
            }
            //And don't let it be turned back on.
            cellCheck.setEnabled(false);
        } else if ("Single Interval Selection" == command) {
            table.setSelectionMode(
                    ListSelectionModel.SINGLE_INTERVAL_SELECTION);
            //Cell selection is ok in this mode.
            cellCheck.setEnabled(true);
        } else if ("Single Selection" == command) {
            table.setSelectionMode(
                    ListSelectionModel.SINGLE_SELECTION);
            //Cell selection is ok in this mode.
            cellCheck.setEnabled(true);
        }
 
        //Update checkboxes to reflect selection mode side effects.
        rowCheck.setSelected(table.getRowSelectionAllowed());
        columnCheck.setSelected(table.getColumnSelectionAllowed());
        if (cellCheck.isEnabled()) {
            cellCheck.setSelected(table.getCellSelectionEnabled());
        }
    }
 
    private void outputSelection() {
        output.append(String.format("Lead: %d, %d. ",
                    table.getSelectionModel().getLeadSelectionIndex(),
                    table.getColumnModel().getSelectionModel().
                        getLeadSelectionIndex()));
        output.append("Rows:");
        for (int c : table.getSelectedRows()) {
            output.append(String.format(" %d", c));
        }
        output.append(". Columns:");
        for (int c : table.getSelectedColumns()) {
            output.append(String.format(" %d", c));
        }
        output.append(".\n");
    }
 
    private class RowListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent event) {
            if (event.getValueIsAdjusting()) {
                return;
            }
            output.append("ROW SELECTION EVENT. ");
            outputSelection();
        }
    }
 
    private class ColumnListener implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent event) {
            if (event.getValueIsAdjusting()) {
                return;
            }
            output.append("COLUMN SELECTION EVENT. ");
            outputSelection();
        }
    }
 
 
    /**
     * Create the GUI and show it.  For thread safety,
     * this method should be invoked from the
     * event-dispatching thread.
     */
    /*
    public static void createAndShowGUI() {
        //Disable boldface controls.
        UIManager.put("swing.boldMetal", Boolean.FALSE); 
 
        //Create and set up the window.
        JFrame frame = new JFrame("Trade List");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
        //Create and set up the content pane.
        SFGUI newContentPane = new SFGUI();
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);
 
        //Display the window.
        frame.pack();
        frame.setVisible(true);

        newContentPane.setTableContent();

    }*/
    
    public void setTableContent()
    {
    	/*
    	ArrayList<TradeManagerInf> trades = new ArrayList<TradeManagerInf>();
    	for ( int i = 0; i <10; i++)
    		{
    			TradeManagerInf t = new TradeManagerInf("USD.JPY" + i);
    			t.action = "BUY" + i;
    			trades.add(t);
    		}

    	tradeTableModel.setTradeModelContent(trades);*/
    }
 
    private void display(String d)
    {
    	System.out.println(d);
    }
    
    private void initColumnSizes(JTable table) {
    	TradeTableModel model = (TradeTableModel)table.getModel();
        TableColumn column = null;
        Component comp = null;
        int headerWidth = 0;
        int cellWidth = 0;
        Integer[] longValues = {15,30,30,30,30,30,30};//model.longValues;
        TableCellRenderer headerRenderer =
            table.getTableHeader().getDefaultRenderer();
 
        for (int i = 0; i < 7; i++) {
            column = table.getColumnModel().getColumn(i);
 /*
            comp = headerRenderer.getTableCellRendererComponent(
                                 null, column.getHeaderValue(),
                                 false, false, 0, 0);
            headerWidth = comp.getPreferredSize().width;
 
            comp = table.getDefaultRenderer(model.getColumnClass(i)).
                             getTableCellRendererComponent(
                                 table, longValues[i],
                                 false, false, 0, i);
            cellWidth = comp.getPreferredSize().width;
 
                System.out.println("Initializing width of column "
                                   + i + ". "
                                   + "headerWidth = " + headerWidth
                                   + "; cellWidth = " + cellWidth);
 */
            column.setPreferredWidth((int)longValues[i]);//*Math.max(headerWidth, cellWidth)*/);
        }
    }
    
    
    public void updateTableContent()
    {
    	/*
    	ArrayList<TradeManagerInf> trades = new ArrayList<TradeManagerInf>();
    	for ( int i = 0; i <10; i++)
    		{
    			TradeManagerInf t = new TradeManagerInf("USD.JPY" + i);
    			t.action = "BUY" + i;
    			trades.add(t);
    		}

    	tradeTableModel.setTradeModelContent(trades);*/
    }

    public void setTable1Content( ArrayList<TradeManagerInf> tradeManagers)
    {
    	tradeTableModel.setTradeModelContent(tradeManagers);
    }

    public void setTable2Content(ArrayList<TradeManagerInf> tradeManagers)
    {
    	tradeTableModel2.setTradeModelContent(tradeManagers);
    }

    public void setTable3Content(ArrayList<TradeManagerInf> tradeManagers)
    {
    	tradeTableModel3.setTradeModelContent(tradeManagers);
    }

 }
 
 


