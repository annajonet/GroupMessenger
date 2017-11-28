package edu.buffalo.cse.cse486586.groupmessenger2;

import java.io.Serializable;
/**
 * Object of this class is used by the client to communicate with server in socket programming
 */

public class Message implements Serializable{
    //0 - message, 1 - port num, 2- sendNum, 3 - agreed/1st time,4- proposed port
    String msg;
    String sender;
    int number;
    boolean agreed;
    int proposedPort;
    public Message(String msg, String sender,int number, boolean agreed){
        this.msg = msg;
        this.sender = sender;
        this.agreed = agreed;
        this.number = number;
    }
    public Message(String msg, String sender, boolean agreed,int number, int proposedPort){
        this.msg = msg;
        this.sender = sender;
        this.agreed = agreed;
        this.number = number;
        this.proposedPort = proposedPort;
    }

    public boolean isAgreed(){
        return this.agreed;
    }
    public String getMsg(){
        return this.msg;
    }
    public String getSender(){
        return this.sender;
    }
    public int getNumber(){
        return this.number;
    }
    public int getProposedPort(){
        return this.proposedPort;
    }
}
