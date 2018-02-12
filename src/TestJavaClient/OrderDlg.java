/*
 * OrderDlg.java
 *
 */
package TestJavaClient;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import tao.trading.Constants;
import tao.trading.Indicator;
import tao.trading.QuoteData;
import tao.trading.Request;
import tao.trading.SessionData;
import tao.trading.Trade;
import tao.trading.strategy.DlgTradeManager;

import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.EClientSocket;
import com.ib.client.EWrapper;
import com.ib.client.Execution;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.UnderComp;

public class OrderDlg extends JDialog implements EWrapper{
	final static String ALL_GENERIC_TICK_TAGS = "mdoff,100,101,104,105,106,107,165,221,225,233,236,258";
    final static int OPERATION_INSERT = 0;
    final static int OPERATION_UPDATE = 1;
    final static int OPERATION_DELETE = 2;

    final static int SIDE_ASK = 0;
    final static int SIDE_BID = 1;

    public boolean 		m_rc;
    public int 			m_id = 0;
    public String       m_backfillEndTime;
    public String		m_backfillDuration;
    public String       m_barSizeSetting;
    public int          m_useRTH;
    public int          m_formatDate;
    public int          m_marketDepthRows;
    public String       m_whatToShow;
    public Contract 	m_contract = new Contract();
    public Order 		m_order = new Order();
    public UnderComp	m_underComp = new UnderComp();
    public int          m_exerciseAction;
    public int          m_exerciseQuantity;
    public int          m_override;

    private JTextField	m_Id = new JTextField( "999");
    private JTextField	m_BackfillEndTime = new JTextField(22);
    private JTextField	m_BackfillDuration = new JTextField( "1 M");
    private JTextField  m_BarSizeSetting = new JTextField("1 day");
    private JTextField	m_UseRTH = new JTextField( "1");
    private JTextField	m_FormatDate = new JTextField( "1");
    private JTextField	m_WhatToShow = new JTextField( "TRADES");
    private JTextField 	m_conId = new JTextField("100");
    private JTextField 	m_symbol = new JTextField( "EUR");
    private JTextField 	m_secType = new JTextField( "CASH");
    private JTextField 	m_expiry = new JTextField();
    private JTextField 	m_strike = new JTextField( "0");
    private JTextField 	m_right = new JTextField();
    private JTextField 	m_multiplier = new JTextField("");
    private JTextField 	m_exchange = new JTextField( "IDEALPRO");
    private JTextField 	m_primaryExch = new JTextField( "" );
    private JTextField 	m_currency = new JTextField("USD");
    private JTextField 	m_localSymbol = new JTextField();
    private JTextField 	m_distance = new JTextField("0.1");
    private JTextField 	m_stop = new JTextField("");
    private JTextField 	m_includeExpired = new JTextField("0");
    private JTextField 	m_action = new JTextField( "BUY");
    private JTextField 	m_totalQuantity = new JTextField( "10");
    private JTextField 	m_orderType = new JTextField( "LMT");
    private JTextField 	m_lmtPrice = new JTextField( "40");
    private JTextField 	m_auxPrice = new JTextField( "0");
    private JTextField 	m_goodAfterTime = new JTextField();
    private JTextField 	m_goodTillDate = new JTextField();
    private JTextField 	m_marketDepthRowTextField = new JTextField( "20");
    private JTextField  m_genericTicksTextField = new JTextField(ALL_GENERIC_TICK_TAGS);
    private JCheckBox   m_snapshotMktDataTextField = new JCheckBox("Snapshot", false);
    private JTextField m_exerciseActionTextField = new JTextField("1");
    private JTextField m_exerciseQuantityTextField = new JTextField("1");
    private JTextField m_overrideTextField = new JTextField("0");

    private JButton	    m_sharesAlloc = new JButton("FA Allocation Info...");
    private JButton 	m_comboLegs = new JButton( "Combo Legs");
    private JButton 	m_btnUnderComp = new JButton( "Delta Neutral");
    private JButton 	m_btnAlgoParams = new JButton( "Algo Params");
    private JButton	    m_start = new JButton("Start");
    private JButton	    m_buy_buttom = new JButton("Buy");
    private JButton 	m_sell_top = new JButton("Sell");
    private JButton 	m_reversal = new JButton("Reversal");
    
