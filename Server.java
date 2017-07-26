import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.util.*;

import static java.nio.file.StandardOpenOption.*;

public class Server implements Runnable {
	private final int port;
	private ServerSocketChannel ssc;
	private Selector selector;
	private ByteBuffer buf = ByteBuffer.allocate(256);
	
	//Player info
	private User[] allUsers = {null};	//save all users in this array
	private User[] connectedUsers = {null};	//save all connected users (online) here

	Server(int port) throws IOException {
		this.port = port;
		this.ssc = ServerSocketChannel.open();
		this.ssc.socket().bind(new InetSocketAddress(port));
		this.ssc.configureBlocking(false);
		this.selector = Selector.open();

		this.ssc.register(selector, SelectionKey.OP_ACCEPT);
	}

	@Override public void run() {
			System.out.println("Server starting on port " + this.port);

			Iterator<SelectionKey> iter;
			SelectionKey key = null;
			while(this.ssc.isOpen()) {
				try{
					selector.select();
					iter=this.selector.selectedKeys().iterator();
					while(iter.hasNext()) {
						key = iter.next();
						iter.remove();

						if(key.isAcceptable()) this.handleAccept(key);
						if(key.isReadable()) this.handleRead(key);
					}
				}
				//if a user disconnects without logging out first (such as client crashing)
				catch(IOException e){
					System.out.println("A user was disconnected without logging out first");
					key.cancel();
					
					String keyString = key.channel().toString();
					
					//let friends know that this person has disconnected from server
					int ind = 0;
					for(ind = 0; ind < connectedUsers.length; ind ++)
					{
						if(keyString.equals(connectedUsers[ind].getKeyString()))
						{
							break;
						}
					}
					
					//tell friends that you have disconnected
					if(connectedUsers[ind].friends[0] != null)
					{
						String name = connectedUsers[ind].getName();
						String theMsg = "Tonline:"+name+" is now offline\n";
						ByteBuffer msg =ByteBuffer.wrap(theMsg.getBytes());
						for(SelectionKey key2 : selector.keys()) {
							if(key2.isValid() && key2.channel() instanceof SocketChannel) 
							{
								SocketChannel sch=(SocketChannel) key2.channel();
								
								if(keyString.equals(sch.toString()))
								{
									continue;
								}
								//else if in friends list, send online message
								else if(isAFriend(ind, sch.toString()))
								{
									try {
									sch.write(msg);
									} catch (IOException e2) {
									}
									msg.rewind();
									break;	
								}													
							}
						}
					}
					connectedUsers[ind].setHasDisconnected(true);
					removeConnection(keyString);					
					continue;
				}
			}
	}

