package tao.trading;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

public class SendCommand
{
	static String[] Symbols = {
		"EUR.USD", 
		"EUR.JPY",
		"EUR.CAD",
		"EUR.GBP",
		"EUR.CHF",
		"EUR.NZD",
		"EUR.AUD",
		"GBP.AUD",
		"GBP.NZD",
		"GBP.USD",
		"GBP.CAD",
		"GBP.CHF",
		"GBP.JPY",
		"CHF.JPY",
		"USD.JPY",
		"USD.CAD",
		"USD.CHF",
		"CAD.JPY",
		"AUD.USD",
		"AUD.JPY",
		"AUD.NZD",
		"USD.DKK",
		"USD.SGD",
		"SPY.USD"
	};

	
	public static void main(String[] args)
	{
	    int numOfArgs = args.length;
		if ( numOfArgs < 2 ){
			System.out.println("create AUD.USD 1 93.46 buy" );
			System.out.println("create_other SPY 1 93.46 buy" );
			System.out.println("plain_order AUD.USD 1 93.46 buy" );
			System.out.println("plain_order_market AUD.USD 1 buy" );
			System.out.println("plain_stop AUD.USD 1 94.27 sell" );
			System.out.println("add_position AUD.USD 1 93.46" );
			//System.out.println("update AUD.USD 1 93.47 buy" );
			System.out.println("stop AUD.USD 94.56");
			System.out.println("cancel_order AUD.USD 94.56");
			//System.out.println("create_limit_2 AUD.USD 1 93.46 1 93.48 buy");
			System.out.println("target AUD.USD 1 93.46");
			System.out.println("remove AUD.USD EUR.JPY");
			System.out.println("target_on_pullback AUD.USD EUR.JPY");
			System.out.println("set_position AUD.USD 0.25" );
			return;
		}
	    
		String cmdFile = args[0];
		String cmdstr = args[1];
		String symbol = null;
	    String action = null;
	    String quantity = null;
		String price = null;
		double dquantity = 0;
		double dprice = 0;
	    String quantity2 = null;
		String price2 = null;
		double dquantity2 = 0;
		double dprice2 = 0;
		String entryMode = null;

		String COMMAND_FILE = "./" + cmdFile;

		try{
			CommandAction ca = null;
			for(CommandAction a: CommandAction.values()){
		        if (a.toString().equalsIgnoreCase(cmdstr)){
		        	ca = a;
		        	break;
		        }
		    }
			if ( ca == null ){
				System.out.println(cmdstr + ": not recognized command");
				return;
			}
	
			if (( ca == CommandAction.remove ) || ( ca == CommandAction.target_on_pullback)){
				validateNumOfParm ( ca, numOfArgs, 3 );
		    	for ( int i = 2; i < numOfArgs; i++){
		    		String sy = args[i].toUpperCase();
					if ( symbol == null )
						symbol = sy;
					else
						symbol += " " + sy;
		    	}
			}
			else if ( ca == CommandAction.set_position ){
				validateNumOfParm ( ca, numOfArgs, 4 );
				symbol = args[2];
			    quantity = args[3];
			    
			    dquantity = Double.valueOf(quantity.trim()).doubleValue();
			}
			else if ( ca == CommandAction.target ){ 
				validateNumOfParm ( ca, numOfArgs, 5 );
				symbol = args[2];
			    quantity = args[3];
				price = args[4];
		    	validateSymbol(symbol);
			   
		        dquantity = Double.valueOf(quantity.trim()).doubleValue();
				dprice = Double.valueOf(price.trim()).doubleValue();
				if (dquantity > 1){
				  	System.out.println("Warning: " + symbol + " quantity is greater than 1");
				}
			}
			else if ( ca == CommandAction.plain_order_market ){ 
				validateNumOfParm ( ca, numOfArgs, 5 );
				symbol = args[2];
			    quantity = args[3];
				action = args[4];
		    	validateSymbol(symbol);
				validateAction(action);
			   
				dquantity = Double.valueOf(quantity.trim()).doubleValue();
				if (dquantity > 1){
					System.out.println("Warning: " + symbol + " quantity is greater than 1");
				}
			}
			else if ((ca ==CommandAction.create)||( ca == CommandAction.create_other )||( ca == CommandAction.plain_order) || ( ca == CommandAction.plain_stop) || ( ca == CommandAction.update )){ 
				validateNumOfParm ( ca, numOfArgs, 6 );
				symbol = args[2];
			    quantity = args[3];
				price = args[4];
				action = args[5];
		    	validateSymbol(symbol);
				validateAction(action);
		      
				dquantity = Double.valueOf(quantity.trim()).doubleValue();
				dprice = Double.valueOf(price.trim()).doubleValue();
			}
			else if ( ca == CommandAction.add_position ){
				validateNumOfParm ( ca, numOfArgs, 5 );
				symbol = args[2];
			    quantity = args[3];
				price = args[4];
		    	validateSymbol(symbol);
			   
		    	dquantity = Double.valueOf(quantity.trim()).doubleValue();
				dprice = Double.valueOf(price.trim()).doubleValue();
			}
			else if ( ca == CommandAction.create_limit_2 ){
				validateNumOfParm ( ca, numOfArgs, 8 );

				symbol = args[2];
			    quantity = args[3];
				price = args[4];
			    quantity2 = args[5];
				price2 = args[6];
				action = args[7];
		    	validateSymbol(symbol);
				validateAction(action);
			   
		    	dquantity = Double.valueOf(quantity.trim()).doubleValue();
				dprice = Double.valueOf(price.trim()).doubleValue();
				dquantity2 = Double.valueOf(quantity2.trim()).doubleValue();
				dprice2 = Double.valueOf(price2.trim()).doubleValue();
			}
			else if (( ca == CommandAction.stop ) || ( ca == CommandAction.cancel_order )){
				validateNumOfParm ( ca, numOfArgs, 4 );

				symbol = args[2];
				price = args[3];
		    	validateSymbol(symbol);
		        dprice = Double.valueOf(price.trim()).doubleValue();
			}
			
			if ( action != null )
				action = action.toUpperCase();
			
			Command cmd = new Command( ca, symbol.toUpperCase(), action, dquantity, dprice, dquantity2, dprice2);
			cmd.entryMode = entryMode;
		    
		    ObjectOutputStream output = new ObjectOutputStream( new FileOutputStream( COMMAND_FILE ) );
		   	output.writeObject( cmd);
			output.close();
		
		}catch ( Exception e ){
		   	System.out.println("Exception occured send command: " + cmdstr + " " + e.getMessage());
		    e.printStackTrace();
		} 

	}
	
	
	static void validateAction ( String action ) throws Exception{
		if (!( action.equalsIgnoreCase(Constants.ACTION_BUY) || action.equalsIgnoreCase(Constants.ACTION_SELL)))
			throw new Exception(action + ": action not defined");
	}

	
	static void validateSymbol ( String symbol ) throws Exception{
    	boolean match = false;
		for( int j = 0; j < Symbols.length; j++){
	        if (Symbols[j].equalsIgnoreCase(symbol)){
	        	match = true;
	        	break;
	        }
	    }
		if (!match)
			throw new Exception(symbol + ": symbol for place order not defined");
	}

	static void validateNumOfParm ( CommandAction ca, int numOfArgs, int expectedNum ) throws Exception{
		if ( numOfArgs != expectedNum )
			throw new Exception(ca.name() + " does not have right number of parameters");
	}

}