    private JButton 	m_ok = new JButton( "OK");
    private JButton 	m_cancel = new JButton( "Cancel");
    private SampleFrame m_parent;

    private String      m_faGroup;
    private String      m_faProfile;
    private String      m_faMethod;
    private String      m_faPercentage;
	public  String      m_genericTicks;
	public  boolean     m_snapshotMktData;      

    private static final int COL1_WIDTH = 50 ;
    private static final int COL2_WIDTH = 150 - COL1_WIDTH ;
    public void faGroup(String s) { m_faGroup = s;}
    public void faProfile(String s) { m_faProfile = s;}
    public void faMethod(String s) { m_faMethod = s;}
    public void faPercentage(String s) { m_faPercentage = s; }
    public int m_clientId;    
    public String m_title;    
    
    EClientSocket m_client = new EClientSocket(this);
    IBTextPanel m_messagePanel = new IBTextPanel("Messages", false);

    // my variables
	protected Indicator indicator = new Indicator();
//	protected Contract contract;
	protected SimpleDateFormat IBDataFormatter = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
	protected DlgTradeManager tradeManager;
	String filename = null;
	Writer output = null;
	String symbol;
	int id_1min_history = 11;
	int id_60min_history = 12;
	int id_realtime_bar = 13;
	Vector<QuoteData> quotes1m = new Vector<QuoteData>(200);
	Vector<QuoteData> quotes60m = new Vector<QuoteData>(200);
	double high60min = 0;
	double high1min = 0;
	double low60min = 999;
	double low1min = 999;
	boolean selltop = false;
	boolean buybuttom = false;
	String lastAction;
	int stopLossTradeId = 0;

	Request request = null;
	int orderId = 10000;
    boolean history60m = false;
    boolean history1m = false;
    String reqAccountUpdatesAction;

    // this is to maintain the flow
    SessionData sessionData=new SessionData();
    String task;
 	Logger logger = Logger.getLogger("OrderDlg");
   
 	// Moving averages
	double[] ema5,ema20,sma200_1m, sma5_1m, sma20_1m;
	double distance;
	boolean firstrealtimebar = false;

    
    private static void addGBComponent(IBGridBagPanel panel, Component comp,
                                       GridBagConstraints gbc, int weightx, int gridwidth)
    {
      gbc.weightx = weightx;
      gbc.gridwidth = gridwidth;
      panel.setConstraints(comp, gbc);
      panel.add(comp, gbc);
    }

