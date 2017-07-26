import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import java.io.*; 
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class Client { 

	public static DataOutputStream outBuffer;
	public static BufferedReader inBuffer;
	public static DataInputStream inputBuffer;
	public boolean isLoggedIn = false;
	public static String name = "";
	
    public static void main(String args[])
    { 
        try {
			if (args.length != 2)
			{
			    System.out.println("Usage: Client <Server IP> <Server Port>");
			    System.exit(1);
			}

			// Initialize a client socket connection to the server
			Socket clientSocket = new Socket(args[0], Integer.parseInt(args[1])); 

			// Initialize input and an output stream for the connection(s)
			outBuffer = 
			  new DataOutputStream(clientSocket.getOutputStream()); 
			inBuffer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); 
			
			InputStream input = clientSocket.getInputStream();
	        inputBuffer = new DataInputStream(input);

			// Initialize user input stream
			String line; 
			BufferedReader inFromUser = 
			new BufferedReader(new InputStreamReader(System.in)); 
			
			//Login process or create new account. If incorrect password, it will loop
			boolean login = false;
			while(!login)
			{
				System.out.print("Enter your Username: ");
				line = inFromUser.readLine(); 
				name = line;
				System.out.print("Enter your Password: ");
				line = line+":"+inFromUser.readLine();
			
				outBuffer.writeBytes("Tlogin:"+line+"\n");
				String response = (String) inBuffer.readLine();
				
				if(response.matches("[T]*server:Incorrect password entered"))
				{
					System.out.println("Incorrect password entered");
					continue;
				}
				else
				{
					System.out.println("Successfully logged in");
					printCommands();
					login = true;
				}
			}        

			//create thread for listening from server
			new ListenFromServer().start();       
			Thread.sleep(50);
			
			// Get user input and send to the server
			// Display the echo meesage from the server
			System.out.print("> ");
			line = inFromUser.readLine(); 
			while (!line.equals("/logout"))
			{
			    //Check what user inputted
				checkCommand(line);
			    System.out.print("> ");
			    line = inFromUser.readLine(); 
			}

			//let server know you're logging off
			try {
				outBuffer.writeBytes("Tlogout\n");
			} catch (IOException e) {
				System.out.println("unable to contact server");
			} 
			// Close the socket
			clientSocket.close();
			System.exit(0);
		} catch (Exception e) {
			System.out.println("closed program");
			System.exit(0);
		}
    }
    
    //checks what command the user inputted, and take appropriate action
    public static void checkCommand(String line)
    {
    	//ask server for list of all online users
    	if(line.equals("/list"))
    	{
    		try {
				outBuffer.writeBytes("Tlist\n");
			} catch (IOException e) {
			} 
    	}
    	//log out from server
    	else if(line.equals("/logout"))
    	{
    		try {
				outBuffer.writeBytes("Tlogout\n");
				System.out.println("[System] You have successfully logged out.");
			} catch (IOException e) {
			}
    	}
    	//add a friend
    	else if(line.matches("\\/add [a-zA-Z0-9]+"))
    	{
    		String[] temp = line.split(" ");
    		String name = temp[1];
    		try {
				outBuffer.writeBytes("Tadd:"+name+"\n");
			} catch (IOException e) {
			}
    	}
    	//remove a friend
    	else if(line.matches("\\/remove [a-zA-Z0-9 ]+"))
    	{
    		String[] temp = line.split(" ");
    		String name = temp[1];
    		try {
				outBuffer.writeBytes("Tremove:"+name+"\n");
			} catch (IOException e) {
			}
    	}
    	//list online friends
    	else if(line.equals("/flist"))
    	{
    		try {
				outBuffer.writeBytes("Tflist\n");
			} catch (IOException e) {
			} 
    	}
    	//create a private chat room
    	else if(line.equals("/create"))
    	{
    		try {
				outBuffer.writeBytes("Tcreate\n");
			} catch (IOException e) {
			} 
    	}
    	//leave a private chat room
    	else if(line.equals("/leave"))
    	{
    		try {
				outBuffer.writeBytes("Tleave\n");
			} catch (IOException e) {
			}
    	}
    	//invite a friend to a private chat room
    	else if(line.matches("\\/invite [a-zA-Z0-9 ]+"))
    	{
    		String[] temp = line.split(" ");
    		String name = temp[1];
    		try {
				outBuffer.writeBytes("Tinvite:"+name+"\n");
			} catch (IOException e) {
			}
    	}
    	//show your chat history
    	else if(line.equals("/log"))
    	{
    		try {
				outBuffer.writeBytes("Tlog\n");
			} catch (IOException e) {
			}
    	}
    	//list your current files in your current directory
    	else if(line.equals("/files"))
    	{
    		//get the list of all file names in the current directory and print it out
    		System.out.println("Listing out files and folders in current directory: ");
    		System.out.println("---------------------------------------------------");
	    	File f = new File(".");
            File[] listOfFiles = f.listFiles();
            for (File file :  listOfFiles)
            {
        		if (file.isDirectory()) {
    				System.out.print("[folder]:");
    			} else {
    				System.out.print("<file>:");
    			}
				System.out.println(file.getName());
            }
    	}
    	//list all files stored on the server's repository for each user
    	else if(line.equals("/repo"))
    	{
    		try {
				outBuffer.writeBytes("Trepo\n");
			} catch (IOException e) {
			}
    	}
    	//upload a file to the server and store it in your repository
    	else if(line.matches("\\/upload .+"))
    	{
    		String[] temp = line.split(" ");
    		String fName = temp[1];
    		File f = new File(fName);
    		byte[] b = null;
    		
    		//Make sure file exists in current directory, and if so, send it
    		if (f.exists() && !f.isDirectory())
    		{
    			System.out.println("Sending "+fName+" to server..");
    			try {
					b = Files.readAllBytes(f.toPath());
					outBuffer.writeBytes(fName);
					outBuffer.flush();
					Files.copy(f.toPath(), outBuffer);
				} catch (IOException e) {
					System.out.println("Problem sending the file. Aborted.");
				}
	    	}
    		else
    		{
    			System.out.println("File does not exist or is a directory");
    		}
    	}
    	//get a file from the server
    	else if(line.matches("\\/get .+"))
    	{
    		String[] temp =  line.split(" ");
    		String fName = temp[1];
    		try {
				outBuffer.writeBytes("Tget:"+fName+"\n");
			} catch (IOException e) {
			}
    	}
    	//else it's a regular text message
    	else
    	{
    		try {
				outBuffer.writeBytes("Tmsg:["+name+"] "+line + '\n');
			} catch (IOException e) {
			} 
    	}
    }
    
    	//prints out all the commands the user is able to use to the screen
  		public static void printCommands()
  		{
  			//PRINT OUT COMMANDS FOR USER ONTO SCREEN
  	        System.out.println("       	 Welcome to chat server!"); 
  	        System.out.println("--------------------Available Commands:--------------------");
  	        System.out.println("<any msg>       - type anything and press enter to send");
  	        System.out.println("/logout         -  logout from chat");
  	        System.out.println("/list           -  list out all current online users");
  	        System.out.println("/add <friend>   - add a user to your friend list");
  	        System.out.println("/flist          - list out all current online friends");
  	        System.out.println("/create         - create a new private chat room");
  	        System.out.println("/invite         - invite a friend to your private chat");
  	        System.out.println("/leave          - leave your current private chat room");
  	        System.out.println("/log            - show your chat history across all chat rooms");
  	        System.out.println("/files          - lists all files in your current directory");
  	        System.out.println("/upload <file>  - upload a file to your repository on the server");
  	        System.out.println("/repo			- lists all users and their files on their repository");
  	        System.out.println("/get <file>  	- download a file from online repository if it exists");
  		}
    
    //Listens for incoming message from server in a separate thread
	static class ListenFromServer extends Thread {
		public void run() {
			while(true) {
				try {
					String line = (String) inBuffer.readLine();
					//server sent us a text message from someone else
					if(line.matches("[T]*msg:.+"))
					{
						String tmp = line;
						String[] tokens = tmp.split(":");
						String[] tokens2 = tokens[1].split("\n");
						String message = tokens2[0];
						System.out.println(message);
						System.out.print("> ");
					}
					//server sending us a list of online users
					else if(line.matches("[T]*list:([a-zA-Z0-9]+:)+"))
					{
						String theList = parseOnlineUsers(line);
						System.out.println("Current Online Users: ");
						System.out.println(theList);
						System.out.print("> ");
					}
					//server sending us a list of online friends
					else if(line.matches("[T]*flist:([a-zA-Z0-9]+:)+"))
					{
						String theList = parseOnlineUsers(line);
						System.out.println("Current Online Friends: ");
						if(theList.equals(""))
						{
							System.out.println("None");
						}
						else
						{
							System.out.println(theList);
						}
						
						System.out.print("> ");
					}
					//getting your chat history from server
					else if(line.matches("[T]*log:(.+:)+"))
					{
						String theLog = parseChatLog(line);
						System.out.println("--------------Your Chat History----------------");
						System.out.println(theLog);
						System.out.print("> ");
					}
					//when receiving an error or success message from server
					else if(line.matches("[T]*server:.+"))
					{
						String[] temp = line.split(":");
						String msg = temp[1];
						System.out.println("Server: "+ msg);
						System.out.print("> ");
					}
					//when a friend logs on
					else if(line.matches("[T]*online:.+"))
					{
						String[] temp = line.split(":");
						System.out.println("[Status] "+temp[1]);
						System.out.print("> ");
					}
					//when receiving file repository information from server
					else if(line.matches("[T]*repo:.*"))
					{
						System.out.println("----------Online File Repository-------------");
						printRepository(line);
						System.out.print("> ");
					}
					//downloading file from server
					//If command sent was 'get <filename>', then we save the file into client's current direc
					//else it's a file being uploaded by a user
					//"\n*[a-zA-Z0-9.\t ]+\n(.|\n)*"
					else if(line.matches("(.|\n)*[a-zA-Z0-9]+\\.[a-zA-Z0-9]+(.|\n)+"))
					{
						String[] temp = line.split(".txt");
						String fileName = temp[0]+".txt";
						
						String contents = "";
						if(temp.length > 1)
						{
							contents = temp[1];
						}
						
						System.out.println("File name: "+fileName);
						System.out.println("File contents: "+contents);			

						// Convert the string to a
						   // byte array.
						File f = new File(fileName);

						byte data[] = contents.getBytes();		    
						Path p = null;
						    
						try {
						    //file name only
						    f = new File(fileName);
						    if(f.createNewFile())
						    {
						        System.out.println(fileName+" Created in current directory");
						        p = Paths.get(fileName);
						         
						    }else System.out.println(fileName+" already exists in current directory");

						}catch (IOException e) {
						}
						   
						if(p != null)
						{
							try (OutputStream out = new BufferedOutputStream(
								Files.newOutputStream(p, CREATE, APPEND))) {
								out.write(data, 0, data.length);
							}catch (IOException x) {
						    	System.err.println(x);
							}	 
						}	       
					   System.out.print("> ");
					}
				}
				catch(IOException e) {
				}
				
			}
			
		}
		
		//parses out the online user string sent by server
		public static String parseOnlineUsers(String list)
		{
			String theList = "";
			String[] temp = list.split(":");
			
			if(temp.length > 1)
			{
				for(int i = 1; i<temp.length; i++)
				{
					theList += temp[i] + ", ";
				}
			}
			return theList;
		}
		
		//parses out the chat log string sent by server
		public static String parseChatLog(String list)
		{
			String theList = "";
			String[] temp = list.split(":");
			
			if(temp.length > 1)
			{
				for(int i = 1; i<temp.length; i++)
				{
					theList += temp[i] + "\n";
				}
			}
			return theList;
		}
		
		//parses out the online file repository info sent by server and prints it out
		public static void printRepository(String list)
		{
			String[] temp = list.split(":");
			if(temp.length > 1)
			{
				for(int i = 1; i<temp.length; i++)
				{
					String[] tmp = temp[i].split("\t");
					if(tmp.length == 1)
					{
						System.out.println(temp[i]+" (no files)");
					}
					else
					{
						System.out.println(temp[i]);
					}
				}
			}
		}
	}
} 
