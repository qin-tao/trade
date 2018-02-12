/*
 * Main.java
 *
 */
package TradeClientDashBoard;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {

    // This method is called to start the application
	
    public static void main (String args[]) {
		//100 "EUR", "CASH", "SMART", "USD" 0.002
		//200 "QQQQ", "STK", "SMART", "USD" 0.30
    	String id = args[0];
    	String symbol = args[1];
    	String type = args[2];
    	String market = args[3];
    	String currency = args[4];
        SampleFrame sampleFrame = new SampleFrame(id, symbol, type, market, currency);
        sampleFrame.setVisible(true);
    }

    static public void inform( final Component parent, final String str) {
        if( SwingUtilities.isEventDispatchThread() ) {
        	showMsg( parent, str, JOptionPane.INFORMATION_MESSAGE);
        }
        else {
            SwingUtilities.invokeLater( new Runnable() {
				public void run() {
					showMsg( parent, str, JOptionPane.INFORMATION_MESSAGE);
				}
			});
        }
    }

    static private void showMsg( Component parent, String str, int type) {    	
        // this function pops up a dlg box displaying a message
        JOptionPane.showMessageDialog( parent, str, "IB Java Test Client", type);
    }
}