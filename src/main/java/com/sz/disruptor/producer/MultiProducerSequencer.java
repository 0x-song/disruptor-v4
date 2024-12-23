package com.sz.disruptor.producer;

import com.sz.disruptor.barrier.SequenceBarrier;
import com.sz.disruptor.sequence.Sequence;
import com.sz.disruptor.strategy.WaitStrategy;
import com.sz.disruptor.util.SequenceUtils;
import com.sz.disruptor.util.UnsafeUtils;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

/**
 * @Author
 * @Date 2024-12-21 22:16
 * @Version 1.0
 * 多线程生产者
 */
public class MultiProducerSequencer implements ProducerSequencer{

    private final int ringBufferSize;

    private final Sequence currentProducerSequence = new Sequence();

    private final List<Sequence> gatingConsumerSequenceList = new ArrayList<>();

    private final WaitStrategy waitStrategy;

    private final Sequence gatingSequenceCache = new Sequence();

    private final int[] availableBuffer;

    //用于快速进行取余运算
    private final int indexMask;

    //用来确定序号在环形缓冲区中"转了多少圈"。
    /**
     * position = 11
     * // 二进制：1011
     * index = 11 & 7 = 3    // 在环中的位置
     * sequence = 11 >> 3 = 1 // 转了1圈
     *
     * position = 3
     * // 二进制：0011
     * index = 3 & 7 = 3     // 在环中的位置
     * sequence = 3 >> 3 = 0  // 还没转完一圈
     */
    private final int indexShift;

    private static final Unsafe unsafe = UnsafeUtils.getUnsafe();

    //base表示数组中第一个元素的起始位置
    private static final long base = unsafe.arrayBaseOffset(int[].class);

    //scale表示数组中每个元素占用的字节数
    private static final long scale = unsafe.arrayIndexScale(int[].class);

    public MultiProducerSequencer(WaitStrategy waitStrategy, int ringBufferSize) {
        this.waitStrategy = waitStrategy;
        this.ringBufferSize = ringBufferSize;
        this.availableBuffer = new int[ringBufferSize];
        this.indexMask = ringBufferSize - 1;
        this.indexShift = log2(ringBufferSize);
        initAvailableBuffer();
    }

    private void initAvailableBuffer() {
        for (int i = availableBuffer.length - 1; i >= 0; i--) {
            this.availableBuffer[i] = -1;
        }
    }

    //i右移直到变成0 log2(8) = 3
    private static int log2(int i) {
        int r = 0;
        while ((i >>= 1) != 0) {
            ++r;
        }
        return r;
    }

    @Override
    public long next() {
        return next(1);
    }

    @Override
    public long next(int n) {
        do {
            long currentMaxProducerSequenceNumber = currentProducerSequence.get();

            long nextProducerSequenceNumber = currentMaxProducerSequenceNumber + n;

            //生产者超过消费者一圈的临界点序列
            long wrapPoint = nextProducerSequenceNumber - this.ringBufferSize;

            long cachedGatingSequence = this.gatingSequenceCache.get();

            if(wrapPoint > cachedGatingSequence){
                long gatingSequence = SequenceUtils.getMinimumSequence(currentMaxProducerSequenceNumber, this.gatingConsumerSequenceList);

                if(wrapPoint > gatingSequence){
                    LockSupport.parkNanos(1L);

                    //park短暂阻塞之后continue跳出重新进入循环
                    continue;
                }

                //更新一下缓存的序列
                this.gatingSequenceCache.set(gatingSequence);
            }else {
                //生产者没有超过消费者一圈，可以放心生产
                if(this.currentProducerSequence.compareAndSet(currentMaxProducerSequenceNumber, nextProducerSequenceNumber)){
                    return nextProducerSequenceNumber;
                }
                //cas失败，则重新进入循环中获取最新的消费序列
            }
        }while (true);
    }

    @Override
    public void publish(long publishIndex) {
        setAvailable(publishIndex);
        waitStrategy.signalWhenBlocking();
    }

    //将特定序列标记为可供消费者使用
    //消费者会调用isAvailable来进行比对，如果一致，可以消费；如果不一致，不可以消费
    //还未完全搞清楚原理
    private void setAvailable(long publishIndex) {

    }

    @Override
    public SequenceBarrier newBarrier() {
        return null;
    }

    @Override
    public SequenceBarrier newBarrier(Sequence... dependenceSequences) {
        return null;
    }

    @Override
    public void addGatingConsumerSequenceList(Sequence... newGatingConsumerSequences) {

    }

    @Override
    public Sequence getCurrentConsumerSequence() {
        return null;
    }

    @Override
    public int getRingBufferSize() {
        return 0;
    }

    @Override
    public long getHighestPublishedSequenceNumber(long nextSequenceNumber, long availableSequenceNumber) {
        return 0;
    }
}