    public OrderDlg( SampleFrame owner, String id, String symbol, String secType, String exchange, String currency, String distance ) {
        super( owner, true);
 
        m_conId = new JTextField(id);
        m_symbol = new JTextField( symbol);
        m_secType = new JTextField( secType );
        m_exchange = new JTextField( exchange );
        m_currency = new JTextField(currency);
        m_distance = new JTextField(distance);
        
        m_parent = owner;
 
        java.awt.GridBagConstraints gbc = new java.awt.GridBagConstraints() ;
        gbc.fill = GridBagConstraints.BOTH ;
        gbc.anchor = GridBagConstraints.CENTER ;
        gbc.weighty = 100 ;
        gbc.fill = GridBagConstraints.BOTH ;
        gbc.gridheight = 1 ;
        // create id panel
        /*
        IBGridBagPanel pId = new IBGridBagPanel();
        pId.setBorder( BorderFactory.createTitledBorder( "Message Id") );

        addGBComponent(pId, new JLabel( "Id"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pId, m_Id, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
		*/
        // create contract panel
        IBGridBagPanel pContractDetails = new IBGridBagPanel();
        pContractDetails.setBorder( BorderFactory.createTitledBorder( "Contract Info") );
        addGBComponent(pContractDetails, new JLabel( "Contract Id"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_conId, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Symbol"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_symbol, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Security Type"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_secType, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Expiry"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_expiry, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Strike"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_strike, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Put/Call"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_right, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
//        addGBComponent(pContractDetails, new JLabel( "Option Multiplier"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
//        addGBComponent(pContractDetails, m_multiplier, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Exchange"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_exchange, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Primary Exchange"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_primaryExch, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Currency"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_currency, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Distance"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_distance, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pContractDetails, new JLabel( "Stop"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pContractDetails, m_stop, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
//        addGBComponent(pContractDetails, new JLabel( "Local Symbol"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
//        addGBComponent(pContractDetails, m_localSymbol, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
//        addGBComponent(pContractDetails, new JLabel( "Include Expired"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
//        addGBComponent(pContractDetails, m_includeExpired, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);

        // create order panel
        IBGridBagPanel pOrderDetails = new IBGridBagPanel();
        pOrderDetails.setBorder( BorderFactory.createTitledBorder( "Order Info") );
        addGBComponent(pOrderDetails, new JLabel( "Action"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_action, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pOrderDetails, new JLabel( "Total Order Size"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_totalQuantity, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pOrderDetails, new JLabel( "Order Type"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_orderType, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pOrderDetails, new JLabel( "Lmt Price"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_lmtPrice, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pOrderDetails, new JLabel( "Aux Price"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_auxPrice, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pOrderDetails, new JLabel( "Good After Time"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_goodAfterTime, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);
        addGBComponent(pOrderDetails, new JLabel( "Good Till Date"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE );
        addGBComponent(pOrderDetails, m_goodTillDate, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER);

        // create marketDepth panel
        /*
        IBGridBagPanel pMarketDepth = new IBGridBagPanel();
        pMarketDepth.setBorder( BorderFactory.createTitledBorder( "Market Depth") );
        addGBComponent(pMarketDepth, new JLabel( "Number of Rows"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pMarketDepth, m_marketDepthRowTextField, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;

        // create marketData panel
        IBGridBagPanel pMarketData = new IBGridBagPanel();
        pMarketData.setBorder( BorderFactory.createTitledBorder( "Market Data") );
        addGBComponent(pMarketData, new JLabel( "Generic Tick Tags"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pMarketData, m_genericTicksTextField, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pMarketData, m_snapshotMktDataTextField, gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;

        // create options exercise panel
        /*
        IBGridBagPanel pOptionsExercise= new IBGridBagPanel();
        pOptionsExercise.setBorder( BorderFactory.createTitledBorder( "Options Exercise") );
        addGBComponent(pOptionsExercise, new JLabel( "Action (1 or 2)"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pOptionsExercise, m_exerciseActionTextField, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pOptionsExercise, new JLabel( "Number of Contracts"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pOptionsExercise, m_exerciseQuantityTextField, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pOptionsExercise, new JLabel( "Override (0 or 1)"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pOptionsExercise, m_overrideTextField, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;

        // create historical data panel
        IBGridBagPanel pBackfill = new IBGridBagPanel();
        pBackfill.setBorder( BorderFactory.createTitledBorder( "Historical Data Query") );
        GregorianCalendar gc = new GregorianCalendar();
        gc.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateTime = "" +
            gc.get(Calendar.YEAR) +
            pad(gc.get(Calendar.MONTH) + 1) +
            pad(gc.get(Calendar.DAY_OF_MONTH)) + " " +
            pad(gc.get(Calendar.HOUR_OF_DAY)) + ":" +
            pad(gc.get(Calendar.MINUTE)) + ":" +
            pad(gc.get(Calendar.SECOND)) + " " +
            gc.getTimeZone().getDisplayName( false, TimeZone.SHORT);

        m_BackfillEndTime.setText(dateTime);
        addGBComponent(pBackfill, new JLabel( "End Date/Time"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pBackfill, m_BackfillEndTime, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pBackfill, new JLabel( "Duration"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pBackfill, m_BackfillDuration, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pBackfill, new JLabel( "Bar Size Setting (1 to 11)"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pBackfill, m_BarSizeSetting, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pBackfill, new JLabel( "What to Show"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pBackfill, m_WhatToShow, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pBackfill, new JLabel( "Regular Trading Hours (1 or 0)"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pBackfill, m_UseRTH, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
        addGBComponent(pBackfill, new JLabel( "Date Format Style (1 or 2)"), gbc, COL1_WIDTH, GridBagConstraints.RELATIVE) ;
        addGBComponent(pBackfill, m_FormatDate, gbc, COL2_WIDTH, GridBagConstraints.REMAINDER) ;
*/
        // create mid Panel
        JPanel pMidPanel = new JPanel();
        pMidPanel.setLayout( new BoxLayout( pMidPanel, BoxLayout.Y_AXIS) );
        pMidPanel.add( pContractDetails, BorderLayout.CENTER);
        pMidPanel.add( pOrderDetails, BorderLayout.CENTER);
  //      pMidPanel.add( pMarketDepth, BorderLayout.CENTER);
  //      pMidPanel.add( pMarketData, BorderLayout.CENTER);
  //      pMidPanel.add( pOptionsExercise, BorderLayout.CENTER);
  //      pMidPanel.add( pBackfill, BorderLayout.CENTER);
        
        // create order button panel
        JPanel pOrderButtonPanel = new JPanel();
        /*
        pOrderButtonPanel.add( m_sharesAlloc);
        pOrderButtonPanel.add( m_comboLegs);
        pOrderButtonPanel.add( m_btnUnderComp);
        pOrderButtonPanel.add( m_btnAlgoParams);
        */
        pOrderButtonPanel.add( m_start);
        pOrderButtonPanel.add( m_reversal);
        pOrderButtonPanel.add( m_buy_buttom);
        pOrderButtonPanel.add( m_sell_top);
        
        pMidPanel.add( pOrderButtonPanel, BorderLayout.CENTER);


        // create button panel
        /*
        JPanel buttonPanel = new JPanel();
        buttonPanel.add( m_ok);
        buttonPanel.add( m_cancel);
*/
        // create action listeners
        m_start.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onStart();
            }
        });

        m_reversal.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onReversal();
            }
        });

        m_sell_top.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onSellTop();
            }
        });

        m_buy_buttom.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onBuyBottom();
            }
        });

        m_sharesAlloc.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onSharesAlloc();
            }
        });

        m_comboLegs.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onAddComboLegs();
            }
        });
        m_btnUnderComp.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onBtnUnderComp();
            }
        });
        m_btnAlgoParams.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onBtnAlgoParams();
            }
        });
        m_ok.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onOk();
            }
        });
        m_cancel.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e) {
                onCancel();
            }
        });

        // create top panel
        JPanel topPanel = new JPanel();
        topPanel.setLayout( new BoxLayout( topPanel, BoxLayout.Y_AXIS) );
        //topPanel.add( pId);
        topPanel.add( pMidPanel);
        topPanel.add( m_messagePanel);

        // create dlg box
        getContentPane().add( topPanel, BorderLayout.CENTER);
        //getContentPane().add( buttonPanel, BorderLayout.SOUTH);

        pack();
        
    }

    private static String pad( int val) {
        return val < 10 ? "0" + val : "" + val;
    }

    void onSharesAlloc() {
        if ( !m_parent.m_bIsFAAccount ) {
            return;
        }

        FAAllocationInfoDlg dlg = new FAAllocationInfoDlg(this);

        // show the combo leg dialog
        dlg.setVisible(true);
    }

    void onAddComboLegs() {
    	
        ComboLegDlg comboLegDlg = new ComboLegDlg(
        		m_contract.m_comboLegs, m_exchange.getText(), this);
        
        // show the combo leg dialog
        comboLegDlg.setVisible( true);
    }
    
    void onBtnUnderComp() {
    	
        UnderCompDlg underCompDlg = new UnderCompDlg(m_underComp, this);
        
        // show delta neutral dialog
        underCompDlg.setVisible( true);
        if (underCompDlg.ok()) {
        	m_contract.m_underComp = m_underComp;
        }
        else if (underCompDlg.reset()) {
        	m_contract.m_underComp = null;
        }
    }
    
    void onBtnAlgoParams() {
    	
        AlgoParamsDlg algoParamsDlg = new AlgoParamsDlg(m_order, this);
        
        // show delta neutral dialog
        algoParamsDlg.setVisible( true);
    }

    void onOk() {
        m_rc = false;

        try {
            // set id
            m_id = Integer.parseInt( m_Id.getText() );

            // set contract fields
            m_contract.m_conId = ParseInt(m_conId.getText(), 0);
            m_contract.m_symbol = m_symbol.getText();
            m_contract.m_secType = m_secType.getText();
            m_contract.m_expiry = m_expiry.getText();
           	m_contract.m_strike = ParseDouble(m_strike.getText(), 0.0);
            m_contract.m_right = m_right.getText();
            m_contract.m_multiplier = m_multiplier.getText();
            m_contract.m_exchange = m_exchange.getText();
            m_contract.m_primaryExch = m_primaryExch.getText();
            m_contract.m_currency = m_currency.getText();
            m_contract.m_localSymbol = m_localSymbol.getText();
            try {
            	int includeExpired = Integer.parseInt(m_includeExpired.getText());
            	m_contract.m_includeExpired = (includeExpired == 1);
            }
            catch (NumberFormatException ex) {
            	m_contract.m_includeExpired = false;
            }
            
            // set order fields
            m_order.m_action = m_action.getText();
            m_order.m_totalQuantity = Integer.parseInt( m_totalQuantity.getText() );
            m_order.m_orderType = m_orderType.getText();
            m_order.m_lmtPrice = Double.parseDouble( m_lmtPrice.getText() );
            m_order.m_auxPrice = Double.parseDouble( m_auxPrice.getText() );
            m_order.m_goodAfterTime = m_goodAfterTime.getText();
            m_order.m_goodTillDate = m_goodTillDate.getText();

            m_order.m_faGroup = m_faGroup;
            m_order.m_faProfile = m_faProfile;
            m_order.m_faMethod = m_faMethod;
            m_order.m_faPercentage = m_faPercentage;

            // set historical data fields
            m_backfillEndTime = m_BackfillEndTime.getText();
            m_backfillDuration = m_BackfillDuration.getText();
            m_barSizeSetting = m_BarSizeSetting.getText();
            m_useRTH = Integer.parseInt( m_UseRTH.getText() );
            m_whatToShow = m_WhatToShow.getText();
            m_formatDate = Integer.parseInt( m_FormatDate.getText() );
            m_exerciseAction = Integer.parseInt( m_exerciseActionTextField.getText() );
            m_exerciseQuantity = Integer.parseInt( m_exerciseQuantityTextField.getText() );
            m_override = Integer.parseInt( m_overrideTextField.getText() );;

            // set market depth rows
            m_marketDepthRows = Integer.parseInt( m_marketDepthRowTextField.getText() );
            m_genericTicks = m_genericTicksTextField.getText();
            m_snapshotMktData = m_snapshotMktDataTextField.isSelected();
        }
        catch( Exception e) {
            Main.inform( this, "Error - " + e);
            return;
        }

        m_rc = true;
        setVisible( false);
    }

    void onCancel() {
        m_rc = false;
        setVisible( false);
    }

    public void show() {
        m_rc = false;
        super.show();
    }

    void setIdAtLeast( int id) {
        try {
            // set id field to at least id
            int curId = Integer.parseInt( m_Id.getText() );
            if( curId < id) {
                m_Id.setText( String.valueOf( id) );
            }
        }
        catch( Exception e) {
            Main.inform( this, "Error - " + e);
        }
    }
    
    private static int ParseInt(String text, int defValue) {
    	try {
    		return Integer.parseInt(text);
    	}
    	catch (NumberFormatException e) {
    		return defValue;
    	}
    }
    
    private static double ParseDouble(String text, double defValue) {
    	try {
    		return Double.parseDouble(text);
    	}
    	catch (NumberFormatException e) {
    		return defValue;
    	}
    }
    
    void onStart()
    {
    	getInputFields();
    	if ( m_client.isConnected())
    		m_client.eDisconnect();
    	m_messagePanel.clear();
    	
    	m_client.eConnect( "127.0.0.1", 7496, m_contract.m_conId);
        if (m_client.isConnected())
        {
            m_messagePanel.add("Connected to Tws server version " +
                       m_client.serverVersion() + " at " +
                       m_client.TwsConnectionTime());
        	
            quotes1m = new Vector<QuoteData>(200);
        
            tradeManager = new DlgTradeManager(m_client, m_contract, m_contract.m_conId, logger);
            m_client.reqCurrentTime();
        }
        else
        {
        	m_messagePanel.add("Can not connect to Tws station");
        }

 	
    }

    void onStop()
    {
    	m_client.eDisconnect();
    	quotes1m = null;
    	quotes60m = null;

     }

    
    
    void onReversal()
    {
    	onPlaceTrade(Constants.ACTION_REVERSAL);
    }
    
    void onSellTop()
    {
    	onPlaceTrade(Constants.ACTION_SELL);
    }
    
    void onBuyBottom()
    {
    	onPlaceTrade(Constants.ACTION_BUY);
    }


    void onPlaceTrade( String action )
    {
   		if ( tradeManager.getTrade() == null )
   		{
       		Trade tt = new Trade();
       		tt.action = action;
       		tt.status = Constants.STATUS_OPEN;
       		tt.reEnter = 3;
       		tt.POSITION_SIZE = calculatePositionSize();
    		tt.entryQuotes.add(quotes1m.lastElement());
       		
    		tradeManager.setTrade(tt);
        	m_messagePanel.add( action + " trade placed");
   		}
   		else
   		{
        	m_messagePanel.add("Trade already exists");
   		}
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
    	m_messagePanel.add("Start:" + currentTime);
    	logger.info("Started:" + currentTime);

    	//getPositionInPortfolio();
    	m_client.reqHistoricalData( id_1min_history, m_contract,  
    			currentTime, //"20091015 16:26:44",
                "36000 S", "1 min", "MIDPOINT", 1, 1);               

    	/*
    	m_client.reqHistoricalData( id_60min_history, m_contract,  
    			currentTime, //"20091015 16:26:44",
           		"20 D", "1 hour", "MIDPOINT", 1, 1);               
    	 */
		
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
		logger.info(m_symbol + " id:" + reqId + " Date:" + date + " Open:" + open + " High" + high + " Low" + low + " Close:" + close + " Volumn:" + volume + " count:" + count + 
				" WAP:" + WAP + " hasGaps:" + hasGaps);
		
		if (date.indexOf("finished-") == -1)
		{
			if (reqId == id_1min_history)
			{
				QuoteData data = new QuoteData(date, open, high, low, close, volume, count, WAP, hasGaps);
				quotes1m.add(data);
			}
		}
		else
		{	
			if (reqId == id_1min_history)
			{
				m_messagePanel.add(symbol + " started");
				logger.info(symbol + " started");
				
				sma200_1m = indicator.calculateSMA(quotes1m, 200);
				sma20_1m = indicator.calculateSMA(quotes1m, 20);

				m_client.reqRealTimeBars(id_realtime_bar, m_contract, 60, "MIDPOINT", true);

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
		System.out.println("Open order: orderId" + orderId + " Contract" + contract + " Order" + order + " OrderState" + order);
		
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
		/*logger.fine("orderStatus: " + " orderId " + orderId + " status " + status + " filled " + filled 
				+ " remaining " + remaining + " avgFillPrice " + avgFillPrice + " permId " + permId + " parentId " + parentId
				+ " lastFillPrice " + lastFillPrice + " clientId " + clientId + " whyHeld " + whyHeld);
		*/
		/*  use program stops 
		if ( "Filled".equals(status))
		{
			logger.info("position filled: orderId" + orderId + " filled " + filled + " avgFillPrice " + avgFillPrice);
			tradeManager.checkStopTriggered(orderId);
		}*/
	}

	
	@Override
	public void realtimeBar(int reqId, long time, double open, double high, double low, double close,
			long volume, double wap, int count)
	{
		String timeStr = IBDataFormatter.format(new Date(time*1000));
		String hr = timeStr.substring(9, 11);
		String min = timeStr.substring(12, 14);
		String sec = timeStr.substring(15,17);

		logger.info(timeStr + ": high-" + high1min + " low-" + low1min );
	
		// the goal is once the current bar does not make new high
		if (high > high1min)
			high1min = high; 
		if ( low < low1min )
			low1min = low;

		try{
			
			Trade trade = tradeManager.getTrade();
			if ( trade != null )
			{
				//logger.info("trade exists");
				QuoteData data = new QuoteData(timeStr, open, high, low, close, 0, 0, wap, false);

				if (trade.status.equals(Constants.STATUS_PLACED))
				{
					// this is to check whether it needs any "stop and reversal"
					//tradeManager.addTradeStopData(data);
					tradeManager.trackTradeExit(data,quotes1m, sma200_1m);
				}
				else if ((firstrealtimebar == true) && (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) || trade.status.equals(Constants.STATUS_REVERSAL)))
				{
					tradeManager.trackTradeEntry(data);
				}

			}
	
			if (sec.equals("00"))
			{
				firstrealtimebar = true;
				// at the minute
				logger.info(timeStr + ": high-" + high1min + " low-" + low1min );
				QuoteData data = new QuoteData(timeStr, 0.0, high1min, low1min, close, 0, 0, wap, false);
				quotes1m.add(data);
			
				if ( quotes1m.size() > 500)
					quotes1m.removeElementAt(0); // to prevent it running too long

				sma200_1m = indicator.calculateSMA(quotes1m, 200);
				sma20_1m = indicator.calculateSMA(quotes1m, 20);

				trade = tradeManager.getTrade();
				if ( trade != null )
				{
					logger.info("trade exists");
					// trade already exists
					if (trade.status.equals(Constants.STATUS_STOPPEDOUT) || trade.status.equals(Constants.STATUS_OPEN) || trade.status.equals(Constants.STATUS_REVERSAL))
					{
						logger.info("add entry data");
						tradeManager.addTradeEntryData(data);
					}
				}
			
				high1min=0;
				low1min=999;

			}
		} 
		catch ( Exception e )
		{
			e.printStackTrace();
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
		//logger.info("updateAccountTime:" + timeStamp);// TODO Auto-generated method stub
		
	}
	@Override
	public void updateAccountValue(String key, String value, String currency, String accountName)
	{
		// TODO Auto-generated method stub
		//logger.info("key:" + key + " value:" + value + " currency:" + currency + " accountName:" + accountName);
		
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
		System.out.println("Contact:" + contract + " position:" + position + " marketPrice:" + marketPrice + " marketValue:"+ marketValue
				+ " averageCost:"+averageCost + " unreliazedPNL:" + unrealizedPNL + " realizedPNL" + realizedPNL + " accountName:" + accountName);

		// only intersted in current contract
		/*
		if ( isThisContract(contract) )
		{
			if ( SessionData.TASK_CLOSE_EXISTING_SHORT_POSITION.equals( sessionData.getTask()))
			{
				if ( position < 0 )
				{
					// THIS IS A SHORT POSITION, NEEDS TO BE CLOSED BEFORE BUY
					orderManager.placeMktOrder("BUY",1);

				}
			}
			else if ( SessionData.TASK_CLOSE_EXISTING_LONG_POSITION.equals( sessionData.getTask()))
			{
				if ( position > 0 )
				{
					// THIS IS A SHORT POSITION, NEEDS TO BE CLOSED BEFORE BUY
					orderManager.placeMktOrder("SELL",1);

				}
			}
				
		}
		*/
		
	}
	@Override
	public void connectionClosed()
	{
		// TODO Auto-generated method stub
		
	}
	@Override
	public void error(Exception e)
	{
		// TODO Auto-generated method stub
		e.printStackTrace();
		
	}
	@Override
	public void error(String str)
	{
		// TODO Auto-generated method stub
		logger.severe("str");
		
	}
	@Override
	public void error(int id, int errorCode, String errorMsg)
	{
		// TODO Auto-generated method stub
		logger.severe(id + " " + errorCode + " " + errorMsg);
		if ( errorMsg.indexOf("Connectivity between IB and TWS has been restored - data maintained") != -1)
			m_client.reqCurrentTime();
			
	}
	
	

    void getInputFields() {

    	// set id
            m_id = Integer.parseInt( m_Id.getText() );

            // set contract fields
            m_contract.m_conId = ParseInt(m_conId.getText(), 0);
            m_contract.m_symbol = m_symbol.getText();
            m_contract.m_secType = m_secType.getText();
          //  m_contract.m_expiry = m_expiry.getText();
          // 	m_contract.m_strike = ParseDouble(m_strike.getText(), 0.0);
          //  m_contract.m_right = m_right.getText();
          //  m_contract.m_multiplier = m_multiplier.getText();
            m_contract.m_exchange = m_exchange.getText();
         //   m_contract.m_primaryExch = m_primaryExch.getText();
            m_contract.m_currency = m_currency.getText();
         //   m_contract.m_localSymbol = m_localSymbol.getText();

            symbol = m_contract.m_symbol + "." + m_contract.m_currency;
            distance = new Double(m_distance.getText());
            /*
            try {
            	int includeExpired = Integer.parseInt(m_includeExpired.getText());
            	m_contract.m_includeExpired = (includeExpired == 1);
            }
            catch (NumberFormatException ex) {
            	m_contract.m_includeExpired = false;
            }*/
            
            // set order fields
            m_order.m_action = m_action.getText();
            m_order.m_totalQuantity = Integer.parseInt( m_totalQuantity.getText() );
            m_order.m_orderType = m_orderType.getText();
            m_order.m_lmtPrice = Double.parseDouble( m_lmtPrice.getText() );
            m_order.m_auxPrice = Double.parseDouble( m_auxPrice.getText() );
            m_order.m_goodAfterTime = m_goodAfterTime.getText();
            m_order.m_goodTillDate = m_goodTillDate.getText();

            m_order.m_faGroup = m_faGroup;
            m_order.m_faProfile = m_faProfile;
            m_order.m_faMethod = m_faMethod;
            m_order.m_faPercentage = m_faPercentage;

            // set historical data fields
            /*
            m_backfillEndTime = m_BackfillEndTime.getText();
            m_backfillDuration = m_BackfillDuration.getText();
            m_barSizeSetting = m_BarSizeSetting.getText();
            m_useRTH = Integer.parseInt( m_UseRTH.getText() );
            m_whatToShow = m_WhatToShow.getText();
            m_formatDate = Integer.parseInt( m_FormatDate.getText() );
            m_exerciseAction = Integer.parseInt( m_exerciseActionTextField.getText() );
            m_exerciseQuantity = Integer.parseInt( m_exerciseQuantityTextField.getText() );
            m_override = Integer.parseInt( m_overrideTextField.getText() );;

            // set market depth rows
            m_marketDepthRows = Integer.parseInt( m_marketDepthRowTextField.getText() );
            m_genericTicks = m_genericTicksTextField.getText();
            m_snapshotMktData = m_snapshotMktDataTextField.isSelected();
            */

     }
    
/*    
    void buy( int position, double stopprice)
    {
    	// close existing position
    	sessionData.setTask(SessionData.TASK_CLOSE_EXISTING_SHORT_POSITION);
		m_client.reqAccountUpdates(true,"");

		// close any open orders
    	// TODO:need to close those stop orders
		//m_client.reqAllOpenOrders();
		//Order order = orderManager.createMktOrder(Constants.ACTION_BUY,sessionData.getBuyPosition(), sessionData.stopPrice());
		
		// next should go to orderstatus
    	
	
		orderManager.placeMktOrder("BUY",1 );
		orderManager.placeStopOrder("SELL",1,stopprice);
		m_client.reqAllOpenOrders();
	
	
    }
*/
    boolean isThisContract(Contract contract)
    {
    	// compare m_contract with this contract
        if (! m_contract.m_symbol.equalsIgnoreCase(contract.m_symbol))
        	return false;
        
        if (! m_contract.m_secType.equalsIgnoreCase(contract.m_secType))
        	return false;

        //if (! m_contract.m_exchange.equalsIgnoreCase(contract.m_exchange))
        //	return false;

        if (! m_contract.m_currency.equalsIgnoreCase(contract.m_currency))
        	return false;
        
        return true;
    }
    
	double calStopPrice(Vector<QuoteData> quotes, String action)
	{
		if ( Constants.ACTION_BUY.equals(action))
		{
			// calculate stop sell, the lowest of the past 15 bars
			int size = quotes.size();
			double sellstop = 999;
			for ( int i = size - 15; i < size; i++)
			{
				double low = ((QuoteData)quotes.elementAt(i)).close;
				if ( low < sellstop)
					sellstop = low;
			}
			return sellstop;
		}
			
		if ( Constants.ACTION_SELL.equals(action))
		{
			// calculate stop sell, the lowest of the past 15 bars
			int size = quotes.size();
			double buytop = 0;
			for ( int i = size - 15; i < size; i++)
			{
				double high = ((QuoteData)quotes.elementAt(i)).close;
				if ( high > buytop )
					buytop = high;
			}
			return buytop;
		}

		return 0.0;
		
	}
	
	void getPositionInPortfolio()
	{
		//updatePortfolioCallBack = new CallBackAction(CallBackAction.GET_POSITION_SIZE);
    	sessionData.setTask(SessionData.TASK_GET_POSITION_BEFORE_BUY);
		m_client.reqAccountUpdates(true,"");

	}

	

	int calculatePositionSize()
	{
		return 1;
		
	}
	

}
