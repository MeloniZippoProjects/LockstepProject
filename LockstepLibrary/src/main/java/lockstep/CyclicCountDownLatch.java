/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lockstep;

import java.util.concurrent.CountDownLatch;

/**
 *
 * @author enric
 */
public class CyclicCountDownLatch {
    CountDownLatch latch;
    int count;
    
    public CyclicCountDownLatch(int count)
    {
        this.count = count;
        this.latch = new CountDownLatch(count);
    }
    
    public void await() throws InterruptedException
    {
        this.latch.await();
        this.latch = new CountDownLatch(count);
    }
    
    public void countDown()
    {
        this.latch.countDown();
    }
    
    public long getCount()
    {
        return this.latch.getCount();
    }
    
    public void reset()
    {
        this.latch = new CountDownLatch(count);
    }
}