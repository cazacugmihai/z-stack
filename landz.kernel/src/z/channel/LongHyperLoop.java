/**
 * Copyright 2013, Landz and its contributors. All rights reserved.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package z.channel;

import static z.util.Contracts.contract;
import static z.util.Unsafes.*;

/**
 *  {@link LongHyperLoop} is an Inter-Thread-Communication(ITC) mechanism.
 *  <P>
 *  Note:<p>
 *    now, the LongHyperLoop aims to support Landz's Asyncor async programming
 *    model. For performance, the current implementations of LongHyperLoop use
 *    off-heap memory via s.m.Unsafe, so it is a little "heavy", take care
 *    when you rush with "new/discard" to LongHyperLoop.
 *    <p> see more
 *
 */
public class LongHyperLoop {
  //TODO: add a config option for SIZE_HYPERLOOP_BASE?
  /**
   * NOTE: you should reserve 128*(4+number of consumers of LongHyperLoop),
   * otherwise you may crash your JVM.
   * <p>
   * contract:
   * 1. now keep it a multiple of 4096
   */
  private static final int SIZE_HYPERLOOP_BASE = 4096;//only for 28 consumers?
  private static final int SIZE_SHIFT_BUFFERSLOT = 3;
  private static final int SIZE_LONG_TYPE = 8;

  private final long addressRaw;
  private final long addressHyperLoop;

  private final int nBufferSlots;
  private final int bufferSlotMask;

  private final long addrWriteCursor;
  private final long addrMinReadCursor;
  private final long addrBuffer;
  private final long addrReadCursorCount;//int type
  private long addrReadCursors;

  /**
   * create a LongHyperLoop with 512 slots in its internal buffer
   *
   */
  public LongHyperLoop() {
    this(512);
  }

  /**
   * create a LongHyperLoop.
   * <p>
   * contract: <p>
   *  1. nBufferSlots >=8  <p>
   *  2. nBufferSlots is a power of 2, and
   *  better if you can make nBufferSlots*8 a multiple of 4096
   *
   * <p>
   *
   * @param nBufferSlots the size of internal buffer in slot unit
   */
  public LongHyperLoop(int nBufferSlots) {
    contract(()->nBufferSlots>=8);
    contract(()->Integer.bitCount(nBufferSlots)==1);
    this.nBufferSlots = nBufferSlots;
    this.bufferSlotMask = nBufferSlots - 1;
    //========================================================
    int bufferSize = nBufferSlots<<SIZE_SHIFT_BUFFERSLOT;
    int requestedSize = bufferSize + SIZE_HYPERLOOP_BASE;
    addressRaw = systemAllocateMemory(requestedSize + SIZE_PAGE);

    addressHyperLoop = nextPageAlignedAddress(addressRaw);
    contract(() -> isPageAligned(addressHyperLoop));

    this.addrWriteCursor = addressHyperLoop
        + SIZE_CACHE_LINE_PADDING;
    contract(() -> isCacheLineAligned(addrWriteCursor));

    this.addrMinReadCursor = addrWriteCursor + SIZE_CACHE_LINE_PADDING;
    contract(() -> isCacheLineAligned(addrMinReadCursor));

    this.addrReadCursorCount = addrMinReadCursor + SIZE_CACHE_LINE_PADDING;
    contract(() -> isCacheLineAligned(addrReadCursorCount));

    this.addrReadCursors = addrReadCursorCount;
    contract(() -> isCacheLineAligned(addrReadCursors));

    this.addrBuffer = addressHyperLoop + SIZE_HYPERLOOP_BASE;
    //assumed ReadCursors gives enough space
    //in fact, the follow is not strictly guaranteed but still hoped it kept
    contract(() -> isCacheLineAligned(addrBuffer));

    UNSAFE.putLong(addrWriteCursor, 0L);
    UNSAFE.putLong(addrMinReadCursor, 0L);
    UNSAFE.putInt(addrReadCursorCount,0);
  }

  /**
   * TODO: overload a long array version?
   * @param value
   * @return
   */
  public boolean send(long value) {
    long minReadCursor = UNSAFE.getLongVolatile(null,addrMinReadCursor);
    long writeCursor = UNSAFE.getLong(addrWriteCursor);
    if (writeCursor == (minReadCursor+nBufferSlots)) {
      // assume readCursorCount>0
      minReadCursor = UNSAFE.getLongVolatile(null, addrReadCursors);
      for (int i = 1; i < UNSAFE.getInt(addrReadCursorCount); i++) {
        long readCursor = UNSAFE.getLongVolatile(null, addrReadCursors
            - i*SIZE_CACHE_LINE_PADDING);
        minReadCursor = minReadCursor > readCursor ? readCursor : minReadCursor;
      }
      UNSAFE.putLong(addrMinReadCursor,minReadCursor);
      return false;
    }

    UNSAFE.putLong(
        addrBuffer + ((writeCursor & bufferSlotMask) << SIZE_SHIFT_BUFFERSLOT),
        value);
    UNSAFE.putLong(addrWriteCursor, writeCursor + 1);
    UNSAFE.storeFence();
    return true;
  }


