/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lockstep;

import lockstep.messages.simulation.InputMessageArray;
import lockstep.messages.simulation.InputMessage;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import lockstep.messages.simulation.FrameACK;
import lockstep.messages.simulation.KeepAlive;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author Raff
 */
public class LockstepReceiver implements Runnable
{
    volatile DatagramSocket dgramSocket;
    volatile Map<Integer, ReceivingQueue> receivingQueues;
    volatile Map<Integer, TransmissionQueue> transmissionFrameQueues;
    volatile ACKQueue ackQueue;
    static final int maxPayloadLength = 300;
    
    private static final Logger LOG = LogManager.getLogger(LockstepReceiver.class);
    
    private final String name;
    
    private final int tickrate;
    
    public LockstepReceiver(DatagramSocket socket, int tickrate, Map<Integer, ReceivingQueue> receivingQueues, Map<Integer, TransmissionQueue> transmissionFrameQueues, String name, ACKQueue ackQueue)
    {
        dgramSocket = socket;
        this.receivingQueues = receivingQueues;
        this.transmissionFrameQueues = transmissionFrameQueues;
        this.name = name;
        this.tickrate = tickrate;
        this.ackQueue = ackQueue;
    }
    
    @Override
    public void run()
    {
        Thread.currentThread().setName(name);
        
        while(true)
        {
            try
            {
                DatagramPacket p = new DatagramPacket(new byte[maxPayloadLength], maxPayloadLength);
                this.dgramSocket.receive(p);
                try(
                    ByteArrayInputStream bain = new ByteArrayInputStream(p.getData());
                    GZIPInputStream gzin = new GZIPInputStream(bain);
                    ObjectInputStream oin = new ObjectInputStream(gzin);
                )
                {
                    Object obj = oin.readObject();
                    messageSwitch(obj);
                }
            }
            catch(SocketTimeoutException | PortUnreachableException disconnectionException)
            {
                //TODO: termination handling
                
                System.out.println("Disconnecteded socket (which one?)");
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            
            /*
            try {
                Thread.sleep(1000/tickrate);
                
                //Study shutdown case
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(LockstepReceiver.class.getName()).log(Level.SEVERE, null, ex);
            }
            */
        }
    }
    
    private void messageSwitch(Object obj) throws Exception
    {
        if(obj instanceof InputMessage)
        {
            InputMessage input = (InputMessage)obj;
            this.processInput(input);
        }
        else if(obj instanceof InputMessageArray)
        {
            InputMessageArray inputs = (InputMessageArray)obj;
            this.processInput(inputs);
        }
        else if(obj instanceof FrameACK)
        {
            FrameACK ack = (FrameACK)obj;
            this.processACK(ack);
        }
        else if(obj instanceof KeepAlive)
        {   
            //Socket connection timeout is reset at packet reception
        }
        else 
        {
            throw(new Exception("Unrecognized message received"));
        }
    }
    
    private void processInput(InputMessage input)
    {
        LOG.debug("1 InputMessage received from " + input.senderID + ": " + input.frame.getFrameNumber());
        ReceivingQueue receivingQueue = this.receivingQueues.get(input.senderID);
        FrameACK frameACK = receivingQueue.push(input.frame);
        frameACK.setSenderID(input.senderID);
        ackQueue.pushACKs(frameACK);
        //sendACK(frameACK);
    }

    private void processInput(InputMessageArray inputs)
    {
        String numbers = "";
        for(FrameInput frame : inputs.frames)
            numbers += frame.getFrameNumber() + ", ";
        LOG.debug("" + inputs.frames.length + " InputMessages received from " + inputs.senderID + ": [ " + numbers + "]");
        ReceivingQueue receivingQueue = this.receivingQueues.get(inputs.senderID);
        FrameACK frameACK = receivingQueue.push(inputs.frames);
        frameACK.setSenderID(inputs.senderID);
        ackQueue.pushACKs(frameACK);
        
        //sendACK(frameACK);
    }
    
    private void processACK(FrameACK ack)
    {
        TransmissionQueue transmissionFrameQueue = this.transmissionFrameQueues.get(ack.senderID);
        transmissionFrameQueue.processACK(ack);
    }
    
    
}
