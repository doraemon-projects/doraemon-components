package org.doraemon.component.uid.impl;

import org.doraemon.component.uid.buffer.BufferPaddingExecutor;
import org.doraemon.component.uid.buffer.RejectedPutBufferHandler;
import org.doraemon.component.uid.buffer.RejectedTakeBufferHandler;
import org.doraemon.component.uid.buffer.RingBuffer;
import org.doraemon.framework.exception.ApplicationRuntimeException;
import org.doraemon.framework.util.AssertUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;

/**
 * @description: 描述
 * @author: fengwenping
 * @date: 2020-07-05 17:38
 */
@Named("cachedUidGenerator")
public class CachedUidGenerator extends DefaultUidGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CachedUidGenerator.class);
    private static final int DEFAULT_BOOST_POWER = 3;

    /**
     * Spring properties
     */
    private int boostPower = DEFAULT_BOOST_POWER;
    private int paddingFactor = RingBuffer.DEFAULT_PADDING_PERCENT;
    private Long scheduleInterval;

    private RejectedPutBufferHandler rejectedPutBufferHandler;
    private RejectedTakeBufferHandler rejectedTakeBufferHandler;

    /**
     * RingBuffer
     */
    private RingBuffer ringBuffer;
    private BufferPaddingExecutor bufferPaddingExecutor;

    @PostConstruct
    public void afterPropertiesSet() throws Exception {
        // initialize workerId & bitsAllocator
        super.afterPropertiesSet();

        // initialize RingBuffer & RingBufferPaddingExecutor
        this.initRingBuffer();
        LOGGER.info("Initialized RingBuffer successfully.");
    }

    @Override
    public long getUID() {
        try {
            return ringBuffer.take();
        } catch (Exception e) {
            throw new ApplicationRuntimeException("Generate unique id exception. ", e);
        }
    }

    @Override
    public String parseUID(long uid) {
        return super.parseUID(uid);
    }

    @PreDestroy
    public void destroy() throws Exception {
        bufferPaddingExecutor.shutdown();
    }

    protected List<Long> nextIdsForOneSecond(long currentSecond) {
        // Initialize result list size of (max sequence + 1)
        int listSize = (int) bitsAllocator.getMaxSequence() + 1;
        List<Long> uidList = new ArrayList<>(listSize);

        // Allocate the first sequence of the second, the others can be calculated with the offset
        long firstSeqUid = bitsAllocator.allocate(currentSecond - epochSeconds, workerId, 0L);
        for (int offset = 0; offset < listSize; offset++) {
            uidList.add(firstSeqUid + offset);
        }

        return uidList;
    }

    /**
     * Initialize RingBuffer & RingBufferPaddingExecutor
     */
    private void initRingBuffer() {
        // initialize RingBuffer
        int bufferSize = ((int) bitsAllocator.getMaxSequence() + 1) << boostPower;
        this.ringBuffer = new RingBuffer(bufferSize, paddingFactor);
        LOGGER.info("Initialized ring buffer size:{}, paddingFactor:{}", bufferSize, paddingFactor);

        // initialize RingBufferPaddingExecutor
        boolean usingSchedule = (scheduleInterval != null);
        this.bufferPaddingExecutor = new BufferPaddingExecutor(ringBuffer, this::nextIdsForOneSecond, usingSchedule);
        if (usingSchedule) {
            bufferPaddingExecutor.setScheduleInterval(scheduleInterval);
        }

        LOGGER.info("Initialized BufferPaddingExecutor. Using schdule:{}, interval:{}", usingSchedule, scheduleInterval);

        // set rejected put/take handle policy
        this.ringBuffer.setBufferPaddingExecutor(bufferPaddingExecutor);
        if (rejectedPutBufferHandler != null) {
            this.ringBuffer.setRejectedPutHandler(rejectedPutBufferHandler);
        }
        if (rejectedTakeBufferHandler != null) {
            this.ringBuffer.setRejectedTakeHandler(rejectedTakeBufferHandler);
        }

        // fill in all slots of the RingBuffer
        bufferPaddingExecutor.paddingBuffer();

        // start buffer padding threads
        bufferPaddingExecutor.start();
    }

    /**
     * Setters for spring property
     */
    public void setBoostPower(int boostPower) {
        AssertUtils.assertTrue(boostPower > 0, "Boost power must be positive!");
        this.boostPower = boostPower;
    }

    public void setRejectedPutBufferHandler(RejectedPutBufferHandler rejectedPutBufferHandler) {
        AssertUtils.assertNotNull(rejectedPutBufferHandler, "RejectedPutBufferHandler can't be null!");
        this.rejectedPutBufferHandler = rejectedPutBufferHandler;
    }

    public void setRejectedTakeBufferHandler(RejectedTakeBufferHandler rejectedTakeBufferHandler) {
        AssertUtils.assertNotNull(rejectedTakeBufferHandler, "RejectedTakeBufferHandler can't be null!");
        this.rejectedTakeBufferHandler = rejectedTakeBufferHandler;
    }

    public void setScheduleInterval(long scheduleInterval) {
        AssertUtils.assertTrue(scheduleInterval > 0, "Schedule interval must positive!");
        this.scheduleInterval = scheduleInterval;
    }

}