  public void sendTo(long value) {
    long minReadCursor = UNSAFE.getLongVolatile(null,addrMinReadCursor);
    long writeCursor = UNSAFE.getLong(addrWriteCursor);

    while (writeCursor == (minReadCursor+nBufferSlots)) {
      // assume readCursorCount>0
      minReadCursor = UNSAFE.getLongVolatile(null, addrReadCursors);
      for (int i = 1; i < UNSAFE.getInt(addrReadCursorCount); i++) {
        long readCursor = UNSAFE.getLongVolatile(null, addrReadCursors
            - i*SIZE_CACHE_LINE_PADDING);
        minReadCursor = minReadCursor > readCursor ? readCursor : minReadCursor;
      }
      UNSAFE.putLong(addrMinReadCursor,minReadCursor);
      Thread.yield();//harm latency but welcome to throughput
    }
    //TODO:
    UNSAFE.putLong(
        addrBuffer + ((writeCursor & bufferSlotMask) << SIZE_SHIFT_BUFFERSLOT),
        value);
    UNSAFE.putLong(addrWriteCursor, writeCursor + 1);
    UNSAFE.storeFence();
  }


  @Override
  public void finalize() {
    systemFreeMemory(addressRaw);
  }

  public OutPort createOutPort() {
    return this.new OutPort();
  }

  /**
   * Note:
   * it is always hoped the values sent into LongHyperLoop could be consumed ASAP.
   *
   * TODO: need to handle the removal of OutPort dynamically
   */
  public final class OutPort {
    private final long addrReadCursor;

    private OutPort() {
      synchronized(OutPort.class) {
        addrReadCursors += SIZE_CACHE_LINE_PADDING;
        this.addrReadCursor = addrReadCursors;
        //TODO: clear the readCursor
        UNSAFE.putLong(addrReadCursor,0L);
        UNSAFE.putInt(addrReadCursorCount,
            UNSAFE.getInt(addrReadCursorCount) + 1);
      }
    }

    public boolean isReceivable() {
      return UNSAFE.getLong(addrReadCursor) !=
          UNSAFE.getLongVolatile(null, addrWriteCursor);
    }

    public boolean notReceivable() {
      return UNSAFE.getLong(addrReadCursor) ==
          UNSAFE.getLongVolatile(null, addrWriteCursor);
    }

    //TODO: unchecked except to checked?
    /**
     * this {@link #receive()} and {@link #notReceivable()} are another kind
     * style of combined APIs.<p>
     * Contrast to {@link #received()},
     * you should first use {@link #notReceivable()} to ensure that you can
     * do the following {@link #receive()}, otherwise you may get an
     * {@link IllegalStateException} when nothing could be received.
     *
     * @return
     */
    public long receive() {
//      UNSAFE.loadFence();
      long readCursor = UNSAFE.getLong(addrReadCursor);
      if (readCursor==UNSAFE.getLong(addrWriteCursor)) {
        throw new IllegalStateException("nothing to receive.");
      }
      //TODO: getLong, getLongVolatile, getAddress seems...
      long value = UNSAFE.getLongVolatile(null,
          addrBuffer + ((readCursor & bufferSlotMask)<<SIZE_SHIFT_BUFFERSLOT));
      UNSAFE.putLong(addrReadCursor, readCursor+1);
      UNSAFE.storeFence();
      return value;
    }

    /**
     * Contrast to {@link #notReceivable()} + {@link #receive()}, this call
     * will wait(block) until something can be received.<p>
     * So this call gives more higher throughput but harms latency.<p>
     * NOTE: This method is just for lazy men.<p>
     * You can use {@link #notReceivable()} + {@link #receive()} with
     * Landz off-heap APIs to achieve more controllable
     * and low latency batch behavior.
     *
     */
    public long received() {
      long readCursor = UNSAFE.getLong(addrReadCursor);
      while (readCursor==UNSAFE.getLongVolatile(null, addrWriteCursor)) {
        Thread.yield();
      }
      long value = UNSAFE.getLong(
          addrBuffer + ((readCursor & bufferSlotMask)<<SIZE_SHIFT_BUFFERSLOT));
      UNSAFE.putLong(addrReadCursor, readCursor+1);
      UNSAFE.storeFence();
      return value;
    }


    public void receiveAll() {
      long readCursor = UNSAFE.getLong(addrReadCursor);
      while (readCursor==UNSAFE.getLongVolatile(null, addrWriteCursor)) {
//        Thread.yield();
      }
      long value = UNSAFE.getLong(
          addrBuffer + ((readCursor & bufferSlotMask)<<SIZE_SHIFT_BUFFERSLOT));
      UNSAFE.putLong(addrReadCursor, readCursor+1);
      UNSAFE.storeFence();
//      return value;
    }


  }

//  @FunctionalInterface
//  public static interface WaitStrategy {
//    public void waitToReceive();
//  }

}