	private final ByteBuffer welcomeBuf = ByteBuffer.wrap("Welcome to the Chat Server!\n".getBytes());
	private void handleAccept(SelectionKey key) throws IOException {
		SocketChannel sc = ((ServerSocketChannel) key.channel()).accept();
		String address = (new StringBuilder( sc.socket().getInetAddress().toString() )).append(":").append( sc.socket().getPort() ).toString();
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ, address);
		//sc.write(welcomeBuf);
		//welcomeBuf.rewind();
		System.out.println("accepted connection from: "+address);
	}

	private void handleRead(SelectionKey key) throws IOException{
		SocketChannel ch = (SocketChannel) key.channel();
		StringBuilder sb = new StringBuilder();
		
		String ip = parseKeyString(key.channel().toString());
		String keyString = key.channel().toString();

		buf.clear();
		int read = 0;
		while( (read = ch.read(buf)) > 0 ) {
			buf.flip();
			byte[] bytes = new byte[buf.limit()];
			buf.get(bytes);
			sb.append(new String(bytes));
			buf.clear();
		}
		String line = sb.toString();
		String msg = "";
		if(read<0) {
			msg = key.attachment()+" left the chat.\n";
			ch.close();
		}
		
		System.out.println(line);
		
		//protocol will handle the message received from client
		protocol(line, ip, keyString, ch);
	}

	//Broadcast message to users
	private void broadcast(String msg, String ip, String keyString, User user) throws IOException {
		
		//parse out just the message (without the header)
		String[] temp = msg.split(":");
		String[] temp2 = temp[1].split("\n");
		String chatLogMsg = temp2[0];
		
		//if user is in private chat, only send to people in his pChat array
		if(user.getIsPrivateChat())
		{
			ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
			for(SelectionKey key : selector.keys()) {
				if(key.isValid() && key.channel() instanceof SocketChannel) 
				{
					SocketChannel sch=(SocketChannel) key.channel();
					
					boolean isInPrivate = false;
					int i = 0;
					for(i = 0; i < user.pChat.length; i++)
					{
						if(user.pChat[0] != null)
						{
							if(sch.toString().equals(user.pChat[i].getKeyString()))
							{
								isInPrivate = true;
								break;
							}
						}						
					}					
					//don't send message to yourself, but add to your log
					if(keyString.equals(sch.toString()))
					{
						user.addToChatLog(chatLogMsg);
						continue;
					}
					//send if in pchat
					else if(isInPrivate)
					{
						user.pChat[i].addToChatLog(chatLogMsg);
						sch.write(msgBuf);
						msgBuf.rewind();
					}
				}
			}
		}
		//else send to everyone else in public chat
		else
		{
			ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
			for(SelectionKey key : selector.keys()) {
				if(key.isValid() && key.channel() instanceof SocketChannel) 
				{
					SocketChannel sch=(SocketChannel) key.channel();
					
					boolean isInPrivate = false;
					for(int i = 0; i < connectedUsers.length; i++)
					{
						if(sch.toString().equals(connectedUsers[i].getKeyString()) && connectedUsers[i].getIsPrivateChat() == true)
						{
							isInPrivate = true;
							break;
						}
					}
					
					//don't send message to yourself
					if(keyString.equals(sch.toString()))
					{
						user.addToChatLog(chatLogMsg);
						continue;
					}
					//don't send to users who are in a private chat
					else if(isInPrivate)
					{
						continue;
					}
					
					addToPublicLog(sch.toString(), chatLogMsg);
					sch.write(msgBuf);
					msgBuf.rewind();
				}
			}
		}		
	}
	
	//protocol for handling all message formats sent by the client
	private void protocol(String line, String ip, String keyString, SocketChannel ch)
	{
		int index = 0;
		//get location of user in array
		if(allUsers[0] != null)
		{			
			for(index = 0; index < allUsers.length; index++)
			{
				if(keyString.equals(allUsers[index].getKeyString()))
				{
					break;
				}
			}
		}		
		
		if(line.matches("[T]*login:[a-zA-Z0-9 ]+:[a-zA-Z0-9]+\n"))
		{
			//Parse out user name and password
			String temp = line;
			String[] tokens = temp.split(":");
			String userName = tokens[1];
			String[] tokens2 = tokens[2].split("\n");
			String password = tokens2[0];
			
			//check login credentials for validity
			String result = credentialCheck(userName, password);
			if(result.equals("wrong"))
			{
				System.out.println("Wrong password was entered by "+ ip);
				String message = "Tserver:Incorrect password entered\n";
				//send list of all online users
				ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
			}
			else if(result.equals("new"))
			{
				System.out.println("Creating new user: "+userName +"  With password: "+password);
				createUser(userName, password, ip, keyString);
				
				System.out.println("Creating new account for "+ip);
				String message = "Tserver:Created new Account\n";
				
				//send success message
				ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
			}
			//else you logged in successfully
			else
			{
				System.out.println("");
				String message = "Tserver:Login Success\n";
				
				//send success message over to the user who just logged in
				ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
				
				//login success
				User tempUser = null;
				for(int i = 0; i<allUsers.length; i++)
				{
					if(allUsers[i].getName().equals(userName))
					{
						tempUser = allUsers[i];
						break;
					}
				}
				
				//add to connected Users, and update fields
				if(tempUser != null)
				{
					tempUser.setIP(ip);
					tempUser.setIsOnline(true);
					tempUser.setKeyString(keyString);
					addConnection(tempUser);			
				}
				
				//let friends know that this person is online now by sending them all a message
				int ind = 0;
				for(ind = 0; ind < connectedUsers.length; ind ++)
				{
					if(keyString.equals(connectedUsers[ind].getKeyString()))
					{
						break;
					}
				}
				
				//only send if he has friends
				if(connectedUsers[ind].friends[0] != null)
				{
					String name = connectedUsers[ind].getName();
					String theMsg = "Tonline:"+name+" is now online\n";
					ByteBuffer msg =ByteBuffer.wrap(theMsg.getBytes());
					for(SelectionKey key : selector.keys()) {
						if(key.isValid() && key.channel() instanceof SocketChannel) 
						{
							SocketChannel sch=(SocketChannel) key.channel();
							
							if(keyString.equals(sch.toString()))
							{
								continue;
							}
							//else if in friends list, send online message
							else if(isAFriend(ind, sch.toString()))
							{
								try {
								sch.write(msg);
								} catch (IOException e) {
								}
								msg.rewind();
								break;	
							}													
						}
					}
				}
				//check if user logged out properly in their previous session
				if(connectedUsers[ind].getHasDisconnected())
				{
					//if they disconnected without logging out, send them chat history
					String message1 = "";
					if(connectedUsers[ind].chatLog[0] != null)
					{
						message1 = connectedUsers[ind].getChatLog();
						//send list of all online users
						ByteBuffer msgBuf1=ByteBuffer.wrap(message1.getBytes());
						for(SelectionKey key : selector.keys()) 
						{
							if(key.isValid() && key.channel() instanceof SocketChannel) {
								SocketChannel sch=(SocketChannel) key.channel();
								
								//only send to person who requested
								if(keyString.equals(sch.toString()))
								{
									try {
										sch.write(msgBuf1);
									} catch (IOException e) {
									}
									msgBuf1.rewind();
									break;
								}					
							}
						}
					}
					else
					{
						message1 = "Tserver: No chat history available\n";
						//send list of all online users
						ByteBuffer msgBuf1=ByteBuffer.wrap(message1.getBytes());
						for(SelectionKey key : selector.keys()) 
						{
							if(key.isValid() && key.channel() instanceof SocketChannel) {
								SocketChannel sch=(SocketChannel) key.channel();
								
								//only send to person who requested
								if(keyString.equals(sch.toString()))
								{
									try {
										sch.write(msgBuf1);
									} catch (IOException e) {
									}
									msgBuf1.rewind();
									break;
								}					
							}
						}
					}
					connectedUsers[ind].setHasDisconnected(false);
				}
			}
		}
		else if(line.matches("[T]*msg:.+\n"))
		{
			String tmp = line;
			String[] tokens = tmp.split(":");
			String[] tokens2 = tokens[1].split("\n");
			String message = tokens2[0];
			
			try {
				broadcast(line,ip,keyString,allUsers[index]);
			} catch (IOException e) {
				System.out.println("Could not broadcast");
			}
		}
		else if(line.matches("[T]*list\n"))
		{
			//get list of names and put them all as one string for sending (names separated by ':')
			String theList = "Tlist:";
			for(int i = 0; i<connectedUsers.length; i++)
			{
				theList = theList + connectedUsers[i].getName() + ":";
				System.out.println("Online users list : " +theList);
			}
			theList = theList+"\n";
			
			//send list of all online users
			ByteBuffer msgBuf=ByteBuffer.wrap(theList.getBytes());
			for(SelectionKey key : selector.keys()) {
				if(key.isValid() && key.channel() instanceof SocketChannel) {
					SocketChannel sch=(SocketChannel) key.channel();
					
					//only send to person who requested
					if(keyString.equals(sch.toString()))
					{
						try {
							sch.write(msgBuf);
						} catch (IOException e) {
						}
						msgBuf.rewind();
						break;
					}					
				}
			}
		}
		//add friend request to be processed
		else if(line.matches("[T]*add:[a-zA-Z0-9]+\n"))
		{
			//parse out name
			String[] temp = line.split(":");
			String[] temp2 = temp[1].split("\n");
			String theirName = temp2[0];
			
			//try to add the friend, if possible
			System.out.println("Attempting to add friend: "+theirName +" to account: "+keyString);
			boolean exists = false;
			User friendToAdd = null;
			int i = 0;
			
			for(i = 0; i < allUsers.length; i++)
			{
				if(allUsers[i].getName().equals(theirName))
				{
					exists = true;
					friendToAdd = allUsers[i];
					break;
				}
			}
			
			int x = 0;
			User friendToAdd2 = null;
			//if player exists, add to your friend list, and vice versa
			if(exists)
			{
				for(x = 0; x < allUsers.length; x++)
				{
					if(keyString.equals(allUsers[x].getKeyString()))
					{
						allUsers[x].addFriend(friendToAdd);
						friendToAdd2 = allUsers[x];
						break;
					}
				}
				
				allUsers[i].addFriend(friendToAdd2);
			}			
		}
		//remove friend a friend
		else if(line.matches("[T]*remove:[a-zA-Z0-9]+\n"))
		{
			//parse out name
			String[] temp = line.split(":");
			String[] temp2 = temp[1].split("\n");
			String theirName = temp2[0];
			
			//try to remove the friend, if possible
			System.out.println("Attempting to remove friend: "+theirName +" from account: "+keyString);
			boolean exists = false;
			int i = 0;
			
			//find friend in array
			for(i = 0; i < allUsers.length; i++)
			{
				if(allUsers[i].getName().equals(theirName))
				{
					exists = true;
					break;
				}
			}
			
			int x = 0;
			String theRemover = "";
			//if player exists, remove from your friend list, and vice versa
			if(exists)
			{
				for(x = 0; x < allUsers.length; x++)
				{
					if(keyString.equals(allUsers[x].getKeyString()))
					{
						allUsers[x].removeFriend(theirName);
						theRemover = allUsers[x].getName();
						break;
					}
				}
				
				allUsers[i].removeFriend(theRemover);
			}			
		}
		//list friends for this user
		else if(line.matches("[T]*flist\n"))
		{			
			//get list of names and put them all as one string for sending (names separated by ':')
			String theList = "Tflist:";
			for(int i = 0; i< allUsers[index].friends.length; i++)
			{
				if(allUsers[index].friends[i] != null)
				{
					if(allUsers[index].friends[i].getIsOnline() == true)
					{
						theList = theList + allUsers[index].friends[i].getName() + ":";					
					}	
				}
							
			}
			
			System.out.println("Friends that are online list : " +theList);
			theList = theList+"\n";
			
			//send list of all online users
			ByteBuffer msgBuf=ByteBuffer.wrap(theList.getBytes());
			for(SelectionKey key : selector.keys()) 
			{
				if(key.isValid() && key.channel() instanceof SocketChannel) {
					SocketChannel sch=(SocketChannel) key.channel();
					
					//only send to person who requested
					if(keyString.equals(sch.toString()))
					{
						try {
							sch.write(msgBuf);
						} catch (IOException e) {
						}
						msgBuf.rewind();
						break;
					}					
				}
			}
		}
		//someone requests to make a private chat
		else if(line.matches("[T]*create\n"))
		{			
			//if he's already part of a chat room, he must first leave
			if(allUsers[index].getIsPrivateChat())
			{
				String msg = "Tserver: Please leave your current private chat before creating a new one\n";
				//send list of all online users
				ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
			}
			//else make a new private chat room
			else
			{
				allUsers[index].setIsPrivateChat(true);
			}
		}
		//invite someone to private chat
		else if(line.matches("[T]*invite:.+\n"))
		{
			//parse out name
			String[] temp = line.split(":");
			String[] temp2 = temp[1].split("\n");
			String theirName = temp2[0];
			
			//check if user exists or is online
			boolean exists = false;
			int i = 0;
			for(i = 0; i < connectedUsers.length; i++)
			{
				if(connectedUsers[i].getName().equals(theirName))
				{
					exists = true;
					break;
				}
			}
			
			//if that user exists or is online
			if(exists)
			{
				//if user already in private chat, don't add
				if(connectedUsers[i].getIsPrivateChat())
				{
					String msg = "Tserver: User already in a private chat\n";
					//send list of all online users
					ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
					for(SelectionKey key : selector.keys()) 
					{
						if(key.isValid() && key.channel() instanceof SocketChannel) {
							SocketChannel sch=(SocketChannel) key.channel();
							
							//only send to person who requested
							if(keyString.equals(sch.toString()))
							{
								try {
									sch.write(msgBuf);
								} catch (IOException e) {
								}
								msgBuf.rewind();
								break;
							}					
						}
					}
				}
				//else add
				else
				{
					connectedUsers[i].setIsPrivateChat(true);
					allUsers[index].addToPrivateChat(connectedUsers[i]);
					connectedUsers[i].addToPrivateChat(allUsers[index]);
					
					//if there are others already in the chat, add them to your pChat, and add yourself to their's
					if(allUsers[index].pChat.length > 1)
					{
						for(int x = 0; x < allUsers[index].pChat.length; x++)
						{
							//don't add yourself
							if(allUsers[index].pChat[x].getName().equals(connectedUsers[i].getName()))
							{
								continue;
							}
							connectedUsers[i].addToPrivateChat(allUsers[index].pChat[x]);
							allUsers[index].pChat[x].addToPrivateChat(connectedUsers[i]);
						}
					}
				}
			}			
			//user doesn't exist or not online, so tell client
			else
			{
				String msg = "Tserver: User not online or does not exist";
				//send list of all online users
				ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}				
			}
		}
		//leaving a private chat room
		else if(line.matches("[T]*leave\n"))
		{
			String myName = allUsers[index].getName();
			//if you're not in a chat room, do nothing
			if(allUsers[index].getIsPrivateChat() == false)
			{
				String msg = "Tserver: You are not in a private chat room\n";
				//send list of all online users
				ByteBuffer msgBuf=ByteBuffer.wrap(msg.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
			}
			//else remove yourself from other's pchats and clear your pchat, and set your boolean to false
			else
			{
				System.out.println("Attempting to leave private chat");
				for(int z = 0; z < allUsers[index].pChat.length; z++)
				{
					if(allUsers[index].pChat[z] == null)
					{
						continue;
					}
					allUsers[index].pChat[z].removeFromPrivateChat(myName);
				}
				User[] cleared = {null};
				allUsers[index].pChat = cleared;
				allUsers[index].setIsPrivateChat(false);
			}
		}
		//user sends us a chat history request
		else if(line.matches("[T]*log\n"))
		{
			String message = "";
			if(allUsers[index].chatLog[0] != null)
			{
				message = allUsers[index].getChatLog();
				//send list of all online users
				ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
			}
			else
			{
				message = "Tserver: No chat history available\n";
				//send list of all online users
				ByteBuffer msgBuf=ByteBuffer.wrap(message.getBytes());
				for(SelectionKey key : selector.keys()) 
				{
					if(key.isValid() && key.channel() instanceof SocketChannel) {
						SocketChannel sch=(SocketChannel) key.channel();
						
						//only send to person who requested
						if(keyString.equals(sch.toString()))
						{
							try {
								sch.write(msgBuf);
							} catch (IOException e) {
							}
							msgBuf.rewind();
							break;
						}					
					}
				}
			}
			
		}
		//logout of the system
		else if(line.matches("[T]*logout\n"))
		{
			//let friends know that this person is logging out now by sending them all a message
			int ind = 0;
			for(ind = 0; ind < connectedUsers.length; ind ++)
			{
				if(keyString.equals(connectedUsers[ind].getKeyString()))
				{
					break;
				}
			}
			
			//tell friends that you are logging out
			if(connectedUsers[ind].friends[0] != null)
			{
				String name = connectedUsers[ind].getName();
				String theMsg = "Tonline:"+name+" is now offline\n";
				ByteBuffer msg =ByteBuffer.wrap(theMsg.getBytes());
				for(SelectionKey key : selector.keys()) {
					if(key.isValid() && key.channel() instanceof SocketChannel) 
					{
						SocketChannel sch=(SocketChannel) key.channel();
						
						if(keyString.equals(sch.toString()))
						{
							continue;
						}
						//else if in friends list, send online message
						else if(isAFriend(ind, sch.toString()))
						{
							try {
							sch.write(msg);
							} catch (IOException e) {
							}
							msg.rewind();
							break;	
						}													
					}
				}
			}
			System.out.println("Disconnected: "+keyString);
			connectedUsers[ind].setHasDisconnected(true);
			removeConnection(keyString);
		}
		//else if user requests files in online repository
		else if(line.matches("[T]*repo\n"))
		{
			//get list of names and their files stored on their repository and put them all as one string for sending
			String theList = "Trepo:";
			for(int i = 0; i<allUsers.length; i++)
			{
				theList = theList + "[" + allUsers[i].getName()+ "'s repo]" + "\t";
				for(int j = 0; j<allUsers[i].repository.length; j++)
				{
					if(allUsers[i].repository[j] == null)
					{
						break;
					}
					theList = theList + allUsers[i].repository[j].getName() + "\t";
				}
				theList = theList + ":";
			}
			System.out.println("repo info: " +theList);
			theList = theList+"\n";
			
			//send list of all online users
			ByteBuffer msgBuf=ByteBuffer.wrap(theList.getBytes());
			for(SelectionKey key : selector.keys()) {
				if(key.isValid() && key.channel() instanceof SocketChannel) {
					SocketChannel sch=(SocketChannel) key.channel();
					
					//only send to person who requested
					if(keyString.equals(sch.toString()))
					{
						try {
							sch.write(msgBuf);
						} catch (IOException e) {
						}
						msgBuf.rewind();
						break;
					}					
				}
			}
		}
		//else user wants to download a file from server
		else if(line.matches("[T]*get:.+\n"))
		{
			//parse out name of the file
			String[] temp = line.split(":");
			String[] temp2 = temp[1].split("\n");
			String fName = temp2[0];
			
			File f = new File(fName);
			byte[] b = null;
			ByteBuffer inBuffer = null;
			inBuffer = ByteBuffer.allocateDirect(256);
			int bytesSent = 0;
    		
    		//Make sure file exists in current directory, and if so, send it
    		if (f.exists() && !f.isDirectory())
    		{
    			try {
    				System.out.println("Sending "+fName+" to client..");
    				b = Files.readAllBytes(f.toPath());
    				
    				ByteBuffer msgBuf=ByteBuffer.wrap(fName.getBytes());
    				for(SelectionKey key : selector.keys()) {
    					if(key.isValid() && key.channel() instanceof SocketChannel) {
    						SocketChannel sch=(SocketChannel) key.channel();
    						
    						//only send to person who requested
    						if(keyString.equals(sch.toString()))
    						{
    							try {
    								sch.write(msgBuf);
    								FileChannel sbc = FileChannel.open(f.toPath());
    								ByteBuffer bf = ByteBuffer.allocate(10000000);
    								int bRead = sbc.read(bf);
    								while(bRead != -1)
    								{
    									bf.flip();
    									sch.write(bf);
    									bf.compact();
    									bRead = sbc.read(bf);
    								}
    							} catch (IOException e) {
    							}
    							msgBuf.rewind();
    							break;
    						}					
    					}
    				}
				} catch (IOException e) {
					System.out.println("Problem sending the file. Aborted.");
				}
	    	}
    		else
    		{
    			System.out.println("File does not exist or is a directory");
    		}
		}
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
			            
			            //add to this user's repo
			            allUsers[index].addToRepository(f);
			            
			        }else System.out.println(fileName+" already exists in current directory");

				} catch (IOException e) {
				}
			    
			    if(p != null)
			    {
			    	try (OutputStream out = new BufferedOutputStream(
			    	Files.newOutputStream(p, CREATE, APPEND))) {
			    	out.write(data, 0, data.length);
			    	} catch (IOException x) {
			    	System.err.println(x);
			    	}	 
			    }	       
			
		}
	}
	
	//checks for valid user and password
	private String credentialCheck(String userName, String password)
	{
		boolean userExist = false;
		boolean passCorrect = false;
		int i;
		if(allUsers[0] == null)
		{
			return "new";
		}
		
		for(i = 0; i < allUsers.length; i++)
		{
			if(allUsers[i].getName().equals(userName))
			{
				userExist = true;
				if(allUsers[i].getPass().equals(password))
				{
					passCorrect = true;
				}
				break;
			}
		}
		
		//if user and pass correct, return i
		if(userExist == true && passCorrect == true)
		{
			String tmp = "";
			tmp = tmp+i;
			return tmp;
		}
		//if user correct, but wrong pass, return -1
		else if(userExist == true && passCorrect == false)
		{
			return "wrong";
		}
		//else, user doesn't exist, return -2
		else
		{
			return "new";
		}
		
	}
	
	//creates a new user and stores it
	private void createUser(String userName, String password, String ip, String keyString)
	{
		User user = new User(userName, password);
		user.setIP(ip);
		user.setIsOnline(true);
		user.setKeyString(keyString);
		//set all connections array to all current connected users
		
		//add user to array of all users
		addUser(user);
		addConnection(user);
	}
	
	//Adds a connection to the connectedUsers array of users
	private void addConnection(User user)
	{
			if(connectedUsers[0] == null)
			{
				connectedUsers[0] = user;
			}
			else
			{
				User[] temp = new User[connectedUsers.length+1];
				for(int i = 0; i<connectedUsers.length; i++)
				{
					temp[i] = connectedUsers[i];
				}
				temp[temp.length-1] = user;	
				connectedUsers = temp;
			}	
	}
	
	//remove a connection from current connected users (when someone logs off)
	private void removeConnection(String keyString)
	{
		int index = 0;
		for(int i =0; i<connectedUsers.length;i++)
		{
			if(connectedUsers[i].getKeyString().equals(keyString))
			{
				index = i;
				connectedUsers[i].setIsOnline(false);
				break;
			}
		}
		
		if(connectedUsers.length>1)
		{
			User[] n = new User[connectedUsers.length-1];
			System.arraycopy(connectedUsers, 0, n, 0, index);
			System.arraycopy(connectedUsers, index+1, n, index, connectedUsers.length - index-1);
			
			connectedUsers = n;
		}
		else
		{
			connectedUsers[0] = null;
		}
	}
	
	//Adds newly created user to array of all users
	private void addUser(User user)
	{
		if(allUsers[0] == null)
		{
			allUsers[0] = user;
		}
		else
		{
			User[] temp = new User[allUsers.length+1];
			for(int i = 0; i<allUsers.length; i++)
			{
				temp[i] = allUsers[i];
			}
			temp[temp.length-1] = user;
			allUsers = temp;
		}
	}
	
	public String parseKeyString(String keyString){
		String[] tokn = keyString.split("/");
        String[] tokn2 = tokn[2].split(":");
        String ip = tokn2[0];
        
        return ip;
	}
	
	public void addToPublicLog(String keyString, String msg)
	{
		for(int i = 0; i<connectedUsers.length; i++)
		{
			if(keyString.equals(connectedUsers[i].getKeyString()))
			{
				connectedUsers[i].addToChatLog(msg);
			}
		}
	}
	
	public boolean isAFriend(int ind, String theKeyString)
	{
		boolean isFriend = false;
		if(connectedUsers[ind].friends[0] != null)
		{
			for(int i = 0; i < connectedUsers[ind].friends.length; i++)
			{
				if(theKeyString.equals(connectedUsers[ind].friends[i].getKeyString()))
				{
					isFriend = true;
					break;
				}
			}
		}
		return isFriend;		
	}

	public static void main(String[] args) throws IOException {
		Server server = new Server(5555);
		(new Thread(server)).start();
	}
}
